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
        root.layout(l->l.width(650).height(440).paddingAll(8).gapAll(6));
        root.addChildren(
                RseLdUiComponents.title(m.kind()==RadioLinkMenu.KIND_TRANSMITTER?"RADIO TRANSMITTER":"RADIO RECEIVER"),
                RseLdUiComponents.tabbedWorkspace(
                        630, 400, 850,
                        new String[]{"Overview", "Configure", "Diagnostics", "Evidence"},
                        new UIElement[]{
                                RseLdUiComponents.workspacePage(
                                        new Label().setText("PIONEER PATTERN • RADIO LINK BUDGET"),
                                        RseLdUiComponents.formulaCard(()->"M_decode = Q_link - Q_min; decode ⇔ coverage ∧ one driver ∧ M_decode ≥ 0; availability = 100 · validSamples / samples")
                                ),
                                RseLdUiComponents.workspacePage(
                                        channelControl(m),
                                        RseLdUiComponents.liveRow("MEASURED","payload",()->Integer.toString(m.payload()))
                                ),
                                RseLdUiComponents.workspacePage(
                                        RseLdUiComponents.liveRow("EVIDENCE","quality",()->m.quality().name()),
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
        f.bind(DataBindingBuilder.string(()->Integer.toString(m.channel()),v->{try{m.setChannelFromUi(Integer.parseInt(v));}catch(NumberFormatException ignored){}}).build());
        p.addChildren(
                RseLdUiComponents.liveRow("ADJUSTABLE","CH",()->m.channel()+" • 0..3"),
                f
        );
        if(m.kind()==RadioLinkMenu.KIND_RECEIVER) p.addChild(RseLdUiComponents.serverAction("Cycle output direction ▶",m::cycleOutputForward));
        return p;
    }

    private static UIElement receiverEvidence(RadioLinkMenu m){
        var p=new UIElement().addClass("panel_bg"); p.layout(l->l.paddingAll(5).gapAll(3));
        if(m.kind()==RadioLinkMenu.KIND_TRANSMITTER){
            p.addChildren(
                    RseLdUiComponents.liveRow("TX","drivers",()->Integer.toString(m.drivers())),
                    RseLdUiComponents.fixedRow("range",()->RadioLinkMenu.RANGE_BLOCKS+" blocks","radio kernel limit")
            );
        }else{
            p.addChildren(
                    RseLdUiComponents.liveRow("RX","output",()->m.output()+" / 15"),
                    RseLdUiComponents.liveRow("LINK","quality",()->m.linkQuality()+" • margin="+m.decodeMargin()),
                    RseLdUiComponents.liveRow("LINK","distance",()->m.distanceBlocks()+" blocks • latency="+m.latency()+"t"),
                    RseLdUiComponents.liveRow("INTERFERENCE","environment",()->"aggressors="+m.adjacentAggressors()+" • obstacles="+m.obstacleHits()+" • noise="+m.noise()+"%"),
                    RseLdUiComponents.liveRow("HISTORY","availability",()->m.availabilityPercent()+"% • samples="+m.samples()),
                    RseLdUiComponents.liveRow("HISTORY","faults",()->"collision="+m.collisions()+" • dropout="+m.dropouts()+" • handoff="+m.handoffs())
            );
        }
        p.addChildren(
                RseLdUiComponents.liveRow("DIAGNOSIS","link",()->diagnosis(m)),
                RseLdUiComponents.liveRow("NEXT","action",()->nextAction(m)),
                RseLdUiComponents.fixedRow("history",()->"receiver-tick counters",
                        "Counters are receiver-tick evidence; the client does not fabricate packet history."),
                RseLdUiComponents.fixedRow("payload 0",()->"VALID DATA",
                        "Payload 0 is a valid frame when source evidence is VALID.")
        );
        return p;
    }

    private static String diagnosis(RadioLinkMenu m){
        if(m.kind()==RadioLinkMenu.KIND_TRANSMITTER) return m.quality().name();
        if(m.collision()) return "SAME-CHANNEL COLLISION";
        if(!m.coverageComplete()) return "STALE / INCOMPLETE COVERAGE";
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
        if(d.contains("OBSTRUCTED") || d.contains("MARGIN") || d.contains("STALE"))
            return "NEXT • improve line-of-sight or shorten the path before accepting the link.";
        return "NEXT • retain this healthy link as commissioning evidence.";
    }
}
