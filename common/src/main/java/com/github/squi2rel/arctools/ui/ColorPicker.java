package com.github.squi2rel.arctools.ui;

import arc.func.Cons;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.Scene;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.ui.Image;
import arc.scene.ui.Slider;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Stack;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import com.github.squi2rel.arc.ui.ArcStyles;

/**
 * Standalone HSV/alpha picker adapted from mod-tools' popup color picker.
 */
public final class ColorPicker extends Window {
    private static final float fieldWidth = 150f;

    private final Color current = new Color();
    private float hue, saturation, value, alpha;
    private TextField hexField;
    private Slider hueSlider, alphaSlider;

    public ColorPicker(Scene owner) {
        super(owner, "Pick color", 190f, 250f, false, false);
        setModal(false);
        setMovable(true);
        closeOnBack();
    }

    public static ColorPicker get(Scene owner) {
        for (Window window : Window.all) {
            if (window instanceof ColorPicker picker && picker.owner == owner) return picker;
        }
        return new ColorPicker(owner);
    }

    public void show(Color color, Cons<Color> consumer) {
        current.set(color);
        float[] hsv = current.toHsv(new float[3]);
        hue = hsv[0];
        saturation = hsv[1];
        value = hsv[2];
        alpha = current.a;

        cont.clearChildren();
        buttons.clearChildren();
        Table picker = new Table();
        Element saturationValue = new Element() {
            @Override
            public void draw() {
                float white = new Color().fromHsv(hue, 0f, 1f).a(parentAlpha).toFloatBits();
                float full = new Color().fromHsv(hue, 1f, 1f).a(parentAlpha).toFloatBits();
                float black = new Color(0f, 0f, 0f, parentAlpha).toFloatBits();
                Fill.quad(x, y, black, x + width, y, black, x + width, y + height, full, x, y + height, white);
                Draw.color(new Color().fromHsv(hue, saturation, value).inv(), parentAlpha);
                Lines.stroke(2f);
                Lines.circle(x + saturation * width, y + value * height, 5f);
                Draw.color();
            }
        };
        saturationValue.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                apply(x, y);
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                apply(x, y);
            }

            private void apply(float x, float y) {
                saturation = Mathf.clamp(x / Math.max(1f, saturationValue.getWidth()));
                value = Mathf.clamp(y / Math.max(1f, saturationValue.getHeight()));
                updateColor(true);
            }
        });
        picker.add(saturationValue).growX().height(100f).padBottom(6f).colspan(2).row();
        picker.defaults().growX().height(24f);
        picker.add(new ColorSample()).size(42f);
        hueSlider = new Slider(0f, 360f, 0.3f, false, ArcStyles.slider);
        hueSlider.setValue(hue);
        hueSlider.moved(v -> {
            hue = v;
            updateColor(true);
        });
        picker.add(new Stack(new HueStrip(), hueSlider)).row();

        hexField = new TextField(current.toString());
        hexField.changed(() -> {
            try {
                current.set(Color.valueOf(hexField.getText().trim().replace("#", "")));
                float[] values = current.toHsv(new float[3]);
                hue = values[0];
                saturation = values[1];
                value = values[2];
                alpha = current.a;
                hueSlider.setValue(hue);
                if (alphaSlider != null) alphaSlider.setValue(alpha);
                hexField.setColor(Color.white);
            } catch (RuntimeException error) {
                hexField.setColor(ArcStyles.error);
            }
        });
        picker.add(hexField).size(fieldWidth, 40f);
        alphaSlider = new Slider(0f, 1f, 0.001f, false, ArcStyles.slider);
        alphaSlider.setValue(alpha);
        alphaSlider.moved(v -> {
            alpha = v;
            updateColor(true);
        });
        picker.add(new Stack(new AlphaStrip(), alphaSlider)).growX().row();

        cont.add(picker).grow();
        buttons.defaults().size(fieldWidth, 32f);
        buttons.button("Cancel", Icons.cancel, ArcStyles.toolTextButton, this::hide);
        buttons.button("OK", Icons.ok, ArcStyles.toolTextButton, () -> {
            consumer.get(current);
            hide();
        });
        show(owner);
        pack();
        setPosition(CorePosition.mouseX(owner, getWidth()), CorePosition.mouseY(owner, getHeight()), Align.topLeft);
    }

    private void updateColor(boolean updateField) {
        hue = Mathf.clamp(hue, 0f, 360f);
        saturation = Mathf.clamp(saturation);
        value = Mathf.clamp(value);
        current.fromHsv(hue, saturation, value).a(alpha);
        if (updateField && hexField != null) {
            hexField.setText(current.toString());
            hexField.setColor(Color.white);
        }
    }

    private final class ColorSample extends Element {
        @Override
        public void draw() {
            drawChecker(this, parentAlpha);
            Draw.color(current, parentAlpha * current.a);
            Fill.circle(x + width / 2f, y + height / 2f, width / 2f);
            Draw.color();
        }
    }

    private final class AlphaStrip extends Image {
        @Override
        public void draw() {
            drawChecker(this, parentAlpha);
            float left = new Color(current).a(0f).toFloatBits();
            float right = new Color(current).a(parentAlpha).toFloatBits();
            Fill.quad(x, y, left, x + width, y, right, x + width, y + height, right, x, y + height, left);
        }
    }

    private static final class HueStrip extends Element {
        private final Color left = new Color();
        private final Color right = new Color();

        @Override
        public void draw() {
            int parts = 24;
            for (int i = 0; i < parts; i++) {
                float x1 = x + width * i / parts;
                float x2 = x + width * (i + 1) / parts;
                float first = left.fromHsv(360f * i / parts, 1f, 1f).a(parentAlpha).toFloatBits();
                float second = right.fromHsv(360f * (i + 1) / parts, 1f, 1f).a(parentAlpha).toFloatBits();
                Fill.quad(x1, y, first, x2, y, second, x2, y + height, second, x1, y + height, first);
            }
        }
    }

    private static void drawChecker(Element element, float parentAlpha) {
        float size = 6f;
        for (int ix = 0; ix < Math.ceil(element.getWidth() / size); ix++) {
            for (int iy = 0; iy < Math.ceil(element.getHeight() / size); iy++) {
                Draw.color(((ix + iy) & 1) == 0 ? Color.lightGray : Color.darkGray, parentAlpha);
                Fill.crect(element.x + ix * size, element.y + iy * size,
                        Math.min(size, element.getWidth() - ix * size), Math.min(size, element.getHeight() - iy * size));
            }
        }
        Draw.color();
    }

    private static final class CorePosition {
        private static float mouseX(Scene scene, float width) {
            return Mathf.clamp(arc.Core.input.mouseX(), 0f, Math.max(0f, scene.getWidth() - width));
        }

        private static float mouseY(Scene scene, float height) {
            return Mathf.clamp(arc.Core.input.mouseY(), height, scene.getHeight());
        }
    }
}
