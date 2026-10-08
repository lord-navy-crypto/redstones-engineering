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
        root.layout(l -> l.width(620).height(440).paddingAll(8).gapAll(6));
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
        return ModularUI.of(UI.of(root, StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.GDP)), player);
    }

    private static UIElement modelPanel(IndustrialBufferMenu m) {
        return new UIElement().addClass("panel_bg").layout(l -> l.paddingAll(5).gapAll(3)).addChildren(
                new Label().setText("PIONEER PATTERN • OPERATIONS / WIP MODEL"),
                RseLdUiComponents.formulaCard(() ->
                        "WIP% = 100·used/capacity ; signal = (used=0)?0:clamp(round(15·used/capacity),1,15)"),
                RseLdUiComponents.liveRow("MEASURED", "used", () -> m.usedUnits() + " units"),
                RseLdUiComponents.fixedRow("capacity", () -> m.capacityUnits() + " units", "server-owned buffer capacity"),
                RseLdUiComponents.liveRow("DERIVED", "free", () -> m.availableUnits() + " units"),
                RseLdUiComponents.liveRow("DERIVED", "WIP PRESSURE", () -> m.wipPressurePercent() + "% • " + fullnessState(m)),
                RseLdUiComponents.liveRow("DERIVED", "WIP signal", () -> m.wipSignal() + " / 15"),
                new Label().setText("FIXED PORT LAW • SOUTH=15 iff free capacity>0 • NORTH=15 iff free capacity=0")
        );
    }

    private static UIElement rolesPanel(IndustrialBufferMenu m) {
        return new UIElement().addClass("panel_bg").layout(l -> l.paddingAll(5).gapAll(3)).addChildren(
                new Label().setText("WORKCELL ROLES"),
                RseLdUiComponents.liveRow("INPUT TO", "workcells", () -> Integer.toString(m.inputConsumerWorkcells())),
                RseLdUiComponents.liveRow("OUTPUT FROM", "workcells", () -> Integer.toString(m.outputProducerWorkcells())),
                new Label().setText("OUTPUT / JOB / LOT IDENTITY never enters BlockState or analog Redstone.")
        );
    }

    private static UIElement lotsPanel(IndustrialBufferMenu m) {
        var panel = new UIElement().addClass("panel_bg");
        panel.layout(l -> l.paddingAll(5).gapAll(3));
        panel.addChildren(
                new Label().setText("LOT IDENTITY • exact bounded server snapshot"),
                new Label().setText("OUTPUT        JOB          UNITS"),
                RseLdUiComponents.liveRow("LOTS", "count", () -> Integer.toString(m.totalLotCount()))
        );
        if (m.visibleLots().isEmpty()) {
            panel.addChild(new Label().setText("No retained lots in the server snapshot."));
        } else {
            int shown = 0;
            for (IndustrialBufferMenu.LotView lot : m.visibleLots()) {
                if (shown++ >= 6) break;
                panel.addChild(new Label().setText(
                        Long.toUnsignedString(lot.outputId()) + "   "
                                + Long.toUnsignedString(lot.jobId()) + "   "
                                + lot.units()));
            }
            if (m.totalLotCount() > shown) {
                int hidden = m.totalLotCount() - shown;
                panel.addChild(new Label().setText("+ " + hidden + " more lot(s) • reopen to refresh identity window"));
            }
        }
        return panel;
    }

    private static UIElement persistencePanel() {
        return new UIElement().addClass("panel_bg").layout(l -> l.paddingAll(5).gapAll(3)).addChildren(
                new Label().setText("PERSISTED WIP"),
                new Label().setText("Non-empty block removal does not silently erase logical WIP."),
                new Label().setText("Replacing the buffer at the same position reattaches its deterministic identity."),
                new Label().setText("Corrupt persisted lot evidence fails closed instead of loading as an empty buffer."),
                new Label().setText("Downstream queue WAIT leaves the persisted lot untouched.")
        );
    }

    private static String fullnessState(IndustrialBufferMenu m) {
        if (m.capacityUnits() <= 0) return "UNAVAILABLE";
        if (m.availableUnits() == 0) return "FULL";
        if (m.wipPressurePercent() >= 80) return "NEAR FULL";
        return "AVAILABLE";
    }
}
