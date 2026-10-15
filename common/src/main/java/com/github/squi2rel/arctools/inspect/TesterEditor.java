package com.github.squi2rel.arctools.inspect;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Font;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.event.ChangeListener.ChangeEvent;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.style.BaseDrawable;
import arc.scene.style.Drawable;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextArea;
import arc.scene.ui.TextButton;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import arc.util.pooling.Pools;
import com.github.squi2rel.arc.ui.ArcStyles;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TextAreaTab's scrollable editor, gutter and completion popup without Mindustry assets.
 */
final class TesterEditor extends Table {
    private static final Pattern tokens = Pattern.compile("//[^\\r\\n]*|/\\*(?:[^*]|\\*(?!/))*(?:\\*/|$)|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|`(?:\\\\.|[^`\\\\])*`|\\b(?:0[xX][\\da-fA-F]+|\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)\\b|\\b(?:var|let|const|function|return|if|else|for|while|do|new|true|false|null|undefined|try|catch|finally|throw|this|switch|case|break|continue|typeof|instanceof|delete|in|of)\\b");
    private static final Color[] colors = {Color.white, Color.gray, Color.valueOf("a5d6a7"), Color.valueOf("ffcc80"), Color.valueOf("ce93d8")};
    final Area area = new Area();
    final ScrollPane pane = new ScrollPane(area);
    final CompletionPopup completion = new CompletionPopup();
    private final InspectorScripts scripts;
    boolean highlight = true, properties = true, autoComplete;
    private boolean pendingCompletion;
    private float completionDelay;
    private String lastText = "";
    private int lastCursor;
    private InspectorScripts.Completion suggestions;

    TesterEditor(InspectorScripts scripts) {
        this.scripts = scripts;
        name = "tester-editor";
        area.name = "tester-code";
        TextField.TextFieldStyle style = new TextField.TextFieldStyle(area.getStyle());
        style.background = new BaseDrawable();
        area.setStyle(style);
        area.setOnlyFontChars(false);
        area.setFocusTraversal(false);
        area.setPrefRows(8);
        pane.setScrollingDisabled(true, false);
        pane.setFadeScrollBars(false);
        pane.setOverscroll(false, false);
        pane.setFlickScroll(false);
        Element gutter = new Element() {
            @Override
            public float getPrefWidth() {
                arc.graphics.g2d.GlyphLayout layout = new arc.graphics.g2d.GlyphLayout();
                layout.setText(area.getStyle().font, Integer.toString(Math.max(1, area.getLines())));
                return layout.width + 6f;
            }

            @Override
            public void draw() {
                area.prepare();
                Font font = area.getStyle().font;
                Color old = font.getColor().cpy();
                font.setColor(Color.gray);
                float lineHeight = font.getLineHeight();
                float top = y + getHeight() + pane.getVisualScrollY();
                int logical = 1;
                for (int line = 0; line < area.getLines(); line++) {
                    int start = area.lineStart(line);
                    boolean first = line == 0 || start > 0 && area.getText().charAt(start - 1) == '\n';
                    float baseline = top - line * lineHeight;
                    if (first && baseline >= y && baseline <= y + getHeight() + lineHeight) {
                        int current = 1;
                        for (int i = 0; i < area.getCursorPosition(); i++)
                            if (area.getText().charAt(i) == '\n') current++;
                        font.setColor(current == logical ? ArcStyles.accent : Color.lightGray);
                        font.draw(Integer.toString(logical), x + getWidth() - 4f, baseline, Align.right);
                    }
                    if (first) logical++;
                }
                font.setColor(old);
            }
        };
        add(gutter).growY();
        add(pane).grow();
        area.addCaptureListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, KeyCode key) {
                if (completion.handle(key)) {
                    event.cancel();
                    return true;
                }
                if (key == KeyCode.tab && !Core.input.ctrl() && !Core.input.alt()) {
                    area.handleTab(Core.input.shift());
                    completion.consumed = true;
                    event.cancel();
                    return true;
                }
                if (key == KeyCode.enter && !Core.input.ctrl() && !Core.input.alt()) {
                    area.paste("\n", true);
                    completion.consumed = true;
                    event.cancel();
                    return true;
                }
                if (key == KeyCode.space && Core.input.ctrl()) {
                    complete();
                    event.cancel();
                    return true;
                }
                if (Core.input.ctrl() && key == KeyCode.slash) {
                    area.comment(Core.input.shift());
                    event.cancel();
                    return true;
                }
                if (Core.input.ctrl() && !Core.input.shift() && key == KeyCode.d) {
                    area.selectWord();
                    event.cancel();
                    return true;
                }
                return false;
            }

