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
    private final DataSlot inputBufferUsedUnits = trackedInt();
    private final DataSlot inputBufferCapacityUnits = trackedInt();
    private final DataSlot outputBufferUsedUnits = trackedInt();
    private final DataSlot outputBufferCapacityUnits = trackedInt();
    private final DataSlot inputWipPressurePercent = trackedInt();
    private final DataSlot outputWipPressurePercent = trackedInt();
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
        inputBufferUsedUnits.set(snapshot.inputBufferUsedUnits());
        inputBufferCapacityUnits.set(snapshot.inputBufferCapacityUnits());
        outputBufferUsedUnits.set(snapshot.outputBufferUsedUnits());
        outputBufferCapacityUnits.set(snapshot.outputBufferCapacityUnits());
        inputWipPressurePercent.set(snapshot.inputWipPressurePercent());
        outputWipPressurePercent.set(snapshot.outputWipPressurePercent());

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
    public int inputBufferUsedUnits() { return inputBufferUsedUnits.get(); }
    public int inputBufferCapacityUnits() { return inputBufferCapacityUnits.get(); }
    public int outputBufferUsedUnits() { return outputBufferUsedUnits.get(); }
    public int outputBufferCapacityUnits() { return outputBufferCapacityUnits.get(); }
    public int inputWipPressurePercent() { return inputWipPressurePercent.get(); }
    public int outputWipPressurePercent() { return outputWipPressurePercent.get(); }
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
            case 7 -> "WORKCELL_BUFFER_BINDING_MISSING";
            case 8 -> "INPUT_BUFFER_MISSING";
            case 9 -> "OUTPUT_BUFFER_MISSING";
            default -> "WORKCELL_CAPACITY_EVIDENCE_INVALID";
        };
    }

    public boolean admissionPermitted() { return admissionCode.get() == 1; }
    public boolean admissionHeld() { return !admissionPermitted(); }

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
            case "WORKCELL_BUFFER_BINDING_MISSING" -> 7;
            case "INPUT_BUFFER_MISSING" -> 8;
            case "OUTPUT_BUFFER_MISSING" -> 9;
            default -> 0;
        };
    }
}
