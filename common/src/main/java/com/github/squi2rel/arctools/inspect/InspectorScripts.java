package com.github.squi2rel.arctools.inspect;

import arc.util.Log;
import com.github.squi2rel.arc.ArcLib;
import com.github.squi2rel.arc.script.ScriptEnvironment;
import com.github.squi2rel.arc.script.ScriptEnvironment.CompiledScript;
import org.mozilla.javascript.*;
import org.mozilla.javascript.ast.AstRoot;
import org.mozilla.javascript.ast.Name;
import org.mozilla.javascript.lc.type.TypeInfoFactory;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Tester, inspector requests and Copy as JS all use ArcLib's default environment.
 */
final class InspectorScripts {
    record Completion(int start, int end, String prefix, List<String> names) {
    }

    record Message(Log.LogLevel level, String text) {
    }

    static final class Execution {
        final AtomicBoolean stopped = new AtomicBoolean();
        final long deadline;

        Execution(long timeoutMillis) {
            deadline = timeoutMillis <= 0 ? Long.MAX_VALUE : System.nanoTime() + timeoutMillis * 1_000_000L;
        }

        void check() {
            if (stopped.get() || Thread.currentThread().isInterrupted()) throw new InterruptedScript("Script stopped");
            if (System.nanoTime() >= deadline) throw new InterruptedScript("Script exceeded its time limit");
        }
    }

    static final class InterruptedScript extends Error {
        InterruptedScript(String message) {
            super(message);
        }
    }

    private final ScriptEnvironment environment = ArcLib.scripts();
    private final ScriptableObject scope = environment.scope();
    private final Function rangeFunction;
    boolean wrapReferences = true;
    BooleanSupplier catchCallbackErrors = () -> false;
    Consumer<Throwable> onCallbackError = ignored -> {
    };

    InspectorScripts() {
        environment.setCallbackErrorHandler(this::handleCallbackFailure);
        for (Class<?> type : new Class<?>[]{arc.scene.Element.class, arc.scene.Group.class, arc.scene.Scene.class,
                arc.scene.ui.layout.Table.class, arc.scene.ui.Label.class, arc.scene.ui.TextButton.class,
                arc.scene.ui.ImageButton.class, arc.scene.ui.CheckBox.class, arc.scene.ui.Slider.class,
                arc.scene.ui.TextField.class, arc.scene.ui.TextArea.class, arc.scene.ui.ScrollPane.class,
                arc.scene.ui.Image.class, arc.flabel.FLabel.class, arc.flabel.FConfig.class,
                arc.graphics.Color.class, arc.struct.Seq.class, arc.math.geom.Vec2.class,
                arc.input.KeyCode.class, arc.files.Fi.class, com.github.squi2rel.arc.ui.ArcStyles.class,
                TesterEvents.class}) {
            put(type.getSimpleName(), type);
        }
        rangeFunction = withContext(cx -> (Function) cx.evaluateString(scope, """
                (function*(start, end, step){
                    if(step === 0) throw new Error('step can not be 0');
                    if(step > 0){ for(let i = start; i < end; i += step) yield i; }
                    else{ for(let i = start; i > end; i += step) yield i; }
                })
                """, "range.js", 1, null));
    }

    private <T> T withContext(ContextAction<T> action) {
        return environment.withContext(action);
    }

    private void handleCallbackFailure(Throwable failure) {
        if (!catchCallbackErrors.getAsBoolean()) {
            if (failure instanceof RuntimeException runtime) throw runtime;
            throw (Error) failure;
        }
        onCallbackError.accept(failure);
    }

    boolean busy() {
        return environment.busy();
    }

    void put(String name, Object value) {
        withContext(cx -> {
            ScriptableObject.putProperty(scope, name, wrap(value));
            return null;
        });
    }

    void helpers(TesterFunctions functions) {
        withContext(cx -> {
            NativeJavaObject helper = new NativeJavaObject(scope, functions, TypeInfoFactory.GLOBAL.create(TesterFunctions.class)) {
                @Override
                public Object get(String name, Scriptable start) {
                    Class<?> primitive = switch (name) {
                        case "void" -> void.class;
                        case "boolean" -> boolean.class;
                        case "byte" -> byte.class;
                        case "short" -> short.class;
                        case "int" -> int.class;
                        case "long" -> long.class;
                        case "float" -> float.class;
                        case "double" -> double.class;
                        case "char" -> char.class;
                        default -> null;
                    };
                    if (primitive != null) return Context.javaToJS(primitive, scope);
                    if (name.equals("JL")) {
                        Object java = ScriptableObject.getProperty(scope, "java");
                        return java instanceof Scriptable package_ ? ScriptableObject.getProperty(package_, "lang") : Scriptable.NOT_FOUND;
                    }
                    return super.get(name, start);
                }

                @Override
                public Object[] getIds() {
                    LinkedHashSet<Object> ids = new LinkedHashSet<>(Arrays.asList(super.getIds()));
                    ids.addAll(List.of("void", "boolean", "byte", "short", "int", "long", "float", "double", "char", "JL"));
                    return ids.toArray();
                }
            };
            ScriptableObject.putProperty(scope, "$", helper);
            ScriptableObject.putProperty(scope, "IntFunc", helper);
            return null;
        });
    }

