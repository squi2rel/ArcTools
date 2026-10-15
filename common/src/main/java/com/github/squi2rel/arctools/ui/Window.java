package com.github.squi2rel.arctools.ui;

import arc.Core;
import arc.graphics.Color;
import arc.input.KeyCode;
import arc.math.Interp;
import arc.math.Mathf;
import arc.scene.Action;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.Scene;
import arc.scene.actions.Actions;
import arc.scene.event.*;
import arc.scene.style.Drawable;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.ImageButton;
import arc.scene.ui.ImageButton.ImageButtonStyle;
import arc.scene.ui.Label;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.pooling.Pool;
import com.github.squi2rel.arc.ui.ArcStyles;

/**
 * Shared movable tool window and keyboard window manager.
 */
public class Window extends Table {
    public static final Seq<Window> all = new Seq<>();
    public static Window frontWindow;
    public static final float titleHeight = 45f;
    public static final float titleButtonSize = 38f;
    private static final int moveEdge = 1 << 5;
    private static final ObjectMap<Scene, WindowState> states = new ObjectMap<>();
    private final Seq<InputListener> globalShortcuts = new Seq<>();
    private final Seq<Runnable> shownListeners = new Seq<>(), hiddenListeners = new Seq<>();

    public final Table titleTable;
    public final Table cont = new Table(ArcStyles.panel);
    public final Table buttons = new Table(ArcStyles.panel);
    public final Label title;
    private final BindCell contentCell, buttonsCell;

    protected final Scene owner;
    public final boolean full;
    public final boolean noButtons;
    private final float minWidth;
    private final float minHeight;
    private boolean modal;
    private boolean movable;
    private boolean resizable;
    private boolean centered = true;
    private boolean keepWithinStage = true;
    private boolean dragging;
    private boolean hiding;
    public boolean sticky;
    private boolean minimized;
    private boolean maximized;
    private int edge;
    private float startX, startY, lastX, lastY;
    private float restoreX, restoreY, restoreWidth, restoreHeight;
    private float uiScale;
    private boolean hasPacked;
    private ImageButton maximizeButton;

