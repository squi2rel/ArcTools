package com.github.squi2rel.arctools.inspect;

import arc.graphics.Color;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.event.Touchable;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.CellAccess;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Reflect;
import arc.util.Strings;
import com.github.squi2rel.arc.ui.ArcStyles;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.function.Function;

/**
 * ReviewElement.InfoDetails entry order and conditional rows, without Mindustry resources.
 */
final class InspectorHoverInfo extends Table {
    private final Label nameLabel = new Label("");
    private final Label sizeLabel = new Label("");
    private final Label hiddenLabel = new Label("hidden");
    private final Image colorSwatch = new Image(ArcStyles.white);
    private Cell<?> hiddenCell;
    private final Seq<Row> rows = new Seq<>();

    InspectorHoverInfo() {
        name = "inspector-hover-info";
        touchable = Touchable.disabled;
        background(ArcStyles.panel);
        margin(4f);
        defaults().growX();
        table(top -> {
            top.add(nameLabel).fontScale(0.85f).color(Color.violet).padLeft(-4f);
            hiddenCell = top.add(hiddenLabel).fontScale(0.8f).color(ArcStyles.accent).pad(4f, 8f, 4f, 4f);
            top.add(sizeLabel).fontScale(0.8f).color(Color.lightGray).padLeft(10f).right().labelAlign(Align.right).growX();
        }).growX().row();
        row("Touchable", e -> e.touchable.name());
        row("Color", e -> e.color.toString().substring(0, e.color.a == 1f ? 6 : 8), colorSwatch);
        row("Transform", e -> e instanceof Group group && group.isTransform() ? "True" : null);
        row("Rotation", e -> !Mathf.equal(e.rotation % 360f, 0f) ? number(e.rotation) : null);
        row("Translation", e -> e.translation.isZero() ? null : pair(e.translation.x, e.translation.y, ", "));
        row("Style", InspectorHoverInfo::styleName);
        row("Align", e -> e instanceof Table table ? Align.toString(table.getAlign()) : null);
        row("ElemScale", e -> Mathf.equal(e.scaleX, 1f) && Mathf.equal(e.scaleY, 1f) ? null : pair(e.scaleX, e.scaleY));
        separator();
        row("CellAlign", e -> cell(e) == null ? null : Align.toString(CellAccess.align(cell(e))));
        row("Colspan", e -> cell(e) == null || CellAccess.colspan(cell(e)) == 1 ? null : String.valueOf(CellAccess.colspan(cell(e))));
        row("MinSize", e -> cell(e) == null ? null : sizePairIfNonzero(CellAccess.minWidth(cell(e)), CellAccess.minHeight(cell(e))));
        row("MaxSize", e -> cell(e) == null ? null : sizePairIfNonzero(CellAccess.maxWidth(cell(e)), CellAccess.maxHeight(cell(e))));
        row("Expand", e -> cell(e) == null ? null : axes(CellAccess.expandX(cell(e)) != 0, CellAccess.expandY(cell(e)) != 0));
        row("Fill", e -> cell(e) == null ? null : axes(CellAccess.fillX(cell(e)) != 0, CellAccess.fillY(cell(e)) != 0));
        row("Uniform", e -> cell(e) == null ? null : axes(CellAccess.uniformX(cell(e)), CellAccess.uniformY(cell(e))));
        separator();
        row("LabelAlign", e -> e instanceof Label label ? Align.toString(label.getLabelAlign()) : null);
        row("LineAlign", e -> e instanceof Label label ? Align.toString(label.getLineAlign()) : null);
        row("LabelScale", e -> e instanceof Label label && (!Mathf.equal(label.getFontScaleX(), 1f) || !Mathf.equal(label.getFontScaleY(), 1f)) ? pair(label.getFontScaleX(), label.getFontScaleY()) : null);
        row("LabelWrap", e -> e instanceof Label label ? String.valueOf((Boolean) Reflect.get(Label.class, label, "wrap")) : null);
        row("Scaling", e -> e instanceof Image image ? String.valueOf((Object) Reflect.get(Image.class, image, "scaling")) : null);
        separator();
        row("Scroll", e -> e instanceof ScrollPane pane ? axes(pane.isScrollX(), pane.isScrollY()) : null);
    }

