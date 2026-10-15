package com.github.squi2rel.arctools;

import arc.scene.Scene;

/**
 * Compatibility entrypoint for the former styles-only browser.
 */
public final class ArcStylesTool {
    private ArcStylesTool() {
    }

    public static void show(Scene scene) {
        ShowUIList.show(scene);
    }
}
