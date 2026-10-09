package dev.redstoneengineering.ui.ldlib;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.redstoneengineering.ui.menu.RadioLinkMenu;
import net.minecraft.world.entity.player.Player;

public final class RadioLinkLdUi {
    private RadioLinkLdUi() {}

    public static ModularUI create(RadioLinkMenu m, Player player) {
        var root=new UIElement().addClass("panel_bg");
        root.layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(8).gapAll(6));
        root.addChildren(
                RseLdUiComponents.title(m.kind()==RadioLinkMenu.KIND_TRANSMITTER?"RADIO TRANSMITTER":"RADIO RECEIVER"),
                RseLdUiComponents.tabbedWorkspace(
                        630, 400, 850,
                        new String[]{"Overview", "Configure", "Diagnostics", "Evidence"},
                        new UIElement[]{
                                RseLdUiComponents.workspacePage(
                                        new Label().setText("PIONEER PATTERN • RADIO LINK BUDGET"),
                                        RseLdUiComponents.formulaCard(()->"M_decode = Q_link - Q_min; decode ⇔ coverage ∧ one driver ∧ M_decode ≥ 0; availability = 100 · validSamples / samples"),
                                         RseLdUiComponents.liveRow("EVIDENCE","snapshot",()->m.snapshotReady()
                                                 ? "SYNCED • radio source/reception inspected"
                                                 : "NOT READY • awaiting radio inspection"),
                                         RseLdUiComponents.note("Current reception evidence and retained receiver-tick counters are separate; a fresh reception does not invent historical samples.")
                                ),
                                RseLdUiComponents.workspacePage(
                                        channelControl(m),
                                        RseLdUiComponents.liveRow("MEASURED","payload",()->m.snapshotReady()
                                                 && m.quality()==dev.redstoneengineering.core.port.PortQuality.VALID
                                                ? Integer.toString(m.payload()) : "NOT READY • "+m.quality().name())
                                ),
                                RseLdUiComponents.workspacePage(
                                        RseLdUiComponents.liveRow("EVIDENCE","quality",()->m.snapshotReady()
                                                 ? m.quality().name() : "NOT READY • awaiting radio quality"),
                                        receiverEvidence(m)
                                ),
                                RseLdUiComponents.workspacePage(
                                        RseLdUiComponents.authorityFooter()
                                )
                        }
                )
        );
        return RseLdUiComponents.responsiveUi(root, player, 650, 440);
    }

    private static UIElement channelControl(RadioLinkMenu m){
        var p=new UIElement().addClass("panel_bg"); p.layout(l->l.paddingAll(5).gapAll(4));
        var f=new TextField().setNumbersOnlyInt(0,3); f.layout(l->l.width(100));
        f.bind(DataBindingBuilder.string(()->m.snapshotReady()?Integer.toString(m.channel()):"",v->{try{m.setChannelFromUi(Integer.parseInt(v));}catch(NumberFormatException ignored){}}).build());
        p.addChildren(
                RseLdUiComponents.liveRow("ADJUSTABLE","CH",()->m.snapshotReady()
                                                 ? m.channel()+" • 0..3" : "NOT READY • channel awaiting server"),
                f
        );
        if(m.kind()==RadioLinkMenu.KIND_RECEIVER) p.addChild(RseLdUiComponents.serverAction("Cycle output direction ▶",m::cycleOutputForward));
        return p;
    }

    private static UIElement receiverEvidence(RadioLinkMenu m){
        var p=new UIElement().addClass("panel_bg"); p.layout(l->l.paddingAll(5).gapAll(3));
        if(m.kind()==RadioLinkMenu.KIND_TRANSMITTER){
            p.addChildren(
                    RseLdUiComponents.liveRow("TX","drivers",()->m.snapshotReady()
                                                 ? Integer.toString(m.drivers()) : "NOT READY • no server TX inspection"),
                    RseLdUiComponents.fixedRow("range",()->RadioLinkMenu.RANGE_BLOCKS+" blocks","radio kernel limit")
            );
        }else{
            p.addChildren(
                    RseLdUiComponents.liveRow("RX","output",()->m.snapshotReady()
                                                 && m.quality()==dev.redstoneengineering.core.port.PortQuality.VALID
                                                 ? m.output()+" / 15" : "UNVERIFIED • decode not proven"),
                    RseLdUiComponents.liveRow("LINK","quality",()->!m.snapshotReady()
                            ? "NOT READY • awaiting current reception"
                            : m.quality()==dev.redstoneengineering.core.port.PortQuality.VALID
                                    ? m.linkQuality()+" • margin="+m.decodeMargin()+" • CURRENT"
                                    : "UNVERIFIED • "+m.quality().name()+" • margin="+m.decodeMargin()),
                    RseLdUiComponents.liveRow("LINK","distance",()->m.snapshotReady() && m.coverageComplete()
                            && m.quality()==dev.redstoneengineering.core.port.PortQuality.VALID
                            ? m.distanceBlocks()+" blocks • latency="+m.latency()+"t"
                            : "UNVERIFIED • source path not established"),
                    RseLdUiComponents.liveRow("INTERFERENCE","environment",()->m.snapshotReady()
                            ? "aggressors="+m.adjacentAggressors()+" • obstacles="+m.obstacleHits()+" • noise="+m.noise()+"%"
                            : "NOT READY • current environment unsynchronized"),
                    RseLdUiComponents.liveRow("HISTORY","availability",()->m.snapshotReady() && m.samples()>0
                            ? m.availabilityPercent()+"% • valid="+m.validSamples()+"/"+m.samples()
                            : "NOT READY • no receiver observations"),
                    RseLdUiComponents.liveRow("HISTORY","faults",()->m.snapshotReady() && m.samples()>0
                            ? "collision="+m.collisions()+" • dropout="+m.dropouts()+" • handoff="+m.handoffs()
                            : "NOT READY • no retained receiver observations")
            );
        }
        p.addChildren(
                RseLdUiComponents.liveRow("DIAGNOSIS","link",()->diagnosis(m)),
                RseLdUiComponents.liveRow("NEXT","action",()->nextAction(m)),
                RseLdUiComponents.fixedRow("history",()->"receiver-tick counters",
                        "Counters are receiver-tick evidence; the client does not fabricate packet history."),
                RseLdUiComponents.fixedRow("payload 0",()->"VALID ONLY WITH SOURCE EVIDENCE",
                        "Payload 0 is a valid frame when source evidence is VALID.")
        );
        return p;
    }

    private static String diagnosis(RadioLinkMenu m){
        if(!m.snapshotReady()) return "NOT READY • AWAITING SERVER RADIO SNAPSHOT";
        if(m.kind()==RadioLinkMenu.KIND_TRANSMITTER) return m.quality().name();
        // Diagnose the current server reception independently of the
        // retained receiver-tick counters; empty history is not a lost frame.
        if(m.collision()) return "SAME-CHANNEL COLLISION";
        if(!m.coverageComplete()) return "STALE / INCOMPLETE COVERAGE";
        if(m.quality()==dev.redstoneengineering.core.port.PortQuality.NO_SIGNAL)
            return "NO RADIO FRAME / SOURCE";
        if(m.quality()!=dev.redstoneengineering.core.port.PortQuality.VALID)
            return "UNVERIFIED RADIO INPUT • "+m.quality().name();
        if(m.decodeMargin()<0) return "BELOW DECODE MARGIN";
        if(m.decodeMargin()<10) return "MARGINAL LINK";
        if(m.adjacentAggressors()>0) return "VALID • ADJACENT INTERFERENCE";
        if(m.obstacleHits()>0) return "VALID • OBSTRUCTED PATH";
        return "HEALTHY LINK";
    }

    private static String nextAction(RadioLinkMenu m){
        String d=diagnosis(m);
        if(d.contains("COLLISION")) return "NEXT • move one same-channel transmitter or change one channel.";
        if(d.contains("ADJACENT")) return "NEXT • separate adjacent channels first; then re-check margin.";
        if(d.contains("NO RADIO FRAME")) return "NEXT • check transmitter activation, matching channel and loaded radio coverage.";
        if(d.contains("NOT READY") || d.contains("UNVERIFIED") || d.contains("STALE"))
            return "NEXT • resolve input quality/coverage before accepting any link as healthy.";
        if(d.contains("OBSTRUCTED") || d.contains("MARGIN"))
            return "NEXT • improve line-of-sight or shorten the path before accepting the link.";
        return "NEXT • retain this healthy link as commissioning evidence.";
    }
}
