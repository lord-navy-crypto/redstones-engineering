package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.operations.OperationCompletionEvidence;
import dev.redstoneengineering.operations.OperationDispatchRuntime;
import dev.redstoneengineering.operations.OperationJob;
import dev.redstoneengineering.operations.OperationResourceSnapshot;
import dev.redstoneengineering.operations.world.OperationJobLifecycleRecord;
import dev.redstoneengineering.operations.world.OperationPlantEvent;
import dev.redstoneengineering.operations.world.OperationPlantSavedData;
import dev.redstoneengineering.operations.world.OperationQueueWorldState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;

/** Local/manual regression coverage for persistent queue dispatch and completion lifecycle. */
public final class RseQueuePersistenceGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseQueuePersistenceGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void persistentQueueSurvivesDispatchCompletionAndNbtRoundTrip(GameTestHelper helper) {
        long now = helper.getLevel().getGameTime();
        long baseId = Math.floorMod(helper.absolutePos(net.minecraft.core.BlockPos.ZERO).asLong()
                ^ now ^ 0x51554555454cL, 800_000_000L) + 200_000L;
        String queueId = "queue_lifecycle:" + baseId;
        String resourceId = "queue_resource:" + baseId;
        long jobId = baseId + 1L;

        OperationQueueWorldState.Decision created = OperationQueueWorldState.create(
                helper.getLevel(), queueId, 4);
        if (created.verdict() != OperationQueueWorldState.Verdict.CREATED) {
            helper.fail("Persistent queue create failed: " + created.reason());
            return;
        }

        OperationJob job = new OperationJob(jobId, "queue_validation", 3, 50, now, now + 100);
        OperationQueueWorldState.Decision enqueued = OperationQueueWorldState.enqueue(
                helper.getLevel(), queueId, job, now);
        if (enqueued.verdict() != OperationQueueWorldState.Verdict.ENQUEUED) {
            helper.fail("Persistent queue enqueue failed: " + enqueued.reason());
            return;
        }

        OperationResourceSnapshot resource = new OperationResourceSnapshot(
                resourceId,
                Set.of("queue_validation"),
                OperationResourceSnapshot.State.AVAILABLE,
                PortQuality.VALID
        );
        OperationQueueWorldState.Decision dispatched = OperationQueueWorldState.dispatch(
                helper.getLevel(),
                queueId,
                List.of(resource),
                now,
                OperationDispatchRuntime.Policy.FIFO
        );
        OperationPlantSavedData plant = OperationPlantSavedData.get(helper.getLevel());
        OperationJobLifecycleRecord dispatchedLifecycle = plant.jobLifecycle(jobId);
        if (dispatched.verdict() != OperationQueueWorldState.Verdict.ASSIGNED
                || dispatched.snapshot() == null
                || dispatched.snapshot().queued().size() != 0
                || dispatched.snapshot().active().size() != 1
                || !resourceId.equals(dispatched.snapshot().active().getFirst().resourceId())
                || dispatchedLifecycle == null
                || dispatchedLifecycle.status() != OperationJobLifecycleRecord.Status.DISPATCHED) {
            helper.fail("Dispatch did not persist active assignment and DISPATCHED lifecycle");
            return;
        }

        CompoundTag activeTag = plant.save(new CompoundTag(), helper.getLevel().registryAccess());
        OperationPlantSavedData restoredActive = OperationPlantSavedData.load(
                activeTag, helper.getLevel().registryAccess());
        var restoredActiveQueue = restoredActive.queue(queueId);
        if (restoredActiveQueue == null
                || restoredActiveQueue.active().size() != 1
                || restoredActiveQueue.active().getFirst().job().jobId() != jobId
                || !resourceId.equals(restoredActiveQueue.active().getFirst().resourceId())) {
            helper.fail("Active assignment did not survive queue NBT round-trip");
            return;
        }

        OperationCompletionEvidence completion = new OperationCompletionEvidence(
                jobId,
                resourceId,
                job.quantity(),
                PortQuality.VALID,
                true,
                true,
                true,
                false
        );
        OperationQueueWorldState.Decision completed = OperationQueueWorldState.complete(
                helper.getLevel(), queueId, completion, now + 5);
        OperationJobLifecycleRecord completedLifecycle = plant.jobLifecycle(jobId);
        boolean deliveryRecorded = plant.plantEvents(OperationPlantEvent.Type.DELIVERY).stream()
                .anyMatch(event -> event.jobId() == jobId && "ON_TIME".equals(event.detail()));
        if (completed.verdict() != OperationQueueWorldState.Verdict.COMPLETED
                || completed.snapshot() == null
                || completed.snapshot().wip() != 0
                || completedLifecycle == null
                || completedLifecycle.status() != OperationJobLifecycleRecord.Status.COMPLETED
                || completedLifecycle.completionTick() != now + 5
                || !deliveryRecorded) {
            helper.fail("Completion did not persist empty queue, lifecycle and on-time delivery evidence");
            return;
        }

        CompoundTag completedTag = plant.save(new CompoundTag(), helper.getLevel().registryAccess());
        OperationPlantSavedData restoredCompleted = OperationPlantSavedData.load(
                completedTag, helper.getLevel().registryAccess());
        var restoredCompletedQueue = restoredCompleted.queue(queueId);
        var restoredLifecycle = restoredCompleted.jobLifecycle(jobId);
        boolean restoredDelivery = restoredCompleted.plantEvents(OperationPlantEvent.Type.DELIVERY).stream()
                .anyMatch(event -> event.jobId() == jobId && "ON_TIME".equals(event.detail()));
        if (restoredCompletedQueue == null
                || restoredCompletedQueue.wip() != 0
                || restoredLifecycle == null
                || restoredLifecycle.status() != OperationJobLifecycleRecord.Status.COMPLETED
                || !restoredDelivery) {
            helper.fail("Completed queue/lifecycle/delivery evidence did not survive NBT round-trip");
            return;
        }

        helper.succeed();
    }
}
