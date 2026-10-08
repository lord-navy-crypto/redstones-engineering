package dev.redstoneengineering.ui.ldlib;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.redstoneengineering.ui.menu.OpticalSystemMenu;
import net.minecraft.world.entity.player.Player;
import org.appliedenergistics.yoga.YogaFlexDirection;

public final class OpticalSystemLdUi {
    private OpticalSystemLdUi() {}

    public static ModularUI create(OpticalSystemMenu m, Player player) {
        var root = new UIElement().addClass("panel_bg");
        root.layout(l -> l.width(680).height(440).paddingAll(8).gapAll(6));
        root.addChildren(
                RseLdUiComponents.title("OPTICAL ENGINEERING HMI"),
                RseLdUiComponents.tabbedWorkspace(
                        660, 400, 910,
                        new String[]{"Overview", "Details", "Evidence"},
                        new UIElement[]{
                                RseLdUiComponents.workspacePage(
                                        RseLdUiComponents.formulaCard(() -> opticalEquation(m)),
                                        statePanel(m)
                                ),
                                RseLdUiComponents.workspacePage(
                                        parameterPanel(m),
                                        routePanel(m)
                                ),
                                RseLdUiComponents.workspacePage(
                                        commissioningPanel(m),
                                        RseLdUiComponents.authorityFooter()
                                )
                        }
                )
        );
        return ModularUI.of(UI.of(root, StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.GDP)), player);
    }

    private static UIElement statePanel(OpticalSystemMenu m) {
        var p = new UIElement().addClass("panel_bg"); p.layout(l -> l.paddingAll(5).gapAll(3));
        p.addChildren(
                new Label().setText("PIONEER PATTERN • OPTICAL MODEL"),
                RseLdUiComponents.liveRow("DEVICE","type",()->name(m.kind())),
                RseLdUiComponents.liveRow("MEASURED","I / state",()->m.primary()+" / 15"),
                RseLdUiComponents.liveRow("MEASURED","channel",()->Integer.toString(m.secondary())),
                RseLdUiComponents.liveRow("EVIDENCE","quality",()->m.quality().name()),
                RseLdUiComponents.liveRow("DERIVED","aux",()->m.tertiary()+" / "+m.auxiliary()),
                new Label().setText("Optical controls remain server-authoritative; the client never performs a second optical propagation solve.")
        );
        return p;
    }

    private static UIElement parameterPanel(OpticalSystemMenu m) {
        var primary = new TextField().setNumbersOnlyInt(0,15); primary.layout(l->l.width(100));
        primary.bind(DataBindingBuilder.string(
                () -> primaryAdjustable(m.kind()) ? Integer.toString(primaryValue(m)) : "",
                v -> { try { m.applyPrimaryFromUi(Integer.parseInt(v)); } catch (NumberFormatException ignored) {} }
        ).build());

        var secondary = new TextField().setNumbersOnlyInt(0,15); secondary.layout(l->l.width(100));
        secondary.bind(DataBindingBuilder.string(
                () -> secondaryAdjustable(m.kind()) ? Integer.toString(m.secondary()) : "",
                v -> { try { m.applySecondaryFromUi(Integer.parseInt(v)); } catch (NumberFormatException ignored) {} }
        ).build());

        var p = new UIElement().addClass("panel_bg"); p.layout(l->l.paddingAll(5).gapAll(4));
        p.addChildren(
                RseLdUiComponents.liveRow(primaryAdjustable(m.kind())?"ADJUSTABLE":"FIXED",primarySymbol(m.kind()),()->primaryControl(m)),
                new UIElement().layout(l->l.flexDirection(YogaFlexDirection.ROW).gapAll(6)).addChildren(
                        new Label().setText("PRIMARY").layout(l->l.width(82)), primary,
                        new Label().setText(primaryRange(m.kind())).layout(l->l.flex(1))
                ),
                RseLdUiComponents.liveRow(secondaryAdjustable(m.kind())?"ADJUSTABLE":"FIXED","channel",()->secondaryControl(m)),
                new UIElement().layout(l->l.flexDirection(YogaFlexDirection.ROW).gapAll(6)).addChildren(
                        new Label().setText("SECONDARY").layout(l->l.width(82)), secondary,
                        new Label().setText(secondaryRange(m.kind())).layout(l->l.flex(1))
                )
        );
        return p;
    }

    private static UIElement routePanel(OpticalSystemMenu m) {
        var p=new UIElement().addClass("panel_bg"); p.layout(l->l.paddingAll(5).gapAll(4));
        p.addChildren(
                new Label().setText("PHYSICAL ROUTE • SERVER OWNED"),
                RseLdUiComponents.liveRow("ROUTE","facing",()->m.facing().getName().toUpperCase()),
                new UIElement().layout(l->l.flexDirection(YogaFlexDirection.ROW).gapAll(6)).addChildren(
                        RseLdUiComponents.serverAction("Cycle direction ▶",m::cycleWholeRouteForward),
                        RseLdUiComponents.serverAction("Cycle RX ▶",m::cycleInputForward),
                        RseLdUiComponents.serverAction("Cycle TX ▶",m::cycleOutputForward)
                ),
                new Label().setText("Direction and physical interface orientation are controlled only on Route.")
        );
        return p;
    }

    private static UIElement commissioningPanel(OpticalSystemMenu m) {
        var p=new UIElement().addClass("panel_bg"); p.layout(l->l.paddingAll(5).gapAll(3));
        p.addChildren(
                new Label().setText("Segment TX / RX • guided optical commissioning budget"),
                RseLdUiComponents.liveRow("COMMISSIONING","status",()->m.commissioningStatus().name()),
                RseLdUiComponents.liveRow("SEGMENT","TX / RX",()->m.budgetSourceIntensity()+"/15 → "+m.primary()+"/15"),
                RseLdUiComponents.liveRow("DERIVED","Observed segment loss",()->Integer.toString(m.budgetObservedLoss())),
                RseLdUiComponents.liveRow("DERIVED","Receiver headroom",()->m.budgetReceiverHeadroom()+" above I=1"),
                RseLdUiComponents.liveRow("TOPOLOGY","Passive nodes / hops",()->m.budgetPassiveNodes()+" / "+m.budgetPassiveHops()),
                RseLdUiComponents.liveRow("TOPOLOGY","source / channel",()->m.budgetSourceCount()+" / "+m.budgetSourceChannel()),
                RseLdUiComponents.liveRow("METER","connected / same CH",()->m.meterConnectedNeighbors()+" / "+m.meterSameChannelNeighbors()),
                RseLdUiComponents.liveRow("METER","mismatches",()->Integer.toString(m.meterChannelMismatches())),
                new Label().setText("Intensity-unit segment budget only; upstream splitter/attenuator loss is not double-counted."),
                new Label().setText("Observer-only commissioning evidence does not mutate or re-solve the optical network.")
        );
        return p;
    }

    private static String opticalEquation(OpticalSystemMenu m) {
        return switch(m.kind()) {
            case OpticalSystemMenu.KIND_ATTENUATOR -> "I_out = max(0, I_in - L)";
            case OpticalSystemMenu.KIND_SPLITTER -> "I_A = floor(I_in/2), I_B = floor(I_in/2), q = I_in - I_A - I_B";
            case OpticalSystemMenu.KIND_FILTER -> "I_out = (CH_in = CH_target) ? I_in : 0";
            case OpticalSystemMenu.KIND_RECEIVER -> "L_obs = I_TX - I_RX ; H_rx = I_RX - 1";
            case OpticalSystemMenu.KIND_EMITTER -> "I_out = I_set on selected channel";
            default -> "I_obs = authoritative optical path evidence";
        };
    }

    private static boolean primaryAdjustable(int k){return k==OpticalSystemMenu.KIND_EMITTER||k==OpticalSystemMenu.KIND_FILTER||k==OpticalSystemMenu.KIND_ATTENUATOR;}
    private static boolean secondaryAdjustable(int k){return k==OpticalSystemMenu.KIND_EMITTER||k==OpticalSystemMenu.KIND_FREE_SPACE_TX||k==OpticalSystemMenu.KIND_FREE_SPACE_RX;}
    private static int primaryValue(OpticalSystemMenu m){return m.kind()==OpticalSystemMenu.KIND_EMITTER?m.primary():m.secondary();}
    private static String primarySymbol(int k){return switch(k){case OpticalSystemMenu.KIND_EMITTER->"I_set";case OpticalSystemMenu.KIND_FILTER->"CH_target";case OpticalSystemMenu.KIND_ATTENUATOR->"L";default->"value";};}
    private static String primaryRange(int k){return k==OpticalSystemMenu.KIND_ATTENUATOR?"0..8":"0..15";}
    private static String secondaryRange(int k){return (k==OpticalSystemMenu.KIND_FREE_SPACE_TX||k==OpticalSystemMenu.KIND_FREE_SPACE_RX)?"0..3":"0..15";}
    private static String primaryControl(OpticalSystemMenu m){return primaryAdjustable(m.kind())?primaryValue(m)+" • exact server-backed value":"READ ONLY";}
    private static String secondaryControl(OpticalSystemMenu m){return secondaryAdjustable(m.kind())?m.secondary()+" • exact server-backed value":"NONE / READ ONLY";}
    private static String name(int k){return switch(k){case OpticalSystemMenu.KIND_EMITTER->"OPTICAL EMITTER";case OpticalSystemMenu.KIND_RECEIVER->"OPTICAL RECEIVER";case OpticalSystemMenu.KIND_METER->"OPTICAL POWER METER";case OpticalSystemMenu.KIND_SPLITTER->"OPTICAL 1×2 SPLITTER";case OpticalSystemMenu.KIND_FILTER->"OPTICAL CHANNEL FILTER";case OpticalSystemMenu.KIND_ATTENUATOR->"OPTICAL ATTENUATOR";case OpticalSystemMenu.KIND_FREE_SPACE_TX->"FREE-SPACE OPTICAL TX";case OpticalSystemMenu.KIND_FREE_SPACE_RX->"FREE-SPACE OPTICAL RX";default->"OPTICAL DEVICE";};}
}
