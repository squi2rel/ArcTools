package com.github.squi2rel.arctools.ui;

import arc.Core;
import arc.func.Cons;
import arc.graphics.Color;
import arc.scene.Scene;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import com.github.squi2rel.arc.ui.ArcStyles;

/**
 * Reusable 42px color swatch and persisted settings row from mod-tools.
 */
public final class ColorBlock {
    private ColorBlock() {
    }

    public static void of(Cell<?> cell, Scene owner, Color color, Cons<Color> callback, boolean needDoubleClick) {
        BorderImage image = new BorderImage(ArcStyles.white, 2f);
        Color inverse = new Color();
        image.update(() -> {
            image.setColor(color);
            image.border(inverse.set(color).inv().a(1f));
        });
        cell.setElement(image).size(42f);
        image.clicked(listener -> listener.setTapCountInterval(0.4f), listener -> {
            if (!needDoubleClick || listener.getTapCount() == 2) {
                ColorPicker.get(owner).show(color, picked -> {
                    color.set(picked);
                    if (callback != null) callback.get(color);
                });
                listener.setTapCount(0);
            }
        });
    }

    public static Color setting(Table table, Scene owner, String text, String key, String fallback, Cons<Color> callback) {
        Color loaded;
        try {
            loaded = Color.valueOf(Core.settings.getString(key, fallback));
        } catch (RuntimeException ignored) {
            loaded = Color.valueOf(fallback);
        }
        Color color = loaded;
        Label label = table.add(text).growY().padRight(4f).left().get();
        label.clicked(listener -> listener.setTapCountInterval(0.4f), listener -> {
            if (listener.getTapCount() == 2) {
                color.set(Color.valueOf(fallback));
                Core.settings.put(key, color.toString());
                if (callback != null) callback.get(color);
                listener.setTapCount(0);
            }
        });
        of(table.add().right().growX(), owner, color, picked -> {
            Core.settings.put(key, picked.toString());
            if (callback != null) callback.get(picked);
        }, false);
        table.row();
        return color;
    }
}
