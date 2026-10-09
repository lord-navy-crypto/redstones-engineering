package dev.redstoneengineering.ui.ldlib;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.redstoneengineering.ui.menu.PidControllerMenu;
import net.minecraft.world.entity.player.Player;
import dev.vfyjxf.taffy.style.FlexWrap;
import org.appliedenergistics.yoga.YogaFlexDirection;

public final class PidControllerLdUi {
    private PidControllerLdUi() {}

    public static ModularUI create(PidControllerMenu m, Player player) {
        var root = new UIElement().addClass("panel_bg");
        root.layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(10).gapAll(8));
        var page0 = page(RseLdUiComponents.title("PIONEER PATTERN • CONTROL / ACCEPTANCE MODEL"),modelPanel(m),trendPanel(m));
        var page1 = page(controls(m),runtimePanel(m));
        page1.setDisplay(false);
        var page2 = page(plantPanel(m));
        page2.setDisplay(false);
        var page3 = page(acceptancePanel(m));
        page3.setDisplay(false);
        var page4 = page(trialPanel(m));
        page4.setDisplay(false);
        var page5 = page(RseLdUiComponents.authorityFooter());
        page5.setDisplay(false);
        var workspace = new ScrollerView().scrollerStyle(style -> style
                .mode(ScrollerMode.BOTH)
                .verticalScrollDisplay(ScrollDisplay.AUTO)
                .horizontalScrollDisplay(ScrollDisplay.AUTO)
                .minScrollPixel(8).maxScrollPixel(72));
        workspace.layout(l -> l.flex(1));
        workspace.viewPort(view -> view.layout(l -> l.paddingAll(8)));
        workspace.viewContainer(view -> view.layout(l -> l.width(950).paddingAll(8).gapAll(8)));
        workspace.addScrollViewChildren(page0, page1, page2, page3, page4, page5);
        var tabs = new UIElement().addClass("panel_bg");
        tabs.layout(l -> l.flexDirection(YogaFlexDirection.ROW).flexWrap(FlexWrap.WRAP).gapAll(5).paddingAll(5));
        tabs.addChildren(
                tabButton("Model", workspace, page0, page0, page1, page2, page3, page4, page5),
                tabButton("Configure", workspace, page1, page0, page1, page2, page3, page4, page5),
                tabButton("Plant", workspace, page2, page0, page1, page2, page3, page4, page5),
                tabButton("Acceptance", workspace, page3, page0, page1, page2, page3, page4, page5),
                tabButton("Trial", workspace, page4, page0, page1, page2, page3, page4, page5),
                tabButton("Authority", workspace, page5, page0, page1, page2, page3, page4, page5)
        );
        root.addChildren(
                RseLdUiComponents.title("PID CLOSED-LOOP ENGINEERING WORKBENCH"),
                tabs,
                new Label().setText("SCROLL • wheel Y • Shift+wheel X"),
                workspace
        );
        return RseLdUiComponents.responsiveUi(root, player, 690, 450);
    }

    private static UIElement page(UIElement... children) {
        return new UIElement().layout(l -> l.width(920).paddingAll(12).gapAll(10)).addChildren(children);
    }

    private static Button tabButton(String label, ScrollerView workspace, UIElement selected, UIElement... pages) {
        return new Button().setText(label).setOnClick(event -> {
            for (UIElement page : pages) page.setDisplay(page == selected);
            workspace.horizontalScroller.setNormalizedValue(0);
            workspace.verticalScroller.setNormalizedValue(0);
        });
    }

    /**
     * Pioneer controller ledger: every numeric term comes from the
     * server-side PidControllerBlock runtime/ClosedLoopCommissioning snapshot.
     * No client-side PID step, integral update or second plant solve.
     */
    private static UIElement modelPanel(PidControllerMenu m){
        return new UIElement().addClass("panel_bg").layout(l->l.paddingAll(5).gapAll(3)).addChildren(
                new Label().setText("PIONEER • SERVER PID TERM DECOMPOSITION"),
                RseLdUiComponents.formulaCard(
                        "e=SP−PV; P=Kp·e; I=Σe/KiDiv; D=Kd·d_f; u=clamp(bias+P+I+D,0,15)"),
                RseLdUiComponents.liveRow("PRESET","tuning",()->tuningName(m.tuning())),
                RseLdUiComponents.liveRow("PRESET","Kp / KiDiv / Kd",()->m.kp()+" / "+m.kiDiv()+" / "+m.kd()),
                RseLdUiComponents.liveRow("PRESET","dSmooth / Δt",()->m.derivativeSmoothing()+" / "+m.sampleTicks()+" ticks"),
                RseLdUiComponents.liveRow("MEASURED","SP / PV",()->m.available()?m.setpoint()+" / "+m.processValue():"UNAVAILABLE • commissioning input"),
                RseLdUiComponents.liveRow("DERIVED","error e",()->m.available()?signed(m.error()):"NOT READY"),
                RseLdUiComponents.liveRow("STATE","Σe / filtered derivative",()->m.integralState()+" / "+m.derivativeState()),
                RseLdUiComponents.liveRow("TERMS","P / I / D",()->m.pTerm()+" / "+m.iTerm()+" / "+m.dTerm()),
                RseLdUiComponents.liveRow("TERMS","bias / u_raw",()->m.bias()+" / "+m.unsaturatedOutput()),
                RseLdUiComponents.liveRow("OUTPUT","clamped u",()->m.controlOutput()+" / 15"),
                RseLdUiComponents.liveRow("SAFETY","anti-windup",()->m.antiWindupHolding()?"INTEGRAL HELD":"INTEGRATION ALLOWED"),
                RseLdUiComponents.liveRow("STATE","controller mode",()->m.inhibited()?"INHIBITED":m.manualMode()?"MANUAL":"AUTOMATIC"),
                RseLdUiComponents.liveRow("EVIDENCE","controller / plant status",()->m.controllerStatus().name()+" / "+
                        (m.plantDetected()?m.plantStatus().name():"NO PNEUMATIC PLANT")),
                new Label().setText("Preset-dependent coefficients are not individual editable knobs; change preset in Configure.")
        );
    }

    private static UIElement trendPanel(PidControllerMenu m){
        return new UIElement().addClass("panel_bg").layout(l->l.paddingAll(5).gapAll(3)).addChildren(
                new Label().setText("AUTHORITATIVE TREND • SP / PV / OUT • 2t/sample • 32-sample bounded ring"),
                new PidTrendPlotElement(m),
                RseLdUiComponents.liveRow("LIVE","SP/PV/OUT",()->m.setpoint()+" / "+m.processValue()+" / "+m.controlOutput()),
                RseLdUiComponents.liveRow("LIVE","error",()->Integer.toString(m.error())),
                RseLdUiComponents.liveRow("EVIDENCE","authoritative samples",()->m.trendCount()+" / "+PidControllerMenu.TREND_SAMPLES)
        );
    }

    private static UIElement controls(PidControllerMenu m){
        return new UIElement().addClass("panel_bg").layout(l->l.paddingAll(5).gapAll(4)).addChildren(
                new UIElement().layout(l->l.flexDirection(YogaFlexDirection.ROW).gapAll(6)).addChildren(
                        RseLdUiComponents.serverAction("Cycle tuning preset ▶",m::cycleTuningForward),
                        RseLdUiComponents.serverAction("Cycle RX ▶",m::cycleInputForward),
                        RseLdUiComponents.serverAction("Cycle TX ▶",m::cycleOutputForward)
                ),
                new UIElement().layout(l->l.flexDirection(YogaFlexDirection.ROW).gapAll(6)).addChildren(
                        RseLdUiComponents.serverAction("Capture acceptance",m::captureAcceptance),
                        RseLdUiComponents.serverAction("Reset runtime + trend",m::resetRuntimeTrend),
                        RseLdUiComponents.serverAction("Trial baseline",m::captureTrialBaseline),
                        RseLdUiComponents.serverAction("Trial candidate",m::captureTrialCandidate),
                        RseLdUiComponents.serverAction("Clear trial",m::clearTrial)
                ),
                RseLdUiComponents.liveRow("ROUTE","RX → TX",()->m.inputFacing().getName().toUpperCase()+" → "+m.outputFacing().getName().toUpperCase())
        );
    }

    private static UIElement runtimePanel(PidControllerMenu m){
        return new UIElement().addClass("panel_bg").layout(l->l.paddingAll(5).gapAll(3)).addChildren(
                RseLdUiComponents.liveRow("STATE","mode",()->m.inhibited()?"INHIBITED":m.manualMode()?"MANUAL":"AUTO"),
                RseLdUiComponents.liveRow("TERMS","P/I/D",()->m.pTerm()+" / "+m.iTerm()+" / "+m.dTerm()),
                RseLdUiComponents.liveRow("STATE","Σe / d_f",()->m.integralState()+" / "+m.derivativeState()),
                RseLdUiComponents.liveRow("DERIVED","u_raw / u",()->m.unsaturatedOutput()+" / "+m.controlOutput()),
                RseLdUiComponents.liveRow("SAFETY","anti-windup",()->m.antiWindupHolding()?"HOLDING INTEGRAL":"INTEGRATING"),
                RseLdUiComponents.liveRow("COMMISSIONING","system/controller",()->m.status().name()+" / "+m.controllerStatus().name()),
                RseLdUiComponents.liveRow("METRICS","rise90/settle",()->m.rise90Ticks()+"t / "+m.settlingTicks()+"t"),
                RseLdUiComponents.liveRow("METRICS","overshoot/saturation",()->m.overshoot()+" / "+m.saturationEvents())
        );
    }

    private static UIElement plantPanel(PidControllerMenu m){
        var p=new UIElement().addClass("panel_bg");p.layout(l->l.paddingAll(5).gapAll(3));
        p.addChildren(
                new Label().setText("PNEUMATIC GOLDEN SYSTEM • CAUSAL COMMISSIONING SPLIT"),
                RseLdUiComponents.liveRow("Controller","status",()->m.controllerStatus().name()+" • score "+m.controllerScore()),
                RseLdUiComponents.liveRow("Pneumatic plant","status",()->m.plantDetected()
                        ? m.plantStatus().name()+" • ready="+m.plantReady()
                        : "NOT DETECTED"),
                RseLdUiComponents.liveRow("Likely cause","diagnosis",()->diagnosisText(m)),
                RseLdUiComponents.liveRow("System verdict","combined",()->m.status().name())
        );
        if(!m.plantDetected()){
            p.addChildren(
                    RseLdUiComponents.fixedRow("plant witness",()->"NOT DETECTED","generic PID commissioning remains valid without a pneumatic witness"),
                    new Label().setText("NONE • explicit cylinder feedback not detected")
            );
            return p;
        }
        p.addChildren(
                RseLdUiComponents.liveRow("PLANT","Actuator / supply pressure",()->m.plantPressure()+" / "+m.plantSupply()),
                RseLdUiComponents.liveRow("PATH","Loss obs / line / restrict",()->m.plantObservedLoss()+" / "+m.plantLineLoss()+" / "+m.plantRestrictionLoss()),
                RseLdUiComponents.liveRow("PLANT","Position / target / stall",()->m.plantPosition()+" / "+m.plantTarget()+" / "+m.plantStallTicks()+"t"),
                RseLdUiComponents.liveRow("PATH","loss decomposition check",()->m.plantObservedLoss()+" observed vs "+
                        (m.plantLineLoss()+m.plantRestrictionLoss())+" modeled"),
                RseLdUiComponents.liveRow("EVIDENCE","plant ready / samples",()->(m.plantReady()?"READY":"NOT READY")+" • "+m.plantSamples()),
                RseLdUiComponents.liveRow("PLANT","samples / penalty",()->m.plantSamples()+" / "+m.plantPenalty())
        );
        return p;
    }

    private static UIElement acceptancePanel(PidControllerMenu m){
        return new UIElement().addClass("panel_bg").layout(l->l.paddingAll(5).gapAll(3)).addChildren(
                new Label().setText("ACCEPTANCE EVIDENCE"),
                RseLdUiComponents.liveRow("HISTORY","count",()->Integer.toString(m.historyCount())),
                RseLdUiComponents.liveRow("LATEST","record",()->m.historyCount()>0?("#"+m.latestSequence()+" • "+m.latestAcceptanceStatus().name()+" • score "+m.latestAcceptanceScore()):"NO CAPTURE"),
                RseLdUiComponents.liveRow("COMPARE","trend",()->m.comparisonTrend()==null
                        ? "Baseline capture established; capture again after a change to compare."
                        : "Compared with previous: "+m.comparisonTrend().name()),
                RseLdUiComponents.liveRow("COMPARE","Δscore/Δissues",()->signed(m.scoreDelta())+" / "+signed(m.topologyIssueDelta()))
        );
    }

    private static UIElement trialPanel(PidControllerMenu m){
        return new UIElement().addClass("panel_bg").layout(l->l.paddingAll(5).gapAll(3)).addChildren(
                new Label().setText("PIONEER WORKFLOW • CLOSED-LOOP COMMISSIONING TRIAL"),
                new Label().setText("START WITH BASELINE • BASELINE READY • settle, then candidate"),
                new Label().setText("Captures require settled PASS / MARGINAL / FAIL evidence"),
                RseLdUiComponents.liveRow("TRIAL","baseline/candidate",()->seq(m.trialBaselineSequence())+" / "+seq(m.trialCandidateSequence())),
                RseLdUiComponents.liveRow("TRIAL","comparison",()->m.trialTrend()==null?"INCOMPLETE":m.trialTrend().name()+" • "+(m.trialRobust()?"ROBUST":"CHECK")),
                RseLdUiComponents.liveRow("DELTA","Δscore",()->signed(m.trialScoreDelta())),
                RseLdUiComponents.liveRow("DELTA","Δsettle",()->signed(m.trialSettlingDelta())+"t"),
                RseLdUiComponents.liveRow("DELTA","Δovershoot",()->signed(m.trialOvershootDelta())),
                RseLdUiComponents.liveRow("DELTA","Δsat",()->signed(m.trialSaturationDelta())),
                RseLdUiComponents.liveRow("DELTA","topology issues",()->signed(m.trialTopologyIssueDelta()))
        );
    }

    private static String diagnosisText(PidControllerMenu m){
        if(!m.plantDetected()) return "NONE • explicit cylinder feedback not detected";
        return switch(m.plantDiagnosis()){
            case RESTRICTION -> "RESTRICTION • check valve / path command";
            case NO_SUPPLY -> "NO SUPPLY • compressor / reservoir / feed";
            case LOW_ACTUATOR_PRESSURE -> "LOW ACTUATOR PRESSURE • inspect path losses";
            case STALLED -> "STALLED • cylinder motion not following target";
            default -> m.plantDiagnosis().name();
        };
    }

    private static String tuningName(int tuning){return switch(tuning){case 0->"P-GENTLE";case 1->"PI";case 2->"PID-BALANCED";case 3->"PID-AGGRESSIVE";default->"UNKNOWN";};}
    private static String signed(int v){return v>0?"+"+v:Integer.toString(v);}
    private static String seq(int v){return v>0?"#"+v:"—";}
}
