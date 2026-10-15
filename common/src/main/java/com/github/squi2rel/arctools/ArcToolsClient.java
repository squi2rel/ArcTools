package com.github.squi2rel.arctools;

import arc.Core;
import arc.Events;
import arc.scene.Scene;
import com.github.squi2rel.arc.Trigger;
import com.github.squi2rel.arc.runtime.ArcClientRuntime;
import com.github.squi2rel.arc.runtime.RuntimeApplication;
import com.github.squi2rel.arctools.inspect.ArcInspector;
import com.github.squi2rel.arctools.inspect.TesterEvents;
import com.github.squi2rel.arctools.ui.IntTab;
import com.github.squi2rel.arctools.ui.Window;

public final class ArcToolsClient {
    private static volatile boolean menuRebuildPending;
    private static volatile boolean windowsRefreshPending;
    private static boolean installed;
    private static boolean initialized;
    private static boolean scriptsStarted;
    private static boolean disposed;

    private ArcToolsClient() {
    }

    public static void init() {
        if (disposed) return;
        if (!installed) {
            installed = true;
            Events.run(Trigger.init, ArcToolsClient::initialize);
            Events.run(Trigger.ready, ArcToolsClient::startScripts);
            Events.run(Trigger.update, ArcToolsClient::tick);
            Events.run(Trigger.draw, ArcToolsClient::beforeDraw);
            Events.run(Trigger.dispose, ArcToolsClient::dispose);
            Events.run(Trigger.scaleChanged, () -> {
                menuRebuildPending = true;
                windowsRefreshPending = true;
            });
            Events.run(Trigger.fontsChanged, () -> {
                menuRebuildPending = true;
                windowsRefreshPending = true;
            });
        }
        // Loader entrypoints may run before or after ArcLib initializes and becomes ready.
        initialize();
        startScripts();
    }

    private static void initialize() {
        if (disposed || initialized || Core.app == null || Core.app.isHeadless()
                || !(Core.app instanceof RuntimeApplication app) || !app.ready())
            return;
        Scene scene = ArcClientRuntime.scene();
        if (scene == null) return;
        Window.install(scene);
        ArcInspector.install(scene);
        ArcToolsMenu.install(scene);
        menuRebuildPending = false;
        windowsRefreshPending = false;
        initialized = true;
        ArcTools.LOGGER.info("ArcTools initialized");
    }

    public static void tick() {
        if (disposed) return;
        if (!initialized) initialize();
        if (!initialized) return;
        Scene scene = ArcClientRuntime.scene();
        if (scene == null) return;
        rebuildMenu(scene);
        refreshWindows(scene);
        ArcInspector inspector = ArcInspector.install(scene);
        ArcToolsMenu.install(scene);
        startScripts();
        Events.fire(new TesterEvents.ClientTick());
        inspector.tick();
    }

    private static void startScripts() {
        if (disposed || scriptsStarted) return;
        if (!initialized) initialize();
        if (!initialized || !(Core.app instanceof RuntimeApplication app) || !app.ready()) return;
        Scene scene = ArcClientRuntime.scene();
        if (scene == null) return;
        scriptsStarted = true;
        ArcInspector.install(scene).startScripts();
        Events.fire(new TesterEvents.ClientStarted());
    }

    public static void beginInspect() {
        if (disposed) return;
        if (!initialized) init();
        if (!initialized) return;
        Scene scene = ArcClientRuntime.scene();
        if (scene == null) return;
        ArcInspector.install(scene).pick();
    }

    private static void beforeDraw() {
        if (disposed || !initialized) return;
        Scene scene = ArcClientRuntime.scene();
        if (scene == null) return;
        rebuildMenu(scene);
        refreshWindows(scene);
        ArcInspector.beforeDraw(scene);
    }

    private static void rebuildMenu(Scene scene) {
        if (!menuRebuildPending || disposed || !initialized || scene == null) return;
        menuRebuildPending = false;
        ArcToolsMenu.rebuild(scene);
    }

    private static void refreshWindows(Scene scene) {
        if (!windowsRefreshPending || disposed || !initialized || scene == null) return;
        windowsRefreshPending = false;
        Window.refreshAll(scene);
        IntTab.refreshDetachedPages();
    }

    public static void dispose() {
        if (disposed) return;
        disposed = true;
        if (!initialized) return;
        Scene scene = ArcClientRuntime.scene();
        if (scene == null) return;
        Events.fire(new TesterEvents.ClientStopping());
        ArcInspector inspector = ArcInspector.get(scene);
        if (inspector != null) inspector.dispose();
    }
}
