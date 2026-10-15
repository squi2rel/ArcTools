package com.github.squi2rel.arctools.ui;

import arc.Core;
import arc.func.Cons;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.Scene;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import com.github.squi2rel.arc.ui.ArcStyles;

/**
 * Mouse-positioned dismissible settings panel corresponding to mod-tools' SelectTable.
 */
public final class SelectTable extends Table {
    public final Table table = new Table();
    private final Scene owner;
    private final Runnable hidden;
    private final Window sourceWindow;
    private boolean closing;

    private final InputListener dismissListener = new InputListener() {
        @Override
        public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
            Element target = event.targetActor;
            if (target != SelectTable.this && (target == null || !target.isDescendantOf(SelectTable.this))) {
                for (Element current = target; current != null; current = current.parent) {
                    if (current instanceof Window window && window != sourceWindow) return false;
                }
                Core.app.post(SelectTable.this::hide);
            }
            return false;
        }

        @Override
        public boolean keyDown(InputEvent event, KeyCode keycode) {
            if (keycode == KeyCode.escape || keycode == KeyCode.back) {
                hide();
                return true;
            }
            return false;
        }
    };

    private SelectTable(Scene owner, Cons<Table> builder, Runnable hidden) {
        this.owner = owner;
        this.hidden = hidden;
        sourceWindow = Window.front(owner);
        background(ArcStyles.panel);
        margin(4f);
        builder.get(table);
        ScrollPane pane = new ScrollPane(table, ArcStyles.scrollPane);
        pane.setFadeScrollBars(false);
        add(pane).grow().maxWidth(Math.max(120f, owner.getWidth() - 24f))
                .maxHeight(Math.max(80f, owner.getHeight() - 24f));
    }

    public static SelectTable show(Scene owner, Cons<Table> builder, Runnable hidden) {
        SelectTable popup = new SelectTable(owner, builder, hidden);
        owner.root.addCaptureListener(popup.dismissListener);
        owner.add(popup);
        popup.pack();
        popup.setPosition(
                Mathf.clamp(Core.input.mouseX(), 0f, Math.max(0f, owner.getWidth() - popup.getWidth())),
                Mathf.clamp(Core.input.mouseY() - popup.getHeight(), 0f, Math.max(0f, owner.getHeight() - popup.getHeight())));
        popup.toFront();
        return popup;
    }

    public void hide() {
        if (closing) return;
        closing = true;
        owner.root.removeCaptureListener(dismissListener);
        remove();
        if (hidden != null) hidden.run();
    }
}
