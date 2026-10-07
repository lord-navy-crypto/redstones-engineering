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
        root.layout(l -> l.width(650).paddingAll(8).gapAll(6));
        root.addChildren(
                RseLdUiComponents.title("WORKCELL CONTROLLER"),
                flowPanel(m),
                admissionPanel(m),
                portsPanel(m),
                authorityPanel(),
                RseLdUiComponents.authorityFooter()
        );
        return ModularUI.of(UI.of(root, StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.GDP)), player);
    }

    private static UIElement flowPanel(WorkcellControllerMenu m) {
        return new UIElement().addClass("panel_bg").layout(l -> l.paddingAll(5).gapAll(3)).addChildren(
                new Label().setText("INPUT → WORKCELL → OUTPUT"),
                RseLdUiComponents.liveRow("IDENTITY", "Workcell", m::workcellId),
                RseLdUiComponents.liveRow("INPUT", "Input WIP", () ->
                        capacityText(m.inputBufferUsedUnits(), m.inputBufferCapacityUnits())
                                + " • " + evidencePercent(m.capacityEvidenceAvailable(), m.inputWipPressurePercent())),
                RseLdUiComponents.liveRow("WORKCELL", "resources", () ->
                        m.validResourceCount() + "/" + m.runningResourceCount() + " valid/running of " + m.boundResourceCount()),
                RseLdUiComponents.liveRow("OUTPUT", "Output WIP", () ->
                        capacityText(m.outputBufferUsedUnits(), m.outputBufferCapacityUnits())
                                + " • " + evidencePercent(m.capacityEvidenceAvailable(), m.outputWipPressurePercent()))
        );
    }

    private static UIElement admissionPanel(WorkcellControllerMenu m) {
        return new UIElement().addClass("panel_bg").layout(l -> l.paddingAll(5).gapAll(3)).addChildren(
                new Label().setText("PIONEER PATTERN • WORKCELL ADMISSION GATE"),
                RseLdUiComponents.formulaCard(() ->
                        "PERMIT ⇔ valid capacity evidence ∧ no fault ∧ output space ∧ resource capacity"),
                RseLdUiComponents.fixedRow("binding authority", () -> "Operations Binding Tool",
                        "resources + INPUT/OUTPUT buffers"),
                RseLdUiComponents.liveRow("MEASURED", "BOUND RESOURCES", () ->
                        m.validResourceCount() + "/" + m.boundResourceCount()),
                RseLdUiComponents.liveRow("DERIVED", "queue pressure", () ->
                        m.queuePressure() < 0 ? "UNAVAILABLE" : m.queuePressure() + "/15"),
                RseLdUiComponents.liveRow("ADMISSION", "state", () -> m.admissionPermitted() ? "PERMIT" : "HOLD"),
                RseLdUiComponents.liveRow("EVIDENCE", "reason", m::admissionReason),
                RseLdUiComponents.liveRow("SAFETY", "fault resources", () -> Integer.toString(m.faultResourceCount()))
        );
    }

    private static UIElement portsPanel(WorkcellControllerMenu m) {
        return new UIElement().addClass("panel_bg").layout(l -> l.paddingAll(5).gapAll(3)).addChildren(
                new Label().setText("LOW-CARDINALITY REDSTONE PROJECTION"),
                RseLdUiComponents.liveRow("NORTH", "ACTIVE", () -> m.runningResourceCount() > 0 ? "HIGH" : "LOW"),
                RseLdUiComponents.liveRow("SOUTH", "PERMIT", () -> m.admissionPermitted() ? "HIGH" : "LOW"),
                RseLdUiComponents.liveRow("EAST", "HOLD", () -> m.admissionHeld() ? "HIGH" : "LOW"),
                RseLdUiComponents.liveRow("WEST", "FAULT", () -> m.faultResourceCount() > 0 ? "HIGH" : "LOW"),
                RseLdUiComponents.liveRow("UP", "QUEUE PRESSURE", () ->
                        m.queuePressure() < 0 ? "UNAVAILABLE" : m.queuePressure() + " / 15"),
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
