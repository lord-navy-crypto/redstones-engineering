package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.operations.OperationMaintenanceCompletionEvidence;
import dev.redstoneengineering.operations.OperationResourceMaintenanceSnapshot;
import dev.redstoneengineering.operations.world.OperationMaintenanceWorldState;
import dev.redstoneengineering.operations.world.OperationPlantEvent;
import dev.redstoneengineering.operations.world.OperationPlantSavedData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Local/manual regression coverage for persistent maintenance start/complete history. */
public final class RseMaintenancePersistenceGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseMaintenancePersistenceGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void maintenanceDueStartCompleteAndRoundTripPersist(GameTestHelper helper) {
        long now = helper.getLevel().getGameTime();
        String resourceId = "maintenance_validation_" + now;
        String maintenanceId = "pm_" + now;

        OperationResourceMaintenanceSnapshot due = new OperationResourceMaintenanceSnapshot(
                resourceId,
                OperationResourceMaintenanceSnapshot.State.MAINTENANCE_DUE,
                maintenanceId,
                PortQuality.VALID,
                false
        );
        var observed = OperationMaintenanceWorldState.observe(
                helper.getLevel(), due, now, "LOCAL_VALIDATION_DUE");
        if (observed.verdict() != OperationMaintenanceWorldState.Verdict.OBSERVED) {
            helper.fail("Maintenance due observation was not persisted: " + observed.reason());
            return;
        }

        var started = OperationMaintenanceWorldState.start(helper.getLevel(), resourceId, now + 1);
        OperationResourceMaintenanceSnapshot inProgress = OperationMaintenanceWorldState.snapshot(
                helper.getLevel(), resourceId);
        if (started.verdict() != OperationMaintenanceWorldState.Verdict.STARTED
                || inProgress == null
                || inProgress.state() != OperationResourceMaintenanceSnapshot.State.IN_PROGRESS
                || !maintenanceId.equals(inProgress.maintenanceId())) {
            helper.fail("Maintenance start did not persist IN_PROGRESS state");
            return;
        }

        OperationMaintenanceCompletionEvidence completion = new OperationMaintenanceCompletionEvidence(
                resourceId,
                maintenanceId,
                PortQuality.VALID,
                true,
                true,
                false
        );
        var completed = OperationMaintenanceWorldState.complete(helper.getLevel(), completion, now + 2);
        OperationResourceMaintenanceSnapshot available = OperationMaintenanceWorldState.snapshot(
                helper.getLevel(), resourceId);
        if (completed.verdict() != OperationMaintenanceWorldState.Verdict.COMPLETED
                || available == null
                || available.state() != OperationResourceMaintenanceSnapshot.State.AVAILABLE
                || available.maintenanceId() != null
                || !available.productionReady()) {
            helper.fail("Maintenance completion did not persist AVAILABLE production-ready state");
            return;
        }

        OperationPlantSavedData live = OperationPlantSavedData.get(helper.getLevel());
        long resourceEvents = live.plantEvents(OperationPlantEvent.Type.MAINTENANCE).stream()
                .filter(event -> resourceId.equals(event.subjectId()))
                .count();
        if (resourceEvents < 3) {
            helper.fail("Maintenance lifecycle did not retain observation/start/complete history");
            return;
        }

        CompoundTag serialized = live.save(new CompoundTag(), helper.getLevel().registryAccess());
        OperationPlantSavedData restored = OperationPlantSavedData.load(
                serialized, helper.getLevel().registryAccess());
        OperationResourceMaintenanceSnapshot restoredSnapshot = restored.maintenanceSnapshot(resourceId);
        long restoredEvents = restored.plantEvents(OperationPlantEvent.Type.MAINTENANCE).stream()
                .filter(event -> resourceId.equals(event.subjectId()))
                .count();
        if (restoredSnapshot == null
                || restoredSnapshot.state() != OperationResourceMaintenanceSnapshot.State.AVAILABLE
                || !restoredSnapshot.productionReady()
                || restoredEvents != resourceEvents) {
            helper.fail("Maintenance current state/history did not survive NBT round-trip");
            return;
        }

        helper.succeed();
    }
}
