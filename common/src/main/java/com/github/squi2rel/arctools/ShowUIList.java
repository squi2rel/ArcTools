package com.github.squi2rel.arctools;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.Colors;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Interp;
import arc.scene.Action;
import arc.scene.Element;
import arc.scene.Scene;
import arc.scene.actions.Actions;
import arc.scene.style.BaseDrawable;
import arc.scene.style.Drawable;
import arc.scene.style.Style;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.*;
import arc.scene.ui.layout.Stack;
import arc.scene.ui.layout.Table;
import arc.scene.utils.Disableable;
import arc.util.Align;
import arc.util.Scaling;
import arc.util.pooling.Pool;
import arc.util.pooling.Pools;
import com.github.squi2rel.arc.ui.ArcStyles;
import com.github.squi2rel.arctools.ui.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * ArcLib adaptation of mod-tools' ShowUIList.
 */
public final class ShowUIList {
    static final String windowName = "arctools-show-ui-list";
    private static final float defaultWindowHeight = 400f;

    private ShowUIList() {
    }

    private static float defaultWindowWidth() {
        return Core.graphics.isPortrait() ? 320f : 400f;
    }

    public static void show(Scene scene) {
        for (Window window : Window.all) {
            if (window instanceof UIListDialog dialog && dialog.owner == scene) {
                dialog.show(scene);
                return;
            }
        }

        new UIListDialog(scene).showWindow();
    }

    private enum Category {
        drawables("Icon", Color.gold),
        styles("Styles", Color.orange),
        colors("Colors", Color.acid),
        interps("Interp", Color.royal),
        actions("Actions", Color.cyan);

        final String title;
        final Color color;

        Category(String title, Color color) {
            this.title = title;
            this.color = color;
        }
    }

    private record Entry(Category category, String name, Object value) {
        boolean matches(String query) {
            if (query.isEmpty()) return true;
            String type = value == null ? "" : value.getClass().getSimpleName();
            return (name + " " + type + " " + category.title).toLowerCase(Locale.ROOT).contains(query);
        }
    }

    private static List<Entry> entries() {
        List<Entry> result = new ArrayList<>();
        addArcStyles(result);
        addAtlas(result);
        addColors(result);
        addInterps(result);
        result.sort(Comparator.comparing(Entry::name));
        return result;
    }

    private static void addAtlas(List<Entry> result) {
        if (Core.atlas == null) return;
        IdentityHashMap<Drawable, Boolean> seen = new IdentityHashMap<>();
        for (Entry entry : result) {
            if (entry.value instanceof Drawable drawable) seen.put(drawable, Boolean.TRUE);
        }
        for (var region : Core.atlas.getRegions()) {
            Drawable drawable = Core.atlas.drawable(region.name);
            if (drawable != null && seen.put(drawable, Boolean.TRUE) == null) {
                result.add(new Entry(Category.drawables, "Tex." + region.name, drawable));
            }
        }
    }

    private static void addArcStyles(List<Entry> result) {
        for (Field field : sortedFields(ArcStyles.class)) {
            if (field.isSynthetic() || field.getName().startsWith("$remold$") || field.getName().startsWith("$nipx$") || !Modifier.isStatic(field.getModifiers()))
                continue;
            try {
                Object value = field.get(null);
                if (value instanceof Drawable) {
                    result.add(new Entry(Category.drawables, "ArcStyles." + field.getName(), value));
                    result.add(new Entry(Category.styles, "ArcStyles." + field.getName(), value));
                } else if (value instanceof Style || value instanceof TreeElement.TreeStyle) {
                    result.add(new Entry(Category.styles, "ArcStyles." + field.getName(), value));
                } else if (value instanceof Color) {
                    result.add(new Entry(Category.colors, "ArcStyles." + field.getName(), value));
                }
            } catch (IllegalAccessException ignored) {
            }
        }
    }

