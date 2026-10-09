package dev.redstoneengineering.ui.ldlib;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.redstoneengineering.ui.menu.RangeSensorMenu;
import net.minecraft.world.entity.player.Player;
import org.appliedenergistics.yoga.YogaFlexDirection;

public final class RangeSensorLdUi {
    private RangeSensorLdUi() {}

    public static ModularUI create(RangeSensorMenu menu, Player player) {
        var root=new UIElement().addClass("panel_bg");
        root.layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(8).gapAll(6));
        root.addChildren(
                RseLdUiComponents.title("RANGE SENSOR • FORMULA-FIRST SENSOR RESPONSE"),
                RseLdUiComponents.tabbedWorkspace(
                        630, 400, 850,
                        new String[]{"Overview", "Configure", "Diagnostics", "Evidence"},
                        new UIElement[]{
                                RseLdUiComponents.workspacePage(
                                        RseLdUiComponents.formulaCard(() -> menu.snapshotReady()
                                                 ? equation(menu.responseMode()) : "NOT READY • awaiting server response configuration"),
                                        RseLdUiComponents.liveRow("EVIDENCE","server snapshot",()->menu.snapshotReady()?"SYNCED":"NOT READY • awaiting server data"),
                                         RseLdUiComponents.liveRow("MEASURED","d",()->distanceReadout(menu)),
                                        RseLdUiComponents.liveRow("ADJUSTABLE","R",()->menu.snapshotReady()
                                                 ? menu.configuredRange()+" blocks • {4,8,15}" : "NOT READY • range not synchronized")
                                ),
                                RseLdUiComponents.workspacePage(
                                        RseLdUiComponents.liveRow("ADJUSTABLE","detect",()->menu.snapshotReady()?detect(menu.detectMode()):"NOT READY"),
                                        RseLdUiComponents.liveRow("ADJUSTABLE","response",()->menu.snapshotReady()?response(menu.responseMode()):"NOT READY"),
                                        RseLdUiComponents.liveRow("DERIVED","y",()->menu.snapshotReady() && menu.evidenceValid()
                                                 ? menu.output()+" / 15 • verified "+scan(menu.scanStatusOrdinal())
                                                 : "UNVERIFIED • no complete scan")
                                ),
                                RseLdUiComponents.workspacePage(
                                        RseLdUiComponents.liveRow("EVIDENCE","scan",()->menu.snapshotReady()
                                                 ? scan(menu.scanStatusOrdinal())+" • "+menu.scannedCells()+"/"+menu.configuredRange()
                                                 : "NOT READY • awaiting server scan"),
                                         RseLdUiComponents.liveRow("EVIDENCE","complete",()->menu.snapshotReady()
                                                 ? (menu.evidenceValid()?"VERIFIED • TARGET or CLEAR":"NOT READY • partial/uninitialized")
                                                 : "NOT READY • snapshot pending"),
                                        controls(menu),
                                        RseLdUiComponents.liveRow("I/O","route",()->menu.snapshotReady()
                                                 ? menu.sensingDirection().getName().toUpperCase()+" → "+menu.outputDirection().getName().toUpperCase()
                                                 : "NOT READY • route awaiting server")
                                ),
                                RseLdUiComponents.workspacePage(
                                        RseLdUiComponents.note("A complete CLEAR scan with d=0 is valid evidence of an empty scan; it is not an object detected at distance zero."),
                                        RseLdUiComponents.authorityFooter()
                                )
                        }
                )
        );
        return RseLdUiComponents.responsiveUi(root, player, 650, 440);
    }

    private static UIElement controls(RangeSensorMenu menu){
        var r=new TextField().setNumbersOnlyInt(4,15);
        r.layout(l->l.width(90));
        r.bind(DataBindingBuilder.string(
                ()->Integer.toString(menu.configuredRange()),
                v->{ try{ menu.setRangeFromUi(Integer.parseInt(v)); }catch(NumberFormatException ignored){} }
        ).build());
        var wrapRow = new UIElement().layout(l->l.flexDirection(YogaFlexDirection.ROW)
                .flexWrap(dev.vfyjxf.taffy.style.FlexWrap.WRAP).gapAll(6));
        wrapRow.addChildren(
                r,
                RseLdUiComponents.serverAction("Range 4", () -> { menu.setRangeFromUi(4); }),
                RseLdUiComponents.serverAction("Range 8", () -> { menu.setRangeFromUi(8); }),
                RseLdUiComponents.serverAction("Range 15", () -> { menu.setRangeFromUi(15); })
        );
        return new UIElement().addClass("panel_bg").layout(l->l.paddingAll(5).gapAll(5)).addChildren(
                RseLdUiComponents.note("R is discrete: only 4, 8 or 15 blocks. Other typed values are rejected by the server."),
                wrapRow,
                new UIElement().layout(l->l.flexDirection(YogaFlexDirection.ROW)
                        .flexWrap(dev.vfyjxf.taffy.style.FlexWrap.WRAP).gapAll(6)).addChildren(
                        RseLdUiComponents.serverAction("Cycle detect ▶",menu::cycleDetectForward),
                        RseLdUiComponents.serverAction("Cycle response ▶",menu::cycleResponseForward),
                        RseLdUiComponents.serverAction("Cycle direction ▶",menu::cycleDirectionForward)
                )
        );
    }

    private static String distanceReadout(RangeSensorMenu menu) {
        if (!menu.snapshotReady()) return "NOT READY • awaiting server snapshot";
        if (!menu.evidenceValid()) return "NOT READY • incomplete or uninitialized scan";
        return switch (menu.scanStatusOrdinal()) {
            case 1 -> menu.distance() > 0
                    ? menu.distance()+" blocks • TARGET"
                    : "NOT READY • target evidence inconsistent";
            case 2 -> "CLEAR • no target within "+menu.configuredRange()+" blocks";
            default -> "NOT READY • status does not confirm a complete scan";
        };
    }

    private static String equation(int mode){
        return switch(mode){
            case 0 -> "y = (d ≤ 0) ? 0 : round(15 · (R - d + 1) / R)";
            case 1 -> "y = (d ≤ 0) ? 0 : round(15 · d / R)";
            case 2 -> "y = (d > 0 ∧ d ≤ max(1, floor(R/2))) ? 15 : 0";
            case 3 -> "y = (max(1,floor(R/3)) ≤ d ≤ max(low,floor(2R/3))) ? 15 : 0";
            default -> "y = 0";
        };
    }
    private static String detect(int m){return switch(m){case 0->"BLOCK";case 1->"ENTITY";default->"ANY";};}
    private static String response(int m){return switch(m){case 0->"PROXIMITY";case 1->"DISTANCE";case 2->"THRESHOLD";default->"WINDOW";};}
    private static String scan(int s){return switch(s){case 1->"TARGET";case 2->"CLEAR";case 3->"INCOMPLETE / UNLOADED";default->"UNINITIALIZED";};}
}
