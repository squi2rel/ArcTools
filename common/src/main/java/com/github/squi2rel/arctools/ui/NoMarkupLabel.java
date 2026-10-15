package com.github.squi2rel.arctools.ui;

import arc.func.Prov;
import arc.scene.ui.Label;

/**
 * A label that treats markup as literal text when measuring and drawing.
 */
public class NoMarkupLabel extends Label {
    public NoMarkupLabel(Prov<CharSequence> text) {
        super(text);
    }

    public NoMarkupLabel(CharSequence text) {
        super(text);
    }

    public NoMarkupLabel(CharSequence text, LabelStyle style) {
        super(text, style);
    }

    public NoMarkupLabel(float scale) {
        super((CharSequence) null);
        setFontScale(scale);
    }

    {
        layout.ignoreMarkup = true;
    }

    @Override
    public float getPrefWidth() {
        boolean previous = prefSizeLayout.ignoreMarkup;
        try {
            prefSizeLayout.ignoreMarkup = true;
            return super.getPrefWidth();
        } finally {
            prefSizeLayout.ignoreMarkup = previous;
        }
    }

    @Override
    public float getPrefHeight() {
        boolean previous = prefSizeLayout.ignoreMarkup;
        try {
            prefSizeLayout.ignoreMarkup = true;
            return super.getPrefHeight();
        } finally {
            prefSizeLayout.ignoreMarkup = previous;
        }
    }
}
