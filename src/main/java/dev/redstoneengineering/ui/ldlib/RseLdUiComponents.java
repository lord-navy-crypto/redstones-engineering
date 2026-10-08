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

    public static Label title(String text) {
        var label = new Label();
        label.setText(text);
        return label;
    }

    public static UIElement formulaCard(Supplier<String> equation) {
        return new UIElement()
                .addClass("panel_bg")
                .layout(l -> l.paddingAll(5))
                .addChild(new Label().bind(DataBindingBuilder.componentS2C(() ->
                        Component.literal(equation.get())
                ).build()));
    }

    public static UIElement liveRow(String role, String symbol, Supplier<String> value) {
        return new UIElement()
                .layout(l -> l.flexDirection(YogaFlexDirection.ROW).gapAll(6))
                .addChildren(
                        new Label().setText(role).layout(l -> l.width(82)),
                        new Label().setText(symbol).layout(l -> l.width(62)),
                        new Label().bind(DataBindingBuilder.componentS2C(() ->
                                Component.literal(value.get())
                        ).build()).layout(l -> l.flex(1))
                );
    }

    public static UIElement fixedRow(String symbol, Supplier<String> value, String reason) {
        return new UIElement()
                .layout(l -> l.flexDirection(YogaFlexDirection.ROW).gapAll(6))
                .addChildren(
                        new Label().setText("FIXED").layout(l -> l.width(82)),
                        new Label().setText(symbol).layout(l -> l.width(62)),
                        new Label().bind(DataBindingBuilder.componentS2C(() ->
                                Component.literal(value.get() + " • " + reason)
                        ).build()).layout(l -> l.flex(1))
                );
    }

    public static Button serverAction(String label, Runnable action) {
        return new Button()
                .setText(label)
                .setOnServerClick(event -> action.run());
    }

    /**
     * Shared viewport for all RSE block-facing HMI families. Pages remain in one
     * UI tree, but only the selected page participates in layout.
     * Content dimensions deliberately exceed the visible frame: this avoids
     * compressing long equations, trace data, or server-backed controls.
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
        scroller.viewContainer(view -> view.layout(l -> l.width(contentWidth).paddingAll(8).gapAll(8)));

        for (int i = 0; i < pages.length; i++) {
            var page = pages[i];
            page.layout(l -> l.width(contentWidth - 30).paddingAll(12).gapAll(10));
            page.setDisplay(i == 0);
            scroller.addScrollViewChild(page);
            var button = new Button().setText(labels[i]).setOnClick(event -> {
                for (UIElement candidate : pages) candidate.setDisplay(candidate == page);
                scroller.horizontalScroller.setNormalizedValue(0);
                scroller.verticalScroller.setNormalizedValue(0);
            });
            button.layout(l -> l.height(20).minWidth(82).paddingAll(4));
            tabs.addChild(button);
        }

        root.addChildren(tabs,
                new Label().setText("SCROLL • wheel Y • Shift+wheel X"),
                scroller);
        return root;
    }

    public static UIElement workspacePage(UIElement... contents) {
        return new UIElement().addChildren(contents);
    }

    public static Label authorityFooter() {
        var label = new Label();
        label.setText("SERVER AUTHORITY • UI is presentation + validated operator intent");
        return label;
    }
}
