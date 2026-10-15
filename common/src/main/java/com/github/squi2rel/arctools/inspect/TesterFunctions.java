package com.github.squi2rel.arctools.inspect;

import arc.Core;
import arc.Events;
import arc.Settings;
import arc.files.Fi;
import arc.func.Boolf;
import arc.func.Cons;
import arc.func.Func;
import arc.func.Prov;
import arc.graphics.Color;
import arc.graphics.Pixmap;
import arc.graphics.Texture;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Font;
import arc.graphics.g2d.TextureRegion;
import arc.graphics.gl.FrameBuffer;
import arc.graphics.gl.Shader;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.Action;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.actions.Actions;
import arc.scene.style.Drawable;
import arc.scene.style.Style;
import arc.scene.ui.ColorImage;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Reflect;
import com.github.squi2rel.arc.ui.ArcStyles;
import com.github.squi2rel.arctools.ui.*;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.Wrapper;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Portable JSFunc entry points backed by the current inspector and shared scope.
 */
@SuppressWarnings("unused")
public final class TesterFunctions {
    private final ArcInspector inspector;
    private final InspectorScripts scripts;

    public final Fi dataDir;
    public final ClassLoader main = TesterFunctions.class.getClassLoader();
    public final Settings settings = Core.settings;
    public final Font FONT = com.github.squi2rel.arc.ui.ArcStyles.label == null ? null : com.github.squi2rel.arc.ui.ArcStyles.label.font;

    public final Class<?> Z = boolean.class, B = byte.class, C = char.class, D = double.class, F = float.class;
    public final Class<?> I = int.class, J = long.class, S = short.class, V = void.class;
    public final Class<?> L = Object.class, CLS = Class.class, CL = ClassLoader.class, STR = String.class, LIST = List.class;
    public final EventSnapshot snapshot = new EventSnapshot();

    TesterFunctions(ArcInspector inspector, InspectorScripts scripts, Fi directory) {
        this.inspector = inspector;
        this.scripts = scripts;
        dataDir = directory;
    }

    public void inspect(Element element) {
        Core.app.post(() -> inspector.inspect(element));
    }

    public void pickElement(Cons<Element> callback) {
        Core.app.post(() -> inspector.pick(Element.class, callback));
    }

    public void setDrawPadElem(Element element) {
        Core.app.post(() -> inspector.setDrawPadElem(element));
    }

    public void toggleDrawPadElem(Element element) {
        Core.app.post(() -> inspector.toggleDrawPadElem(element));
    }

    public void focusElement(Element element) {
        applyDraw(() -> {
            Vec2 point = getAbsPosCenter(element);
            float radius = Mathf.dst(element.getWidth(), element.getHeight()) / 2f;
            float outer = Mathf.dst(Core.scene.getWidth(), Core.scene.getHeight()) * 2f;
            Draw.color(Color.black, 0.4f);
            for (int i = 0; i < 100; i++) {
                float a = i * Mathf.PI2 / 100f, b = (i + 1) * Mathf.PI2 / 100f;
                arc.graphics.g2d.Fill.quad(point.x + Mathf.cos(a) * radius, point.y + Mathf.sin(a) * radius,
                        point.x + Mathf.cos(a) * outer, point.y + Mathf.sin(a) * outer,
                        point.x + Mathf.cos(b) * outer, point.y + Mathf.sin(b) * outer,
                        point.x + Mathf.cos(b) * radius, point.y + Mathf.sin(b) * radius);
            }
            Draw.color();
        });
    }

    public void scl() {
        var camera = Core.scene.getCamera();
        float ratio = camera.height / camera.width;
        camera.position.set(Core.input.mouseX(), Core.input.mouseY());
        camera.width = 200f;
        camera.height = 200f * ratio;
    }

    public void scl(Element element) {
        var camera = Core.scene.getCamera();
        float mul = Core.graphics.getHeight() / (float) Core.graphics.getWidth();
        Vec2 endPos = getAbsPosCenter(element);
        float end = element.getWidth(), start = Core.graphics.getWidth();
        float startX = camera.position.x, startY = camera.position.y;
        Core.scene.root.addAction(new Action() {
            float a;

            @Override
            public boolean act(float delta) {
                a += 0.01f;
                float v = a < 1f ? a : 2f - a;
                camera.position.set(Interp.swing.apply(startX, endPos.x, v), Interp.swing.apply(startY, endPos.y, v));
                camera.width = Interp.swing.apply(start, end, v);
                camera.height = camera.width * mul;
                if (a < 2f) return false;
                camera.width = Core.graphics.getWidth();
                camera.height = Core.graphics.getHeight();
                camera.position.set(camera.width / 2f, camera.height / 2f);
                return true;
            }
        });
    }

