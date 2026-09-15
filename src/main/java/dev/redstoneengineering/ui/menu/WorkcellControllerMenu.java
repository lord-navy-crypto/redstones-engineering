package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.EngineeringSystemsModule;
import dev.redstoneengineering.block.WorkcellControllerBlock;
import dev.redstoneengineering.operations.world.OperationWorkcellStore;
import dev.redstoneengineering.ui.EngineeringUiRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.DataSlot;

/** Server-authoritative readback for one explicitly bound production workcell. */
public final class WorkcellControllerMenu extends EngineeringDeviceMenu {
    private final DataSlot boundResources = trackedInt();
    private final DataSlot validResources = trackedInt();
    private final DataSlot runningResources = trackedInt();
    private final DataSlot faultResources = trackedInt();
    private final DataSlot capacityEvidence = trackedInt();
    private final DataSlot admissionCode = trackedInt();
    private final DataSlot activeAssignments = trackedInt();
    private final DataSlot queuePressure = trackedInt();
    private final DataSlot setup = trackedInt();
    private final DataSlot maintenance = trackedInt();

    public WorkcellControllerMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos());
    }

    public WorkcellControllerMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(EngineeringUiRegistration.WORKCELL_CONTROLLER.get(), containerId, inventory, pos,
                EngineeringSystemsModule.WORKCELL_CONTROLLER.get());
        activeAssignments.set(-1);
        queuePressure.set(-1);
        setup.set(0);
        maintenance.set(0);
        if (!level.isClientSide) refreshAuthoritativeSnapshot();
    }

    @Override
    protected void refreshAuthoritativeSnapshot() {
        if (!(level instanceof ServerLevel)) return;
        WorkcellControllerBlock.Snapshot snapshot = WorkcellControllerBlock.inspect(level, blockPos);
        boundResources.set(snapshot.boundResources());
        validResources.set(snapshot.validResources());
        runningResources.set(snapshot.runningResources());
        faultResources.set(snapshot.faultResources());
        capacityEvidence.set(snapshot.capacityEvidenceAvailable() ? 1 : 0);
        admissionCode.set(admissionReasonCode(snapshot.admissionReason()));

        // These stay explicitly unavailable until their server-owned world state exists.
        // Running resources are not silently relabeled as active Operations assignments.
        activeAssignments.set(-1);
        queuePressure.set(snapshot.capacityEvidenceAvailable() ? snapshot.queuePressure() : -1);
        setup.set(0);
        maintenance.set(0);
    }

    public String workcellId() { return WorkcellControllerBlock.workcellId(blockPos); }
    public int boundResourceCount() { return boundResources.get(); }
    public int validResourceCount() { return validResources.get(); }
    public int runningResourceCount() { return runningResources.get(); }
    public int faultResourceCount() { return faultResources.get(); }
    public boolean capacityEvidenceAvailable() { return capacityEvidence.get() != 0; }
    public int activeAssignments() { return activeAssignments.get(); }
    public int queuePressure() { return queuePressure.get(); }
    public boolean setupEvidenceAvailable() { return setup.get() != 0; }
    public boolean maintenanceEvidenceAvailable() { return maintenance.get() != 0; }

    public String admissionReason() {
        return switch (admissionCode.get()) {
            case 1 -> "WORKCELL_ADMISSION_PERMIT";
            case 2 -> "WORKCELL_FAULT_ACTIVE";
            case 3 -> "WORKCELL_RESOURCE_CAPACITY_REACHED";
            case 4 -> "OUTPUT_BUFFER_CAPACITY_REACHED";
            case 5 -> "RESOURCE_EVIDENCE_INVALID";
            case 6 -> "NO_BOUND_RESOURCES";
            default -> "WORKCELL_CAPACITY_EVIDENCE_INVALID";
        };
    }

    /** Explicit binding is a server-owned OperationWorkcellStore concern, not client state. */
    public static OperationWorkcellStore.Decision bindResource(
            ServerLevel level, BlockPos controllerPos, BlockPos resourcePos) {
        return WorkcellControllerBlock.bindResource(level, controllerPos, resourcePos);
    }

    private static int admissionReasonCode(String reason) {
        return switch (reason) {
            case "WORKCELL_ADMISSION_PERMIT" -> 1;
            case "WORKCELL_FAULT_ACTIVE" -> 2;
            case "WORKCELL_RESOURCE_CAPACITY_REACHED" -> 3;
            case "OUTPUT_BUFFER_CAPACITY_REACHED" -> 4;
            case "RESOURCE_EVIDENCE_INVALID" -> 5;
            case "NO_BOUND_RESOURCES" -> 6;
            default -> 0;
        };
    }
}
