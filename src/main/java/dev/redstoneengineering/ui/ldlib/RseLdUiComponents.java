package dev.redstoneengineering.ui.ldlib;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.math.Size;
import dev.vfyjxf.taffy.style.FlexWrap;
import net.minecraft.world.entity.player.Player;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import net.minecraft.network.chat.Component;
import org.appliedenergistics.yoga.YogaFlexDirection;

import java.util.function.Supplier;

/** Reusable LDLib2 primitives for formula-first RSE engineering HMIs. */
public final class RseLdUiComponents {
    private RseLdUiComponents() {}

    /**
     * The client canvas must fit the actual scaled Minecraft screen, not the
     * developer's monitor. LDLib2 evaluates the size provider at screen init.
     * The same preferred dimensions still work on the logical server menu.
     */
    public static ModularUI responsiveUi(UIElement root, Player player, int preferredWidth, int preferredHeight) {
        var ui = UI.of(
                root,
                java.util.List.of(StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.GDP)),
                screen -> Size.of(
                        Math.max(1, Math.min(preferredWidth, screen.getWidth() - 24)),
                        Math.max(1, Math.min(preferredHeight, screen.getHeight() - 30))
                )
        );
        return ModularUI.of(ui, player);
    }

    private static Label wrap(Label label) {
        label.textStyle(style -> style.textWrap(TextWrap.WRAP)
                .adaptiveWidth(false)
                .adaptiveHeight(true));
        return label;
    }

    private static Label wrappedLabel(String text) {
        var label = new Label();
        label.setText(text);
        return wrap(label);
    }

    private static Label liveText(Supplier<String> source) {
        var label = new Label();
        // Most engineering snapshots are unchanged between ticks. Reuse the
        // Component when its rendered value is unchanged instead of allocating
        // a fresh one on every LDLib2 CHANGED_PERIODIC sync check.
        final String[] lastText = {null};
        final Component[] lastComponent = {Component.empty()};
        label.bind(DataBindingBuilder.componentS2C(() -> {
            String current = source.get();
            if (!java.util.Objects.equals(lastText[0], current)) {
                lastText[0] = current;
                lastComponent[0] = Component.literal(current == null ? "" : current);
            }
            return lastComponent[0];
        }).build());
        return wrap(label);
    }

    /** Wrapped explanatory text for dense workbench pages; unlike vanilla
     * Label defaults, full warnings and units must remain readable. */
    public static Label note(String text) {
        var label = wrappedLabel(text);
        label.layout(l -> l.widthPercent(100));
        return label;
    }

    public static Label title(String text) {
        var label = wrappedLabel(text);
        label.layout(l -> l.widthPercent(100));
        return label;
    }

    public static UIElement formulaCard(Supplier<String> equation) {
        return new UIElement()
                .addClass("panel_bg")
                .layout(l -> l.paddingAll(8))
                .addChild(liveText(equation).layout(l -> l.widthPercent(100)));
    }

    /** Immutable formula: no per-tick server-to-client binding is needed. */
    public static UIElement formulaCard(String equation) {
        var label = wrappedLabel(equation);
        label.layout(l -> l.widthPercent(100));
        return new UIElement()
                .addClass("panel_bg")
                .layout(l -> l.paddingAll(8))
                .addChild(label);
    }

    public static UIElement liveRow(String role, String symbol, Supplier<String> value) {
        return new UIElement()
                .layout(l -> l.flexDirection(YogaFlexDirection.ROW).flexWrap(FlexWrap.WRAP).gapAll(9).paddingAll(2))
                .addChildren(
                        wrappedLabel(role).layout(l -> l.width(92)),
                        wrappedLabel(symbol).layout(l -> l.width(90)),
                        liveText(value).layout(l -> l.flex(1).minWidth(165))
                );
    }

    public static UIElement fixedRow(String symbol, Supplier<String> value, String reason) {
        return new UIElement()
                .layout(l -> l.flexDirection(YogaFlexDirection.ROW).flexWrap(FlexWrap.WRAP).gapAll(9).paddingAll(2))
                .addChildren(
                        new Label().setText("FIXED").layout(l -> l.width(92)),
                        wrappedLabel(symbol).layout(l -> l.width(90)),
                        liveText(() -> value.get() + " • " + reason).layout(l -> l.flex(1).minWidth(165))
                );
    }

    public static Button serverAction(String label, Runnable action) {
        var button = new Button()
                .setText(label)
                .setOnServerClick(event -> action.run());
        // The default 14px LDLib2 button is too short for dense engineering HMIs.
        // More generous hit targets are scrollable within the workspace.
        button.layout(layout -> layout.height(20).paddingAll(4));
        return button;
    }

    /**
     * Shared viewport for all RSE block-facing HMI families. Pages remain in one
     * UI tree, but only the selected page participates in layout.
     * Layouts follow the viewport when possible, wrapping long formulas
     * and retaining two-axis scroll when narrow Minecraft windows require it.
     */
    public static UIElement tabbedWorkspace(int width, int height, int contentWidth,
                                             String[] labels, UIElement[] pages) {
        if (labels.length != pages.length || pages.length == 0) {
            throw new IllegalArgumentException("One label required per workspace page");
        }
        var root = new UIElement().addClass("panel_bg");
        root.layout(l -> l.widthPercent(100).flex(1).paddingAll(8).gapAll(6));
        var tabs = new UIElement().addClass("panel_bg");
        tabs.layout(l -> l.flexDirection(YogaFlexDirection.ROW).flexWrap(FlexWrap.WRAP).gapAll(5).paddingAll(5));

        var scroller = new ScrollerView().scrollerStyle(style -> style
                .mode(ScrollerMode.BOTH)
                .verticalScrollDisplay(ScrollDisplay.AUTO)
                .horizontalScrollDisplay(ScrollDisplay.AUTO)
                .minScrollPixel(8).maxScrollPixel(72));
        scroller.layout(l -> l.flex(1));
        scroller.viewPort(view -> view.layout(l -> l.paddingAll(8)));
        // Prefer wrapping to the visible viewport instead of forcing every
        // 620px screen to pan sideways through an 850px wide page.
        // Only very narrow screens fall back to horizontal scrolling.
        final int readableMinWidth = Math.min(contentWidth, 440);
        scroller.viewContainer(view -> view.layout(l ->
                l.widthPercent(100).minWidth(readableMinWidth).paddingAll(8).gapAll(8)));

        var tabButtons = new java.util.ArrayList<Button>(labels.length);
        for (int i = 0; i < pages.length; i++) {
            var page = pages[i];
            page.layout(l -> l.widthPercent(100).paddingAll(12).gapAll(10));
            page.setDisplay(i == 0);
            scroller.addScrollViewChild(page);
            final int selectedIndex = i;
            var button = new Button().setText(i == 0 ? "▶ " + labels[i] : labels[i]).setOnClick(event -> {
                for (UIElement candidate : pages) candidate.setDisplay(candidate == page);
                for (int j = 0; j < tabButtons.size(); j++) {
                    tabButtons.get(j).setText(j == selectedIndex ? "▶ " + labels[j] : labels[j]);
                }
                scroller.horizontalScroller.setNormalizedValue(0);
                scroller.verticalScroller.setNormalizedValue(0);
            });
            button.layout(l -> l.height(20).minWidth(82).paddingAll(4));
            tabButtons.add(button);
            tabs.addChild(button);
        }

        root.addChildren(tabs,
                new Label().setText("SCROLL • wheel Y • Shift+wheel X"),
                scroller);
        return root;
    }

    /**
     * Standalone instrument HMIs (PID, scope, logic analyzer, conditioner)
     * keep a persistent selected-tab indicator without inventing UI state.
     * The first tab corresponds to the only initially visible page.
     */
    public static UIElement standaloneTabs(ScrollerView scroller, String[] labels, UIElement[] pages) {
        if (labels.length != pages.length || pages.length == 0) {
            throw new IllegalArgumentException("Standalone tab labels and pages must match");
        }
        var tabs = new UIElement().addClass("panel_bg");
        tabs.layout(l -> l.flexDirection(YogaFlexDirection.ROW)
                .flexWrap(FlexWrap.WRAP).gapAll(5).paddingAll(5));
        var buttons = new java.util.ArrayList<Button>(labels.length);
        for (int i = 0; i < pages.length; i++) {
            final int selected = i;
            var button = new Button()
                    .setText(i == 0 ? "▶ " + labels[i] : labels[i])
                    .setOnClick(event -> {
                        for (int j = 0; j < pages.length; j++) {
                            pages[j].setDisplay(j == selected);
                            buttons.get(j).setText(j == selected ? "▶ " + labels[j] : labels[j]);
                        }
                        scroller.horizontalScroller.setNormalizedValue(0);
                        scroller.verticalScroller.setNormalizedValue(0);
                    });
            button.layout(l -> l.height(20).minWidth(82).paddingAll(4));
            buttons.add(button);
            tabs.addChild(button);
        }
        return tabs;
    }

    public static UIElement workspacePage(UIElement... contents) {
        return new UIElement().addChildren(contents);
    }

    public static Label authorityFooter() {
        return title("SERVER AUTHORITY • UI is presentation + validated operator intent");
    }
}
