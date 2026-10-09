package dev.redstoneengineering.ui.ldlib;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.redstoneengineering.block.SignalProbeBlock;
import dev.redstoneengineering.blockentity.LogicAnalyzerBlockEntity;
import dev.redstoneengineering.ui.menu.LogicAnalyzerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import dev.vfyjxf.taffy.style.FlexWrap;
import org.appliedenergistics.yoga.YogaFlexDirection;

public final class LogicAnalyzerLdUi {
    private LogicAnalyzerLdUi(){}

    public static ModularUI create(LogicAnalyzerMenu menu, Player player) {
        var root = new UIElement().addClass("panel_bg");
        root.layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(10).gapAll(8));

        var capture = page(
                RseLdUiComponents.formulaCard(() -> "D_ch[n] = (x_ch[n] ≥ T) ? HIGH : LOW"),
                new LogicAnalyzerPlotElement(menu),
                timingPanel(menu)
        );
        var configure = page(controls(menu));
        var channels = page(channelPanel(menu));
        var network = page(networkPanel(menu));
        var authority = page(RseLdUiComponents.authorityFooter());
        configure.setDisplay(false);
        channels.setDisplay(false);
        network.setDisplay(false);
        authority.setDisplay(false);

        var workspace = new ScrollerView().scrollerStyle(style -> style
                .mode(ScrollerMode.BOTH)
                .verticalScrollDisplay(ScrollDisplay.AUTO)
                .horizontalScrollDisplay(ScrollDisplay.AUTO)
                .minScrollPixel(8).maxScrollPixel(72));
        workspace.layout(l -> l.flex(1));
        workspace.viewPort(view -> view.layout(l -> l.paddingAll(8)));
        workspace.viewContainer(view -> view.layout(l -> l.width(870).paddingAll(8).gapAll(8)));
        workspace.addScrollViewChildren(capture, configure, channels, network, authority);

        var tabs = new UIElement().addClass("panel_bg");
        tabs.layout(l -> l.flexDirection(YogaFlexDirection.ROW).flexWrap(FlexWrap.WRAP).gapAll(5).paddingAll(5));
        tabs.addChildren(
                tabButton("Capture", workspace, capture, capture, configure, channels, network, authority),
                tabButton("Configure", workspace, configure, capture, configure, channels, network, authority),
                tabButton("Channels", workspace, channels, capture, configure, channels, network, authority),
                tabButton("Network", workspace, network, capture, configure, channels, network, authority),
                tabButton("Authority", workspace, authority, capture, configure, channels, network, authority)
        );

        root.addChildren(
                RseLdUiComponents.title("FOUR-CHANNEL LOGIC ANALYZER"),
                tabs,
                new Label().setText("SCROLL • wheel Y • Shift+wheel X"),
                workspace
        );
        return RseLdUiComponents.responsiveUi(root, player, 640, 440);
    }

    private static UIElement page(UIElement... children) {
        return new UIElement().layout(l -> l.width(840).paddingAll(12).gapAll(10)).addChildren(children);
    }

    private static Button tabButton(String label, ScrollerView workspace, UIElement selected, UIElement... pages) {
        return new Button().setText(label).setOnClick(event -> {
            for (UIElement page : pages) page.setDisplay(page == selected);
            workspace.horizontalScroller.setNormalizedValue(0);
            workspace.verticalScroller.setNormalizedValue(0);
        });
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
                RseLdUiComponents.liveRow("EVIDENCE","Capture",()->m.sampleCount()+"/32 • coverage="+captureCoverage(m)+"%"),
                RseLdUiComponents.liveRow("STATE","trigger / buffer",()->captureState(m.captureState())
                        +" • "+(m.bounded()?"bounded probes":"incomplete network scan")),
                RseLdUiComponents.liveRow("EVIDENCE","connected/valid channels",()->m.activeChannels()+" / "+m.validChannels()),
                RseLdUiComponents.liveRow("EVIDENCE","duplicate assignments",()->Integer.toString(m.duplicateChannels())),
                RseLdUiComponents.liveRow("DERIVED","Cursor Δ",()->m.sampleCount()>0
                        ? Math.abs(m.cursorB()-m.cursorA())+" samples / "
                            +Math.abs(m.cursorB()-m.cursorA())*LogicAnalyzerBlockEntity.SAMPLE_PERIOD_TICKS+" ticks"
                        : "NOT READY • capture waveform first"),
                new Label().setText("Threshold/cursors/trigger remain server-authoritative; retained capture evidence is synchronized only.")
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
        p.addChild(RseLdUiComponents.liveRow("EVIDENCE","network scan",
                ()->m.bounded()?"COMPLETE":"UNVERIFIED • INCOMPLETE"));
        for(int ch=0;ch<4;ch++){
            final int c=ch;
            p.addChild(new Label().setText("CHANNEL "+channelName(c)+" • EDGE TIMING"));
            p.addChild(RseLdUiComponents.liveRow("PORT","probe count",
                    ()->Integer.toString(m.probeCount(c))));
            p.addChild(RseLdUiComponents.liveRow("MEASURED","HIGH duty / transitions",
                    ()->m.coverage(c)>0?m.duty(c)+"% / "+m.transitionRate(c)+"%":"NOT READY"));
            p.addChild(RseLdUiComponents.liveRow("EVENTS","rising / falling",
                    ()->m.coverage(c)>0?m.rising(c)+" / "+m.falling(c):"NOT READY"));
            p.addChild(RseLdUiComponents.liveRow("EVIDENCE","valid coverage",
                    ()->m.coverage(c)+"%"+(m.probeCount(c)==0?" • NO PROBE":"")));
        }
        return p;
    }

    private static UIElement networkPanel(LogicAnalyzerMenu m){
        var p=new UIElement().addClass("panel_bg"); p.layout(l->l.paddingAll(5).gapAll(3));
        p.addChildren(
                RseLdUiComponents.liveRow("NETWORK","nodes",()->"cable="+m.cableNodes()+" • probes="+m.probeNodes()),
                RseLdUiComponents.liveRow("NETWORK","channels",()->"valid="+m.validChannels()+" • active="+m.activeChannels()+" • duplicate="+m.duplicateChannels()),
                RseLdUiComponents.liveRow("EVIDENCE","topology complete",
                        ()->m.bounded()?"BOUNDED":"INCOMPLETE • NOT VERIFIED"),
                RseLdUiComponents.liveRow("SHIELDING","shielded/unshielded cables",
                        ()->m.shieldedCableNodes()+" / "+m.unshieldedCableNodes()),
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

    private static String captureState(int state){
        return switch(state){case 1->"ARMED";case 2->"TRIGGERED";default->"HOLD";}
    }

    private static int captureCoverage(LogicAnalyzerMenu m){
        int total=0;
        for(int c=0;c<4;c++) total+=m.coverage(c);
        return total/4;
    }
    private static String channelName(int c){return SignalProbeBlock.channelName(c);}
    private static String edgeName(int e){return e==2?"FALLING":"RISING";}
}
