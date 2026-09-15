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
        statusBadge(graphics, ready ? "INDUSTRIAL BUFFER • READY" : "BUFFER EVIDENCE • UNAVAILABLE",
                ready ? GOOD : WARN, 16, 78);
        labelValue(graphics, "Capacity", ready ? menu.capacityUnits() + " units" : "—", 102);
        labelValue(graphics, "Used WIP", ready ? menu.usedUnits() + " units" : "—", 120);
        labelValue(graphics, "Free capacity", ready ? menu.availableUnits() + " units" : "—", 138);
        labelValue(graphics, "Lots", ready ? Integer.toString(menu.totalLotCount()) : "—", 156);
        labelValue(graphics, "WIP signal", ready ? menu.wipSignal() + " / 15" : "—", 174);
        statusLine(graphics, "SPACE PERMIT", ready && menu.availableUnits() > 0 ? "HIGH" : "LOW",
                ready && menu.availableUnits() > 0 ? GOOD : WARN, 194);
        safeText(graphics, "Logical WIP is server-owned; this console never creates or edits a lot.", 16, 220, MUTED);
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
        statusBadge(graphics, "READ-ONLY LOT AUTHORITY", INFO, 16, 82);
        safeText(graphics, "Receipts are admitted only through evidence-bound Operations buffer runtime.", 16, 110, TEXT);
        safeText(graphics, "Allocations/releases are performed by Operations material-flow authority.", 16, 130, TEXT);
        safeText(graphics, "No button can forge quality acceptance, completion, output ID, or JOB identity.", 16, 150, MUTED);
        safeText(graphics, "Capacity is server-owned persistent state, not a client setting.", 16, 170, MUTED);
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

    private static String compactId(long value) {
        return Long.toUnsignedString(value);
    }
}
