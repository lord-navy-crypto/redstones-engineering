package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.OperationsMonitorBlock;
import dev.redstoneengineering.diagnostics.CopperEvidenceAssessment;
import dev.redstoneengineering.diagnostics.ElectricalReliabilityAssessment;
import dev.redstoneengineering.diagnostics.IndustrialOperationsAssessment;
import dev.redstoneengineering.diagnostics.OperationBottleneckAssessment;
import dev.redstoneengineering.diagnostics.OperationPersistentPlantRuntimeAssessment;
import dev.redstoneengineering.diagnostics.OperationPlantViewAssessment;
import dev.redstoneengineering.diagnostics.OperationWorldPlantStateAssessment;
import dev.redstoneengineering.diagnostics.OperationsDashboardSnapshot;
import dev.redstoneengineering.diagnostics.OperationsEventWindow;
import dev.redstoneengineering.diagnostics.OperationsIncidentSummary;
import dev.redstoneengineering.diagnostics.events.SystemEventKind;
import dev.redstoneengineering.diagnostics.events.SystemEventRecord;
import dev.redstoneengineering.ui.EngineeringUiRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Dedicated read-only synchronized operations console for the existing Operations Monitor block. */
public final class OperationsMonitorMenu extends EngineeringDeviceMenu {
    public static final int EVENT_SLOTS = OperationsEventWindow.MAX_VISIBLE_EVENTS;
    private static final int MAX_SYNC_AGE_TICKS = 32767;

    private final DataSlot queue = trackedInt();
    private final DataSlot throughput = trackedInt();
    private final DataSlot lastCycleTicks = trackedInt();
    private final DataSlot downtime = trackedInt();
    private final DataSlot state = trackedInt();
    private final DataSlot machineState = trackedInt();
    private final DataSlot queuePressure = trackedInt();
    private final DataSlot dominantConstraint = trackedInt();
    private final DataSlot telemetryReady = trackedInt();
    private final DataSlot runEvidenceValid = trackedInt();
    private final DataSlot queueEvidenceSources = trackedInt();
    private final DataSlot cycleEvidenceValid = trackedInt();

    // Legacy composed Plant View stays conservative until every input assessment is supplied.
    private final DataSlot plantCoverage = trackedInt();
    private final DataSlot plantEvidenceAuthoritative = trackedInt();
    private final DataSlot plantBottleneckPresent = trackedInt();
    private final DataSlot plantBottleneckConstraint = trackedInt();
    private final DataSlot plantConstrainedWorkcells = trackedInt();
    private final DataSlot plantFirstPassYieldPercent = trackedInt();
    private final DataSlot plantRejectRatePercent = trackedInt();
    private final DataSlot plantReworkRatePercent = trackedInt();
    private final DataSlot plantObservedAvailabilityPercent = trackedInt();
    private final DataSlot plantFailureCount = trackedInt();
    private final DataSlot plantOverdueOutstandingJobs = trackedInt();
    private final DataSlot plantOutstandingWithDueDate = trackedInt();

    // Persistent Plant Runtime is independent world-backed evidence retained in OperationPlantSavedData.
    private final DataSlot persistentPlantCoverage = trackedInt();
    private final DataSlot persistentPlantRetainedJobs = trackedInt();
    private final DataSlot persistentPlantActiveJobs = trackedInt();
    private final DataSlot persistentPlantCompletedJobs = trackedInt();
    private final DataSlot persistentPlantRetainedEvents = trackedInt();
    private final DataSlot persistentPlantQueueEvents = trackedInt();
    private final DataSlot persistentPlantQualityEvents = trackedInt();
    private final DataSlot persistentPlantMaintenanceEvents = trackedInt();
    private final DataSlot persistentPlantDeliveryEvents = trackedInt();
    private final DataSlot persistentPlantLogisticsEvents = trackedInt();
    private final DataSlot persistentPlantOnTimeDeliveries = trackedInt();
    private final DataSlot persistentPlantLateDeliveries = trackedInt();
    private final DataSlot persistentPlantUndatedDeliveries = trackedInt();
    private final DataSlot persistentPlantOnTimeDeliveryPercent = trackedInt();
    private final DataSlot persistentPlantFirstPassYieldPercent = trackedInt();
    private final DataSlot persistentPlantRejectRatePercent = trackedInt();
    private final DataSlot persistentPlantReworkRatePercent = trackedInt();
    private final DataSlot persistentPlantMaintenanceFaultEvents = trackedInt();
    private final DataSlot persistentPlantOutstandingWithDueDate = trackedInt();
    private final DataSlot persistentPlantOverdueOutstandingJobs = trackedInt();

