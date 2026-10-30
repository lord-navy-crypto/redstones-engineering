package dev.redstoneengineering.ui.ldlib;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.math.Size;
import dev.vfyjxf.taffy.style.FlexWrap;
import net.minecraft.world.entity.player.Player;
import dev.redstoneengineering.ui.menu.EngineeringDeviceMenu;
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

    /** Small title for the fixed header; detailed page titles remain unchanged. */
    public static Label compactTitle(String text) {
        var label = wrappedLabel(text);
        label.textStyle(style -> style.fontSize(9f).textWrap(TextWrap.WRAP)
                .adaptiveWidth(false).adaptiveHeight(true));
        label.layout(l -> l.widthPercent(100));
        return label;
    }

    private static Label compactLabel(Label label) {
        label.textStyle(style -> style.fontSize(8f).textWrap(TextWrap.WRAP)
                .adaptiveWidth(false).adaptiveHeight(true));
        return label;
    }

    private static Label compactLiveText(Supplier<String> source) {
        return compactLabel(liveText(source));
    }

    private static Label compactWrappedLabel(String text) {
        return compactLabel(wrappedLabel(text));
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

    /**
     * Compact persistent, responsive dashboard tiles for shared engineering HMIs.
     * Tile content is a server-synchronized text binding supplied by the caller,
     * never an independent client-side measurement or authority decision.
     * Narrow Minecraft windows wrap tiles instead of cropping the statuses.
     */
    public static UIElement statusTile(String label, Supplier<String> serverReadback) {
        return new UIElement().addClass("panel_bg")
                .layout(l -> l.flex(1).minWidth(76).flexDirection(YogaFlexDirection.COLUMN).paddingAll(3).gapAll(1))
                .addChildren(
                        compactWrappedLabel(label),
                        compactLiveText(serverReadback).layout(l -> l.widthPercent(100))
                );
    }

    public static UIElement statusRail(UIElement... tiles) {
        var rail = new UIElement().addClass("panel_bg");
        rail.layout(l -> l.widthPercent(100).flexDirection(YogaFlexDirection.ROW)
                .flexWrap(FlexWrap.WRAP).gapAll(3).paddingAll(3));
        rail.addChildren(tiles);
        return rail;
    }

    /**
     * Server-guided first action for the Overview pages. Unlike a fake "OK"
     * badge, this text always comes from the menu's synchronized state and
     * explains which page to consult without performing an operation.
     * Kept inside Overview so the persistent status rail stays compact.
     */
    public static UIElement operatorGuide(String heading, Supplier<String> nextAction, String readingKey) {
        var panel = new UIElement().addClass("panel_bg");
        panel.layout(l -> l.widthPercent(100).paddingAll(8).gapAll(5));
        panel.addChildren(
                wrappedLabel(heading).layout(l -> l.widthPercent(100)),
                liveText(nextAction).layout(l -> l.widthPercent(100)),
                wrappedLabel("READING KEY • " + readingKey).layout(l -> l.widthPercent(100))
        );
        return panel;
    }

    /**
     * Uniform, read-only device identity and evidence for every block-facing
     * LDLib2 HMI, including dedicated instruments and transport systems.
     *
     * This consumes the common server-synchronized EngineeringDeviceMenu
     * snapshot. It never guesses a measurement, implies editable controls,
     * or treats the client's initial health slot as NOMINAL.
     */
    public static UIElement deviceContext(EngineeringDeviceMenu menu) {
        var header = new UIElement().addClass("panel_bg");
        header.layout(l -> l.widthPercent(100).paddingAll(3).gapAll(2));

        // One compact identity line stays visible; all secondary information is
        // retained in the collapsed details panel so the workspace can grow.
        var identity = new UIElement().layout(l -> l.widthPercent(100)
                .flexDirection(YogaFlexDirection.ROW).flexWrap(FlexWrap.WRAP).gapAll(4));
        identity.addChild(
                compactLiveText(() -> "DEVICE • " + menu.deviceDisplayName()
                        + "   |   POS • " + menu.blockPos().toShortString()
                        + "   |   HEALTH • " + menu.operationalHealthLabel())
                        .layout(l -> l.flex(1).minWidth(210))
        );

        var details = new UIElement().addClass("panel_bg");
        details.layout(l -> l.widthPercent(100).paddingAll(4).gapAll(2));
        details.addChildren(
                compactLiveText(() -> "ROLE • "
                        + (menu.operationalHealth() == EngineeringDeviceMenu.HEALTH_UNVERIFIED
                                ? "NOT READY" : menu.topologyRoleLabel()))
                        .layout(l -> l.widthPercent(100)),
                compactLiveText(() -> "EVIDENCE • "
                        + (menu.operationalHealth() == EngineeringDeviceMenu.HEALTH_UNVERIFIED
                                ? "NOT READY" : menu.evidenceStateLabel()))
                        .layout(l -> l.widthPercent(100)),
                compactLiveText(() -> "I/O • " + menu.portRouteLabel())
                        .layout(l -> l.widthPercent(100)),
                compactLiveText(() -> "PORT CONTRACT • " + menu.portRoleHintLabel())
                        .layout(l -> l.widthPercent(100)),
                compactLiveText(() -> "QUALITY MEANING • " + menu.evidenceMeaningLabel())
                        .layout(l -> l.widthPercent(100)),
                compactLiveText(() -> "NEXT CHECK • " + menu.operatorCheckLabel())
                        .layout(l -> l.widthPercent(100)),
                compactWrappedLabel("READING KEY • declared faces are capabilities, not detected "
                        + "wires; health is independent of port evidence. Detailed measurements "
                        + "and controls remain on their original pages.")
                        .layout(l -> l.widthPercent(100))
        );
        details.setDisplay(false);

        // Local disclosure only; opening it never sends a menu action.
        var detailToggle = new Button().setText("DEVICE DETAILS ▾");
        final boolean[] expanded = {false};
        detailToggle.setOnClick(event -> {
            expanded[0] = !expanded[0];
            details.setDisplay(expanded[0]);
            detailToggle.setText(expanded[0] ? "HIDE DETAILS ▲" : "DEVICE DETAILS ▾");
        });
        detailToggle.layout(l -> l.height(16).minWidth(94).paddingAll(2));
        identity.addChild(detailToggle);

        header.addChildren(identity, details);
        return header;
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
        root.layout(l -> l.widthPercent(100).flex(1).minHeight(150).paddingAll(4).gapAll(3));
        var tabs = new UIElement().addClass("panel_bg");
        tabs.layout(l -> l.flexDirection(YogaFlexDirection.ROW).flexWrap(FlexWrap.WRAP).gapAll(3).paddingAll(3));

        var scroller = new ScrollerView().scrollerStyle(style -> style
                .mode(ScrollerMode.BOTH)
                .verticalScrollDisplay(ScrollDisplay.AUTO)
                .horizontalScrollDisplay(ScrollDisplay.AUTO)
                .minScrollPixel(8).maxScrollPixel(72));
        // Preserve the first-round two-axis ScrollerView while reserving a real
        // display area below the compact fixed header and tab strip.
        scroller.layout(l -> l.flex(1).minHeight(150));
        scroller.viewPort(view -> view.layout(l -> l.paddingAll(4)));
        // Prefer wrapping to the visible viewport instead of forcing every
        // 620px screen to pan sideways through an 850px wide page.
        // Only very narrow screens fall back to horizontal scrolling.
        // Use wrapped rows on narrow 320px layouts; horizontal scrolling
        // remains a fallback when the actual viewport is smaller.
        final int readableMinWidth = Math.min(contentWidth, 320);
        scroller.viewContainer(view -> view.layout(l ->
                l.widthPercent(100).minWidth(readableMinWidth).paddingAll(4).gapAll(6)));

        // A persistent page breadcrumb makes navigation legible even when
        // long labels wrap or the active page is vertically scrolled.
        var pageIndicator = compactWrappedLabel("PAGE 1 / " + labels.length + " • " + labels[0]
                + "   |   WHEEL ↓  •  SHIFT+WHEEL →");
        pageIndicator.layout(l -> l.widthPercent(100));
        var tabButtons = new java.util.ArrayList<Button>(labels.length);
        for (int i = 0; i < pages.length; i++) {
            var page = pages[i];
            page.layout(l -> l.widthPercent(100).paddingAll(6).gapAll(6));
            page.setDisplay(i == 0);
            scroller.addScrollViewChild(page);
            final int selectedIndex = i;
            var button = new Button().setText(i == 0 ? "▶ " + labels[i] : labels[i]).setOnClick(event -> {
                for (UIElement candidate : pages) candidate.setDisplay(candidate == page);
                for (int j = 0; j < tabButtons.size(); j++) {
                    tabButtons.get(j).setText(j == selectedIndex ? "▶ " + labels[j] : labels[j]);
                }
                pageIndicator.setText("PAGE " + (selectedIndex + 1) + " / " + labels.length + " • " + labels[selectedIndex]
                        + "   |   WHEEL ↓  •  SHIFT+WHEEL →");
                scroller.horizontalScroller.setNormalizedValue(0);
                scroller.verticalScroller.setNormalizedValue(0);
            });
            button.layout(l -> l.height(18).minWidth(72).paddingAll(3));
            tabButtons.add(button);
            tabs.addChild(button);
        }

        root.addChildren(tabs, pageIndicator, scroller);
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
                .flexWrap(FlexWrap.WRAP).gapAll(3).paddingAll(3));
        var pageIndicator = compactWrappedLabel("PAGE 1 / " + labels.length + " • " + labels[0]
                + "   |   WHEEL ↓  •  SHIFT+WHEEL →");
        pageIndicator.layout(l -> l.widthPercent(100));
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
                        pageIndicator.setText("PAGE " + (selected + 1) + " / " + labels.length + " • " + labels[selected]
                                + "   |   WHEEL ↓  •  SHIFT+WHEEL →");
                        scroller.horizontalScroller.setNormalizedValue(0);
                        scroller.verticalScroller.setNormalizedValue(0);
                    });
            button.layout(l -> l.height(18).minWidth(72).paddingAll(3));
            buttons.add(button);
            tabs.addChild(button);
        }
        var navigation = new UIElement();
        navigation.layout(l -> l.widthPercent(100).flexDirection(YogaFlexDirection.COLUMN).gapAll(2));
        navigation.addChildren(tabs, pageIndicator);
        return navigation;
    }

    public static UIElement workspacePage(UIElement... contents) {
        return new UIElement().addChildren(contents);
    }

    public static Label authorityFooter() {
        return title("SERVER AUTHORITY • UI is presentation + validated operator intent");
    }
}
