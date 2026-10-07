package dev.redstoneengineering.ui.ldlib;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.redstoneengineering.block.SignalProbeBlock;
import dev.redstoneengineering.blockentity.LogicAnalyzerBlockEntity;
import dev.redstoneengineering.ui.menu.LogicAnalyzerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.appliedenergistics.yoga.YogaFlexDirection;

public final class LogicAnalyzerLdUi {
    private LogicAnalyzerLdUi(){}

    public static ModularUI create(LogicAnalyzerMenu menu, Player player){
        var root=new UIElement().addClass("panel_bg");
        root.layout(l->l.width(620).paddingAll(8).gapAll(6));
        root.addChildren(
                RseLdUiComponents.title("FOUR-CHANNEL LOGIC ANALYZER"),
                RseLdUiComponents.formulaCard(()->"D_ch[n] = (x_ch[n] ≥ T) ? HIGH : LOW"),
                new LogicAnalyzerPlotElement(menu),
                timingPanel(menu),
                controls(menu),
                channelPanel(menu),
                networkPanel(menu),
                RseLdUiComponents.authorityFooter()
        );
        return ModularUI.of(UI.of(root,StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.GDP)),player);
    }

    private static UIElement timingPanel(LogicAnalyzerMenu m){
        var p=new UIElement().addClass("panel_bg"); p.layout(l->l.paddingAll(5).gapAll(3));
        p.addChildren(
                new Label().setText("PIONEER PATTERN • DIGITAL TIMING MODEL"),
                RseLdUiComponents.liveRow("MEASURED","x_ch[n]",()->"instrument bus sample • Redstone 0..15"),
                RseLdUiComponents.liveRow("ADJUSTABLE","T",()->m.threshold()+" • 1..15"),
                RseLdUiComponents.fixedRow("Δt_sample",()->Integer.toString(LogicAnalyzerBlockEntity.SAMPLE_PERIOD_TICKS),"tick"),
                RseLdUiComponents.liveRow("ADJUSTABLE","trigger",()->"CH "+channelName(m.triggerChannel())+" "+edgeName(m.triggerEdge())),
                RseLdUiComponents.liveRow("ADJUSTABLE","cursor A/B",()->m.cursorA()+" / "+m.cursorB()),
                RseLdUiComponents.liveRow("DERIVED","Δt_cursor",()->Math.abs(m.cursorB()-m.cursorA())*LogicAnalyzerBlockEntity.SAMPLE_PERIOD_TICKS+" ticks"),
                RseLdUiComponents.liveRow("EVIDENCE","capture",()->m.sampleCount()+"/32 • "+captureCoverage(m)+"%")
        );
        return p;
    }

    private static UIElement controls(LogicAnalyzerMenu m){
        var t=field(1,15,m::threshold,m::setThresholdFromUi);
        var a=field(0,15,m::cursorA,m::setCursorAFromUi);
        var b=field(0,15,m::cursorB,m::setCursorBFromUi);
        var p=new UIElement().addClass("panel_bg"); p.layout(l->l.paddingAll(5).gapAll(4));
        p.addChildren(
                row("T 1..15",t), row("cursor A 0..15",a), row("cursor B 0..15",b),
                new UIElement().layout(l->l.flexDirection(YogaFlexDirection.ROW).gapAll(6)).addChildren(
                        RseLdUiComponents.serverAction("Arm / Hold",m::armOrHold),
                        RseLdUiComponents.serverAction("Trigger CH ▶",m::cycleTriggerChannel),
                        RseLdUiComponents.serverAction("Trigger edge ▶",m::cycleTriggerEdge),
                        RseLdUiComponents.serverAction("Clear capture",m::clearCapture)
                )
        );
        return p;
    }

    private static UIElement channelPanel(LogicAnalyzerMenu m){
        var p=new UIElement().addClass("panel_bg"); p.layout(l->l.paddingAll(5).gapAll(3));
        for(int ch=0;ch<4;ch++){
            final int c=ch;
            p.addChild(RseLdUiComponents.liveRow("CHANNEL",channelName(c),()->
                    "coverage="+m.coverage(c)+"% • duty="+m.duty(c)+"% • edges ↑"+m.rising(c)+" ↓"+m.falling(c)+" • probes="+m.probeCount(c)));
        }
        return p;
    }

    private static UIElement networkPanel(LogicAnalyzerMenu m){
        var p=new UIElement().addClass("panel_bg"); p.layout(l->l.paddingAll(5).gapAll(3));
        p.addChildren(
                RseLdUiComponents.liveRow("NETWORK","nodes",()->"cable="+m.cableNodes()+" • probes="+m.probeNodes()),
                RseLdUiComponents.liveRow("NETWORK","channels",()->"valid="+m.validChannels()+" • active="+m.activeChannels()+" • duplicate="+m.duplicateChannels()),
                RseLdUiComponents.liveRow("EVIDENCE","Bus interference",()->"exposure="+m.interferenceExposure()+"% • confidence="+m.interferenceConfidence()+"%"),
                RseLdUiComponents.liveRow("EVIDENCE","shielding",()->m.shieldingCoverage()+"%"),
                RseLdUiComponents.liveRow("NEXT","mitigation",()->m.unshieldedExposedNodes()>0?"shield exposed instrument segments first":"instrument routing evidence coherent")
        );
        return p;
    }

    private static UIElement row(String label, TextField f){
        var r=new UIElement(); r.layout(l->l.flexDirection(YogaFlexDirection.ROW).gapAll(6));
        r.addChildren(new Label().setText(label).layout(l->l.width(120)),f);
        return r;
    }

    private static TextField field(int min,int max,java.util.function.IntSupplier getter,java.util.function.IntPredicate setter){
        var f=new TextField().setNumbersOnlyInt(min,max); f.layout(l->l.width(90));
        f.bind(DataBindingBuilder.string(()->Integer.toString(getter.getAsInt()),v->{try{setter.test(Integer.parseInt(v));}catch(NumberFormatException ignored){}}).build());
        return f;
    }

    private static int captureCoverage(LogicAnalyzerMenu m){
        int total=0;
        for(int c=0;c<4;c++) total+=m.coverage(c);
        return total/4;
    }
    private static String channelName(int c){return SignalProbeBlock.channelName(c);}
    private static String edgeName(int e){return e==2?"FALLING":"RISING";}
}
