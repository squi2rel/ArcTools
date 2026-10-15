package com.github.squi2rel.arctools.ui;

import arc.graphics.Color;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.actions.Actions;
import arc.scene.style.Drawable;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.*;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import com.github.squi2rel.arc.ui.ArcStyles;

import java.util.*;

/**
 * Tabbable pane ported from mod-tools' IntTab, with Arc-only styles.
 */
public class IntTab {
    private static final Set<IntTab> instances = Collections.newSetFromMap(new WeakHashMap<>());
    public float titleWidth, eachWidth;
    public int cols;
    public boolean verticalLayout;

    private final String[] tabNames;
    private final Color[] tabColors;
    private final Table[] tabContents;
    private Drawable[] tabIcons;
    private final Map<String, Label> labels = new LinkedHashMap<>();
    private Table mainContainer, tabsBar;
    private ScrollPane contentPane;
    private int selectedIndex = -1;
    private boolean transitioning;
    private float uiScale;

    public IntTab(float titleWidth, String[] names, Color[] colors, Table[] tables) {
        this(titleWidth, names, colors, tables, Integer.MAX_VALUE, false);
    }

    public IntTab(float titleWidth, String[] names, Color color, Table[] tables) {
        this(titleWidth, names, fill(names.length, color), tables, Integer.MAX_VALUE, false);
    }

    public IntTab(float titleWidth, String[] names, Color[] colors, Table[] tables, int cols, boolean vertical) {
        if (names.length == 0 || names.length != colors.length || names.length != tables.length) {
            throw new IllegalArgumentException("Tab names, colors, and tables must have the same non-zero size");
        }
        this.titleWidth = titleWidth;
        this.tabNames = names;
        this.tabColors = colors;
        this.tabContents = tables;
        this.cols = cols;
        this.verticalLayout = vertical;
        uiScale = Scl.scl();
        instances.add(this);
    }

    /**
     * Pages not currently in a scroll pane are outside ArcRuntime's scene traversal.
     */
    public static void refreshDetachedPages() {
        float nextScale = Scl.scl();
        for (IntTab tab : instances) {
            float ratio = nextScale / tab.uiScale;
            tab.uiScale = nextScale;
            for (Table page : tab.tabContents) {
                if (page.parent == null) refreshDetached(page, ratio);
            }
        }
    }

    private static void refreshDetached(Element element, float ratio) {
        if (element instanceof Table table) table.rescale(ratio);
        element.invalidate();
        if (element instanceof TextField field) field.refreshFontMetrics();
        if (element instanceof Group group) {
            for (Element child : group.getChildren()) refreshDetached(child, ratio);
        }
    }

    public void setIcons(Drawable... icons) {
        if (icons.length != tabNames.length) throw new IllegalArgumentException("Icon count must match tab count");
        tabIcons = icons;
    }

    public Table build() {
        if (mainContainer != null) return mainContainer;
        TextureRegionDrawable white = (TextureRegionDrawable) ArcStyles.white;
        Drawable tabNone = white.tint(Color.clear);
        Drawable tabFlatOver = white.tint(Color.valueOf("454545"));
        tabNone.setMinWidth(0f);
        tabNone.setMinHeight(0f);
        tabFlatOver.setMinWidth(0f);
        tabFlatOver.setMinHeight(0f);
        mainContainer = new Table();
        mainContainer.name = "IntTab-MainContainer";
        tabsBar = new Table();
        tabsBar.name = "IntTab-TabsBar";
        contentPane = new ScrollPane(null, ArcStyles.scrollPane);
        contentPane.setFadeScrollBars(false);
        contentPane.setOverscroll(false, false);

        ScrollPane tabsPane = new ScrollPane(tabsBar, ArcStyles.scrollPane);
        tabsPane.setScrollingDisabled(verticalLayout, !verticalLayout);
        tabsPane.setFadeScrollBars(false);
        tabsPane.setOverscroll(false, false);
        if (verticalLayout) {
            mainContainer.add(tabsPane).growY().minWidth(0f).minHeight(0f);
            mainContainer.add(contentPane).grow().minWidth(0f).minHeight(0f);
        } else {
            // The content's preferred height must never squeeze or clip the tab buttons.
            mainContainer.add(tabsPane).height(42f).growX().minWidth(0f).row();
            mainContainer.add(contentPane).grow().minWidth(0f).minHeight(0f);
        }

        for (int i = 0; i < tabNames.length; i++) {
            int index = i;
            // Port of HopeStyles.clearb: transparent normally, flat highlight on hover/down.
            // HopeStyles only replaces these three fields on a copy of defaultb.
            Button.ButtonStyle style = new Button.ButtonStyle(ArcStyles.button);
            style.up = tabNone;
            style.over = tabFlatOver;
            style.down = style.over;
            Button button = new Button(style);
            Table title = new Table();
            if (tabIcons != null) title.add(new Image(tabIcons[i])).size(28f).padRight(4f);
            Label label = new Label(tabNames[i]);
            label.setWrap(false);
            label.setAlignment(Align.center);
            labels.put(tabNames[i], label);
            title.add(label).growX().minWidth(0f).minHeight(28f).center();
            button.add(title).growX().pad(4f);
            button.row();
            Image indicator = button.image(ArcStyles.white).growX().height(3f).get();
            button.update(() -> indicator.setColor(selectedIndex == index ? tabColors[index] : Color.gray));
            button.clicked(() -> selectTab(index, true));
            var cell = tabsBar.add(button).height(42f);
            if (eachWidth > 0f) {
                cell.width(eachWidth).minWidth(eachWidth).maxWidth(eachWidth);
            } else {
                cell.growX();
            }
            if ((i + 1) % cols == 0) tabsBar.row();
        }
        selectTab(0, false);
        return mainContainer;
    }

    public void selectTab(int index, boolean animate) {
        if (index < 0 || index >= tabContents.length || selectedIndex == index || transitioning) return;
        Table next = tabContents[index];
        if (selectedIndex < 0 || !animate) {
            contentPane.setWidget(next);
            selectedIndex = index;
            return;
        }
        transitioning = true;
        Table previous = tabContents[selectedIndex];
        previous.actions(Actions.sequence(Actions.fadeOut(0.16f), Actions.run(() -> {
            previous.color.a = 1f;
            contentPane.setWidget(next);
            next.color.a = 0f;
            next.actions(Actions.sequence(Actions.fadeIn(0.16f), Actions.run(() -> transitioning = false)));
            selectedIndex = index;
        })));
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public ScrollPane getContentPane() {
        return contentPane;
    }

    public Table getMainContainer() {
        return mainContainer;
    }

    public Table getTabsBar() {
        return tabsBar;
    }

    public Map<String, Label> getLabels() {
        return labels;
    }

    private static Color[] fill(int size, Color color) {
        Color[] colors = new Color[size];
        Arrays.fill(colors, color);
        return colors;
    }
}
