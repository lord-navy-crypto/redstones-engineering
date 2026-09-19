package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.operations.OperationOutputSnapshot;
import dev.redstoneengineering.operations.OperationTransportDemand;
import dev.redstoneengineering.operations.world.OperationPlantSavedData;
import dev.redstoneengineering.operations.world.OperationRobotTransportWorldState;
import dev.redstoneengineering.operations.world.OperationTransportRuntimeRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Local/manual regression coverage for persistent AMR mission correlation. */
public final class RseAmrWorldLogisticsPersistenceGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseAmrWorldLogisticsPersistenceGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void transportReadyStartedDeliveredSurvivesNbtRoundTrip(GameTestHelper helper) {
        long now = helper.getLevel().getGameTime();
        long suffix = Math.max(1L, now);
        long missionId = 9_000_000L + suffix;
        long outputId = 8_000_000L + suffix;
        long jobId = 7_000_000L + suffix;
        BlockPos source = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos target = helper.absolutePos(new BlockPos(3, 1, 3));

        OperationOutputSnapshot output = new OperationOutputSnapshot(
                outputId,
                jobId,
                "amr_validation_resource_" + suffix,
                4,
                source,
                PortQuality.VALID,
                true,
                true,
                false
        );
        OperationTransportDemand demand = new OperationTransportDemand(
                missionId,
                outputId,
                source,
                target,
                4,
                60
        );

        var ready = OperationRobotTransportWorldState.prepare(helper.getLevel(), output, demand, now);
        if (ready.verdict() != OperationRobotTransportWorldState.Verdict.READY
                || ready.record() == null
                || ready.record().status() != OperationTransportRuntimeRecord.Status.READY) {
            helper.fail("AMR transport prepare did not persist READY: " + ready.reason());
            return;
        }

        // This persistence GameTest isolates the SavedData lifecycle. The real robot route admission
        // path is guarded by OperationRobotTransportWorldState.startRoute and its static verifier;
        // full entity movement remains a local Validation Factory test.
        OperationTransportRuntimeRecord started = ready.record().transition(
                OperationTransportRuntimeRecord.Status.STARTED,
                now + 1,
                "local-amr-" + suffix
        );
        OperationPlantSavedData live = OperationPlantSavedData.get(helper.getLevel());
        if (!live.putTransportRecord(started)) {
            helper.fail("AMR STARTED persistence was rejected");
            return;
        }

        var delivered = OperationRobotTransportWorldState.markDelivered(
                helper.getLevel(), missionId, outputId, jobId, now + 2);
        if (delivered.verdict() != OperationRobotTransportWorldState.Verdict.DELIVERED
                || delivered.record() == null
                || delivered.record().status() != OperationTransportRuntimeRecord.Status.DELIVERED) {
            helper.fail("AMR transport did not persist DELIVERED: " + delivered.reason());
            return;
        }

        CompoundTag serialized = live.save(new CompoundTag(), helper.getLevel().registryAccess());
        OperationPlantSavedData restored = OperationPlantSavedData.load(
                serialized, helper.getLevel().registryAccess());
        OperationTransportRuntimeRecord restoredRecord = restored.transportRecord(missionId);
        if (restoredRecord == null
                || restoredRecord.status() != OperationTransportRuntimeRecord.Status.DELIVERED
                || restoredRecord.outputId() != outputId
                || restoredRecord.jobId() != jobId
                || !source.equals(restoredRecord.source())
                || !target.equals(restoredRecord.target())
                || !("local-amr-" + suffix).equals(restoredRecord.robotId())) {
            helper.fail("AMR transport correlation did not survive NBT round-trip");
            return;
        }

        helper.succeed();
    }
}
