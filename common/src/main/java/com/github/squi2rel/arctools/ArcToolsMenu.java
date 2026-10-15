package com.github.squi2rel.arctools;

import arc.input.KeyCode;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.Scene;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.Label;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import com.github.squi2rel.arc.ui.ArcStyles;
import com.github.squi2rel.arctools.inspect.ArcInspector;
import com.github.squi2rel.arctools.ui.Icons;
import com.github.squi2rel.arctools.ui.WindowManager;

public final class ArcToolsMenu {
    private static final String menuName = "arctools-global-menu";

    private ArcToolsMenu() {
    }

    public static void install(Scene scene) {
        for (Element child : scene.root.getChildren()) {
            if (menuName.equals(child.name)) return;
        }
        Menu menu = new Menu(scene);
        menu.name = menuName;
        scene.root.addChild(menu);
    }

    public static void rebuild(Scene scene) {
        for (Element child : scene.root.getChildren()) {
            if (child instanceof Menu menu) {
                Menu replacement = new Menu(scene, menu.collapsed);
                replacement.name = menuName;
                replacement.positioned = menu.positioned;
                replacement.setPosition(menu.x, menu.y);
                menu.remove();
                scene.root.addChild(replacement);
                if (replacement.positioned) replacement.clamp();
                return;
            }
        }
    }

    private static class Menu extends Table {
        private static final float margin = 8f;

        private final Scene scene;
        private boolean collapsed;
        private boolean positioned;
        private boolean dragging;
        private float dragStartX;
        private float dragStartY;
        private float menuStartX;
        private float menuStartY;

        Menu(Scene scene) {
            this(scene, false);
        }

        Menu(Scene scene, boolean collapsed) {
            super(ArcStyles.translucentPanel);
            this.scene = scene;
            this.collapsed = collapsed;
            touchable = Touchable.childrenOnly;
            rebuild();
            update(this::keepOnScreen);
        }

        private void rebuild() {
            clearChildren();
            top().left();
            defaults().growX().left().pad(2f);

            Table header = new Table(ArcStyles.overlay);
            header.touchable = Touchable.enabled;
            header.defaults().height(24f).pad(1f);
            header.add(new Label(collapsed ? "Tools" : "Arc Tools")).growX().left().padLeft(6f);
            header.button(collapsed ? "+" : "-", ArcStyles.toolTextButton, () -> {
                collapsed = !collapsed;
                rebuild();
            }).size(24f);
            header.addListener(new InputListener() {
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                    if (button != KeyCode.mouseLeft) return false;
                    dragging = false;
                    dragStartX = event.stageX;
                    dragStartY = event.stageY;
                    menuStartX = Menu.this.x;
                    menuStartY = Menu.this.y;
                    return true;
                }

                @Override
                public void touchDragged(InputEvent event, float x, float y, int pointer) {
                    dragging = true;
                    setPosition(menuStartX + event.stageX - dragStartX, menuStartY + event.stageY - dragStartY);
                    clamp();
                    event.stop();
                }

                @Override
                public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
                    dragging = false;
                }
            });
            add(header).minWidth(collapsed ? 92f : 118f).growX().row();

            if (!collapsed) {
                button("Inspect", Icons.code, ArcStyles.textButton, 18f, ArcToolsClient::beginInspect).row();
                button("UI List", Icons.image, ArcStyles.textButton, 18f, () -> ShowUIList.show(scene)).row();
                button("Tester", Icons.terminal, ArcStyles.textButton, 18f, () -> ArcInspector.install(scene).showTester()).row();
                button("Windows", Icons.windows, ArcStyles.textButton, 18f, () -> WindowManager.show(scene)).name("arctools-open-window-manager").row();
                button("Settings", Icons.settings, ArcStyles.textButton, 18f, () -> ArcInspector.install(scene).showSettings()).name("arctools-open-settings").row();
                for (Element child : getChildren()) {
                    if (child instanceof TextButton button) {
                        button.margin(5f);
                        button.getCells().first().padRight(8f);
                        button.getLabelCell().labelAlign(Align.left);
                    }
                }
            }

            pack();
            if (positioned) clamp();
        }

        private void keepOnScreen() {
            if (scene.getWidth() <= 0f || scene.getHeight() <= 0f) return;
            pack();
            if (!positioned) {
                positioned = true;
                setPosition(scene.getWidth() - getWidth() - margin, scene.getHeight() - getHeight() - margin);
            }
            if (!dragging) clamp();
            toFront();
        }

        private void clamp() {
            setPosition(
                    Mathf.clamp(x, margin, Math.max(margin, scene.getWidth() - getWidth() - margin)),
                    Mathf.clamp(y, margin, Math.max(margin, scene.getHeight() - getHeight() - margin))
            );
        }
    }
}