    private static void addColors(List<Entry> result) {
        IdentityHashMap<Color, Boolean> seen = new IdentityHashMap<>();
        for (Entry entry : result) {
            if (entry.value instanceof Color color) seen.put(color, Boolean.TRUE);
        }
        for (Field field : sortedFields(Color.class)) {
            if (field.isSynthetic() || !Modifier.isStatic(field.getModifiers()) || !Color.class.isAssignableFrom(field.getType()))
                continue;
            try {
                Color color = (Color) field.get(null);
                if (color != null && seen.put(color, Boolean.TRUE) == null) {
                    result.add(new Entry(Category.colors, "Color." + field.getName(), color));
                }
            } catch (IllegalAccessException ignored) {
            }
        }
        for (var entry : Colors.getColors()) {
            Color color = entry.value;
            if (color != null && seen.put(color, Boolean.TRUE) == null) {
                result.add(new Entry(Category.colors, "Colors." + entry.key, color));
            }
        }
    }

    private static void addInterps(List<Entry> result) {
        IdentityHashMap<Interp, Boolean> seen = new IdentityHashMap<>();
        for (Field field : sortedFields(Interp.class)) {
            if (field.isSynthetic() || !Modifier.isStatic(field.getModifiers()) || !Interp.class.isAssignableFrom(field.getType()))
                continue;
            try {
                Interp interp = (Interp) field.get(null);
                if (interp != null && seen.put(interp, Boolean.TRUE) == null) {
                    result.add(new Entry(Category.interps, "Interp." + field.getName(), interp));
                }
            } catch (IllegalAccessException ignored) {
            }
        }
    }

    private static Field[] sortedFields(Class<?> type) {
        Field[] fields = type.getFields();
        java.util.Arrays.sort(fields, Comparator.comparing(Field::getName));
        return fields;
    }

    private static final class UIListDialog extends Window {
        private static final int interpColumns = 3;
        private static final Set<String> actionFieldBlacklist = Set.of("time", "began", "complete", "lastPercent", "color",
                "start", "startR", "startG", "startB", "startA", "startX", "startY");

        private final Scene owner;
        private final List<Entry> entries = ShowUIList.entries();
        private final EnumMap<Category, Table> pages = new EnumMap<>(Category.class);
        private final TextField search = new TextField();
        private final Image actionPreview = new Image(ArcStyles.white);
        private final Color backgroundColor;
        private final Color iconColor;
        private Table rows;
        private boolean forceDisabled;

        UIListDialog(Scene owner) {
            super(owner, "ShowUIList", defaultWindowWidth(), defaultWindowHeight, true);
            this.owner = owner;
            name = windowName;
            setModal(false);
            setMovable(true);
            setResizable(true);
            setCentered(false);
            closeOnBack();
            actionPreview.update(() -> actionPreview.setOrigin(Align.center));

            cont.top().left();
            Table colorWrap = new Table();
            backgroundColor = ColorBlock.setting(colorWrap.table().get(), owner, "bg",
                    "arctools.showuilist.background", "877f5eff", null);
            iconColor = ColorBlock.setting(colorWrap.table().padLeft(6f).get(), owner, "icon",
                    "arctools.showuilist.icon", "ffffffff", null);

            Table options = new Table();
            options.left().defaults().left();
            options.add(colorWrap);
            Label copyHint = new Label("Double-click names to copy");
            copyHint.setColor(Color.lightGray);
            copyHint.setEllipsis(true);
            copyHint.setAlignment(Align.left);
            options.add(copyHint).padLeft(6f).growX().minWidth(0f).left().row();
            CheckBox disabled = new CheckBox("ForceDisabled", ArcStyles.checkBox);
            disabled.changed(() -> forceDisabled = disabled.isChecked());
            options.add(disabled).colspan(3).growX().left().padTop(-4f);
            cont.add(options).growX().minWidth(0f).row();

            search.setMessageText("search UI values");
            search.changed(this::rebuild);
            cont.add(search).growX().minWidth(0f).height(36f).pad(5f).row();

            Category[] categories = Category.values();
            String[] names = new String[categories.length];
            Color[] colors = new Color[categories.length];
            Table[] tables = new Table[categories.length];
            for (int i = 0; i < categories.length; i++) {
                Category category = categories[i];
                names[i] = category.title;
                colors[i] = category.color;
                tables[i] = createPage();
                pages.put(category, tables[i]);
            }
            IntTab tabs = new IntTab(-1f, names, colors, tables);
            tabs.eachWidth = defaultWindowWidth() / 4.3f;
            cont.add(tabs.build()).grow().minWidth(0f).minHeight(0f).pad(0f, 5f, 5f, 5f);
            rebuild();
        }

