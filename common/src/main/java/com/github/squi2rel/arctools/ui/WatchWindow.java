package com.github.squi2rel.arctools.ui;

import arc.Core;
import arc.func.Cons;
import arc.func.Func;
import arc.func.Prov;
import arc.graphics.Color;
import arc.scene.Scene;
import arc.scene.style.Drawable;
import arc.scene.ui.CheckBox;
import arc.scene.ui.Image;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Align;
import com.github.squi2rel.arc.ui.ArcStyles;
import com.github.squi2rel.arctools.inspect.TesterFunctions.MyProv;
import com.github.squi2rel.arctools.inspect.TesterFunctions.MyProvIns;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;

/**
 * Chainable Tester watches with per-value refresh intervals.
 */
public class WatchWindow extends Window implements Window.IDisposable {
    private final Table template = new Table();
    private final ObjectMap<String, WatchLabel> labels = new ObjectMap<>();
    private final Seq<IconWatch> iconWatches = new Seq<>();
    private int unnamed;
    private boolean noFilter;

    public WatchWindow(Scene owner) {
        super(owner, "Watch", 220f, 120f, true);
        setMovable(true);
        setResizable(true);
        setCentered(false);
        closeOnBack();
        template.top().left().defaults().growX().left();
        cont.pane(template).grow();
    }

    public WatchWindow watch(String info, Object value, Func<Object, String> stringify) {
        return watch(info, new MyProvIns<>(() -> value, stringify), 0f);
    }

    public WatchWindow watch(String info, Object value) {
        return watch(info, provider(value), 0f);
    }

    public WatchWindow watch(String info, Function value, float interval) {
        return watch(info, provider(value), interval);
    }

    private MyProv<Object> provider(Object value) {
        if (value instanceof Function) {
            Prov<?> callback = (Prov<?>) Context.jsToJava(value, Prov.class);
            return callback::get;
        }
        return () -> value;
    }

    public WatchWindow watchConst(String info, Object value) {
        return watch(info, () -> value, Float.POSITIVE_INFINITY);
    }

    public WatchWindow watch(String info, MyProv<Object> value) {
        return watch(info, value, 0f);
    }

    public WatchWindow watch(String info, MyProv<Object> value, float interval) {
        WatchLabel label = labels.get(info);
        if (label != null) {
            label.prov = value;
            return this;
        }
        template.add(info).color(ArcStyles.accent).row();
        template.image(ArcStyles.white).color(ArcStyles.accent).height(1f).row();
        label = new WatchLabel(value, interval);
        label.name = info;
        labels.put(info, label);
        template.add(label).row();
        return this;
    }

    public WatchWindow watch(Drawable icon, MyProv<Object> value) {
        return watch(icon, value, 0f);
    }

    public WatchWindow watch(Drawable icon, Object value) {
        return watch(icon, provider(value), 0f);
    }

    public WatchWindow watch(Drawable icon, Function value, float interval) {
        return watch(icon, provider(value), interval);
    }

    public WatchWindow watch(Drawable icon, MyProv<Object> value, float interval) {
        WatchLabel label = new WatchLabel(value, interval);
        labels.put("#" + unnamed++, label);
        Table row = new Table();
        row.add(new Image(icon)).size(32f);
        row.add(label).growX().left();
        iconWatches.add(new IconWatch(BindCell.ofConst(template.add(row)), label));
        template.row();
        return this;
    }

    public WatchWindow watchWithSetter(Drawable icon, MyProv<Object> value, Cons<String> setter) {
        Table row = new Table();
        row.add(new Image(icon)).size(32f);
        TextField field = new TextField();
        field.update(() -> {
            if (Core.scene.getKeyboardFocus() != field) {
                try {
                    field.setText(value.stringify(value.get()));
                } catch (Exception error) {
                    field.setText(error.toString());
                }
            }
        });
        field.changed(() -> {
            if (setter != null && arc.util.Strings.canParseFloat(field.getText())) setter.get(field.getText());
        });
        row.add(field).growX();
        template.add(row).row();
        return this;
    }

    public WatchWindow watchWithSetter(Drawable icon, Object value, Cons<String> setter) {
        return watchWithSetter(icon, provider(value), setter);
    }

    public WatchWindow watch(String info, float value) {
        return watch(info, () -> value);
    }

    public WatchWindow watch(String info, int value) {
        return watch(info, () -> value);
    }

    public WatchWindow watch(String info, boolean value) {
        return watch(info, () -> value);
    }

    public Object getWatch(String info) {
        return labels.get(info).value;
    }

    public WatchLabel getWatchLabel(String info) {
        return labels.get(info);
    }

    public void newLine() {
        template.row();
    }

    public void addAllCheckbox() {
        CheckBox all = new CheckBox("No Filter");
        all.setChecked(noFilter);
        all.changed(() -> noFilter = all.isChecked());
        cont.row();
        cont.add(all).left();
    }

    @Override
    public WatchWindow show() {
        labels.each((name, label) -> label.refresh());
        super.show();
        setPosition(Core.input.mouseX(), Core.input.mouseY(), Align.topLeft);
        keepInStage();
        return this;
    }

    public WatchWindow showIfOk() {
        if (Core.scene.root != null && !isEmpty()) show();
        return this;
    }

    public WatchWindow clearWatch() {
        iconWatches.clear();
        template.clear();
        labels.clear();
        return this;
    }

    @Override
    public void clearAll() {
        Window.IDisposable.super.clearAll();
        clearWatch();
    }

    public boolean isEmpty() {
        return template.getChildren().isEmpty();
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        for (IconWatch watch : iconWatches) {
            // Detached rows still need samples so a zero value can become visible again.
            if (!watch.cell.cell.hasElement()) watch.label.act(delta);
            watch.cell.toggle(noFilter || arc.util.Strings.parseFloat(String.valueOf(watch.label.value), 0f) != 0f);
        }
    }

    private record IconWatch(BindCell cell, WatchLabel label) {
    }

    public static class WatchLabel extends NoMarkupLabel {
        public MyProv<Object> prov;
        public Object value;
        public final float interval;
        private float elapsed;
        private boolean sampled;

        WatchLabel(MyProv<Object> prov, float interval) {
            super("");
            this.prov = prov;
            this.interval = interval;
        }

        void refresh() {
            try {
                value = prov.get();
                setText(prov.stringify(value));
                setColor(Color.white);
            } catch (Exception error) {
                setText(error.toString());
                setColor(ArcStyles.error);
            }
            sampled = true;
            elapsed = 0f;
        }

        @Override
        public void act(float delta) {
            super.act(delta);
            elapsed += delta * 60f;
            if (!sampled || elapsed >= interval) refresh();
        }
    }
}
