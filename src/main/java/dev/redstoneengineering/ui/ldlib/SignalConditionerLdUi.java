package dev.redstoneengineering.ui.ldlib;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.redstoneengineering.ui.menu.SignalConditionerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import dev.vfyjxf.taffy.style.FlexWrap;
import org.appliedenergistics.yoga.YogaFlexDirection;

/**
 * First production LDLib2 engineering HMI.
 *
 * <p>Presentation/layout lives here. Every mutation delegates to the existing
 * server-authoritative SignalConditionerMenu; no transfer physics is duplicated
 * on the client.</p>
 */
public final class SignalConditionerLdUi {
    private SignalConditionerLdUi() {}

    public static ModularUI create(SignalConditionerMenu menu, Player player) {
        var root = new UIElement()
                .addClass("panel_bg")
                .layout(l -> l.width(500).height(340).paddingAll(10).gapAll(8));

        var overview = page(
                new Label().setText("FORMULA-FIRST SERVER CONTROL"),
                RseLdUiComponents.formulaCard(() -> governingEquation(menu.mode())),
                RseLdUiComponents.liveRow("MEASURED", "x", () -> menu.input() + " / 15"),
                RseLdUiComponents.liveRow("STATE", "mode", () -> modeName(menu.mode())),
                RseLdUiComponents.liveRow("DERIVED", "y", () -> menu.output() + " / 15"),
                RseLdUiComponents.liveRow("EVIDENCE", "boundary", () -> menu.limiting() ? "SATURATED" : "IN RANGE")
        );
        var configure = page(
                parameterControl(menu),
                routeRow(menu)
        );
        var authority = page(RseLdUiComponents.authorityFooter());
        configure.setDisplay(false);
        authority.setDisplay(false);

        var workspace = new ScrollerView()
                .scrollerStyle(style -> style
                        .mode(ScrollerMode.BOTH)
                        .verticalScrollDisplay(ScrollDisplay.AUTO)
                        .horizontalScrollDisplay(ScrollDisplay.AUTO)
                        .minScrollPixel(8)
                        .maxScrollPixel(64));
        workspace.layout(l -> l.flex(1));
        workspace.viewPort(view -> view.layout(l -> l.paddingAll(8)));
        workspace.viewContainer(view -> view.layout(l -> l.width(640).paddingAll(8).gapAll(8)));
        workspace.addScrollViewChildren(overview, configure, authority);

        var tabs = new UIElement().addClass("panel_bg")
                .layout(l -> l.flexDirection(YogaFlexDirection.ROW).flexWrap(FlexWrap.WRAP).gapAll(6).paddingAll(5))
                .addChildren(
                        tabButton("Overview", workspace, overview, overview, configure, authority),
                        tabButton("Configure", workspace, configure, overview, configure, authority),
                        tabButton("Authority", workspace, authority, overview, configure, authority)
                );

        root.addChildren(
                RseLdUiComponents.title("SERIES SIGNAL CONDITIONER"),
                tabs,
                new Label().setText("SCROLL • wheel Y • Shift+wheel X"),
                workspace
        );

        return RseLdUiComponents.responsiveUi(root, player, 500, 340);
    }

    private static UIElement page(UIElement... children) {
        return new UIElement()
                .layout(l -> l.width(610).paddingAll(12).gapAll(10))
                .addChildren(children);
    }

    private static Button tabButton(
            String label,
            ScrollerView workspace,
            UIElement selected,
            UIElement... pages
    ) {
        return new Button().setText(label).setOnClick(event -> {
            for (UIElement page : pages) page.setDisplay(page == selected);
            workspace.horizontalScroller.setNormalizedValue(0);
            workspace.verticalScroller.setNormalizedValue(0);
        });
    }

    private static UIElement parameterControl(SignalConditionerMenu menu) {
        var parameter = new TextField().setNumbersOnlyInt(-5, 15);
        parameter.layout(l -> l.flex(1));

        parameter.bind(DataBindingBuilder.string(
                () -> Integer.toString(visibleFormulaParameter(menu.mode(), menu.parameter())),
                value -> {
                    try {
                        int parsed = Integer.parseInt(value);
                        if (validParameter(menu.mode(), parsed)) {
                            menu.applyVisibleFormulaParameter(parsed);
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
        ).build());

        return new UIElement()
                .layout(l -> l.flexDirection(YogaFlexDirection.ROW).gapAll(6))
                .addChildren(
                        new Label().bind(DataBindingBuilder.componentS2C(() ->
                                Component.literal(parameterSymbol(menu.mode()) + "  " + parameterRange(menu.mode()))
                        ).build()).layout(l -> l.width(120)),
                        parameter,
                        new Button()
                                .setText("Cycle mode ▶")
                                .setOnServerClick(event -> menu.cycleModeForward())
                                .layout(l -> l.width(100))
                );
    }

    private static UIElement routeRow(SignalConditionerMenu menu) {
        return new UIElement()
                .layout(l -> l.flexDirection(YogaFlexDirection.ROW).gapAll(6))
                .addChildren(
                        new Button()
                                .setText("Cycle RX ▶")
                                .setOnServerClick(event -> menu.cycleInputForward())
                                .layout(l -> l.flex(1)),
                        new Button()
                                .setText("Cycle TX ▶")
                                .setOnServerClick(event -> menu.cycleOutputForward())
                                .layout(l -> l.flex(1))
                );
    }

    private static int visibleFormulaParameter(int mode, int raw) {
        return mode == 1 ? Math.min(10, raw) - 5 : raw;
    }

    private static boolean validParameter(int mode, int value) {
        return switch (mode) {
            case 0, 4 -> value >= 1 && value <= 4;
            case 1 -> value >= -5 && value <= 5;
            case 2, 3 -> value >= 1 && value <= 15;
            default -> false;
        };
    }

    private static String modeName(int mode) {
        return switch (mode) {
            case 0 -> "GAIN";
            case 1 -> "OFFSET";
            case 2 -> "CLAMP";
            case 3 -> "THRESHOLD";
            case 4 -> "DEADBAND";
            default -> "UNKNOWN";
        };
    }

    private static String parameterSymbol(int mode) {
        return switch (mode) {
            case 0 -> "g";
            case 1 -> "b";
            case 2 -> "c";
            case 3 -> "T";
            case 4 -> "B";
            default -> "p";
        };
    }

    private static String parameterRange(int mode) {
        return switch (mode) {
            case 0 -> "×1..×4";
            case 1 -> "−5..+5";
            case 2, 3 -> "1..15";
            case 4 -> "1..4";
            default -> "—";
        };
    }

    private static String governingEquation(int mode) {
        return switch (mode) {
            case 0 -> "y = clamp₀..₁₅(g · x)";
            case 1 -> "y = clamp₀..₁₅(x + b)";
            case 2 -> "y = min(x, c)";
            case 3 -> "y = (x ≥ T) ? x : 0";
            case 4 -> "y = (|x - y_prev| ≥ B) ? x : y_prev";
            default -> "y = x";
        };
    }
}
