# ArcTools

ArcTools provides the inspector and Rhino tester tools for ArcLib. It is derived
from [mod-tools](https://github.com/I-hope1/mod-tools) revision `1c89d0da` and remains licensed under GNU Affero General
Public License v3.0 only.

The mod icon comes from [
`assets/icon.png` in mod-tools at the same revision](https://github.com/I-hope1/mod-tools/blob/1c89d0dac3b5557eca30733c94299ceca41ae2c0/assets/icon.png)
and is shared by all loader artifacts. The upstream file contains JPEG data; it is resized to 128×128 and re-encoded as
PNG here.

## Source layout

- `common`: loader- and Minecraft-independent inspector/tester sources, depending only on ArcLib's plain Java common
  API.
- `<minecraft>/fabric` and `forge`/`neoforge`: thin loader entrypoints, version compatibility metadata, and final
  packaging. These compile against loader APIs without Minecraft, mappings, Loom, or Fabric API.

ArcTools requires the matching ArcLib Minecraft version and loader, with Rhino 1.9.1 or newer supplied by ArcLib.
Supported versions are Minecraft 1.20.1 on Fabric and Forge (including the compatible NeoForge 47.1 fork), and Minecraft
1.21.1, 1.21.11, and 26.2 on Fabric and NeoForge. Minecraft 1.20.1 requires Java 17, Minecraft 1.21.1 and 1.21.11
require Java 21, and Minecraft 26.2 requires Java 25. The standalone common module targets Java 17.

`ArcToolsClient.init()` subscribes once to ArcLib's `Trigger.init`, `ready`, `update`, `draw`, and `dispose`
notifications. It
also catches up if ArcLib is already initialized or ready. Inspector and Rhino initialization wait until ready so they
do not block the game's first frame setup. Startup scripts run once when ready, tester updates run once per tick, and
the inspector is disposed before ArcLib releases its resources. Headless runtimes do not install UI or emit tester
client events. Element selection uses Arc's existing scene input listener, including `Ctrl+Shift+C`; ArcTools has no
mixins or direct game API calls.

## Tools and shortcuts

The **UI List** window exposes the ArcLib/Minecraft equivalents of mod-tools' Icon/Tex, Styles, Colors, and Interps
pages. Its search field uses `Ctrl+F`; `Ctrl+Shift+F` clears the current query before focusing it.

Open **Windows** from the **Arc Tools** menu to manage tool windows, including hidden, minimized, and exception
windows. Double-click a title or use its show button to restore and focus it. The manager provides pin,
maximize/restore, close, and remove controls. Reusable tools stay in the list when closed;
temporary inspector, detail, watch, script, and exception windows are cleaned up after their closing animation.
Removing a reusable window also unregisters it from the manager.

Open **Settings** from the **Arc Tools** menu for the upstream-style window with **Tester**, **Review Element**, and
**UI** sections. Right-click the Tester editor for **Stop**, **Tasks**, **Import**, and **Export**.
With **Multiple Windows** enabled, closed Tester windows are reused before creating another instance.

Inspector shortcuts:

- `Ctrl+Shift+C`: select an element to inspect.
- `Ctrl+Alt+D`: toggle bounds for the selected element and its children.
- `Ctrl+Alt+Shift+D`: enable bounds and select the element subtree to outline.
- `F3` / `Shift+F3`: move to the next / previous tree search result.
- `Ctrl+Tab` / `Ctrl+Shift+Tab`: switch tool windows; `Shift+F4`: close the front tool window.
- On a focused element row: `I` opens details, `P` picks an image drawable, `Delete` removes it (`Shift+Delete` skips
  confirmation), `Left` / `Right` collapse or expand a group, `F` pins it, and `R` opens method invocation.

Tester shortcuts:

- `Ctrl+Shift+Enter`: execute the editor contents.
- `Ctrl+Alt+Shift+Up` / `Ctrl+Alt+Shift+Down`: move through history.
- `Ctrl+Shift+D`: show result details.
- `Alt+V`: preview a texture result.

Tester, startup scripts, and scheduled bookmarks use ArcLib's default environment, available to Java callers through
`com.github.squi2rel.arc.ArcLib.scripts()`. Variables, functions, and `$0` are shared across Tester windows and callers
of this singleton. Background execution uses the same environment lock. UI and asynchronous callbacks wait for the
running script to finish; starting another script explicitly while the environment is busy fails immediately.

These defaults follow the [mod-tools Wiki](https://i-hope1.github.io/mod-tools/).

See the [Tester comparison](docs/tester-upstream.md) for the checked upstream controls and remaining platform
adaptations.

## IntelliJ IDEA

Open this directory as a Gradle project. The root build includes `common` and all four Minecraft versions, so IDEA
imports their source sets and dependencies together. After updating an existing checkout, use **Reload All Gradle
Projects** in the Gradle tool window.

## Build

Minecraft version builds use the same `gradle/java-conventions.gradle` and `gradle/loader-packaging.gradle` conventions
as ArcLib and DynamicUI, including explicit Java 17/21/25 toolchains. ArcTools has no game-specific bridge: its loader
modules compile against the matching loader APIs and package only `arctools-common`. ArcLib and Rhino remain external
dependencies supplied by the installed ArcLib mod.

Only the plain Java `common` module in the sibling `../multiplatform` ArcLib repository is resolved through Gradle
composite builds. Version-specific ArcLib backends are not part of the ArcTools build graph.

```bash
./gradlew buildAll
```

`buildAll` also collects the main loader jars for every supported version into the project root's `build/libs/`
directory for uploading. This directory is synchronized with the current build, removing stale artifacts; sources,
development, thin, and common jars are excluded.

Build one version with `./gradlew -p <minecraft> build`, where `<minecraft>` is `1.20.1`, `1.21.1`, `1.21.11`, or
`26.2`. Build the standalone Java module with `./gradlew -p common build`.

Final loader jars are written below `<minecraft>/<loader>/build/libs/` and include the Minecraft version in their name.

Each loader's `check` task verifies source and dependency isolation from game APIs. To launch a development game, use
the matching ArcLib backend's run task and install the built ArcTools jar into that run directory's `mods` folder;
ArcTools itself no longer needs game development plugins.
