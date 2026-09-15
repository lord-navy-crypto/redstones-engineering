package dev.redstoneengineering.gametest;

import dev.redstoneengineering.EngineeringSystemsModule;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.IndustrialBufferBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.operations.OperationBufferReceiptEvidence;
import dev.redstoneengineering.operations.OperationInputRequirement;
import dev.redstoneengineering.operations.OperationJob;
import dev.redstoneengineering.operations.OperationMaterialReleaseRuntime;
import dev.redstoneengineering.operations.OperationOutputSnapshot;
import dev.redstoneengineering.operations.OperationQueueSnapshot;
import dev.redstoneengineering.operations.world.OperationIndustrialBufferState;
import dev.redstoneengineering.operations.world.OperationJobLifecycleRecord;
import dev.redstoneengineering.operations.world.OperationPlantEvent;
import dev.redstoneengineering.operations.world.OperationPlantSavedData;
import dev.redstoneengineering.operations.world.OperationQueueWorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Local/manual regression coverage for the real world-backed WIP-to-queue commit boundary. */
public final class RseMaterialReleasePersistenceGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseMaterialReleasePersistenceGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void liveMaterialReleasePersistsQueueHistoryAndWaitIsNonDestructive(GameTestHelper helper) {
        BlockPos bufferPos = new BlockPos(2, 1, 2);
        helper.setBlock(bufferPos, EngineeringSystemsModule.INDUSTRIAL_BUFFER.get().defaultBlockState());

        helper.runAfterDelay(2, () -> {
            BlockPos worldPos = helper.absolutePos(bufferPos);
            String bufferId = IndustrialBufferBlock.bufferId(worldPos);
            String queueId = "release_validation_queue:" + worldPos.asLong();
            long now = helper.getLevel().getGameTime();
            long baseId = Math.floorMod(worldPos.asLong() ^ now ^ 0x52454c45415345L, 800_000_000L) + 100_000L;
            long upstreamJobId = baseId;
            long outputId = baseId * 10L + 1L;
            long missionId = baseId * 10L + 2L;
            long releasedJobId = baseId + 1L;
            long blockedJobId = baseId + 2L;

            if (OperationIndustrialBufferState.snapshot(helper.getLevel(), bufferId) == null) {
                helper.fail("Industrial Buffer did not attach to persistent plant state", bufferPos);
                return;
            }

            OperationPlantSavedData plant = OperationPlantSavedData.get(helper.getLevel());
            OperationQueueWorldState.Decision createdQueue = OperationQueueWorldState.create(
                    helper.getLevel(), queueId, 1);
            if (createdQueue.verdict() != OperationQueueWorldState.Verdict.CREATED) {
                helper.fail("Could not create persistent downstream validation queue: " + createdQueue.reason(), bufferPos);
                return;
            }

            OperationOutputSnapshot acceptedOutput = new OperationOutputSnapshot(
                    outputId,
                    upstreamJobId,
                    "release_validation_source",
                    10,
                    worldPos.west(),
                    PortQuality.VALID,
                    true,
                    true,
                    false
            );
            OperationBufferReceiptEvidence receipt = new OperationBufferReceiptEvidence(
                    missionId,
                    outputId,
                    upstreamJobId,
                    bufferId,
                    worldPos,
                    10,
                    PortQuality.VALID,
                    true,
                    false
            );
            var receiptDecision = OperationIndustrialBufferState.receiveQualityCleared(
                    helper.getLevel(), acceptedOutput, receipt);
            if (receiptDecision.verdict() != OperationIndustrialBufferState.Verdict.RECEIVED) {
                helper.fail("Could not seed persistent WIP: " + receiptDecision.reason(), bufferPos);
                return;
            }

            OperationJob releasedJob = new OperationJob(
                    releasedJobId, "release_validation", 4, 80, now, now + 200);
            OperationInputRequirement releasedRequirement = new OperationInputRequirement(
                    releasedJobId, bufferId, outputId, 4);
            OperationMaterialReleaseRuntime.Decision released = OperationIndustrialBufferState.releaseMaterial(
                    helper.getLevel(),
                    bufferId,
                    queueId,
                    releasedJob,
                    releasedRequirement
            );
            OperationQueueSnapshot persistedQueue = OperationQueueWorldState.snapshot(helper.getLevel(), queueId);
            if (released.verdict() != OperationMaterialReleaseRuntime.Verdict.RELEASED
                    || persistedQueue == null
                    || persistedQueue.queued().size() != 1
                    || persistedQueue.queued().getFirst().jobId() != releasedJobId) {
                helper.fail("Live material release did not commit persistent WIP and queue together", bufferPos);
                return;
            }

            var releasedBuffer = OperationIndustrialBufferState.snapshot(helper.getLevel(), bufferId);
            OperationJobLifecycleRecord releasedLifecycle = plant.jobLifecycle(releasedJobId);
            if (releasedBuffer == null || releasedBuffer.usedUnits() != 6
                    || releasedLifecycle == null
                    || releasedLifecycle.status() != OperationJobLifecycleRecord.Status.QUEUED
                    || !hasMaterialReleaseEvent(plant, releasedJobId)) {
                helper.fail("Successful live release did not persist QUEUED lifecycle/history evidence", bufferPos);
                return;
            }

            OperationJob blockedJob = new OperationJob(
                    blockedJobId, "release_validation", 2, 70, now, now + 200);
            OperationInputRequirement blockedRequirement = new OperationInputRequirement(
                    blockedJobId, bufferId, outputId, 2);

            int unitsBeforeWait = releasedBuffer.usedUnits();
            int queueEventsBeforeWait = plant.plantEvents(OperationPlantEvent.Type.QUEUE).size();
            OperationQueueSnapshot queueBeforeWait = OperationQueueWorldState.snapshot(helper.getLevel(), queueId);
            OperationMaterialReleaseRuntime.Decision waitDecision = OperationIndustrialBufferState.releaseMaterial(
                    helper.getLevel(),
                    bufferId,
                    queueId,
                    blockedJob,
                    blockedRequirement
            );
            var afterWaitBuffer = OperationIndustrialBufferState.snapshot(helper.getLevel(), bufferId);
            OperationQueueSnapshot afterWaitQueue = OperationQueueWorldState.snapshot(helper.getLevel(), queueId);
            if (waitDecision.verdict() != OperationMaterialReleaseRuntime.Verdict.WAIT
                    || afterWaitBuffer == null
                    || afterWaitBuffer.usedUnits() != unitsBeforeWait
                    || afterWaitQueue == null
                    || !afterWaitQueue.equals(queueBeforeWait)
                    || plant.jobLifecycle(blockedJobId) != null
                    || plant.plantEvents(OperationPlantEvent.Type.QUEUE).size() != queueEventsBeforeWait) {
                helper.fail("Full persistent downstream queue consumed WIP or fabricated runtime history", bufferPos);
                return;
            }

            helper.succeed();
        });
    }

    private static boolean hasMaterialReleaseEvent(OperationPlantSavedData data, long jobId) {
        return data.plantEvents(OperationPlantEvent.Type.QUEUE).stream()
                .anyMatch(event -> event.jobId() == jobId && event.detail().contains("MATERIAL_JOB_RELEASED"));
    }
}
