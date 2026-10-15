package com.github.squi2rel.arctools;

import arc.func.*;
import arc.graphics.Color;
import arc.scene.Scene;
import arc.scene.style.Drawable;
import arc.scene.ui.CheckBox;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Strings;
import com.github.squi2rel.arctools.ui.IntTab;
import com.github.squi2rel.arctools.ui.Window;

import java.util.Objects;

/**
 * Arc-only adaptation of mod-tools' sectioned SettingsUI.
 */
public final class SettingsUI {
    private final Scene scene;
    private Window ui;
    private final Seq<String> sectionNames = new Seq<>();
    private final Seq<Drawable> sectionIcons = new Seq<>();
    private final Seq<Table> sectionTables = new Seq<>();
    private final Seq<Color> sectionColors = new Seq<>();
    private final Seq<Runnable> customSections = new Seq<>();

    public SettingsUI(Scene scene) {
        this.scene = scene;
    }

    public void addSection(String title, Drawable icon, Cons<Table> contentBuilder) {
        customSections.add(() -> {
            Table table = new Table();
            table.name = "settings-" + title.toLowerCase().replace(' ', '-');
            table.top().left().margin(6f).defaults().left().pad(6f);
            contentBuilder.get(table);
            sectionNames.add(title);
            sectionIcons.add(icon == null ? new arc.scene.style.BaseDrawable() : icon);
            sectionTables.add(table);
            sectionColors.add(Color.sky);
        });
    }

    public void build() {
        boolean first = ui == null;
        if (first) lazyLoad();
        ui.show(scene);
        if (first) {
            ui.setSize(Math.min(Scl.scl(700f), scene.getWidth()), Math.min(Scl.scl(560f), scene.getHeight()));
            ui.setPosition((scene.getWidth() - ui.getWidth()) / 2f, (scene.getHeight() - ui.getHeight()) / 2f);
        }
        ui.keepInStage();
        ui.requestKeyboard();
    }

    private void lazyLoad() {
        ui = new Window(scene, "Settings", 550f, 500f, true);
        ui.name = "arctools-settings";
        ui.setMovable(true);
        ui.setResizable(true);
        ui.setCentered(false);
        ui.closeOnBack();
        customSections.each(Runnable::run);

        IntTab tab = new IntTab(Float.NEGATIVE_INFINITY,
                sectionNames.toArray(String.class), sectionColors.toArray(Color.class),
                sectionTables.toArray(Table.class), 1, true);
        tab.setIcons(sectionIcons.toArray(Drawable.class));
        ui.cont.add(tab.build()).grow().minWidth(0f).minHeight(0f);
        sectionNames.clear();
        sectionIcons.clear();
        sectionTables.clear();
        sectionColors.clear();
        customSections.clear();
    }

    public static final class SettingsBuilder {
        private static Table main;

        private SettingsBuilder() {
        }

        public static void build(Table mainTable) {
            main = Objects.requireNonNull(mainTable, "main table cannot be null");
            main.left().defaults().left();
        }

        public static void clearBuild() {
            main = null;
        }

        public static void check(String text, Boolc cons, Boolp prov) {
            check(text, cons, prov, null);
        }

        public static void check(String text, Boolc cons, Boolp prov, Boolp condition) {
            CheckBox checkBox = new CheckBox(text);
            checkBox.setChecked(prov.get());
            checkBox.setProgrammaticChangeEvents(false);
            checkBox.changed(() -> cons.get(checkBox.isChecked()));
            checkBox.update(() -> {
                checkBox.setChecked(prov.get());
                if (condition != null) checkBox.setDisabled(!condition.get());
            });
            checkBox.left();
            main.add(checkBox).growX().padLeft(10f).row();
        }

        public static void numberi(String text, Intc cons, Intp prov, int min, int max) {
            main.table(t -> {
                t.left();
                t.add(text).left().padRight(5f).growX();
                TextField field = new TextField(String.valueOf(prov.get()));
                field.setValidator(value -> Strings.canParseInt(value) &&
                        Strings.parseInt(value) >= min && Strings.parseInt(value) <= max);
                field.changed(() -> {
                    if (field.isValid()) cons.get(Strings.parseInt(field.getText()));
                });
                t.add(field).width(120f).left();
            }).growX().padTop(0f).row();
        }
    }
}