            @Override
            public boolean keyTyped(InputEvent event, char character) {
                if ((character == '\t' || character == '\n') && completion.consumed) {
                    completion.consumed = false;
                    event.cancel();
                    return true;
                }
                if (character == 8 && Core.input.ctrl() && !area.selecting()) {
                    area.deleteWord();
                    event.cancel();
                    return true;
                }
                return false;
            }

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                completion.hide();
                return false;
            }
        });
    }

    void complete() {
        lastText = area.getText();
        lastCursor = area.getCursorPosition();
        pendingCompletion = false;
        area.requestKeyboard();
        suggestions = scripts.complete(area.getText(), area.getCursorPosition(), properties);
        completion.show(suggestions.names());
    }

    void changedText() {
        area.prepare();
        area.invalidateHierarchy();
        trackCursor();
        if (autoComplete) {
            pendingCompletion = true;
            completionDelay = 0.05f;
        } else completion.hide();
    }

    private void trackCursor() {
        pane.validate();
        float lineHeight = area.getStyle().font.getLineHeight();
        pane.scrollTo(0f, area.getHeight() - (area.getCursorLine() + 2) * lineHeight, 1f, lineHeight * 2f, false, false);
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (!lastText.equals(area.getText())) {
            lastText = area.getText();
            changedText();
        } else if (lastCursor != area.getCursorPosition()) trackCursor();
        lastCursor = area.getCursorPosition();
        if (pendingCompletion && (completionDelay -= delta) <= 0f) {
            pendingCompletion = false;
            int cursor = area.getCursorPosition();
            if (Core.scene.getKeyboardFocus() == area && cursor > 0 &&
                    (Character.isJavaIdentifierPart(area.getText().charAt(cursor - 1)) || area.getText().charAt(cursor - 1) == '.'))
                complete();
            else completion.hide();
        }
        Element focus = Core.scene.getKeyboardFocus();
        if (completion.getScene() != null && focus != area && (focus == null || !focus.isDescendantOf(completion)))
            completion.hide();
    }

    final class Area extends TextArea {
        private String syntaxText;
        private byte[] syntaxColors = new byte[0];

        Area() {
            super("");
        }

        @Override
        protected void sizeChanged() {
            super.sizeChanged();
            syntaxText = null;
        }

        private boolean changeText(String oldText, String newText) {
            if (newText.equals(oldText)) return false;
            text = newText;
            ChangeEvent event = Pools.obtain(ChangeEvent.class, ChangeEvent::new);
            boolean cancelled = fire(event);
            text = cancelled ? oldText : newText;
            Pools.free(event);
            return !cancelled;
        }

        int lineStart(int line) {
            return line * 2 < linesBreak.size ? linesBreak.get(line * 2) : text.length();
        }

        void prepare() {
            calculateOffsets();
        }

        @Override
        protected void calculateOffsets() {
            super.calculateOffsets();
            firstLineShowing = 0;
        }

        @Override
        public float getPrefHeight() {
            float height = getStyle().font.getLineHeight() * Math.max(1, getLines());
            Drawable background = getStyle().background;
            if (background != null) height += background.getTopHeight() + background.getBottomHeight();
            return Math.max(super.getPrefHeight(), height + TesterEditor.this.getHeight() / 2f);
        }

        @Override
        protected void drawText(Font font, float x, float y) {
            if (!highlight) {
                super.drawText(font, x, y);
                return;
            }
            if (!text.equals(syntaxText)) {
                syntaxText = text;
                syntaxColors = new byte[text.length()];
                Matcher matcher = tokens.matcher(text);
                while (matcher.find()) {
                    char first = text.charAt(matcher.start());
                    byte color = (byte) (first == '/' ? 1 : first == '\'' || first == '"' || first == '`' ? 2 : Character.isDigit(first) ? 3 : 4);
                    Arrays.fill(syntaxColors, matcher.start(), matcher.end(), color);
                }
            }
            Color old = font.getColor().cpy();
            boolean markup = font.getData().markupEnabled;
            font.getData().markupEnabled = false;
            try {
                int first = Math.max(0, (int) (pane.getVisualScrollY() / font.getLineHeight()) - 1);
                int last = Math.min(linesBreak.size / 2, first + (int) (TesterEditor.this.getHeight() / font.getLineHeight()) + 3);
                for (int line = first; line < last; line++) {
                    int start = linesBreak.get(line * 2), end = linesBreak.get(line * 2 + 1);
                    for (int from = start; from < end; ) {
                        int to = from + 1;
                        while (to < end && syntaxColors[to] == syntaxColors[from]) to++;
                        font.setColor(colors[syntaxColors[from]]);
                        font.getColor().a = old.a;
                        float offset = glyphPositions.get(from) - glyphPositions.get(start);
                        font.draw(text, x + offset, y - line * font.getLineHeight(), from, to, 0f, Align.left, false);
                        from = to;
                    }
                }
            } finally {
                font.setColor(old);
                font.getData().markupEnabled = markup;
            }
        }

        Vec2 cursorPosition() {
            prepare();
            int line = cursorLine * 2;
            float left = style.background == null ? 0f : style.background.getLeftWidth();
            float offset = line < linesBreak.size && cursor < glyphPositions.size ? glyphPositions.get(cursor) - glyphPositions.get(linesBreak.get(line)) : 0f;
            return localToStageCoordinates(new Vec2(left + offset, getTextY(style.font, style.background) - (cursorLine + 1) * style.font.getLineHeight()));
        }

        void move(KeyCode key) {
            requestKeyboard();
            if (Core.scene.keyDown(key)) Core.scene.keyUp(key);
            trackCursor();
        }

        void selectWord() {
            int start = hasSelection ? selectionStart : cursor;
            while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) start--;
            int end = cursor;
            while (end < text.length() && Character.isJavaIdentifierPart(text.charAt(end))) end++;
            setSelection(start, end);
        }

        boolean selecting() {
            return hasSelection;
        }

        void deleteWord() {
            int end = cursor;
            moveCursor(false, true);
            if (cursor < end) {
                changeText(text, text.substring(0, cursor) + text.substring(end));
                updateDisplayText();
            }
        }

        void handleTab(boolean unindent) {
            prepare();
            if (!hasSelection) {
                if (!unindent) paste("\t", true);
                else {
                    int start = lineStart(cursorLine);
                    if (start < text.length() && text.charAt(start) == '\t') {
                        int position = Math.max(start, cursor - 1);
                        if (changeText(text, text.substring(0, start) + text.substring(start + 1))) {
                            updateDisplayText();
                            setCursorPosition(position);
                        }
                    }
                }
                return;
            }
            changeLines(unindent ? null : "\t");
        }

        void comment(boolean block) {
            if (block) {
                int start = hasSelection ? Math.min(cursor, selectionStart) : cursor;
                int end = hasSelection ? Math.max(cursor, selectionStart) : cursor;
                if (start >= 2 && end + 2 <= text.length() && text.startsWith("/*", start - 2) && text.startsWith("*/", end)) {
                    if (changeText(text, text.substring(0, start - 2) + text.substring(start, end) + text.substring(end + 2))) {
                        updateDisplayText();
                        setSelection(start - 2, end - 2);
                    }
                } else if (changeText(text, text.substring(0, start) + "/*" + text.substring(start, end) + "*/" + text.substring(end))) {
                    updateDisplayText();
                    setSelection(start + 2, end + 2);
                }
            } else changeLines("//");
        }

        private void changeLines(String prefix) {
            int start = hasSelection ? Math.min(cursor, selectionStart) : cursor;
            int end = hasSelection ? Math.max(cursor, selectionStart) : cursor;
            int first = text.lastIndexOf('\n', Math.max(0, start - 1)) + 1;
            if (start == 0) first = 0;
            ArrayList<Integer> starts = new ArrayList<>();
            starts.add(first);
            for (int i = first; i < end; i++) if (text.charAt(i) == '\n' && i + 1 < end) starts.add(i + 1);
            boolean remove = prefix == null;
            if ("//".equals(prefix)) {
                remove = true;
                for (int line : starts) {
                    int at = line;
                    while (at < text.length() && (text.charAt(at) == ' ' || text.charAt(at) == '\t')) at++;
                    if (!text.startsWith("//", at)) {
                        remove = false;
                        break;
                    }
                }
            }
            String actual = prefix == null ? "\t" : prefix;
            StringBuilder changed = new StringBuilder(text);
            int from = start, to = end;
            for (int i = starts.size() - 1; i >= 0; i--) {
                int at = starts.get(i);
                if (remove && "//".equals(actual))
                    while (at < text.length() && (text.charAt(at) == ' ' || text.charAt(at) == '\t')) at++;
                if (remove) {
                    if (!text.startsWith(actual, at)) continue;
                    changed.delete(at, at + actual.length());
                    if (at < start) from -= Math.min(actual.length(), start - at);
                    if (at < end) to -= Math.min(actual.length(), end - at);
                } else {
                    changed.insert(at, actual);
                    if (at <= start) from += actual.length();
                    if (at <= end) to += actual.length();
                }
            }
            if (changeText(text, changed.toString())) {
                updateDisplayText();
                if (hasSelection) setSelection(Math.max(0, from), Math.max(0, to));
                else setCursorPosition(Math.max(0, to));
            }
        }
    }

    final class CompletionPopup extends Table {
        final Table list = new Table();
        final ScrollPane scroll = new ScrollPane(list);
        List<String> names = List.of();
        int selected;
        boolean consumed;

        CompletionPopup() {
            super(ArcStyles.panel);
            name = "tester-completion";
            scroll.setFadeScrollBars(false);
            scroll.setOverscroll(false, false);
            scroll.setCancelTouchFocus(false);
            add(scroll).grow();
        }

        void show(List<String> suggestions) {
            hide();
            if (suggestions.isEmpty()) return;
            names = suggestions;
            selected = 0;
            list.clearChildren();
            for (int i = 0; i < suggestions.size(); i++) {
                int index = i;
                TextButton button = new TextButton(suggestions.get(i), ArcStyles.toolTextButton);
                button.getLabelCell().labelAlign(Align.left).padLeft(4f).padRight(4f);
                button.clicked(() -> select(index));
                list.add(button).growX().height(24f).row();
            }
            Core.scene.add(this);
            setSize(Math.min(400f, Math.max(160f, getPrefWidth())), Math.min(216f, getPrefHeight()));
            Vec2 cursor = area.cursorPosition();
            setPosition(Mathf.clamp(cursor.x, 0f, Math.max(0f, Core.scene.getWidth() - getWidth())),
                    Mathf.clamp(cursor.y - getHeight(), 0f, Math.max(0f, Core.scene.getHeight() - getHeight())));
            highlight();
        }

        void hide() {
            remove();
            names = List.of();
        }

        boolean handle(KeyCode key) {
            consumed = false;
            if (getScene() == null) return false;
            if (key == KeyCode.escape) {
                hide();
                return true;
            }
            if (key == KeyCode.tab || key == KeyCode.enter) {
                select(selected);
                consumed = true;
                return true;
            }
            if (key != KeyCode.up && key != KeyCode.down) return false;
            selected = Mathf.clamp(selected + (key == KeyCode.up ? -1 : 1), 0, names.size() - 1);
            highlight();
            return true;
        }

        private void select(int index) {
            String text = area.getText(), chosen = names.get(index);
            area.setText(text.substring(0, suggestions.start()) + chosen + text.substring(suggestions.end()));
            area.setCursorPosition(suggestions.start() + chosen.length());
            area.clearSelection();
            area.requestKeyboard();
            hide();
        }

        private void highlight() {
            for (int i = 0; i < list.getChildren().size; i++) {
                TextButton button = (TextButton) list.getChildren().get(i);
                button.getLabel().setColor(i == selected ? ArcStyles.accent : Color.white);
                if (i == selected) {
                    list.validate();
                    scroll.scrollTo(button.x, button.y, button.getWidth(), button.getHeight(), false, false);
                }
            }
        }
    }
}
