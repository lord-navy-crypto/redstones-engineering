package dev.redstoneengineering.ui.ldlib;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.redstoneengineering.ui.menu.MagneticSystemMenu;
import net.minecraft.world.entity.player.Player;
import org.appliedenergistics.yoga.YogaFlexDirection;

public final class MagneticSystemLdUi {
    private MagneticSystemLdUi() {}

    public static ModularUI create(MagneticSystemMenu m, Player player) {
        var root=new UIElement().addClass("panel_bg");
        root.layout(l->l.width(580).paddingAll(8).gapAll(6));
        root.addChildren(
                RseLdUiComponents.title(deviceName(m)),
                RseLdUiComponents.title("PIONEER PATTERN • MAGNETIC MODEL"),
                RseLdUiComponents.formulaCard(()->equation(m)),
                overview(m),
                controls(m),
                evidence(m),
                RseLdUiComponents.authorityFooter()
        );
        return ModularUI.of(UI.of(root,StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.GDP)),player);
    }

    private static UIElement overview(MagneticSystemMenu m){
        var p=new UIElement().addClass("panel_bg");p.layout(l->l.paddingAll(5).gapAll(3));
        switch(m.kind()){
            case MagneticSystemMenu.KIND_ELECTROMAGNET -> p.addChildren(
                    RseLdUiComponents.liveRow("MEASURED","V_coil",()->m.secondary()+" / 15"),
                    RseLdUiComponents.liveRow("DERIVED","S_field",()->m.primary()+" / 15"),
                    RseLdUiComponents.liveRow("EVIDENCE","feeds",()->Integer.toString(m.tertiary())));
            case MagneticSystemMenu.KIND_PERMANENT -> p.addChildren(
                    RseLdUiComponents.liveRow("ADJUSTABLE","S",()->m.primary()+" / 15"),
                    RseLdUiComponents.liveRow("STATE","N marker",()->m.facing().getName().toUpperCase()));
            case MagneticSystemMenu.KIND_COIL -> p.addChildren(
                    RseLdUiComponents.liveRow("MEASURED","B",()->m.primary()+" / 15"),
                    RseLdUiComponents.liveRow("ADJUSTABLE","N",()->Integer.toString(m.tertiary())),
                    RseLdUiComponents.liveRow("DERIVED","V_ind",()->m.secondary()+" / 15"));
            case MagneticSystemMenu.KIND_FIELD_SENSOR -> p.addChildren(
                    RseLdUiComponents.liveRow("MEASURED","B",()->m.primary()+" / 15"),
                    RseLdUiComponents.liveRow("EVIDENCE","coverage",()->m.secondary()+" / "+m.tertiary()));
            default -> p.addChildren(
                    RseLdUiComponents.liveRow("MEASURED","Gx/Gy/Gz",()->m.primary()+" / "+m.secondary()+" / "+m.tertiary()),
                    RseLdUiComponents.liveRow("MEASURED","B_local",()->m.auxiliary()+" / 15"));
        }
        return p;
    }

    private static UIElement controls(MagneticSystemMenu m){
        var p=new UIElement().addClass("panel_bg");p.layout(l->l.paddingAll(5).gapAll(4));
        if(m.kind()==MagneticSystemMenu.KIND_PERMANENT||m.kind()==MagneticSystemMenu.KIND_COIL){
            int max=m.kind()==MagneticSystemMenu.KIND_PERMANENT?15:4;
            var f=new TextField().setNumbersOnlyInt(1,max); f.layout(l->l.width(110));
            f.bind(DataBindingBuilder.string(
                    ()->Integer.toString(m.kind()==MagneticSystemMenu.KIND_PERMANENT?m.primary():m.tertiary()),
                    v->{try{m.setPrimaryFromUi(Integer.parseInt(v));}catch(NumberFormatException ignored){}}
            ).build());
            p.addChildren(
                    RseLdUiComponents.liveRow("ADJUSTABLE",m.kind()==MagneticSystemMenu.KIND_PERMANENT?"S":"N",()->m.kind()==MagneticSystemMenu.KIND_PERMANENT?m.primary()+" • 1..15":m.tertiary()+" • 1..4"),
                    f
            );
        }else{
            p.addChild(RseLdUiComponents.fixedRow("control",()->"NONE","observer/external actuator owns no local tuning coefficient"));
        }
        if(m.kind()==MagneticSystemMenu.KIND_PERMANENT){
            p.addChild(RseLdUiComponents.serverAction("Cycle N marker ▶",m::cycleOrientationForward));
        }else if(m.kind()==MagneticSystemMenu.KIND_COIL){
            p.addChild(new UIElement().layout(l->l.flexDirection(YogaFlexDirection.ROW).gapAll(6)).addChildren(
                    RseLdUiComponents.serverAction("Cycle RX ▶",m::cycleInputForward),
                    RseLdUiComponents.serverAction("Cycle TX ▶",m::cycleOutputForward)
            ));
        }
        return p;
    }

    private static UIElement evidence(MagneticSystemMenu m){
        return new UIElement().addClass("panel_bg").layout(l->l.paddingAll(5).gapAll(3)).addChildren(
                RseLdUiComponents.liveRow("EVIDENCE","quality",()->m.quality().name()),
                RseLdUiComponents.liveRow("EVIDENCE","coverage",()->m.complete()?"COMPLETE":"INCOMPLETE / STALE"),
                RseLdUiComponents.fixedRow("field law",()->"bounded inverse-square-style accumulation","server magnetic model")
        );
    }

    private static String deviceName(MagneticSystemMenu m){return switch(m.kind()){case MagneticSystemMenu.KIND_ELECTROMAGNET->"ELECTROMAGNET";case MagneticSystemMenu.KIND_PERMANENT->"PERMANENT MAGNET";case MagneticSystemMenu.KIND_COIL->"INDUCTION COIL";case MagneticSystemMenu.KIND_FIELD_SENSOR->"MAGNETIC FIELD SENSOR";default->"MAGNETIC GRADIENT METER";};}
    private static String equation(MagneticSystemMenu m){return switch(m.kind()){case MagneticSystemMenu.KIND_ELECTROMAGNET->"S_field = valid(Copper) ? V_coil : 0";case MagneticSystemMenu.KIND_COIL->"V_ind = clamp(N · |B[n] - B[n-1]|, 0, 15)";case MagneticSystemMenu.KIND_GRADIENT->"G = spatial ΔB from bounded field samples";default->"B = clamp(round(Σ S_i / max(1,r_i²)),0,15)";};}
}
