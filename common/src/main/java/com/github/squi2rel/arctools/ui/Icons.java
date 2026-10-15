package com.github.squi2rel.arctools.ui;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.EarClippingTriangulator;
import arc.math.Mathf;
import arc.math.geom.Geometry;
import arc.scene.style.BaseDrawable;
import arc.scene.style.Drawable;
import arc.struct.Seq;

public final class Icons {
    public static final Drawable pin = new WindowIconDrawable(WindowIcon.pin);
    public static final Drawable minimize = new WindowIconDrawable(WindowIcon.minimize);
    public static final Drawable maximize = new WindowIconDrawable(WindowIcon.maximize);
    public static final Drawable restore = new WindowIconDrawable(WindowIcon.restore);
    public static final Drawable close = new WindowIconDrawable(WindowIcon.close);
    public static final Drawable windows = icon("windows",
            path(3, 3, 16, 3, 16, 16, 3, 16, 3, 3), path(8, 16, 8, 21, 21, 21, 21, 8, 16, 8));
    public static final Drawable settings = icon("settings", gear(), arc(12, 12, 3, 0, 360));
    public static final Drawable move = icon("move",
            path(12, 3, 12, 21), path(3, 12, 21, 12),
            path(8, 17, 12, 21, 16, 17), path(8, 7, 12, 3, 16, 7),
            path(7, 8, 3, 12, 7, 16), path(17, 8, 21, 12, 17, 16));
    public static final Drawable run = icon("run", path(6, 3, 21, 12, 6, 21, 6, 3));
    public static final Drawable up = icon("up", path(5, 9, 12, 16, 19, 9));
    public static final Drawable left = icon("left", path(15, 5, 8, 12, 15, 19));
    public static final Drawable right = icon("right", path(9, 5, 16, 12, 9, 19));
    public static final Drawable down = icon("down", path(5, 15, 12, 8, 19, 15));
    public static final Drawable copy = icon("copy",
            path(8, 3, 20, 3, 20, 16, 8, 16, 8, 3), path(4, 8, 4, 21, 16, 21));
    public static final Drawable paste = icon("paste",
            path(8, 19, 4, 19, 4, 3, 20, 3, 20, 19, 16, 19),
            path(8, 17, 8, 21, 16, 21, 16, 17, 8, 17), path(8, 12, 16, 12), path(8, 8, 16, 8));
    public static final Drawable watch = icon("watch",
            path(2, 12, 6, 17, 12, 20, 18, 17, 22, 12, 18, 7, 12, 4, 6, 7, 2, 12),
            arc(12, 12, 3, 0, 360));
    public static final Drawable star = icon("star", star());
    public static final Drawable loop = icon("loop",
            path(3, 13, 3, 18, 20, 18, 16, 22), path(20, 18, 16, 14),
            path(21, 11, 21, 6, 4, 6, 8, 2), path(4, 6, 8, 10));
    public static final Drawable strict = icon("strict",
            path(5, 3, 19, 3, 19, 14, 5, 14, 5, 3), arc(12, 16, 5, 0, 180),
            path(7, 14, 7, 16), path(17, 14, 17, 16), path(12, 7, 12, 10));
    public static final Drawable highlight = icon("highlight",
            path(6, 9, 15, 20, 20, 16, 11, 5, 6, 9), path(6, 9, 3, 4, 8, 5), path(3, 2, 17, 2));
    public static final Drawable details = icon("details", arc(12, 12, 9, 0, 360),
            path(12, 6, 12, 12, 10, 12), path(10, 6, 14, 6), path(12, 16, 12, 17));
    public static final Drawable history = icon("history", arc(12, 12, 8, 180, 330),
            path(5, 21, 5, 16, 10, 16), path(12, 17, 12, 12, 16, 9));
    public static final Drawable bookmarks = icon("bookmarks",
            path(6, 3, 6, 21, 18, 21, 18, 3, 12, 8, 6, 3), path(9, 16, 15, 16));
    public static final Drawable timeout = icon("timeout", arc(12, 10, 8, 0, 360),
            path(9, 22, 15, 22), path(12, 22, 12, 18), path(18, 17, 20, 19), path(12, 10, 12, 15));
    public static final Drawable background = icon("background",
            path(2, 17, 6, 19, 10, 17, 14, 19, 18, 17, 22, 19),
            path(2, 11, 6, 13, 10, 11, 14, 13, 18, 11, 22, 13),
            path(2, 5, 6, 7, 10, 5, 14, 7, 18, 5, 22, 7));
    public static final Drawable delete = icon("delete",
            path(3, 18, 21, 18), path(9, 18, 9, 21, 15, 21, 15, 18),
            path(5, 18, 6, 3, 18, 3, 19, 18), path(10, 7, 10, 14), path(14, 7, 14, 14));
    public static final Drawable pause = icon("pause",
            path(5, 3, 9, 3, 9, 21, 5, 21, 5, 3), path(15, 3, 19, 3, 19, 21, 15, 21, 15, 3));

