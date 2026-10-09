package dev.redstoneengineering.ui.ldlib;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.redstoneengineering.ui.menu.WorkcellControllerMenu;
import net.minecraft.world.entity.player.Player;

/** Read-only LDLib2 workcell admission/evidence HMI. */
public final class WorkcellControllerLdUi {
    private WorkcellControllerLdUi() {}

    public static ModularUI create(WorkcellControllerMenu m, Player player) {
        var root = new UIElement().addClass("panel_bg");
        root.layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(8).gapAll(6));
        root.addChildren(
                RseLdUiComponents.title("WORKCELL CONTROLLER"),
                RseLdUiComponents.tabbedWorkspace(
                        630, 400, 850,
                        new String[]{"Overview", "Configure", "Diagnostics"},
                        new UIElement[]{
                                RseLdUiComponents.workspacePage(
                                        flowPanel(m),
                                        admissionPanel(m)
                                ),
                                RseLdUiComponents.workspacePage(
                                        portsPanel(m),
                                        authorityPanel()
                                ),
                                RseLdUiComponents.workspacePage(
                                        RseLdUiComponents.authorityFooter()
                                )
                        }
                )
        );
        return RseLdUiComponents.responsiveUi(root, player, 650, 440);
    }

    private static UIElement flowPanel(WorkcellControllerMenu m) {
        return new UIElement().addClass("panel_bg").layout(l -> l.paddingAll(5).gapAll(3)).addChildren(
                new Label().setText("INPUT → WORKCELL → OUTPUT"),
                RseLdUiComponents.liveRow("IDENTITY", "Workcell", m::workcellId),
                RseLdUiComponents.liveRow("INPUT", "Input WIP", () ->
                        m.capacityEvidenceAvailable()
                                ? capacityText(m.inputBufferUsedUnits(), m.inputBufferCapacityUnits())
                                        + " • " + evidencePercent(true, m.inputWipPressurePercent())
                                : "NOT READY • input buffer/capacity evidence missing"),
                RseLdUiComponents.liveRow("WORKCELL", "resources", () ->
                        m.inspectionReady()
                                ? "valid=" + m.validResourceCount() + " • running=" + m.runningResourceCount()
                                        + " • bound=" + m.boundResourceCount()
                                : "NOT READY • awaiting server workcell inspection"),
                RseLdUiComponents.liveRow("OUTPUT", "Output WIP", () ->
                        m.capacityEvidenceAvailable()
                                ? capacityText(m.outputBufferUsedUnits(), m.outputBufferCapacityUnits())
                                        + " • " + evidencePercent(true, m.outputWipPressurePercent())
                                : "NOT READY • output buffer/capacity evidence missing")
        );
    }

    private static UIElement admissionPanel(WorkcellControllerMenu m) {
        return new UIElement().addClass("panel_bg").layout(l -> l.paddingAll(5).gapAll(3)).addChildren(
                new Label().setText("PIONEER PATTERN • WORKCELL ADMISSION GATE"),
                RseLdUiComponents.formulaCard(() ->
                        "PERMIT ⇔ valid capacity evidence ∧ no fault ∧ output space ∧ resource capacity"),
                RseLdUiComponents.fixedRow("binding authority", () -> "Operations Binding Tool",
                        "resources + INPUT/OUTPUT buffers"),
                RseLdUiComponents.liveRow("EVIDENCE", "server inspection", () ->
                        m.inspectionReady() ? "AVAILABLE" : "NOT READY • snapshot pending"),
                RseLdUiComponents.liveRow("MEASURED", "BOUND RESOURCES", () ->
                        m.inspectionReady() ? m.validResourceCount() + "/" + m.boundResourceCount() : "NOT READY"),
                RseLdUiComponents.liveRow("DERIVED", "queue pressure", () ->
                        !m.inspectionReady() || !m.capacityEvidenceAvailable() || m.queuePressure() < 0
                                ? "UNAVAILABLE • capacity evidence missing" : m.queuePressure() + "/15"),
                RseLdUiComponents.liveRow("ADMISSION", "admission", () ->
                        !m.inspectionReady() ? "NOT READY • no inspected decision"
                                : m.admissionPermitted() ? "PERMIT" : "HOLD"),
                RseLdUiComponents.liveRow("EVIDENCE", "reason", () ->
                        m.inspectionReady() ? m.admissionReason() : "NOT READY • no server assessment"),
                RseLdUiComponents.liveRow("SAFETY", "fault resources", () ->
                        m.inspectionReady() ? Integer.toString(m.faultResourceCount()) : "NOT READY"),
                RseLdUiComponents.liveRow("EVIDENCE", "SETUP", () ->
                        m.setupEvidenceAvailable() ? "AVAILABLE" : "WITHHELD • WORLD EVIDENCE NOT PERSISTED"),
                RseLdUiComponents.liveRow("EVIDENCE", "MAINTENANCE", () ->
                        m.maintenanceEvidenceAvailable() ? "AVAILABLE" : "WITHHELD • WORLD EVIDENCE NOT PERSISTED")
        );
    }

    private static UIElement portsPanel(WorkcellControllerMenu m) {
        return new UIElement().addClass("panel_bg").layout(l -> l.paddingAll(5).gapAll(3)).addChildren(
                new Label().setText("LOW-CARDINALITY REDSTONE PROJECTION"),
                RseLdUiComponents.liveRow("NORTH", "ACTIVE", () ->
                        m.inspectionReady() ? (m.runningResourceCount() > 0 ? "HIGH" : "LOW") : "UNVERIFIED • fail-closed LOW"),
                RseLdUiComponents.liveRow("SOUTH", "PERMIT", () ->
                        m.inspectionReady() ? (m.admissionPermitted() ? "HIGH" : "LOW") : "UNVERIFIED • fail-closed LOW"),
                RseLdUiComponents.liveRow("EAST", "HOLD", () ->
                        m.inspectionReady() ? (m.admissionHeld() ? "HIGH" : "LOW") : "UNVERIFIED • awaiting inspection"),
                RseLdUiComponents.liveRow("WEST", "FAULT", () ->
                        m.inspectionReady() ? (m.faultResourceCount() > 0 ? "HIGH" : "LOW") : "UNVERIFIED • no fault assessment"),
                RseLdUiComponents.liveRow("UP", "QUEUE PRESSURE", () ->
                        !m.inspectionReady() || !m.capacityEvidenceAvailable() || m.queuePressure() < 0
                                ? "UNAVAILABLE • no verified buffer capacity" : m.queuePressure() + " / 15"),
                new Label().setText("Job/resource/lot identities never travel through analog redstone.")
        );
    }

    private static UIElement authorityPanel() {
        return new UIElement().addClass("panel_bg").layout(l -> l.paddingAll(5).gapAll(3)).addChildren(
                new Label().setText("AUTHORITY BOUNDARY"),
                new Label().setText("Binding is external server authority: this HMI cannot rewrite resources, buffers, lot identity, scheduling, setup or maintenance state."),
                new Label().setText("Controller stores no duplicate scheduler or bottleneck ranking."),
                new Label().setText("Admission is delegated to OperationWorkcellAdmissionAssessment."),
                new Label().setText("Missing setup, maintenance, or buffer evidence remains unavailable.")
        );
    }

    private static String capacityText(int used, int capacity) {
        return capacity <= 0 ? "—" : used + " / " + capacity + " units";
    }

    private static String evidencePercent(boolean available, int percent) {
        return available ? percent + "%" : "UNAVAILABLE";
    }
}
