package com.github.squi2rel.arctools.ui;

import arc.graphics.Color;
import arc.math.Interp;
import arc.scene.Scene;
import arc.scene.ui.CheckBox;
import arc.scene.ui.TextButton;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import arc.util.Log;
import com.github.squi2rel.arc.ui.ArcStyles;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Reflection settings builder corresponding to mod-tools' RBuilder.
 */
public final class RBuilder {
    private static final Runnable emptyRun = () -> {
    };

    private RBuilder() {
    }

    public static void show(Scene owner, String title, Object object, Set<String> blacklist, Runnable applied) {
        SelectTable.show(owner, fields -> {
            fields.top().left();
            fields.defaults().left().pad(2f, 5f, 2f, 5f);
            buildFor(fields, owner, object.getClass(), object, blacklist == null ? Set.of() : blacklist);
        }, applied);
    }

    public static void buildFor(Table table, Scene owner, Class<?> type, Object object, Set<String> blacklist) {
        int count = 0;
        for (Field field : fields(type)) {
            if (field.isSynthetic() || Modifier.isStatic(field.getModifiers()) || blacklist.contains(field.getName()))
                continue;
            Class<?> fieldType = box(field.getType());
            if (!(fieldType == Boolean.class || Number.class.isAssignableFrom(fieldType) || fieldType == Color.class
                    || fieldType == Interp.class || fieldType.isEnum() || fieldType == Runnable.class)) continue;
            if (Modifier.isFinal(field.getModifiers()) && fieldType != Color.class) continue;
            if (!field.trySetAccessible()) continue;
            try {
                addField(table, owner, object, field, fieldType);
                count++;
            } catch (ReflectiveOperationException | RuntimeException error) {
                Log.err("Unable to build field editor for " + field, error);
            }
        }
        if (count == 0) table.add("No editable parameters").pad(12f).row();
    }

    private static void addField(Table table, Scene owner, Object object, Field field, Class<?> type)
            throws ReflectiveOperationException {
        String name = field.getName();
        if (type == Runnable.class) {
            field.set(object, emptyRun);
            return;
        }
        table.add(name).growX().left();
        if (type == Boolean.class) {
            CheckBox input = new CheckBox("", ArcStyles.checkBox);
            input.setChecked(Boolean.TRUE.equals(field.get(object)));
            input.changed(() -> write(field, object, input.isChecked()));
            table.add(input).size(42f);
        } else if (Number.class.isAssignableFrom(type)) {
            TextField input = new TextField(String.valueOf(field.get(object)));
            input.changed(() -> {
                try {
                    write(field, object, parseNumber(input.getText(), type));
                    input.setColor(Color.white);
                } catch (RuntimeException error) {
                    input.setColor(ArcStyles.error);
                }
            });
            table.add(input).width(140f).height(42f);
        } else if (type == Color.class) {
            Color color = (Color) field.get(object);
            if (color == null) {
                color = new Color(Color.white);
                field.set(object, color);
            }
            Color mutable = color;
            ColorBlock.of(table.add().right(), owner, mutable, picked -> {
                if (!Modifier.isFinal(field.getModifiers())) write(field, object, picked);
            }, false);
        } else if (type == Interp.class) {
            List<NamedValue<Interp>> values = interpValues();
            int[] index = {indexOf(values, field.get(object))};
            if (field.get(object) == null) field.set(object, values.get(index[0]).value);
            TextButton input = new TextButton(valueName(values, index[0]), ArcStyles.toolTextButton);
            input.clicked(() -> {
                index[0] = (index[0] + 1) % values.size();
                input.setText(valueName(values, index[0]));
                write(field, object, values.get(index[0]).value);
            });
            table.add(input).width(140f).height(42f);
        } else if (type.isEnum()) {
            Object[] values = type.getEnumConstants();
            int[] index = {Math.max(0, Arrays.asList(values).indexOf(field.get(object)))};
            TextButton input = new TextButton(String.valueOf(values[index[0]]), ArcStyles.toolTextButton);
            input.clicked(() -> {
                index[0] = (index[0] + 1) % values.length;
                input.setText(String.valueOf(values[index[0]]));
                write(field, object, values[index[0]]);
            });
            table.add(input).width(140f).height(42f);
        }
        table.row();
    }

    private static void write(Field field, Object object, Object value) {
        try {
            field.set(object, value);
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException(error);
        }
    }

    private static Object parseNumber(String value, Class<?> type) {
        if (type == Byte.class) return Byte.parseByte(value);
        if (type == Short.class) return Short.parseShort(value);
        if (type == Integer.class) return Integer.parseInt(value);
        if (type == Long.class) return Long.parseLong(value);
        if (type == Float.class) return Float.parseFloat(value);
        if (type == Double.class) return Double.parseDouble(value);
        throw new IllegalArgumentException("Unsupported number type: " + type.getName());
    }

    private static List<Field> fields(Class<?> type) {
        List<Class<?>> hierarchy = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            hierarchy.add(current);
        }
        LinkedHashMap<String, Field> result = new LinkedHashMap<>();
        for (int i = hierarchy.size() - 1; i >= 0; i--) {
            for (Field field : hierarchy.get(i).getDeclaredFields()) {
                if (!Modifier.isTransient(field.getModifiers())) result.put(field.getName(), field);
            }
        }
        return new ArrayList<>(result.values());
    }

    private static List<NamedValue<Interp>> interpValues() {
        List<NamedValue<Interp>> result = new ArrayList<>();
        Set<Interp> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Field field : Interp.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !Interp.class.isAssignableFrom(field.getType())) continue;
            try {
                Interp value = (Interp) field.get(null);
                if (value != null && seen.add(value)) result.add(new NamedValue<>(field.getName(), value));
            } catch (IllegalAccessException ignored) {
            }
        }
        result.sort(Comparator.comparing(NamedValue::name));
        if (result.isEmpty()) result.add(new NamedValue<>("linear", Interp.linear));
        return result;
    }

    private static int indexOf(List<NamedValue<Interp>> values, Object value) {
        for (int i = 0; i < values.size(); i++) if (values.get(i).value == value) return i;
        return 0;
    }

    private static String valueName(List<NamedValue<Interp>> values, int index) {
        return values.get(index).name;
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        return type;
    }

    private record NamedValue<T>(String name, T value) {
    }
}
