package dev.redstoneengineering.ui.ldlib;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.ui.menu.PneumaticSystemMenu;
import net.minecraft.world.entity.player.Player;
import org.appliedenergistics.yoga.YogaFlexDirection;

public final class PneumaticSystemLdUi {
    private PneumaticSystemLdUi() {}

    public static ModularUI create(PneumaticSystemMenu m, Player player) {
        var root=new UIElement().addClass("panel_bg");
        root.layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(8).gapAll(6));
        root.addChildren(
                RseLdUiComponents.title(deviceName(m)),
                RseLdUiComponents.tabbedWorkspace(
                        600, 400, 850,
                        new String[]{"Overview", "Details", "Evidence"},
                        new UIElement[]{
                                RseLdUiComponents.workspacePage(
                                        RseLdUiComponents.title("PIONEER PATTERN • PNEUMATIC MODEL"),
                                        RseLdUiComponents.formulaCard(()->equation(m)),
                                        overview(m)
                                ),
                                RseLdUiComponents.workspacePage(
                                        controls(m),
                                        pneumaticMechanism(m)
                                ),
                                RseLdUiComponents.workspacePage(
                                        diagnostics(m),
                                        RseLdUiComponents.authorityFooter()
                                )
                        }
                )
        );
        return RseLdUiComponents.responsiveUi(root, player, 620, 440);
    }

    private static UIElement overview(PneumaticSystemMenu m){
        var p=new UIElement().addClass("panel_bg"); p.layout(l->l.paddingAll(5).gapAll(3));
        p.addChildren(
                RseLdUiComponents.liveRow("MEASURED",primaryMetric(m.kind()),()->primaryReadout(m)),
                RseLdUiComponents.liveRow(secondaryRole(m.kind()),secondaryMetric(m.kind()),()->secondarySnapshot(m)),
                RseLdUiComponents.liveRow("EVIDENCE","quality",()->m.inputQuality().name()+" → "+m.outputQuality().name())
        );
        if(m.kind()==PneumaticSystemMenu.KIND_FLOW_METER){
            p.addChildren(
                    RseLdUiComponents.liveRow("COMMISSIONING","status",()->m.commissioningStatus().name()),
                    RseLdUiComponents.liveRow("WITNESS","up/down",()->witnessReadout(m))
            );
        }
        if(m.kind()==PneumaticSystemMenu.KIND_CYLINDER){
            p.addChildren(
                    RseLdUiComponents.liveRow("ACTUATOR","position/target",()->cylinderReady(m)?m.secondary()+" / "+m.tertiary():"NOT READY • actuator response unverified"),
                    RseLdUiComponents.liveRow("PATH","Supply / cylinder P",()->cylinderReady(m)?m.cylinderSupply()+" / "+m.primary():"NOT READY • actuator path unverified"),
                    RseLdUiComponents.liveRow("PATH","Path loss",()->cylinderReady(m)?m.cylinderObservedLoss()+" = line "+m.cylinderLineLoss()+" + restriction "+m.cylinderRestrictionLoss():"NOT READY • no verified path"),
                    RseLdUiComponents.liveRow("PATH","winning path edges",()->Integer.toString(m.cylinderPathEdges())),
                    RseLdUiComponents.liveRow("RESPONSE","Response / remaining",()->m.cylinderResponsePeriod()+"t / "+m.cylinderRemainingTicks()+"t"),
                    RseLdUiComponents.liveRow("RESPONSE","velocity/error",()->cylinderReady(m)?m.cylinderVelocity()+" / "+m.cylinderError():"NOT READY • no valid actuator response"),
                    RseLdUiComponents.liveRow("HISTORY","stall/reversal/samples",()->m.cylinderStallTicks()+" / "+m.cylinderReversals()+" / "+m.cylinderSamples())
            );
        }
        return p;
    }

    /** Slot interpretation is device-specific; no new network solver runs in UI. */
    private static String primaryMetric(int kind) {
        return switch (kind) {
            case PneumaticSystemMenu.KIND_COMPRESSOR -> "command u_R";
            case PneumaticSystemMenu.KIND_PIPE, PneumaticSystemMenu.KIND_REGULATOR -> "P_network";
            case PneumaticSystemMenu.KIND_RESERVOIR -> "P_stored";
            case PneumaticSystemMenu.KIND_FLOW_METER -> "flow proxy";
            case PneumaticSystemMenu.KIND_CYLINDER -> "P_cylinder";
            default -> "P_in";
        };
    }

    private static String secondaryRole(int kind) {
        return switch (kind) {
            case PneumaticSystemMenu.KIND_COMPRESSOR,
                 PneumaticSystemMenu.KIND_RECEIVER -> "DERIVED";
            case PneumaticSystemMenu.KIND_REGULATOR -> "ADJUSTABLE";
            case PneumaticSystemMenu.KIND_PIPE -> "N/A";
            default -> "MEASURED";
        };
    }

    private static String secondaryMetric(int kind) {
        return switch (kind) {
            case PneumaticSystemMenu.KIND_COMPRESSOR -> "P_commanded";
            case PneumaticSystemMenu.KIND_RESERVOIR -> "P_network";
            case PneumaticSystemMenu.KIND_REGULATOR -> "P_set";
            case PneumaticSystemMenu.KIND_RECEIVER -> "Redstone output";
            case PneumaticSystemMenu.KIND_FLOW_METER -> "pressure drop";
            case PneumaticSystemMenu.KIND_CYLINDER -> "position";
            case PneumaticSystemMenu.KIND_PIPE -> "no second scalar";
            default -> "P_out";
        };
    }

    private static String secondarySnapshot(PneumaticSystemMenu m) {
        if (m.kind() == PneumaticSystemMenu.KIND_PIPE)
            return "N/A • node pressure is shown above";
        if (m.kind() == PneumaticSystemMenu.KIND_COMPRESSOR || m.kind() == PneumaticSystemMenu.KIND_REGULATOR)
            return Integer.toString(m.secondary()) + " • commanded/configured";
        if (m.kind() == PneumaticSystemMenu.KIND_CYLINDER && !cylinderReady(m))
            return "NOT READY • no valid actuator response";
        if (m.kind() == PneumaticSystemMenu.KIND_FLOW_METER && !flowMeterReady(m))
            return "NOT READY • no valid flow meter witnesses";
        return m.outputQuality() == PortQuality.VALID
                ? Integer.toString(m.secondary()) : "NOT READY • " + m.outputQuality().name();
    }

    private static boolean cylinderReady(PneumaticSystemMenu m) {
        return m.inputQuality() == PortQuality.VALID && m.cylinderSamples() > 0;
    }

    private static boolean flowMeterReady(PneumaticSystemMenu m) {
        return m.stateFlag() > 0 && m.inputQuality() == PortQuality.VALID
                && m.outputQuality() == PortQuality.VALID
                && m.upstreamQuality() == PortQuality.VALID
                && m.downstreamQuality() == PortQuality.VALID;
    }

    private static String primaryReadout(PneumaticSystemMenu m) {
        if (m.kind() == PneumaticSystemMenu.KIND_COMPRESSOR)
            return m.primary() + " • Redstone command";
        boolean ready = m.inputQuality() == PortQuality.VALID;
        if (m.kind() == PneumaticSystemMenu.KIND_FLOW_METER) ready = flowMeterReady(m);
        if (m.kind() == PneumaticSystemMenu.KIND_CYLINDER) ready = cylinderReady(m);
        return ready ? Integer.toString(m.primary()) : "NOT READY • input evidence incomplete";
    }

    private static String witnessReadout(PneumaticSystemMenu m) {
        String up = m.upstreamQuality() == PortQuality.VALID
                ? Integer.toString(m.upstreamPressure()) : "NOT READY";
        String down = m.downstreamQuality() == PortQuality.VALID
                ? Integer.toString(m.downstreamPressure()) : "NOT READY";
        return m.upstreamQuality().name() + ":" + up
                + " / " + m.downstreamQuality().name() + ":" + down;
    }

    private static UIElement pneumaticMechanism(PneumaticSystemMenu m) {
        var p = new UIElement().addClass("panel_bg");
        p.layout(l -> l.paddingAll(5).gapAll(4));
        p.addChild(RseLdUiComponents.title("PIONEER • INTERNAL PHYSICAL MECHANISM"));
        switch(m.kind()) {
            case PneumaticSystemMenu.KIND_COMPRESSOR -> p.addChildren(
                    RseLdUiComponents.liveRow("INPUT","Redstone command",()->m.primary()+" / 15"),
                    RseLdUiComponents.liveRow("SOURCE","commanded pressure",()->m.secondary()+" / 100"),
                    RseLdUiComponents.liveRow("SOLVER","network pressure",()->m.inputQuality()==PortQuality.VALID
                            ? m.tertiary()+" / 100":"NOT READY • no valid pneumatic network input"));
            case PneumaticSystemMenu.KIND_REGULATOR -> p.addChildren(
                    RseLdUiComponents.liveRow("ADJUSTABLE","setpoint",()->m.secondary()+" / 100"),
                    RseLdUiComponents.liveRow("MODEL","setpoint index",()->Integer.toString(m.tertiary())),
                    RseLdUiComponents.liveRow("SOLVER","network pressure",()->m.inputQuality()==PortQuality.VALID
                            ? m.primary()+" / 100":"NOT READY • no valid pneumatic network pressure"));
            case PneumaticSystemMenu.KIND_FLOW_METER -> p.addChildren(
                    RseLdUiComponents.liveRow("MEASURED","flow proxy",()->flowMeterReady(m)?Integer.toString(m.primary()):"NOT READY • sample/witness evidence incomplete"),
                    RseLdUiComponents.liveRow("MEASURED","meter ΔP",()->flowMeterReady(m)?Integer.toString(m.secondary()):"NOT READY • sample/witness evidence incomplete"),
                    RseLdUiComponents.liveRow("MEASURED","inlet / outlet pressure",()->flowMeterReady(m)?m.tertiary()+" / "+m.auxiliary():"NOT READY • sample/witness evidence incomplete"),
                    RseLdUiComponents.liveRow("EVIDENCE","meter samples",()->Integer.toString(m.stateFlag())),
                    RseLdUiComponents.liveRow("EVIDENCE","up / down witness",()->witnessReadout(m)));
            case PneumaticSystemMenu.KIND_PROPORTIONAL -> p.addChildren(
                    RseLdUiComponents.liveRow("CONTROL","external opening index",()->m.tertiary()+" / 15"),
                    RseLdUiComponents.liveRow("MODEL","local pressure drop",()->m.inputQuality()==PortQuality.VALID && m.outputQuality()==PortQuality.VALID
                            ? Math.max(0,m.primary()-m.secondary())+" pressure units":"NOT READY • inlet/outlet evidence incomplete"),
                    RseLdUiComponents.liveRow("SOLVER","network node pressure",()->m.inputQuality()==PortQuality.VALID && m.outputQuality()==PortQuality.VALID
                            ? m.auxiliary()+" / 100":"NOT READY • valve/network evidence incomplete"));
            case PneumaticSystemMenu.KIND_RELIEF -> p.addChildren(
                    RseLdUiComponents.liveRow("ADJUSTABLE","vent threshold P_set",()->m.tertiary()+" / 100"),
                    RseLdUiComponents.liveRow("EVENTS","vent count",()->Integer.toString(m.auxiliary())),
                    RseLdUiComponents.liveRow("STATE","relief valve",()->m.inputQuality()==PortQuality.VALID && m.outputQuality()==PortQuality.VALID
                            ? (m.stateFlag()==1?"VENTING":"ARMED"):"NOT READY • relief pressure unverified"));
            case PneumaticSystemMenu.KIND_CYLINDER -> p.addChildren(
                    RseLdUiComponents.liveRow("PATH","source pressure",()->cylinderReady(m)?m.cylinderSupply()+" / 100":"NOT READY • no verified actuator path"),
                    RseLdUiComponents.liveRow("PATH","physical path edges",()->cylinderReady(m)?Integer.toString(m.cylinderPathEdges()):"UNVERIFIED • no actuator samples"),
                    RseLdUiComponents.liveRow("PATH","loss (line + restriction)",()->cylinderReady(m)?m.cylinderLineLoss()+" + "+m.cylinderRestrictionLoss():"NOT READY • no verified path"),
                    RseLdUiComponents.liveRow("ACTUATOR","target / position",()->cylinderReady(m)?m.tertiary()+" / "+m.secondary():"NOT READY • no valid actuator response"),
                    RseLdUiComponents.liveRow("ACTUATOR","velocity / error",()->cylinderReady(m)?m.cylinderVelocity()+" / "+m.cylinderError():"NOT READY • no valid actuator response"),
                    RseLdUiComponents.liveRow("EVIDENCE","retained samples",()->Integer.toString(m.cylinderSamples())));
            case PneumaticSystemMenu.KIND_RESERVOIR -> p.addChildren(
                    RseLdUiComponents.liveRow("STORAGE","P_stored / P_line",()->m.inputQuality()==PortQuality.VALID
                            ? m.primary()+" / "+m.secondary():"NOT READY • storage / network pressure unverified"),
                    RseLdUiComponents.fixedRow("rate",()->"5 charge / 10t; 1 leak / 10t",
                            "Server-discrete storage model, not continuous fluid dynamics"));
            default -> p.addChild(RseLdUiComponents.fixedRow("mechanism",()->"SERVER PRESSURE / ROUTE",
                    "No fabricated valve dynamics; the upstream and downstream witness comes from the server"));
        }
        return p;
    }

    private static UIElement controls(PneumaticSystemMenu m){
        var p=new UIElement().addClass("panel_bg"); p.layout(l->l.paddingAll(5).gapAll(4));
        if(m.kind()==PneumaticSystemMenu.KIND_REGULATOR || m.kind()==PneumaticSystemMenu.KIND_RELIEF){
            var f=new TextField().setNumbersOnlyInt(25,100); f.layout(l->l.width(120));
            f.bind(DataBindingBuilder.string(
                    ()->Integer.toString(m.kind()==PneumaticSystemMenu.KIND_REGULATOR?m.secondary():m.tertiary()),
                    v->{try{m.setSetpointFromUi(Integer.parseInt(v));}catch(NumberFormatException ignored){}}
            ).build());
            p.addChildren(
                    RseLdUiComponents.liveRow("ADJUSTABLE","P_set",()->(m.kind()==PneumaticSystemMenu.KIND_REGULATOR?m.secondary():m.tertiary())+" • P ∈ {25,50,75,100}"),
                    f
            );
        } else if(m.kind()==PneumaticSystemMenu.KIND_VALVE){
            p.addChildren(
                    RseLdUiComponents.liveRow("ADJUSTABLE","state",()->m.stateFlag()==1?"OPEN":"CLOSED"),
                    RseLdUiComponents.serverAction("Toggle valve",m::toggleValve)
            );
        } else {
            p.addChild(RseLdUiComponents.fixedRow("control",()->m.kind()==PneumaticSystemMenu.KIND_PROPORTIONAL?"EXTERNAL UP COMMAND":"NONE",
                    "no local manual process coefficient"));
        }

        if(m.directional()){
            p.addChild(new UIElement().layout(l->l.flexDirection(YogaFlexDirection.ROW).flexWrap(dev.vfyjxf.taffy.style.FlexWrap.WRAP).gapAll(6)).addChildren(
                    RseLdUiComponents.serverAction("Cycle direction ▶",m::cycleWholeRouteForward),
                    RseLdUiComponents.serverAction("Cycle RX ▶",m::cycleInputForward),
                    RseLdUiComponents.serverAction("Cycle TX ▶",m::cycleOutputForward)
            ));
        }
        return p;
    }

    private static UIElement diagnostics(PneumaticSystemMenu m){
        var p=new UIElement().addClass("panel_bg");p.layout(l->l.paddingAll(5).gapAll(3));
        p.addChildren(
                RseLdUiComponents.liveRow("DIAGNOSIS","state",()->diagnosis(m)),
                RseLdUiComponents.liveRow("NEXT","action",()->nextAction(m)),
                RseLdUiComponents.fixedRow("solver",()->"SERVER PNEUMATIC NETWORK",
                        "The HMI exposes the authoritative pneumatic solve and retained path evidence; it never reruns the network solver locally.")
        );
        if(m.kind()==PneumaticSystemMenu.KIND_RESERVOIR){
            p.addChild(RseLdUiComponents.fixedRow("storage law",()->"charge ≤5 / 10t • leak 1 / 10t",
                    "finite-rate retained state; no continuous CFD or random leak history is fabricated"));
        }
        if(m.kind()==PneumaticSystemMenu.KIND_PROPORTIONAL){
            p.addChild(RseLdUiComponents.liveRow("DERIVED","Local ΔP",()->m.inputQuality()==PortQuality.VALID && m.outputQuality()==PortQuality.VALID
                        ? Integer.toString(Math.max(0,m.primary()-m.secondary())):"NOT READY • pressure evidence incomplete"));
        }
        if(m.kind()==PneumaticSystemMenu.KIND_FLOW_METER){
            p.addChild(RseLdUiComponents.liveRow("COMMISSIONING","evidence",()->switch(m.commissioningStatus()){
                case NOT_READY->"NOT READY • collect valid inlet/outlet evidence and at least four samples.";
                case PASS->"PASS • local pressure-loss evidence is complete and within the commissioning band.";
                case MARGINAL->"MARGINAL • inspect missing witnesses, degraded quality, or elevated local ΔP.";
                case FAIL->"FAIL • hard evidence fault or excessive local meter-section pressure drop.";
            }));
        }
        return p;
    }

    private static String equation(PneumaticSystemMenu m){
        if(m.kind()==PneumaticSystemMenu.KIND_COMPRESSOR)return "P_out = round(100 · u_R / 15)";
        if(m.kind()==PneumaticSystemMenu.KIND_RECEIVER)return "y_R = min(15, floor(15 · P_in / 100))";
        if(m.kind()==PneumaticSystemMenu.KIND_VALVE)return "OPEN ⇒ BACK ↔ FRONT ; CLOSED ⇒ isolated";
        if(m.kind()==PneumaticSystemMenu.KIND_CHECK_VALVE)return "permitted flow = BACK → FRONT only ; reverse blocked";
        if(m.kind()==PneumaticSystemMenu.KIND_CYLINDER)return "ΔP_path = ΔP_line + ΔP_restriction";
        if(m.kind()==PneumaticSystemMenu.KIND_RESERVOIR)return "H_charge = max(0, P_line - P_stored)";
        if(m.kind()==PneumaticSystemMenu.KIND_PROPORTIONAL)return "ΔP_local = max(0, P_in - P_out)";
        if(m.kind()==PneumaticSystemMenu.KIND_FLOW_METER)return "ΔP_meter = P_in - P_out";
        if(m.directional())return "ΔP = P_in - P_out";
        return "P_node = authoritative pneumatic network solve";
    }

    private static String diagnosis(PneumaticSystemMenu m){
        if(m.kind()==PneumaticSystemMenu.KIND_CYLINDER){
            if(hard(m.inputQuality())||hard(m.outputQuality()))return "ACTUATOR INPUT / OUTPUT EVIDENCE FAULT";
            if(!trustworthy(m.inputQuality()))return "ACTUATOR INPUT NOT VERIFIED • "+m.inputQuality().name();
            if(m.cylinderSamples()<=0)return "NO RETAINED ACTUATOR RESPONSE • NOT READY";
            if(m.secondary()==m.tertiary())return "AT TARGET • OBSERVED POSITION";
            if(m.cylinderSupply()<=0)return "NO PRESSURIZED SUPPLY PATH";
            if(m.cylinderSupply()<25)return "LOW SUPPLY PRESSURE • SLOW RESPONSE BAND";
            if(m.cylinderRestrictionLoss()>=Math.max(3,m.cylinderLineLoss()))return "RESTRICTION / REGULATION LOSS DOMINANT";
            if(m.cylinderLineLoss()>=4&&m.cylinderLineLoss()>=m.cylinderRestrictionLoss())return "DISTRIBUTION PATH LOSS DOMINANT";
            if(m.primary()<25)return "DOWNSTREAM PRESSURE STARVATION";
            return "NORMAL PRESSURE-DEPENDENT FINITE-RATE RESPONSE";
        }
        if(m.kind()==PneumaticSystemMenu.KIND_RESERVOIR){
            if(hard(m.inputQuality()))return "RESERVOIR EVIDENCE FAULT";
            if(!trustworthy(m.inputQuality()))return "STORAGE PRESSURE UNVERIFIED • "+m.inputQuality().name();
            if(m.primary()<=0&&m.secondary()<=0)return "EMPTY / DEPRESSURIZED";
            if(m.secondary()>m.primary())return "CHARGING TOWARD LINE PRESSURE";
            if(m.primary()>m.secondary())return "DISCHARGING / SUPPORTING LOWER-PRESSURE LINE";
            return "BUFFERED • STORED AND LINE PRESSURE ALIGNED";
        }
        if(m.kind()==PneumaticSystemMenu.KIND_PROPORTIONAL){
            if(hard(m.inputQuality())||hard(m.outputQuality()))return "VALVE EVIDENCE FAULT";
            if(!trustworthy(m.inputQuality()))return "VALVE INPUT UNVERIFIED • "+m.inputQuality().name();
            if(m.primary()<=0)return "NO UPSTREAM PRESSURE";
            if(m.tertiary()<=0)return "COMMANDED CLOSED • FULL ISOLATION";
            int drop=Math.max(0,m.primary()-m.secondary());
            if(m.tertiary()<5&&drop>=5)return "STRONG COMMANDED RESTRICTION";
            if(m.tertiary()<12&&drop>=3)return "PARTIAL COMMANDED RESTRICTION";
            if(drop<=2)return "LOW LOCAL RESTRICTION";
            return "VALVE PRESSURE TRANSFORM ACTIVE";
        }
        if(m.kind()==PneumaticSystemMenu.KIND_RELIEF || m.kind()==PneumaticSystemMenu.KIND_VALVE) {
            String configured = m.kind()==PneumaticSystemMenu.KIND_RELIEF
                    ? (m.stateFlag()==1?"VENTING":"ARMED")
                    : (m.stateFlag()==1?"OPEN":"CLOSED");
            if(hard(m.inputQuality()) || hard(m.outputQuality())) return "VALVE EVIDENCE FAULT • "+configured;
            if(!trustworthy(m.inputQuality()) || !trustworthy(m.outputQuality()))
                return (m.kind()==PneumaticSystemMenu.KIND_RELIEF?"RETAINED RELIEF ":"CONFIG ")
                        +configured+" • PRESSURE UNVERIFIED";
            return configured+" • PRESSURE EVIDENCE VALID";
        }
        if(m.kind()==PneumaticSystemMenu.KIND_FLOW_METER) return "FLOW METER • "+m.commissioningStatus().name();
        if(hard(m.inputQuality()) || hard(m.outputQuality())) return "PNEUMATIC EVIDENCE FAULT";
        if(m.inputQuality()!=PortQuality.VALID || m.outputQuality()!=PortQuality.VALID)
            return "NOT READY • INCOMPLETE PNEUMATIC EVIDENCE";
        return "NOMINAL • INPUT / OUTPUT VERIFIED";
    }

    private static String nextAction(PneumaticSystemMenu m){
        String d=diagnosis(m);
        if(d.contains("NO PRESSURIZED"))return "NEXT • restore a permitted compressor/reservoir path and check closed or reversed pneumatic elements.";
        if(d.contains("LOW SUPPLY"))return "NEXT • raise available source pressure before tuning downstream restrictions.";
        if(d.contains("RESTRICTION"))return "NEXT • inspect regulator setpoint and proportional/closed valve restrictions on the winning pressure path.";
        if(d.contains("PATH LOSS"))return "NEXT • shorten/segment the pipe run or move storage closer to the actuator.";
        if(d.contains("STARVATION"))return "NEXT • compare supply pressure with retained path loss before changing the cylinder.";
        if(d.contains("UNVERIFIED")||d.contains("NOT VERIFIED")||d.contains("NOT READY"))return "NEXT • collect valid pressure / retained response evidence before accepting a safe operating state.";
        if(d.contains("FAULT"))return "NEXT • repair topology/evidence quality before interpreting actuator response.";
        if(d.startsWith("CHARGING"))return "NEXT • allow finite-rate recovery; storage rises by at most 5 pressure units every 10 ticks.";
        if(d.contains("NO UPSTREAM"))return "NEXT • restore supply pressure; opening changes cannot create upstream pressure.";
        return "NEXT • retained evidence is coherent; preserve this state as the commissioning reference.";
    }

    private static boolean trustworthy(PortQuality q){return q==PortQuality.VALID;}
    private static boolean hard(PortQuality q){return q==PortQuality.FAULT||q==PortQuality.DOMAIN_MISMATCH||q==PortQuality.TOPOLOGY_ERROR;}
    private static String deviceName(PneumaticSystemMenu m){return switch(m.kind()){case 0->"AIR COMPRESSOR";case 1->"PNEUMATIC PIPE";case 2->"AIR RESERVOIR";case 3->"PRESSURE REGULATOR";case 4->"PNEUMATIC RECEIVER";case 5->"PNEUMATIC VALVE";case 6->"CHECK VALVE";case 7->"FLOW METER";case 8->"PROPORTIONAL VALVE";case 9->"RELIEF VALVE";case 10->"PNEUMATIC CYLINDER";default->"PNEUMATIC DEVICE";};}
}
