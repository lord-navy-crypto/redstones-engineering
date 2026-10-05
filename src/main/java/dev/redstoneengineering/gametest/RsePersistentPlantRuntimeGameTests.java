package dev.redstoneengineering.gametest;

import dev.redstoneengineering.EngineeringSystemsModule;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.IndustrialBufferBlock;
import dev.redstoneengineering.block.WorkcellControllerBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.operations.OperationBufferReceiptEvidence;
import dev.redstoneengineering.operations.OperationCompletionEvidence;
import dev.redstoneengineering.operations.OperationDispatchRuntime;
import dev.redstoneengineering.operations.OperationJob;
import dev.redstoneengineering.operations.OperationOutputSnapshot;
import dev.redstoneengineering.operations.OperationQualityInspectionEvidence;
import dev.redstoneengineering.operations.OperationQueueRuntime;
import dev.redstoneengineering.operations.OperationQueueSnapshot;
import dev.redstoneengineering.operations.OperationResourceMaintenanceSnapshot;
import dev.redstoneengineering.operations.OperationResourceSnapshot;
import dev.redstoneengineering.operations.OperationTransportDemand;
import dev.redstoneengineering.operations.world.OperationIndustrialBufferState;
import dev.redstoneengineering.operations.world.OperationJobLifecycleRecord;
import dev.redstoneengineering.operations.world.OperationPlantEvent;
import dev.redstoneengineering.operations.world.OperationPlantRuntimeRecorder;
import dev.redstoneengineering.operations.world.OperationPlantSavedData;
import dev.redstoneengineering.operations.world.OperationWorkcellBinding;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
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

    /**
     * Builds a compact, walk-up validation cell from the existing 5x4x5 test template.
     * It intentionally uses real RSE blocks and authoritative buffer SavedData rather than mock blocks.
     * The workcell has no fabricated machine resource: until a real OperationWorldResourceProvider is
     * explicitly bound, its HMI correctly reports missing resource evidence while the buffer/logistics
     * side remains fully inspectable.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 120)
    public static void visiblePersistentPlantValidationFactory(GameTestHelper helper) {
        BlockPos inputBuffer = new BlockPos(1, 1, 2);
        BlockPos controller = new BlockPos(2, 1, 2);
        BlockPos outputBuffer = new BlockPos(3, 1, 2);
        BlockPos monitor = new BlockPos(2, 2, 3);
        BlockPos monitorRun = new BlockPos(2, 1, 3);
        BlockPos monitorQueue = new BlockPos(1, 2, 3);
        BlockPos monitorCycle = new BlockPos(2, 3, 3);

        helper.setBlock(inputBuffer, EngineeringSystemsModule.INDUSTRIAL_BUFFER.get().defaultBlockState());
        helper.setBlock(controller, EngineeringSystemsModule.WORKCELL_CONTROLLER.get().defaultBlockState());
        helper.setBlock(outputBuffer, EngineeringSystemsModule.INDUSTRIAL_BUFFER.get().defaultBlockState());
        helper.setBlock(monitor, RedstoneEngineering.OPERATIONS_MONITOR.get().defaultBlockState());
        helper.setBlock(monitorRun, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(monitorQueue, Blocks.REDSTONE_BLOCK.defaultBlockState());

        helper.runAfterDelay(2, () -> {
            BlockPos inputWorld = helper.absolutePos(inputBuffer);
            BlockPos controllerWorld = helper.absolutePos(controller);
            BlockPos outputWorld = helper.absolutePos(outputBuffer);
            long now = helper.getLevel().getGameTime();
            long baseId = Math.floorMod(controllerWorld.asLong() ^ now ^ 0x56414c4944415445L, 900_000_000L) + 100_000L;
            long jobId = baseId;
            long inputOutputId = baseId * 10L + 1L;
            long outputOutputId = baseId * 10L + 2L;
            long inboundMissionId = baseId * 10L + 3L;
            long outboundMissionId = baseId * 10L + 4L;

            String inputId = IndustrialBufferBlock.bufferId(inputWorld);
            String outputId = IndustrialBufferBlock.bufferId(outputWorld);
            String workcellId = WorkcellControllerBlock.workcellId(controllerWorld);
            OperationPlantSavedData plant = OperationPlantSavedData.get(helper.getLevel());

            if (OperationIndustrialBufferState.snapshot(helper.getLevel(), inputId) == null
                    || OperationIndustrialBufferState.snapshot(helper.getLevel(), outputId) == null) {
                helper.fail("Validation Factory buffers did not attach to persistent plant state", controller);
                return;
            }

            OperationOutputSnapshot inboundOutput = new OperationOutputSnapshot(
                    inputOutputId, jobId, "validation_receiving", 24, inputWorld.west(),
                    PortQuality.VALID, true, true, false);
            OperationBufferReceiptEvidence inboundReceipt = new OperationBufferReceiptEvidence(
                    inboundMissionId, inputOutputId, jobId, inputId, inputWorld, 24,
                    PortQuality.VALID, true, false);
            var inboundDecision = OperationIndustrialBufferState.receiveQualityCleared(
                    helper.getLevel(), inboundOutput, inboundReceipt);
            if (inboundDecision.verdict() != OperationIndustrialBufferState.Verdict.RECEIVED) {
                helper.fail("Validation Factory input WIP was not persisted: " + inboundDecision.reason(), inputBuffer);
                return;
            }

            OperationOutputSnapshot finishedOutput = new OperationOutputSnapshot(
                    outputOutputId, jobId, "validation_workcell", 8, controllerWorld,
                    PortQuality.VALID, true, true, false);
            OperationBufferReceiptEvidence outboundReceipt = new OperationBufferReceiptEvidence(
                    outboundMissionId, outputOutputId, jobId, outputId, outputWorld, 8,
                    PortQuality.VALID, true, false);
            var outboundDecision = OperationIndustrialBufferState.receiveQualityCleared(
                    helper.getLevel(), finishedOutput, outboundReceipt);
            if (outboundDecision.verdict() != OperationIndustrialBufferState.Verdict.RECEIVED) {
                helper.fail("Validation Factory output WIP was not persisted: " + outboundDecision.reason(), outputBuffer);
                return;
            }

            if (!plant.putWorkcell(new OperationWorkcellBinding(workcellId, List.of()))) {
                helper.fail("Validation Factory workcell identity was not persisted", controller);
                return;
            }
            var bufferBinding = WorkcellControllerBlock.bindBuffers(helper.getLevel(), controllerWorld, inputId, outputId);
            if (!bufferBinding.changed()) {
                helper.fail("Validation Factory buffers were not bound to workcell: " + bufferBinding.reason(), controller);
                return;
            }

            OperationJob displayJob = new OperationJob(jobId, "validation_factory", 8, 80, now, now + 100);
            if (!plant.recordJobAdmitted(displayJob, now)
                    || !plant.transitionJob(jobId, OperationJobLifecycleRecord.Status.QUEUED, now, "VALIDATION_FACTORY_QUEUE")
                    || !plant.transitionJob(jobId, OperationJobLifecycleRecord.Status.DISPATCHED, now, "VALIDATION_FACTORY_DISPATCH")
                    || !plant.transitionJob(jobId, OperationJobLifecycleRecord.Status.IN_PROCESS, now, "VALIDATION_FACTORY_RUN")
                    || !plant.transitionJob(jobId, OperationJobLifecycleRecord.Status.COMPLETED, now, "VALIDATION_FACTORY_COMPLETE")) {
                helper.fail("Validation Factory job lifecycle could not be seeded", controller);
                return;
            }

            OperationPlantRuntimeRecorder.recordQualityInspection(
                    helper.getLevel(),
                    "validation_inspection:" + jobId,
                    new OperationQualityInspectionEvidence(
                            jobId, outputOutputId, 8, 7, 1, 0, PortQuality.VALID, true, false),
                    now);
            OperationPlantRuntimeRecorder.recordMaintenanceSnapshot(
                    helper.getLevel(),
                    new OperationResourceMaintenanceSnapshot(
                            "validation_workcell", OperationResourceMaintenanceSnapshot.State.MAINTENANCE_DUE,
                            "validation_pm:" + jobId, PortQuality.VALID, false),
                    now,
                    "VALIDATION_FACTORY_PM_DUE");
            OperationPlantRuntimeRecorder.recordLogisticsDemand(
                    helper.getLevel(),
                    new OperationTransportDemand(outboundMissionId, outputOutputId, controllerWorld, outputWorld, 8, 80),
                    now);
            OperationPlantRuntimeRecorder.recordLogisticsEvent(
                    helper.getLevel(), outboundMissionId, outputOutputId, now, "DELIVERED", "validation output dock");

            helper.setBlock(monitorCycle, Blocks.REDSTONE_BLOCK.defaultBlockState());
            helper.runAfterDelay(2, () -> {
                helper.setBlock(monitorCycle, Blocks.AIR.defaultBlockState());
                helper.runAfterDelay(4, () -> {
                    var persistedInput = IndustrialBufferBlock.snapshot(helper.getLevel(), helper.absolutePos(inputBuffer));
                    var persistedOutput = IndustrialBufferBlock.snapshot(helper.getLevel(), helper.absolutePos(outputBuffer));
                    var lifecycle = OperationPlantSavedData.get(helper.getLevel()).jobLifecycle(jobId);
                    if (persistedInput == null || persistedInput.usedUnits() != 24
                            || persistedOutput == null || persistedOutput.usedUnits() != 8
                            || lifecycle == null || lifecycle.status() != OperationJobLifecycleRecord.Status.COMPLETED) {
                        helper.fail("Validation Factory did not retain visible WIP/lifecycle evidence", controller);
                        return;
                    }
                    helper.succeed();
                });
            });
        });
    }

    private static boolean hasEvent(OperationPlantSavedData data, OperationPlantEvent.Type type, long jobId) {
        return data.plantEvents(type).stream().anyMatch(event -> event.jobId() == jobId);
    }

    private static boolean hasSubjectEvent(OperationPlantSavedData data, OperationPlantEvent.Type type, String subjectId) {
        return data.plantEvents(type).stream().anyMatch(event -> subjectId.equals(event.subjectId()));
    }
}
