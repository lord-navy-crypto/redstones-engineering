package dev.redstoneengineering.ui.ldlib;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.redstoneengineering.ui.menu.PidControllerMenu;
import net.minecraft.world.entity.player.Player;
import org.appliedenergistics.yoga.YogaFlexDirection;

public final class PidControllerLdUi {
    private PidControllerLdUi() {}

    public static ModularUI create(PidControllerMenu m, Player player) {
        var root=new UIElement().addClass("panel_bg");
        root.layout(l->l.width(720).paddingAll(8).gapAll(6));
        root.addChildren(
                RseLdUiComponents.title("PID CLOSED-LOOP ENGINEERING WORKBENCH"),
                RseLdUiComponents.title("PIONEER PATTERN • CONTROL / ACCEPTANCE MODEL"),
                modelPanel(m),
                trendPanel(m),
                controls(m),
                runtimePanel(m),
                plantPanel(m),
                acceptancePanel(m),
                trialPanel(m),
                RseLdUiComponents.authorityFooter()
        );
        return ModularUI.of(UI.of(root,StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.GDP)),player);
    }

    private static UIElement modelPanel(PidControllerMenu m){
        return new UIElement().addClass("panel_bg").layout(l->l.paddingAll(5).gapAll(3)).addChildren(
                new Label().setText("e[n]=SP[n]-PV[n]; P[n]=Kp·e[n]"),
                new Label().setText("Σe_cand=clamp(Σe[n-1]+e[n],-180,180); I[n]=KiDiv==0 ? 0 : Σe_cand/KiDiv"),
                new Label().setText("d_f[n]=d_f[n-1]+(Δe-d_f[n-1])/dSmooth; D[n]=Kd·d_f[n]"),
                new Label().setText("u_raw=bias+P+I+D; u=clamp(u_raw,0,15); saturation may hold integral"),
                RseLdUiComponents.liveRow("PRESET","tuning",()->tuningName(m.tuning())),
                RseLdUiComponents.liveRow("FIXED","Kp/KiDiv/Kd",()->m.kp()+" / "+m.kiDiv()+" / "+m.kd()),
                RseLdUiComponents.liveRow("FIXED","dSmooth/Δt",()->m.derivativeSmoothing()+" / "+m.sampleTicks()+"t")
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
        if(!m.plantDetected()){
            p.addChild(RseLdUiComponents.fixedRow("plant witness",()->"NOT DETECTED","generic PID commissioning remains valid without a pneumatic witness"));
            return p;
        }
        p.addChildren(
                RseLdUiComponents.liveRow("PLANT","ready/status",()->m.plantReady()+" / "+m.plantStatus().name()),
                RseLdUiComponents.liveRow("PLANT","position/target",()->m.plantPosition()+" / "+m.plantTarget()),
                RseLdUiComponents.liveRow("PNEUMATIC","pressure/supply",()->m.plantPressure()+" / "+m.plantSupply()),
                RseLdUiComponents.liveRow("PATH","loss",()->m.plantObservedLoss()+" = line "+m.plantLineLoss()+" + restriction "+m.plantRestrictionLoss()),
                RseLdUiComponents.liveRow("PLANT","stall/samples/penalty",()->m.plantStallTicks()+" / "+m.plantSamples()+" / "+m.plantPenalty()),
                RseLdUiComponents.liveRow("DIAGNOSIS","plant",()->m.plantDiagnosis().name())
        );
        return p;
    }

    private static UIElement acceptancePanel(PidControllerMenu m){
        String latest=m.historyCount()>0?("#"+m.latestSequence()+" • "+m.latestAcceptanceStatus().name()+" • score "+m.latestAcceptanceScore()):"NO CAPTURE";
        return new UIElement().addClass("panel_bg").layout(l->l.paddingAll(5).gapAll(3)).addChildren(
                new Label().setText("ACCEPTANCE EVIDENCE"),
                RseLdUiComponents.liveRow("HISTORY","count",()->Integer.toString(m.historyCount())),
                RseLdUiComponents.liveRow("LATEST","record",()->m.historyCount()>0?("#"+m.latestSequence()+" • "+m.latestAcceptanceStatus().name()+" • score "+m.latestAcceptanceScore()):"NO CAPTURE"),
                RseLdUiComponents.liveRow("COMPARE","trend",()->m.comparisonTrend()==null?"NOT COMPARABLE":m.comparisonTrend().name()),
                RseLdUiComponents.liveRow("COMPARE","Δscore/Δissues",()->signed(m.scoreDelta())+" / "+signed(m.topologyIssueDelta()))
        );
    }

    private static UIElement trialPanel(PidControllerMenu m){
        return new UIElement().addClass("panel_bg").layout(l->l.paddingAll(5).gapAll(3)).addChildren(
                new Label().setText("PIONEER WORKFLOW • CLOSED-LOOP COMMISSIONING TRIAL"),
                RseLdUiComponents.liveRow("TRIAL","baseline/candidate",()->seq(m.trialBaselineSequence())+" / "+seq(m.trialCandidateSequence())),
                RseLdUiComponents.liveRow("TRIAL","comparison",()->m.trialTrend()==null?"INCOMPLETE":m.trialTrend().name()+" • "+(m.trialRobust()?"ROBUST":"CHECK")),
                RseLdUiComponents.liveRow("DELTA","score/settle",()->signed(m.trialScoreDelta())+" / "+signed(m.trialSettlingDelta())+"t"),
                RseLdUiComponents.liveRow("DELTA","overshoot/saturation",()->signed(m.trialOvershootDelta())+" / "+signed(m.trialSaturationDelta())),
                RseLdUiComponents.liveRow("DELTA","topology issues",()->signed(m.trialTopologyIssueDelta()))
        );
    }

    private static String tuningName(int tuning){return switch(tuning){case 0->"P-GENTLE";case 1->"PI";case 2->"PID-BALANCED";case 3->"PID-AGGRESSIVE";default->"UNKNOWN";};}
    private static String signed(int v){return v>0?"+"+v:Integer.toString(v);}
    private static String seq(int v){return v>0?"#"+v:"—";}
}
