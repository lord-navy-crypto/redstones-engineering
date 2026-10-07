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
        root.layout(l->l.width(560).paddingAll(8).gapAll(6));
        root.addChildren(
                RseLdUiComponents.title(m.kind()==RadioLinkMenu.KIND_TRANSMITTER?"RADIO TRANSMITTER":"RADIO RECEIVER"),
                new Label().setText("PIONEER PATTERN • RADIO LINK BUDGET"),
                RseLdUiComponents.formulaCard(()->"M_decode = Q_link - Q_min; decode ⇔ coverage ∧ one driver ∧ M_decode ≥ 0; availability = 100 · validSamples / samples"),
                channelControl(m),
                RseLdUiComponents.liveRow("MEASURED","payload",()->Integer.toString(m.payload())),
                RseLdUiComponents.liveRow("EVIDENCE","quality",()->m.quality().name()),
                receiverEvidence(m),
                RseLdUiComponents.authorityFooter()
        );
        return ModularUI.of(UI.of(root, StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.GDP)),player);
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
        return p;
    }
}
