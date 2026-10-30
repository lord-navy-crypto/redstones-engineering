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
        root.layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(8).gapAll(6));
        root.addChildren(
                RseLdUiComponents.title("COPPER CIRCUIT METER"),
                RseLdUiComponents.tabbedWorkspace(
                        600, 400, 850,
                        new String[]{"Overview", "Details", "Controls", "Diagnostics", "Evidence"},
                        new UIElement[]{
                                RseLdUiComponents.workspacePage(
                                        RseLdUiComponents.title("COPPER POWER / LOAD NETWORK"),
                                        RseLdUiComponents.title("PIONEER PATTERN • ELECTRICAL MEASUREMENT MODEL"),
                                        RseLdUiComponents.formulaCard(()->"I = V / R_eq ; P = V · I"),
                                        RseLdUiComponents.liveRow("EVIDENCE","quality",()->qualityLabel(m)),
                                        RseLdUiComponents.liveRow("MEASURED","V",()->measured(m,m.voltage()+" V-eq")),
                                        RseLdUiComponents.liveRow("DERIVED","I",()->measured(m,String.format(java.util.Locale.ROOT,"%.3f I-eq",m.current()))),
                                        RseLdUiComponents.liveRow("DERIVED","P",()->measured(m,String.format(java.util.Locale.ROOT,"%.2f P-eq",m.power())))
                                ),
                                RseLdUiComponents.workspacePage(
                                        RseLdUiComponents.liveRow("MEASURED","V",()->measured(m,m.voltage()+" V-eq")),
                                        RseLdUiComponents.liveRow("DERIVED","R_eq",()->measured(m,String.format(java.util.Locale.ROOT,"%.2f R-eq",m.resistance()))),
                                        RseLdUiComponents.liveRow("DERIVED","I",()->measured(m,String.format(java.util.Locale.ROOT,"%.3f I-eq",m.current())))
                                ),
                                RseLdUiComponents.workspacePage(
                                        RseLdUiComponents.liveRow("DERIVED","P",()->measured(m,String.format(java.util.Locale.ROOT,"%.2f P-eq",m.power()))),
                                        RseLdUiComponents.liveRow("EVIDENCE","quality",()->m.quality().name()),
                                        RseLdUiComponents.liveRow("COMMISSIONING","status",()->m.snapshotReady()?m.commissioningStatus().name():"NOT READY • awaiting server diagnostic")
                                ),
                                RseLdUiComponents.workspacePage(
                                        RseLdUiComponents.fixedRow("authority",()->"SERVER-SYNCHRONIZED OBSERVER",
                        "server computes V, R_eq, I and P • readout variables: V, Req, I and P • observer-only • meter never drives Copper state"),
                                        RseLdUiComponents.serverAction("Cycle measurement face ▶",m::cycleFaceForward),
                                        RseLdUiComponents.note("OBSERVER ONLY • measurements are server-synchronized; this meter never drives the circuit")
                                ),
                                RseLdUiComponents.workspacePage(
                                        RseLdUiComponents.liveRow("EVIDENCE","measurement quality",()->qualityLabel(m)),
                                        new Label().setText("No fabricated history; missing input is NOT a measured zero."),
                                        RseLdUiComponents.authorityFooter()
                                )
                        }
                )
        );
        return RseLdUiComponents.responsiveUi(root, player, 620, 440);
    }
    private static String qualityLabel(CopperCircuitMeterMenu m) {
        return m.snapshotReady() ? m.quality().name() : "NOT READY • first server snapshot pending";
    }

    /** Electrical quantities have meaning only with a valid server observation. */
    private static String measured(CopperCircuitMeterMenu m, String reading) {
        return m.snapshotReady() && m.quality()==dev.redstoneengineering.core.port.PortQuality.VALID
                ? reading : "NOT READY • "+m.quality().name();
    }

}
