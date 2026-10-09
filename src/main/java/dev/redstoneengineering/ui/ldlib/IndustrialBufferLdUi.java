package dev.redstoneengineering.ui.ldlib;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.redstoneengineering.ui.menu.IndustrialBufferMenu;
import net.minecraft.world.entity.player.Player;

/** Read-only LDLib2 operations HMI for persisted Industrial Buffer evidence. */
public final class IndustrialBufferLdUi {
    private IndustrialBufferLdUi() {}

    public static ModularUI create(IndustrialBufferMenu m, Player player) {
        var root = new UIElement().addClass("panel_bg");
        root.layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(8).gapAll(6));
        root.addChildren(
                RseLdUiComponents.title("INDUSTRIAL BUFFER • SERVER SNAPSHOT"),
                RseLdUiComponents.tabbedWorkspace(
                        600, 400, 850,
                        new String[]{"Overview", "Details", "Evidence"},
                        new UIElement[]{
                                RseLdUiComponents.workspacePage(
                                        modelPanel(m),
                                        rolesPanel(m)
                                ),
                                RseLdUiComponents.workspacePage(
                                        lotsPanel(m),
                                        persistencePanel()
                                ),
                                RseLdUiComponents.workspacePage(
                                        RseLdUiComponents.authorityFooter()
                                )
                        }
                )
        );
        return RseLdUiComponents.responsiveUi(root, player, 620, 440);
    }

    private static UIElement modelPanel(IndustrialBufferMenu m) {
        return new UIElement().addClass("panel_bg").layout(l -> l.paddingAll(5).gapAll(3)).addChildren(
                new Label().setText("PIONEER PATTERN • OPERATIONS / WIP MODEL"),
                RseLdUiComponents.formulaCard(() ->
                        "WIP% = 100·used/capacity ; signal = (used=0)?0:clamp(round(15·used/capacity),1,15)"),
                RseLdUiComponents.liveRow("EVIDENCE", "persisted buffer", () -> m.snapshotPresent()?"AVAILABLE":"NOT READY • no persisted snapshot"),
                RseLdUiComponents.liveRow("MEASURED", "used", () -> m.snapshotPresent()?m.usedUnits()+" units":"UNAVAILABLE"),
                RseLdUiComponents.fixedRow("capacity", () -> m.snapshotPresent()?m.capacityUnits()+" units":"UNAVAILABLE", "server-owned buffer capacity"),
                RseLdUiComponents.liveRow("DERIVED", "free", () -> m.snapshotPresent()?m.availableUnits()+" units":"UNAVAILABLE"),
                RseLdUiComponents.liveRow("DERIVED", "WIP PRESSURE", () -> m.snapshotPresent()?m.wipPressurePercent()+"% • "+fullnessState(m):"NOT READY • buffer snapshot missing"),
                RseLdUiComponents.liveRow("DERIVED", "WIP signal", () -> m.snapshotPresent()?m.wipSignal()+" / 15":"UNVERIFIED"),
                RseLdUiComponents.note("FIXED PORT LAW • SOUTH=15 iff free capacity>0 • NORTH=15 iff free capacity=0")
        );
    }

    private static UIElement rolesPanel(IndustrialBufferMenu m) {
        return new UIElement().addClass("panel_bg").layout(l -> l.paddingAll(5).gapAll(3)).addChildren(
                new Label().setText("WORKCELL ROLES"),
                RseLdUiComponents.liveRow("INPUT TO", "workcells", () -> m.snapshotPresent()
                         ? Integer.toString(m.inputConsumerWorkcells()) : "NOT READY • buffer snapshot unavailable"),
                RseLdUiComponents.liveRow("OUTPUT FROM", "workcells", () -> m.snapshotPresent()
                         ? Integer.toString(m.outputProducerWorkcells()) : "NOT READY • buffer snapshot unavailable"),
                RseLdUiComponents.note("OUTPUT / JOB / LOT IDENTITY never enters BlockState or analog Redstone.")
        );
    }

    private static UIElement lotsPanel(IndustrialBufferMenu m) {
        var panel = new UIElement().addClass("panel_bg");
        panel.layout(l -> l.paddingAll(5).gapAll(3));
        panel.addChildren(
                RseLdUiComponents.note("LOT IDENTITY • IMMUTABLE OPENING SNAPSHOT • output/job IDs are 64-bit and are not refreshed by 16-bit DataSlots"),
                new Label().setText("OUTPUT        JOB          UNITS"),
                RseLdUiComponents.liveRow("LOTS", "live count", () -> m.snapshotPresent()
                        ? Integer.toString(m.totalLotCount()) : "NOT READY • live buffer evidence unavailable"),
                RseLdUiComponents.fixedRow("opening count", () -> Integer.toString(m.openingLotCount()),
                        "identity list is frozen at menu open; reopen for new lot IDs")
        );
        if (m.visibleLots().isEmpty()) {
            panel.addChild(RseLdUiComponents.note("NO LOT IDENTITIES IN OPENING SNAPSHOT • this is NOT proof of an empty live buffer."));
        } else {
            int shown = 0;
            for (IndustrialBufferMenu.LotView lot : m.visibleLots()) {
                if (shown++ >= 6) break;
                panel.addChild(RseLdUiComponents.note(
                        Long.toUnsignedString(lot.outputId()) + "   "
                                + Long.toUnsignedString(lot.jobId()) + "   "
                                + lot.units()));
            }
            if (m.openingLotCount() > shown) {
                int hidden = m.openingLotCount() - shown;
                panel.addChild(RseLdUiComponents.note("+ " + hidden
                        + " more lot(s) in the OPENING snapshot • reopen to refresh identity window"));
            }
        }
        return panel;
    }

    private static UIElement persistencePanel() {
        return new UIElement().addClass("panel_bg").layout(l -> l.paddingAll(5).gapAll(3)).addChildren(
                new Label().setText("PERSISTED WIP"),
                RseLdUiComponents.note("Non-empty block removal does not silently erase logical WIP."),
                RseLdUiComponents.note("Replacing the buffer at the same position reattaches its deterministic identity."),
                RseLdUiComponents.note("Corrupt persisted lot evidence fails closed instead of loading as an empty buffer."),
                new Label().setText("Downstream queue WAIT leaves the persisted lot untouched.")
        );
    }

    private static String fullnessState(IndustrialBufferMenu m) {
        if (!m.snapshotPresent() || m.capacityUnits() <= 0) return "UNAVAILABLE";
        if (m.availableUnits() == 0) return "FULL";
        if (m.wipPressurePercent() >= 80) return "NEAR FULL";
        return "AVAILABLE";
    }
}
