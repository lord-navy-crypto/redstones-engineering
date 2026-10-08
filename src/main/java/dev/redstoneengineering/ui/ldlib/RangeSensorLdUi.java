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
        root.layout(l->l.width(650).height(440).paddingAll(8).gapAll(6));
        root.addChildren(
                RseLdUiComponents.title("RANGE SENSOR • FORMULA-FIRST SENSOR RESPONSE"),
                RseLdUiComponents.tabbedWorkspace(
                        600, 400, 850,
                        new String[]{"Evidence"},
                        new UIElement[]{
                                RseLdUiComponents.workspacePage(
                                        RseLdUiComponents.tabbedWorkspace(
                        630, 400, 850,
                        new String[]{"Overview", "Configure", "Diagnostics", "Evidence"},
                        new UIElement[]{
                                RseLdUiComponents.workspacePage(
                                        RseLdUiComponents.formulaCard(() -> equation(menu.responseMode())),
                                        RseLdUiComponents.liveRow("MEASURED","d",()->menu.distance()+" blocks"),
                                        RseLdUiComponents.liveRow("ADJUSTABLE","R",()->menu.configuredRange()+" blocks • {4,8,15}")
                                ),
                                RseLdUiComponents.workspacePage(
                                        RseLdUiComponents.liveRow("ADJUSTABLE","detect",()->detect(menu.detectMode())),
                                        RseLdUiComponents.liveRow("ADJUSTABLE","response",()->response(menu.responseMode())),
                                        RseLdUiComponents.liveRow("DERIVED","y",()->menu.output()+" / 15")
                                ),
                                RseLdUiComponents.workspacePage(
                                        RseLdUiComponents.liveRow("EVIDENCE","scan",()->scan(menu.scanStatusOrdinal())+" • "+menu.scannedCells()+"/"+menu.configuredRange()),
                                        controls(menu),
                                        RseLdUiComponents.liveRow("I/O","route",()->menu.sensingDirection().getName().toUpperCase()+" → "+menu.outputDirection().getName().toUpperCase())
                                ),
                                RseLdUiComponents.workspacePage(
                                        new Label().setText("A complete CLEAR scan with d=0 is valid evidence. The client never infers validity from d>0."),
                                        RseLdUiComponents.authorityFooter()
                                )
                        }
                )
                                )
                        }
                )
        );
        return ModularUI.of(UI.of(root, StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.GDP)), player);
    }

    private static UIElement controls(RangeSensorMenu menu){
        var r=new TextField().setNumbersOnlyInt(4,15);
        r.layout(l->l.width(90));
        r.bind(DataBindingBuilder.string(
                ()->Integer.toString(menu.configuredRange()),
                v->{ try{ menu.setRangeFromUi(Integer.parseInt(v)); }catch(NumberFormatException ignored){} }
        ).build());
        return new UIElement().addClass("panel_bg").layout(l->l.flexDirection(YogaFlexDirection.ROW).gapAll(6).paddingAll(5)).addChildren(
                r,
                RseLdUiComponents.serverAction("Cycle detect ▶",menu::cycleDetectForward),
                RseLdUiComponents.serverAction("Cycle response ▶",menu::cycleResponseForward),
                RseLdUiComponents.serverAction("Cycle direction ▶",menu::cycleDirectionForward)
        );
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
