package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.operations.OperationJob;
import dev.redstoneengineering.operations.OperationQualityInspectionEvidence;
import dev.redstoneengineering.operations.OperationTransportDemand;
import dev.redstoneengineering.operations.integration.OperationRobotLogisticsRuntime;
import dev.redstoneengineering.operations.world.OperationJobLifecycleSnapshot;
import dev.redstoneengineering.operations.world.OperationJobLifecycleState;
import dev.redstoneengineering.operations.world.OperationPlantEvent;
import dev.redstoneengineering.operations.world.OperationPlantRuntimeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Plant-level contracts for durable job/history/logistics evidence. */
public final class RsePersistentPlantRuntimeGameTests {
    private static final String TEMPLATE = "empty5x4x5";
    private static final BlockPos MARKER = new BlockPos(2, 1, 2);

    private RsePersistentPlantRuntimeGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void jobLifecycleAndDeliveryHistoryRoundTrip(GameTestHelper helper) {
        OperationPlantRuntimeSavedData data = new OperationPlantRuntimeSavedData();
        OperationJob job = new OperationJob(1001, "validation_factory", 4, 80, 100, 220);

        if (!data.release(job, 100)
                || !data.queue(job.jobId(), 105)
                || !data.assign(job.jobId(), "WORKCELL_A", 120)
                || !data.start(job.jobId(), 130)
                || !data.qualityHold(job.jobId(), 501, 170)
                || !data.transport(job.jobId(), 501, 9001, 180)
                || !data.complete(job.jobId(), 501, 210)) {
            helper.fail("Normal plant lifecycle refused a valid transition", MARKER);
            return;
        }

        OperationJobLifecycleSnapshot before = data.job(job.jobId());
        if (before == null
                || before.state() != OperationJobLifecycleState.COMPLETED
                || before.deliveryStatus() != OperationJobLifecycleSnapshot.DeliveryStatus.ON_TIME
                || before.completionTick() != 210) {
            helper.fail("Completed lifecycle did not retain on-time delivery evidence", MARKER);
            return;
        }

        CompoundTag encoded = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        OperationPlantRuntimeSavedData restored = OperationPlantRuntimeSavedData.load(encoded, helper.getLevel().registryAccess());
        OperationJobLifecycleSnapshot after = restored.job(job.jobId());
        if (after == null
                || after.state() != OperationJobLifecycleState.COMPLETED
                || after.deliveryStatus() != OperationJobLifecycleSnapshot.DeliveryStatus.ON_TIME
                || restored.eventsForJob(job.jobId()).size() < 7) {
            helper.fail("Persistent plant runtime did not round-trip lifecycle/history evidence", MARKER);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 30)
    public static void invalidLifecycleTransitionFailsClosed(GameTestHelper helper) {
        OperationPlantRuntimeSavedData data = new OperationPlantRuntimeSavedData();
        OperationJob job = new OperationJob(1002, "validation_factory", 1, 50, 20, 0);
        data.release(job, 20);

        int eventsBefore = data.events().size();
        if (data.complete(job.jobId(), 601, 21)
                || data.job(job.jobId()).state() != OperationJobLifecycleState.RELEASED
                || data.events().size() != eventsBefore) {
            helper.fail("Lifecycle accepted an impossible RELEASED -> COMPLETED jump", MARKER);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 30)
    public static void qualityReworkPreservesJobAndOutputIdentity(GameTestHelper helper) {
        OperationPlantRuntimeSavedData data = runningJob(1003, 40);
        OperationQualityInspectionEvidence inspection = new OperationQualityInspectionEvidence(
                701, 1003, 4, 2, 1, 1, PortQuality.VALID, true, false);

        if (!data.recordQuality(inspection, 80)) {
            helper.fail("Valid quality evidence was rejected", MARKER);
            return;
        }
        var events = data.eventsForJob(1003);
        boolean inspectionRecorded = events.stream().anyMatch(event ->
                event.type() == OperationPlantEvent.Type.QUALITY_INSPECTED && event.outputId() == 701);
        boolean reworkRecorded = events.stream().anyMatch(event ->
                event.type() == OperationPlantEvent.Type.REWORK_REQUESTED && event.outputId() == 701);
        if (!inspectionRecorded || !reworkRecorded
                || data.job(1003).state() != OperationJobLifecycleState.QUALITY_HOLD) {
            helper.fail("Quality/rework history lost output identity or lifecycle hold", MARKER);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 30)
    public static void maintenanceHistoryRetainsFaultDowntimeAndRecovery(GameTestHelper helper) {
        OperationPlantRuntimeSavedData data = new OperationPlantRuntimeSavedData();
        if (!data.maintenanceDue("WORKCELL_A", "PM-42", 10)
                || !data.maintenanceStarted("WORKCELL_A", "PM-42", 12)
                || !data.maintenanceFault("WORKCELL_A", "PM-42", 15, "SPINDLE_FAULT")
                || !data.maintenanceCompleted("WORKCELL_A", "PM-42", 40)) {
            helper.fail("Maintenance history refused a valid due/start/fault/recovery sequence", MARKER);
            return;
        }
        long faultTick = data.events().stream()
                .filter(event -> event.type() == OperationPlantEvent.Type.MAINTENANCE_FAULT)
                .mapToLong(OperationPlantEvent::tick).findFirst().orElse(-1);
        long completionTick = data.events().stream()
                .filter(event -> event.type() == OperationPlantEvent.Type.MAINTENANCE_COMPLETED)
                .mapToLong(OperationPlantEvent::tick).findFirst().orElse(-1);
        if (faultTick != 15 || completionTick != 40) {
            helper.fail("Maintenance history lost downtime boundaries", MARKER);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 30)
    public static void lateDeliveryIsDerivedFromDueAndCompletionTicks(GameTestHelper helper) {
        OperationPlantRuntimeSavedData data = new OperationPlantRuntimeSavedData();
        OperationJob job = new OperationJob(1004, "validation_factory", 1, 60, 10, 50);
        data.release(job, 10);
        data.queue(job.jobId(), 12);
        data.assign(job.jobId(), "WORKCELL_A", 20);
        data.start(job.jobId(), 25);
        data.qualityHold(job.jobId(), 801, 45);
        data.transport(job.jobId(), 801, 9100, 48);
        data.complete(job.jobId(), 801, 65);
        if (data.job(job.jobId()).deliveryStatus() != OperationJobLifecycleSnapshot.DeliveryStatus.LATE) {
            helper.fail("Completion after dueTick was not classified LATE", MARKER);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 30)
    public static void amrHandoffPreservesTransportDemandIdentity(GameTestHelper helper) {
        OperationTransportDemand demand = new OperationTransportDemand(
                9200, 901, new BlockPos(1, 1, 1), new BlockPos(4, 1, 1), 3, 75);
        var mission = OperationRobotLogisticsRuntime.toRobotMission(demand);
        if (mission.missionId() != demand.missionId()
                || mission.source() == null
                || !mission.source().equals(demand.source())
                || !mission.target().equals(demand.target())
                || mission.payloadUnits() != demand.units()
                || mission.priority() != demand.priority()) {
            helper.fail("Operations -> AMR handoff changed transport identity", MARKER);
            return;
        }
        helper.succeed();
    }

    private static OperationPlantRuntimeSavedData runningJob(long jobId, long releaseTick) {
        OperationPlantRuntimeSavedData data = new OperationPlantRuntimeSavedData();
        OperationJob job = new OperationJob(jobId, "validation_factory", 4, 70, releaseTick, 0);
        data.release(job, releaseTick);
        data.queue(jobId, releaseTick + 1);
        data.assign(jobId, "WORKCELL_A", releaseTick + 2);
        data.start(jobId, releaseTick + 3);
        return data;
    }
}
