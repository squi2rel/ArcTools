package com.github.squi2rel.arctools.inspect;

import arc.Core;
import arc.scene.Element;
import arc.scene.ui.*;
import arc.struct.ObjectMap;
import org.mozilla.javascript.*;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.*;
import java.util.*;

/**
 * Reflection and JSRequest semantics shared by the inspector's value and member menus.
 */
final class InspectorReflection {
    private static final int maxCollectionItems = 64;
    final InspectorScripts scripts;

    InspectorReflection() {
        scripts = new InspectorScripts();
    }

    String store(String prefix, Object value) {
        return scripts.store(prefix, value);
    }

    Object evaluate(String code, Object self, String binding, Object value) {
        return scripts.evaluate(code, self, binding, value);
    }

    static Object unwrap(Object value) {
        while (value instanceof Wrapper wrapper) value = wrapper.unwrap();
        return value;
    }

    static Object convert(Object value, Class<?> type) {
        value = unwrap(value);
        if (value == Undefined.instance || value == Scriptable.NOT_FOUND) value = null;
        if (value == null) {
            if (type.isPrimitive())
                throw new IllegalArgumentException("null cannot be assigned to " + type.getTypeName());
            return null;
        }
        if (type.isInstance(value)) return value;
        Context.enter();
        try {
            return Context.jsToJava(value, type);
        } finally {
            Context.exit();
        }
    }

    static Object[] arguments(Object value, Class<?>[] types) {
        Object[] values;
        value = unwrap(value);
        if (value instanceof NativeArray array) {
            if (array.getLength() > Integer.MAX_VALUE) throw new IllegalArgumentException("Too many arguments");
            values = new Object[(int) array.getLength()];
            for (int i = 0; i < values.length; i++) values[i] = array.get(i, array);
        } else if (value instanceof Object[] array) {
            values = array;
        } else if (value instanceof List<?> list) {
            values = list.toArray();
        } else {
            throw new IllegalArgumentException("Method arguments must be an array");
        }
        if (values.length != types.length) {
            throw new IllegalArgumentException("Expected " + types.length + " arguments, got " + values.length);
        }
        Object[] args = new Object[types.length];
        for (int i = 0; i < args.length; i++) args[i] = convert(values[i], types[i]);
        return args;
    }

    static Object read(Field field, Object object) throws IllegalAccessException {
        Object target = target(field, object);
        field.trySetAccessible();
        return field.get(target);
    }

    static void write(Field field, Object object, Object value) throws ReflectiveOperationException {
        Object target = target(field, object);
        if (Modifier.isFinal(field.getModifiers()))
            throw new IllegalAccessException("Field is final: " + field.getName());
        Object converted = convert(value, field.getType());
        if (!field.trySetAccessible()) throw new IllegalAccessException("Cannot access field: " + field);
        // Arc widgets cache derived style data; use their setters as FieldValueLabel does.
        if (field.getName().equals("style")) {
            if (target instanceof ImageButton button && converted instanceof ImageButton.ImageButtonStyle style) {
                ImageButton.ImageButtonStyle copy = new ImageButton.ImageButtonStyle(style);
                ImageButton.ImageButtonStyle old = button.getStyle();
                copy.imageUp = old.imageUp;
                copy.imageDown = old.imageDown;
                copy.imageOver = old.imageOver;
                copy.imageUpColor = old.imageUpColor;
                copy.imageDownColor = old.imageDownColor;
                copy.imageOverColor = old.imageOverColor;
                button.setStyle(copy);
                converted = copy;
            } else if (target instanceof TextButton button && converted instanceof TextButton.TextButtonStyle style) {
                button.setStyle(style);
            } else if (target instanceof Button button && converted instanceof Button.ButtonStyle style) {
                button.setStyle(style);
            } else if (target instanceof ScrollPane pane && converted instanceof ScrollPane.ScrollPaneStyle style) {
                pane.setStyle(style);
            } else if (target instanceof TextField text && converted instanceof TextField.TextFieldStyle style) {
                text.setStyle(style);
            } else if (target instanceof ProgressBar bar && converted instanceof ProgressBar.ProgressBarStyle style) {
                bar.setStyle(style);
            } else if (target instanceof Dialog dialog && converted instanceof Dialog.DialogStyle style) {
                dialog.setStyle(style);
            } else if (target instanceof Label label && converted instanceof Label.LabelStyle style) {
                label.setStyle(style);
            }
        }
        field.set(target, converted);
        if (target instanceof Element element) element.invalidateHierarchy();
    }

