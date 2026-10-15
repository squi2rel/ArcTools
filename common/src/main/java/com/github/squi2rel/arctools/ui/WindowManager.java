package com.github.squi2rel.arctools.ui;

import arc.scene.Scene;
import arc.scene.event.ClickListener;
import arc.scene.event.InputEvent;
import arc.scene.event.Touchable;
import arc.scene.ui.ImageButton;
import arc.scene.ui.ImageButton.ImageButtonStyle;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import com.github.squi2rel.arc.ui.ArcStyles;

public final class WindowManager {
    private WindowManager() {
    }

    public static void show(Scene scene) {
        for (Window window : Window.all) {
            if (window instanceof ManagerWindow manager && window.owner == scene) {
                manager.open();
                return;
            }
        }
        new ManagerWindow(scene).open();
    }

    private static final class ManagerWindow extends Window {
        private final Table list = new Table();
        private final Seq<Window> listed = new Seq<>();
        private boolean positioned;

        ManagerWindow(Scene scene) {
            super(scene, "Window Manager", 240f, 160f, true);
            name = "arctools-window-manager";
            setModal(false);
            setMovable(true);
            setResizable(true);
            setCentered(false);
            closeOnBack();
            list.top().left();
            ScrollPane pane = new ScrollPane(list, ArcStyles.scrollPane);
            pane.setScrollingDisabled(true, false);
            cont.add(pane).grow().minSize(0f);
            getCell(cont).minSize(0f);
        }

        void open() {
            rebuild();
            visible = true;
            touchable = Touchable.enabled;
            invalidateHierarchy();
            show(owner);
            if (isMinimized()) toggleMinimize();
            owner.setKeyboardFocus(this);
            if (!positioned) {
                positioned = true;
                setSize(Math.min(Scl.scl(300f), owner.getWidth()), Math.min(Scl.scl(400f), owner.getHeight()));
                setPosition((owner.getWidth() - getWidth()) / 2f, (owner.getHeight() - getHeight()) / 2f);
            }
            keepInStage();
        }

        @Override
        public void act(float delta) {
            super.act(delta);
            if (windowsChanged()) rebuild();
        }

        private boolean windowsChanged() {
            int index = 0;
            for (Window window : Window.all) {
                if (window == this || window.owner != owner) continue;
                if (index >= listed.size || listed.get(index++) != window) return true;
            }
            return index != listed.size;
        }

        private void rebuild() {
            list.clearChildren();
            listed.clear();
            for (Window window : Window.all) {
                if (window == this || window.owner != owner) continue;
                listed.add(window);
                addWindow(window);
            }
            if (listed.isEmpty()) list.add("No windows").pad(8f);
        }

        private void addWindow(Window window) {
            Table row = new Table(ArcStyles.panel);
            row.name = "window-entry";
            row.touchable = Touchable.enabled;
            row.defaults().pad(2f);
            Table text = new Table();
            text.touchable = Touchable.disabled;
            text.label(window.title::getText).growX().minWidth(0f).left().get().setEllipsis(true);
            text.row();
            text.label(() -> !window.isShown() || !window.visible ? "Hidden" : window.isMinimized() ? "Minimized" : "Shown")
                    .left().get().setFontScale(0.75f);
            row.add(text).growX().minWidth(0f).height(42f).padLeft(8f);
            row.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (event.targetActor == row && getTapCount() % 2 == 0) window.display();
                }
            });
            row.button(Icons.watch, ArcStyles.toolImageButton, 22f, window::display)
                    .size(32f).name("show").tooltip("Show window");
            if (window.full) {
                ImageButton pin = window.titleTable.find("sticky");
                if (pin != null) {
                    row.button(pin.getStyle().imageUp, pin.getStyle(), 22f, () -> window.sticky = !window.sticky)
                            .size(32f).checked(button -> window.sticky).name("pin").tooltip("Keep on top");
                }
                ImageButton maximize = window.titleTable.find("maximize");
                if (maximize != null) {
                    ImageButtonStyle style = new ImageButtonStyle(maximize.getStyle());
                    row.button(style.imageUp, style, 22f, () -> {
                                window.display();
                                window.toggleMaximize();
                            }).size(32f).update(button -> button.getStyle().imageUp = maximize.getStyle().imageUp)
                            .name("maximize").tooltip("Maximize / restore");
                }
            }
            ImageButton close = window.titleTable.find("close");
            ImageButtonStyle closeStyle = close == null ? ArcStyles.toolImageButton : close.getStyle();
            row.button(Icons.cancel, closeStyle, 22f, window::hide).size(32f).name("close").tooltip("Close");
            row.button(Icons.delete, closeStyle, 22f, () -> {
                window.hide();
                Window.all.remove(window, true);
                rebuild();
            }).size(32f).name("remove").tooltip("Remove from manager");
            list.add(row).growX().minWidth(0f).padBottom(3f).row();
        }
    }
}
