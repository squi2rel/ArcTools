package com.github.squi2rel.arctools.inspect;

import arc.Core;
import arc.files.Fi;
import arc.func.Boolp;
import arc.func.Cons;
import arc.func.Floatc;
import arc.func.Floatp;
import arc.graphics.Color;
import arc.graphics.Gl;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.input.KeyCode;
import arc.math.Mat;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.Scene;
import arc.scene.actions.Actions;
import arc.scene.event.ElementGestureListener;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.style.BaseDrawable;
import arc.scene.style.Drawable;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.*;
import arc.scene.ui.Dialog.DialogStyle;
import arc.scene.ui.ImageButton.ImageButtonStyle;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.CellAccess;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Reflect;
import arc.util.Strings;
import arc.util.Tmp;
import arc.util.serialization.Jval;
import com.github.squi2rel.arc.ui.ArcStyles;
import com.github.squi2rel.arc.ui.TextDrawable;
import com.github.squi2rel.arctools.SettingsUI;
import com.github.squi2rel.arctools.SettingsUI.SettingsBuilder;
import com.github.squi2rel.arctools.ui.*;

import java.lang.reflect.*;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class ArcInspector {
    private static final ObjectMap<Scene, ArcInspector> inspectors = new ObjectMap<>();
    private static final Color maskColor = Color.valueOf("00000055");
    private static final Color focusFill = Color.valueOf("ffd37f33");
    private static final Color focusLine = Color.valueOf("ffd37fff");
    private static final Color pinnedLine = Color.valueOf("7fd8ffff");
    private static final Color cellLine = Color.valueOf("e55454ff");
    public static final int padColor = 0x8CE99A75;
    public static final int padTextColor = Color.green.rgba();
    public static final int marginColor = Color.orange.cpy().a(0.5f).rgba();
    public static final int marginTextColor = ArcStyles.accent.rgba();
    private static final Color underlineColor = Color.lightGray.cpy().a(0.5f);
    private static final float dashTileSize = 8f;
    private static final Mat oldTransform = new Mat();
    private static final Vec2 tmp1 = new Vec2();
    private static final Vec2 tmp2 = new Vec2();
    private static final Drawable rowNone = new BaseDrawable();
    private static final Drawable rowFlatDown = new BaseDrawable() {
        @Override
        public void draw(float x, float y, float width, float height) {
            if (ArcStyles.selection != null) ArcStyles.selection.draw(x, y, width, height);
        }

        @Override
        public void draw(float x, float y, float originX, float originY, float width, float height, float scaleX, float scaleY, float rotation) {
            if (ArcStyles.selection != null)
                ArcStyles.selection.draw(x, y, originX, originY, width, height, scaleX, scaleY, rotation);
        }
    };

    private final Scene scene;
    private final InspectorLayer layer;
    private final Element mask;
    private final ObjectMap<Element, Boolean> filtered = new ObjectMap<>();
    private Element previousKeyboardFocus;
    private Cons<Element> selectionCallback;
    private Class<?> selectionType = Element.class;
    private final InspectorReflection reflection = new InspectorReflection();
    private final Seq<TesterWindow> testers = new Seq<>();
    private final Seq<Runnable> residentDrawTasks = new Seq<>();
    private TesterFiles testerFiles;
    private TesterTasks testerTasks;
    private SettingsUI settingsUI;
    private boolean hoverInfoWindow = Core.settings.getBool("arcui.inspect.hoverInfoWindow", true);
    private boolean anonymousInsteadSuper = Core.settings.getBool("arcui.inspect.anonymousInsteadSuper", true);
    private boolean checkCullingArea = Core.settings.getBool("arcui.inspect.checkCullingArea", false);
    private boolean autoScrollToSearch = Core.settings.getBool("arcui.inspect.autoScrollToSearch", false);
    private boolean debugBounds = Core.settings.getBool("arcui.inspect.debugBounds", false);
    private boolean drawHiddenPad = Core.settings.getBool("arcui.inspect.drawHiddenPad", false);
    private int maxDepthForAutoExpand = Core.settings.getInt("arcui.inspect.maxDepthForAutoExpand", 5);
    private Element selected;
    private Element focused;
    private Element pinned;
    private Element drawPadElem;
    private boolean selecting;
    private boolean locked;
    private boolean cancelEvent;
    private InspectorWindow frontWindow;
    private InspectorWindow focusedWindow;
    private Cell<?> focusedCell;
    private Table activeMenu;
    private Table activeSubMenu;
    private TextButton activeSubMenuButton;
    private Element activeMenuHitter;
    private Element activeSubMenuHitter;

    private ArcInspector(Scene scene) {
        this.scene = scene;
        this.mask = new Element() {
            @Override
            public Element hit(float x, float y, boolean touchable) {
                return selecting && cancelEvent ? this : null;
            }
        };
        mask.setFillParent(true);
        mask.touchable = Touchable.enabled;
        this.layer = new InspectorLayer();
        layer.setFillParent(true);
        layer.touchable = Touchable.disabled;
        scene.root.addChild(layer);
        scene.root.getCaptureListeners().insert(0, new Capture());
    }

    public static ArcInspector install(Scene scene) {
        if (scene == null) return null;
        ArcInspector existing = inspectors.get(scene);
        if (existing != null) return existing;
        ArcInspector created = new ArcInspector(scene);
        inspectors.put(scene, created);
        return created;
    }

    public static ArcInspector get(Scene scene) {
        return inspectors.get(scene);
    }

    public static void beforeDraw(Scene scene) {
        ArcInspector inspector = inspectors.get(scene);
        if (inspector != null) inspector.beforeDraw();
    }

    private void beforeDraw() {
        layer.setBounds(0f, 0f, scene.getWidth(), scene.getHeight());
        layer.updateInfo();
        layer.toFront();
    }

    public void inspect(Element element) {
        if (element == null) return;
        new InspectorWindow(element).showWindow();
    }

    public void pick() {
        beginSelect();
    }

    public void showTester() {
        if (testerFiles == null) initializeTester(new TesterFiles(Core.settings.getDataDirectory().child("tester")));
        TesterWindow window;
        if (testerFiles.enabled("multi_windows")) {
            window = testers.find(tester -> !tester.isShown() || tester.isHiding() || !tester.visible);
        } else {
            window = testers.isEmpty() ? null : testers.first();
        }
        if (window == null) {
            window = new TesterWindow();
            testers.add(window);
        }
        window.showWindow();
    }

    public void showSettings() {
        if (testerFiles == null) initializeTester(new TesterFiles(Core.settings.getDataDirectory().child("tester")));
        if (settingsUI == null) {
            settingsUI = new SettingsUI(scene);
            settingsUI.addSection("Tester", Icons.run, this::testerSettings);
            settingsUI.addSection("Review Element", Icons.watch, table -> reviewSettings(table, this::rebuildInspectors));
            settingsUI.addSection("UI", Icons.settings, this::uiSettings);
        }
        settingsUI.build();
    }

    private void testerSettings(Table table) {
        SettingsBuilder.build(table);
        String[][] settings = {
                {"Ignore Popup Error", "ignore_popup_error"}, {"Catch Callback Error", "catch_outsize_error"},
                {"Wrap References", "wrap_ref"}, {"Rollback History", "rollback_history"},
                {"Multiple Windows", "multi_windows"}, {"Output to Log", "output_to_log"},
                {"Capture Logger", "capture_logger"}, {"JS Properties", "js_prop"}, {"Auto Complete", "auto_complement"}
        };
        for (String[] entry : settings) {
            SettingsBuilder.check(entry[0], value -> {
                testerFiles.setting(entry[1], value);
                reflection.scripts.wrapReferences = testerFiles.enabled("wrap_ref");
            }, () -> testerFiles.enabled(entry[1]), () -> !entry[1].equals("auto_complement") || testerFiles.enabled("js_prop"));
        }
        SettingsBuilder.numberi("Max History Size", testerFiles::maxHistory, testerFiles::maxHistory, 0, 100);
        SettingsBuilder.clearBuild();
    }

    private void reviewSettings(Table table, Runnable rebuild) {
        SettingsBuilder.build(table);
        inspectorSetting("Hover Info Window", "hoverInfoWindow", () -> hoverInfoWindow, value -> hoverInfoWindow = value);
        inspectorSetting("Anonymous Instead Super", "anonymousInsteadSuper", () -> anonymousInsteadSuper, value -> {
            anonymousInsteadSuper = value;
            rebuild.run();
        });
        inspectorSetting("Check Culling Area", "checkCullingArea", () -> checkCullingArea, value -> checkCullingArea = value);
        SettingsBuilder.numberi("Max Depth For Auto Expand", value -> {
            maxDepthForAutoExpand = value;
            Core.settings.put("arcui.inspect.maxDepthForAutoExpand", value);
            rebuild.run();
        }, () -> maxDepthForAutoExpand, 0, 50);
        inspectorSetting("Auto Scroll To Search", "autoScrollToSearch", () -> autoScrollToSearch, value -> {
            autoScrollToSearch = value;
            rebuild.run();
        });
        SettingsBuilder.clearBuild();
    }

    private void inspectorSetting(String text, String key, Boolp getter, Cons<Boolean> setter) {
        SettingsBuilder.check(text, value -> {
            setter.get(value);
            Core.settings.put("arcui.inspect." + key, value);
        }, getter);
    }

    private void rebuildInspectors() {
        for (Element child : scene.root.getChildren().copy()) {
            if (child instanceof InspectorWindow window) {
                window.title.setText(name(window.rootElement));
                window.rebuild();
            }
        }
    }

    private void uiSettings(Table table) {
        SettingsBuilder.build(table);
        SettingsBuilder.check("Debug Bounds", this::setDebugBounds, () -> debugBounds);
        SettingsBuilder.check("Draw Hidden Pad", value -> {
            drawHiddenPad = value;
            Core.settings.put("arcui.inspect.drawHiddenPad", value);
        }, () -> drawHiddenPad, () -> debugBounds);
        SettingsBuilder.clearBuild();
        table.table(target -> {
            target.add("Bound Element").padRight(8f);
            target.label(() -> drawPadElem == null ? "<null>" : name(drawPadElem))
                    .growX().minWidth(0f).with(label -> label.setEllipsis(true));
            target.button("Select", ArcStyles.toolTextButton, () -> pick(Element.class, this::setDrawPadElem))
                    .disabled(button -> !debugBounds);
            target.button("Clear", ArcStyles.toolTextButton, () -> setDrawPadElem(null))
                    .disabled(button -> !debugBounds || drawPadElem == null);
        }).growX().row();
    }

    void initializeTester(TesterFiles files) {
        testerFiles = files;
        reflection.scripts.helpers(new TesterFunctions(this, reflection.scripts, files.directory));
        reflection.scripts.wrapReferences = files.enabled("wrap_ref");
        reflection.scripts.catchCallbackErrors = () -> files.enabled("catch_outsize_error");
        reflection.scripts.onCallbackError = error -> Core.app.post(() -> {
            if (testers.isEmpty()) testerTasks.onError.accept(error);
            else testers.peek().tester.callbackFailure(error);
        });
        testerTasks = new TesterTasks(reflection.scripts, files);
        testerTasks.onError = error -> {
            if (files.enabled("output_to_log")) arc.util.Log.err("[TESTER startup]", error);
            if (!files.enabled("ignore_popup_error")) reportError(error);
        };
    }

    public void startScripts() {
        if (testerFiles == null) initializeTester(new TesterFiles(Core.settings.getDataDirectory().child("tester")));
        testerTasks.start();
    }

    public void tick() {
        for (TesterWindow tester : testers) tester.tester.tick();
        if (testerTasks != null) testerTasks.tick(System.nanoTime());
    }

    public void dispose() {
        residentDrawTasks.clear();
        for (TesterWindow tester : testers) tester.tester.close();
        if (testerTasks != null) {
            testerTasks.tick(System.nanoTime());
            testerTasks.close();
        }
    }

    void watchScript(String title, arc.func.Prov<Object> getter) {
        watch(title, getter::get);
    }

    void applyDraw(Runnable draw) {
        residentDrawTasks.add(draw);
    }

    public void pick(Class<? extends Element> type, Cons<Element> callback) {
        beginSelect();
        selectionType = type;
        selectionCallback = callback;
    }

    private void beginSelect() {
        selectionCallback = null;
        selectionType = Element.class;
        restoreFiltered();
        selecting = true;
        locked = false;
        cancelEvent = false;
        previousKeyboardFocus = scene.getKeyboardFocus();
        scene.setKeyboardFocus(layer);
        selected = null;
        focused = null;
        pinned = null;
    }

    private void cancelSelect() {
        selectionCallback = null;
        selecting = false;
        locked = false;
        cancelEvent = false;
        mask.remove();
        restoreKeyboardFocus();
        restoreFiltered();
        selected = null;
        focused = null;
        focusedWindow = null;
    }

    private void selectAt(float rootX, float rootY) {
        selected = hit(rootX, rootY);
        if (selected != null && !selectionType.isInstance(selected)) selected = null;
        focused = selected;
        focusedWindow = null;
    }

    private Element hit(float rootX, float rootY) {
        Element actor = scene.root.hit(rootX, rootY, true);
        if (selectionCallback != null) return actor;
        while (actor != null && isInspectorElement(actor)) {
            actor = actor.parent;
        }
        return actor == null || actor == scene.root ? null : actor;
    }

    private void filter(Element element) {
        if (element == null || element == scene.root || filtered.containsKey(element)) return;
        filtered.put(element, element.visible);
        element.visible = false;
    }

    private void restoreFiltered() {
        for (ObjectMap.Entry<Element, Boolean> entry : filtered) {
            entry.key.visible = entry.value;
        }
        filtered.clear();
    }

    private void restoreKeyboardFocus() {
        Element focus = previousKeyboardFocus;
        previousKeyboardFocus = null;
        scene.setKeyboardFocus(focus != null && focus.getScene() == scene ? focus : null);
    }

    private boolean isInspectorElement(Element element) {
        for (Element current = element; current != null; current = current.parent) {
            if (current == layer || current instanceof Window || current == activeMenu || current == activeSubMenu || current == activeMenuHitter || current == activeSubMenuHitter) {
                return true;
            }
        }
        return false;
    }

    private void hideMenu() {
        hideSubMenu();
        if (activeMenu != null) {
            activeMenu.remove();
            activeMenu = null;
        }
        if (activeMenuHitter != null) {
            activeMenuHitter.remove();
            activeMenuHitter = null;
        }
    }

    private void hideSubMenu() {
        if (activeSubMenu != null) {
            activeSubMenu.remove();
            activeSubMenu = null;
        }
        if (activeSubMenuHitter != null) {
            activeSubMenuHitter.remove();
            activeSubMenuHitter = null;
        }
        if (activeSubMenuButton != null) {
            activeSubMenuButton.setChecked(false);
            activeSubMenuButton = null;
        }
    }

    private void setFocus(Element element) {
        setFocus(element, null);
    }

    private void setFocus(Element element, InspectorWindow source) {
        pinned = source == null ? null : source.fixedFocus;
        focused = pinned == null ? element : pinned;
        focusedWindow = source;
        focusedCell = null;
    }

    private void clearFocus(Element element) {
        if (pinned == null && focused == element) {
            focused = null;
            focusedWindow = null;
            focusedCell = null;
            for (Window window : Window.all) {
                if (window instanceof InspectorWindow inspectorWindow && window.getScene() == scene && window.isShown()
                        && inspectorWindow.fixedFocus != null) {
                    pinned = inspectorWindow.fixedFocus;
                    focused = pinned;
                    focusedWindow = inspectorWindow;
                }
            }
        }
    }

    private void addFocusSource(Element source, arc.func.Prov<Element> target, Cell<?> cell) {
        source.addListener(new InputListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Element fromActor) {
                Element element = target.get();
                if (element != null) {
                    setFocus(element);
                    focusedCell = cell;
                }
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Element toActor) {
                if (toActor == null || !toActor.isDescendantOf(source)) clearFocus(target.get());
            }
        });
    }

    void setDrawPadElem(Element drawPadElem) {
        setDebugBounds(drawPadElem != null);
        this.drawPadElem = drawPadElem;
    }

    void toggleDrawPadElem(Element element) {
        setDrawPadElem(drawPadElem == element ? null : element);
    }

    private String name(Element element) {
        if (element == null) return "<null>";
        if (element == scene.root) return "ROOT";
        Class<?> type = element.getClass();
        if (!anonymousInsteadSuper) {
            while (type.isAnonymousClass() && type.getSuperclass() != null) type = type.getSuperclass();
        }
        StringBuilder out = new StringBuilder(type.getSimpleName());
        if (element instanceof TextButton button && !button.getText().isEmpty()) {
            out.append(": ").append(button.getText());
        } else if (element instanceof Label label && !label.getText().isEmpty()) {
            out.append(": ").append(label.getText());
        }
        if (element.name != null) out.append(" [").append(element.name).append(']');
        return out.toString();
    }

    private String path(Element element) {
        if (element == null) return "";
        Seq<String> parts = new Seq<>();
        Element current = element;
        while (current != null) {
            String part = current == scene.root ? "root" : current.getClass().getSimpleName();
            if (current.name != null) part += "#" + current.name;
            int index = current.getZIndex();
            if (index >= 0) part += "[" + index + "]";
            parts.add(part);
            current = current.parent;
        }
        parts.reverse();
        return parts.toString("/");
    }

    private void copy(String text) {
        Core.app.setClipboardText(text == null ? "" : text);
    }

    private DialogStyle dialogStyle() {
        DialogStyle style = new DialogStyle();
        style.background = ArcStyles.panel;
        style.titleFont = ArcStyles.dialog.titleFont;
        style.titleFontColor = Color.white;
        return style;
    }

    private void styleDialog(Dialog dialog) {
        dialog.setClip(false);
        dialog.titleTable.background(windowTitleBackground());
        dialog.titleTable.margin(0f);
        dialog.titleTable.defaults().size(38f);
        dialog.getCell(dialog.titleTable).height(45f).pad(0f);
        dialog.cont.background(ArcStyles.panel);
        dialog.cont.margin(6f);
        dialog.buttons.background(ArcStyles.panel);
        dialog.buttons.margin(0f);
        dialog.getCell(dialog.buttons).pad(0f);
        installTitleDrag(dialog);
    }

    private Drawable windowTitleBackground() {
        return tintWhite(ArcStyles.accent.cpy().lerp(Color.gray, 0.6f).a(0.9f));
    }

    private Drawable tintWhite(Color color) {
        return ((TextureRegionDrawable) ArcStyles.white).tint(color);
    }

    private Drawable rowBackground(boolean focused) {
        return focused ? rowFlatDown : rowNone;
    }

    private ImageButtonStyle closeButtonStyle() {
        return new ImageButtonStyle(ArcStyles.toolImageButton) {{
            over = tintWhite(ArcStyles.error);
            down = tintWhite(ArcStyles.error.cpy().lerp(Color.gray, 0.3f));
        }};
    }

    private static class InspectorFoldedImageButton extends ImageButton {
        private static final float duration = 0.1f;
        private boolean checked;
        public Element element;
        public Runnable rebuild, _clear;
        public Cell<?> cell;

        InspectorFoldedImageButton(boolean fireOnlyClick) {
            this(fireOnlyClick, ArcStyles.toolImageButton);
        }

        InspectorFoldedImageButton(boolean fireOnlyClick, ImageButtonStyle style) {
            super(new TextDrawable(">", 18f), style);

            clicked(() -> {
                checked = !checked;
                if (fireOnlyClick) {
                    applyChanged();
                }
            });

            setOrigin(Align.center);
            if (!fireOnlyClick) update(this::applyChanged);
            addListener(new InputListener() {
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                    event.stop();
                    return super.touchDown(event, x, y, pointer, button);
                }

                @Override
                public void enter(InputEvent event, float x, float y, int pointer, Element fromActor) {
                    event.stop();
                }
            });
        }

        @Override
        public boolean isChecked() {
            return checked;
        }

        public void fireCheck(boolean checked) {
            fireCheck(checked, true);
        }

        public void fireCheck(boolean checked, boolean fireEvents) {
            if (this.checked == checked && cell != null && cell.hasElement() == checked) return;
            if (isDisabled()) return;

            this.checked = checked;
            if (fireEvents) applyChanged();
            else getImage().rotation = checked ? -90f : 0f;
        }

        private void applyChanged() {
            Image image = getImage();
            if (checked) {
                if (rebuild != null) rebuild.run();
                if (image.rotation != -90f) image.addAction(Actions.rotateTo(-90f, duration));
                if (cell != null) cell.setElement(element);
            } else {
                if (_clear != null) _clear.run();
                if (image.rotation != 0f) image.addAction(Actions.rotateTo(0f, duration));
                if (cell != null) cell.clearElement();
            }
        }

        public void setContainer(Cell<?> cell) {
            this.cell = cell;
            this.element = cell.get();
        }
    }

    private void installTitleDrag(Dialog dialog) {
        dialog.titleTable.touchable = Touchable.enabled;
        dialog.titleTable.addListener(new InputListener() {
            float lastX, lastY;
            boolean dragging;

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                if (button != KeyCode.mouseLeft) return false;
                if (event.targetActor != dialog.titleTable) return false;
                dragging = true;
                lastX = event.stageX;
                lastY = event.stageY;
                dialog.toFront();
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                if (!dragging) return;
                dialog.moveBy(event.stageX - lastX, event.stageY - lastY);
                dialog.keepInStage();
                lastX = event.stageX;
                lastY = event.stageY;
                event.stop();
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
                dragging = false;
            }
        });
    }

    private class Capture extends InputListener {
        @Override
        public boolean keyDown(InputEvent event, KeyCode keycode) {
            if (selecting) {
                if (keycode == KeyCode.escape) {
                    cancelSelect();
                } else if (keycode == KeyCode.f && selected != null) {
                    filter(selected);
                } else if (keycode == KeyCode.p && selected != null && selected.parent != null && selected.parent != scene.root) {
                    selected = selected.parent;
                    focused = selected;
                }
                event.stop();
                return true;
            }

            boolean ctrl = Core.input.ctrl(), shift = Core.input.shift(), alt = Core.input.alt();
            if (keycode == KeyCode.d && ctrl && alt && shift) {
                setDebugBounds(true);
                pick(Element.class, ArcInspector.this::setDrawPadElem);
                event.stop();
                return true;
            } else if (keycode == KeyCode.d && ctrl && alt) {
                setDebugBounds(!debugBounds);
                event.stop();
                return true;
            } else if (keycode == KeyCode.f3) {
                InspectorWindow window = focusedWindow != null && focusedWindow.getScene() == scene
                        ? focusedWindow : Window.front(scene) instanceof InspectorWindow front ? front : null;
                if (window != null && window.nextSearch(shift)) {
                    event.stop();
                    return true;
                }
                return false;
            } else if (keycode == KeyCode.c && ctrl && shift) {
                beginSelect();
                event.stop();
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseMoved(InputEvent event, float x, float y) {
            if (!selecting) return false;
            selectAt(x, y);
            event.stop();
            return true;
        }

        @Override
        public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
            if (!selecting) return false;
            if (locked) return false;
            locked = true;
            scene.root.addChild(mask);
            cancelEvent = true;
            event.cancel();
            cancelEvent = false;
            selectAt(x, y);
            cancelEvent = true;
            return button == KeyCode.mouseLeft;
        }

        @Override
        public void touchDragged(InputEvent event, float x, float y, int pointer) {
            if (selecting) {
                cancelEvent = false;
                selectAt(x, y);
                cancelEvent = true;
                event.stop();
            }
        }

        @Override
        public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
            if (!selecting) return;
            locked = false;
            mask.remove();
            cancelEvent = false;
            Element picked = selected;
            Cons<Element> callback = selectionCallback;
            cancelSelect();
            if (picked != null) {
                if (callback != null) callback.get(picked);
                else inspect(picked);
            }
            event.stop();
        }
    }

    private void setDebugBounds(boolean enabled) {
        debugBounds = enabled;
        Core.settings.put("arcui.inspect.debugBounds", enabled);
    }

    private class InspectorLayer extends Group {
        private final InspectorHoverInfo info = new InspectorHoverInfo();
        private Element lastFocusedElement;

        InspectorLayer() {
            setTransform(false);
            info.visible = false;
            addChild(info);
        }

        @Override
        public void act(float delta) {
            super.act(delta);
            beforeDraw();
        }

        @Override
        public void draw() {
            if (selecting) {
                Draw.color(maskColor);
                Fill.crect(0f, 0f, scene.getWidth(), scene.getHeight());
                Draw.color(Color.valueOf("111111dd"));
                Fill.crect(8f, scene.getHeight() - 34f, 310f, 26f);
                ArcStyles.label.font.draw("Inspect: move mouse, click select, Esc cancel", 14f,
                        scene.getHeight() - 16f, Color.white, 0.75f, false, Align.left);
            }

            Element target = selecting ? selected : pinned == null ? focused : pinned;
            if (target != null) {
                if (focusedCell != null && focusedCell.get() == target) drawCellFocus(focusedCell, target);
                else
                    drawFocus(target, pinned == null ? focusLine : pinnedLine, pinned == null && focusedWindow != null && focusedWindow.drawCell);
            }
            Draw.color();
            super.draw();
            for (Runnable draw : residentDrawTasks.copy()) runChecked(draw::run);
            if (debugBounds && drawPadElem != null && (drawPadElem.parent != null || drawPadElem == scene.root)) {
                Draw.color();
                Draw.alpha(0.7f);
                drawPad(drawPadElem, tmp2);
                Draw.color();
            }
        }

        void updateInfo() {
            Element target = selecting ? selected : pinned == null ? focused : pinned;
            info.visible = hoverInfoWindow && target != null && target.getScene() == scene;
            if (!info.visible) {
                lastFocusedElement = null;
                return;
            }
            if (target != lastFocusedElement) {
                info.updateFor(target, name(target));
                positionTooltip(target, Align.topLeft, info, Align.bottomLeft);
                lastFocusedElement = target;
            }
        }
    }

    private void drawPad(Element elem, Vec2 vec2) {
        if (!drawHiddenPad && !elem.visible) return;
        elem.localToStageCoordinates(vec2.set(0, 0));

        if (checkCullingArea) {
            elem.localToParentCoordinates(tmp1.set(0, 0));
            if (elem.cullable && elem.parent != null && elem.parent.getCullingArea() != null &&
                    !elem.parent.getCullingArea().overlaps(tmp1.x, tmp1.y, elem.getWidth(), elem.getHeight())) return;
        }

        float thick = elem instanceof Group ? 2 : 1;
        Draw.color(elem instanceof Group ? Color.sky : Color.green, 0.9f);
        Lines.stroke(thick);
        dashRect(vec2.x, vec2.y - thick,
                clamp(vec2, elem.getWidth() + thick, true), clamp(vec2, elem.getHeight() + thick, false));

        if (elem instanceof Table table) {
            drawMargin(vec2, table);
            drawPadding(elem, vec2, table);
        }
        if (elem == drawPadElem && elem.parent instanceof Table table) drawPadding(elem, vec2, table);

        if (elem instanceof Group group) {
            float x = vec2.x, y = vec2.y;
            for (Element child : group.getChildren()) drawPad(child, vec2.set(x, y));
        }
    }

    private static float clamp(Vec2 pos, float value, boolean x) {
        return Mathf.clamp(value, 0, x ? pos.x + Core.graphics.getWidth() : pos.y + Core.graphics.getHeight());
    }

    private static void drawPadding(Element elem, Vec2 vec2, Table table) {
        if ((padColor & 0xFF) == 0) return;
        Draw.color(padColor);
        Cell<?> cl = table.getCell(elem);
        if (cl != null) {
            drawMarginOrPad(vec2, elem, true, CellAccess.padLeft(cl), CellAccess.padTop(cl),
                    CellAccess.padRight(cl), CellAccess.padBottom(cl));
        }
        Draw.color();
    }

    private static void drawMargin(Vec2 vec2, Table table) {
        if ((marginColor & 0xFF) == 0) return;
        Draw.color(marginColor);
        drawMarginOrPad(vec2, table, false,
                table.getMarginLeft(), table.getMarginTop(), table.getMarginRight(), table.getMarginBottom());
        Draw.color();
    }

    private static void drawMarginOrPad(Vec2 vec2, Element elem, boolean pad,
                                        float left, float top, float right, float bottom) {
        left = clamp(vec2, left, true);
        top = clamp(vec2, top, false);
        right = clamp(vec2, right, true);
        bottom = clamp(vec2, bottom, false);
        if (pad) {
            left *= -1;
            top *= -1;
            right *= -1;
            bottom *= -1;
        }

        Color color = Tmp.c1.set(pad ? padTextColor : marginTextColor);
        float mul = pad ? -1 : 1;
        float elemX = vec2.x, elemY = vec2.y, elemW = elem.getWidth(), elemH = elem.getHeight();
        float fontH = ArcStyles.label.font.getLineHeight() * 0.6f;
        drawSideIndicator(left, mul, color,
                elemX, elemY, left, elemH, elemX + left / 2f, elemY + (fontH + elemH) / 2f);
        drawSideIndicator(bottom, mul, color,
                elemX, elemY, elemW, bottom, elemX + elemW / 2f, elemY + bottom);
        drawSideIndicator(top, mul, color,
                elemX, elemY + elemH, elemW, -top, elemX + elemW / 2f, elemY + elemH - top / 2f);
        drawSideIndicator(right, mul, color,
                elemX + elemW, elemY, -right, elemH, elemX + elemW - right / 2f, elemY + (fontH + elemH) / 2f);
    }

    private static void drawSideIndicator(float value, float textMultiplier, Color color,
                                          float rectX, float rectY, float rectW, float rectH, float textX, float textY) {
        if (value == 0) return;
        Fill.crect(rectX, rectY, rectW, rectH);
        ArcStyles.label.font.draw(Strings.autoFixed(value * textMultiplier, 1), textX, textY,
                color, 0.6f, false, Align.center);
    }

    private void drawFocus(Element element, Color lineColor) {
        drawFocus(element, lineColor, false);
    }

    private void drawFocus(Element element, Color lineColor, boolean drawCell) {
        if (element == null || element.getScene() != scene) return;
        if (checkCullingArea && element.cullable && element.parent != null && element.parent.getCullingArea() != null) {
            element.localToParentCoordinates(tmp1.set(0f, 0f));
            if (!element.parent.getCullingArea().overlaps(tmp1.x, tmp1.y, element.getWidth(), element.getHeight()))
                return;
        }
        if (drawCell) {
            Cell<?> cell = CellAccess.cell(element);
            if (cell != null) {
                drawCellFocus(cell, element);
                return;
            }
        }

        Group transformParent = transformParent(element);
        boolean restoreTransform = false;
        Gl.flush();
        if (transformParent != null) {
            oldTransform.set(Draw.trans());
            Draw.trans(Reflect.get(Group.class, transformParent, "computedTransform"));
            if (element == transformParent) {
                tmp1.set(0f, 0f);
            } else {
                element.localToAscendantCoordinates(transformParent, tmp1.set(0f, 0f));
            }
            restoreTransform = true;
        } else {
            element.localToStageCoordinates(tmp1.set(0f, 0f));
        }

        try {
            float alpha = focusFill.a * (element.visible ? 0.9f : 0.6f);
            if (alpha > 0f && overlapsStage(tmp1.x, tmp1.y, element.getWidth(), element.getHeight())) {
                Draw.color(focusFill, alpha);
                Fill.crect(tmp1.x, tmp1.y, element.getWidth(), element.getHeight());
            }
            if (!element.visible) {
                ArcStyles.label.font.draw("hidden", Mathf.clamp(tmp1.x, 0f, scene.getWidth() - 72f),
                        Mathf.clamp(tmp1.y + 18f, 18f, scene.getHeight()));
            }
            Draw.color(ArcStyles.accent);
            Lines.stroke(1f);
            dashRect(tmp1.x - 1f, tmp1.y - 1f, element.getWidth() + 1f, element.getHeight() + 1f);
        } finally {
            if (restoreTransform) Draw.trans(oldTransform);
        }
    }

    private void drawCellFocus(Cell<?> cell, Element element) {
        try {
            Table table = cell.getTable();
            table.validate();
            int column = CellAccess.column(cell);
            int row = CellAccess.row(cell);
            if (column < 0 || row < 0 || column >= table.getColumns() || row >= table.getRows()) return;

            float spanW = table.getColumnWidth(column);
            float spanH = table.getRowHeight(row);
            element.localToStageCoordinates(tmp1.set(0f, 0f));
            int align = CellAccess.align(cell);
            float spanX = (align & Align.left) != 0 ? -CellAccess.computedPadLeft(cell) :
                    (align & Align.right) != 0 ? CellAccess.computedPadRight(cell) - spanW + element.getWidth() :
                            (element.getWidth() - spanW - CellAccess.computedPadLeft(cell) + CellAccess.computedPadRight(cell)) / 2f;
            float spanY = (align & Align.top) != 0 ? CellAccess.computedPadTop(cell) - spanH + element.getHeight() :
                    (align & Align.bottom) != 0 ? -CellAccess.computedPadBottom(cell) :
                            (element.getHeight() - spanH - CellAccess.computedPadBottom(cell) + CellAccess.computedPadTop(cell)) / 2f;

            Draw.color(ArcStyles.error);
            Lines.stroke(2f);
            dashRect(tmp1.x + spanX - 1f, tmp1.y + spanY - 1f, spanW + 2f, spanH + 2f);
            Draw.color(Color.royal);
            Lines.stroke(3f);
            dashRect(tmp1.x - 1.5f, tmp1.y - 1.5f, element.getWidth() + 3f, element.getHeight() + 3f);
        } catch (Throwable ignored) {
        }
    }

    private boolean overlapsStage(float x, float y, float width, float height) {
        return x + width >= 0f && y + height >= 0f && x <= scene.getWidth() && y <= scene.getHeight();
    }

    private void dashRect(float x, float y, float width, float height) {
        dashLineBasic(x, y, x + width, y);
        dashLineBasic(x + width, y, x + width, y + height);
        dashLineBasic(x + width, y + height, x, y + height);
        dashLineBasic(x, y + height, x, y);
    }

    private void dashLineBasic(float x, float y, float x2, float y2) {
        int divisions = (int) (Math.max(Math.abs(x - x2), Math.abs(y - y2)) / dashTileSize * 2f);
        Lines.dashLine(x, y, x2, y2, divisions);
    }

    private Group transformParent(Element element) {
        Group transformParent = element instanceof Group ? (Group) element : element.parent;
        while (transformParent != null && !transformParent.isTransform()) {
            transformParent = transformParent.parent;
        }
        if (transformParent != null && (transformParent instanceof ScrollPane || transformParent.parent instanceof ScrollPane)) {
            return null;
        }
        return transformParent;
    }

    private class InspectorDialog extends Window {
        InspectorDialog(String titleText, float minWidth, float minHeight, boolean full) {
            super(ArcInspector.this.scene, titleText, minWidth, minHeight, full);
        }

        InspectorDialog(String titleText, float minWidth, float minHeight, boolean full, boolean noButtons) {
            super(ArcInspector.this.scene, titleText, minWidth, minHeight, full, noButtons);
        }
    }

    void showScriptDialog(Element element) {
        if (element == null) return;
        InspectorDialog window = new DisposableInspectorDialog("JS", 120f, 80f, true);
        window.setModal(false);
        window.setMovable(true);
        window.setResizable(true);
        window.setCentered(false);
        window.closeOnBack();
        window.cont.add(element).grow();
        window.show(scene);
        window.setSize(
                Math.min(Math.max(Scl.scl(240f), window.getPrefWidth()), Math.max(0f, scene.getWidth() - 20f)),
                Math.min(Math.max(Scl.scl(160f), window.getPrefHeight()), Math.max(0f, scene.getHeight() - 20f))
        );
        window.setPosition(Core.input.mouseX(), Core.input.mouseY(), Align.center);
        window.keepInStage();
    }

    private void reportError(Throwable error) {
        InspectorDialog dialog = new DisposableInspectorDialog("Exception", 120f, 80f, false) {
            @Override
            public float getPrefWidth() {
                return Math.min(super.getPrefWidth(), ArcInspector.this.scene.getWidth());
            }

            @Override
            public float getPrefHeight() {
                return Math.min(super.getPrefHeight(), ArcInspector.this.scene.getHeight());
            }
        };
        dialog.setModal(false);
        dialog.setMovable(true);
        dialog.setCentered(false);
        dialog.getCell(dialog.cont).minSize(0f);
        Label message = new Label(InspectorReflection.error(error));
        message.setWrap(true);
        dialog.cont.add(message).width(Math.min(600f, scene.getWidth() - 40f)).color(Color.red).row();
        dialog.cont.pane(table -> table.add(Strings.getStackTrace(error)).left()).maxHeight(300f).grow();
        dialog.closeOnBack();
        dialog.show(scene);
        dialog.setPosition((scene.getWidth() - dialog.getWidth()) / 2f, (scene.getHeight() - dialog.getHeight()) / 2f);
        dialog.keepInStage();
    }

    private class InspectorSettings extends Table {
        InspectorSettings(InspectorWindow window) {
            super(ArcStyles.panel);
            top().left();
            touchable = Touchable.childrenOnly;
            margin(6f);

            CheckBox drawCell = new CheckBox("Draw Cell");
            drawCell.setChecked(window.drawCell);
            drawCell.changed(() -> window.drawCell = drawCell.isChecked());
            add(drawCell).left().height(32f).pad(3f).row();
            image(ArcStyles.white).color(underlineColor).height(1f).growX().pad(4f).row();
            reviewSettings(this, () -> {
                window.title.setText(name(window.rootElement));
                window.rebuild();
            });
            keyDown(key -> {
                if (key == KeyCode.escape || key == KeyCode.back) hideMenu();
            });
        }

        @Override
        public float getPrefWidth() {
            return Math.min(super.getPrefWidth(), scene.getWidth());
        }

        @Override
        public float getPrefHeight() {
            return Math.min(super.getPrefHeight(), scene.getHeight());
        }

        void showAt(float x, float y) {
            hideMenu();
            activeMenu = this;
            activeMenuHitter = new MenuHitter(ArcInspector.this::hideMenu);
            scene.root.addChild(activeMenuHitter);
            scene.root.addChild(this);
            pack();
            setPosition(x, y, Align.topLeft);
            keepInStage();
            requestKeyboard();
        }
    }

    private class InspectorMenu extends Table {
        private static final float itemHeight = 42f;
        private final Table items = new Table(ArcStyles.panel);
        private final Element menuElement;

        InspectorMenu(Element element) {
            menuElement = element;
            top().left();
            touchable = Touchable.childrenOnly;
            items.top().left();
            add(menuPane(items)).grow().pad(0f);
            menuButton("Copy Path", Icons.copy, () -> copy(path(element)));
            disabledMenuButton("Screenshot", Icons.image, () -> true, () -> {
            });
            separator();
            menuButton("Copy as JS", Icons.copy, () -> copy(reflection.store("Element", element)));
            menuButton("Remove...", Icons.delete, () -> confirmRemove(element));
            checkboxMenuButton("Toggle Bounds", Icons.bounds, () -> drawPadElem == element, () -> toggleDrawPadElem(element));
            menuButton("New Window", Icons.copy, () -> new InspectorWindow(element).showWindow());
            menuButton("Details", Icons.details, () -> showJavaObject(element));
            foldedMenu("Exec", element);
            menuButton("Elem Details", Icons.crafting, () -> new ElementDetails(element).showWindow());
            if (element instanceof Table || element != null && element.parent instanceof Table) {
                separator();
                if (element instanceof Table table)
                    menuButton("All Cells", Icons.background, () -> openAllCells(table));
                if (element.parent instanceof Table)
                    disabledMenuButton("This Cell", Icons.background, () -> CellAccess.cell(element) == null, () -> openCell(element));
            }
            addListener(new InputListener() {
                @Override
                public boolean keyDown(InputEvent event, KeyCode keycode) {
                    if (keycode != KeyCode.escape && keycode != KeyCode.back) return false;
                    hideMenu();
                    event.stop();
                    return true;
                }
            });
        }

        @Override
        public float getPrefWidth() {
            return Math.min(super.getPrefWidth(), scene.getWidth());
        }

        @Override
        public float getPrefHeight() {
            return Math.min(super.getPrefHeight(), scene.getHeight());
        }

        private void separator() {
            separator(items);
        }

        private void separator(Table table) {
            table.image(ArcStyles.white).color(underlineColor).height(1f).growX().pad(4f).row();
        }

        private void menuButton(String text, Drawable icon, Runnable run) {
            TextButton button = menuButton(items, text, icon, ArcStyles.toggleTextButton).get();
            button.clicked(() -> {
                hideMenu();
                run.run();
            });
        }

        private void submenuButton(Table table, String text, Runnable run) {
            TextButton button = menuButton(table, text, Icons.box, ArcStyles.toggleTextButton).get();
            button.clicked(() -> {
                hideSubMenu();
                run.run();
            });
        }

        private void disabledMenuButton(String text, Drawable icon, Boolp disabled, Runnable run) {
            Cell<TextButton> cell = menuButton(items, text, icon, ArcStyles.toggleTextButton);
            cell.disabled(button -> disabled.get());
            cell.get().clicked(() -> {
                if (disabled.get()) return;
                hideMenu();
                run.run();
            });
        }

        private void checkboxMenuButton(String text, Drawable icon, Boolp checked, Runnable run) {
            Cell<TextButton> cell = menuButton(items, text, icon, ArcStyles.toggleTextButton);
            cell.update(button -> button.setChecked(checked.get()));
            cell.get().clicked(() -> {
                hideMenu();
                run.run();
            });
        }

        private void foldedMenu(String text, Element element) {
            Cell<TextButton> cell = menuButton(items, text, Icons.box, ArcStyles.toggleTextButton);
            TextButton button = cell.get();
            button.clicked(() -> {
                if (!button.isChecked()) return;
                showSubMenu(button, element);
            });
        }

        private Cell<TextButton> menuButton(Table table, String text, Drawable icon, TextButton.TextButtonStyle style) {
            Cell<TextButton> cell = table.button(text, icon, style, 24f, () -> {
            });
            cell.minSize(Float.NEGATIVE_INFINITY, itemHeight).growX().left().padTop(-1f).marginLeft(5f).marginRight(5f).wrapLabel(false);
            cell.get().getLabelCell().padLeft(8f).labelAlign(Align.left);
            cell.row();
            return cell;
        }

        private void showSubMenu(TextButton anchor, Element element) {
            hideSubMenu();
            activeSubMenuButton = anchor;
            Table subItems = new Table(ArcStyles.panel);
            subItems.top().left();
            activeSubMenu = new Table();
            activeSubMenu.top().left();
            activeSubMenu.touchable = Touchable.childrenOnly;
            activeSubMenu.add(menuPane(subItems)).grow().pad(0f);
            for (MenuEntry entry : execEntries(element)) {
                if (entry == null) separator(subItems);
                else submenuButton(subItems, entry.text, entry.run);
            }
            activeSubMenuHitter = new MenuHitter(ArcInspector.this::hideSubMenu);
            scene.root.addChild(activeSubMenuHitter);
            scene.root.addChild(activeSubMenu);
            activeSubMenu.pack();
            activeSubMenu.update(() -> {
                if (anchor.getScene() != scene) {
                    hideSubMenu();
                    return;
                }
                positionTooltip(anchor, Align.topRight, activeSubMenu, Align.topLeft);
                checkBound(activeSubMenu);
            });
        }

        private Seq<MenuEntry> execEntries(Element element) {
            Seq<MenuEntry> entries = new Seq<>();
            entries.add(new MenuEntry("Invalidate", element::invalidate));
            entries.add(new MenuEntry("InvalidateHierarchy", element::invalidateHierarchy));
            entries.add(new MenuEntry("Layout", element::layout));
            entries.add(new MenuEntry("Pack", element::pack));
            entries.add(new MenuEntry("Validate", element::validate));
            entries.add(new MenuEntry("Keep in stage", element::keepInStage));
            entries.add(new MenuEntry("To Front", element::toFront));
            entries.add(new MenuEntry("To Back", element::toBack));
            boolean hasExtra = element instanceof Table || element instanceof Label;
            if (hasExtra) entries.add((MenuEntry) null);
            if (element instanceof Table table) {
                entries.add(new MenuEntry("Set Background", () -> pickDrawable(table.getBackground(), table::setBackground)));
                entries.add(new MenuEntry("Table Center", () -> alignTable(table, Align.center)));
                entries.add(new MenuEntry("Table Left", () -> alignTable(table, Align.left)));
                entries.add(new MenuEntry("Table Right", () -> alignTable(table, Align.right)));
                entries.add(new MenuEntry("Table Top", () -> alignTable(table, Align.top)));
                entries.add(new MenuEntry("Table Bottom", () -> alignTable(table, Align.bottom)));
            }
            if (element instanceof Label label) {
                entries.add(new MenuEntry("Copy Text", () -> copy(label.getText().toString())));
                entries.add(new MenuEntry("Label Center", () -> label.setAlignment(Align.center)));
                entries.add(new MenuEntry("Label Left", () -> label.setAlignment(Align.left)));
                entries.add(new MenuEntry("Label Right", () -> label.setAlignment(Align.right)));
                entries.add(new MenuEntry("Label Top", () -> label.setAlignment(Align.top)));
                entries.add(new MenuEntry("Label Bottom", () -> label.setAlignment(Align.bottom)));
            }
            if (element instanceof Dialog dialog) {
                entries.add(new MenuEntry("Hide", dialog::hide));
                entries.add(new MenuEntry("Show", dialog::show));
            }
            return entries;
        }

        void showExecAt(float x, float y) {
            InspectorPopup menu = new InspectorPopup();
            for (MenuEntry entry : execEntries(menuElement)) {
                if (entry == null) menu.separator();
                else menu.item(entry.text, Icons.box, entry.run);
            }
            menu.showAt(x, y);
        }

        void showAt(float x, float y) {
            hideMenu();
            activeMenu = this;
            activeMenuHitter = new MenuHitter(ArcInspector.this::hideMenu);
            scene.root.addChild(activeMenuHitter);
            scene.root.addChild(this);
            pack();
            setPosition(x, y, Align.topLeft);
            checkBound(this);
            update(() -> {
                setPosition(x, y, Align.topLeft);
                checkBound(this);
            });
            requestKeyboard();
        }
    }

    private ScrollPane menuPane(Table table) {
        ScrollPane pane = new ScrollPane(table, ArcStyles.scrollPane);
        pane.setScrollingDisabled(true, false);
        pane.setOverscroll(false, false);
        pane.update(() -> pane.setScrollingDisabled(true, Mathf.equal(pane.getHeight(), pane.getPrefHeight())));
        return pane;
    }

    private void checkBound(Table table) {
        if (table.getWidth() > scene.getWidth()) {
            table.setWidth(scene.getWidth());
        }
        if (table.getHeight() > scene.getHeight()) {
            table.setHeight(scene.getHeight());
        }
        table.keepInStage();
        table.invalidateHierarchy();
        table.pack();
    }

    private void positionTooltip(Element lying, int lyingAlign, Table table, int tableAlign) {
        int currentLyingAlign = lyingAlign;
        int currentTableAlign = tableAlign;

        applyPos(lying, currentLyingAlign, table, currentTableAlign);
        if (table.y + table.getHeight() > scene.getHeight() || table.y < 0f) {
            currentLyingAlign = flipV(currentLyingAlign);
            currentTableAlign = flipV(currentTableAlign);
            applyPos(lying, currentLyingAlign, table, currentTableAlign);
        }
        if (table.x + table.getWidth() > scene.getWidth() || table.x < 0f) {
            currentLyingAlign = flipH(currentLyingAlign);
            currentTableAlign = flipH(currentTableAlign);
            applyPos(lying, currentLyingAlign, table, currentTableAlign);
        }

        table.keepInStage();
    }

    private void applyPos(Element lying, int lyingAlign, Table table, int tableAlign) {
        lying.localToStageCoordinates(tmp1.set(
                lying.getX(lyingAlign) - lying.x,
                lying.getY(lyingAlign) - lying.y
        ));
        table.setPosition(tmp1.x, tmp1.y, tableAlign);
    }

    private int flipV(int align) {
        if (Align.isTop(align)) return align & ~Align.top | Align.bottom;
        if (Align.isBottom(align)) return align & ~Align.bottom | Align.top;
        return align;
    }

    private int flipH(int align) {
        if (Align.isLeft(align)) return align & ~Align.left | Align.right;
        if (Align.isRight(align)) return align & ~Align.right | Align.left;
        return align;
    }

    private boolean mouseDown() {
        return Core.input.keyDown(KeyCode.mouseLeft) || Core.input.keyDown(KeyCode.mouseRight) || Core.input.keyDown(KeyCode.mouseMiddle);
    }

    private void alignTable(Table table, int align) {
        table.align(align);
        table.layout();
    }

    public Window showJavaObject(Object object) {
        JavaObjectInfo window = new JavaObjectInfo(object);
        window.showWindow();
        return window;
    }

    public Window showJavaObject(Object object, Class<?> type) {
        JavaObjectInfo window = new JavaObjectInfo(object, type);
        window.showWindow();
        return window;
    }

    private void watch(String title, ValueGetter getter) {
        InspectorDialog window = new DisposableInspectorDialog(title, 220f, 120f, true);
        window.setMovable(true);
        window.setResizable(true);
        window.setCentered(false);
        window.closeOnBack();
        ObjectValue value = new ObjectValue(Object.class, null, getter, null);
        value.update(() -> {
            if (value.autoRefresh) value.refresh();
        });
        window.cont.pane(table -> table.add(value).grow()).grow();
        window.show(scene);
        window.setSize(Math.min(Scl.scl(500f), scene.getWidth() - 20f), Scl.scl(180f));
        window.setPosition(Core.input.mouseX(), Core.input.mouseY(), Align.topLeft);
        window.keepInStage();
    }

    @FunctionalInterface
    private interface ValueGetter {
        Object get() throws Throwable;
    }

    @FunctionalInterface
    private interface ValueSetter {
        void set(Object value) throws Throwable;
    }

    private void preview(Drawable drawable) {
        InspectorDialog window = new DisposableInspectorDialog("img", 220f, 180f, true);
        window.setMovable(true);
        window.setResizable(true);
        window.setCentered(false);
        window.closeOnBack();
        window.cont.add(new Image(drawable)).grow();
        window.show(scene);
        window.setSize(Scl.scl(320f), Scl.scl(260f));
        window.keepInStage();
    }

    private void runChecked(ThrowingRunnable action) {
        try {
            action.run();
        } catch (Throwable error) {
            reportError(error);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Throwable;
    }

    private class InspectorPopup extends Table {
        final Table items = new Table(ArcStyles.panel);

        InspectorPopup() {
            touchable = Touchable.childrenOnly;
            items.top().left().defaults().growX().left();
            add(menuPane(items)).grow();
        }

        @Override
        public float getPrefWidth() {
            return Math.min(super.getPrefWidth(), scene.getWidth());
        }

        @Override
        public float getPrefHeight() {
            return Math.min(super.getPrefHeight(), scene.getHeight());
        }

        void item(String text, Runnable action) {
            item(text, null, action);
        }

        void item(String text, Drawable icon, Runnable action) {
            item(text, icon, () -> false, action);
        }

        void item(String text, Drawable icon, Boolp disabled, Runnable action) {
            Cell<TextButton> cell = items.button(text, icon, ArcStyles.toggleTextButton, icon == null ? 0f : 24f, () -> {
                hideMenu();
                action.run();
            });
            TextButton button = cell.get();
            button.getLabelCell().padLeft(8f).labelAlign(Align.left);
            cell.minHeight(42f).growX().padTop(-1f).marginLeft(5f).marginRight(5f).wrapLabel(false).row();
            button.setDisabled(disabled);
        }

        void check(String text, Boolp checked, Runnable action) {
            CheckBox box = new CheckBox(text);
            box.setProgrammaticChangeEvents(false);
            box.setChecked(checked.get());
            box.update(() -> box.setChecked(checked.get()));
            box.changed(action);
            items.add(box).height(42f).pad(5f).row();
        }

        void check(String text, Drawable icon, Boolp checked, Runnable action) {
            Cell<TextButton> cell = items.button(text, icon, ArcStyles.toggleTextButton, 24f, action)
                    .minHeight(42f).growX().padTop(-1f).marginLeft(5f).marginRight(5f).wrapLabel(false);
            cell.row();
            TextButton button = cell.get();
            button.getLabelCell().padLeft(8f).labelAlign(Align.left);
            button.setProgrammaticChangeEvents(false);
            button.setChecked(checked.get());
            button.update(() -> button.setChecked(checked.get()));
        }

        void separator() {
            items.image(ArcStyles.white).color(underlineColor).height(1f).pad(4f).row();
        }

        void showAt(float x, float y) {
            hideMenu();
            activeMenu = this;
            activeMenuHitter = new MenuHitter(ArcInspector.this::hideMenu);
            scene.add(activeMenuHitter);
            scene.add(this);
            pack();
            setSize(Math.min(getWidth(), scene.getWidth()), Math.min(getHeight(), scene.getHeight()));
            setPosition(x, y, Align.topLeft);
            checkBound(this);
            requestKeyboard();
            keyDown(key -> {
                if (key == KeyCode.escape || key == KeyCode.back) hideMenu();
            });
        }
    }

    private void contextMenu(Element target, arc.func.Prov<InspectorPopup> builder) {
        target.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                if (button != KeyCode.mouseRight) return false;
                builder.get().showAt(Core.input.mouseX(), Core.input.mouseY());
                event.stop();
                return true;
            }
        });
    }

    private void requestValue(String title, String initial, Object self, String binding, Object bound, ValueSetter callback) {
        new JavaRequest(title, initial, self, binding, bound, callback).showWindow();
    }

    private class DisposableInspectorDialog extends InspectorDialog implements Window.IDisposable {
        DisposableInspectorDialog(String titleText, float minWidth, float minHeight, boolean full) {
            super(titleText, minWidth, minHeight, full);
        }

        DisposableInspectorDialog(String titleText, float minWidth, float minHeight, boolean full, boolean noButtons) {
            super(titleText, minWidth, minHeight, full, noButtons);
        }
    }

    private class InspectorWindow extends DisposableInspectorDialog {
        private final ObjectSet<Element> expanded = new ObjectSet<>();
        private final ObjectSet<Element> collapsed = new ObjectSet<>();
        private final ObjectMap<Element, ElementWrap> wraps = new ObjectMap<>();
        private Element rootElement;
        private Element fixedFocus;
        private final Table tree = new Table();
        private final TextField search = new TextField("");
        private Pattern pattern;
        private boolean drawCell;
        private ScrollPane pane;
        private boolean pendingSearch;
        private final Seq<Element> searchResults = new Seq<>();
        private int searchIndex;

        InspectorWindow(Element element) {
            super(name(element), 20f, 160f, true);
            this.rootElement = element;
            setModal(false);
            setMovable(true);
            setResizable(true);
            setCentered(false);
            closeOnBack();
            build();
        }

        @Override
        public void clearAll() {
            if (ArcInspector.this.frontWindow == this) ArcInspector.this.frontWindow = null;
            if (focusedWindow == this) {
                pinned = null;
                clearFocus(focused);
            }
            super.clearAll();
        }

        void showWindow() {
            show(scene);
            float maxWidth = Math.max(0f, scene.getWidth() - 20f);
            float maxHeight = Math.max(0f, scene.getHeight() - 20f);
            setSize(Math.min(Math.max(Scl.scl(520f), getPrefWidth()), maxWidth), Math.min(Scl.scl(420f), maxHeight));
            setPosition(10f, Math.max(10f, scene.getHeight() - getHeight() - 10f));
            ArcInspector.this.frontWindow = this;
        }

        @Override
        public void activate() {
            super.activate();
            ArcInspector.this.frontWindow = this;
        }

        private void build() {
            cont.clearChildren();
            cont.top().left();
            cont.defaults().left().top().pad(3f);

            search.setMessageText("search");
            search.changed(this::rebuild);
            cont.table(t -> {
                t.left();
                t.button("Parent", Icons.up, ArcStyles.textButton, 24f, () -> {
                    if (rootElement.parent == null) return;
                    if (rootElement.parent == scene.root) {
                        confirmRootParent(() -> setRootElement(rootElement.parent));
                    } else {
                        setRootElement(rootElement.parent);
                    }
                }).disabled(button -> rootElement.parent == null).with(button -> button.getLabel().setFontScale(0.9f)).size(130f, 35f).padLeft(3f);
                t.button(Icons.copy, ArcStyles.toolImageButton, () -> {
                    InspectorWindow copy = new InspectorWindow(rootElement);
                    copy.search.setText(search.getText());
                    copy.rebuild();
                    copy.drawCell = drawCell;
                    copy.showWindow();
                    copy.setSize(getWidth(), getHeight());
                    copy.keepInStage();
                }).size(35f).padLeft(3f).tooltip("Copy");
                t.button(Icons.refresh, ArcStyles.toolImageButton, this::rebuild).size(35f).padLeft(3f).tooltip("Refresh");
                t.button(Icons.settings, ArcStyles.toolImageButton, () -> new InspectorSettings(this).showAt(Core.input.mouseX(), Core.input.mouseY())).size(35f).padLeft(3f).tooltip("Settings");
                t.table(searchTable -> {
                    searchTable.image(Icons.zoom).size(24f).pad(5.5f);
                    searchTable.add(search).growX();
                }).growX().height(35f).padLeft(6f);
            }).growX().row();

            tree.left().top().defaults().left().top().growX();
            pane = new ScrollPane(tree, ArcStyles.scrollPane);
            cont.add(pane).grow().minHeight(140f);
            search.keyDown(key -> {
                if (key == KeyCode.enter && autoScrollToSearch) nextSearch(Core.input.shift());
            });
            search.addListener(new InputListener() {
                @Override
                public void enter(InputEvent event, float x, float y, int pointer, Element fromActor) {
                    focusedWindow = InspectorWindow.this;
                }
            });
            rebuild();
        }

        @Override
        public boolean focusSearch(boolean clear) {
            if (clear) search.setText("");
            focusedWindow = this;
            search.requestKeyboard();
            search.setCursorPosition(Integer.MAX_VALUE);
            return true;
        }

        private boolean nextSearch(boolean previous) {
            if (pattern == null) return false;
            boolean alreadySelected = !searchResults.isEmpty();
            collectSearchResults();
            if (searchResults.isEmpty()) return true;
            searchIndex = alreadySelected
                    ? Mathf.mod(searchIndex + (previous ? -1 : 1), searchResults.size)
                    : previous ? searchResults.size - 1 : 0;
            pendingSearch = true;
            return true;
        }

        private void collectSearchResults() {
            searchResults.clear();
            tree.forEach(row -> {
                if (row.userObject instanceof Element element && matches(element)) searchResults.add(row);
            });
        }

        private void setRootElement(Element element) {
            if (element == null) return;
            rootElement = element;
            wraps.clear();
            fixedFocus = null;
            title.setText(name(element));
            rebuild();
        }

        private void rebuild() {
            String text = search.getText();
            pattern = text == null || text.isEmpty() ? null : compile(text);
            searchResults.clear();
            searchIndex = 0;
            pendingSearch = autoScrollToSearch && pattern != null;
            tree.clearChildren();
            tree.left().top().defaults().left().top().growX();
            buildWrap(tree, rootElement, 1);
        }

        private Pattern compile(String text) {
            try {
                return Pattern.compile(text, Pattern.CASE_INSENSITIVE);
            } catch (PatternSyntaxException ignored) {
                return Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE);
            }
        }

        private boolean matches(Element element) {
            return pattern == null || pattern.matcher(name(element)).find() || pattern.matcher(path(element)).find();
        }

        private void buildWrap(Table parent, Element element, int depth) {
            if (element == null || (element != rootElement && isInspectorElement(element))) return;
            ElementWrap wrap = wraps.get(element);
            if (wrap == null || wrap.depth != depth) {
                wrap = new ElementWrap(element, depth);
                wraps.put(element, wrap);
            }
            parent.add(wrap).growX().left().row();
        }

        private void forgetWrap(Element element) {
            wraps.remove(element);
            if (element instanceof Group group) {
                for (Element child : group.getChildren()) forgetWrap(child);
            }
        }

        private boolean isOpen(Element element, int depth) {
            if (!(element instanceof Group group) || !hasTreeChildren(group) || collapsed.contains(element))
                return false;
            return expanded.contains(element) || autoOpen(group, depth);
        }

        private boolean autoOpen(Group group, int depth) {
            if (depth > maxDepthForAutoExpand) return false;
            int childCount = treeChildCount(group);
            boolean onlyChild = group.parent != null && treeChildCount(group.parent) == 1;
            return childCount < 20 || onlyChild || group == rootElement;
        }

        private boolean hasTreeChildren(Group group) {
            return treeChildCount(group) > 0;
        }

        private int treeChildCount(Group group) {
            int count = 0;
            for (Element child : group.getChildren()) {
                if (!isInspectorElement(child)) count++;
            }
            return count;
        }

        private void underline(Table table, int colspan) {
            table.row();
            table.image(ArcStyles.white).color(underlineColor).height(1.2f).growX().colspan(colspan);
            table.row();
        }

        private Table elementLine(Element element) {
            Table row = new Table();
            row.left().defaults().left().padRight(4f);
            row.touchable = Touchable.enabled;
            row.userObject = element;
            row.background(rowBackground(false));
            row.update(() -> row.background(rowBackground(focused == element || fixedFocus == element)));

            Label nameLabel = new Label(name(element));
            nameLabel.setEllipsis(true);
            nameLabel.update(() -> nameLabel.setColor(pattern != null && matches(element) ? ArcStyles.accent : Color.white));
            row.add(nameLabel).growX().left().minWidth(0f);
            row.add(positionText(element)).color(Color.lightGray).fontScale(0.7f).padLeft(4f).padRight(4f);
            if (element instanceof Image image) {
                PreviewUtils.addPreviewButton(row, preview -> {
                    Drawable drawable = image.getDrawable();
                    if (drawable != null) {
                        preview.image(drawable).size(100f).row();
                        preview.add(drawable.getClass().getSimpleName()).pad(4f);
                    }
                }).get().clicked(() -> pickDrawable(image.getDrawable(), image::setDrawable));
            }

            row.addListener(new InputListener() {
                @Override
                public void enter(InputEvent event, float x, float y, int pointer, Element fromActor) {
                    if (!scene.hasField()) row.requestKeyboard();
                    setFocus(element, InspectorWindow.this);
                }

                @Override
                public void exit(InputEvent event, float x, float y, int pointer, Element toActor) {
                    if (toActor != null && toActor.isDescendantOf(row)) return;
                    clearFocus(element);
                }

                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                    if (button == KeyCode.mouseRight) {
                        new InspectorMenu(element).showAt(Core.input.mouseX(), Core.input.mouseY());
                        event.stop();
                        return true;
                    }
                    setFocus(element, InspectorWindow.this);
                    return false;
                }

                @Override
                public boolean keyDown(InputEvent event, KeyCode keycode) {
                    if (keycode == KeyCode.f) {
                        fixedFocus = fixedFocus == element ? null : element;
                        setFocus(element, InspectorWindow.this);
                        return true;
                    } else if (keycode == KeyCode.i) {
                        showJavaObject(element);
                        return true;
                    } else if (keycode == KeyCode.c) {
                        openCell(element);
                        return true;
                    } else if (keycode == KeyCode.r) {
                        new InspectorMenu(element).showExecAt(Core.input.mouseX(), Core.input.mouseY());
                        return true;
                    } else if (keycode == KeyCode.p && element instanceof Image image) {
                        pickDrawable(image.getDrawable(), image::setDrawable);
                        return true;
                    } else if (keycode == KeyCode.del) {
                        if (Core.input.shift()) removeElement(element);
                        else confirmRemove(element);
                        return true;
                    } else if (keycode == KeyCode.left && element instanceof Group) {
                        collapsed.add(element);
                        expanded.remove(element);
                        rebuild();
                        return true;
                    } else if (keycode == KeyCode.right && element instanceof Group) {
                        collapsed.remove(element);
                        expanded.add(element);
                        rebuild();
                        return true;
                    }
                    return false;
                }
            });
            row.clicked(listener -> listener.setTapCountInterval(0.35f), listener -> {
                row.requestKeyboard();
                if (listener.getTapCount() == 2) copy(reflection.store("Element", element));
            });
            return row;
        }

        @Override
        public void act(float delta) {
            super.act(delta);
            if (!pendingSearch) return;
            collectSearchResults();
            if (searchResults.isEmpty()) {
                pendingSearch = false;
                return;
            }
            validate();
            pane.validate();
            tree.validate();
            tree.forEach(Element::validate);
            Element target = searchResults.get(Math.min(searchIndex, searchResults.size - 1));
            if (target.getScene() != scene) return;
            target.localToAscendantCoordinates(tree, tmp1.set(target.getWidth() / 2f, target.getHeight() / 2f));
            pane.scrollTo(0f, tmp1.y, target.getWidth(), target.getHeight(), false, true);
            pane.updateVisualScroll();
            pendingSearch = false;
        }

        private class ElementWrap extends Table {
            private final Element element;
            private final int depth;
            private final Table children = new Table();
            private InspectorFoldedImageButton fold;
            private Group group;
            private final Seq<Element> snapshot = new Seq<>();

            ElementWrap(Element element, int depth) {
                this.element = element;
                this.depth = depth;
                left().defaults().left();
                touchable = Touchable.enabled;

                if (element instanceof Group group) {
                    buildGroup(group);
                } else {
                    add(elementLine(element)).growX().left();
                    underline(this, 2);
                }
            }

            private void buildGroup(Group group) {
                this.group = group;
                fold = new InspectorFoldedImageButton(true);
                fold.setDisabled(() -> !hasTreeChildren(group));

                add(fold).size(32f);
                add(elementLine(element)).growX().left();
                underline(this, 2);

                Image guide = new Image(ArcStyles.white);
                guide.update(() -> guide.setColor(focused == element || fixedFocus == element ? ArcStyles.accent : Color.darkGray));
                add(guide).width(2f).growY().left();
                children.left().defaults().left().growX();
                boolean open = isOpen(element, depth);
                fold.fireCheck(open);
                if (open) rebuildChildren();
                Cell<?> childCell = add(children).growX().left();
                row();
                fold.setContainer(childCell);
                fold.rebuild = () -> {
                    setOpenState(true);
                    rebuildChildren();
                };
                fold._clear = () -> setOpenState(false);
            }

            private void setOpenState(boolean open) {
                if (open) {
                    collapsed.remove(element);
                    expanded.add(element);
                } else {
                    collapsed.add(element);
                    expanded.remove(element);
                }
            }

            private void rebuildChildren() {
                children.clearChildren();
                children.left().defaults().left().growX();
                if (group != null) {
                    for (Element previous : snapshot) if (previous.parent == null) forgetWrap(previous);
                    snapshot.clear();
                    for (Element child : group.getChildren()) {
                        if (isInspectorElement(child)) continue;
                        snapshot.add(child);
                        buildWrap(children, child, depth + 1);
                    }
                }
                children.invalidateHierarchy();
            }

            @Override
            public void act(float delta) {
                super.act(delta);
                if (group == null || !fold.isChecked()) return;
                Seq<Element> current = group.getChildren();
                int treeIndex = 0;
                boolean changed = false;
                for (Element child : current) {
                    if (isInspectorElement(child)) continue;
                    if (treeIndex >= snapshot.size || child != snapshot.get(treeIndex++)) {
                        changed = true;
                        break;
                    }
                }
                if (!changed) changed = treeIndex != snapshot.size;
                if (changed) rebuildChildren();
            }
        }

        @Override
        public Element hit(float x, float y, boolean touchable) {
            Element hit = super.hit(x, y, touchable);
            if (hit != null && hit.isDescendantOf(this)) ArcInspector.this.frontWindow = this;
            return hit;
        }
    }

    private class ObjectValue extends Table {
        final Class<?> type;
        final ValueGetter getter;
        final ValueSetter setter;
        final Object self;
        final Label label = new NoMarkupLabel("");
        Object value;
        Throwable error;
        boolean assigned, autoRefresh = true;
        final Runnable afterSet = () -> {
        };
        Runnable onClear = () -> {
        };
        String unassignedText = "<unset>";

        ObjectValue(Class<?> type, Object self, ValueGetter getter, ValueSetter setter) {
            this.type = type;
            this.self = self;
            this.getter = getter;
            this.setter = setter;
            left().defaults().left();
            label.setWrap(true, true);
            label.setAlignment(Align.topLeft);
            add(label).minWidth(64f).growX().top();
            if (Drawable.class.isAssignableFrom(type) || arc.graphics.Texture.class.isAssignableFrom(type)
                    || arc.graphics.g2d.TextureRegion.class.isAssignableFrom(type)) {
                PreviewUtils.addPreviewButton(this, preview -> {
                    Drawable drawable = previewDrawable();
                    if (drawable != null) preview.image(drawable).size(100f);
                    else preview.add("null");
                }).get().clicked(() -> {
                    Drawable drawable = previewDrawable();
                    if (drawable == null) return;
                    if (setter != null && Drawable.class.isAssignableFrom(type)) {
                        pickDrawable(drawable, picked -> runChecked(() -> write(picked)));
                    } else preview(drawable);
                });
            }
            if (getter != null) refresh();
            else label.setText("");
            contextMenu(label, this::menu);
        }

        private Drawable previewDrawable() {
            if (value instanceof Drawable drawable) return drawable;
            if (value instanceof arc.graphics.Texture texture) return new TextureRegionDrawable(Draw.wrap(texture));
            if (value instanceof arc.graphics.g2d.TextureRegion region) return new TextureRegionDrawable(region);
            return null;
        }

        void set(Object value) {
            this.value = value;
            this.error = null;
            assigned = true;
            label.setColor(Color.white);
            label.setText(InspectorReflection.describe(value));
        }

        void resetValue() {
            value = null;
            assigned = false;
            error = null;
            label.setText(unassignedText);
            label.setColor(Color.white);
        }

        void fail(Throwable error) {
            this.error = error;
            assigned = true;
            label.setColor(Color.red);
            label.setText("<" + InspectorReflection.error(error) + ">");
        }

        void refresh() {
            try {
                set(getter.get());
            } catch (Throwable error) {
                fail(error);
            }
        }

        void write(Object value) throws Throwable {
            if (setter == null) throw new IllegalStateException("Read-only value");
            setter.set(InspectorReflection.convert(value, type));
            if (getter != null) refresh();
            else set(value);
            afterSet.run();
        }

        InspectorPopup menu() {
            InspectorPopup menu = new InspectorPopup();
            if (value instanceof Element element) {
                menu.item("Inspector", Icons.zoom, () -> inspect(element));
                menu.item("Elem Details", Icons.crafting, () -> new ElementDetails(element).showWindow());
            }
            if (value instanceof Cell<?> cell)
                menu.item("Cell details", Icons.details, () -> new CellDetails(cell).showWindow());
            if (value instanceof Drawable drawable) menu.item("img", Icons.image, () -> preview(drawable));
            if (value instanceof arc.graphics.g2d.TextureRegion region)
                menu.item("img", Icons.image, () -> preview(new arc.scene.style.TextureRegionDrawable(region)));
            if (value instanceof arc.graphics.Texture texture)
                menu.item("img", Icons.image, () -> preview(new arc.scene.style.TextureRegionDrawable(new arc.graphics.g2d.TextureRegion(texture))));
            if (setter != null && Drawable.class.isAssignableFrom(type))
                menu.item("Pick Drawable", Icons.edit, () -> pickDrawable(value instanceof Drawable drawable ? drawable : null, drawable -> runChecked(() -> write(drawable))));
            if (setter != null && Element.class.isAssignableFrom(type)) {
                menu.item("Select and Replace", Icons.edit, () -> selecting, () -> pick(type.asSubclass(Element.class), element -> runChecked(() -> write(element))));
            }
            menu.separator();
            menu.item("Details", Icons.details, () -> type.isPrimitive() && value == null, () -> {
                if (value instanceof Class<?> clazz) showJavaObject(null, clazz);
                else showJavaObject(value, value != null ? value.getClass() : type);
            });
            if (setter != null)
                menu.item("Set", Icons.edit, () -> requestValue(type.getTypeName(), literal(value), self, "p1", value, this::write));
            if (getter != null) {
                menu.item("Refresh", Icons.refresh, this::refresh);
                menu.check("Auto Refresh", Icons.refresh, () -> autoRefresh, () -> autoRefresh = !autoRefresh);
                menu.item("Watch", Icons.watch, () -> watch(type.getTypeName(), getter));
            } else {
                menu.item("Clear", Icons.eraser, () -> {
                    resetValue();
                    onClear.run();
                });
            }
            menu.item("Copy as JS", Icons.copy, () -> copy(reflection.store("value", value)));
            menu.item("Copy", Icons.copy, () -> copy(value instanceof String text ? text : InspectorReflection.describe(value)));
            if (error != null) menu.item("Exception", Icons.details, () -> reportError(error));
            return menu;
        }
    }

    private String literal(Object value) {
        if (value == null) return "null";
        if (value instanceof String || value instanceof Character)
            return "\"" + org.mozilla.javascript.ScriptRuntime.escapeString(String.valueOf(value), '"') + "\"";
        if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
        return "p1";
    }

    private void pickDrawable(Drawable current, Cons<Drawable> callback) {
        InspectorPopup menu = new InspectorPopup();
        menu.item("null", () -> callback.get(null));
        for (Field field : ArcStyles.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !Drawable.class.isAssignableFrom(field.getType())) continue;
            runChecked(() -> {
                Drawable drawable = (Drawable) field.get(null);
                menu.item(field.getName(), () -> callback.get(drawable));
            });
        }
        menu.separator();
        menu.item("Set", Icons.edit, () -> requestValue("Drawable", "p1", null, "p1", current, result -> callback.get((Drawable) InspectorReflection.convert(result, Drawable.class))));
        menu.showAt(Core.input.mouseX(), Core.input.mouseY());
    }

    private class TesterWindow extends InspectorDialog {
        final InspectorTester tester = new InspectorTester(reflection.scripts, testerFiles);
        final TesterEditor editor = new TesterEditor(reflection.scripts);
        final Table logs = new Table();
        final ObjectValue value = new ObjectValue(Object.class, null, null, null);
        final Cell<Table> infoCell;
        InspectorDialog historyWindow, bookmarkWindow;
        long revision = -1;
        int logCount = -1;
        boolean shown, suppressTyped;

        TesterWindow() {
            super("Tester", 420f, 280f, true);
            name = "arc-tester";
            setMovable(true);
            setResizable(true);
            setCentered(false);
            tester.onError = ArcInspector.this::reportError;
            tester.deferMainExecution = true;
            value.name = "tester-result";
            value.label.name = "tester-result-text";
            value.unassignedText = "";
            logs.name = "tester-logs";
            value.onClear = tester::clearResult;
            contextMenu(editor.area, this::testerMenu);
            cont.margin(0f);
            Table body = new Table();
            body.add(editor).grow().minHeight(80f).row();
            Table center = new Table();
            center.name = "tester-actions";
            Table actions = new Table();
            actions.name = "tester-action-buttons";
            actions.defaults().size(42f).padRight(4f);
            iconButton(actions, Icons.move, "Move cursor", this::moveMenu);
            actions.button("OK", ArcStyles.textButton, () -> runChecked(() -> tester.run(editor.area.getText())))
                    .width(64f).name("tester-run").tooltip("Run (Ctrl+Shift+Enter)").disabled(button -> !tester.finished);
            iconButton(actions, Icons.copy, "Copy", editor.area::copy);
            iconButton(actions, Icons.paste, "Paste", () -> editor.area.paste(Core.app.getClipboardText(), true));
            iconButton(actions, Icons.watch, "Watch", () -> runChecked(() -> {
                var getter = tester.watch(editor.area.getText());
                watch(editor.area.getText(), getter::get);
            }));
            center.add(actions);
            body.add(center).growX().row();
            Table info = new Table(ArcStyles.panel);
            Table output = new Table();
            output.top().left();
            output.add(logs).growX().colspan(2).row();
            output.image(Icons.left).color(Color.gray).size(24f).top();
            output.add(value).growX().top();
            info.add(new ScrollPane(output)).grow().with(pane -> pane.setScrollingDisabled(true, false));
            infoCell = body.add(info).growX().height(64f).padLeft(8f);
            body.row();
            center.addListener(new InputListener() {
                float startY, startHeight;

                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                    if (button != KeyCode.mouseLeft || event.targetActor != center) return false;
                    startY = event.stageY;
                    startHeight = info.getHeight();
                    return true;
                }

                @Override
                public void touchDragged(InputEvent event, float x, float y, int pointer) {
                    infoCell.height(Mathf.clamp(startHeight + event.stageY - startY, 32f, Math.max(32f, body.getHeight() - 120f)));
                    body.invalidate();
                }
            });
            ScrollPane bodyPane = new ScrollPane(body);
            bodyPane.name = "tester-body";
            bodyPane.setScrollingDisabled(true, true);
            cont.add(bodyPane).grow().row();
            buildBottom();
            addGlobalShortcutListener(new InputListener() {
                @Override
                public boolean keyDown(InputEvent event, KeyCode key) {
                    suppressTyped = false;
                    if (Core.input.ctrl() && Core.input.shift() && key == KeyCode.enter) {
                        runChecked(() -> tester.run(editor.area.getText()));
                    } else if (Core.input.ctrl() && Core.input.shift() && Core.input.alt() && (key == KeyCode.up || key == KeyCode.down)) {
                        runChecked(() -> rollHistory(key == KeyCode.up));
                    } else if (Core.input.ctrl() && Core.input.shift() && key == KeyCode.d) {
                        showResult();
                    } else if (Core.input.alt() && key == KeyCode.v) {
                        viewResult();
                    } else return false;
                    suppressTyped = true;
                    event.cancel();
                    return true;
                }

                @Override
                public boolean keyTyped(InputEvent event, char character) {
                    if (!suppressTyped) return false;
                    event.cancel();
                    return true;
                }

                @Override
                public boolean keyUp(InputEvent event, KeyCode key) {
                    boolean consumed = suppressTyped;
                    suppressTyped = false;
                    if (consumed) event.cancel();
                    return consumed;
                }
            });
            update(this::refresh);
        }

        void buildBottom() {
            InspectorFoldedImageButton folder = new InspectorFoldedImageButton(true, ArcStyles.toolImageButton);
            folder.name = "tester-tools-fold";
            Table tools = new Table();
            ScrollPane pane = new ScrollPane(tools) {
                @Override
                public float getPrefWidth() {
                    return 420f * Scl.scl();
                }

                @Override
                public float getPrefHeight() {
                    return 56f;
                }

                @Override
                public float getMinHeight() {
                    return 56f;
                }
            };
            pane.name = "tester-tools";
            pane.setScrollingDisabled(false, true);
            folder.rebuild = () -> {
                tools.clearChildren();
                tools.defaults().size(45f).padLeft(2f);
                iconButton(tools, Icons.star, "Add bookmark", () -> askText("Bookmark", "", name -> testerFiles.bookmark(name, editor.area.getText())), 26f);
                toggle(tools, Icons.loop, "Loop", "Not looping", () -> tester.loop, () -> tester.loop = !tester.loop);
                toggle(tools, Icons.strict, "Strict", "Not strict", () -> tester.strict, () -> tester.strict = !tester.strict);
                toggle(tools, Icons.highlight, "Highlighting", "Normal", () -> tester.highlight, () -> tester.highlight = !tester.highlight);
                iconButton(tools, Icons.details, "Result details (Ctrl+Shift+D)", this::showResult, 26f).disabled(button -> !tester.assigned);
                iconButton(tools, Icons.history, "History (Ctrl+Alt+Shift+Up / Down; swipe up for newer, down for older)", () -> files(false), 26f)
                        .get().addCaptureListener(new ElementGestureListener() {
                            @Override
                            public void touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                                pane.setCancelTouchFocus(false);
                            }

                            @Override
                            public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
                                pane.setCancelTouchFocus(true);
                            }

                            @Override
                            public void fling(InputEvent event, float velocityX, float velocityY, KeyCode button) {
                                if (velocityY > 10f) {
                                    runChecked(() -> rollHistory(false));
                                }
                                if (velocityY < -10f) {
                                    runChecked(() -> rollHistory(true));
                                }
                            }
                        });
                iconButton(tools, Icons.bookmarks, "Bookmarks", () -> files(true), 26f);
                toggle(tools, Icons.timeout, "Stop on timeout", "Never stop", () -> tester.stopIfOvertime, () -> tester.stopIfOvertime = !tester.stopIfOvertime);
                toggle(tools, Icons.background, "Multi Thread", "Main Thread", () -> tester.multiThread, () -> {
                    if (tester.finished) tester.multiThread = !tester.multiThread;
                }).disabled(button -> !tester.finished);
            };
            folder.setContainer(cont.add(pane).growX().padLeft(6f));
            Table folderContainer = new Table();
            folderContainer.left().bottom().add(folder).size(36f);
            folderContainer.setFillParent(true);
            folderContainer.update(() -> folderContainer.y = folder.cell.hasElement() ? pane.getHeight() : 0f);
            cont.addChild(folderContainer);
            cont.row();
            folder.fireCheck(true);
        }

        Cell<ImageButton> iconButton(Table table, Drawable icon, String tooltip, Runnable action) {
            return iconButton(table, icon, tooltip, action, 24f);
        }

        Cell<ImageButton> iconButton(Table table, Drawable icon, String tooltip, Runnable action, float iconSize) {
            return table.button(icon, ArcStyles.toolImageButton, iconSize, action).name("tester-" + icon).tooltip(tooltip);
        }

        Cell<ImageButton> toggle(Table table, Drawable icon, String enabledTip, String disabledTip, Boolp checked, Runnable action) {
            Cell<ImageButton> cell = table.button(icon, new ImageButtonStyle(ArcStyles.toolImageButton), 26f, action)
                    .name("tester-" + icon).tooltip(tip -> {
                        tip.background(ArcStyles.overlay).margin(6f);
                        tip.add(checked.get() ? enabledTip : disabledTip)
                                .update(label -> label.setText(checked.get() ? enabledTip : disabledTip));
                    });
            ImageButton button = cell.get();
            button.setProgrammaticChangeEvents(false);
            button.update(() -> {
                button.setChecked(checked.get());
                button.getStyle().imageUpColor = checked.get() ? Color.white : Color.gray;
            });
            return cell;
        }

        void refresh() {
            editor.highlight = tester.highlight;
            editor.properties = testerFiles.enabled("js_prop");
            editor.autoComplete = editor.properties && testerFiles.enabled("auto_complement");
            if (logCount != tester.messageCount() || revision != tester.revision) {
                logs.clearChildren();
                logs.top().left().defaults().left().top();
                for (InspectorScripts.Message item : tester.messages()) {
                    Color color = switch (item.level()) {
                        case debug -> Color.green;
                        case info -> Color.royal;
                        case warn -> Color.yellow;
                        case err -> Color.scarlet;
                        default -> Color.white;
                    };
                    logs.add("[" + switch (item.level()) {
                        case debug -> "D";
                        case info -> "I";
                        case warn -> "W";
                        case err -> "E";
                        default -> " ";
                    } + "]").color(color);
                    Label text = new NoMarkupLabel(item.text());
                    text.setWrap(true, true);
                    text.setAlignment(Align.left);
                    logs.add(text).growX().row();
                }
                logCount = tester.messageCount();
            }
            if (revision == tester.revision) return;
            revision = tester.revision;
            if (tester.error != null) {
                value.fail(tester.error);
                value.label.setText(tester.log);
            } else if (tester.assigned) {
                value.set(tester.result);
            } else {
                value.resetValue();
                value.label.setText(tester.log);
            }
        }

        void showResult() {
            if (!tester.assigned) return;
            if (tester.result instanceof Class<?> type) showJavaObject(null, type);
            else showJavaObject(tester.result);
        }

        void viewResult() {
            if (tester.result instanceof Element element) inspect(element);
            else if (tester.result instanceof Drawable drawable) preview(drawable);
            else if (tester.result instanceof arc.graphics.g2d.TextureRegion region)
                preview(new arc.scene.style.TextureRegionDrawable(region));
            else if (tester.result instanceof arc.graphics.Texture texture)
                preview(new arc.scene.style.TextureRegionDrawable(new arc.graphics.g2d.TextureRegion(texture)));
            else if (tester.result instanceof String text) {
                InspectorDialog view = new DisposableInspectorDialog("Text", 220f, 180f, true);
                Label label = new NoMarkupLabel(text);
                label.setWrap(true, true);
                view.cont.pane(table -> table.add(label).growX()).grow();
                showAux(view, 500f, 300f);
            } else if (tester.result instanceof Color color) preview(tintWhite(color));
        }

        void moveMenu() {
            InspectorPopup popup = new InspectorPopup();
            popup.name = "tester-cursor-pad";
            popup.items.defaults().size(42f);
            popup.items.add();
            iconButton(popup.items, Icons.up, "Up", () -> editor.area.move(KeyCode.up));
            popup.items.add().row();
            iconButton(popup.items, Icons.left, "Left", () -> editor.area.move(KeyCode.left));
            popup.items.add();
            iconButton(popup.items, Icons.right, "Right", () -> editor.area.move(KeyCode.right)).row();
            popup.items.add();
            iconButton(popup.items, Icons.down, "Down", () -> editor.area.move(KeyCode.down));
            popup.items.add();
            popup.showAt(Core.input.mouseX(), Core.input.mouseY());
        }

        void rollHistory(boolean forward) {
            int previous = tester.historyIndex;
            editor.area.setText(tester.rollHistory(editor.area.getText(), forward));
            if (previous == tester.historyIndex) return;
            int count = testerFiles.histories().size();
            boolean wrapped = testerFiles.enabled("rollback_history") && (forward ? previous == count - 1 : previous == -1);
            Table hint = new Table(ArcStyles.overlay);
            hint.name = "tester-history-status";
            hint.touchable = Touchable.disabled;
            hint.margin(6f).add((tester.historyIndex + 1) + "/" + count + (wrapped ? "  Rolled back" : ""));
            scene.add(hint);
            hint.pack();
            hint.setPosition(x, y + 20f);
            hint.actions(Actions.sequence(Actions.delay(1.5f), Actions.fadeOut(0.3f), Actions.remove()));
        }

        InspectorPopup testerMenu() {
            InspectorPopup popup = new InspectorPopup();
            popup.item("Stop", Icons.stop, tester::stop);
            popup.item("Tasks", Icons.crafting, this::tasks);
            popup.item("Import", Icons.download, () -> askText("Import script path", "", path -> editor.area.setText(new Fi(path).readString())));
            popup.item("Export", Icons.upload, () -> askText("Export script path", "", path -> new Fi(path).writeString(editor.area.getText())));
            return popup;
        }

        void files(boolean bookmark) {
            InspectorDialog existing = bookmark ? bookmarkWindow : historyWindow;
            if (existing != null) {
                ((Runnable) existing.userObject).run();
                existing.display();
                return;
            }
            InspectorDialog window = new InspectorDialog(bookmark ? "bookmark" : "history", 420f, 260f, true);
            TextField search = new TextField("");
            search.setMessageText("search");
            Table list = new Table();
            Runnable rebuild = () -> {
                list.clearChildren();
                list.top().left().defaults().growX().left();
                List<Fi> entries = bookmark ? testerFiles.bookmarks() : testerFiles.histories();
                for (Fi file : entries) {
                    String code = (bookmark ? file : file.child("message.txt")).readString();
                    if (!file.name().toLowerCase().contains(search.getText().toLowerCase()) && !code.toLowerCase().contains(search.getText().toLowerCase()))
                        continue;
                    Table row = new Table();
                    TextButton name = new TextButton(file.name(), ArcStyles.toolTextButton);
                    name.getLabel().setEllipsis(true);
                    name.clicked(() -> {
                        editor.area.setText(code);
                        if (!bookmark) tester.loadHistory(file);
                        window.hide();
                        editor.area.requestKeyboard();
                    });
                    row.add(name).growX().minWidth(0f);
                    iconButton(row, Icons.details, "Details", () -> fileDetails(file, bookmark)).size(36f);
                    iconButton(row, Icons.delete, "Delete", () -> {
                        if (bookmark) {
                            testerTasks.configure(file, null);
                            testerFiles.deleteBookmark(file);
                        } else file.deleteDirectory();
                        ((Runnable) window.userObject).run();
                    }).size(36f);
                    list.add(row).growX().row();
                    Label preview = new NoMarkupLabel(code);
                    preview.setWrap(true, true);
                    preview.setAlignment(Align.left);
                    list.add(preview).growX().padBottom(6f).row();
                }
            };
            search.changed(rebuild);
            window.cont.add(search).growX().row();
            window.cont.add(new ScrollPane(list)).grow().with(pane -> pane.setScrollingDisabled(true, false));
            window.userObject = rebuild;
            rebuild.run();
            if (bookmark) bookmarkWindow = window;
            else historyWindow = window;
            showAux(window, 600f, 420f);
        }

        void fileDetails(Fi file, boolean bookmark) {
            InspectorDialog window = new DisposableInspectorDialog(file.name(), 420f, 240f, true, false);
            TextArea code = new TextArea((bookmark ? file : file.child("message.txt")).readString());
            code.setPrefRows(8f);
            window.cont.add(code).grow().row();
            if (bookmark) {
                window.cont.button("Execution Settings", ArcStyles.toolTextButton, () -> executionSettings(file)).growX().row();
                window.cont.button("Rename", ArcStyles.toolTextButton, () -> askText("Rename", file.name(), name -> {
                    testerTasks.rename(file, name);
                    window.hide();
                })).growX().row();
                window.buttons.button("Save", ArcStyles.textButton, () -> {
                    file.writeString(code.getText());
                    window.hide();
                });
            } else {
                Label log = new NoMarkupLabel(file.child("log.txt").exists() ? file.child("log.txt").readString() : "");
                log.setWrap(true, true);
                window.cont.pane(table -> table.add(log).growX()).height(100f).growX().row();
            }
            window.buttons.button("Load", ArcStyles.textButton, () -> {
                editor.area.setText(code.getText());
                if (!bookmark) tester.loadHistory(file);
                window.hide();
            });
            showAux(window, 600f, 420f);
        }

        void executionSettings(Fi file) {
            InspectorDialog window = new DisposableInspectorDialog("Execution Settings", 420f, 240f, true, false);
            Jval saved = testerFiles.execution.get(file.name());
            CheckBox enabled = new CheckBox("Add to executor");
            enabled.setChecked(saved != null);
            CheckBox event = new CheckBox("Event trigger");
            event.setChecked(saved != null && saved.getBool("disposable", false));
            TextField interval = new TextField(saved == null ? "0.1" : Float.toString(saved.getFloat("intervalSeconds", 0.1f)));
            TextField repeat = new TextField(saved == null ? "0" : Integer.toString(saved.getInt("repeatCount", 0)));
            TextField type = new TextField(saved == null ? "" : saved.getString("type"));
            window.cont.add(enabled).colspan(2).left().row();
            window.cont.add("Interval Seconds").left();
            window.cont.add(interval).growX().row();
            window.cont.add("Repeat Count").left();
            window.cont.add(repeat).growX().row();
            window.cont.add(event).colspan(2).left().row();
            window.cont.add("Event Class").left();
            window.cont.add(type).growX().row();
            window.cont.update(() -> {
                interval.setDisabled(!enabled.isChecked() || event.isChecked());
                repeat.setDisabled(!enabled.isChecked() || event.isChecked());
                type.setDisabled(!enabled.isChecked() || !event.isChecked());
            });
            window.buttons.button("OK", ArcStyles.textButton, () -> runChecked(() -> {
                if (!enabled.isChecked()) {
                    testerTasks.configure(file, null);
                    window.hide();
                    return;
                }
                float seconds = Float.parseFloat(interval.getText());
                int count = Integer.parseInt(repeat.getText());
                if (!Float.isFinite(seconds) || seconds < 0.01f || count < -1)
                    throw new IllegalArgumentException("Invalid interval or repeat count");
                testerTasks.configure(file, Jval.newObject().put("intervalSeconds", seconds).put("repeatCount", count).put("disposable", event.isChecked()).put("type", type.getText()));
                window.hide();
            }));
            showAux(window, 560f, 330f);
        }

        void tasks() {
            testerTasks.start();
            InspectorDialog window = new DisposableInspectorDialog("JS startup", 420f, 240f, true);
            Table list = new Table();
            for (TesterTasks.Job job : testerTasks.jobs) {
                Label status = new Label("");
                status.setWrap(true, true);
                status.update(() -> status.setText(job.status + " (" + job.runs + ")"));
                list.add(job.file.name()).left();
                list.add(status).growX();
                iconButton(list, Icons.run, "Run", job::resume).size(36f);
                iconButton(list, Icons.pause, "Pause", job::close).size(36f).row();
            }
            window.cont.add(new ScrollPane(list)).grow().with(pane -> pane.setScrollingDisabled(true, false));
            showAux(window, 620f, 360f);
        }

        void showAux(InspectorDialog window, float width, float height) {
            window.setMovable(true);
            window.setResizable(true);
            window.setCentered(false);
            window.show(scene);
            window.setSize(Math.min(Scl.scl(width), scene.getWidth()), Math.min(Scl.scl(height), scene.getHeight()));
            window.setPosition(x + 20f, y + 20f);
            window.keepInStage();
        }

        void askText(String title, String initial, Cons<String> callback) {
            InspectorDialog window = new DisposableInspectorDialog(title, 320f, 150f, false, false);
            TextField field = new TextField(initial);
            window.cont.add(field).growX();
            window.buttons.button("Cancel", ArcStyles.textButton, window::hide);
            window.buttons.button("OK", ArcStyles.textButton, () -> runChecked(() -> {
                callback.get(field.getText());
                window.hide();
            }));
            showAux(window, 480f, 180f);
            field.requestKeyboard();
        }

        @Override
        public void hide() {
            editor.completion.hide();
            super.hide();
        }

        void showWindow() {
            display();
            if (!shown) {
                shown = true;
                setSize(Math.min(Scl.scl(600f), scene.getWidth() - 20f), Math.min(Scl.scl(480f), scene.getHeight() - 20f));
                setPosition((scene.getWidth() - getWidth()) / 2f, (scene.getHeight() - getHeight()) / 2f);
            }
            keepInStage();
            editor.area.requestKeyboard();
        }
    }

    private class JavaRequest extends DisposableInspectorDialog {
        final TextArea area;
        final Label log = new Label("");
        final Object self, bound;
        final String binding;
        final ValueSetter callback;
        boolean keepOpen;

        JavaRequest(String title, String initial, Object self, String binding, Object bound, ValueSetter callback) {
            super(title, 220f, 220f, true, false);
            this.self = self;
            this.binding = binding;
            this.bound = bound;
            this.callback = callback;
            setMovable(true);
            setResizable(true);
            setCentered(false);
            closeOnBack();
            area = new TextArea(initial);
            area.setPrefRows(4);
            cont.add(area).grow().row();
            cont.pane(table -> table.add(log).left()).growX().height(42f).row();
            CheckBox check = new CheckBox("Do not hide automatically");
            check.changed(() -> keepOpen = check.isChecked());
            buttons.add(check).colspan(3).padRight(100f).left().row();
            buttons.margin(6f, 8f, 6f, 8f);
            buttons.defaults().growX().height(42f);
            buttons.button("Cancel", ArcStyles.textButton, this::hide);
            buttons.button("Test", ArcStyles.textButton, () -> execute(false));
            buttons.button("OK", ArcStyles.textButton, () -> execute(true));
        }

        void execute(boolean apply) {
            try {
                Object result = reflection.evaluate(area.getText(), self, binding, bound);
                log.setText(InspectorReflection.describe(result));
                log.setColor(Color.lightGray);
                if (apply) {
                    callback.set(result);
                    if (!keepOpen) hide();
                }
            } catch (Throwable error) {
                log.setText(InspectorReflection.error(error));
                log.setColor(Color.red);
            }
        }

        void showWindow() {
            show(scene);
            setSize(Math.min(Scl.scl(600f), scene.getWidth() - 20f), Math.min(Scl.scl(400f), scene.getHeight() - 20f));
            setPosition(Core.input.mouseX(), Core.input.mouseY(), Align.center);
            keepInStage();
            area.requestKeyboard();
        }
    }

    private class JavaObjectInfo extends DisposableInspectorDialog {
        private final Object object;
        private final Class<?> type;
        private final Table build = new Table();
        private final TextField search = new TextField("");
        private final boolean[] open = {true, true, true, true};
        private boolean autoRefresh = javaSetting("autoRefresh", true), displaySynthetic = javaSetting("displaySynthetic", false);
        private boolean displayGeneric = javaSetting("displayGeneric", true), exactSearch = javaSetting("exactSearch", false);
        private boolean displayModifiers = javaSetting("displayModifiers", true), displayTypes = javaSetting("displayTypes", true), displayButtons = javaSetting("displayButtons", true), displayValues = javaSetting("displayValues", true);
        private boolean editNumbers = javaSetting("editNumbers", false), editStrings = javaSetting("editStrings", false);
        private boolean foldedNames = javaSetting("foldedNames", false);
        private int searchMode;
        private int activeSection;
        private int selectedModifiers = -1;
        private boolean blacklist;
        private final Seq<ObjectValue> fields = new Seq<>();
        private final ObjectMap<Executable, ObjectValue> results = new ObjectMap<>();
        private final Seq<FilteredRow> filteredRows = new Seq<>();
        private final Seq<MemberGroup> memberGroups = new Seq<>();
        private final ObjectMap<String, Seq<FilteredRow>> sameNames = new ObjectMap<>();
        private final ObjectMap<String, Cell<ImageButton>> foldButtons = new ObjectMap<>();
        private final ObjectMap<String, ImageButton> foldImages = new ObjectMap<>();
        private final ObjectSet<String> unfoldedNames = new ObjectSet<>();
        private Pattern filter;

        JavaObjectInfo(Object object) {
            this(object instanceof Class<?> ? null : object, object instanceof Class<?> clazz ? clazz : object == null ? Object.class : object.getClass());
        }

        JavaObjectInfo(Object object, Class<?> type) {
            super(type.getSimpleName(), 200f, 200f, true);
            this.object = object;
            this.type = type;
            setModal(false);
            setMovable(true);
            setResizable(true);
            setCentered(false);
            closeOnBack();
            build();
        }

        void showWindow() {
            show(scene);
            setSize(Math.min(Math.max(Scl.scl(560f), getPrefWidth()), Math.max(0f, scene.getWidth() - 20f)), Math.min(Scl.scl(620f), Math.max(0f, scene.getHeight() - 20f)));
            setPosition(Core.input.mouseX(), Core.input.mouseY(), Align.topLeft);
            keepInStage();
        }

        @Override
        public boolean focusSearch(boolean clear) {
            if (clear) search.clear();
            search.requestKeyboard();
            search.setCursorPosition(Integer.MAX_VALUE);
            return true;
        }

        private void build() {
            cont.clearChildren();
            cont.top().left().defaults().left().growX();

            Table topTable = new Table();
            topTable.left().defaults().left().growX();

            topTable.pane(toolbar -> {
                toolbar.left().defaults().left().height(42f).padRight(6f);
                toolbar.button(Icons.settings, ArcStyles.toolImageButton, this::settings).size(42f).tooltip("Settings");
                toolbar.button("Copy class", ArcStyles.toolTextButton, () -> copy(type.getName()));
                toolbar.button(Icons.refresh, ArcStyles.toolImageButton, this::refreshValues).size(42f).tooltip("Refresh");
                toolbar.button(Icons.refresh, ArcStyles.toolImageButton, this::rebuild)
                        .size(42f).color(Color.red).tooltip("Force rebuild");
                if (object != null) {
                    toolbar.button("Copy as JS", ArcStyles.toolTextButton, () -> copy(reflection.store("Object", object)));
                    toolbar.add(identity()).padLeft(8f).color(Color.lightGray);
                }
                for (Element child : toolbar.getChildren()) {
                    if (child instanceof TextButton button) button.getLabel().setWrap(false);
                }
            }).with(pane -> pane.setScrollingDisabledY(true)).height(42f).growX().row();

            search.setMessageText("search");
            search.changed(this::filterMembers);
            topTable.table(t -> {
                t.left().defaults().left();
                t.add(search).growX();
                t.button(Icons.filter, ArcStyles.toolImageButton, this::filters).size(42f).tooltip("Filter");
            }).growX().height(42f).row();

            topTable.table(t -> {
                t.left().defaults().left();
                t.pane(info -> info.left().add(type.getTypeName()).left().color(ArcStyles.accent))
                        .with(pane -> pane.setScrollingDisabledY(true)).grow();
                t.button(Icons.copy, ArcStyles.toolImageButton, () -> copy(type.getTypeName())).size(32f).tooltip("Copy");
                if (object == null) t.add("NULL").color(Color.red).padLeft(8f);
            }).pad(6f, 10f, 6f, 10f).growX().row();

            cont.add(topTable).growX().row();

            build.left().top().defaults().left().growX();
            cont.add(new ScrollPane(build, ArcStyles.scrollPane)).grow().minHeight(160f).row();
            rebuild();
        }

        private void rebuild() {
            String text = search.getText();
            filter = text == null || text.isEmpty() ? null : compileFilter(text);
            build.clearChildren();
            fields.clear();
            results.clear();
            filteredRows.clear();
            memberGroups.clear();
            sameNames.clear();
            foldButtons.clear();
            foldImages.clear();

            if (type.isArray() || object instanceof List<?> || object instanceof Map<?, ?> || type.isPrimitive()) {
                contents();
            }

            section("Fields", 0, table -> {
                for (Class<?> current : InspectorReflection.hierarchy(type)) {
                    memberTable(table, current, this::fields);
                }
            });
            section("Methods", 1, table -> {
                for (Class<?> current : InspectorReflection.hierarchy(type)) {
                    memberTable(table, current, this::methods);
                }
            });
            section("Constructors", 2, table -> {
                for (Class<?> current : InspectorReflection.hierarchy(type))
                    memberTable(table, current, this::constructors);
            });
            section("Classes", 3, table -> {
                for (Class<?> current : InspectorReflection.hierarchy(type)) {
                    memberTable(table, current, this::classes);
                }
            });
            applyFilter();
        }

        private void refreshValues() {
            for (ObjectValue value : fields) value.refresh();
            for (ObjectMap.Entry<Executable, ObjectValue> entry : results) {
                if (entry.key instanceof Method) entry.value.resetValue();
            }
        }

        private void filterMembers() {
            String text = search.getText();
            filter = text == null || text.isEmpty() ? null : compileFilter(text);
            applyFilter();
        }

        private void applyFilter() {
            for (FilteredRow row : filteredRows) row.apply();
            for (ObjectMap.Entry<String, Cell<ImageButton>> entry : foldButtons) {
                Seq<FilteredRow> group = sameNames.get(entry.key);
                boolean show = foldedNames && filter == null && group != null && group.size > 1;
                entry.value.setElement(show ? foldImages.get(entry.key) : null);
                entry.value.size(show ? 24f : 0f);
            }
            for (MemberGroup group : memberGroups) {
                boolean visible = false;
                for (int i = group.first; i < group.end; i++) {
                    if (filteredRows.get(i).shown()) {
                        visible = true;
                        break;
                    }
                }
                group.cell.setElement(visible ? group.table : null);
            }
            build.invalidateHierarchy();
        }

        private void remember(Table table, int first, Boolp visible) {
            remember(table, first, visible, null);
        }

        private void remember(Table table, int first, Boolp visible, String groupKey) {
            Seq<BindCell> cells = new Seq<>();
            for (int i = first; i < table.getCells().size; i++) {
                Cell<?> cell = table.getCells().get(i);
                cells.add(BindCell.ofConst(cell));
            }
            FilteredRow row = new FilteredRow(visible, cells, groupKey);
            filteredRows.add(row);
            if (groupKey != null) {
                Seq<FilteredRow> group = sameNames.get(groupKey);
                if (group == null) {
                    group = new Seq<>();
                    sameNames.put(groupKey, group);
                }
                group.add(row);
            }
        }

        private record MemberGroup(Cell<Table> cell, Table table, int first, int end) {
        }

        private class FilteredRow {
            final Boolp visible;
            final Seq<BindCell> cells;
            final String groupKey;

            FilteredRow(Boolp visible, Seq<BindCell> cells, String groupKey) {
                this.visible = visible;
                this.cells = cells;
                this.groupKey = groupKey;
            }

            boolean shown() {
                if (!visible.get()) return false;
                if (!foldedNames || filter != null || groupKey == null || unfoldedNames.contains(groupKey)) return true;
                Seq<FilteredRow> group = sameNames.get(groupKey);
                return group == null || group.first() == this;
            }

            void apply() {
                boolean show = shown();
                for (BindCell cell : cells) cell.toggle(show);
            }
        }

        private Element nameWithFold(Label name, String key) {
            if (foldButtons.containsKey(key)) return name;
            Table wrapper = new Table();
            wrapper.left();
            wrapper.add(name).growX().left();
            Cell<ImageButton> cell = wrapper.button(Icons.down, ArcStyles.toolImageButton, () -> {
                if (!unfoldedNames.add(key)) unfoldedNames.remove(key);
                applyFilter();
            }).size(24f);
            foldButtons.put(key, cell);
            foldImages.put(key, cell.get());
            return wrapper;
        }

        private void memberTable(Table parent, Class<?> owner, java.util.function.BiConsumer<Table, Class<?>> builder) {
            Table table = new Table();
            table.left().top().defaults().left().top();
            int first = filteredRows.size;
            builder.accept(table, owner);
            if (table.hasChildren()) {
                Cell<Table> cell = parent.add(table).growX();
                cell.row();
                memberGroups.add(new MemberGroup(cell, table, first, filteredRows.size));
            }
        }

        private void settings() {
            InspectorPopup menu = new InspectorPopup();
            setting(menu, "Auto Refresh", "autoRefresh", () -> autoRefresh, () -> autoRefresh = !autoRefresh);
            setting(menu, "Fold Same Name", "foldedNames", () -> foldedNames, () -> {
                foldedNames = !foldedNames;
                applyFilter();
            });
            setting(menu, "Search Exact", "exactSearch", () -> exactSearch, () -> {
                exactSearch = !exactSearch;
                applyFilter();
            });
            setting(menu, "Display Generic", "displayGeneric", () -> displayGeneric, () -> {
                displayGeneric = !displayGeneric;
                rebuild();
            });
            setting(menu, "Display Synthetic", "displaySynthetic", () -> displaySynthetic, () -> {
                displaySynthetic = !displaySynthetic;
                rebuild();
            });
            menu.separator();
            setting(menu, "Display Modifiers", "displayModifiers", () -> displayModifiers, () -> {
                displayModifiers = !displayModifiers;
                rebuild();
            });
            setting(menu, "Display Type", "displayTypes", () -> displayTypes, () -> {
                displayTypes = !displayTypes;
                rebuild();
            });
            setting(menu, "Display Value", "displayValues", () -> displayValues, () -> {
                displayValues = !displayValues;
                rebuild();
            });
            setting(menu, "Display Buttons", "displayButtons", () -> displayButtons, () -> {
                displayButtons = !displayButtons;
                rebuild();
            });
            menu.separator();
            setting(menu, "Edit Number", "editNumbers", () -> editNumbers, () -> {
                editNumbers = !editNumbers;
                rebuild();
            });
            setting(menu, "Edit String", "editStrings", () -> editStrings, () -> {
                editStrings = !editStrings;
                rebuild();
            });
            menu.showAt(Core.input.mouseX(), Core.input.mouseY());
        }

        private boolean javaSetting(String key, boolean fallback) {
            return Core.settings.getBool("arcui.inspect.java." + key, fallback);
        }

        private void setting(InspectorPopup menu, String text, String key, Boolp getter, Runnable changed) {
            menu.check(text, getter, () -> {
                changed.run();
                Core.settings.put("arcui.inspect.java." + key, getter.get());
            });
        }

        private void filters() {
            InspectorPopup menu = new InspectorPopup();
            String[] modes = {"Name", "Field Type / Return Type", "Parameters", "Exceptions"};
            for (int i = 0; i < modes.length; i++) {
                int index = i;
                menu.check(modes[i], () -> searchMode == index, () -> {
                    searchMode = index;
                    applyFilter();
                });
            }
            menu.separator();
            menu.check("Blacklist", () -> blacklist, () -> {
                blacklist = !blacklist;
                applyFilter();
            });
            menu.item("Modifiers", Icons.filter, this::modifierFilter);
            menu.showAt(Core.input.mouseX(), Core.input.mouseY());
        }

        private void modifierFilter() {
            InspectorPopup menu = new InspectorPopup();
            int[] masks = {Modifier.PUBLIC, Modifier.PRIVATE, Modifier.PROTECTED, Modifier.STATIC, Modifier.FINAL, Modifier.SYNCHRONIZED, Modifier.VOLATILE, Modifier.TRANSIENT, Modifier.NATIVE, Modifier.INTERFACE, Modifier.ABSTRACT, Modifier.STRICT};
            menu.items.table(grid -> {
                grid.left().defaults().left().height(42f).pad(5f);
                for (int i = 0; i < masks.length; i++) {
                    int mask = masks[i];
                    CheckBox box = new CheckBox(Modifier.toString(mask));
                    box.setChecked((selectedModifiers & mask) != 0);
                    box.changed(() -> {
                        selectedModifiers ^= mask;
                        applyFilter();
                    });
                    grid.add(box).growX();
                    if (i % 4 == 3) grid.row();
                }
            }).growX();
            menu.showAt(Core.input.mouseX(), Core.input.mouseY());
        }

        private boolean valid(Member member) {
            return (validModifiers(member.getModifiers()) && matchesMember(member)) != blacklist;
        }

        private boolean validModifiers(int modifiers) {
            return modifiers == 0 || (modifiers & selectedModifiers) != 0;
        }

        private boolean matchesMember(Member member) {
            if (filter == null) return true;
            return switch (searchMode) {
                case 1 ->
                        member instanceof Field field ? valid(field.getType().getTypeName()) : member instanceof Method method && valid(method.getReturnType().getTypeName());
                case 2 -> member instanceof Executable executable && anyType(executable.getParameterTypes());
                case 3 -> member instanceof Executable executable && anyType(executable.getExceptionTypes());
                default -> valid(member.getName());
            };
        }

        private boolean validClass(Class<?> type) {
            boolean matches = switch (searchMode) {
                case 0 -> valid(type.getName()) || valid(type.getSimpleName());
                case 1 -> valid(type.getTypeName());
                default -> false;
            };
            return (validModifiers(type.getModifiers()) && matches) != blacklist;
        }

        private boolean anyType(Class<?>[] types) {
            for (Class<?> clazz : types) if (valid(clazz.getTypeName())) return true;
            return false;
        }

        private void addType(Table table, Class<?> type, String text) {
            Label label = new Label(text);
            label.setColor(ArcStyles.accent);
            label.setFontScale(0.9f);
            label.clicked(() -> showJavaObject(null, type));
            table.add(label).padRight(16f);
        }

        private String classExpression(Class<?> owner) {
            return "java.lang.Class.forName(\"" + owner.getName() + "\")";
        }

        private String javaClass(Class<?> owner) {
            String name = owner.getCanonicalName();
            return name == null ? classExpression(owner) : name + ".class";
        }

        private String fieldGetter(Field field) {
            return "Field field = " + javaClass(field.getDeclaringClass()) + ".getDeclaredField(\"" + field.getName() + "\");";
        }

        private String executableGetter(Executable executable) {
            boolean method = executable instanceof Method;
            StringBuilder code = new StringBuilder(method ? "Method m = " : "Constructor<?> c = ");
            code.append(javaClass(executable.getDeclaringClass()));
            code.append(method ? ".getDeclaredMethod(\"" + executable.getName() + "\"" : ".getDeclaredConstructor(");
            Class<?>[] types = executable.getParameterTypes();
            for (int i = 0; i < types.length; i++) {
                if (method || i > 0) code.append(", ");
                code.append(javaClass(types[i]));
            }
            return code.append(");").toString();
        }

        private String methodValueGetter(Method method) {
            StringBuilder code = new StringBuilder("T val = Reflect.invoke(");
            code.append(javaClass(method.getDeclaringClass())).append(", ")
                    .append(Modifier.isStatic(method.getModifiers()) ? "null" : "object")
                    .append(", \"").append(method.getName()).append("\", ");
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length == 0) code.append("null");
            else {
                code.append("new Object[]{");
                for (int i = 0; i < parameters.length; i++) {
                    if (i > 0) code.append(", ");
                    code.append("p").append(i);
                }
                code.append("}");
                for (Class<?> parameter : parameters) code.append(", ").append(javaClass(parameter));
            }
            return code.append(");").toString();
        }

        private InspectorPopup executableMenu(Executable executable, ObjectValue result) {
            InspectorPopup menu = new InspectorPopup();
            menu.item("Copy as JS", Icons.copy, () -> copy(reflection.store(executable instanceof Method ? "method" : "constructor", executable)));
            menu.item(executable instanceof Method ? "Cpy method getter" : "Cpy reflect getter", Icons.copy, () -> copy(executableGetter(executable)));
            if (executable instanceof Method method)
                menu.item("Cpy value getter", Icons.copy, () -> copy(methodValueGetter(method)));
            menu.item("Invoke", Icons.box, () -> !canInvoke(executable), () -> invoke(executable, result, false));
            menu.item("InvokeSpecial", Icons.box, () -> !canInvoke(executable) || Modifier.isAbstract(executable.getModifiers()), () -> invoke(executable, result, true));
            menu.item("Details", Icons.details, () -> showJavaObject(executable, executable instanceof Method ? Method.class : Constructor.class));
            return menu;
        }

        private boolean canInvoke(Executable executable) {
            if (executable instanceof Constructor<?> constructor)
                return !Modifier.isAbstract(constructor.getDeclaringClass().getModifiers()) && !constructor.getDeclaringClass().isEnum();
            return object != null || Modifier.isStatic(executable.getModifiers());
        }

        private void invoke(Executable executable, ObjectValue result, boolean special) {
            ValueSetter apply = args -> {
                try {
                    Object value = InspectorReflection.invoke(executable, object, args, special);
                    result.set(value);
                    if (executable instanceof Method method && method.getReturnType() == void.class)
                        result.label.setText("<void>");
                    if (executable instanceof Constructor<?>) copy(reflection.store("Instance", value));
                } catch (Throwable error) {
                    result.fail(error);
                    throw error;
                }
            };
            if (executable.getParameterCount() == 0) {
                runChecked(() -> apply.set(new Object[0]));
            } else {
                requestValue(executable.toGenericString(), "[]", object, "m0", executable, apply);
            }
        }

        private void contents() {
            if (type.isPrimitive()) {
                build.add("<PRIMITIVE>").color(Color.gray).row();
                build.add(type.getTypeName()).color(ArcStyles.accent).row();
                build.add(InspectorReflection.describe(object)).row();
                return;
            }
            Table entries = new Table();
            entries.left().top().defaults().left().top();
            if (object != null && type.isArray()) {
                Class<?> component = type.getComponentType();
                int length = Array.getLength(object);
                for (int i = 0; i < length; i++) {
                    int index = i;
                    addEntry(entries, "[" + i + "]", component, () -> Array.get(object, index), value -> Array.set(object, index, InspectorReflection.convert(value, component)));
                }
            } else if (object instanceof List<?> list) {
                for (int i = 0; i < list.size(); i++) {
                    int index = i;
                    addEntry(entries, "[" + i + "]", Object.class, () -> list.get(index), value -> setListValue(list, index, value));
                }
            } else if (object instanceof Map<?, ?> map) {
                for (Object key : map.keySet().toArray()) {
                    addEntry(entries, InspectorReflection.describe(key), Object.class, () -> map.get(key), value -> setMapValue(map, key, value));
                }
            }
            build.add(entries).growX().row();
        }

        @SuppressWarnings("unchecked")
        private void setListValue(List<?> list, int index, Object value) {
            ((List<Object>) list).set(index, value);
        }

        @SuppressWarnings("unchecked")
        private void setMapValue(Map<?, ?> map, Object key, Object value) {
            ((Map<Object, Object>) map).put(key, value);
        }

        private void addEntry(Table table, String key, Class<?> type, ValueGetter getter, ValueSetter setter) {
            int first = table.getCells().size;
            table.add(key).pad(4f);
            ObjectValue value = new ObjectValue(type, object, getter, setter);
            fields.add(value);
            table.add(displayValue(value)).growX().pad(4f).row();
            underline(table, 2);
            remember(table, first, () -> valid(key));
        }

        private ObjectValue displayValue(ObjectValue value) {
            if (!displayValues) {
                // Keep the holder attached for auto refresh while removing its display and editor cells.
                for (Cell<?> cell : value.getCells()) BindCell.ofConst(cell).remove();
            }
            return value;
        }

        @Override
        public void act(float delta) {
            if (autoRefresh) {
                for (ObjectValue value : fields) {
                    Element focus = scene.getKeyboardFocus();
                    if (value.autoRefresh && value.error == null && value.getScene() != null && value.visible
                            && !(focus instanceof TextField && focus.isDescendantOf(value))) value.refresh();
                }
            }
            super.act(delta);
        }

        private Pattern compileFilter(String text) {
            try {
                return Pattern.compile(text, Pattern.CASE_INSENSITIVE);
            } catch (PatternSyntaxException ignored) {
                return Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE);
            }
        }

        private void section(String text, int index, arc.func.Cons<Table> builder) {
            build.button(text, ArcStyles.toggleTextButton, () -> open[index] = !open[index])
                    .growX().height(42f).checked(button -> open[index])
                    .with(button -> button.getLabelCell().padLeft(10f).growX().labelAlign(Align.left))
                    .row();
            build.image(ArcStyles.white).color(ArcStyles.accent).height(2f).fill(0.75f, 0f).growX().row();
            Table table = new Table();
            table.left().defaults().left().top();
            activeSection = index;
            builder.get(table);
            build.collapser(table, true, () -> open[index])
                    .pad(4f, 6f, 4f, 6f).growX().padTop(8f).get().setDuration(0.1f);
            build.row();
            build.add().grow().top().row();
        }

        private void classHeader(Table table, Class<?> owner, int colspan) {
            table.add(owner.getTypeName()).wrap(true).color(owner.isInterface() ? Color.lightGray : ArcStyles.accent).growX().colspan(colspan).row();
            table.image(ArcStyles.white).color(Color.lightGray).height(1f).growX().colspan(colspan).padTop(6f).row();
        }

        private void fields(Table table, Class<?> owner) {
            Field[] fields = owner.getDeclaredFields();
            if (fields.length == 0) return;
            boolean wroteHeader = false;
            for (Field field : fields) {
                if (!displaySynthetic && field.isSynthetic()) continue;
                if (!wroteHeader) {
                    classHeader(table, owner, 5);
                    wroteHeader = true;
                }
                int first = table.getCells().size;

                table.table(attr -> {
                    attr.left().defaults().left();
                    if (displayModifiers)
                        attr.add(modifiers(field.getModifiers())).fontScale(0.7f).color(Color.lightGray).padRight(8f).row();
                    if (displayTypes)
                        addType(attr, field.getType(), displayGeneric ? field.getGenericType().getTypeName() : simpleName(field.getType()));
                }).top();
                Label name = copyLabel(field.getName());
                contextMenu(name, () -> {
                    InspectorPopup menu = new InspectorPopup();
                    menu.item("Copy as JS", Icons.copy, () -> copy(reflection.store("Field", field)));
                    menu.item("Cpy field getter", Icons.copy, () -> copy(fieldGetter(field)));
                    menu.item("Cpy value getter", Icons.copy, () -> copy("T val = Reflect.get(" + javaClass(field.getDeclaringClass()) + ", " + (Modifier.isStatic(field.getModifiers()) ? "null" : "obj") + ", \"" + field.getName() + "\");"));
                    menu.item("Details", Icons.details, () -> showJavaObject(field, Field.class));
                    return menu;
                });
                String groupKey = activeSection + ":" + field.getName();
                table.add(nameWithFold(name, groupKey)).padTop(4f).top().minWidth(Math.max(64f, name.getPrefWidth()));
                table.add(" = ").color(Color.lightGray).top().padTop(6f).touchable(Touchable.disabled);
                ObjectValue value = fieldValue(field);
                table.add(displayValue(value)).pad(4f).growX().top();
                Table buttons = new Table();
                if (displayButtons) {
                    buttons.defaults().size(42f);
                    if (!field.getType().isPrimitive())
                        buttons.button(Icons.details, ArcStyles.toolImageButton, 28f, () -> showJavaObject(value.value, value.value != null ? value.value.getClass() : field.getType())).tooltip("Info");
                    buttons.button(Icons.watch, ArcStyles.toolImageButton, () -> watch(field.getName(), () -> InspectorReflection.read(field, object))).tooltip("Watch").disabled(button -> !Modifier.isStatic(field.getModifiers()) && object == null);
                }
                table.add(buttons).right().top();
                table.row();
                underline(table, 5);
                remember(table, first, () -> valid(field), groupKey);
            }
        }

        private void methods(Table table, Class<?> owner) {
            Method[] methods = owner.getDeclaredMethods();
            if (methods.length == 0) return;
            boolean wroteHeader = false;
            for (Method method : methods) {
                if (!displaySynthetic && method.isSynthetic()) continue;
                if (!wroteHeader) {
                    classHeader(table, owner, 4);
                    wroteHeader = true;
                }
                int first = table.getCells().size;
                table.table(attr -> {
                    attr.left().defaults().left();
                    if (displayModifiers)
                        attr.add(modifiers(method.getModifiers())).fontScale(0.7f).color(Color.lightGray).padRight(8f).row();
                    if (displayTypes)
                        addType(attr, method.getReturnType(), displayGeneric ? method.getGenericReturnType().getTypeName() : simpleName(method.getReturnType()));
                }).top();
                Label name = copyLabel(method.getName());
                ObjectValue result = results.get(method);
                if (result == null) {
                    result = new ObjectValue(method.getReturnType(), object, null, null);
                    results.put(method, result);
                }
                ObjectValue value = result;
                contextMenu(name, () -> executableMenu(method, value));
                String groupKey = activeSection + ":" + method.getName();
                table.add(nameWithFold(name, groupKey)).padTop(4f).top().minWidth(Math.max(72f, name.getPrefWidth()));
                table.table(body -> {
                    body.left().defaults().left();
                    body.add(argsAndExceptions(method)).growY().pad(4f).left();
                    body.add(displayValue(value)).growX().top();
                    Table buttons = new Table();
                    if (displayButtons) {
                        buttons.defaults().size(42f);
                        if (method.getParameterCount() == 0 && (object != null || Modifier.isStatic(method.getModifiers()))) {
                            buttons.button(Icons.execute, ArcStyles.toolImageButton, () -> invoke(method, value, false)).tooltip("Run");
                            buttons.button(Icons.watch, ArcStyles.toolImageButton, () -> watch(method.getName(), () -> InspectorReflection.invoke(method, object, new Object[0], false))).tooltip("Watch");
                        }
                        if (!method.getReturnType().isPrimitive())
                            buttons.button(Icons.details, ArcStyles.toolImageButton, 28f, () -> showJavaObject(value.value, value.value != null ? value.value.getClass() : method.getReturnType())).tooltip("Info").disabled(button -> !value.assigned);
                    }
                    body.add(buttons).right().top();
                }).growX().left().top();
                table.add().top();
                table.row();
                underline(table, 4);
                remember(table, first, () -> valid(method), groupKey);
            }
        }

        private void constructors(Table table, Class<?> owner) {
            if (owner.getDeclaredConstructors().length == 0) return;
            boolean wroteHeader = false;
            for (Constructor<?> constructor : owner.getDeclaredConstructors()) {
                if (!displaySynthetic && constructor.isSynthetic()) continue;
                if (!wroteHeader) {
                    classHeader(table, owner, 4);
                    wroteHeader = true;
                }
                int first = table.getCells().size;
                table.add(displayModifiers ? modifiers(constructor.getModifiers()) : "").fontScale(0.7f).color(Color.lightGray).padRight(8f);
                Label name = copyLabel(simpleName(constructor.getDeclaringClass()));
                ObjectValue result = results.get(constructor);
                if (result == null) {
                    result = new ObjectValue(constructor.getDeclaringClass(), object, null, null);
                    results.put(constructor, result);
                }
                ObjectValue value = result;
                contextMenu(name, () -> executableMenu(constructor, value));
                table.add(name).labelAlign(Align.top).padTop(4f).top().minWidth(Math.max(96f, name.getPrefWidth()));
                table.add(argsAndExceptions(constructor)).growY().pad(4f).left();
                table.add(displayValue(value)).growX().top();
                table.row();
                underline(table, 4);
                remember(table, first, () -> valid(constructor));
            }
        }

        private void classes(Table table, Class<?> owner) {
            Class<?>[] classes = owner.getDeclaredClasses();
            if (classes.length == 0) return;
            boolean wroteHeader = false;
            for (Class<?> clazz : classes) {
                String label = classLabel(clazz);
                if (!displaySynthetic && clazz.isSynthetic()) continue;
                if (!wroteHeader) {
                    classHeader(table, owner, 1);
                    wroteHeader = true;
                }
                int first = table.getCells().size;
                table.table(t -> {
                    t.left().top().defaults().left().padTop(4f);
                    if (displayModifiers)
                        t.add(classModifiers(clazz)).fontScale(1f).color(Color.lightGray).padRight(8f);
                    TextButton name = new TextButton(clazz.getSimpleName(), ArcStyles.toolTextButton);
                    name.getLabel().setWrap(false);
                    name.clicked(() -> showJavaObject(clazz));
                    name.getLabel().setColor(ArcStyles.accent);
                    t.add(name).padRight(8f);
                    Class<?> superClass = clazz.getSuperclass();
                    if (superClass != null) {
                        t.add(" extends ").color(Color.lightGray).padRight(8f);
                        addType(t, superClass, superClass.getSimpleName());
                    }
                    Class<?>[] interfaces = clazz.getInterfaces();
                    if (interfaces.length > 0) {
                        t.add(" implements ").color(Color.lightGray).padRight(8f);
                        for (Class<?> iface : interfaces) {
                            addType(t, iface, iface.getSimpleName());
                        }
                    }
                    if (displayButtons) t.add(buttonsFor(clazz, label)).right();
                }).growX().left().top().row();
                underline(table, 1);
                remember(table, first, () -> validClass(clazz));
            }
        }

        private boolean valid(String text) {
            return filter == null || (exactSearch ? filter.matcher(text).matches() : filter.matcher(text).find());
        }

        private ObjectValue fieldValue(Field field) {
            boolean writable = !Modifier.isFinal(field.getModifiers()) && (object != null || Modifier.isStatic(field.getModifiers()));
            ObjectValue value = new ObjectValue(field.getType(), object, () -> InspectorReflection.read(field, object), writable ? replacement -> InspectorReflection.write(field, object, replacement) : null);
            this.fields.add(value);
            if (writable && value.error == null) {
                Class<?> fieldType = field.getType();
                if (fieldType == boolean.class || fieldType == Boolean.class) {
                    TextButton toggle = createToggle(value);
                    value.clearChildren();
                    value.add(toggle).size(96f, 42f);
                    value.add(value.label).minWidth(64f).growX().top();
                } else if (editNumbers && (fieldType.isPrimitive() && fieldType != char.class || Number.class.isAssignableFrom(fieldType)) || editStrings && fieldType == String.class) {
                    TextField editor = createEditor(value, fieldType);
                    value.clearChildren();
                    value.add(editor).height(42f).growX();
                    if (fieldType == String.class) value.row();
                    value.add(value.label).minWidth(64f).growX().top();
                } else if (fieldType.isEnum()) {
                    value.label.clicked(() -> {
                        InspectorPopup menu = new InspectorPopup();
                        for (Object constant : fieldType.getEnumConstants())
                            menu.item(String.valueOf(constant), () -> runChecked(() -> value.write(constant)));
                        menu.showAt(Core.input.mouseX(), Core.input.mouseY());
                    });
                }
            }
            return value;
        }

        private TextButton createToggle(ObjectValue value) {
            TextButton toggle = new TextButton("", ArcStyles.toggleTextButton);
            toggle.setDisabled(() -> value.value == null);
            toggle.update(() -> {
                toggle.setText(value.value == null ? "ERROR" : Boolean.TRUE.equals(value.value) ? "TRUE" : "FALSE");
                toggle.setChecked(Boolean.TRUE.equals(value.value));
            });
            toggle.clicked(() -> runChecked(() -> value.write(!Boolean.TRUE.equals(value.value))));
            return toggle;
        }

        private TextField createEditor(ObjectValue value, Class<?> fieldType) {
            TextField editor = new TextField(value.value == null ? "null" : String.valueOf(value.value));
            editor.changed(() -> {
                try {
                    Object replacement = fieldType == String.class ? editor.getText() : reflection.evaluate(editor.getText(), object, "p1", value.value);
                    value.write(replacement);
                    editor.setColor(Color.white);
                } catch (Throwable error) {
                    editor.setColor(Color.red);
                }
            });
            editor.update(() -> {
                if (!editor.hasKeyboard())
                    editor.setText(value.value == null ? "null" : String.valueOf(value.value));
            });
            return editor;
        }

        private String classLabel(Class<?> clazz) {
            return classModifiers(clazz) + " " + clazz.getTypeName();
        }

        private String classModifiers(Class<?> clazz) {
            StringBuilder out = new StringBuilder();
            int modifiers = clazz.getModifiers() & ~Modifier.classModifiers();
            if (modifiers != 0) out.append(Modifier.toString(modifiers)).append(' ');
            if (Modifier.isStatic(clazz.getModifiers())) out.append("static ");
            if (!clazz.isInterface()) out.append("class");
            else out.append("interface");
            return out.toString();
        }

        private String modifiers(int modifiers) {
            String text = Modifier.toString(modifiers);
            return text.isEmpty() ? "" : text + " ";
        }

        private String identity() {
            return object == null ? "NULL" : String.valueOf(System.identityHashCode(object));
        }

        private Label copyLabel(String text) {
            Label label = new Label(text);
            label.setAlignment(Align.topLeft);
            label.setWrap(false);
            label.clicked(() -> copy(text));
            return label;
        }

        private Table buttonsFor(Object value, String copyText) {
            Table table = new Table();
            table.right().defaults().size(60f, 42f);
            table.button("Copy", ArcStyles.toolTextButton, () -> copy(copyText));
            table.button(Icons.details, ArcStyles.toolImageButton, 28f, () -> {
                if (value instanceof Class<?> clazz) showJavaObject(clazz);
                else if (value != null) showJavaObject(value);
            }).size(42f).tooltip("Info").disabled(button -> value == null || simpleValue(value instanceof Class<?> clazz ? clazz : value.getClass()));
            return table;
        }

        private Table argsAndExceptions(Executable executable) {
            Table table = new Table();
            table.left().defaults().left();
            table.table(args -> {
                args.left().defaults().left();
                args.add("(").color(Color.lightGray);
                Class<?>[] parameters = executable.getParameterTypes();
                java.lang.reflect.Type[] generic = executable.getGenericParameterTypes();
                for (int i = 0; i < parameters.length; i++) {
                    if (i > 0) args.add(", ").color(Color.lightGray);
                    addType(args, parameters[i], displayGeneric ? generic[i].getTypeName() : simpleName(parameters[i]));
                }
                args.add(")").color(Color.lightGray);
            }).row();
            Class<?>[] exceptions = executable.getExceptionTypes();
            if (exceptions.length > 0) {
                table.table(thrown -> {
                    thrown.left().defaults().left();
                    thrown.add("throws ").color(Color.valueOf("ff8877ff"));
                    for (int i = 0; i < exceptions.length; i++) {
                        if (i > 0) thrown.add(", ").color(Color.lightGray);
                        addType(thrown, exceptions[i], simpleName(exceptions[i]));
                    }
                }).row();
            }
            return table;
        }

        private void underline(Table table, int colspan) {
            table.image(ArcStyles.white).color(underlineColor).height(1f).growX().colspan(colspan).row();
        }

        private boolean simpleValue(Class<?> valueType) {
            return valueType.isPrimitive() || valueType.isEnum() || Reflect.isWrapper(valueType) || valueType == String.class || CharSequence.class.isAssignableFrom(valueType);
        }

        private String simpleName(Class<?> valueType) {
            if (valueType.isArray()) return simpleName(valueType.getComponentType()) + "[]";
            String name = valueType.getSimpleName();
            return name.isEmpty() ? valueType.getName() : name;
        }

    }

    private class MenuHitter extends Element {
        private final Runnable hide;
        private boolean init = !mouseDown();

        MenuHitter(Runnable hide) {
            this.hide = hide;
            setFillParent(true);
            touchable = Touchable.enabled;
        }

        @Override
        public Element hit(float x, float y, boolean touchable) {
            if (touchable && this.touchable != Touchable.enabled) return null;
            if (!init) {
                init = !mouseDown();
                return null;
            }
            if (mouseDown()) {
                hide.run();
            }
            return null;
        }
    }

    private record MenuEntry(String text, Runnable run) {
    }

    private String summary(Element element) {
        return name(element) + " x=" + element.x + " y=" + element.y + " w=" + element.getWidth() + " h=" + element.getHeight();
    }

    private String positionText(Element element) {
        return "(" + Strings.autoFixed(element.x, 2) + ", " + Strings.autoFixed(element.y, 2) + ")";
    }

    private void openCell(Element element) {
        Cell<?> cell = CellAccess.cell(element);
        if (cell != null) new CellDetails(cell).showWindow();
    }

    private void openAllCells(Table table) {
        new AllCells(table).showWindow();
    }

    private void confirmRemove(Element element) {
        if (element == null || element == scene.root || isInspectorElement(element)) return;
        Dialog dialog = new Dialog("Remove?", dialogStyle());
        styleDialog(dialog);
        dialog.setModal(true);
        dialog.cont.add(name(element)).pad(8f).row();
        dialog.buttons.button("Cancel", Icons.cancel, ArcStyles.textButton, dialog::hide);
        dialog.buttons.button("Remove", Icons.ok, ArcStyles.textButton, () -> {
            removeElement(element);
            dialog.hide();
        });
        dialog.show(scene, null);
    }

    private void removeElement(Element element) {
        if (element == null || element == scene.root || isInspectorElement(element)) return;
        element.remove();
        if (frontWindow != null) frontWindow.rebuild();
    }

    private void confirmRootParent(Runnable confirmed) {
        Dialog dialog = new Dialog("Confirm", dialogStyle());
        styleDialog(dialog);
        dialog.setModal(true);
        dialog.cont.add("Parent is ROOT. Are you sure?").pad(8f).row();
        dialog.buttons.button("Cancel", Icons.cancel, ArcStyles.textButton, dialog::hide);
        dialog.buttons.button("OK", Icons.ok, ArcStyles.textButton, () -> {
            dialog.hide();
            confirmed.run();
        });
        dialog.closeOnBack();
        dialog.show(scene, null);
    }

    private class AllCells extends DisposableInspectorDialog {
        private final Table inspected;
        private final Table rows = new Table();
        private boolean ignoreEmptyCell = true;

        AllCells(Table inspected) {
            super("All Cells", 240f, 160f, true);
            this.inspected = inspected;
            setModal(false);
            setMovable(true);
            setResizable(true);
            setCentered(false);
            closeOnBack();
            build();
        }

        void showWindow() {
            show(scene);
            setSize(Math.min(Math.max(Scl.scl(500f), getPrefWidth()), Math.max(0f, scene.getWidth() - 20f)), Math.min(Scl.scl(420f), Math.max(0f, scene.getHeight() - 20f)));
            setPosition(20f, Math.max(10f, scene.getHeight() - getHeight() - 20f));
        }

        private void build() {
            cont.clearChildren();
            cont.top().left();
            CheckBox filter = new CheckBox("Filter out empty cell");
            filter.setChecked(ignoreEmptyCell);
            filter.changed(() -> {
                ignoreEmptyCell = filter.isChecked();
                rebuildRows();
            });
            cont.add(filter).left().pad(4f).row();
            cont.add(new ScrollPane(rows, ArcStyles.scrollPane)).grow().minHeight(120f);
            rebuildRows();
        }

        private void rebuildRows() {
            rows.clearChildren();
            rows.top().left().defaults().left().height(42f).pad(2f);

            int index = 0;
            for (Cell<?> cell : inspected.getCells()) {
                Element element = cell.get();
                String label = "#" + index++ + " " + name(element);
                if (!ignoreEmptyCell || cell.hasElement()) {
                    Table item = new Table(ArcStyles.overlay);
                    item.left().margin(4f);
                    item.add(label).growX().left();
                    item.clicked(() -> new CellDetails(cell).showWindow());
                    addFocusSource(item, cell::get, cell);
                    rows.add(item).growX().colspan(Math.max(1, CellAccess.colspan(cell)));
                }
                if (CellAccess.endRow(cell)) rows.row();
            }
            rows.invalidateHierarchy();
        }
    }

    private class ElementDetails extends DisposableInspectorDialog {
        private final Element element;

        ElementDetails(Element element) {
            super(name(element), 20f, 160f, true);
            this.element = element;
            setModal(false);
            setMovable(true);
            setResizable(true);
            setCentered(false);
            closeOnBack();
            addFocusSource(this, () -> element, null);
            build();
        }

        void showWindow() {
            show(scene);
            setSize(Scl.scl(360f), Math.min(Scl.scl(620f), scene.getHeight() - 20f));
            setPosition(Math.max(10f, scene.getWidth() - getWidth() - 10f), 10f);
        }

        private void build() {
            cont.clearChildren();
            cont.left().defaults().growX().left().height(30f).pad(2f);
            floatField("x", () -> element.x, value -> element.x = value);
            floatField("y", () -> element.y, value -> element.y = value);
            floatField("originX", () -> element.originX, value -> element.originX = value);
            floatField("originY", () -> element.originY, value -> element.originY = value);
            floatField("width", element::getWidth, element::setWidth);
            floatField("height", element::getHeight, element::setHeight);
            readOnly("prefWidth", element::getPrefWidth);
            readOnly("prefHeight", element::getPrefHeight);
            floatField("rotation", element::getRotation, element::setRotation);
            boolField("fillParent", () -> element.fillParent, value -> element.fillParent = value);
            boolField("visible", () -> element.visible, value -> element.visible = value);
            if (element instanceof Group group) {
                boolField("transform", group::isTransform, group::setTransform);
            }
            if (element instanceof Label label) {
                boolField("wrap", () -> Reflect.get(Label.class, label, "wrap"), label::setWrap);
            }
            Cell<?> cell = CellAccess.cell(element);
            if (cell != null) {
                cont.table(padding -> {
                    padding.defaults().growX().height(32f);
                    padding.add();
                    cellPadField(padding, cell, "padTop", () -> CellAccess.padTop(cell), value -> CellAccess.padTop(cell, value));
                    padding.add().row();
                    cellPadField(padding, cell, "padLeft", () -> CellAccess.padLeft(cell), value -> CellAccess.padLeft(cell, value));
                    padding.add(element instanceof Label ? "Label" : name(element)).labelAlign(Align.center);
                    cellPadField(padding, cell, "padRight", () -> CellAccess.padRight(cell), value -> CellAccess.padRight(cell, value));
                    padding.row();
                    padding.add();
                    cellPadField(padding, cell, "padBottom", () -> CellAccess.padBottom(cell), value -> CellAccess.padBottom(cell, value));
                    padding.add();
                }).growX().row();
                cont.table(grow -> {
                    grow.button("GrowX", ArcStyles.textButton, () -> {
                        cell.growX();
                        CellAccess.flush(cell);
                    }).growX();
                    grow.button("GrowY", ArcStyles.textButton, () -> {
                        cell.growY();
                        CellAccess.flush(cell);
                    }).growX();
                }).growX().row();
            }
            cont.button("Invalidate", ArcStyles.textButton, element::invalidate).row();
            cont.button("Invalidate Hierarchy", ArcStyles.textButton, element::invalidateHierarchy).row();
            cont.button("Layout", ArcStyles.textButton, element::layout).row();
            cont.button("Pack", ArcStyles.textButton, element::pack).row();
        }

        private void floatField(String label, Floatp getter, Floatc setter) {
            cont.table(t -> {
                t.add(label).width(100f).left();
                TextField field = new TextField(Strings.fixed(getter.get(), 2));
                field.setValidator(Strings::canParseFloat);
                field.changed(() -> {
                    if (field.isValid()) setter.get(Strings.parseFloat(field.getText(), getter.get()));
                });
                field.update(() -> {
                    if (!field.hasKeyboard()) field.setText(Strings.fixed(getter.get(), 2));
                });
                t.add(field).growX();
            }).growX().row();
        }

        private void readOnly(String label, Floatp getter) {
            cont.table(t -> {
                t.add(label).width(100f).left();
                t.label(() -> Strings.fixed(getter.get(), 2)).growX().right();
            }).growX().row();
        }

        private void boolField(String label, Boolp getter, Boolc setter) {
            CheckBox box = new CheckBox(label);
            box.update(() -> box.setChecked(getter.get()));
            box.changed(() -> setter.get(box.isChecked()));
            cont.add(box).growX().row();
        }

        private void cellPadField(Table table, Cell<?> cell, String label, Floatp getter, Floatc setter) {
            table.table(item -> {
                item.add(label).fontScale(0.75f).row();
                TextField field = new TextField(Strings.fixed(getter.get(), 2));
                field.setValidator(Strings::canParseFloat);
                field.changed(() -> {
                    if (field.isValid()) {
                        setter.get(Strings.parseFloat(field.getText(), getter.get()));
                        CellAccess.flush(cell);
                    }
                });
                field.update(() -> {
                    if (!field.hasKeyboard()) field.setText(Strings.fixed(getter.get(), 2));
                });
                item.add(field).growX();
            }).growX();
        }
    }

    private class CellDetails extends DisposableInspectorDialog {
        private final Cell<?> cell;

        CellDetails(Cell<?> cell) {
            super("Cell", 220f, 0f, false);
            this.cell = cell;
            setModal(false);
            setMovable(true);
            setResizable(true);
            setCentered(false);
            closeOnBack();
            addFocusSource(this, cell::get, cell);
            build();
        }

        void showWindow() {
            show(scene);
            setSize(Scl.scl(360f), Math.min(Scl.scl(560f), scene.getHeight() - 20f));
            setPosition(Math.max(10f, scene.getWidth() - getWidth() - 10f), 10f);
        }

        private void build() {
            cont.clearChildren();
            cont.left().defaults().growX().left().height(30f).pad(2f);
            title.update(() -> title.setText("Cell" + (cell.hasElement() ? ": " + name(cell.get()) : "")));
            TextButton placeholder = new TextButton("<VALUE>", ArcStyles.toolTextButton);
            ObjectValue detailValue = new ObjectValue(Element.class, cell, cell::get, null);
            Cell<?> valueCell = cont.add(placeholder).growX();
            placeholder.clicked(() -> valueCell.setElement(detailValue));
            detailValue.clicked(() -> valueCell.setElement(placeholder));
            cont.row();
            cellFloat("padTop", () -> CellAccess.padTop(cell), value -> CellAccess.padTop(cell, value));
            cellFloat("padLeft", () -> CellAccess.padLeft(cell), value -> CellAccess.padLeft(cell, value));
            cellFloat("padBottom", () -> CellAccess.padBottom(cell), value -> CellAccess.padBottom(cell, value));
            cellFloat("padRight", () -> CellAccess.padRight(cell), value -> CellAccess.padRight(cell, value));
            cellFloat("minWidth", () -> CellAccess.minWidth(cell), value -> CellAccess.minWidth(cell, value));
            cellFloat("minHeight", () -> CellAccess.minHeight(cell), value -> CellAccess.minHeight(cell, value));
            cellFloat("maxWidth", () -> CellAccess.maxWidth(cell), value -> CellAccess.maxWidth(cell, value));
            cellFloat("maxHeight", () -> CellAccess.maxHeight(cell), value -> CellAccess.maxHeight(cell, value));
            cellFloat("colspan", () -> CellAccess.colspan(cell), value -> CellAccess.colspan(cell, Math.max(1, Math.round(value))));
            cellBool("fillX", () -> CellAccess.fillX(cell) != 0f, value -> CellAccess.fillX(cell, value ? 1f : 0f), () -> CellAccess.fillX(cell), value -> CellAccess.fillX(cell, value), false);
            cellBool("fillY", () -> CellAccess.fillY(cell) != 0f, value -> CellAccess.fillY(cell, value ? 1f : 0f), () -> CellAccess.fillY(cell), value -> CellAccess.fillY(cell, value), false);
            cellBool("expandX", () -> CellAccess.expandX(cell) != 0, value -> CellAccess.expandX(cell, value ? 1 : 0), () -> CellAccess.expandX(cell), value -> CellAccess.expandX(cell, Math.round(value)), true);
            cellBool("expandY", () -> CellAccess.expandY(cell) != 0, value -> CellAccess.expandY(cell, value ? 1 : 0), () -> CellAccess.expandY(cell), value -> CellAccess.expandY(cell, Math.round(value)), true);
            cellBool("uniformX", () -> CellAccess.uniformX(cell), value -> CellAccess.uniformX(cell, value));
            cellBool("uniformY", () -> CellAccess.uniformY(cell), value -> CellAccess.uniformY(cell, value));
            cellBool("endRow", () -> CellAccess.endRow(cell), value -> CellAccess.endRow(cell, value));
            cont.table(t -> {
                t.defaults().height(30f).padRight(2f);
                t.button("Center", ArcStyles.textButton, () -> setAlign(Align.center));
                t.button("Left", ArcStyles.textButton, () -> setAlign((CellAccess.align(cell) | Align.left) & ~Align.right));
                t.button("Right", ArcStyles.textButton, () -> setAlign((CellAccess.align(cell) | Align.right) & ~Align.left));
                t.button("Top", ArcStyles.textButton, () -> setAlign((CellAccess.align(cell) | Align.top) & ~Align.bottom));
                t.button("Bottom", ArcStyles.textButton, () -> setAlign((CellAccess.align(cell) | Align.bottom) & ~Align.top));
            }).growX().row();
            update(() -> {
                if (detailValue.getScene() == scene && !detailValue.hasKeyboard()) detailValue.refresh();
            });
            cont.table(t -> {
                t.defaults().height(30f).padRight(2f);
                t.button("Layout", ArcStyles.textButton, () -> cell.getTable().layout());
                t.button("Invalidate", ArcStyles.textButton, () -> cell.getTable().invalidateHierarchy());
                t.button("GrowX", ArcStyles.textButton, () -> {
                    cell.growX();
                    CellAccess.flush(cell);
                });
                t.button("GrowY", ArcStyles.textButton, () -> {
                    cell.growY();
                    CellAccess.flush(cell);
                });
            }).growX().row();
        }

        private void setAlign(int align) {
            CellAccess.align(cell, align);
            CellAccess.flush(cell);
        }

        private void cellFloat(String label, Floatp getter, Floatc setter) {
            cont.table(t -> {
                t.add(label).width(100f).left();
                TextField field = new TextField(Strings.fixed(getter.get(), 2));
                field.setValidator(Strings::canParseFloat);
                field.changed(() -> {
                    if (field.isValid()) {
                        setter.get(Strings.parseFloat(field.getText(), getter.get()));
                        CellAccess.flush(cell);
                    }
                });
                t.add(field).growX();
            }).growX().row();
        }

        private void cellBool(String label, Boolp getter, Boolc setter) {
            CheckBox box = new CheckBox(label);
            box.update(() -> box.setChecked(getter.get()));
            box.changed(() -> {
                setter.get(box.isChecked());
                CellAccess.flush(cell);
            });
            cont.add(box).growX().row();
        }

        private void cellBool(String label, Boolp getter, Boolc setter, Floatp exact, Floatc setExact, boolean integer) {
            cellBool(label, getter, setter);
            CheckBox box = (CheckBox) cont.getChildren().peek();
            contextMenu(box, () -> {
                InspectorPopup menu = new InspectorPopup();
                TextField field = new TextField(integer ? String.valueOf(Math.round(exact.get())) : Strings.fixed(exact.get(), 2));
                field.setValidator(integer ? Strings::canParseInt : Strings::canParseFloat);
                field.changed(() -> {
                    if (field.isValid()) {
                        setExact.get(Strings.parseFloat(field.getText(), exact.get()));
                        CellAccess.flush(cell);
                    }
                });
                menu.items.add(field).width(140f).height(36f).pad(6f);
                return menu;
            });
        }
    }

    private interface Boolc {
        void get(boolean value);
    }
}