    Object range(int start, int end, int step) {
        return withContext(cx -> rangeFunction.call(cx, scope, scope, new Object[]{start, end, step}));
    }

    NativeArray array(Iterable<?> values) {
        ArrayList<Object> items = new ArrayList<>();
        for (Object value : values) items.add(value);
        return withContext(cx -> {
            NativeArray array = new NativeArray(items.toArray());
            array.setParentScope(scope);
            array.setPrototype(ScriptableObject.getArrayPrototype(scope));
            return array;
        });
    }

    Object asJs(Object value) {
        return withContext(cx -> wrap(InspectorReflection.unwrap(value)));
    }

    String store(String prefix, Object value) {
        return withContext(cx -> {
            String base = prefix.replaceAll("[^a-zA-Z0-9_$]", "_");
            int index = 0;
            while (ScriptableObject.hasProperty(scope, base + index)) index++;
            String key = base + index;
            ScriptableObject.putProperty(scope, key, wrap(value));
            return key;
        });
    }

    private Object wrap(Object value) {
        if (value instanceof Class<?> type) return new NativeJavaClass(scope, type);
        if (wrapReferences && value instanceof Method method) return new MethodFunction(method);
        if (wrapReferences && value instanceof MethodHandle handle) return new HandleFunction(handle);
        return Context.javaToJS(value, scope);
    }

    private final class MethodFunction extends BaseFunction {
        private final Method method;

        MethodFunction(Method method) {
            super(scope, ScriptableObject.getFunctionPrototype(scope));
            this.method = method;
        }

        @Override
        public String getFunctionName() {
            return method.getName();
        }

        @Override
        public Object call(Context context, Scriptable local, Scriptable self, Object[] args) {
            try {
                Object target = null;
                if (!Modifier.isStatic(method.getModifiers())) {
                    for (Scriptable current = self; current != null; current = current.getPrototype()) {
                        if (current instanceof Wrapper wrapper && method.getDeclaringClass().isInstance(wrapper.unwrap())) {
                            target = wrapper.unwrap();
                            break;
                        }
                    }
                    InspectorReflection.target(method, target);
                }

                var types = TypeInfoFactory.get(scope).createList(method.getGenericParameterTypes());
                int fixed = method.isVarArgs() ? types.size() - 1 : types.size();
                if (args.length < fixed || !method.isVarArgs() && args.length != fixed)
                    throw new IllegalArgumentException("Invalid argument count for " + method);
                Object[] converted = new Object[types.size()];
                for (int i = 0; i < fixed; i++) converted[i] = Context.jsToJava(args[i], types.get(i));
                if (method.isVarArgs()) {
                    var arrayType = types.get(fixed);
                    if (args.length == types.size()
                            && (args[fixed] == null || args[fixed] instanceof NativeArray || args[fixed] instanceof NativeJavaArray)) {
                        converted[fixed] = Context.jsToJava(args[fixed], arrayType);
                    } else {
                        var componentType = arrayType.getComponentType();
                        Object rest = componentType.newArray(args.length - fixed);
                        for (int i = fixed; i < args.length; i++)
                            Array.set(rest, i - fixed, Context.jsToJava(args[i], componentType));
                        converted[fixed] = rest;
                    }
                }
                method.trySetAccessible();
                Object result = method.invoke(target, converted);
                if (method.getReturnType() == void.class) return Undefined.instance;
                return context.getWrapFactory().wrap(context, local, result,
                        TypeInfoFactory.get(scope).create(method.getGenericReturnType()));
            } catch (Throwable failure) {
                throw Context.throwAsScriptRuntimeEx(failure);
            }
        }
    }

    private final class HandleFunction extends BaseFunction implements Wrapper {
        private final MethodHandle handle;

        HandleFunction(MethodHandle handle) {
            super(scope, ScriptableObject.getFunctionPrototype(scope));
            this.handle = handle;
        }

        @Override
        public Object call(Context context, Scriptable local, Scriptable self, Object[] args) {
            try {
                Object[] converted = InspectorReflection.arguments(args, handle.type().parameterArray());
                Object result = handle.asFixedArity().invokeWithArguments(converted);
                return context.getWrapFactory().wrap(context, local, result, handle.type().returnType());
            } catch (Throwable failure) {
                throw Context.throwAsScriptRuntimeEx(failure);
            }
        }

