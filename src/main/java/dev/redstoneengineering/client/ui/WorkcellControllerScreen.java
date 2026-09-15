package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.ui.menu.WorkcellControllerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Workcell Controller HMI: synchronized server evidence only, no client-side scheduling logic. */
public final class WorkcellControllerScreen extends EngineeringScreen<WorkcellControllerMenu> {
    public WorkcellControllerScreen(WorkcellControllerMenu menu, Inventory inventory, Component title) {
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
        int stateColor = menu.faultResourceCount() > 0 ? BAD : (menu.admissionPermitted() ? GOOD : WARN);
        statusBadge(graphics, "WORKCELL CONTROLLER", stateColor, 16, 76);
        labelValue(graphics, "Workcell", menu.workcellId(), 96);

        safeText(graphics, "INPUT", 20, 118, INFO);
        safeText(graphics, "→", 88, 118, MUTED);
        safeText(graphics, "WORKCELL", 118, 118, TEXT);
        safeText(graphics, "→", 202, 118, MUTED);
        safeText(graphics, "OUTPUT", 232, 118, INFO);

        labelValue(graphics, "Input WIP", capacityText(menu.inputBufferUsedUnits(), menu.inputBufferCapacityUnits()), 138);
        drawPressureBar(graphics, 16, 154, 126, menu.inputWipPressurePercent());
        labelValue(graphics, "Resources valid/running", menu.validResourceCount() + "/" + menu.runningResourceCount()
                + " of " + menu.boundResourceCount(), 172);
        labelValue(graphics, "Output WIP", capacityText(menu.outputBufferUsedUnits(), menu.outputBufferCapacityUnits()), 190);
        drawPressureBar(graphics, 16, 206, 126, menu.outputWipPressurePercent());
        statusLine(graphics, "ADMISSION", admissionState(), admissionColor(), 226);
    }

    private void renderPorts(GuiGraphics graphics) {
        statusLine(graphics, "NORTH • ACTIVE", menu.runningResourceCount() > 0 ? "HIGH" : "LOW", INFO, 84);
        statusLine(graphics, "SOUTH • PERMIT", menu.admissionPermitted() ? "HIGH" : "LOW", menu.admissionPermitted() ? GOOD : WARN, 104);
        statusLine(graphics, "EAST • HOLD", menu.admissionHeld() ? "HIGH" : "LOW", menu.admissionHeld() ? WARN : GOOD, 124);
        statusLine(graphics, "WEST • FAULT", menu.faultResourceCount() > 0 ? "HIGH" : "LOW", menu.faultResourceCount() > 0 ? BAD : GOOD, 144);
        statusLine(graphics, "UP • QUEUE PRESSURE", menu.queuePressure() < 0 ? "UNAVAILABLE" : menu.queuePressure() + " / 15", menu.queuePressure() < 0 ? WARN : INFO, 164);
        safeText(graphics, "Job/resource/lot identities never travel through analog redstone.", 16, 190, MUTED);
    }

    private void renderConfigure(GuiGraphics graphics) {
        statusBadge(graphics, "EXPLICIT BINDING", INFO, 16, 78);
        labelValue(graphics, "Bound resources", Integer.toString(menu.boundResourceCount()), 100);
        statusLine(graphics, "INPUT buffer", menu.capacityEvidenceAvailable()
                ? capacityText(menu.inputBufferUsedUnits(), menu.inputBufferCapacityUnits()) : "MISSING / INVALID", menu.capacityEvidenceAvailable() ? GOOD : WARN, 120);
        statusLine(graphics, "OUTPUT buffer", menu.capacityEvidenceAvailable()
                ? capacityText(menu.outputBufferUsedUnits(), menu.outputBufferCapacityUnits()) : "MISSING / INVALID", menu.capacityEvidenceAvailable() ? GOOD : WARN, 140);
        safeText(graphics, "Use the Operations Binding Tool to select resources and INPUT/OUTPUT buffers.", 16, 166, TEXT);
        safeText(graphics, "Binding is explicit and server-authoritative; no proximity discovery is performed.", 16, 186, MUTED);
        safeText(graphics, "This HMI visualizes configuration but cannot rewrite lot, quality, or scheduling state.", 16, 206, MUTED);
    }

    private void renderDiagnostics(GuiGraphics graphics) {
        statusLine(graphics, "Resource evidence", menu.validResourceCount() + " / " + menu.boundResourceCount() + " VALID",
                menu.validResourceCount() == menu.boundResourceCount() && menu.boundResourceCount() > 0 ? GOOD : WARN, 80);
        statusLine(graphics, "Fault resources", Integer.toString(menu.faultResourceCount()), menu.faultResourceCount() > 0 ? BAD : GOOD, 100);
        statusLine(graphics, "Capacity evidence", menu.capacityEvidenceAvailable() ? "VALID" : "INCOMPLETE", menu.capacityEvidenceAvailable() ? GOOD : WARN, 120);
        labelValue(graphics, "Input pressure", menu.capacityEvidenceAvailable() ? menu.inputWipPressurePercent() + "%" : "—", 140);
        labelValue(graphics, "Output pressure", menu.capacityEvidenceAvailable() ? menu.outputWipPressurePercent() + "%" : "—", 158);
        statusLine(graphics, "PERMIT / HOLD", menu.admissionPermitted() ? "PERMIT" : "HOLD", admissionColor(), 178);
        safeText(graphics, "Reason • " + menu.admissionReason(), 16, 202, admissionColor());
        safeText(graphics, "SETUP and MAINTENANCE remain withheld until their world evidence is persisted.", 16, 222, MUTED);
    }

    private void renderHistory(GuiGraphics graphics) {
        statusBadge(graphics, "AUTHORITY BOUNDARY", INFO, 16, 82);
        safeText(graphics, "Controller stores no duplicate scheduler or bottleneck ranking.", 16, 108, TEXT);
        safeText(graphics, "Admission is delegated to OperationWorkcellAdmissionAssessment.", 16, 128, TEXT);
        safeText(graphics, "Missing setup, maintenance, or buffer evidence remains unavailable.", 16, 148, TEXT);
        safeText(graphics, "Completed work still requires explicit completion evidence from the resource.", 16, 168, MUTED);
    }

    private String admissionState() {
        if (menu.admissionPermitted()) return "PERMIT • " + menu.admissionReason();
        if (menu.faultResourceCount() > 0) return "HOLD / FAULT • " + menu.admissionReason();
        return "HOLD • " + menu.admissionReason();
    }

    private int admissionColor() {
        if (menu.faultResourceCount() > 0) return BAD;
        return menu.admissionPermitted() ? GOOD : WARN;
    }

    private static String capacityText(int used, int capacity) {
        return capacity <= 0 ? "—" : used + " / " + capacity + " units";
    }

    private void drawPressureBar(GuiGraphics graphics, int x, int y, int width, int percent) {
        int bounded = Math.max(0, Math.min(100, percent));
        graphics.fill(x, y, x + width, y + 6, 0xFF171C21);
        int filled = Math.round(width * bounded / 100.0F);
        int color = bounded >= 90 ? BAD : (bounded >= 70 ? WARN : GOOD);
        if (filled > 0) graphics.fill(x, y, x + filled, y + 6, color);
        graphics.drawString(font, bounded + "%", x + width + 8, y - 1, TEXT, false);
    }
}
