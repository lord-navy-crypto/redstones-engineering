package dev.redstoneengineering.operations.world;

import dev.redstoneengineering.operations.OperationJob;
import dev.redstoneengineering.operations.OperationQualityInspectionEvidence;
import dev.redstoneengineering.operations.OperationTransportDemand;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-owned persistent plant runtime ledger.
 *
 * <p>This complements {@link OperationPlantSavedData}: that class owns topology/buffer/WIP state,
 * while this class owns durable job lifecycle and historical evidence. Runtime decision classes
 * remain authoritative for dispatch/quality/maintenance/robot behavior; this ledger records facts.</p>
 */
public final class OperationPlantRuntimeSavedData extends SavedData {
    private static final String DATA_NAME = "rse_operations_plant_runtime";

    private final Map<Long, OperationJobLifecycleSnapshot> jobs = new LinkedHashMap<>();
    private final List<OperationPlantEvent> events = new ArrayList<>();
    private long nextSequence;

    public OperationPlantRuntimeSavedData() {}

    public static OperationPlantRuntimeSavedData get(ServerLevel level) {
        if (level == null || level.getServer() == null) {
            throw new IllegalArgumentException("server level is required");
        }
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(OperationPlantRuntimeSavedData::new, OperationPlantRuntimeSavedData::load),
                DATA_NAME
        );
    }

    public static OperationPlantRuntimeSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        OperationPlantRuntimeSavedData data = new OperationPlantRuntimeSavedData();

        ListTag jobTags = tag.getList("Jobs", Tag.TAG_COMPOUND);
        for (int index = 0; index < jobTags.size(); index++) {
            CompoundTag jobTag = jobTags.getCompound(index);
            try {
                OperationJob job = new OperationJob(
                        jobTag.getLong("JobId"),
                        jobTag.getString("ProcessId"),
                        jobTag.getInt("Quantity"),
                        jobTag.getInt("Priority"),
                        jobTag.getLong("ReleaseTick"),
                        jobTag.getLong("DueTick")
                );
                OperationJobLifecycleSnapshot snapshot = new OperationJobLifecycleSnapshot(
                        job,
                        OperationJobLifecycleState.valueOf(jobTag.getString("State")),
                        jobTag.getString("ResourceId"),
                        jobTag.getLong("OutputId"),
                        jobTag.getLong("MissionId"),
                        jobTag.getLong("CompletionTick"),
                        OperationJobLifecycleSnapshot.DeliveryStatus.valueOf(jobTag.getString("DeliveryStatus"))
                );
                data.jobs.putIfAbsent(job.jobId(), snapshot);
            } catch (IllegalArgumentException ignored) {
                // Corrupt lifecycle records fail closed by remaining absent from authoritative runtime state.
            }
        }

        ListTag eventTags = tag.getList("Events", Tag.TAG_COMPOUND);
        long highestSequence = -1;
        for (int index = 0; index < eventTags.size(); index++) {
            CompoundTag eventTag = eventTags.getCompound(index);
            try {
                OperationPlantEvent event = new OperationPlantEvent(
                        eventTag.getLong("Sequence"),
                        eventTag.getLong("Tick"),
                        OperationPlantEvent.Type.valueOf(eventTag.getString("Type")),
                        eventTag.getLong("JobId"),
                        eventTag.getLong("OutputId"),
                        eventTag.getLong("MissionId"),
                        eventTag.getString("ResourceId"),
                        eventTag.getString("Detail")
                );
                data.events.add(event);
                highestSequence = Math.max(highestSequence, event.sequence());
            } catch (IllegalArgumentException ignored) {
                // One malformed history entry must not poison otherwise valid plant evidence.
            }
        }
        data.nextSequence = Math.max(tag.getLong("NextSequence"), highestSequence + 1);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag jobTags = new ListTag();
        for (OperationJobLifecycleSnapshot snapshot : jobs.values()) {
            OperationJob job = snapshot.job();
            CompoundTag jobTag = new CompoundTag();
            jobTag.putLong("JobId", job.jobId());
            jobTag.putString("ProcessId", job.processId());
            jobTag.putInt("Quantity", job.quantity());
            jobTag.putInt("Priority", job.priority());
            jobTag.putLong("ReleaseTick", job.releaseTick());
            jobTag.putLong("DueTick", job.dueTick());
            jobTag.putString("State", snapshot.state().name());
            jobTag.putString("ResourceId", snapshot.resourceId());
            jobTag.putLong("OutputId", snapshot.outputId());
            jobTag.putLong("MissionId", snapshot.missionId());
            jobTag.putLong("CompletionTick", snapshot.completionTick());
            jobTag.putString("DeliveryStatus", snapshot.deliveryStatus().name());
            jobTags.add(jobTag);
        }
        tag.put("Jobs", jobTags);

        ListTag eventTags = new ListTag();
        for (OperationPlantEvent event : events) {
            CompoundTag eventTag = new CompoundTag();
            eventTag.putLong("Sequence", event.sequence());
            eventTag.putLong("Tick", event.tick());
            eventTag.putString("Type", event.type().name());
            eventTag.putLong("JobId", event.jobId());
            eventTag.putLong("OutputId", event.outputId());
            eventTag.putLong("MissionId", event.missionId());
            eventTag.putString("ResourceId", event.resourceId());
            eventTag.putString("Detail", event.detail());
            eventTags.add(eventTag);
        }
        tag.put("Events", eventTags);
        tag.putLong("NextSequence", nextSequence);
        return tag;
    }

    public Collection<OperationJobLifecycleSnapshot> jobs() {
        return List.copyOf(jobs.values());
    }

    public OperationJobLifecycleSnapshot job(long jobId) {
        return jobs.get(jobId);
    }

    public List<OperationPlantEvent> events() {
        return List.copyOf(events);
    }

    public List<OperationPlantEvent> eventsForJob(long jobId) {
        return events.stream().filter(event -> event.jobId() == jobId).toList();
    }

    public List<OperationPlantEvent> eventsForResource(String resourceId) {
        if (resourceId == null || resourceId.isBlank()) return List.of();
        String normalized = resourceId.trim();
        return events.stream().filter(event -> normalized.equals(event.resourceId())).toList();
    }

    public boolean release(OperationJob job, long tick) {
        if (job == null || tick < 0 || jobs.containsKey(job.jobId()) || tick < job.releaseTick()) return false;
        OperationJobLifecycleSnapshot snapshot = new OperationJobLifecycleSnapshot(
                job, OperationJobLifecycleState.RELEASED, "", -1, -1, -1,
                OperationJobLifecycleSnapshot.DeliveryStatus.NONE);
        jobs.put(job.jobId(), snapshot);
        append(tick, OperationPlantEvent.Type.JOB_RELEASED, job.jobId(), -1, -1, "",
                "process=" + job.processId() + ";quantity=" + job.quantity() + ";due=" + job.dueTick());
        setDirty();
        return true;
    }

    public boolean queue(long jobId, long tick) {
        return transition(jobId, OperationJobLifecycleState.RELEASED, OperationJobLifecycleState.QUEUED,
                tick, null, null, null, OperationPlantEvent.Type.JOB_QUEUED, "QUEUED");
    }

    public boolean assign(long jobId, String resourceId, long tick) {
        if (resourceId == null || resourceId.isBlank()) return false;
        return transition(jobId, OperationJobLifecycleState.QUEUED, OperationJobLifecycleState.ASSIGNED,
                tick, resourceId.trim(), null, null, OperationPlantEvent.Type.JOB_ASSIGNED, "ASSIGNED");
    }

    public boolean start(long jobId, long tick) {
        return transition(jobId, OperationJobLifecycleState.ASSIGNED, OperationJobLifecycleState.RUNNING,
                tick, null, null, null, OperationPlantEvent.Type.JOB_STARTED, "RUNNING");
    }

    public boolean qualityHold(long jobId, long outputId, long tick) {
        if (outputId < 0) return false;
        return transition(jobId, OperationJobLifecycleState.RUNNING, OperationJobLifecycleState.QUALITY_HOLD,
                tick, null, outputId, null, OperationPlantEvent.Type.QUALITY_HOLD, "QUALITY_HOLD");
    }

    public boolean transport(long jobId, long outputId, long missionId, long tick) {
        if (outputId < 0 || missionId < 0) return false;
        return transition(jobId, OperationJobLifecycleState.QUALITY_HOLD, OperationJobLifecycleState.TRANSPORT,
                tick, null, outputId, missionId, OperationPlantEvent.Type.TRANSPORT_REQUESTED, "TRANSPORT");
    }

    public boolean complete(long jobId, long outputId, long tick) {
        OperationJobLifecycleSnapshot current = jobs.get(jobId);
        if (current == null || current.state() != OperationJobLifecycleState.TRANSPORT || outputId < 0 || tick < 0) {
            return false;
        }
        if (current.outputId() >= 0 && current.outputId() != outputId) return false;

        OperationJobLifecycleSnapshot.DeliveryStatus delivery = deliveryStatus(current.job(), tick);
        OperationJobLifecycleSnapshot next = new OperationJobLifecycleSnapshot(
                current.job(), OperationJobLifecycleState.COMPLETED, current.resourceId(), outputId,
                current.missionId(), tick, delivery);
        jobs.put(jobId, next);
        append(tick, OperationPlantEvent.Type.JOB_COMPLETED, jobId, outputId, current.missionId(),
                current.resourceId(), "completionTick=" + tick);
        if (delivery == OperationJobLifecycleSnapshot.DeliveryStatus.ON_TIME) {
            append(tick, OperationPlantEvent.Type.DELIVERY_ON_TIME, jobId, outputId, current.missionId(),
                    current.resourceId(), "dueTick=" + current.job().dueTick() + ";completionTick=" + tick);
        } else if (delivery == OperationJobLifecycleSnapshot.DeliveryStatus.LATE) {
            append(tick, OperationPlantEvent.Type.DELIVERY_LATE, jobId, outputId, current.missionId(),
                    current.resourceId(), "dueTick=" + current.job().dueTick() + ";completionTick=" + tick);
        }
        setDirty();
        return true;
    }

    public boolean recordQuality(OperationQualityInspectionEvidence evidence, long tick) {
        if (evidence == null || tick < 0 || !evidence.inspectionConfirmed() || evidence.faultActive()
                || evidence.evidenceQuality() != dev.redstoneengineering.core.port.PortQuality.VALID
                || evidence.dispositionUnits() != evidence.inspectedUnits()) return false;
        OperationJobLifecycleSnapshot current = jobs.get(evidence.jobId());
        if (current == null) return false;
        if (current.state() == OperationJobLifecycleState.RUNNING) {
            if (!qualityHold(evidence.jobId(), evidence.outputId(), tick)) return false;
            current = jobs.get(evidence.jobId());
        } else if (current.state() != OperationJobLifecycleState.QUALITY_HOLD) {
            return false;
        }
        if (current.outputId() >= 0 && current.outputId() != evidence.outputId()) return false;

        append(tick, OperationPlantEvent.Type.QUALITY_INSPECTED, evidence.jobId(), evidence.outputId(), -1,
                current.resourceId(), "inspected=" + evidence.inspectedUnits());
        if (evidence.goodUnits() > 0) {
            append(tick, OperationPlantEvent.Type.QUALITY_GOOD, evidence.jobId(), evidence.outputId(), -1,
                    current.resourceId(), "units=" + evidence.goodUnits());
        }
        if (evidence.rejectUnits() > 0) {
            append(tick, OperationPlantEvent.Type.QUALITY_REJECTED, evidence.jobId(), evidence.outputId(), -1,
                    current.resourceId(), "units=" + evidence.rejectUnits());
        }
        if (evidence.reworkUnits() > 0) {
            append(tick, OperationPlantEvent.Type.REWORK_REQUESTED, evidence.jobId(), evidence.outputId(), -1,
                    current.resourceId(), "units=" + evidence.reworkUnits());
        }
        setDirty();
        return true;
    }

    public boolean recordTransportDemand(long jobId, OperationTransportDemand demand, long tick) {
        if (demand == null) return false;
        return transport(jobId, demand.outputId(), demand.missionId(), tick);
    }

    public boolean amrAssigned(long jobId, long outputId, long missionId, String robotId, long tick) {
        return appendLogistics(jobId, outputId, missionId, robotId, tick,
                OperationPlantEvent.Type.AMR_ASSIGNED, "AMR_ASSIGNED");
    }

    public boolean amrPickedUp(long jobId, long outputId, long missionId, String robotId, long tick) {
        return appendLogistics(jobId, outputId, missionId, robotId, tick,
                OperationPlantEvent.Type.AMR_PICKED_UP, "PICKUP_CONFIRMED");
    }

    public boolean amrArrived(long jobId, long outputId, long missionId, String robotId, long tick) {
        return appendLogistics(jobId, outputId, missionId, robotId, tick,
                OperationPlantEvent.Type.AMR_ARRIVED, "TARGET_REACHED");
    }

    public boolean amrUnloaded(long jobId, long outputId, long missionId, String robotId, long tick) {
        return appendLogistics(jobId, outputId, missionId, robotId, tick,
                OperationPlantEvent.Type.AMR_UNLOADED, "UNLOAD_CONFIRMED");
    }

    public boolean downstreamReceived(long jobId, long outputId, long missionId, String receiverId, long tick) {
        return appendLogistics(jobId, outputId, missionId, receiverId, tick,
                OperationPlantEvent.Type.DOWNSTREAM_RECEIVED, "RECEIPT_CONFIRMED");
    }

    public boolean maintenanceDue(String resourceId, String maintenanceId, long tick) {
        if (!validMaintenanceIdentity(resourceId, maintenanceId, tick)) return false;
        OperationPlantEvent.Type latest = latestMaintenanceType(resourceId.trim());
        if (latest == OperationPlantEvent.Type.MAINTENANCE_DUE
                || latest == OperationPlantEvent.Type.MAINTENANCE_STARTED
                || latest == OperationPlantEvent.Type.MAINTENANCE_FAULT) return false;
        append(tick, OperationPlantEvent.Type.MAINTENANCE_DUE, -1, -1, -1, resourceId.trim(), maintenanceId.trim());
        setDirty();
        return true;
    }

    public boolean maintenanceStarted(String resourceId, String maintenanceId, long tick) {
        if (!validMaintenanceIdentity(resourceId, maintenanceId, tick)) return false;
        if (!latestMaintenanceMatches(resourceId.trim(), maintenanceId.trim(), OperationPlantEvent.Type.MAINTENANCE_DUE)) {
            return false;
        }
        append(tick, OperationPlantEvent.Type.MAINTENANCE_STARTED, -1, -1, -1, resourceId.trim(), maintenanceId.trim());
        setDirty();
        return true;
    }

    public boolean maintenanceFault(String resourceId, String maintenanceId, long tick, String faultCode) {
        if (!validMaintenanceIdentity(resourceId, maintenanceId, tick) || faultCode == null || faultCode.isBlank()) return false;
        if (!latestMaintenanceMatches(resourceId.trim(), maintenanceId.trim(), OperationPlantEvent.Type.MAINTENANCE_STARTED)) {
            return false;
        }
        append(tick, OperationPlantEvent.Type.MAINTENANCE_FAULT, -1, -1, -1, resourceId.trim(),
                maintenanceId.trim() + ";fault=" + faultCode.trim());
        setDirty();
        return true;
    }

    public boolean maintenanceCompleted(String resourceId, String maintenanceId, long tick) {
        if (!validMaintenanceIdentity(resourceId, maintenanceId, tick)) return false;
        String normalizedResource = resourceId.trim();
        String normalizedMaintenance = maintenanceId.trim();
        OperationPlantEvent latest = latestMaintenanceEvent(normalizedResource);
        if (latest == null || (latest.type() != OperationPlantEvent.Type.MAINTENANCE_STARTED
                && latest.type() != OperationPlantEvent.Type.MAINTENANCE_FAULT)
                || !maintenanceId(latest.detail()).equals(normalizedMaintenance)) return false;
        append(tick, OperationPlantEvent.Type.MAINTENANCE_COMPLETED, -1, -1, -1,
                normalizedResource, normalizedMaintenance);
        setDirty();
        return true;
    }

    private boolean transition(
            long jobId,
            OperationJobLifecycleState expected,
            OperationJobLifecycleState nextState,
            long tick,
            String resourceId,
            Long outputId,
            Long missionId,
            OperationPlantEvent.Type eventType,
            String detail
    ) {
        OperationJobLifecycleSnapshot current = jobs.get(jobId);
        if (current == null || current.state() != expected || tick < 0) return false;
        String nextResource = resourceId == null ? current.resourceId() : resourceId;
        long nextOutput = outputId == null ? current.outputId() : outputId;
        long nextMission = missionId == null ? current.missionId() : missionId;
        OperationJobLifecycleSnapshot next = new OperationJobLifecycleSnapshot(
                current.job(), nextState, nextResource, nextOutput, nextMission, -1,
                OperationJobLifecycleSnapshot.DeliveryStatus.NONE);
        jobs.put(jobId, next);
        append(tick, eventType, jobId, nextOutput, nextMission, nextResource, detail);
        setDirty();
        return true;
    }

    private boolean appendLogistics(
            long jobId, long outputId, long missionId, String actorId, long tick,
            OperationPlantEvent.Type type, String detail
    ) {
        OperationJobLifecycleSnapshot current = jobs.get(jobId);
        if (current == null || current.state() != OperationJobLifecycleState.TRANSPORT
                || outputId < 0 || missionId < 0 || tick < 0
                || current.outputId() != outputId || current.missionId() != missionId) return false;
        append(tick, type, jobId, outputId, missionId, actorId == null ? "" : actorId.trim(), detail);
        setDirty();
        return true;
    }

    private OperationJobLifecycleSnapshot.DeliveryStatus deliveryStatus(OperationJob job, long completionTick) {
        if (!job.hasDueDate()) return OperationJobLifecycleSnapshot.DeliveryStatus.NONE;
        return completionTick <= job.dueTick()
                ? OperationJobLifecycleSnapshot.DeliveryStatus.ON_TIME
                : OperationJobLifecycleSnapshot.DeliveryStatus.LATE;
    }

    private void append(long tick, OperationPlantEvent.Type type, long jobId, long outputId, long missionId,
                        String resourceId, String detail) {
        events.add(new OperationPlantEvent(nextSequence++, tick, type, jobId, outputId, missionId,
                resourceId, detail));
    }

    private boolean validMaintenanceIdentity(String resourceId, String maintenanceId, long tick) {
        return tick >= 0 && resourceId != null && !resourceId.isBlank()
                && maintenanceId != null && !maintenanceId.isBlank();
    }

    private OperationPlantEvent.Type latestMaintenanceType(String resourceId) {
        OperationPlantEvent event = latestMaintenanceEvent(resourceId);
        return event == null ? null : event.type();
    }

    private OperationPlantEvent latestMaintenanceEvent(String resourceId) {
        for (int index = events.size() - 1; index >= 0; index--) {
            OperationPlantEvent event = events.get(index);
            if (resourceId.equals(event.resourceId()) && isMaintenance(event.type())) return event;
        }
        return null;
    }

    private boolean latestMaintenanceMatches(String resourceId, String maintenanceId, OperationPlantEvent.Type type) {
        OperationPlantEvent latest = latestMaintenanceEvent(resourceId);
        return latest != null && latest.type() == type && maintenanceId(latest.detail()).equals(maintenanceId);
    }

    private String maintenanceId(String detail) {
        if (detail == null) return "";
        int delimiter = detail.indexOf(';');
        return delimiter < 0 ? detail : detail.substring(0, delimiter);
    }

    private boolean isMaintenance(OperationPlantEvent.Type type) {
        return type == OperationPlantEvent.Type.MAINTENANCE_DUE
                || type == OperationPlantEvent.Type.MAINTENANCE_STARTED
                || type == OperationPlantEvent.Type.MAINTENANCE_FAULT
                || type == OperationPlantEvent.Type.MAINTENANCE_COMPLETED;
    }
}