        @Override
        public Object unwrap() {
            return handle;
        }

        @Override
        public String toString() {
            return handle.toString();
        }
    }

    Object evaluate(String code, Object self, String binding, Object bound) {
        return withContext(cx -> {
            Scriptable local = cx.newObject(scope);
            local.setParentScope(scope);
            if (self != null) local.setPrototype(Context.toObject(self, scope));
            ScriptableObject.putProperty(local, "self", Context.javaToJS(self, scope));
            if (binding != null) ScriptableObject.putProperty(local, binding, Context.javaToJS(bound, scope));
            return InspectorReflection.unwrap(cx.evaluateString(local, code, "mini_console.js", 1, null));
        });
    }

    CompiledScript compile(String source, String name, boolean strict) {
        return strict ? environment.compileStrict(source, name) : environment.compile(source, name);
    }

    Object execute(CompiledScript code, Execution current, Consumer<Message> logger) {
        return environment.withContext(cx -> {
            try (Capture ignored = Capture.open(logger)) {
                current.check();
                Object result = environment.execute(code);
                current.check();
                return result instanceof NativeArray array ? array.toArray() : result;
            }
        }, current::check);
    }

    Completion complete(String text, int cursor, boolean properties) {
        int end = Math.max(0, Math.min(cursor, text.length())), start = end;
        while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) start--;
        String prefix = text.substring(start, end);
        boolean afterDot = start > 0 && text.charAt(start - 1) == '.';
        if (afterDot && !properties) return new Completion(start, end, prefix, List.of());
        final int from = start;
        return environment.tryWithContext(cx -> {
            Scriptable object = scope;
            if (afterDot) {
                int base = from - 1;
                while (base > 0 && (Character.isJavaIdentifierPart(text.charAt(base - 1)) || text.charAt(base - 1) == '.'))
                    base--;
                String[] path = text.substring(base, from - 1).split("\\.");
                Object value = ScriptableObject.getProperty(scope, path[0]);
                for (int i = 1; i < path.length && value instanceof Scriptable parent; i++) {
                    value = ScriptableObject.getProperty(parent, path[i]);
                }
                if (!(value instanceof Scriptable target)) return new Completion(from, end, prefix, List.of());
                object = target;
            }
            TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (Scriptable parent = object; parent != null; parent = afterDot ? null : parent.getParentScope()) {
                for (Object key : ScriptableObject.getPropertyIds(parent)) names.add(String.valueOf(key));
            }
            if (!afterDot) {
                names.addAll(List.of("var", "let", "const", "function", "return", "if", "else", "for", "while", "new", "true", "false", "null", "undefined", "importClass", "importPackage"));
                try {
                    CompilerEnvirons environs = CompilerEnvirons.ideEnvirons();
                    environs.setLanguageVersion(Context.VERSION_ES6);
                    AstRoot root = new Parser(environs).parse(text, "completion.js", 1);
                    root.visit(node -> {
                        if (node instanceof Name name) names.add(name.getIdentifier());
                        return true;
                    });
                } catch (RuntimeException ignored) {
                }
            }
            names.removeIf(name -> name.equals(prefix) || !name.regionMatches(true, 0, prefix, 0, prefix.length()));
            return new Completion(from, end, prefix, List.copyOf(names));
        }, new Completion(start, end, prefix, List.of()));
    }

    static String error(Throwable error) {
        if (error instanceof RhinoException rhino) return rhino.getMessage() + "\n" + rhino.getScriptStackTrace();
        return arc.util.Strings.getStackTrace(error);
    }

    /**
     * Routes only the executing thread's Arc logs; unrelated client logs keep their handler.
     */
    private static final class Capture implements AutoCloseable {
        private static final ThreadLocal<Consumer<Message>> sinks = new ThreadLocal<>();
        private static Log.LogHandler previous;
        private static int users;
        private static final Log.LogHandler router = (level, text) -> {
            Consumer<Message> sink = sinks.get();
            if (sink != null)
                sink.accept(new Message(level, Log.removeColors(text).replaceAll("\\x1B\\[[0-9;]*m", "")));
            else previous.log(level, text);
        };
        private final Consumer<Message> old;

        private Capture(Consumer<Message> sink) {
            old = sinks.get();
            sinks.set(sink);
        }

        static synchronized Capture open(Consumer<Message> sink) {
            if (users++ == 0) {
                previous = Log.logger;
                Log.logger = router;
            }
            return new Capture(sink);
        }

        @Override
        public void close() {
            if (old == null) sinks.remove();
            else sinks.set(old);
            synchronized (Capture.class) {
                if (--users == 0 && Log.logger == router) Log.logger = previous;
            }
        }
    }
}