        void showWindow() {
            show(owner);
            setPosition(Math.max(0f, (owner.getWidth() - getWidth()) / 2f),
                    Math.max(0f, (owner.getHeight() - getHeight()) / 2f));
            search.requestKeyboard();
        }

        @Override
        public boolean focusSearch(boolean clear) {
            if (clear) search.setText("");
            search.requestKeyboard();
            search.setCursorPosition(Integer.MAX_VALUE);
            return true;
        }

        private Table createPage() {
            Table page = new Table();
            page.top().left();
            page.background(new BaseDrawable() {
                @Override
                public void draw(float x, float y, float width, float height) {
                    float oldColor = Draw.getColorPacked();
                    Draw.color(backgroundColor, backgroundColor.a * Draw.getColorAlpha());
                    Fill.crect(x, y, width, height);
                    Draw.color(oldColor);
                }
            });
            return page;
        }

        private void rebuild() {
            String query = search.getText().trim().toLowerCase(Locale.ROOT);
            for (Category category : Category.values()) {
                rows = pages.get(category);
                rows.reset();
                switch (category) {
                    case drawables -> rebuildDrawables(query);
                    case styles -> rebuildStyles(query);
                    case colors -> rebuildColors(query);
                    case interps -> rebuildInterps(query);
                    case actions -> rebuildActions(query);
                }
                rows.invalidateHierarchy();
            }
        }

        private void rebuildDrawables(String query) {
            rows.top().left();
            rows.defaults().left().padLeft(6f);
            int visible = addDrawableSection("Icon", "ArcStyles.", query, true);
            visible += addDrawableSection("Tex", "Tex.", query, false);
            if (visible == 0) noMatches(Category.drawables.title, 2);
        }

        private int addDrawableSection(String title, String prefix, String query, boolean icon) {
            int count = 0;
            for (Entry entry : entries) {
                if (entry.category == Category.drawables && entry.name.startsWith(prefix) && entry.matches(query))
                    count++;
            }
            if (count == 0) return 0;

            section(title);
            for (Entry entry : entries) {
                if (entry.category != Category.drawables || !entry.name.startsWith(prefix) || !entry.matches(query))
                    continue;
                Drawable drawable = (Drawable) entry.value;
                if (icon) {
                    Image image = new Image(drawable, drawable instanceof TextureRegionDrawable ? Scaling.fit : Scaling.stretch);
                    image.update(() -> image.setColor(iconColor));
                    if (drawable instanceof TextureRegionDrawable texture && texture.getRegion().width != 0) {
                        rows.add(image).size(36f, texture.getRegion().height / (float) texture.getRegion().width * 36f);
                    } else {
                        rows.add(image).size(36f);
                    }
                    rows.add(copyLabel(entry)).growX().left().row();
                } else {
                    rows.add(drawablePreview(drawable)).size(32f);
                    Table field = new Table();
                    field.left();
                    field.add(copyLabel(entry)).growX().left();
                    addViewButton(field, entry);
                    rows.add(field).growX().left().row();
                }
            }
            return count;
        }

        private void rebuildStyles(String query) {
            rows.top().left();
            rows.defaults().left().padLeft(6f);
            int visible = 0;
            for (Entry entry : entries) {
                if (entry.category != Category.styles || !entry.matches(query)) continue;
                visible++;
                addStylePreview(rows, entry.value);
                Table field = new Table();
                field.left();
                field.add(copyLabel(entry)).growX().left();
                addViewButton(field, entry);
                rows.add(field).growX().left().row();
            }
            if (visible == 0) noMatches(Category.styles.title, 2);
        }

        private void rebuildColors(String query) {
            rows.top().left();
            rows.defaults().left().padLeft(6f);
            int visible = addColorSection("ArcStyles", "ArcStyles.", query);
            visible += addColorSection("Color", "Color.", query);
            visible += addColorSection("Colors", "Colors.", query);
            if (visible == 0) noMatches(Category.colors.title, 2);
        }

        private int addColorSection(String title, String prefix, String query) {
            int count = 0;
            for (Entry entry : entries) {
                if (entry.category == Category.colors && entry.name.startsWith(prefix) && entry.matches(query)) count++;
            }
            if (count == 0) return 0;

            section(title);
            for (Entry entry : entries) {
                if (entry.category != Category.colors || !entry.name.startsWith(prefix) || !entry.matches(query))
                    continue;
                Color color = (Color) entry.value;
                BorderImage preview = new BorderImage(ArcStyles.white, 2f).border(new Color(color).inv().a(1f));
                preview.setColor(color);
                rows.add(preview).tooltip("#" + color).size(42f);
                rows.add(copyLabel(entry)).growX().left().row();
            }
            return count;
        }

