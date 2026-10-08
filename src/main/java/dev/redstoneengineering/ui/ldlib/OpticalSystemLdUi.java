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
        root.layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(8).gapAll(6));
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
        return RseLdUiComponents.responsiveUi(root, player, 680, 440);
    }

    private static UIElement statePanel(OpticalSystemMenu m) {
        var p = new UIElement().addClass("panel_bg"); p.layout(l -> l.paddingAll(5).gapAll(3));
        p.addChildren(
                new Label().setText("PIONEER PATTERN • OPTICAL MODEL"),
                RseLdUiComponents.liveRow("DEVICE","type",()->name(m.kind())),
                RseLdUiComponents.liveRow(m.kind()==OpticalSystemMenu.KIND_EMITTER?"ADJUSTABLE":"MEASURED", primaryMetric(m.kind()),()->m.primary()+" / 15"),
                RseLdUiComponents.liveRow(secondaryIsConfiguration(m.kind())?"ADJUSTABLE":"MEASURED", secondaryMetric(m.kind()),()->Integer.toString(m.secondary())),
                RseLdUiComponents.liveRow("EVIDENCE","input / source quality",()->m.quality().name()),
                RseLdUiComponents.liveRow("DERIVED",tertiaryMetric(m.kind()),()->m.tertiary()+" / "+m.auxiliary()),
                new Label().setText("Optical controls remain server-authoritative; the client never performs a second optical propagation solve.")
        );
        return p;
    }

    private static UIElement parameterPanel(OpticalSystemMenu m) {
        int kind = m.kind();
        var p = new UIElement().addClass("panel_bg");
        p.layout(l->l.paddingAll(5).gapAll(4));
        if (primaryAdjustable(kind)) {
            // The optical attenuator supports only 0..8, not the full 0..15.
            int limit = kind == OpticalSystemMenu.KIND_ATTENUATOR ? 8 : 15;
            var primary = new TextField().setNumbersOnlyInt(0,limit);
            primary.layout(l->l.width(100));
            primary.bind(DataBindingBuilder.string(
                    () -> Integer.toString(primaryValue(m)),
                    v -> { try { m.applyPrimaryFromUi(Integer.parseInt(v)); }
                           catch (NumberFormatException ignored) {} }
            ).build());
            p.addChildren(
                    RseLdUiComponents.liveRow("ADJUSTABLE",primarySymbol(kind),()->primaryControl(m)),
                    new UIElement().layout(l->l.flexDirection(YogaFlexDirection.ROW).gapAll(6)).addChildren(
                            new Label().setText("PRIMARY").layout(l->l.width(82)), primary,
                            new Label().setText(primaryRange(kind)).layout(l->l.flex(1)))
            );
        } else {
            p.addChild(RseLdUiComponents.fixedRow("PRIMARY",
                    () -> "READ ONLY", "No editable intensity, target-channel or attenuation control"));
        }
        if (secondaryAdjustable(kind)) {
            // Free-space channel modes are 0..3; invalid UI intents must not
            // be presented as if the server could accept them.
            int limit = (kind==OpticalSystemMenu.KIND_FREE_SPACE_TX
                    || kind==OpticalSystemMenu.KIND_FREE_SPACE_RX) ? 3 : 15;
            var secondary = new TextField().setNumbersOnlyInt(0,limit);
            secondary.layout(l->l.width(100));
            secondary.bind(DataBindingBuilder.string(
                    () -> Integer.toString(m.secondary()),
                    v -> { try { m.applySecondaryFromUi(Integer.parseInt(v)); }
                           catch (NumberFormatException ignored) {} }
            ).build());
            p.addChildren(
                    RseLdUiComponents.liveRow("ADJUSTABLE","channel",()->secondaryControl(m)),
                    new UIElement().layout(l->l.flexDirection(YogaFlexDirection.ROW).gapAll(6)).addChildren(
                            new Label().setText("SECONDARY").layout(l->l.width(82)), secondary,
                            new Label().setText(secondaryRange(kind)).layout(l->l.flex(1)))
            );
        } else {
            p.addChild(RseLdUiComponents.fixedRow("SECONDARY",
                    () -> "READ ONLY", "Observed channel, branch intensity or loss is not an extra knob"));
        }
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

    /** Expose the actual physical meaning of the server-synchronized slots. */
    private static String primaryMetric(int kind) {
        return switch (kind) {
            case OpticalSystemMenu.KIND_EMITTER -> "I_set";
            case OpticalSystemMenu.KIND_SPLITTER, OpticalSystemMenu.KIND_FILTER,
                 OpticalSystemMenu.KIND_ATTENUATOR -> "I_in";
            case OpticalSystemMenu.KIND_FREE_SPACE_TX -> "input intensity";
            case OpticalSystemMenu.KIND_FREE_SPACE_RX -> "received input";
            default -> "I_observed";
        };
    }

    private static String secondaryMetric(int kind) {
        return switch (kind) {
            case OpticalSystemMenu.KIND_SPLITTER -> "I_branch_A";
            case OpticalSystemMenu.KIND_FILTER -> "CH_target";
            case OpticalSystemMenu.KIND_ATTENUATOR -> "loss L";
            case OpticalSystemMenu.KIND_EMITTER, OpticalSystemMenu.KIND_FREE_SPACE_TX,
                 OpticalSystemMenu.KIND_FREE_SPACE_RX -> "CH_set";
            default -> "CH_observed";
        };
    }

    private static String tertiaryMetric(int kind) {
        return switch (kind) {
            case OpticalSystemMenu.KIND_SPLITTER -> "I_branch_B / quantization loss";
            case OpticalSystemMenu.KIND_FILTER -> "I_out / input CH";
            case OpticalSystemMenu.KIND_ATTENUATOR -> "I_out / channel";
            case OpticalSystemMenu.KIND_RECEIVER -> "inputs / drivers";
            default -> "auxiliary evidence";
        };
    }

    private static boolean secondaryIsConfiguration(int kind) {
        return secondaryAdjustable(kind) || kind == OpticalSystemMenu.KIND_FILTER
                || kind == OpticalSystemMenu.KIND_ATTENUATOR;
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