    public static final Drawable refresh = icon("refresh", arc(12, 12, 8, 45, 290),
            path(13, 18, 18, 18, 18, 23));
    public static final Drawable filter = icon("filter",
            path(3, 20, 21, 20, 15, 12, 15, 5, 9, 2, 9, 12, 3, 20));
    public static final Drawable image = icon("image",
            path(2, 3, 22, 3, 22, 21, 2, 21, 2, 3),
            path(2, 5, 8, 12, 13, 7, 17, 11, 22, 6), arc(16, 16, 2, 0, 360));
    public static final Drawable cancel = icon("cancel", path(5, 5, 19, 19), path(5, 19, 19, 5));
    public static final Drawable ok = icon("ok", path(3, 12, 9, 6, 21, 19));
    public static final Drawable execute = icon("execute",
            path(3, 3, 9, 3, 9, 21, 3, 21), path(13, 5, 20, 12, 13, 19));
    public static final Drawable code = icon("code",
            path(7, 5, 1, 12, 7, 19), path(17, 5, 23, 12, 17, 19), path(10, 3, 14, 21));
    public static final Drawable terminal = icon("terminal",
            path(2, 3, 22, 3, 22, 21, 2, 21, 2, 3), path(5, 8, 10, 12, 5, 16), path(13, 8, 18, 8));
    public static final Drawable chart = icon("chart",
            path(2, 22, 2, 2, 22, 2), path(6, 5, 6, 11, 9, 11, 9, 5, 6, 5),
            path(12, 5, 12, 20, 15, 20, 15, 5, 12, 5), path(18, 5, 18, 15, 21, 15, 21, 5, 18, 5));
    public static final Drawable box = icon("box",
            path(3, 7, 12, 2, 21, 7, 21, 17, 12, 22, 3, 17, 3, 7),
            path(3, 17, 12, 12, 21, 17), path(12, 12, 12, 2), path(8, 20, 17, 15));
    public static final Drawable zoom = icon("zoom", arc(10, 14, 7, 0, 360), path(15, 9, 22, 2));
    public static final Drawable edit = icon("edit",
            path(3, 3, 5, 10, 17, 22, 22, 17, 10, 5, 3, 3), path(14, 19, 19, 14), path(5, 10, 10, 5));
    public static final Drawable eraser = icon("eraser",
            path(3, 9, 15, 21, 22, 14, 10, 2, 3, 9), path(9, 15, 16, 8), path(10, 2, 22, 2));
    public static final Drawable crafting = icon("crafting",
            path(3, 19, 16, 6, 19, 3, 22, 6, 19, 9, 6, 22, 3, 19),
            path(3, 3, 15, 15), path(12, 18, 17, 23, 22, 18, 17, 13, 12, 18));
    public static final Drawable bounds = icon("bounds",
            path(2, 16, 2, 22, 8, 22), path(16, 22, 22, 22, 22, 16),
            path(22, 8, 22, 2, 16, 2), path(8, 2, 2, 2, 2, 8),
            path(6, 6, 18, 6, 18, 18, 6, 18, 6, 6));
    public static final Drawable stop = icon("stop", path(4, 4, 20, 4, 20, 20, 4, 20, 4, 4));
    public static final Drawable download = icon("download",
            path(12, 22, 12, 8), path(6, 14, 12, 8, 18, 14), path(3, 8, 3, 2, 21, 2, 21, 8));
    public static final Drawable upload = icon("upload",
            path(12, 8, 12, 22), path(6, 16, 12, 22, 18, 16), path(3, 8, 3, 2, 21, 2, 21, 8));

    private Icons() {
    }

    private static float[] path(float... points) {
        return points;
    }

    private static float[] arc(float x, float y, float radius, float start, float sweep) {
        int segments = Math.max(1, (int) Math.ceil(Math.abs(sweep) / 10f));
        float[] points = new float[(segments + 1) * 2];
        for (int i = 0; i <= segments; i++) {
            float angle = start + sweep * i / segments;
            points[i * 2] = x + Mathf.cosDeg(angle) * radius;
            points[i * 2 + 1] = y + Mathf.sinDeg(angle) * radius;
        }
        if (Math.abs(sweep) == 360f) {
            points[points.length - 2] = points[0];
            points[points.length - 1] = points[1];
        }
        return points;
    }

