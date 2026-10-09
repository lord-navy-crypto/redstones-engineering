package dev.redstoneengineering.ui.ldlib;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.redstoneengineering.block.SignalAnalyzerBlock;
import dev.redstoneengineering.diagnostics.SignalCalibrationTrialComparison;
import dev.redstoneengineering.ui.menu.SignalAnalyzerMenu;
import net.minecraft.world.entity.player.Player;
import org.appliedenergistics.yoga.YogaFlexDirection;

import java.util.Locale;

public final class SignalAnalyzerLdUi {
    private SignalAnalyzerLdUi(){}

    public static ModularUI create(SignalAnalyzerMenu m, Player player){
        var root=new UIElement().addClass("panel_bg");
        root.layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(8).gapAll(6));
        root.addChildren(
                RseLdUiComponents.title("SIGNAL ANALYZER • METROLOGY / CALIBRATION"),
                RseLdUiComponents.tabbedWorkspace(
                        630, 400, 850,
                        new String[]{"Overview", "Configure", "Diagnostics", "Evidence"},
                        new UIElement[]{
                                RseLdUiComponents.workspacePage(
                                        RseLdUiComponents.formulaCard(()->"x_cal = clamp(x_raw + b_cal, 0, 15) ; e_ref needs per-sample calibrated history (trial evidence)"),
                                        measurementPanel(m),
                                        new SignalAnalyzerPlotElement(m),
                                        RseLdUiComponents.note("Plot lines are visual interpolation between retained samples; invalid slots are gaps, not measured zero. Mean/min/max use valid history only.")
                                ),
                                RseLdUiComponents.workspacePage(
                                        measurementPanel(m),
                                        controlPanel(m)
                                ),
                                RseLdUiComponents.workspacePage(
                                        statisticsPanel(m),
                                        trialPanel(m)
                                ),
                                RseLdUiComponents.workspacePage(
                                        historyEvidencePanel(m),
                                        RseLdUiComponents.authorityFooter()
                                )
                        }
                )
        );
        return RseLdUiComponents.responsiveUi(root, player, 650, 440);
    }

    private static UIElement measurementPanel(SignalAnalyzerMenu m){
        var p=new UIElement().addClass("panel_bg"); p.layout(l->l.paddingAll(5).gapAll(3));
        p.addChildren(
                new Label().setText("PIONEER PATTERN • METROLOGY / CALIBRATION"),
                RseLdUiComponents.liveRow("EVIDENCE","server snapshot",()->m.snapshotReady()?"SYNCED":"NOT READY • awaiting server data"),
                RseLdUiComponents.liveRow("STATE","mode",()->m.snapshotReady()?modeName(m.mode()):"NOT READY"),
                RseLdUiComponents.liveRow("MODEL","boundary",()->m.mode()==SignalAnalyzerBlock.TAP
                        ? "TAP • NON-INVASIVE • no output drive"
                        : "INLINE • explicit two-port boundary"),
                RseLdUiComponents.liveRow("MEASURED","x_raw",()->m.snapshotReady() && m.measurementQuality()==dev.redstoneengineering.core.port.PortQuality.VALID
                        ? m.raw()+" / 15" : "NO SIGNAL • measurement unverified"),
                RseLdUiComponents.liveRow("DERIVED","x_cal",()->m.snapshotReady() && m.measurementQuality()==dev.redstoneengineering.core.port.PortQuality.VALID
                        ? m.calibrated()+" / 15" : "NOT READY • valid raw input required"),
                RseLdUiComponents.liveRow("MODEL","b_cal / x_ref",()->signed(m.calibrationOffset())+" / "+m.reference()),
                RseLdUiComponents.liveRow("OUTPUT","inline",()->!m.snapshotReady()
                        ? "NOT READY • awaiting server snapshot"
                        : m.mode()==SignalAnalyzerBlock.TAP
                        ? "NOT APPLICABLE • TAP mode never drives Redstone"
                        : m.measurementQuality()==dev.redstoneengineering.core.port.PortQuality.VALID
                            ? m.output()+" / 15 • raw pass-through"
                            : "NOT READY • INLINE input evidence unverified"),
                RseLdUiComponents.liveRow("EVIDENCE","measurement",()->m.snapshotReady()
                        ? m.measurementQuality().name()+" • coverage="+m.coveragePercent()+"%"
                        : "NOT READY • awaiting synchronized quality"),
                new Label().setText("DISPLAY ONLY • calibrated reading does not drive INLINE; physical output remains RAW."),
                new Label().setText("Rolling statistics are synchronized server evidence; the client never samples the world."),
                new Label().setText("μ=rounded mean • min/max guides bound the synchronized rolling window.")
        );
        return p;
    }

    private static UIElement controlPanel(SignalAnalyzerMenu m){
        var cal=field(-2,2,m::calibrationOffset,m::setCalibrationFromUi);
        var ref=field(0,15,m::reference,m::setReferenceFromUi);
        var p=new UIElement().addClass("panel_bg"); p.layout(l->l.paddingAll(5).gapAll(4));
        p.addChildren(
                controlRow("b_cal","-2..+2 • direct entry",cal),
                controlRow("x_ref","0..15 • direct entry",ref),
                new UIElement().layout(l->l.flexDirection(YogaFlexDirection.ROW).flexWrap(dev.vfyjxf.taffy.style.FlexWrap.WRAP).gapAll(6)).addChildren(
                        RseLdUiComponents.serverAction("Toggle TAP/INLINE",m::toggleMode),
                        RseLdUiComponents.serverAction("Reset statistics",m::resetStatistics)
                ),
                new Label().setText("b_cal and x_ref are server-authoritative; client does not sample the world.")
        );
        return p;
    }

    private static UIElement statisticsPanel(SignalAnalyzerMenu m){
        var p=new UIElement().addClass("panel_bg"); p.layout(l->l.paddingAll(5).gapAll(3));
        p.addChildren(
                RseLdUiComponents.liveRow("WINDOW","avg / p2p",()->m.validWindowCount()>0
                        ? decimal100(m.average100())+" / "+m.peakToPeak()+" • "+m.validWindowCount()+" valid samples"
                        : "NOT READY • no valid rolling samples"),
                RseLdUiComponents.liveRow("WINDOW","meanStep",()->m.validWindowCount()>1
                        ? decimal100(m.meanStep100()) : "NOT READY • need ≥2 valid samples"),
                RseLdUiComponents.liveRow("WINDOW","measurement coverage",()->"measurement coverage="+m.validWindowCount()+"/"+m.windowCount()+" • "+m.coveragePercent()+"%"),
                RseLdUiComponents.liveRow("SYNC","age",()->m.sampleAgeTicks()<0?"NO SAMPLE":m.sampleAgeTicks()+" ticks"),
                RseLdUiComponents.liveRow("LIFETIME","min / max",()->m.lifeMin()+" / "+m.lifeMax()),
                RseLdUiComponents.liveRow("LIFETIME","changes / edges",()->m.changes()+" • ↑"+m.rising()+" ↓"+m.falling()),
                RseLdUiComponents.liveRow("LIFETIME","last / max Δ",()->m.lastDelta()+" / "+m.maxDelta()),
                RseLdUiComponents.liveRow("STATE","stable age",()->m.stableAgeTicks()+" ticks"),
                RseLdUiComponents.liveRow("STATE","variation",()->stabilityClass(m)),
                RseLdUiComponents.liveRow("EVIDENCE","valid rolling samples",()->m.validWindowCount()+"/"+m.windowCount()),
                RseLdUiComponents.liveRow("METROLOGY","raw mean − reference",()->
                        completeValidWindow(m)
                                ? decimal100(m.average100()-100*m.reference())+" levels (uncalibrated)"
                                : "NOT READY • fully valid window required"),
                RseLdUiComponents.fixedRow("calibration",()->"PER-SAMPLE CLAMP",
                        "A calibrated window residual cannot be recovered exactly from the raw mean alone"),
                new Label().setText("Rolling statistics are synchronized server evidence; numeric zero remains distinct from missing evidence.")
        );
        return p;
    }

    private static UIElement historyEvidencePanel(SignalAnalyzerMenu m){
        var p=new UIElement().addClass("panel_bg");
        p.layout(l->l.paddingAll(5).gapAll(3));
        p.addChildren(
                new Label().setText("SERVER RETAINED HISTORY • NO CLIENT SAMPLING"),
                RseLdUiComponents.liveRow("EVIDENCE","current quality",()->m.measurementQuality().name()),
                RseLdUiComponents.liveRow("HISTORY","recorded samples",()->Integer.toString(m.totalSamples())),
                RseLdUiComponents.liveRow("WINDOW","valid / total",()->m.validWindowCount()+"/"+m.windowCount()
                        +" • "+m.coveragePercent()+"%"),
                RseLdUiComponents.liveRow("FRESHNESS","last captured sample",()->m.sampleAgeTicks()<0
                        ? "NOT CAPTURED" : m.sampleAgeTicks()+" game ticks ago"),
                RseLdUiComponents.liveRow("WINDOW","readiness",()->completeValidWindow(m)
                        ? "COMPLETE AND CURRENT" : "INCOMPLETE / UNVERIFIED"),
                RseLdUiComponents.liveRow("EVENTS","mode / calibration / reference switches",()->
                        m.modeSwitches()+" / "+m.calibrationSwitches()+" / "+m.referenceSwitches()),
                RseLdUiComponents.liveRow("EDGES","rising / falling",()->m.rising()+" / "+m.falling()),
                RseLdUiComponents.liveRow("TRIAL","baseline / candidate",()->(m.trialBaselineSequence()>0
                        ? "#"+m.trialBaselineSequence() : "NONE")+" / "+(m.trialCandidateSequence()>0
                        ? "#"+m.trialCandidateSequence() : "NONE")),
                RseLdUiComponents.note("History statistics are raw; clipping-aware calibrated residuals require a qualified server trial."),
                RseLdUiComponents.note("An empty or partially invalid rolling window is not evidence of a stable zero.")
        );
        return p;
    }

    private static UIElement trialPanel(SignalAnalyzerMenu m){
        var p=new UIElement().addClass("panel_bg"); p.layout(l->l.paddingAll(5).gapAll(4));
        p.addChildren(
                new Label().setText("PIONEER WORKFLOW • INTERNAL REFERENCE CALIBRATION TRIAL"),
                new UIElement().layout(l->l.flexDirection(YogaFlexDirection.ROW).gapAll(6)).addChildren(
                        RseLdUiComponents.serverAction("Trial baseline",m::captureTrialBaseline),
                        RseLdUiComponents.serverAction("Trial candidate",m::captureTrialCandidate),
                        RseLdUiComponents.serverAction("Clear trial",m::clearTrial)
                ),
                RseLdUiComponents.liveRow("TRIAL","baseline",()->m.trialBaselineSequence()>0?"#"+m.trialBaselineSequence():"NOT CAPTURED"),
                RseLdUiComponents.liveRow("TRIAL","candidate",()->m.trialCandidateSequence()>0?"#"+m.trialCandidateSequence():"NOT CAPTURED"),
                RseLdUiComponents.liveRow("EVIDENCE","trend",()->trialReady(m)?trialName(m.trialTrend()):"NOT READY"),
                RseLdUiComponents.liveRow("DELTA","|error| / clip",()->trialReady(m)
                        ?decimal100(m.trialErrorDelta100())+" / "+signed(m.trialClippingDelta()):"NOT READY"),
                RseLdUiComponents.liveRow("DELTA","span / meanStep",()->trialReady(m)
                        ?signed(m.trialSpanDelta())+" / "+decimal100(m.trialMeanStepDelta100()):"NOT READY"),
                RseLdUiComponents.liveRow("DELTA","calibration",()->trialReady(m)
                        ?signed(m.trialCalibrationDelta()):"NOT READY"),
                RseLdUiComponents.note("Internal RSE reference comparison only; this does not establish external metrological traceability.")
        );
        return p;
    }

    private static UIElement controlRow(String symbol,String range,TextField field){
        var r=new UIElement(); r.layout(l->l.flexDirection(YogaFlexDirection.ROW).flexWrap(dev.vfyjxf.taffy.style.FlexWrap.WRAP).gapAll(6));
        r.addChildren(new Label().setText("ADJUSTABLE").layout(l->l.width(82)),
                new Label().setText(symbol).layout(l->l.width(62)),field,
                new Label().setText(range).layout(l->l.flex(1)));
        return r;
    }

    private static TextField field(int min,int max,java.util.function.IntSupplier getter,java.util.function.IntPredicate setter){
        var f=new TextField().setNumbersOnlyInt(min,max); f.layout(l->l.width(90));
        f.bind(DataBindingBuilder.string(()->Integer.toString(getter.getAsInt()),v->{try{setter.test(Integer.parseInt(v));}catch(NumberFormatException ignored){}}).build());
        return f;
    }

    private static boolean completeValidWindow(SignalAnalyzerMenu m){
        return m.snapshotReady() && m.windowCount()>0 && m.validWindowCount()==m.windowCount()
                && m.sampleAgeTicks()>=0 && m.sampleAgeTicks()<=4
                && m.measurementQuality()==dev.redstoneengineering.core.port.PortQuality.VALID;
    }

    private static boolean trialReady(SignalAnalyzerMenu m){
        return m.trialBaselineSequence()>0 && m.trialCandidateSequence()>0 && m.trialTrend()!=null;
    }

    private static String stabilityClass(SignalAnalyzerMenu m){
        // A recorded zero is only steady when the window contains valid samples.
        // Empty/default evidence must never be interpreted as zero variation.
        if(!completeValidWindow(m))
            return "UNVERIFIED • "+m.validWindowCount()+"/"+m.windowCount()
                    +" valid • "+m.measurementQuality().name();
        if(m.windowCount()<4 || m.validWindowCount()<4) return "WARMUP";
        if(m.peakToPeak()==0 && m.meanStep100()==0) return "STEADY";
        if(m.peakToPeak()<=1 && m.meanStep100()<=50) return "STABLE";
        if(m.peakToPeak()<=5 && m.meanStep100()<=200) return "DYNAMIC";
        return "HIGH VARIATION";
    }

    private static String modeName(int mode){return mode==SignalAnalyzerBlock.INLINE?"INLINE":"TAP";}
    private static String decimal100(int v){int a=Math.abs(v);return (v<0?"-":"")+(a/100)+"."+String.format(Locale.ROOT,"%02d",a%100);}
    private static String signed(int v){return v>0?"+"+v:Integer.toString(v);}
    private static String trialName(SignalCalibrationTrialComparison.Trend t){return t==null?"NOT READY":t.name();}
}