    // World Plant State uses only persisted workcell/buffer/binding evidence and is independently visible.
    private final DataSlot worldPlantCoverage = trackedInt();
    private final DataSlot worldPlantWorkcells = trackedInt();
    private final DataSlot worldPlantConfiguredWorkcells = trackedInt();
    private final DataSlot worldPlantBuffers = trackedInt();
    private final DataSlot worldPlantUsedBufferUnits = trackedInt();
    private final DataSlot worldPlantBufferCapacityUnits = trackedInt();
    private final DataSlot worldPlantWipPressurePercent = trackedInt();
    private final DataSlot worldPlantBoundResources = trackedInt();
    private final DataSlot worldPlantValidResources = trackedInt();
    private final DataSlot worldPlantFaultResources = trackedInt();

    private final DataSlot retainedEvents = trackedInt();
    private final DataSlot recentEvents = trackedInt();
    private final DataSlot recentAbnormalEvents = trackedInt();
    private final DataSlot timelineCount = trackedInt();
    private final DataSlot firstOutSlot = trackedInt();
    private final DataSlot firstOutKind = trackedInt();
    private final DataSlot firstOutSeverity = trackedInt();
    private final DataSlot firstOutAge = trackedInt();

    private final DataSlot incidentPresent = trackedInt();
    private final DataSlot firstOutDx = trackedInt();
    private final DataSlot firstOutDy = trackedInt();
    private final DataSlot firstOutDz = trackedInt();
    private final DataSlot incidentDuration = trackedInt();
    private final DataSlot downstreamObservations = trackedInt();
    private final DataSlot abnormalDownstreamObservations = trackedInt();
    private final DataSlot evidenceTraceEntries = trackedInt();

    private final DataSlot electricalTrips = trackedInt();
    private final DataSlot electricalRecoveries = trackedInt();
    private final DataSlot electricalRepeatTrips = trackedInt();
    private final DataSlot electricalActiveTrips = trackedInt();
    private final DataSlot electricalDowntime = trackedInt();
    private final DataSlot electricalLastTripAge = trackedInt();
    private final DataSlot electricalLastRecoveryDuration = trackedInt();

    private final DataSlot copperEvidenceDegraded = trackedInt();
    private final DataSlot copperEvidenceFailed = trackedInt();
    private final DataSlot copperEvidenceRestored = trackedInt();
    private final DataSlot copperEvidenceActiveDegraded = trackedInt();
    private final DataSlot copperEvidenceActiveFailed = trackedInt();
    private final DataSlot copperEvidenceLastFailureAge = trackedInt();
    private final DataSlot copperEvidenceLastRestoreAge = trackedInt();

    private final DataSlot[] eventKinds = trackedInts(EVENT_SLOTS);
    private final DataSlot[] eventSeverities = trackedInts(EVENT_SLOTS);
    private final DataSlot[] eventAges = trackedInts(EVENT_SLOTS);