        private void rebuildInterps(String query) {
            InterpAnimation animation = new InterpAnimation();
            Table graphs = new Table();
            int[] count = {0};
            Runnable builtins = () -> {
                animation.reset();
                count[0] = buildInterpGraphs(graphs, query, animation);
            };
            builtins.run();
            ScrollPane pane = new ScrollPane(graphs, ArcStyles.scrollPane);
            pane.setFadeScrollBars(false);
            rows.add(pane).pad(10f).grow().colspan(2).row();
            rows.defaults().growX();
            rows.button("Built-in", ArcStyles.textButton, builtins).height(42f);
            rows.button("Add", ArcStyles.textButton, () -> JSRequest.request(owner, Interp.class, interp -> {
                if (count[0] == 0) graphs.clearChildren();
                graphs.add(new InterpGraph(interp, animation, count[0])).size(120f).padBottom(32f);
                if (++count[0] % interpColumns == 0) graphs.row();
            })).height(42f);
        }

        private int buildInterpGraphs(Table graphs, String query, InterpAnimation animation) {
            graphs.clearChildren();
            graphs.center().top();
            int visible = 0;
            for (Entry entry : entries) {
                if (entry.category != Category.interps || !entry.matches(query)) continue;
                InterpGraph graph = new InterpGraph((Interp) entry.value, animation, visible);
                graph.clicked(() -> Core.app.setClipboardText(entry.name));
                graphs.add(graph).tooltip(entry.name).size(120f).padBottom(32f);
                if (++visible % interpColumns == 0) graphs.row();
            }
            if (visible == 0) graphs.add("No matching interps").colspan(interpColumns).pad(20f);
            return visible;
        }

        @SuppressWarnings("unchecked")
        private void rebuildActions(String query) {
            rows.center();
            rows.defaults().center();
            rows.add(actionPreview).size(64f).pad(24f).row();
            rows.button("Reset", ArcStyles.textButton, this::resetActionPreview)
                    .growX().height(42f).row();

            Table buttons = new Table();
            LinkedHashSet<Class<? extends Action>> actionTypes = new LinkedHashSet<>();
            for (var method : Actions.class.getDeclaredMethods()) {
                Class<?> returnType = method.getReturnType();
                if (Action.class.isAssignableFrom(returnType) && !Modifier.isAbstract(returnType.getModifiers())) {
                    actionTypes.add((Class<? extends Action>) returnType);
                }
            }

            int visible = 0;
            for (Class<? extends Action> actionType : actionTypes) {
                String simpleName = actionType.getSimpleName();
                String name = simpleName.endsWith("Action") ? simpleName.substring(0, simpleName.length() - 6) : simpleName;
                if (!query.isEmpty() && !(name + " " + actionType.getName()).toLowerCase(Locale.ROOT).contains(query))
                    continue;
                TextButton button = new TextButton(name, ArcStyles.toolTextButton);
                button.clicked(() -> applyAction(actionType));
                button.addListener(arc.scene.ui.Tooltip.Tooltips.getInstance().create(actionType.getName()));
                buttons.add(button).size(140f, 42f);
                if (++visible % 2 == 0) buttons.row();
            }
            if (visible == 0) buttons.add("No matching actions").colspan(2).pad(20f);

            ScrollPane pane = new ScrollPane(buttons, ArcStyles.scrollPane);
            pane.setFadeScrollBars(false);
            rows.add(pane).grow().minHeight(160f);
        }

        private void resetActionPreview() {
            actionPreview.clearActions();
            actionPreview.translation.set(0f, 0f);
            actionPreview.setScale(1f);
            actionPreview.setRotation(0f);
            actionPreview.setColor(Color.white);
            actionPreview.visible = true;
            actionPreview.touchable = arc.scene.event.Touchable.enabled;
            actionPreview.setDrawable(ArcStyles.white);
            actionPreview.setScaling(Scaling.stretch);
            actionPreview.setAlign(Align.center);
            actionPreview.setSize(64f, 64f);
            if (actionPreview.parent != null) actionPreview.parent.invalidateHierarchy();
            if (actionPreview.parent == null) rebuild();
        }

