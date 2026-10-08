package dev.redstoneengineering.ui.ldlib;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.redstoneengineering.ui.menu.CopperCircuitMeterMenu;
import net.minecraft.world.entity.player.Player;

public final class CopperCircuitMeterLdUi {
    private CopperCircuitMeterLdUi() {}

    public static ModularUI create(CopperCircuitMeterMenu m, Player player) {
        var root=new UIElement().addClass("panel_bg");
        root.layout(l->l.width(620).height(440).paddingAll(8).gapAll(6));
        root.addChildren(
                RseLdUiComponents.title("COPPER CIRCUIT METER"),
                RseLdUiComponents.tabbedWorkspace(
                        600, 400, 850,
                        new String[]{"Overview", "Details", "Controls", "Diagnostics", "Evidence"},
                        new UIElement[]{
                                RseLdUiComponents.workspacePage(
                                        RseLdUiComponents.title("COPPER POWER / LOAD NETWORK"),
                                        RseLdUiComponents.title("PIONEER PATTERN • ELECTRICAL MEASUREMENT MODEL"),
                                        RseLdUiComponents.formulaCard(()->"I = V / R_eq ; P = V · I")
                                ),
                                RseLdUiComponents.workspacePage(
                                        RseLdUiComponents.liveRow("MEASURED","V",()->m.voltage()+" V-eq"),
                                        RseLdUiComponents.liveRow("DERIVED","R_eq",()->String.format(java.util.Locale.ROOT,"%.2f R-eq",m.resistance())),
                                        RseLdUiComponents.liveRow("DERIVED","I",()->String.format(java.util.Locale.ROOT,"%.3f I-eq",m.current()))
                                ),
                                RseLdUiComponents.workspacePage(
                                        RseLdUiComponents.liveRow("DERIVED","P",()->String.format(java.util.Locale.ROOT,"%.2f P-eq",m.power())),
                                        RseLdUiComponents.liveRow("EVIDENCE","quality",()->m.quality().name()),
                                        RseLdUiComponents.liveRow("COMMISSIONING","status",()->m.commissioningStatus().name())
                                ),
                                RseLdUiComponents.workspacePage(
                                        RseLdUiComponents.fixedRow("authority",()->"SERVER-SYNCHRONIZED OBSERVER",
                        "server computes V, R_eq, I and P • readout variables: V, Req, I and P • observer-only • meter never drives Copper state"),
                                        RseLdUiComponents.serverAction("Cycle measurement face ▶",m::cycleFaceForward),
                                        new Label().setText("OBSERVER ONLY • measurements are server-synchronized; this meter never drives the circuit")
                                ),
                                RseLdUiComponents.workspacePage(
                                        RseLdUiComponents.authorityFooter()
                                )
                        }
                )
        );
        return RseLdUiComponents.responsiveUi(root, player, 620, 440);
    }
}