    public Window showInfo(Object object) {
        return inspector.showJavaObject(object);
    }

    public Window showInfo(Class<?> type) {
        return showInfo(null, type);
    }

    public Window showInfo(Object object, Class<?> type) {
        return inspector.showJavaObject(object, Objects.requireNonNull(type));
    }

    public WatchWindow watch() {
        return new WatchWindow(Core.scene);
    }

    public WatchWindow watch(WatchWindow watch) {
        return watch == null ? watch() : watch;
    }

    public WatchWindow watch(String name, Prov<Object> value) {
        return watch().watch(name, value::get).show();
    }

    public WatchWindow watch(Prov<Object> value) {
        return watch("Watch", value);
    }

    public void copyText(CharSequence text, Element element) {
        copyText(text, getAbsolutePos(element));
    }

    public void copyText(CharSequence text) {
        copyText(text, new Vec2(Core.input.mouseX(), Core.input.mouseY()));
    }

    public void copyText(CharSequence text, Vec2 position) {
        Core.app.setClipboardText(String.valueOf(text));
        notice("Copied: " + text, position);
    }

    public void copyValue(String text, Object value) {
        copyValue(text, value, new Vec2(Core.input.mouseX(), Core.input.mouseY()));
    }

    public void copyValue(String text, Object value, Vec2 position) {
        notice(text + " saved as " + quietPut(value), position);
    }

    public void copyValue(Object value) {
        copyValue("value", value);
    }

    private void notice(String text, Vec2 position) {
        Vec2 point = position.cpy();
        Core.app.post(() -> {
            Table tip = new Table(ArcStyles.overlay);
            tip.add(text).pad(6f);
            tip.pack();
            Core.scene.add(tip);
            tip.setPosition(point.x, point.y, Align.center);
            tip.keepInStage();
            tip.actions(Actions.sequence(Actions.delay(2f), Actions.fadeOut(0.4f), Actions.remove()));
        });
    }

    public String quietPut(Object value) {
        return scripts.store("tmp", value);
    }

    public void quietPut0(String name, Object value) {
        scripts.put(name, value);
    }

    public void put(Object value) {
        copyText(quietPut(value));
    }

    public Pair<Object, Object> pairOf(Object first, Object second) {
        return Pair.of(first, second);
    }