        private <T extends Action> void applyAction(Class<T> actionType) {
            try {
                Constructor<T> constructor = actionType.getDeclaredConstructor();
                constructor.trySetAccessible();
                Pool<T> pool = Pools.get(actionType, () -> {
                    try {
                        return constructor.newInstance();
                    } catch (ReflectiveOperationException error) {
                        throw new RuntimeException(error);
                    }
                });
                T action = pool.obtain();
                action.reset();
                action.setPool(pool);
                RBuilder.show(owner, actionType.getSimpleName(), action, actionFieldBlacklist,
                        () -> actionPreview.addAction(action));
            } catch (ReflectiveOperationException | RuntimeException error) {
                arc.util.Log.err("Unable to preview action: " + actionType.getName(), error);
            }
        }

        private void section(String title) {
            rows.add(title).colspan(2).color(ArcStyles.accent).growX().left().padTop(6f).row();
            rows.image(ArcStyles.white).color(ArcStyles.accent).colspan(2).growX().height(1f).row();
        }

        private void noMatches(String title, int colspan) {
            rows.add("No matching " + title.toLowerCase(Locale.ROOT)).colspan(colspan).pad(20f);
        }

        private Label copyLabel(Entry entry) {
            String name = entry.name.substring(entry.name.lastIndexOf('.') + 1);
            Label label = new Label(name);
            label.clicked(listener -> listener.setTapCountInterval(0.4f), listener -> {
                if (listener.getTapCount() == 2) {
                    Core.app.setClipboardText(entry.name);
                    listener.setTapCount(0);
                }
            });
            label.addListener(arc.scene.ui.Tooltip.Tooltips.getInstance().create(entry.name));
            return label;
        }

        private void addViewButton(Table parent, Entry entry) {
            PreviewUtils.addPreviewButton(parent, table -> {
                table.defaults().left().pad(2f, 5f, 2f, 5f);
                if (entry.value instanceof Drawable drawable) {
                    detailRow(table, "Left", number(drawable.getLeftWidth()));
                    detailRow(table, "Right", number(drawable.getRightWidth()));
                    detailRow(table, "Top", number(drawable.getTopHeight()));
                    detailRow(table, "Bottom", number(drawable.getBottomHeight()));
                    detailRow(table, "Min width", number(drawable.getMinWidth()));
                    detailRow(table, "Min height", number(drawable.getMinHeight()));
                    detailRow(table, "Image size", number(drawable.imageSize()));
                } else {
                    table.button("Copy", ArcStyles.toolTextButton,
                            () -> Core.app.setClipboardText(entry.name)).size(96f, 42f);
                }
            }).padLeft(8f);
        }

        private Stack drawablePreview(Drawable drawable) {
            Image image = new Image(drawable, drawable instanceof TextureRegionDrawable ? Scaling.fit : Scaling.stretch);
            image.update(() -> image.setColor(iconColor));
            Label type = new Label(drawable instanceof TextureRegionDrawable ? "texture" : "nine");
            type.setColor(Color.lightGray);
            type.setFontScale(0.7f);
            type.setAlignment(Align.topLeft);
            Stack stack = new Stack(image, type);
            stack.hovered(() -> type.visible = false);
            stack.exited(() -> type.visible = true);
            return stack;
        }

        private <T extends Element> T applyDisabled(T element) {
            if (element instanceof Disableable disableable) {
                element.update(() -> disableable.setDisabled(forceDisabled));
            }
            return element;
        }

