package com.github.squi2rel.arctools.ui;

import arc.scene.Element;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.CellAccess;

/**
 * Saves and restores cell constraints when filtering, as mod-tools' BindCell does.
 */
public final class BindCell {
    private static final Cell<?> emptyCell = new Cell<>();

    public final Cell<?> cell;
    private Element element;
    private Cell<?> copy;
    private boolean removed;

    private BindCell(Cell<?> cell) {
        this.cell = cell;
    }

    public static BindCell ofConst(Cell<?> cell) {
        return new BindCell(cell);
    }

    public void build() {
        if (!removed) return;
        cell.set(copy).setElement(element);
        removed = false;
        cell.getTable().invalidateHierarchy();
    }

    public void remove() {
        if (removed) return;
        element = cell.get();
        if (copy == null) copy = new Cell<>();
        copy.set(cell);
        cell.set(emptyCell).colspan(CellAccess.colspan(copy)).clearElement();
        removed = true;
        cell.getTable().invalidateHierarchy();
    }

    public void toggle(boolean visible) {
        if (visible) build();
        else remove();
    }
}