    public Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName(name, true, main);
    }

    public Class<?> forName(String name) throws ClassNotFoundException {
        return Class.forName(name, false, main);
    }

    public Object findClass(String name) throws ClassNotFoundException {
        return scripts.asJs(forName(name));
    }

    public Object invoke(Object callable, Object... arguments) throws Throwable {
        Object value = unwrap(callable);
        if (!(value instanceof MethodHandle handle))
            throw new IllegalArgumentException("callable must be a MethodHandle");
        return handle.invokeWithArguments(arguments);
    }

    public Object invoke(Object callable, Object arg1) throws Throwable {
        return ((MethodHandle) unwrap(callable)).invoke(arg1);
    }

    public Object invoke(Object callable, Object arg1, Object arg2) throws Throwable {
        return ((MethodHandle) unwrap(callable)).invoke(arg1, arg2);
    }

    public Object invoke(Object callable, Object arg1, Object arg2, Object arg3) throws Throwable {
        return ((MethodHandle) unwrap(callable)).invoke(arg1, arg2, arg3);
    }

    public Object unwrap(Object value) {
        while (value instanceof Wrapper wrapper) value = wrapper.unwrap();
        return value == Undefined.instance ? "undefined" : value;
    }

    public Class<?> basic(Class<?> type) {
        type = box(type);
        if (Number.class.isAssignableFrom(type)) return Number.class;
        return type;
    }

    public Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == float.class) return Float.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        return type;
    }

    public Class<?> unbox(Class<?> type) {
        if (type.isPrimitive()) return type;
        if (type == Boolean.class) return boolean.class;
        if (type == Byte.class) return byte.class;
        if (type == Character.class) return char.class;
        if (type == Short.class) return short.class;
        if (type == Integer.class) return int.class;
        if (type == Float.class) return float.class;
        if (type == Long.class) return long.class;
        if (type == Double.class) return double.class;
        return type;
    }

    public Object cast(Object value, Class<?> type) {
        return InspectorReflection.convert(value, type);
    }

    public Object asJS(Object value) {
        return scripts.asJs(value);
    }

    public Object toFloat(float value) {
        return value;
    }

    public Object toInt(int value) {
        return value;
    }

    public Object toDouble(double value) {
        return value;
    }

    public Object toLong(long value) {
        return value;
    }

    public Object toByte(byte value) {
        return value;
    }

    public Object toShort(short value) {
        return value;
    }

    public Object range(int max) {
        return range(0, max, 1);
    }

    public Object range(int min, int max) {
        return range(min, max, 1);
    }

    public Object range(int from, int to, int step) {
        return scripts.range(from, to, step);
    }

    public NativeArray toArray(Iterable<?> values) {
        return scripts.array(values);
    }

    public <T> void forEach(Iterable<T> values, Cons<T> consumer) {
        for (T value : values) consumer.get(value);
    }

    public String repeat(String value, int count) {
        return value.repeat(count);
    }

    public String substring(String value, int start, int end) {
        if (start < 0) start += value.length();
        if (end < 0) end += value.length();
        return value.substring(start, end);
    }

    public boolean equals(CharSequence text, int start, int end, CharSequence expected) {
        if (expected == null || end - start != expected.length()) return false;
        for (int index = start; index < end; index++)
            if (text.charAt(index) != expected.charAt(index - start)) return false;
        return true;
    }

    public int indexOf(String text, String pattern) {
        return text.indexOf(pattern);
    }

    public boolean isBlank(CharSequence text, int start, int end) {
        for (int index = start; index < end; index++) if (!Character.isWhitespace(text.charAt(index))) return false;
        return true;
    }

    public boolean isParentheses(CharSequence text, int start, int end) {
        if (start < 0 || end >= text.length() || start > end) return false;
        int index = start;
        while (index <= end && Character.isWhitespace(text.charAt(index))) index++;
        if (index > end || text.charAt(index) != '(') return false;
        do index++; while (index <= end && Character.isWhitespace(text.charAt(index)));
        return index > end;
    }

    public <T> T findParent(Element element, Class<T> type) {
        return findParent(element, type::isInstance);
    }

    @SuppressWarnings("unchecked")
    public <T> T findParent(Element element, Boolf<Element> predicate) {
        for (Element current = element; current != null; current = current.parent)
            if (predicate.get(current)) return (T) current;
        return null;
    }

    public boolean checkIn(Element element, float x, float y) {
        return checkIn(element, Core.scene.root, x, y);
    }

    public boolean checkIn(Element element, Group group, float x, float y) {
        if (element == null || group == null) return false;
        Vec2 local = group.localToDescendantCoordinates(element, new Vec2(x, y));
        return local.x > 0f && local.y > 0f && local.x < element.getWidth() && local.y < element.getHeight();
    }

    public Style getStyle(Element element) {
        try {
            return (Style) element.getClass().getMethod("getStyle").invoke(element);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    public CharSequence getPath(Element element) {
        if (element == null) return "null";
        if (element.name != null)
            return "Core.scene.find(\"" + element.name.replace("\\", "\\\\").replace("\"", "\\\"") + "\")";
        StringBuilder suffix = new StringBuilder();
        for (Element current = element; current != null && current != Core.scene.root; current = current.parent) {
            suffix.insert(0, ".children.get(" + current.getZIndex() + ")");
        }
        return "Core.scene.root" + suffix;
    }

    public void scrollTo(Element actor, Element target) {
        Core.app.post(() -> {
            ScrollPane pane = findClosestPane(actor);
            if (pane == null || target == null) return;
            pane.setSmoothScrolling(false);
            Vec2 position = target.localToAscendantCoordinates(pane.getWidget(), new Vec2(target.getWidth() / 2f, target.getHeight() / 2f));
            pane.scrollTo(0f, position.y, target.getWidth(), target.getHeight(), false, true);
            pane.act(1f);
            pane.setSmoothScrolling(true);
        });
    }

    public boolean checkInStage(Vec2 position) {
        return position != null && position.x >= 0f && position.y >= 0f && position.x <= Core.scene.getWidth() && position.y <= Core.scene.getHeight();
    }

    public void hideBarIfValid(ScrollPane pane) {
        if (pane == null) return;
        pane.setScrollingDisabled(true, false);
        pane.setOverscroll(false, false);
        pane.update(() -> pane.setScrollingDisabled(true, Mathf.equal(pane.getHeight(), pane.getPrefHeight())));
    }

    public void addOrRemove(Element element, boolean show) {
        Core.app.post(() -> {
            if (show) {
                if (element.parent == null) Core.scene.add(element);
            } else element.remove();
        });
    }

    public Vec2 getAbsPosCenter(Element element) {
        return element.localToStageCoordinates(new Vec2(element.getWidth() / 2f, element.getHeight() / 2f));
    }

    public Vec2 getAbsolutePos(Element element) {
        return element.localToStageCoordinates(new Vec2());
    }

    public Vec2 getAbsolutePosTopLeft(Element element) {
        return element.localToStageCoordinates(new Vec2(0f, element.getHeight()));
    }

    public ScrollPane findClosestPane(Element element) {
        return findParent(element, ScrollPane.class);
    }

    public Element findWindow(Element element) {
        return findParent(element, current -> current instanceof arc.scene.ui.Dialog || current.getClass().getSimpleName().endsWith("Window"));
    }

    public Pixmap pixmapOf(Texture texture) {
        return texture == null || texture.getTextureData() == null ? null : texture.getTextureData().getPixmap();
    }

    public Fi file(String path) {
        return Core.files.internal(path);
    }

    public void rotateCamera(float degrees) {
        Core.scene.getCamera().mat.rotate(degrees);
    }

    public boolean eq(Object first, Object second) {
        first = InspectorReflection.unwrap(first);
        second = InspectorReflection.unwrap(second);
        if (first != null && Reflect.isWrapper(first.getClass())) return first.equals(second);
        return first == second;
    }

    public Element fx(String text) {
        Label label = new Label(text);
        label.setColor(com.github.squi2rel.arc.ui.ArcStyles.accent);
        return label;
    }

    public void applyDraw(Runnable draw) {
        inspector.applyDraw(draw);
    }

    public void compare(Object first, Object second) {
        window(window -> {
            window.title.setText("Comparator");
            if (first == second) {
                window.cont.add("Two objects are the same.");
                return;
            }
            if (first == null || second == null) {
                window.cont.add((first == null ? "o1" : "o2") + " is null.");
                return;
            }
            Table cont = new Table();
            cont.top().defaults().growX();
            window.cont.pane(cont).grow();
            LinkedHashSet<Class<?>> classes = new LinkedHashSet<>(InspectorReflection.hierarchy(first.getClass()));
            classes.addAll(InspectorReflection.hierarchy(second.getClass()));
            for (Class<?> type : classes) {
                cont.add("CLAZZ").color(ArcStyles.accent);
                cont.add(type.getSimpleName()).colspan(2).row();
                for (Field field : type.getDeclaredFields()) {
                    Object v1 = fieldValue(field, first), v2 = fieldValue(field, second);
                    if (Objects.deepEquals(v1, v2)) continue;
                    cont.add(field.getName());
                    for (Object value : new Object[]{v1, v2}) {
                        Table item = new Table();
                        item.add(InspectorReflection.describe(value)).left();
                        item.button("Compare", Icons.chart, () -> compare(fieldValue(field, first), fieldValue(field, second)));
                        cont.add(item);
                    }
                    cont.row();
                }
            }
        });
    }

    private Object fieldValue(Field field, Object object) {
        try {
            return InspectorReflection.read(field, object);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    public void showInterp(Interp interpolation) {
        dialog(new InterpImage(interpolation));
    }

    public JSWindow window(Cons<Window> cons) {
        return new JSWindow(Core.scene, cons);
    }

    public JSWindow btn(String text, Runnable run) {
        return dialog(t -> t.button(text, ArcStyles.toolTextButton, run).size(64f, 45f));
    }

    public JSWindow testDraw(Runnable draw) {
        return testDraw0(group -> draw.run());
    }

    public JSWindow testDraw0(Cons<Group> draw) {
        return dialog(new Group() {
            {
                setTransform(true);
            }

            @Override
            public void drawChildren() {
                draw.get(this);
            }
        });
    }

    public JSWindow testShader(Shader shader, Runnable draw) {
        return testDraw0(group -> {
            FrameBuffer buffer = new FrameBuffer(Core.graphics.getWidth(), Core.graphics.getHeight());
            try {
                Draw.reset();
                buffer.begin(Color.clear);
                try {
                    draw.run();
                } finally {
                    buffer.end();
                }
                Draw.blit(buffer.getTexture(), shader);
            } finally {
                buffer.dispose();
            }
        });
    }

    public JSWindow dialog(Element element) {
        return window(d -> d.cont.pane(element).grow());
    }

    public JSWindow dialogText(String text) {
        return dialog(new Label(text));
    }

    public JSWindow dialog(TextureRegion region) {
        return dialog(new Image(region));
    }

    public JSWindow dialog(Texture texture) {
        return dialog(new TextureRegion(texture));
    }

    public JSWindow dialogd(Drawable drawable) {
        return dialog(new Image(drawable));
    }

    public JSWindow dialog(Color color) {
        return dialog(new ColorImage(color));
    }

    public JSWindow dialog(Cons<Table> builder) {
        return dialog(new Table(builder));
    }

    public JSWindow pixmap(int size, Cons<Pixmap> builder) {
        return pixmap(size, size, builder);
    }

    public JSWindow pixmap(int width, int height, Cons<Pixmap> builder) {
        Pixmap pixmap = new Pixmap(width, height);
        try {
            builder.get(pixmap);
            JSWindow dialog = dialog(new TextureRegion(new Texture(pixmap)));
            dialog.hidden(pixmap::dispose);
            return dialog;
        } catch (RuntimeException | Error error) {
            pixmap.dispose();
            throw error;
        }
    }

    public void dialog(Element element, boolean disposable) {
        dialog(element).autoDispose = disposable;
    }

    public void dialogText(String text, boolean disposable) {
        dialogText(text).autoDispose = disposable;
    }

    public void dialog(TextureRegion region, boolean disposable) {
        dialog(region).autoDispose = disposable;
    }

    public void dialog(Texture texture, boolean disposable) {
        dialog(texture).autoDispose = disposable;
    }

    public void dialog(Drawable drawable, boolean disposable) {
        dialogd(drawable).autoDispose = disposable;
    }

    public void dialog(Color color, boolean disposable) {
        dialog(color).autoDispose = disposable;
    }

    public void dialog(Pixmap pixmap, boolean disposable) {
        dialog(new TextureRegion(new Texture(pixmap))).autoDispose = disposable;
    }

    public interface MyProv<T> {
        T get() throws Exception;

        default String stringify(Object value) {
            return String.valueOf(value);
        }
    }

    public static class MyProvIns<T> implements MyProv<T> {
        private final Prov<T> prov;
        private final Func<Object, String> stringify;

        public MyProvIns(Prov<T> prov, Func<Object, String> stringify) {
            this.prov = prov;
            this.stringify = stringify;
        }

        @Override
        public T get() {
            return prov.get();
        }

        @Override
        public String stringify(Object value) {
            return stringify.get(value);
        }
    }

    public EventSnapshot newSnapshot() {
        return new EventSnapshot();
    }

    public static final class EventSnapshot {
        private final ObjectMap<Object, Seq<Cons<?>>> values = new ObjectMap<>();
        private final arc.graphics.g2d.TextureAtlas atlas;

        EventSnapshot() {
            ObjectMap<Object, Seq<Cons<?>>> events = Reflect.get(Events.class, "events");
            events.each((key, listeners) -> values.put(key, listeners.copy()));
            atlas = Core.atlas;
        }

        public void rollback() {
            ObjectMap<Object, Seq<Cons<?>>> events = Reflect.get(Events.class, "events");
            events.clear();
            values.each((key, listeners) -> events.put(key, listeners.copy()));
            Core.atlas = atlas;
        }
    }
}
