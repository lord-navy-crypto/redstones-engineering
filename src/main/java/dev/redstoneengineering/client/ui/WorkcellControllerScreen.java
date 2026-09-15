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
        statusBadge(graphics, "WORKCELL CONTROLLER", menu.faultResourceCount() > 0 ? BAD : INFO, 16, 78);
        labelValue(graphics, "Workcell", menu.workcellId(), 100);
        labelValue(graphics, "BOUND RESOURCES", menu.boundResourceCount() + "", 118);
        labelValue(graphics, "Valid / running", menu.validResourceCount() + " / " + menu.runningResourceCount(), 136);
        statusLine(graphics, "ADMISSION", menu.admissionReason(), menu.capacityEvidenceAvailable() ? GOOD : WARN, 154);
        labelValue(graphics, "Active assignments", optional(menu.activeAssignments()), 172);
        labelValue(graphics, "Queue pressure", menu.queuePressure() < 0 ? "—" : menu.queuePressure() + " / 15", 190);
        safeText(graphics, menu.capacityEvidenceAvailable()
                ? "Finite-capacity evidence available."
                : "Capacity evidence incomplete: no Industrial Buffer world state is fabricated.", 16, 214, MUTED);
    }

    private void renderPorts(GuiGraphics graphics) {
        statusLine(graphics, "NORTH • ACTIVE", menu.runningResourceCount() > 0 ? "HIGH" : "LOW", INFO, 84);
        statusLine(graphics, "SOUTH • PERMIT", "authority: capacity assessment", INFO, 104);
        statusLine(graphics, "EAST • HOLD", menu.capacityEvidenceAvailable() ? "ASSESSED" : "FAIL-CLOSED", WARN, 124);
        statusLine(graphics, "WEST • FAULT", menu.faultResourceCount() > 0 ? "HIGH" : "LOW", menu.faultResourceCount() > 0 ? BAD : GOOD, 144);
        statusLine(graphics, "UP • QUEUE PRESSURE", menu.queuePressure() < 0 ? "UNAVAILABLE" : menu.queuePressure() + " / 15", menu.queuePressure() < 0 ? WARN : INFO, 164);
        safeText(graphics, "Job/resource/lot identities never travel through analog redstone.", 16, 190, MUTED);
    }

    private void renderConfigure(GuiGraphics graphics) {
        statusBadge(graphics, "EXPLICIT BINDING", INFO, 16, 82);
        labelValue(graphics, "Bound resources", Integer.toString(menu.boundResourceCount()), 108);
        safeText(graphics, "Binding/unbinding is validated server-side by OperationWorkcellStore.", 16, 132, TEXT);
        safeText(graphics, "No proximity discovery is performed by this controller.", 16, 152, MUTED);
        safeText(graphics, "Interactive target selection is the next UI slice; current state is read-only.", 16, 172, MUTED);
    }

    private void renderDiagnostics(GuiGraphics graphics) {
        statusLine(graphics, "Resource evidence", menu.validResourceCount() + " / " + menu.boundResourceCount() + " VALID",
                menu.validResourceCount() == menu.boundResourceCount() && menu.boundResourceCount() > 0 ? GOOD : WARN, 82);
        statusLine(graphics, "Fault resources", Integer.toString(menu.faultResourceCount()), menu.faultResourceCount() > 0 ? BAD : GOOD, 102);
        statusLine(graphics, "SETUP", menu.setupEvidenceAvailable() ? "AVAILABLE" : "UNAVAILABLE", menu.setupEvidenceAvailable() ? GOOD : WARN, 122);
        statusLine(graphics, "MAINTENANCE", menu.maintenanceEvidenceAvailable() ? "AVAILABLE" : "UNAVAILABLE", menu.maintenanceEvidenceAvailable() ? GOOD : WARN, 142);
        statusLine(graphics, "Capacity evidence", menu.capacityEvidenceAvailable() ? "VALID" : "INCOMPLETE", menu.capacityEvidenceAvailable() ? GOOD : WARN, 162);
        safeText(graphics, "Dispatch/changeover/maintenance decisions remain owned by their Operations runtimes.", 16, 190, MUTED);
    }

    private void renderHistory(GuiGraphics graphics) {
        statusBadge(graphics, "AUTHORITY BOUNDARY", INFO, 16, 82);
        safeText(graphics, "Controller stores no duplicate scheduler or bottleneck ranking.", 16, 108, TEXT);
        safeText(graphics, "Missing setup, maintenance, queue, or buffer evidence remains unavailable.", 16, 128, TEXT);
        safeText(graphics, "Completed work still requires explicit completion evidence from the resource.", 16, 148, MUTED);
    }

    private static String optional(int value) {
        return value < 0 ? "—" : Integer.toString(value);
    }
}
