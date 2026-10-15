package com.github.squi2rel.arctools.ui;

import arc.Core;
import arc.func.Cons;
import arc.input.KeyCode;
import arc.scene.Scene;
import arc.util.Align;

/**
 * The disposable window returned by Tester dialog helpers.
 */
public class JSWindow extends Window implements Window.IDisposable {
    public boolean autoDispose;
    private float elapsed;

    public JSWindow(Scene owner, Cons<Window> cons) {
        super(owner, "TEST", 64f, 64f);
        setMovable(true);
        setResizable(true);
        setCentered(false);
        closeOnBack();
        cons.get(this);
        show();
        setPosition(Core.input.mouseX(), Core.input.mouseY(), Align.center);
        keepInStage();
    }

    @Override
    public JSWindow show() {
        super.show();
        return this;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        elapsed += delta;
        if (autoDispose) {
            if (titleTable.parent != null) {
                getCell(titleTable).height(0f);
                titleTable.remove();
                invalidateHierarchy();
            }
            if (elapsed >= 40f / 60f && Core.input.keyDown(KeyCode.anyKey)) hide();
        }
    }
}