    private static float[] star() {
        float[] points = new float[22];
        for (int i = 0; i < 10; i++) {
            float radius = i % 2 == 0 ? 10f : 4.5f;
            points[i * 2] = 12f + Mathf.cosDeg(90f + i * 36f) * radius;
            points[i * 2 + 1] = 12f + Mathf.sinDeg(90f + i * 36f) * radius;
        }
        points[20] = points[0];
        points[21] = points[1];
        return points;
    }

    private static float[] gear() {
        float[] points = new float[66];
        for (int i = 0; i < 32; i++) {
            float radius = i % 4 < 2 ? 9.5f : 7f;
            points[i * 2] = 12f + Mathf.cosDeg(i * 11.25f) * radius;
            points[i * 2 + 1] = 12f + Mathf.sinDeg(i * 11.25f) * radius;
        }
        points[64] = points[0];
        points[65] = points[1];
        return points;
    }

    private static Drawable icon(String name, float[]... paths) {
        IconShape[] shapes = new IconShape[paths.length];
        for (int i = 0; i < paths.length; i++) shapes[i] = IconShape.path(1.8f, paths[i]);
        BaseDrawable drawable = new BaseDrawable() {
            @Override
            public void draw(float x, float y, float width, float height) {
                float size = Math.min(width, height);
                if (size <= 0f) return;
                float scale = size / 24f;
                float left = x + (width - size) / 2f, bottom = y + (height - size) / 2f;
                for (IconShape shape : shapes) shape.draw(left, bottom, scale);
            }
        };
        drawable.setName(name);
        drawable.setMinWidth(24f);
        drawable.setMinHeight(24f);
        return drawable;
    }

    private enum WindowIcon {
        pin, minimize, maximize, restore, close
    }

    private static final class WindowIconDrawable extends BaseDrawable {
        private final Seq<IconShape> shapes = new Seq<>();

        WindowIconDrawable(WindowIcon icon) {
            setName(icon.name());
            setMinWidth(22f);
            setMinHeight(22f);
            float size = 24f, stroke = size * 0.085f;
            switch (icon) {
                case pin -> shapes.add(new IconShape(0, 1f,
                        11, 19, 13, 21, 21, 13, 19, 11, 17, 13, 13, 9, 13, 5,
                        10, 8, 3, 3, 8, 10, 5, 13, 9, 13, 13, 17));
                case minimize -> shapes.add(IconShape.path(stroke,
                        path(size * 0.22f, size * 0.30f, size * 0.78f, size * 0.30f)));
                case maximize -> outline(size * 0.22f, size * 0.22f, size * 0.56f, size * 0.56f, stroke);
                case restore -> {
                    float backX = size * 0.34f, backY = size * 0.34f;
                    float side = size * 0.48f, offset = size * 0.16f;
                    float half = stroke / 2f;
                    shapes.add(IconShape.path(stroke,
                            path(backX + half, backY + side - offset,
                                    backX + half, backY + side - half,
                                    backX + side - half, backY + side - half,
                                    backX + side - half, backY + half,
                                    backX + side - offset, backY + half)));
                    outline(backX - offset, backY - offset, side, side, stroke);
                }
                case close -> {
                    float half = size * 0.72f / (2f * Mathf.sqrt2);
                    shapes.add(IconShape.path(stroke, path(12f - half, 12f - half, 12f + half, 12f + half)));
                    shapes.add(IconShape.path(stroke, path(12f - half, 12f + half, 12f + half, 12f - half)));
                }
            }
        }

        private void outline(float x, float y, float width, float height, float stroke) {
            float half = stroke / 2f;
            shapes.add(IconShape.path(stroke, path(x + half, y + half,
                    x + width - half, y + half, x + width - half, y + height - half,
                    x + half, y + height - half, x + half, y + half)));
        }

        @Override
        public void draw(float x, float y, float width, float height) {
            float size = Math.min(width, height);
            if (size <= 0f) return;
            float left = x + (width - size) / 2f, bottom = y + (height - size) / 2f;
            for (IconShape shape : shapes) shape.draw(left, bottom, size / 24f);
        }
    }

    private static final class IconShape {
        private final float stroke, maxFringe;
        private final float[] contour, miters, inner, outer, innerFade, outerFade;
        private final short[] triangles;