    public OperationsMonitorMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos());
    }

    public OperationsMonitorMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(EngineeringUiRegistration.OPERATIONS_MONITOR.get(), containerId, inventory, pos,
                RedstoneEngineering.OPERATIONS_MONITOR.get());
        if (!level.isClientSide) refreshAuthoritativeSnapshot();
    }

    @Override
    protected void refreshAuthoritativeSnapshot() {
        BlockState blockState = level.getBlockState(blockPos);
        if (!(blockState.getBlock() instanceof OperationsMonitorBlock)) return;

        OperationsDashboardSnapshot dashboard = OperationsDashboardSnapshot.inspect(level, blockPos);
        IndustrialOperationsAssessment.Snapshot operations = dashboard.operations();
        OperationsEventWindow window = OperationsEventWindow.inspect(level, dashboard);
        OperationsIncidentSummary incident = OperationsIncidentSummary.inspect(level, blockPos, dashboard);
        ElectricalReliabilityAssessment.Snapshot electrical =
                ElectricalReliabilityAssessment.inspect(level, dashboard.eventScope());
        CopperEvidenceAssessment.Snapshot copperEvidence =
                CopperEvidenceAssessment.inspect(level, dashboard.eventScope());
        OperationsMonitorBlock.InputEvidence evidence = OperationsMonitorBlock.inputEvidence(level, blockPos);

        OperationPlantViewAssessment.Snapshot plant = OperationPlantViewAssessment.inspect(
                dashboard, null, null, null, null);
        boolean authoritativePlantEvidence = false;
        ServerLevel serverLevel = level instanceof ServerLevel server ? server : null;
        OperationWorldPlantStateAssessment.Snapshot worldPlant = OperationWorldPlantStateAssessment.inspect(serverLevel);
        OperationPersistentPlantRuntimeAssessment.Snapshot persistentPlant =
                OperationPersistentPlantRuntimeAssessment.inspect(serverLevel);

        queue.set(operations.queueNow());
        throughput.set(operations.throughputCyclesPerMinute());
        lastCycleTicks.set(operations.lastCycleTicks());
        downtime.set(operations.downtimeTicks());
        state.set(operations.state().ordinal());
        machineState.set(operations.machineState().ordinal());
        queuePressure.set(operations.queuePressurePercent());
        dominantConstraint.set(operations.dominantConstraint().ordinal());
        telemetryReady.set(evidence.operationalReady() ? 1 : 0);
        runEvidenceValid.set(evidence.run().valid() ? 1 : 0);
        queueEvidenceSources.set(evidence.queueSources());
        cycleEvidenceValid.set(evidence.cycle().valid() ? 1 : 0);

        plantCoverage.set(plant.coverage().ordinal());
        plantEvidenceAuthoritative.set(authoritativePlantEvidence ? 1 : 0);
        plantBottleneckPresent.set(plant.bottleneckPresent() ? 1 : 0);
        plantBottleneckConstraint.set(plant.bottleneckConstraint().ordinal());
        plantConstrainedWorkcells.set(plant.constrainedWorkcells());
        plantFirstPassYieldPercent.set(plant.firstPassYieldPercent());
        plantRejectRatePercent.set(plant.rejectRatePercent());
        plantReworkRatePercent.set(plant.reworkRatePercent());
        plantObservedAvailabilityPercent.set(plant.observedAvailabilityPercent());
        plantFailureCount.set(plant.failureCount());
        plantOverdueOutstandingJobs.set(plant.overdueOutstandingJobs());
        plantOutstandingWithDueDate.set(plant.outstandingWithDueDate());

        persistentPlantCoverage.set(persistentPlant.coverage().ordinal());
        persistentPlantRetainedJobs.set(persistentPlant.retainedJobs());
        persistentPlantActiveJobs.set(persistentPlant.activeJobs());
        persistentPlantCompletedJobs.set(persistentPlant.completedJobs());
        persistentPlantRetainedEvents.set(persistentPlant.retainedEvents());
        persistentPlantQueueEvents.set(persistentPlant.queueEvents());
        persistentPlantQualityEvents.set(persistentPlant.qualityEvents());
        persistentPlantMaintenanceEvents.set(persistentPlant.maintenanceEvents());
        persistentPlantDeliveryEvents.set(persistentPlant.deliveryEvents());
        persistentPlantLogisticsEvents.set(persistentPlant.logisticsEvents());
        persistentPlantOnTimeDeliveries.set(persistentPlant.onTimeDeliveries());
        persistentPlantLateDeliveries.set(persistentPlant.lateDeliveries());
        persistentPlantUndatedDeliveries.set(persistentPlant.undatedDeliveries());
        persistentPlantOnTimeDeliveryPercent.set(persistentPlant.onTimeDeliveryPercent());
        persistentPlantFirstPassYieldPercent.set(persistentPlant.firstPassYieldPercent());
        persistentPlantRejectRatePercent.set(persistentPlant.rejectRatePercent());
        persistentPlantReworkRatePercent.set(persistentPlant.reworkRatePercent());
        persistentPlantMaintenanceFaultEvents.set(persistentPlant.maintenanceFaultEvents());
        persistentPlantOutstandingWithDueDate.set(persistentPlant.outstandingWithDueDate());
        persistentPlantOverdueOutstandingJobs.set(persistentPlant.overdueOutstandingJobs());

        worldPlantCoverage.set(worldPlant.coverage().ordinal());
        worldPlantWorkcells.set(worldPlant.totalWorkcells());
        worldPlantConfiguredWorkcells.set(worldPlant.configuredWorkcells());
        worldPlantBuffers.set(worldPlant.totalBuffers());
        worldPlantUsedBufferUnits.set(worldPlant.usedBufferUnits());
        worldPlantBufferCapacityUnits.set(worldPlant.bufferCapacityUnits());
        worldPlantWipPressurePercent.set(worldPlant.wipPressurePercent());
        worldPlantBoundResources.set(worldPlant.boundResources());
        worldPlantValidResources.set(worldPlant.validResources());
        worldPlantFaultResources.set(worldPlant.faultResources());

        retainedEvents.set(dashboard.retainedEvents());
        recentEvents.set(dashboard.recentEvents());
        recentAbnormalEvents.set(dashboard.recentAbnormalEvents());

        incidentPresent.set(incident.present() ? 1 : 0);
        firstOutDx.set(incident.firstOutDx());
        firstOutDy.set(incident.firstOutDy());
        firstOutDz.set(incident.firstOutDz());
        incidentDuration.set(syncTicks(incident.incidentDurationTicks()));
        downstreamObservations.set(incident.downstreamObservations());
        abnormalDownstreamObservations.set(incident.abnormalDownstreamObservations());
        evidenceTraceEntries.set(incident.evidenceTraceEntries());

        electricalTrips.set(electrical.tripCount());
        electricalRecoveries.set(electrical.recoveryCount());
        electricalRepeatTrips.set(electrical.repeatTripCount());
        electricalActiveTrips.set(electrical.activeTripCount());
        electricalDowntime.set(syncTicks(electrical.electricalDowntimeTicks()));
        electricalLastTripAge.set(syncOptionalTicks(electrical.lastTripAgeTicks()));
        electricalLastRecoveryDuration.set(syncOptionalTicks(electrical.lastRecoveryDurationTicks()));

        copperEvidenceDegraded.set(copperEvidence.degradedTransitions());
        copperEvidenceFailed.set(copperEvidence.failedTransitions());
        copperEvidenceRestored.set(copperEvidence.restoredTransitions());
        copperEvidenceActiveDegraded.set(copperEvidence.activeDegradedSources());
        copperEvidenceActiveFailed.set(copperEvidence.activeFailedSources());
        copperEvidenceLastFailureAge.set(syncOptionalTicks(copperEvidence.lastFailureAgeTicks()));
        copperEvidenceLastRestoreAge.set(syncOptionalTicks(copperEvidence.lastRestoreAgeTicks()));

        List<SystemEventRecord> events = window.events();
        int count = Math.min(EVENT_SLOTS, events.size());
        int pad = EVENT_SLOTS - count;
        timelineCount.set(count);
        for (int slot = 0; slot < EVENT_SLOTS; slot++) {
            if (slot < pad) {
                eventKinds[slot].set(-1);
                eventSeverities[slot].set(-1);
                eventAges[slot].set(-1);
                continue;
            }
            SystemEventRecord event = events.get(slot - pad);
            eventKinds[slot].set(event.kind().ordinal());
            eventSeverities[slot].set(event.severity());
            eventAges[slot].set(ageTicks(event.tick()));
        }

        int visibleFirstOut = window.firstOutIndex();
        firstOutSlot.set(visibleFirstOut < 0 ? -1 : pad + visibleFirstOut);
        if (window.firstOut().isPresent()) {
            SystemEventRecord first = window.firstOut().get().firstOut();
            firstOutKind.set(first.kind().ordinal());
            firstOutSeverity.set(first.severity());
            firstOutAge.set(ageTicks(first.tick()));
        } else {
            firstOutKind.set(-1);
            firstOutSeverity.set(-1);
            firstOutAge.set(-1);
        }
    }

    private int ageTicks(long eventTick) { return syncTicks(Math.max(0L, level.getGameTime() - eventTick)); }
    private static int syncTicks(long ticks) { return (int) Math.min(MAX_SYNC_AGE_TICKS, Math.max(0L, ticks)); }
    private static int syncOptionalTicks(long ticks) { return ticks < 0 ? -1 : syncTicks(ticks); }

    public int queue() { return queue.get(); }
    public int throughput() { return throughput.get(); }
    public int lastCycleTicks() { return lastCycleTicks.get(); }
    public int downtimeTicks() { return downtime.get(); }
    public int queuePressurePercent() { return queuePressure.get(); }
    public boolean telemetryReady() { return telemetryReady.get() != 0; }
    public boolean runEvidenceValid() { return runEvidenceValid.get() != 0; }
    public int queueEvidenceSources() { return queueEvidenceSources.get(); }
    public boolean cycleEvidenceValid() { return cycleEvidenceValid.get() != 0; }

    public OperationPlantViewAssessment.EvidenceCoverage plantCoverage() {
        OperationPlantViewAssessment.EvidenceCoverage[] values = OperationPlantViewAssessment.EvidenceCoverage.values();
        return values[clampIndex(plantCoverage.get(), values.length)];
    }
    public boolean plantEvidenceAuthoritative() { return plantEvidenceAuthoritative.get() != 0; }
    public boolean plantBottleneckPresent() { return plantBottleneckPresent.get() != 0; }
    public OperationBottleneckAssessment.Constraint plantBottleneckConstraint() {
        OperationBottleneckAssessment.Constraint[] values = OperationBottleneckAssessment.Constraint.values();
        return values[clampIndex(plantBottleneckConstraint.get(), values.length)];
    }
    public int plantConstrainedWorkcells() { return plantConstrainedWorkcells.get(); }
    public int plantFirstPassYieldPercent() { return plantFirstPassYieldPercent.get(); }
    public int plantRejectRatePercent() { return plantRejectRatePercent.get(); }
    public int plantReworkRatePercent() { return plantReworkRatePercent.get(); }
    public int plantObservedAvailabilityPercent() { return plantObservedAvailabilityPercent.get(); }
    public int plantFailureCount() { return plantFailureCount.get(); }
    public int plantOverdueOutstandingJobs() { return plantOverdueOutstandingJobs.get(); }
    public int plantOutstandingWithDueDate() { return plantOutstandingWithDueDate.get(); }

    public OperationPersistentPlantRuntimeAssessment.Coverage persistentPlantCoverage() {
        OperationPersistentPlantRuntimeAssessment.Coverage[] values = OperationPersistentPlantRuntimeAssessment.Coverage.values();
        return values[clampIndex(persistentPlantCoverage.get(), values.length)];
    }
    public int persistentPlantRetainedJobs() { return persistentPlantRetainedJobs.get(); }
    public int persistentPlantActiveJobs() { return persistentPlantActiveJobs.get(); }
    public int persistentPlantCompletedJobs() { return persistentPlantCompletedJobs.get(); }
    public int persistentPlantRetainedEvents() { return persistentPlantRetainedEvents.get(); }
    public int persistentPlantQueueEvents() { return persistentPlantQueueEvents.get(); }
    public int persistentPlantQualityEvents() { return persistentPlantQualityEvents.get(); }
    public int persistentPlantMaintenanceEvents() { return persistentPlantMaintenanceEvents.get(); }
    public int persistentPlantDeliveryEvents() { return persistentPlantDeliveryEvents.get(); }
    public int persistentPlantLogisticsEvents() { return persistentPlantLogisticsEvents.get(); }
    public int persistentPlantOnTimeDeliveries() { return persistentPlantOnTimeDeliveries.get(); }
    public int persistentPlantLateDeliveries() { return persistentPlantLateDeliveries.get(); }
    public int persistentPlantUndatedDeliveries() { return persistentPlantUndatedDeliveries.get(); }
    public int persistentPlantOnTimeDeliveryPercent() { return persistentPlantOnTimeDeliveryPercent.get(); }
    public int persistentPlantFirstPassYieldPercent() { return persistentPlantFirstPassYieldPercent.get(); }
    public int persistentPlantRejectRatePercent() { return persistentPlantRejectRatePercent.get(); }
    public int persistentPlantReworkRatePercent() { return persistentPlantReworkRatePercent.get(); }
    public int persistentPlantMaintenanceFaultEvents() { return persistentPlantMaintenanceFaultEvents.get(); }
    public int persistentPlantOutstandingWithDueDate() { return persistentPlantOutstandingWithDueDate.get(); }
    public int persistentPlantOverdueOutstandingJobs() { return persistentPlantOverdueOutstandingJobs.get(); }

    public OperationWorldPlantStateAssessment.Coverage worldPlantCoverage() {
        OperationWorldPlantStateAssessment.Coverage[] values = OperationWorldPlantStateAssessment.Coverage.values();
        return values[clampIndex(worldPlantCoverage.get(), values.length)];
    }
    public int worldPlantWorkcells() { return worldPlantWorkcells.get(); }
    public int worldPlantConfiguredWorkcells() { return worldPlantConfiguredWorkcells.get(); }
    public int worldPlantBuffers() { return worldPlantBuffers.get(); }
    public int worldPlantUsedBufferUnits() { return worldPlantUsedBufferUnits.get(); }
    public int worldPlantBufferCapacityUnits() { return worldPlantBufferCapacityUnits.get(); }
    public int worldPlantWipPressurePercent() { return worldPlantWipPressurePercent.get(); }
    public int worldPlantBoundResources() { return worldPlantBoundResources.get(); }
    public int worldPlantValidResources() { return worldPlantValidResources.get(); }
    public int worldPlantFaultResources() { return worldPlantFaultResources.get(); }

    public int retainedEvents() { return retainedEvents.get(); }
    public int recentEvents() { return recentEvents.get(); }
    public int recentAbnormalEvents() { return recentAbnormalEvents.get(); }
    public int timelineCount() { return timelineCount.get(); }
    public int firstOutSlot() { return firstOutSlot.get(); }
    public int firstOutKindOrdinal() { return firstOutKind.get(); }
    public int firstOutSeverity() { return firstOutSeverity.get(); }
    public int firstOutAgeTicks() { return firstOutAge.get(); }

    public boolean incidentPresent() { return incidentPresent.get() != 0; }
    public int firstOutDx() { return firstOutDx.get(); }
    public int firstOutDy() { return firstOutDy.get(); }
    public int firstOutDz() { return firstOutDz.get(); }
    public int incidentDurationTicks() { return incidentDuration.get(); }
    public int downstreamObservations() { return downstreamObservations.get(); }
    public int abnormalDownstreamObservations() { return abnormalDownstreamObservations.get(); }
    public int evidenceTraceEntries() { return evidenceTraceEntries.get(); }

    public int electricalTripCount() { return electricalTrips.get(); }
    public int electricalRecoveryCount() { return electricalRecoveries.get(); }
    public int electricalRepeatTripCount() { return electricalRepeatTrips.get(); }
    public int electricalActiveTripCount() { return electricalActiveTrips.get(); }
    public int electricalDowntimeTicks() { return electricalDowntime.get(); }
    public int electricalLastTripAgeTicks() { return electricalLastTripAge.get(); }
    public int electricalLastRecoveryDurationTicks() { return electricalLastRecoveryDuration.get(); }

    public int copperEvidenceDegradedCount() { return copperEvidenceDegraded.get(); }
    public int copperEvidenceFailedCount() { return copperEvidenceFailed.get(); }
    public int copperEvidenceRestoredCount() { return copperEvidenceRestored.get(); }
    public int copperEvidenceActiveDegradedCount() { return copperEvidenceActiveDegraded.get(); }
    public int copperEvidenceActiveFailedCount() { return copperEvidenceActiveFailed.get(); }
    public int copperEvidenceLastFailureAgeTicks() { return copperEvidenceLastFailureAge.get(); }
    public int copperEvidenceLastRestoreAgeTicks() { return copperEvidenceLastRestoreAge.get(); }

    public OperationsMonitorBlock.SystemState state() {
        OperationsMonitorBlock.SystemState[] values = OperationsMonitorBlock.SystemState.values();
        return values[clampIndex(state.get(), values.length)];
    }

    public IndustrialOperationsAssessment.MachineState machineState() {
        IndustrialOperationsAssessment.MachineState[] values = IndustrialOperationsAssessment.MachineState.values();
        return values[clampIndex(machineState.get(), values.length)];
    }

    public IndustrialOperationsAssessment.Constraint dominantConstraint() {
        IndustrialOperationsAssessment.Constraint[] values = IndustrialOperationsAssessment.Constraint.values();
        return values[clampIndex(dominantConstraint.get(), values.length)];
    }

    public int eventKindOrdinal(int slot) { return validSlot(slot) ? eventKinds[slot].get() : -1; }
    public int eventSeverity(int slot) { return validSlot(slot) ? eventSeverities[slot].get() : -1; }
    public int eventAgeTicks(int slot) { return validSlot(slot) ? eventAges[slot].get() : -1; }

    public boolean eventAbnormal(int slot) {
        int kind = eventKindOrdinal(slot);
        if (kind < 0) return false;
        SystemEventKind[] values = SystemEventKind.values();
        int index = clampIndex(kind, values.length);
        return values[index].abnormal() || eventSeverity(slot) >= 2;
    }

    private static boolean validSlot(int slot) { return slot >= 0 && slot < EVENT_SLOTS; }
    private static int clampIndex(int value, int length) {
        return Math.max(0, Math.min(Math.max(0, length - 1), value));
    }
}
