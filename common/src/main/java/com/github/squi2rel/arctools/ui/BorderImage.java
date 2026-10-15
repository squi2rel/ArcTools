package com.github.squi2rel.arctools.ui;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.scene.style.Drawable;
import arc.scene.ui.Image;

/**
 * Image with the same simple outline used by mod-tools' color swatches.
 */
public class BorderImage extends Image {
    private final Color borderColor = new Color(Color.white);
    private float borderWidth;

    public BorderImage(Drawable drawable, float borderWidth) {
        super(drawable);
        this.borderWidth = borderWidth;
    }

    public BorderImage border(Color color) {
        borderColor.set(color);
        return this;
    }

    public BorderImage borderWidth(float width) {
        borderWidth = width;
        return this;
    }

    @Override
    public void draw() {
        super.draw();
        float oldStroke = Lines.getStroke();
        Lines.stroke(borderWidth);
        Draw.color(borderColor, parentAlpha * borderColor.a);
        Lines.rect(x, y, width, height);
        Lines.stroke(oldStroke);
        Draw.color();
    }
}
