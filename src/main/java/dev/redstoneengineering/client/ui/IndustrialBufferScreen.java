package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.ui.menu.IndustrialBufferMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Read-only Industrial Buffer HMI; exact visible lot identities are a bounded server snapshot. */
public final class IndustrialBufferScreen extends EngineeringScreen<IndustrialBufferMenu> {
    public IndustrialBufferScreen(IndustrialBufferMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void renderSection(GuiGraphics graphics, Section section) {
        switch (section) {
            case OVERVIEW -> renderOverview(graphics);
            case PORTS -> renderPorts(graphics);
            case CONFIGURE -> renderConfigure(graphics);
            case DIAGNOSTICS -> renderDiagnostics(graphics);
            case HISTORY -> renderHistory(graphics);
        }
    }

    private void renderOverview(GuiGraphics graphics) {
        boolean ready = menu.capacityUnits() > 0;
        statusBadge(graphics, ready ? "INDUSTRIAL BUFFER • " + fullnessState() : "BUFFER EVIDENCE • UNAVAILABLE",
                ready ? fullnessColor() : WARN, 16, 76);
        labelValue(graphics, "Capacity", ready ? menu.capacityUnits() + " units" : "—", 98);
        labelValue(graphics, "Used WIP", ready ? menu.usedUnits() + " units" : "—", 116);
        labelValue(graphics, "Free capacity", ready ? menu.availableUnits() + " units" : "—", 134);
        statusLine(graphics, "WIP PRESSURE", ready ? menu.wipPressurePercent() + "%" : "—", ready ? fullnessColor() : WARN, 152);
        drawWipBar(graphics, 16, 170, 180, menu.wipPressurePercent());
        labelValue(graphics, "Lots", ready ? Integer.toString(menu.totalLotCount()) : "—", 188);
        labelValue(graphics, "INPUT TO workcells", Integer.toString(menu.inputConsumerWorkcells()), 206);
        labelValue(graphics, "OUTPUT FROM workcells", Integer.toString(menu.outputProducerWorkcells()), 224);
    }

    private void renderPorts(GuiGraphics graphics) {
        statusLine(graphics, "UP • WIP LEVEL", menu.capacityUnits() > 0 ? menu.wipSignal() + " / 15" : "NO EVIDENCE",
                menu.capacityUnits() > 0 ? INFO : WARN, 86);
        statusLine(graphics, "SOUTH • SPACE PERMIT", menu.capacityUnits() > 0 && menu.availableUnits() > 0 ? "HIGH" : "LOW",
                menu.capacityUnits() > 0 && menu.availableUnits() > 0 ? GOOD : WARN, 108);
        statusLine(graphics, "NORTH • FULL", menu.capacityUnits() > 0 && menu.availableUnits() == 0 ? "HIGH" : "LOW",
                menu.capacityUnits() > 0 && menu.availableUnits() == 0 ? WARN : GOOD, 130);
        safeText(graphics, "Only fullness/WIP/permit cross the vanilla 0–15 boundary.", 16, 158, TEXT);
        safeText(graphics, "OUTPUT / JOB / LOT IDENTITY never enters BlockState or analog redstone.", 16, 178, MUTED);
    }

    private void renderConfigure(GuiGraphics graphics) {
        statusBadge(graphics, "WORKCELL ROLES • READ ONLY", INFO, 16, 78);
        statusLine(graphics, "INPUT TO", menu.inputConsumerWorkcells() + " workcell(s)", menu.inputConsumerWorkcells() > 0 ? GOOD : INFO, 104);
        statusLine(graphics, "OUTPUT FROM", menu.outputProducerWorkcells() + " workcell(s)", menu.outputProducerWorkcells() > 0 ? GOOD : INFO, 126);
        safeText(graphics, "Role counts are derived from persisted OperationWorkcellBufferBinding records.", 16, 152, TEXT);
        safeText(graphics, "Use the Operations Binding Tool to change relationships; this screen cannot mutate them.", 16, 172, MUTED);
        safeText(graphics, "Capacity and lots remain server-owned Operations state.", 16, 192, MUTED);
    }

    private void renderDiagnostics(GuiGraphics graphics) {
        statusBadge(graphics, "LOT IDENTITY • server snapshot", INFO, 16, 78);
        safeText(graphics, "OUTPUT        JOB          UNITS", 18, 99, MUTED);
        int y = 116;
        int shown = 0;
        for (IndustrialBufferMenu.LotView lot : menu.visibleLots()) {
            if (shown >= 6) break;
            safeText(graphics,
                    compactId(lot.outputId()) + "   " + compactId(lot.jobId()) + "   " + lot.units(),
                    18, y, TEXT);
            y += 18;
            shown++;
        }
        if (shown == 0) safeText(graphics, "No retained lots in the server snapshot.", 18, y, MUTED);
        if (menu.totalLotCount() > shown) {
            safeText(graphics, "+ " + (menu.totalLotCount() - shown) + " more lot(s) • reopen to refresh identity window", 18, 228, MUTED);
        } else {
            safeText(graphics, "Identity list is captured exactly when this server snapshot opens.", 18, 228, MUTED);
        }
    }

    private void renderHistory(GuiGraphics graphics) {
        statusBadge(graphics, "PERSISTED WIP", INFO, 16, 82);
        safeText(graphics, "Non-empty block removal does not silently erase logical WIP.", 16, 110, TEXT);
        safeText(graphics, "Replacing the buffer at the same position reattaches its deterministic identity.", 16, 130, TEXT);
        safeText(graphics, "Corrupt persisted lot evidence fails closed instead of loading as an empty buffer.", 16, 150, MUTED);
        safeText(graphics, "Downstream queue WAIT leaves the persisted lot untouched.", 16, 170, MUTED);
    }

    private String fullnessState() {
        if (menu.capacityUnits() <= 0) return "UNAVAILABLE";
        if (menu.availableUnits() == 0) return "FULL";
        if (menu.wipPressurePercent() >= 80) return "NEAR FULL";
        return "AVAILABLE";
    }

    private int fullnessColor() {
        if (menu.capacityUnits() <= 0) return WARN;
        if (menu.availableUnits() == 0) return BAD;
        if (menu.wipPressurePercent() >= 80) return WARN;
        return GOOD;
    }

    private void drawWipBar(GuiGraphics graphics, int x, int y, int width, int percent) {
        int bounded = Math.max(0, Math.min(100, percent));
        graphics.fill(x, y, x + width, y + 8, 0xFF171C21);
        int filled = Math.round(width * bounded / 100.0F);
        if (filled > 0) graphics.fill(x, y, x + filled, y + 8, fullnessColor());
    }

    private static String compactId(long value) {
        return Long.toUnsignedString(value);
    }
}