    private void row(String key, Function<Element, String> value) {
        row(key, value, null);
    }

    private void row(String key, Function<Element, String> value, Element decoration) {
        Table row = new Table();
        row.left().defaults().left();
        row.add(key).fontScale(0.84f).color(Color.lightGray).padRight(10f);
        Label label = new Label("");
        row.table(values -> {
            values.right();
            if (decoration != null) values.add(decoration).size(16f).padRight(4f);
            values.add(label).fontScale(0.8f).right().labelAlign(Align.right);
        }).growX().right();
        rows.add(new Row(add(row).growX(), row, label, value));
        row();
    }

    private void separator() {
        Image line = new Image(ArcStyles.white);
        line.setColor(Color.lightGray.cpy().a(0.5f));
        rows.add(new Row(add(line).height(1f).growX().pad(4f, 2f, 4f, 2f), line, null, null));
        row();
    }

    void updateFor(Element element, String name) {
        nameLabel.setText(name);
        colorSwatch.setColor(element.color);
        sizeLabel.setText(pair(element.getWidth(), element.getHeight()));
        hiddenCell.setElement(element.visible ? null : hiddenLabel);
        if (element.visible) hiddenCell.pad(0f).size(0f);
        else hiddenCell.pad(4f, 8f, 4f, 4f).size(Float.NEGATIVE_INFINITY);
        for (Row row : rows) {
            if (row.value == null) continue;
            String text = row.value.apply(element);
            row.cell.setElement(text == null ? null : row.element);
            if (text != null) row.label.setText(text);
        }
        boolean precedingRow = false;
        for (int i = 0; i < rows.size; i++) {
            Row row = rows.get(i);
            if (row.value != null) {
                if (row.cell.hasElement()) precedingRow = true;
                continue;
            }
            boolean followingRow = false;
            for (int j = i + 1; j < rows.size && rows.get(j).value != null; j++) {
                if (rows.get(j).cell.hasElement()) followingRow = true;
            }
            row.cell.setElement(precedingRow && followingRow ? row.element : null);
            if (row.cell.hasElement()) row.cell.height(1f).pad(4f, 2f, 4f, 2f);
            else row.cell.height(0f).pad(0f);
            if (row.cell.hasElement()) precedingRow = false;
        }
        invalidateHierarchy();
        pack();
    }

    private static Cell<?> cell(Element element) {
        return CellAccess.cell(element);
    }

    private static String number(float value) {
        return Strings.autoFixed(value, 2);
    }

    private static String pair(float x, float y) {
        return pair(x, y, " x ");
    }

    private static String pair(float x, float y, String delimiter) {
        return number(x) + delimiter + number(y);
    }

    private static String pairIfNonzero(float x, float y) {
        return x == 0f && y == 0f ? null : pair(x, y);
    }

    /**
     * Mirrors modtools' SizeProv: cell constraints are stored in scaled units.
     */
    private static String sizePairIfNonzero(float x, float y) {
        return x == 0f && y == 0f ? null : sizePair(x, y);
    }

    private static String sizePair(float x, float y) {
        return sizeNumber(x) + " × " + sizeNumber(y);
    }

    private static String sizeNumber(float value) {
        if (value == Float.NEGATIVE_INFINITY) return "UNSET";
        if (value == Float.POSITIVE_INFINITY) return "+Infinity";
        if (Float.isNaN(value)) return "NaN";
        return number(value / Scl.scl());
    }

    private static String axes(boolean x, boolean y) {
        return !x && !y ? null : "[" + (x ? "accent" : "gray") + "]x[] | [" + (y ? "accent" : "gray") + "]y[]";
    }

    private static String styleName(Element element) {
        Object style;
        try {
            style = element.getClass().getMethod("getStyle").invoke(element);
            for (Field field : ArcStyles.class.getFields()) {
                if (Modifier.isStatic(field.getModifiers()) && field.get(null) == style)
                    return "ArcStyles." + field.getName();
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    private record Row(Cell<?> cell, Element element, Label label, Function<Element, String> value) {
    }
}