    public Window(Scene owner, String titleText, float minWidth, float minHeight, boolean full, boolean noButtons) {
        if (owner == null) throw new IllegalArgumentException("owner scene cannot be null");
        this.owner = owner;
        this.minWidth = minWidth;
        this.minHeight = minHeight;
        uiScale = Scl.scl();
        this.full = full;
        this.noButtons = noButtons;
        install(owner);
        all.add(this);

        titleTable = new Table(tintWhite(ArcStyles.accent.cpy().lerp(Color.gray, 0.6f).a(0.9f)));
        touchable = Touchable.enabled;
        titleTable.touchable = Touchable.enabled;
        if (!Core.app.isMobile() && full) titleTable.addListener(new ClickListener() {
            float firstX, firstY, downX, downY;

            {
                setTapCountInterval(0.3f);
            }

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                if (event.targetActor != titleTable) return false;
                downX = event.stageX;
                downY = event.stageY;
                return super.touchDown(event, x, y, pointer, button);
            }

            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (Mathf.dst(downX, downY, event.stageX, event.stageY) > 35f) {
                    setTapCount(0);
                    return;
                }
                if (getTapCount() % 2 == 0 && Mathf.dst(firstX, firstY, event.stageX, event.stageY) < 35f)
                    toggleMaximize();
                else setTapCount(1);
                firstX = event.stageX;
                firstY = event.stageY;
            }
        });
        setClip(false);
        left().defaults().left();

        title = new Label(titleText, new Label.LabelStyle(ArcStyles.dialog.titleFont, Color.white));
        title.setEllipsis(true);
        title.update(() -> title.setColor(front(owner) == this ? Color.white : Color.lightGray));

        titleTable.margin(0f);
        add(titleTable).growX().height(titleHeight).name("titleTable");
        row();

        titleTable.add(title).grow().touchable(Touchable.disabled).padLeft(6f).padRight(6f).minWidth(0f);
        titleTable.defaults().size(titleButtonSize);

        if (full) {
            titleTable.button(Icons.pin, toggleImageButtonStyle(), 22f, () -> sticky = !sticky)
                    .padLeft(4f).name("sticky").checked(button -> sticky);
            titleTable.button(Icons.minimize, ArcStyles.toolImageButton, 22f, this::toggleMinimize)
                    .padLeft(4f).name("minimize");
            maximizeButton = titleTable.button(Icons.maximize, ArcStyles.toolImageButton, 22f, this::toggleMaximize)
                    .disabled(button -> !isShown()).padLeft(4f).name("maximize").get();
            maximizeButton.update(() -> maximizeButton.getStyle().imageUp = maximized ? Icons.restore : Icons.maximize);
        }
        titleTable.button(Icons.close, closeButtonStyle(), 22f, this::hide).padLeft(4f).padRight(4f).name("close");

        cont.margin(6f);
        buttons.margin(0f);
        contentCell = BindCell.ofConst(add(cont).name("cont").grow().top());
        row();
        buttonsCell = noButtons ? null : BindCell.ofConst(add(buttons).name("buttons").growX());
        if (!noButtons) row();

        addCaptureListener(event -> {
            if (hiding && event instanceof InputEvent input && input.type != InputEvent.InputEventType.exit) {
                event.cancel();
                return true;
            }
            return false;
        });
        addCaptureListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                activate();
                return false;
            }
        });
        addCaptureListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                if (button == KeyCode.mouseLeft) {
                    updateEdge(event, x, y);
                    dragging = edge != 0;
                    startX = x;
                    startY = y;
                    lastX = x - getWidth();
                    lastY = y - getHeight();
                    if (dragging && (edge & moveEdge) == 0) event.stop();
                }
                return edge != 0 || modal;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                if (!dragging) return;
                dragTo(x, y);
                event.stop();
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
                dragging = false;
            }

            @Override
            public boolean mouseMoved(InputEvent event, float x, float y) {
                updateEdge(event, x, y);
                return modal;
            }

            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
                return modal;
            }

            @Override
            public boolean keyDown(InputEvent event, KeyCode keycode) {
                return modal;
            }

            @Override
            public boolean keyUp(InputEvent event, KeyCode keycode) {
                return modal;
            }

            @Override
            public boolean keyTyped(InputEvent event, char character) {
                return modal;
            }
        });
    }

    /**
     * Matches mod-tools: the four-argument form has no bottom button row.
     */
    public Window(Scene owner, String titleText, float minWidth, float minHeight, boolean full) {
        this(owner, titleText, minWidth, minHeight, full, true);
    }

    public Window(Scene owner, String titleText, float minWidth, float minHeight) {
        this(owner, titleText, minWidth, minHeight, false);
    }

    public Window(Scene owner, String titleText) {
        this(owner, titleText, 120f, 80f, false);
    }

    public static void install(Scene scene) {
        if (scene == null || states.containsKey(scene)) return;
        WindowState state = new WindowState(scene);
        states.put(scene, state);
        scene.root.getCaptureListeners().insert(0, state.shortcuts);
        scene.root.addListener(state.keyboardFallback);
    }

    /**
     * Recomputes existing windows after a font reload or UI scale change.
     */
    public static void refreshAll(Scene scene) {
        for (Window window : all) {
            if (window.owner == scene) window.refreshLayout();
        }
    }

    private void refreshLayout() {
        float nextScale = Scl.scl();
        float ratio = nextScale / uiScale;
        uiScale = nextScale;
        if (minimized) {
            refreshDetached(cont, ratio);
            if (buttonsCell != null) refreshDetached(buttons, ratio);
        }
        if (!isShown()) {
            // ArcRuntime only visits elements attached to the scene.
            refreshDetached(this, ratio);
            return;
        }
        if (ratio != 1f && restoreWidth > 0f) {
            float centerX = restoreX + restoreWidth / 2f;
            float centerY = restoreY + restoreHeight / 2f;
            restoreWidth *= ratio;
            restoreHeight *= ratio;
            restoreX = centerX - restoreWidth / 2f;
            restoreY = centerY - restoreHeight / 2f;
        }
        invalidateHierarchy();
        if (maximized && !minimized) {
            setBounds(0f, 0f, owner.getWidth(), owner.getHeight());
        } else if (minimized) {
            float height = Scl.scl(titleHeight);
            if (maximized) setBounds(x, y + this.height - height, getMinWidth(), height);
            else setBounds(restoreX, restoreY + restoreHeight - height, getMinWidth(), height);
        } else {
            float centerX = x + width / 2f;
            float centerY = y + height / 2f;
            float width = Math.min(owner.getWidth(), Math.max(this.width * ratio, getPrefWidth()));
            float height = Math.min(owner.getHeight(), Math.max(this.height * ratio, getPrefHeight()));
            setBounds(centerX - width / 2f, centerY - height / 2f, width, height);
        }
        if (keepWithinStage) keepInStage();
    }

    private static void refreshDetached(Element element, float ratio) {
        if (element instanceof Table table) table.rescale(ratio);
        element.invalidate();
        if (element instanceof TextField field) field.refreshFontMetrics();
        if (element instanceof Group group) {
            for (Element child : group.getChildren()) refreshDetached(child, ratio);
        }
    }

    public static Window front(Scene scene) {
        if (scene == null) return null;
        install(scene);
        WindowState state = states.get(scene);
        state.front = topWindow(scene);
        frontWindow = state.front;
        return state.front;
    }

    public static boolean switchWindow(Scene scene, boolean previous) {
        Seq<Window> windows = shownWindows(scene);
        if (windows.isEmpty()) return false;
        Window current = front(scene);
        int index = windows.indexOf(current, true);
        if (index < 0) index = previous ? 0 : windows.size - 1;
        Window target = windows.get(Mathf.mod(index + (previous ? -1 : 1), windows.size));
        target.activate();
        target.requestKeyboard();
        return true;
    }

    public static boolean closeFront(Scene scene) {
        Window current = front(scene);
        if (current == null) return false;
        current.hide();
        return true;
    }

    private static Seq<Window> shownWindows(Scene scene) {
        Seq<Window> windows = new Seq<>();
        if (scene == null) return windows;
        for (Element child : scene.root.getChildren()) {
            if (child instanceof Window window && window.isShown() && !window.hiding) windows.add(window);
        }
        return windows;
    }

    private static Window topWindow(Scene scene) {
        Seq<Element> children = scene.root.getChildren();
        for (int i = children.size - 1; i >= 0; i--) {
            if (children.get(i) instanceof Window window && window.isShown() && !window.hiding) return window;
        }
        return null;
    }

    private static ImageButtonStyle toggleImageButtonStyle() {
        ImageButtonStyle style = new ImageButtonStyle(ArcStyles.toolImageButton);
        style.checked = ArcStyles.selection;
        style.imageChecked = Icons.pin;
        style.imageCheckedColor = ArcStyles.accent;
        return style;
    }

    private static ImageButtonStyle closeButtonStyle() {
        ImageButtonStyle style = new ImageButtonStyle(ArcStyles.toolImageButton);
        style.over = tintWhite(ArcStyles.error);
        style.down = tintWhite(ArcStyles.error.cpy().lerp(Color.gray, 0.3f));
        return style;
    }

    private static Drawable tintWhite(Color color) {
        return ((TextureRegionDrawable) ArcStyles.white).tint(color);
    }

    private void updateEdge(InputEvent event, float x, float y) {
        edge = 0;
        if (minimized) {
            if (movable && event.targetActor == titleTable) edge = moveEdge;
            return;
        }

        int resizeBorder = 8;
        float border = Scl.scl(resizeBorder);
        float width = getWidth(), height = getHeight();
        if (resizable && x >= 0f && x <= width && y >= 0f && y <= height) {
            if (Math.abs(x) < border) edge |= Align.left;
            if (Math.abs(x - width) < border) edge |= Align.right;
            if (Math.abs(y) < border) edge |= Align.bottom;
            if (Math.abs(y - height) < border) edge |= Align.top;
        }
        if (movable && edge == 0 && y <= height && y >= height - Scl.scl(titleHeight) && event.targetActor == titleTable) {
            edge = moveEdge;
        }
    }

    private void dragTo(float x, float y) {
        if ((edge & moveEdge) != 0 && maximized && !minimized) {
            float mulxw = startX / width, mulxh = startY / height;
            float mouseX = this.x + x, mouseY = this.y + y;
            toggleMaximize();
            startX = width * mulxw;
            startY = height * mulxh;
            setPosition(mouseX - startX, mouseY - startY);
            x = startX;
            y = startY;
        }
        float width = getWidth(), height = getHeight();
        float windowX = this.x, windowY = this.y;
        Scene stage = getScene();
        boolean clampPosition = keepWithinStage && stage != null && parent == stage.root;

        if ((edge & moveEdge) != 0) {
            windowX += x - startX;
            windowY += y - startY;
        }
        if ((edge & Align.left) != 0) {
            float amountX = x - startX;
            if (width - amountX < getMinWidth()) amountX = -(getMinWidth() - width);
            if (clampPosition && windowX + amountX < 0f) amountX = -windowX;
            width -= amountX;
            windowX += amountX;
        }
        if ((edge & Align.bottom) != 0) {
            float amountY = y - startY;
            if (height - amountY < getMinHeight()) amountY = -(getMinHeight() - height);
            if (clampPosition && windowY + amountY < 0f) amountY = -windowY;
            height -= amountY;
            windowY += amountY;
        }
        if ((edge & Align.right) != 0) {
            float amountX = x - lastX - width;
            if (width + amountX < getMinWidth()) amountX = getMinWidth() - width;
            if (clampPosition && windowX + width + amountX > stage.getWidth())
                amountX = stage.getWidth() - windowX - width;
            width += amountX;
        }
        if ((edge & Align.top) != 0) {
            float amountY = y - lastY - height;
            if (height + amountY < getMinHeight()) amountY = getMinHeight() - height;
            if (clampPosition && windowY + height + amountY > stage.getHeight())
                amountY = stage.getHeight() - windowY - height;
            height += amountY;
        }
        setBounds(Math.round(windowX), Math.round(windowY), Math.round(width), Math.round(height));
    }

    public void toggleMinimize() {
        float scaledTitleHeight = Scl.scl(titleHeight);
        minimized = !minimized;
        if (minimized) {
            if (!maximized) storeRestoreBounds();
            contentCell.remove();
            if (buttonsCell != null) buttonsCell.remove();
            setBounds(x, y + restoreHeight - scaledTitleHeight, getMinWidth(), scaledTitleHeight);
        } else {
            contentCell.build();
            if (buttonsCell != null) buttonsCell.build();
            if (maximized) setBounds(0f, 0f, owner.getWidth(), owner.getHeight());
            else setBounds(restoreX = x, restoreY = y - restoreHeight + scaledTitleHeight, restoreWidth, restoreHeight);
        }
        invalidateHierarchy();
    }

    public void toggleMaximize() {
        boolean lastMin = minimized;
        if (minimized) toggleMinimize();
        maximized = !maximized;
        if (maximized) {
            if (!lastMin) storeRestoreBounds();
            setBounds(0f, 0f, owner.getWidth(), owner.getHeight());
        } else {
            setBounds(restoreX, restoreY, restoreWidth, restoreHeight);
        }
        invalidateHierarchy();
    }

    private void storeRestoreBounds() {
        restoreX = x;
        restoreY = y;
        restoreWidth = width;
        restoreHeight = height;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (sticky) toFront();
        if (getScene() != null) {
            if (maximized && !minimized) {
                setBounds(0f, 0f, owner.getWidth(), owner.getHeight());
            } else {
                if (keepWithinStage) keepInStage();
                if (centered && !movable && getActions().size == 0) {
                    setPosition(Math.round((owner.getWidth() - getWidth()) / 2f), Math.round((owner.getHeight() - getHeight()) / 2f));
                }
            }
        }
    }

    @Override
    public Element hit(float x, float y, boolean touchable) {
        Element hit = super.hit(x, y, touchable);
        if (hit == null && getScene() != null) {
            Element scroll = owner.getScrollFocus();
            if (scroll != null && scroll.isDescendantOf(this)) owner.setScrollFocus(null);
        }
        return hit;
    }

    @Override
    public float getMinWidth() {
        return Math.max(Scl.scl(minWidth), super.getMinWidth());
    }

    @Override
    public float getMinHeight() {
        return Math.max(Scl.scl(minHeight), super.getMinHeight());
    }

    @Override
    public float getPrefWidth() {
        return Math.min(owner.getWidth(), Math.max(super.getPrefWidth(), getMinWidth()));
    }

    @Override
    public float getPrefHeight() {
        return Math.min(owner.getHeight(), Math.max(super.getPrefHeight(), getMinHeight()));
    }

    public void setModal(boolean modal) {
        this.modal = modal;
    }

    public void setMovable(boolean movable) {
        this.movable = movable;
    }

    public void setResizable(boolean resizable) {
        this.resizable = resizable;
    }

    public void setCentered(boolean centered) {
        this.centered = centered;
    }

    public void setKeepWithinStage(boolean keepWithinStage) {
        this.keepWithinStage = keepWithinStage;
    }

    public boolean isShown() {
        return getScene() != null;
    }

    public boolean isHiding() {
        return hiding;
    }

    public boolean isMinimized() {
        return minimized;
    }

    public boolean isMaximized() {
        return maximized;
    }

    public void display() {
        visible = true;
        touchable = Touchable.enabled;
        invalidateHierarchy();
        show(owner, null);
        if (minimized) toggleMinimize();
        clearActions();
        color.a = 1f;
        if (keepWithinStage) keepInStage();
        owner.setKeyboardFocus(this);
    }

    @Override
    public void clear() {
        super.clear();
        all.remove(this, true);
    }

    public Window show(Scene stage, Action action) {
        if (!all.contains(this, true)) all.add(this);
        if (isShown() && !hiding) {
            activate();
            if (minimized) toggleMinimize();
            return this;
        }
        setOrigin(Align.center);
        setClip(false);
        clearActions();
        hiding = false;
        color.a = 1f;
        if (minimized) toggleMinimize();
        if (!hasPacked) {
            pack();
            hasPacked = true;
        }
        if (keepWithinStage) {
            setSize(Math.min(getWidth(), stage.getWidth()), Math.min(getHeight(), stage.getHeight()));
        }
        stage.add(this);
        if (centered && !movable) {
            setPosition(Math.round((owner.getWidth() - getWidth()) / 2f), Math.round((owner.getHeight() - getHeight()) / 2f));
        }
        activate();
        if (action != null) addAction(action);
        shownListeners.copy().each(Runnable::run);
        if (keepWithinStage) keepInStage();
        return this;
    }

    public Window show(Scene stage) {
        return show(stage, Actions.sequence(Actions.alpha(0f), Actions.fadeIn(0.2f, Interp.fade)));
    }

    public Window show() {
        return show(owner);
    }

    public void shown(Runnable run) {
        shownListeners.add(run);
    }

    public void hidden(Runnable run) {
        hiddenListeners.add(run);
    }

    public void activate() {
        if (hiding) return;
        if (isShown()) toFront();
        WindowState state = states.get(owner);
        if (state != null) {
            state.front = this;
            state.keyboardActive = true;
        }
        frontWindow = this;
    }

    public void hide() {
        if (!isShown() || hiding) return;
        hiding = true;
        dragging = false;
        owner.unfocus(this);
        clearActions();
        addAction(Actions.sequence(Actions.fadeOut(0.2f, Interp.fade), Actions.run(() -> {
            remove();
            if (this instanceof IDisposable disposable) {
                all.remove(this, true);
                disposable.clearAll();
            }
        })));
        hiddenListeners.copy().each(Runnable::run);
        WindowState state = states.get(owner);
        if (state != null && state.front == this) {
            state.front = topWindow(owner);
            frontWindow = state.front;
        }
    }

    public boolean focusSearch(boolean clear) {
        return false;
    }

    /**
     * Keeps explicit shortcuts available after an empty-space click releases window keyboard input.
     */
    public void addGlobalShortcutListener(InputListener listener) {
        globalShortcuts.add(listener);
        addCaptureListener(listener);
    }

    public void closeOnBack() {
        keyDown(key -> {
            if (key == KeyCode.escape || key == KeyCode.back) Core.app.post(this::hide);
        });
    }

    public interface IDisposable {
        default void clearAll() {
            if (!(this instanceof Group group)) return;
            Seq<Element> elements = new Seq<>();
            elements.add(group);
            group.forEach(elements::add);
            if (group instanceof Window window) {
                for (Table table : Seq.with(window.cont, window.buttons, window.titleTable)) {
                    if (elements.contains(table, true)) continue;
                    elements.add(table);
                    table.forEach(elements::add);
                }
                window.globalShortcuts.clear();
                window.shownListeners.clear();
                window.hiddenListeners.clear();
            }
            for (Element element : elements) {
                element.update(null);
                element.visibility = null;
                element.touchablility = null;
                element.userObject = null;
                element.clear();
                if (element instanceof Pool.Poolable poolable) poolable.reset();
            }
        }
    }

    private static final class WindowState {
        final Scene scene;
        final InputListener shortcuts;
        final EventListener keyboardFallback;
        Window front;
        boolean keyboardActive = true;

        WindowState(Scene scene) {
            this.scene = scene;
            this.keyboardFallback = event -> event instanceof InputEvent input && forwardKeyboard(input);
            this.shortcuts = new InputListener() {
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                    if (event.targetActor == scene.root) {
                        keyboardActive = false;
                        Element keyboard = scene.getKeyboardFocus();
                        if (keyboard != null && keyboard.isDescendantOf(element -> element instanceof Window))
                            scene.setKeyboardFocus(null);
                        Element scroll = scene.getScrollFocus();
                        if (scroll != null && scroll.isDescendantOf(element -> element instanceof Window))
                            scene.setScrollFocus(null);
                    }
                    return false;
                }

                @Override
                public boolean keyDown(InputEvent event, KeyCode keycode) {
                    boolean ctrl = Core.input.ctrl();
                    boolean shift = Core.input.shift();
                    if (keycode == KeyCode.tab && ctrl) {
                        if (switchWindow(scene, shift)) event.stop();
                        return true;
                    }
                    if (keycode == KeyCode.f4 && shift) {
                        if (closeFront(scene)) event.stop();
                        return true;
                    }
                    if (keycode == KeyCode.f && ctrl) {
                        Window current = front(scene);
                        if (current != null && current.focusSearch(shift)) {
                            current.activate();
                            event.stop();
                            return true;
                        }
                    }
                    return false;
                }
            };
        }

        private boolean forwardKeyboard(InputEvent event) {
            if (event.targetActor != scene.root) return false;
            switch (event.type) {
                case keyDown, keyUp, keyTyped -> {
                }
                default -> {
                    return false;
                }
            }
            Window current = front(scene);
            if (current == null || current.minimized || !current.visible) return false;

            InputEvent forwarded = new InputEvent();
            forwarded.type = event.type;
            forwarded.keyCode = event.keyCode;
            forwarded.character = event.character;
            forwarded.targetActor = current;
            if (keyboardActive) {
                current.notify(forwarded, true);
                if (!forwarded.stopped) current.notify(forwarded, false);
            } else {
                forwarded.listenerActor = current;
                forwarded.capture = true;
                for (InputListener listener : current.globalShortcuts) {
                    if (listener.handle(forwarded)) forwarded.handle();
                }
            }
            if (forwarded.cancelled) event.cancel();
            else if (forwarded.stopped) event.stop();
            return forwarded.handled;
        }
    }
}