        IconShape(float stroke, float maxFringe, float... contour) {
            this.stroke = stroke;
            this.maxFringe = maxFringe;
            this.contour = contour;
            Geometry.ensureCCW(contour);
            triangles = stroke == 0 ? new EarClippingTriangulator().computeTriangles(contour).toArray() : null;
            miters = new float[contour.length];
            inner = new float[contour.length];
            outer = new float[contour.length];
            innerFade = stroke == 0 ? null : new float[contour.length];
            outerFade = stroke == 0 ? null : new float[contour.length];
            for (int i = 0; i < contour.length; i += 2) {
                int previous = (i + contour.length - 2) % contour.length, next = (i + 2) % contour.length;
                float dx1 = contour[i] - contour[previous], dy1 = contour[i + 1] - contour[previous + 1];
                float dx2 = contour[next] - contour[i], dy2 = contour[next + 1] - contour[i + 1];
                float length1 = Mathf.len(dx1, dy1), length2 = Mathf.len(dx2, dy2);
                float nx1 = dy1 / length1, ny1 = -dx1 / length1;
                float nx2 = dy2 / length2, ny2 = -dx2 / length2;
                float factor = 1f / (1f + nx1 * nx2 + ny1 * ny2);
                miters[i] = (nx1 + nx2) * factor;
                miters[i + 1] = (ny1 + ny2) * factor;
            }
        }

        private static IconShape path(float stroke, float[] points) {
            int length = points.length;
            boolean closed = points[0] == points[length - 2] && points[1] == points[length - 1];
            if (closed) {
                float[] contour = new float[length - 2];
                System.arraycopy(points, 0, contour, 0, contour.length);
                return new IconShape(stroke, stroke / 2f, contour);
            }

            float[] contour = new float[length * 2];
            for (int i = 0; i < length; i += 2) {
                int previous = Math.max(0, i - 2), next = Math.min(length - 2, i + 2);
                float dx1 = points[i] - points[previous], dy1 = points[i + 1] - points[previous + 1];
                float dx2 = points[next] - points[i], dy2 = points[next + 1] - points[i + 1];
                if (i == 0) {
                    dx1 = dx2;
                    dy1 = dy2;
                }
                if (i == length - 2) {
                    dx2 = dx1;
                    dy2 = dy1;
                }
                float length1 = Mathf.len(dx1, dy1), length2 = Mathf.len(dx2, dy2);
                float nx1 = dy1 / length1, ny1 = -dx1 / length1;
                float nx2 = dy2 / length2, ny2 = -dx2 / length2;
                float factor = stroke / (2f * (1f + nx1 * nx2 + ny1 * ny2));
                float mx = (nx1 + nx2) * factor, my = (ny1 + ny2) * factor;
                contour[i] = points[i] + mx;
                contour[i + 1] = points[i + 1] + my;
                int opposite = contour.length - i - 2;
                contour[opposite] = points[i] - mx;
                contour[opposite + 1] = points[i + 1] - my;
            }
            return new IconShape(0, stroke / 2f, contour);
        }

        private static void ring(float[] inner, float[] outer, float color, float edge) {
            for (int i = 0; i < inner.length; i += 2) {
                int next = (i + 2) % inner.length;
                Fill.quad(inner[i], inner[i + 1], color,
                        outer[i], outer[i + 1], edge,
                        outer[next], outer[next + 1], edge,
                        inner[next], inner[next + 1], color);
            }
        }

        void draw(float x, float y, float scale) {
            float fringe = Math.min(0.5f, maxFringe * scale * 0.95f), half = stroke * scale / 2f;
            offset(inner, x, y, scale, stroke == 0 ? -fringe : -half + fringe);
            offset(outer, x, y, scale, stroke == 0 ? fringe : half - fringe);
            float color = Draw.getColorPacked();
            float clear = Color.intToFloatColor(Color.floatToIntColor(color) & 0x00ffffff);
            if (stroke == 0) {
                for (int i = 0; i < triangles.length; i += 3) {
                    int a = triangles[i] * 2, b = triangles[i + 1] * 2, c = triangles[i + 2] * 2;
                    Fill.tri(inner[a], inner[a + 1], inner[b], inner[b + 1], inner[c], inner[c + 1]);
                }
                ring(inner, outer, color, clear);
            } else {
                offset(innerFade, x, y, scale, -half - fringe);
                offset(outerFade, x, y, scale, half + fringe);
                ring(inner, outer, color, color);
                ring(inner, innerFade, color, clear);
                ring(outer, outerFade, color, clear);
            }
        }

        private void offset(float[] target, float x, float y, float scale, float distance) {
            for (int i = 0; i < contour.length; i += 2) {
                target[i] = x + contour[i] * scale + miters[i] * distance;
                target[i + 1] = y + contour[i + 1] * scale + miters[i + 1] * distance;
            }
        }
    }
}
