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
        root.layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(8).gapAll(6));
        root.addChildren(
                RseLdUiComponents.title(deviceName(m)),
                RseLdUiComponents.tabbedWorkspace(
                        600, 400, 850,
                        new String[]{"Overview", "Details", "Evidence"},
                        new UIElement[]{
                                RseLdUiComponents.workspacePage(
                                        RseLdUiComponents.title("PIONEER PATTERN • MAGNETIC MODEL"),
                                        RseLdUiComponents.formulaCard(()->equation(m)),
                                        overview(m)
                                ),
                                RseLdUiComponents.workspacePage(
                                        controls(m),
                                        mechanismPanel(m)
                                ),
                                RseLdUiComponents.workspacePage(
                                        evidence(m),
                                        RseLdUiComponents.authorityFooter()
                                )
                        }
                )
        );
        return RseLdUiComponents.responsiveUi(root, player, 620, 440);
    }

    private static UIElement overview(MagneticSystemMenu m){
        var p=new UIElement().addClass("panel_bg");p.layout(l->l.paddingAll(5).gapAll(3));
        switch(m.kind()){
            case MagneticSystemMenu.KIND_ELECTROMAGNET -> p.addChildren(
                    RseLdUiComponents.liveRow("MEASURED","V_coil",()->m.complete()?m.secondary()+" / 15":"UNVERIFIED • Copper input"),
                    RseLdUiComponents.liveRow("DERIVED","S_field",()->m.complete()?m.primary()+" / 15":"UNVERIFIED • field drive"),
                    RseLdUiComponents.liveRow("EVIDENCE","feeds",()->Integer.toString(m.tertiary())));
            case MagneticSystemMenu.KIND_PERMANENT -> p.addChildren(
                    RseLdUiComponents.liveRow("ADJUSTABLE","S",()->m.primary()+" / 15"),
                    RseLdUiComponents.liveRow("STATE","N marker",()->m.facing().getName().toUpperCase()));
            case MagneticSystemMenu.KIND_COIL -> p.addChildren(
                    RseLdUiComponents.liveRow("MEASURED","B",()->m.complete()?m.primary()+" / 15":"NOT READY • field input unverified"),
                    RseLdUiComponents.liveRow("ADJUSTABLE","N",()->Integer.toString(m.tertiary())),
                    RseLdUiComponents.liveRow("DERIVED","V_ind",()->m.complete()?m.secondary()+" / 15":"UNVERIFIED • coil input"));
            case MagneticSystemMenu.KIND_FIELD_SENSOR -> p.addChildren(
                    RseLdUiComponents.liveRow("MEASURED","B",()->m.complete()?m.primary()+" / 15":"NOT READY • field input unverified"),
                    RseLdUiComponents.liveRow("EVIDENCE","coverage",()->m.secondary()+" / "+m.tertiary()));
            default -> p.addChildren(
                    RseLdUiComponents.liveRow("MEASURED","Gx/Gy/Gz",()->m.complete()?m.primary()+" / "+m.secondary()+" / "+m.tertiary():"NOT READY • three-axis scan incomplete"),
                    RseLdUiComponents.liveRow("MEASURED","B_local",()->m.complete()?m.auxiliary()+" / 15":"UNVERIFIED • partial scan"));
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
            p.addChild(new UIElement().layout(l->l.flexDirection(YogaFlexDirection.ROW).flexWrap(dev.vfyjxf.taffy.style.FlexWrap.WRAP).gapAll(6)).addChildren(
                    RseLdUiComponents.serverAction("Cycle RX ▶",m::cycleInputForward),
                    RseLdUiComponents.serverAction("Cycle TX ▶",m::cycleOutputForward)
            ));
        }
        return p;
    }

    /**
     * All rows consume already synchronized server snapshots. In particular,
     * a partial field scan is not a valid zero gradient or an accurate map.
     */
    private static UIElement mechanismPanel(MagneticSystemMenu m) {
        var panel = new UIElement().addClass("panel_bg");
        panel.layout(l -> l.paddingAll(5).gapAll(3));
        panel.addChild(RseLdUiComponents.title("INTERNAL MECHANISM • SERVER OBSERVATIONS"));
        switch (m.kind()) {
            case MagneticSystemMenu.KIND_ELECTROMAGNET -> panel.addChildren(
                    RseLdUiComponents.liveRow("INPUT", "connected Copper feeds", () -> Integer.toString(m.tertiary())),
                    RseLdUiComponents.liveRow("PHYSICAL", "coil potential", () -> m.complete()?m.secondary()+" / 15":"UNVERIFIED • Copper input"),
                    RseLdUiComponents.liveRow("SOURCE", "generated field strength", () -> m.complete()?m.primary()+" / 15":"UNVERIFIED • Copper input"));
            case MagneticSystemMenu.KIND_PERMANENT -> panel.addChildren(
                    RseLdUiComponents.liveRow("FIXED MODEL", "source strength", () -> m.primary() + " / 15"),
                    RseLdUiComponents.liveRow("ORIENTATION", "north marker", () -> m.facing().getName().toUpperCase()));
            case MagneticSystemMenu.KIND_COIL -> panel.addChildren(
                    RseLdUiComponents.liveRow("INPUT", "sampled magnetic field", () -> m.complete()?m.primary()+" / 15":"NOT READY • field input unverified"),
                    RseLdUiComponents.liveRow("ADJUSTABLE", "coil turns N", () -> Integer.toString(m.tertiary())),
                    RseLdUiComponents.liveRow("OUTPUT", "induced voltage", () -> m.complete()?m.secondary()+" / 15":"UNVERIFIED • induction input"),
                    RseLdUiComponents.fixedRow("ΔB", () -> "NOT RETAINED IN HMI",
                            "Induction uses server state; no fictitious prior-field sample is reconstructed"));
            case MagneticSystemMenu.KIND_FIELD_SENSOR -> panel.addChildren(
                    RseLdUiComponents.liveRow("METROLOGY", "scanned / expected cells", () -> m.secondary() + " / " + m.tertiary()),
                    RseLdUiComponents.liveRow("MEASURED", "field B", () -> m.complete()?m.primary()+" / 15":"NOT READY • scan incomplete"));
            case MagneticSystemMenu.KIND_GRADIENT -> panel.addChildren(
                    RseLdUiComponents.liveRow("MEASURED", "∂B along X", () -> m.complete()?Integer.toString(m.primary()):"NOT READY • X/Y/Z scan incomplete"),
                    RseLdUiComponents.liveRow("MEASURED", "∂B along Y", () -> m.complete()?Integer.toString(m.secondary()):"NOT READY • X/Y/Z scan incomplete"),
                    RseLdUiComponents.liveRow("MEASURED", "∂B along Z", () -> m.complete()?Integer.toString(m.tertiary()):"NOT READY • X/Y/Z scan incomplete"),
                    RseLdUiComponents.liveRow("MEASURED", "local field B", () -> m.complete()?Integer.toString(m.auxiliary()):"UNVERIFIED • partial scan"),
                    RseLdUiComponents.liveRow("EVIDENCE", "total scanned cells (X+Y+Z)", () -> Integer.toString(m.extra())));
            default -> panel.addChild(RseLdUiComponents.fixedRow("mechanism", () -> "UNCLASSIFIED",
                    "Unknown magnetic device, no fabricated field output"));
        }
        return panel;
    }

    private static UIElement evidence(MagneticSystemMenu m){
        var panel = new UIElement().addClass("panel_bg").layout(l->l.paddingAll(5).gapAll(3)).addChildren(
                RseLdUiComponents.liveRow("EVIDENCE","quality",()->m.quality().name()),
                RseLdUiComponents.liveRow("EVIDENCE","scan / topology",()->m.complete()?"COMPLETE":"INCOMPLETE / UNVERIFIED"),
                RseLdUiComponents.fixedRow("field law",()->"bounded inverse-square-style accumulation","server magnetic model")
        );
        if (m.kind()==MagneticSystemMenu.KIND_GRADIENT) {
            panel.addChild(RseLdUiComponents.liveRow("COVERAGE", "sum scanned cells", () -> Integer.toString(m.extra())));
            panel.addChild(RseLdUiComponents.fixedRow("completeness",()->"ALL THREE AXES",
                    "Only the server's per-axis complete flags can certify this three-axis result"));
        } else if (m.kind()==MagneticSystemMenu.KIND_FIELD_SENSOR) {
            panel.addChild(RseLdUiComponents.liveRow("COVERAGE", "scanned / expected", () -> m.secondary()+" / "+m.tertiary()));
        }
        return panel;
    }

    private static String deviceName(MagneticSystemMenu m){return switch(m.kind()){case MagneticSystemMenu.KIND_ELECTROMAGNET->"ELECTROMAGNET";case MagneticSystemMenu.KIND_PERMANENT->"PERMANENT MAGNET";case MagneticSystemMenu.KIND_COIL->"INDUCTION COIL";case MagneticSystemMenu.KIND_FIELD_SENSOR->"MAGNETIC FIELD SENSOR";default->"MAGNETIC GRADIENT METER";};}
    private static String equation(MagneticSystemMenu m){return switch(m.kind()){case MagneticSystemMenu.KIND_ELECTROMAGNET->"S_field = valid(Copper) ? V_coil : 0";case MagneticSystemMenu.KIND_COIL->"V_ind = clamp(N · |B[n] - B[n-1]|, 0, 15)";case MagneticSystemMenu.KIND_GRADIENT->"G = spatial ΔB from bounded field samples";default->"B = clamp(round(Σ S_i / max(1,r_i²)),0,15)";};}
}