        private void addStylePreview(Table table, Object value) {
            if (value instanceof ScrollPane.ScrollPaneStyle style) {
                Table content = new Table();
                content.add("pane").row();
                content.add("test-test-test").color(Color.gray).row();
                content.add("test-test-test").color(Color.gray).row();
                table.add(new ScrollPane(content, style)).growX().maxWidth(144f).height(64f);
            } else if (value instanceof Dialog.DialogStyle style) {
                Table content = new Table();
                content.add(new Dialog("dialog", style));
                table.add(new ScrollPane(content, ArcStyles.scrollPane)).growX().height(42f);
            } else if (value instanceof Label.LabelStyle style) {
                table.add(new Label("label", style));
            } else if (value instanceof Slider.SliderStyle style) {
                Slider slider = applyDisabled(new Slider(0f, 10f, 1f, false, style));
                slider.setValue(5f);
                table.add(slider).growX();
            } else if (value instanceof TextButton.TextButtonStyle style) {
                table.add(applyDisabled(new TextButton("text button", style))).size(96f, 42f);
            } else if (value instanceof ImageButton.ImageButtonStyle style) {
                ImageButton button = applyDisabled(new ImageButton(ArcStyles.white, style));
                button.resizeImage(18f);
                table.add(button).size(96f, 42f);
            } else if (value instanceof Button.ButtonStyle style) {
                Button button = applyDisabled(new Button(style));
                button.add("button");
                table.add(button).size(96f, 42f);
            } else if (value instanceof TextField.TextFieldStyle style) {
                table.add(applyDisabled(new TextField("field", style))).growX();
            } else if (value instanceof TreeElement.TreeStyle style) {
                TreeElement tree = new TreeElement(style);
                TreeElement.TreeElementNode root = new TreeElement.TreeElementNode(new Label("tree"));
                root.add(new TreeElement.TreeElementNode(new Label("child")));
                root.setExpanded(true);
                tree.add(root);
                table.add(tree).growX();
            } else if (value instanceof Drawable drawable) {
                table.add(drawablePreview(drawable)).size(32f);
            } else {
                table.add(value.getClass().getSimpleName());
            }
        }

        private static void detailRow(Table table, String key, String value) {
            table.add(key).color(ArcStyles.accent).top();
            table.add(value).growX().labelAlign(Align.right).row();
        }

        private static String number(float value) {
            return value == (long) value ? Long.toString((long) value) : Float.toString(value);
        }

    }

    private static final class InterpGraph extends Element {
        private static final int pointAmount = 100;
        private static final Color[] palette = {Color.red, Color.orange, Color.yellow, Color.green,
                Color.cyan, Color.sky, Color.pink, Color.purple};

        private final Interp interp;
        private final InterpAnimation animation;
        private final Color curveColor;

        InterpGraph(Interp interp, InterpAnimation animation, int index) {
            this.interp = interp;
            this.animation = animation;
            int value = Math.floorMod(index * 10, (palette.length - 1) * 60);
            float progress = value / 60f;
            int colorIndex = (int) progress;
            curveColor = new Color(palette[colorIndex]).lerp(palette[colorIndex + 1], progress - colorIndex);
        }

        @Override
        public void draw() {
            super.draw();
            Core.graphics.requestRendering();

            int visiblePoints = animation.visiblePoints();

            float graphX = x + width / 8f;
            float graphY = y + height / 8f;
            float graphWidth = width * 6f / 8f;
            float graphHeight = height * 6f / 8f;
            float oldStroke = Lines.getStroke();
            float alpha = parentAlpha * color.a;

            Lines.stroke(3f);
            Draw.color(Color.lightGray, alpha);
            Lines.rect(graphX, graphY, graphWidth, graphHeight);

            Draw.color(curveColor, alpha);
            int lastPoint = Math.min(visiblePoints, pointAmount);
            if (lastPoint == 1) {
                Lines.line(graphX, graphY + graphHeight * interp.apply(0f),
                        graphX + graphWidth / pointAmount, graphY + graphHeight * interp.apply(1f / pointAmount), false);
            } else if (lastPoint > 1) {
                Lines.beginLine();
                for (int i = 0; i <= lastPoint; i++) {
                    float progress = i / (float) pointAmount;
                    Lines.linePoint(graphX + graphWidth * progress, graphY + graphHeight * interp.apply(progress));
                }
                Lines.endLine();
            }

            Lines.stroke(oldStroke);
            Draw.color();
        }
    }

    private static final class InterpAnimation {
        private static final int holdFrames = 30;
        private long lastFrame = Long.MIN_VALUE;
        private int visiblePoints;

        int visiblePoints() {
            long frame = Core.graphics.getFrameId();
            if (frame != lastFrame) {
                lastFrame = frame;
                visiblePoints = (visiblePoints + 1) % (InterpGraph.pointAmount + holdFrames + 1);
            }
            return visiblePoints;
        }

        void reset() {
            lastFrame = Long.MIN_VALUE;
            visiblePoints = 0;
        }
    }
}