    static Object invoke(Executable executable, Object object, Object arguments, boolean special) throws Throwable {
        Object[] args = arguments(arguments, executable.getParameterTypes());
        executable.trySetAccessible();
        try {
            if (executable instanceof Constructor<?> constructor) return constructor.newInstance(args);
            Method method = (Method) executable;
            Object target = target(method, object);
            if (special && !Modifier.isStatic(method.getModifiers())) {
                Class<?> owner = method.getDeclaringClass();
                return MethodHandles.privateLookupIn(owner, MethodHandles.lookup())
                        .unreflectSpecial(method, owner).bindTo(target).invokeWithArguments(args);
            }
            return method.invoke(target, args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    static Object target(Member member, Object object) {
        if (Modifier.isStatic(member.getModifiers())) return null;
        if (object == null) throw new IllegalStateException("'obj' is null.");
        if (!member.getDeclaringClass().isInstance(object))
            throw new IllegalArgumentException("Object is not an instance of " + member.getDeclaringClass().getName());
        return object;
    }

    static List<Class<?>> hierarchy(Class<?> type) {
        LinkedHashSet<Class<?>> types = new LinkedHashSet<>();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) addType(types, current);
        return new ArrayList<>(types);
    }

    private static void addType(Set<Class<?>> types, Class<?> type) {
        if (!types.add(type)) return;
        for (Class<?> iface : type.getInterfaces()) addType(types, iface);
    }

    static String describe(Object value) {
        return describe(value, new IdentityHashMap<>());
    }

    private static String describe(Object value, IdentityHashMap<Object, Boolean> visiting) {
        if (value == null) return "null";
        if (value == Undefined.instance) return "undefined";
        if (value instanceof CharSequence chars) return "\"" + chars + "\"";
        // Group.toString() recursively includes inspector windows and feeds the viewer back into itself.
        if (value instanceof Element element) return elementName(element);
        if (value.getClass().isArray())
            return value.getClass().getComponentType().getTypeName() + "[" + Array.getLength(value) + "]";
        try {
            if (value instanceof Map<?, ?> map) return describeItems(map, map.entrySet(), true, visiting);
            if (value instanceof ObjectMap<?, ?> map) return describeItems(map, map, true, visiting);
            if (value instanceof Iterable<?> iterable) return describeItems(iterable, iterable, false, visiting);
            if (value instanceof Map.Entry<?, ?> entry)
                return describeEntry(entry, entry.getKey(), entry.getValue(), visiting);
            if (value instanceof ObjectMap.Entry<?, ?> entry)
                return describeEntry(entry, entry.key, entry.value, visiting);
            return String.valueOf(value);
        } catch (Throwable error) {
            return "<toString: " + error.getClass().getSimpleName() + ">";
        }
    }

    private static String describeItems(Object container, Iterable<?> items, boolean map, IdentityHashMap<Object, Boolean> visiting) {
        if (visiting.put(container, true) != null) return "<recursive>";
        try {
            StringBuilder out = new StringBuilder(map ? "{" : "[");
            int index = 0;
            for (Object item : items) {
                if (index > 0) out.append(", ");
                if (index++ == maxCollectionItems) {
                    out.append("...");
                    break;
                }
                out.append(describe(item, visiting));
            }
            return out.append(map ? '}' : ']').toString();
        } finally {
            visiting.remove(container);
        }
    }

    private static String describeEntry(Object entry, Object key, Object value, IdentityHashMap<Object, Boolean> visiting) {
        if (visiting.put(entry, true) != null) return "<recursive>";
        try {
            return describe(key, visiting) + '=' + describe(value, visiting);
        } finally {
            visiting.remove(entry);
        }
    }

    static String elementName(Element element) {
        if (Core.scene != null && element == Core.scene.root) return "ROOT";
        Class<?> type = element.getClass();
        while (type.isAnonymousClass() && type.getSuperclass() != null) type = type.getSuperclass();
        StringBuilder out = new StringBuilder(type.getSimpleName());
        if (element instanceof TextButton button && !button.getText().isEmpty()) {
            out.append(": ").append(button.getText());
        }
        if (element.name != null) out.append(" [").append(element.name).append(']');
        return out.toString();
    }

    static String error(Throwable error) {
        while (error instanceof InvocationTargetException && error.getCause() != null) error = error.getCause();
        return error.getClass().getSimpleName() + (error.getMessage() == null ? "" : ": " + error.getMessage());
    }
}
