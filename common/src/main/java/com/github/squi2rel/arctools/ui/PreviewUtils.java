package com.github.squi2rel.arctools.ui;

import arc.func.Cons;
import arc.scene.ui.ImageButton;
import arc.scene.ui.Tooltip;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import com.github.squi2rel.arc.ui.ArcStyles;

/**
 * Shared 36px preview affordance used by the UI catalog.
 */
public final class PreviewUtils {
    private PreviewUtils() {
    }

    public static Cell<ImageButton> addPreviewButton(Table table, Cons<Table> contents) {
        ImageButton button = new ImageButton(Icons.image, ArcStyles.toolImageButton);
        button.addListener(new Tooltip(preview -> {
            preview.background(ArcStyles.panel);
            preview.margin(4f);
            contents.get(preview);
        }));
        return table.add(button).size(36f);
    }
}
