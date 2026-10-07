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
        root.layout(l->l.width(640).paddingAll(8).gapAll(6));
        root.addChildren(
                RseLdUiComponents.title("SIGNAL ANALYZER • METROLOGY / CALIBRATION"),
                RseLdUiComponents.formulaCard(()->"x_cal = clamp(x_raw + b_cal, 0, 15) ; e_ref = mean(clamp(x_raw + b_cal,0,15)) - x_ref"),
                new SignalAnalyzerPlotElement(m),
                measurementPanel(m),
                controlPanel(m),
                statisticsPanel(m),
                trialPanel(m),
                RseLdUiComponents.authorityFooter()
        );
        return ModularUI.of(UI.of(root,StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.GDP)),player);
    }

    private static UIElement measurementPanel(SignalAnalyzerMenu m){
        var p=new UIElement().addClass("panel_bg"); p.layout(l->l.paddingAll(5).gapAll(3));
        p.addChildren(
                new Label().setText("PIONEER PATTERN • METROLOGY / CALIBRATION"),
                RseLdUiComponents.liveRow("STATE","mode",()->modeName(m.mode())),
                RseLdUiComponents.liveRow("MODEL","boundary",()->m.mode()==SignalAnalyzerBlock.TAP
                        ? "TAP • NON-INVASIVE • no output drive"
                        : "INLINE • explicit two-port boundary"),
                RseLdUiComponents.liveRow("MEASURED","x_raw",()->m.raw()+" / 15"),
                RseLdUiComponents.liveRow("DERIVED","x_cal",()->m.calibrated()+" / 15"),
                RseLdUiComponents.liveRow("OUTPUT","inline",()->m.output()+" / 15 • raw pass-through semantics"),
                RseLdUiComponents.liveRow("EVIDENCE","measurement",()->m.measurementQuality().name()+" • coverage="+m.coveragePercent()+"%"),
                new Label().setText("Calibration is DISPLAY ONLY • INLINE output remains RAW."),
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
                new UIElement().layout(l->l.flexDirection(YogaFlexDirection.ROW).gapAll(6)).addChildren(
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
                RseLdUiComponents.liveRow("WINDOW","avg / p2p",()->decimal100(m.average100())+" / "+m.peakToPeak()),
                RseLdUiComponents.liveRow("WINDOW","meanStep",()->decimal100(m.meanStep100())),
                RseLdUiComponents.liveRow("WINDOW","measurement coverage",()->"measurement coverage="+m.validWindowCount()+"/"+m.windowCount()+" • "+m.coveragePercent()+"%"),
                RseLdUiComponents.liveRow("SYNC","age",()->m.sampleAgeTicks()<0?"NO SAMPLE":m.sampleAgeTicks()+" ticks"),
                RseLdUiComponents.liveRow("LIFETIME","min / max",()->m.lifeMin()+" / "+m.lifeMax()),
                RseLdUiComponents.liveRow("LIFETIME","changes / edges",()->m.changes()+" • ↑"+m.rising()+" ↓"+m.falling()),
                RseLdUiComponents.liveRow("LIFETIME","last / max Δ",()->m.lastDelta()+" / "+m.maxDelta()),
                RseLdUiComponents.liveRow("STATE","stable age",()->m.stableAgeTicks()+" ticks"),
                RseLdUiComponents.liveRow("STATE","variation",()->stabilityClass(m)),
                new Label().setText("Rolling statistics are synchronized server evidence; numeric zero remains distinct from missing evidence.")
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
                RseLdUiComponents.liveRow("EVIDENCE","trend",()->trialName(m.trialTrend())),
                RseLdUiComponents.liveRow("DELTA","|error| / clip",()->decimal100(m.trialErrorDelta100())+" / "+signed(m.trialClippingDelta())),
                RseLdUiComponents.liveRow("DELTA","span / meanStep",()->signed(m.trialSpanDelta())+" / "+decimal100(m.trialMeanStepDelta100())),
                RseLdUiComponents.liveRow("DELTA","calibration",()->signed(m.trialCalibrationDelta())),
                new Label().setText("Internal RSE reference comparison only; this does not establish external metrological traceability.")
        );
        return p;
    }

    private static UIElement controlRow(String symbol,String range,TextField field){
        var r=new UIElement(); r.layout(l->l.flexDirection(YogaFlexDirection.ROW).gapAll(6));
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

    private static String stabilityClass(SignalAnalyzerMenu m){
        if(m.windowCount()<4) return "WARMUP";
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
