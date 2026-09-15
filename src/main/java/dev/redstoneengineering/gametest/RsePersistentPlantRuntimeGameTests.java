package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.operations.OperationCompletionEvidence;
import dev.redstoneengineering.operations.OperationDispatchRuntime;
import dev.redstoneengineering.operations.OperationJob;
import dev.redstoneengineering.operations.OperationQualityInspectionEvidence;
import dev.redstoneengineering.operations.OperationQueueRuntime;
import dev.redstoneengineering.operations.OperationQueueSnapshot;
import dev.redstoneengineering.operations.OperationResourceMaintenanceSnapshot;
import dev.redstoneengineering.operations.OperationResourceSnapshot;
import dev.redstoneengineering.operations.OperationTransportDemand;
import dev.redstoneengineering.operations.world.OperationJobLifecycleRecord;
import dev.redstoneengineering.operations.world.OperationPlantEvent;
import dev.redstoneengineering.operations.world.OperationPlantRuntimeRecorder;
import dev.redstoneengineering.operations.world.OperationPlantSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;

/** Local/manual validation for the world-backed Persistent Plant Runtime. */
public final class RsePersistentPlantRuntimeGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RsePersistentPlantRuntimeGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void plantRuntimePersistsJobQueueQualityMaintenanceDeliveryAndLogistics(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 1, 2));
        long admittedTick = helper.getLevel().getGameTime();
        long jobId = Math.floorMod(anchor.asLong() ^ admittedTick ^ 0x525345L, Long.MAX_VALUE);
        String queueId = "validation_queue_" + jobId;
        String resourceId = "validation_resource_" + jobId;
        String processId = "validation_process";

        OperationJob job = new OperationJob(jobId, processId, 4, 70, admittedTick, admittedTick + 20);
        OperationQueueSnapshot queue = OperationQueueSnapshot.empty(4);

        OperationQueueRuntime.Decision enqueue = OperationQueueRuntime.enqueue(queue, job);
        if (enqueue.verdict() != OperationQueueRuntime.Verdict.ENQUEUED
                || !OperationPlantRuntimeRecorder.recordQueueEnqueue(helper.getLevel(), queueId, job, enqueue, admittedTick)) {
            helper.fail("Persistent plant runtime did not admit/enqueue validation job", new BlockPos(2, 1, 2));
            return;
        }
        queue = enqueue.nextState();

        OperationResourceSnapshot resource = new OperationResourceSnapshot(
                resourceId,
                Set.of(processId),
                OperationResourceSnapshot.State.AVAILABLE,
                PortQuality.VALID
        );
        OperationQueueRuntime.Decision dispatch = OperationQueueRuntime.dispatch(
                queue,
                List.of(resource),
                admittedTick + 1,
                OperationDispatchRuntime.Policy.FIFO
        );
        if (dispatch.verdict() != OperationQueueRuntime.Verdict.ASSIGNED
                || !OperationPlantRuntimeRecorder.recordQueueDispatch(helper.getLevel(), queueId, dispatch, admittedTick + 1)) {
            helper.fail("Persistent plant runtime did not record dispatch", new BlockPos(2, 1, 2));
            return;
        }
        queue = dispatch.nextState();

        if (!OperationPlantRuntimeRecorder.recordJobInProcess(helper.getLevel(), jobId, resourceId, admittedTick + 2)) {
            helper.fail("Persistent plant runtime did not record in-process transition", new BlockPos(2, 1, 2));
            return;
        }

        OperationCompletionEvidence completionEvidence = new OperationCompletionEvidence(
                jobId,
                resourceId,
                job.quantity(),
                PortQuality.VALID,
                true,
                true,
                true,
                false
        );
        OperationQueueRuntime.Decision completion = OperationQueueRuntime.complete(queue, completionEvidence);
        if (completion.verdict() != OperationQueueRuntime.Verdict.COMPLETED
                || !OperationPlantRuntimeRecorder.recordQueueCompletion(helper.getLevel(), queueId, completion, admittedTick + 10)) {
            helper.fail("Persistent plant runtime did not record completion/delivery", new BlockPos(2, 1, 2));
            return;
        }

        OperationQualityInspectionEvidence quality = new OperationQualityInspectionEvidence(
                jobId,
                jobId,
                4,
                3,
                1,
                0,
                PortQuality.VALID,
                true,
                false
        );
        if (!OperationPlantRuntimeRecorder.recordQualityInspection(
                helper.getLevel(), "inspection:" + jobId, quality, admittedTick + 11)) {
            helper.fail("Persistent plant runtime did not record quality evidence", new BlockPos(2, 1, 2));
            return;
        }

        OperationResourceMaintenanceSnapshot maintenanceDue = new OperationResourceMaintenanceSnapshot(
                resourceId,
                OperationResourceMaintenanceSnapshot.State.MAINTENANCE_DUE,
                "pm:" + jobId,
                PortQuality.VALID,
                false
        );
        if (!OperationPlantRuntimeRecorder.recordMaintenanceSnapshot(
                helper.getLevel(), maintenanceDue, admittedTick + 12, "VALIDATION_PM_DUE")) {
            helper.fail("Persistent plant runtime did not record maintenance history", new BlockPos(2, 1, 2));
            return;
        }

        OperationTransportDemand demand = new OperationTransportDemand(
                jobId,
                jobId,
                anchor,
                anchor.east(2),
                4,
                70
        );
        if (!OperationPlantRuntimeRecorder.recordLogisticsDemand(helper.getLevel(), demand, admittedTick + 13)
                || !OperationPlantRuntimeRecorder.recordLogisticsEvent(
                        helper.getLevel(), jobId, jobId, admittedTick + 14, "DELIVERED", "validation dock receipt")) {
            helper.fail("Persistent plant runtime did not record logistics history", new BlockPos(2, 1, 2));
            return;
        }

        OperationPlantSavedData live = OperationPlantSavedData.get(helper.getLevel());
        OperationJobLifecycleRecord lifecycle = live.jobLifecycle(jobId);
        if (lifecycle == null || lifecycle.status() != OperationJobLifecycleRecord.Status.COMPLETED
                || lifecycle.completionTick() != admittedTick + 10 || !lifecycle.onTime()) {
            helper.fail("Completed on-time job lifecycle was not retained", new BlockPos(2, 1, 2));
            return;
        }
        if (!hasEvent(live, OperationPlantEvent.Type.QUEUE, jobId)
                || !hasEvent(live, OperationPlantEvent.Type.QUALITY, jobId)
                || !hasSubjectEvent(live, OperationPlantEvent.Type.MAINTENANCE, resourceId)
                || !hasEvent(live, OperationPlantEvent.Type.DELIVERY, jobId)
                || !hasSubjectEvent(live, OperationPlantEvent.Type.LOGISTICS, "mission:" + jobId)) {
            helper.fail("Persistent plant runtime is missing one or more plant-wide history domains", new BlockPos(2, 1, 2));
            return;
        }

        CompoundTag persisted = live.save(new CompoundTag(), helper.getLevel().registryAccess());
        OperationPlantSavedData restored = OperationPlantSavedData.load(persisted, helper.getLevel().registryAccess());
        OperationJobLifecycleRecord restoredLifecycle = restored.jobLifecycle(jobId);
        if (restoredLifecycle == null
                || restoredLifecycle.status() != OperationJobLifecycleRecord.Status.COMPLETED
                || !restoredLifecycle.onTime()
                || !hasEvent(restored, OperationPlantEvent.Type.QUALITY, jobId)
                || !hasSubjectEvent(restored, OperationPlantEvent.Type.MAINTENANCE, resourceId)
                || !hasSubjectEvent(restored, OperationPlantEvent.Type.LOGISTICS, "mission:" + jobId)) {
            helper.fail("Plant runtime evidence did not survive NBT save/load round-trip", new BlockPos(2, 1, 2));
            return;
        }

        helper.succeed();
    }

    private static boolean hasEvent(OperationPlantSavedData data, OperationPlantEvent.Type type, long jobId) {
        return data.plantEvents(type).stream().anyMatch(event -> event.jobId() == jobId);
    }

    private static boolean hasSubjectEvent(OperationPlantSavedData data, OperationPlantEvent.Type type, String subjectId) {
        return data.plantEvents(type).stream().anyMatch(event -> subjectId.equals(event.subjectId()));
    }
}
