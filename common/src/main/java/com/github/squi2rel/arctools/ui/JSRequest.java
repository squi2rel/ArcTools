package com.github.squi2rel.arctools.ui;

import arc.func.Cons;
import arc.scene.Scene;
import arc.scene.ui.TextField;
import com.github.squi2rel.arc.ArcLib;
import com.github.squi2rel.arc.ui.ArcStyles;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Wrapper;

/**
 * Small typed JavaScript value request used by catalog extension buttons.
 */
public final class JSRequest {
    private JSRequest() {
    }

    public static <T> void request(Scene owner, Class<T> expectedType, Cons<T> consumer) {
        Window window = new RequestWindow(owner, "Add " + expectedType.getSimpleName());
        window.setModal(false);
        window.setMovable(true);
        window.closeOnBack();
        TextField expression = new TextField("Packages." + expectedType.getName() + ".linear");
        expression.setMessageText("JavaScript expression");
        window.cont.add(expression).growX().height(42f);
        window.buttons.button("Cancel", ArcStyles.toolTextButton, window::hide);
        window.buttons.button("Add", ArcStyles.textButton, () -> {
            try {
                Object value = evaluate(expression.getText());
                if (!expectedType.isInstance(value)) {
                    throw new IllegalArgumentException("Expected " + expectedType.getName());
                }
                consumer.get(expectedType.cast(value));
                window.hide();
            } catch (RuntimeException error) {
                expression.setColor(ArcStyles.error);
                expression.setMessageText(error.getMessage());
            }
        });
        window.show(owner);
        window.pack();
        expression.requestKeyboard();
        expression.setCursorPosition(Integer.MAX_VALUE);
    }

    private static final class RequestWindow extends Window implements Window.IDisposable {
        RequestWindow(Scene owner, String title) {
            super(owner, title, 300f, 100f, false, false);
        }
    }

    private static Object evaluate(String source) {
        return ArcLib.scripts().withContext(context -> {
            Scriptable scope = context.newObject(ArcLib.scripts().scope());
            scope.setParentScope(ArcLib.scripts().scope());
            Object value = context.evaluateString(scope, source, "UI request", 1, null);
            while (value instanceof Wrapper wrapper) value = wrapper.unwrap();
            return value;
        });
    }
}
