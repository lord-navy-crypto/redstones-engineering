package dev.redstoneengineering.operations.world;

import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.operations.OperationBufferLot;
import dev.redstoneengineering.operations.OperationBufferSnapshot;
import dev.redstoneengineering.operations.OperationJob;
import dev.redstoneengineering.operations.OperationResourceMaintenanceSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Server-owned persistent Operations plant identity, logical WIP and runtime evidence. */
public final class OperationPlantSavedData extends SavedData {
    private static final String DATA_NAME = "rse_operations_plant";
    private static final int RUNTIME_SCHEMA_VERSION = 2;
    private static final int MAX_PLANT_EVENTS = 1024;
    private static final int MAX_TERMINAL_JOB_RECORDS = 2048;

    private final Map<String, OperationWorkcellBinding> workcells = new LinkedHashMap<>();
    private final Map<String, OperationBufferSnapshot> buffers = new LinkedHashMap<>();
    private final Map<String, OperationWorkcellBufferBinding> workcellBuffers = new LinkedHashMap<>();
    private final Map<String, OperationResourceMaintenanceSnapshot> maintenanceSnapshots = new LinkedHashMap<>();
    private final Map<Long, OperationJobLifecycleRecord> jobs = new LinkedHashMap<>();
    private final List<OperationPlantEvent> plantEvents = new ArrayList<>();
    private long nextEventSequence;

    public static OperationPlantSavedData get(ServerLevel level) {
        if (level == null || level.getServer() == null) {
            throw new IllegalArgumentException("server level is required");
        }
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(OperationPlantSavedData::new, OperationPlantSavedData::load),
                DATA_NAME
        );
    }

    public static OperationPlantSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        OperationPlantSavedData data = new OperationPlantSavedData();
        ListTag workcellTags = tag.getList("Workcells", Tag.TAG_COMPOUND);
        for (int index = 0; index < workcellTags.size(); index++) {
            CompoundTag workcellTag = workcellTags.getCompound(index);
            String workcellId = workcellTag.getString("WorkcellId");
            ListTag resourceTags = workcellTag.getList("Resources", Tag.TAG_COMPOUND);
            ArrayList<OperationWorkcellBinding.ResourceBinding> resources = new ArrayList<>();
            for (int resourceIndex = 0; resourceIndex < resourceTags.size(); resourceIndex++) {
                CompoundTag resourceTag = resourceTags.getCompound(resourceIndex);
                resources.add(new OperationWorkcellBinding.ResourceBinding(
                        BlockPos.of(resourceTag.getLong("Position")),
                        resourceTag.getString("ExpectedResourceId")
                ));
            }
            OperationWorkcellBinding binding = new OperationWorkcellBinding(workcellId, resources);
            if (binding.validBinding() && !data.workcells.containsKey(binding.workcellId())) {
                data.workcells.put(binding.workcellId(), binding);
            }
        }

        ListTag bufferTags = tag.getList("Buffers", Tag.TAG_COMPOUND);
        for (int index = 0; index < bufferTags.size(); index++) {
            CompoundTag bufferTag = bufferTags.getCompound(index);
            String bufferId = bufferTag.getString("BufferId");
            BlockPos location = BlockPos.of(bufferTag.getLong("Location"));
            int capacityUnits = bufferTag.getInt("CapacityUnits");
            ListTag lotTags = bufferTag.getList("Lots", Tag.TAG_COMPOUND);
            ArrayList<OperationBufferLot> lots = new ArrayList<>();
            boolean invalidLotEvidence = false;
            for (int lotIndex = 0; lotIndex < lotTags.size(); lotIndex++) {
                CompoundTag lotTag = lotTags.getCompound(lotIndex);
                try {
                    lots.add(new OperationBufferLot(
                            lotTag.getLong("OutputId"),
                            lotTag.getLong("JobId"),
                            lotTag.getInt("Units")
                    ));
                } catch (IllegalArgumentException ignored) {
                    invalidLotEvidence = true;
                    break;
                }
            }
            if (invalidLotEvidence) continue;
            try {
                OperationBufferSnapshot buffer = new OperationBufferSnapshot(
                        bufferId, location, capacityUnits, lots);
                boolean locationAlreadyUsed = data.buffers.values().stream()
                        .anyMatch(existing -> existing.location().equals(buffer.location()));
                if (!locationAlreadyUsed && !data.buffers.containsKey(buffer.bufferId())) {
                    data.buffers.put(buffer.bufferId(), buffer);
                }
            } catch (IllegalArgumentException | ArithmeticException ignored) {
                // Invalid/corrupt persisted WIP fails closed by remaining absent from authoritative state.
            }
        }

        ListTag workcellBufferTags = tag.getList("WorkcellBuffers", Tag.TAG_COMPOUND);
        for (int index = 0; index < workcellBufferTags.size(); index++) {
            CompoundTag bindingTag = workcellBufferTags.getCompound(index);
            OperationWorkcellBufferBinding binding = new OperationWorkcellBufferBinding(
                    bindingTag.getString("WorkcellId"),
                    bindingTag.getString("InputBufferId"),
                    bindingTag.getString("OutputBufferId")
            );
            if (!binding.validBinding()) continue;
            if (!data.workcells.containsKey(binding.workcellId())) continue;
            if (!data.buffers.containsKey(binding.inputBufferId()) || !data.buffers.containsKey(binding.outputBufferId())) continue;
            data.workcellBuffers.putIfAbsent(binding.workcellId(), binding);
        }

        ListTag maintenanceTags = tag.getList("ResourceMaintenance", Tag.TAG_COMPOUND);
        for (int index = 0; index < maintenanceTags.size(); index++) {
            CompoundTag maintenanceTag = maintenanceTags.getCompound(index);
            try {
                String maintenanceId = maintenanceTag.getString("MaintenanceId");
                if (maintenanceId.isBlank()) maintenanceId = null;
                OperationResourceMaintenanceSnapshot snapshot = new OperationResourceMaintenanceSnapshot(
                        maintenanceTag.getString("ResourceId"),
                        OperationResourceMaintenanceSnapshot.State.valueOf(maintenanceTag.getString("State")),
                        maintenanceId,
                        PortQuality.valueOf(maintenanceTag.getString("EvidenceQuality")),
                        maintenanceTag.getBoolean("FaultActive")
                );
                data.maintenanceSnapshots.putIfAbsent(snapshot.resourceId(), snapshot);
            } catch (IllegalArgumentException ignored) {
                // Corrupt maintenance evidence is isolated rather than inventing a production-ready state.
            }
        }

        ListTag jobTags = tag.getList("RuntimeJobs", Tag.TAG_COMPOUND);
        for (int index = 0; index < jobTags.size(); index++) {
            CompoundTag jobTag = jobTags.getCompound(index);
            try {
                OperationJobLifecycleRecord.Status status = OperationJobLifecycleRecord.Status.valueOf(jobTag.getString("Status"));
                OperationJobLifecycleRecord record = new OperationJobLifecycleRecord(
                        jobTag.getLong("JobId"),
                        jobTag.getString("ProcessId"),
                        jobTag.getInt("Quantity"),
                        jobTag.getInt("Priority"),
                        jobTag.getLong("ReleaseTick"),
                        jobTag.getLong("DueTick"),
                        jobTag.getLong("AdmittedTick"),
                        jobTag.getLong("StateTick"),
                        jobTag.getLong("CompletionTick"),
                        status
                );
                data.jobs.put(record.jobId(), record);
            } catch (IllegalArgumentException ignored) {
                // Corrupt lifecycle evidence is isolated instead of preventing the world from loading.
            }
        }
        data.trimTerminalJobs();

        ListTag eventTags = tag.getList("PlantEvents", Tag.TAG_COMPOUND);
        for (int index = 0; index < eventTags.size(); index++) {
            CompoundTag eventTag = eventTags.getCompound(index);
            try {
                OperationPlantEvent event = new OperationPlantEvent(
                        eventTag.getLong("Sequence"),
                        eventTag.getLong("GameTick"),
                        OperationPlantEvent.Type.valueOf(eventTag.getString("Type")),
                        eventTag.getString("SubjectId"),
                        eventTag.getLong("JobId"),
                        eventTag.getString("Detail")
                );
                data.plantEvents.add(event);
                data.nextEventSequence = Math.max(data.nextEventSequence, event.sequence() + 1);
            } catch (IllegalArgumentException ignored) {
                // One malformed history row must not invalidate the remaining plant evidence.
            }
        }
        data.plantEvents.sort(Comparator.comparingLong(OperationPlantEvent::sequence));
        data.trimPlantEvents();
        data.nextEventSequence = Math.max(data.nextEventSequence, tag.getLong("NextEventSequence"));
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("RuntimeSchemaVersion", RUNTIME_SCHEMA_VERSION);

        ListTag workcellTags = new ListTag();
        for (OperationWorkcellBinding binding : workcells.values()) {
            CompoundTag workcellTag = new CompoundTag();
            workcellTag.putString("WorkcellId", binding.workcellId());
            ListTag resourceTags = new ListTag();
            for (OperationWorkcellBinding.ResourceBinding resource : binding.resourceBindings()) {
                CompoundTag resourceTag = new CompoundTag();
                resourceTag.putLong("Position", resource.position().asLong());
                resourceTag.putString("ExpectedResourceId", resource.expectedResourceId());
                resourceTags.add(resourceTag);
            }
            workcellTag.put("Resources", resourceTags);
            workcellTags.add(workcellTag);
        }
        tag.put("Workcells", workcellTags);

        ListTag bufferTags = new ListTag();
        for (OperationBufferSnapshot buffer : buffers.values()) {
            CompoundTag bufferTag = new CompoundTag();
            bufferTag.putString("BufferId", buffer.bufferId());
            bufferTag.putLong("Location", buffer.location().asLong());
            bufferTag.putInt("CapacityUnits", buffer.capacityUnits());
            ListTag lotTags = new ListTag();
            for (OperationBufferLot lot : buffer.lots()) {
                CompoundTag lotTag = new CompoundTag();
                lotTag.putLong("OutputId", lot.outputId());
                lotTag.putLong("JobId", lot.jobId());
                lotTag.putInt("Units", lot.units());
                lotTags.add(lotTag);
            }
            bufferTag.put("Lots", lotTags);
            bufferTags.add(bufferTag);
        }
        tag.put("Buffers", bufferTags);

        ListTag workcellBufferTags = new ListTag();
        for (OperationWorkcellBufferBinding binding : workcellBuffers.values()) {
            CompoundTag bindingTag = new CompoundTag();
            bindingTag.putString("WorkcellId", binding.workcellId());
            bindingTag.putString("InputBufferId", binding.inputBufferId());
            bindingTag.putString("OutputBufferId", binding.outputBufferId());
            workcellBufferTags.add(bindingTag);
        }
        tag.put("WorkcellBuffers", workcellBufferTags);

        ListTag maintenanceTags = new ListTag();
        for (OperationResourceMaintenanceSnapshot snapshot : maintenanceSnapshots.values()) {
            CompoundTag maintenanceTag = new CompoundTag();
            maintenanceTag.putString("ResourceId", snapshot.resourceId());
            maintenanceTag.putString("State", snapshot.state().name());
            maintenanceTag.putString("MaintenanceId", snapshot.maintenanceId() == null ? "" : snapshot.maintenanceId());
            maintenanceTag.putString("EvidenceQuality", snapshot.evidenceQuality().name());
            maintenanceTag.putBoolean("FaultActive", snapshot.faultActive());
            maintenanceTags.add(maintenanceTag);
        }
        tag.put("ResourceMaintenance", maintenanceTags);

        ListTag jobTags = new ListTag();
        for (OperationJobLifecycleRecord record : jobs.values()) {
            CompoundTag jobTag = new CompoundTag();
            jobTag.putLong("JobId", record.jobId());
            jobTag.putString("ProcessId", record.processId());
            jobTag.putInt("Quantity", record.quantity());
            jobTag.putInt("Priority", record.priority());
            jobTag.putLong("ReleaseTick", record.releaseTick());
            jobTag.putLong("DueTick", record.dueTick());
            jobTag.putLong("AdmittedTick", record.admittedTick());
            jobTag.putLong("StateTick", record.stateTick());
            jobTag.putLong("CompletionTick", record.completionTick());
            jobTag.putString("Status", record.status().name());
            jobTags.add(jobTag);
        }
        tag.put("RuntimeJobs", jobTags);

        ListTag eventTags = new ListTag();
        for (OperationPlantEvent event : plantEvents) {
            CompoundTag eventTag = new CompoundTag();
            eventTag.putLong("Sequence", event.sequence());
            eventTag.putLong("GameTick", event.gameTick());
            eventTag.putString("Type", event.type().name());
            eventTag.putString("SubjectId", event.subjectId());
            eventTag.putLong("JobId", event.jobId());
            eventTag.putString("Detail", event.detail());
            eventTags.add(eventTag);
        }
        tag.put("PlantEvents", eventTags);
        tag.putLong("NextEventSequence", nextEventSequence);
        return tag;
    }

    public Collection<OperationWorkcellBinding> workcells() {
        return List.copyOf(workcells.values());
    }

    public OperationWorkcellBinding workcell(String workcellId) {
        if (workcellId == null) return null;
        return workcells.get(workcellId.trim());
    }

    public boolean putWorkcell(OperationWorkcellBinding binding) {
        if (binding == null || !binding.validBinding()) return false;
        workcells.put(binding.workcellId(), binding);
        setDirty();
        return true;
    }

    public boolean removeWorkcell(String workcellId) {
        if (workcellId == null || workcellId.isBlank()) return false;
        String normalized = workcellId.trim();
        if (workcells.remove(normalized) == null) return false;
        workcellBuffers.remove(normalized);
        setDirty();
        return true;
    }

    public Collection<OperationBufferSnapshot> buffers() {
        return List.copyOf(buffers.values());
    }

    public OperationBufferSnapshot buffer(String bufferId) {
        if (bufferId == null) return null;
        return buffers.get(bufferId.trim());
    }

    public boolean putBuffer(OperationBufferSnapshot buffer) {
        if (buffer == null) return false;
        for (OperationBufferSnapshot existing : buffers.values()) {
            if (!existing.bufferId().equals(buffer.bufferId())
                    && existing.location().equals(buffer.location())) {
                return false;
            }
        }
        buffers.put(buffer.bufferId(), buffer);
        setDirty();
        return true;
    }

    public boolean removeBuffer(String bufferId) {
        if (bufferId == null || bufferId.isBlank()) return false;
        String normalized = bufferId.trim();
        if (buffers.remove(normalized) == null) return false;
        workcellBuffers.entrySet().removeIf(entry ->
                entry.getValue().inputBufferId().equals(normalized)
                        || entry.getValue().outputBufferId().equals(normalized));
        setDirty();
        return true;
    }

    public Collection<OperationWorkcellBufferBinding> workcellBufferBindings() {
        return List.copyOf(workcellBuffers.values());
    }

    public OperationWorkcellBufferBinding workcellBufferBinding(String workcellId) {
        if (workcellId == null) return null;
        return workcellBuffers.get(workcellId.trim());
    }

    public boolean putWorkcellBufferBinding(OperationWorkcellBufferBinding binding) {
        if (binding == null || !binding.validBinding()) return false;
        if (!workcells.containsKey(binding.workcellId())) return false;
        if (!buffers.containsKey(binding.inputBufferId()) || !buffers.containsKey(binding.outputBufferId())) return false;
        workcellBuffers.put(binding.workcellId(), binding);
        setDirty();
        return true;
    }

    public boolean removeWorkcellBufferBinding(String workcellId) {
        if (workcellId == null || workcellId.isBlank()) return false;
        if (workcellBuffers.remove(workcellId.trim()) == null) return false;
        setDirty();
        return true;
    }

    public Collection<OperationResourceMaintenanceSnapshot> maintenanceSnapshots() {
        return List.copyOf(maintenanceSnapshots.values());
    }

    public OperationResourceMaintenanceSnapshot maintenanceSnapshot(String resourceId) {
        if (resourceId == null || resourceId.isBlank()) return null;
        return maintenanceSnapshots.get(resourceId.trim());
    }

    public boolean putMaintenanceSnapshot(OperationResourceMaintenanceSnapshot snapshot) {
        if (snapshot == null) return false;
        maintenanceSnapshots.put(snapshot.resourceId(), snapshot);
        setDirty();
        return true;
    }

    public boolean removeMaintenanceSnapshot(String resourceId) {
        if (resourceId == null || resourceId.isBlank()) return false;
        if (maintenanceSnapshots.remove(resourceId.trim()) == null) return false;
        setDirty();
        return true;
    }

    public Collection<OperationJobLifecycleRecord> jobLifecycles() {
        return List.copyOf(jobs.values());
    }

    public OperationJobLifecycleRecord jobLifecycle(long jobId) {
        return jobs.get(jobId);
    }

    public boolean recordJobAdmitted(OperationJob job, long gameTick) {
        if (job == null || gameTick < 0 || jobs.containsKey(job.jobId())) return false;
        try {
            OperationJobLifecycleRecord record = OperationJobLifecycleRecord.admitted(job, gameTick);
            jobs.put(record.jobId(), record);
            appendEvent(OperationPlantEvent.Type.JOB, gameTick, record.processId(), record.jobId(), "ADMITTED");
            trimTerminalJobs();
            setDirty();
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public boolean transitionJob(long jobId, OperationJobLifecycleRecord.Status status, long gameTick, String detail) {
        OperationJobLifecycleRecord current = jobs.get(jobId);
        if (current == null || status == null || gameTick < current.stateTick()) return false;
        if (current.status().terminal()) {
            return current.status() == status && current.stateTick() == gameTick;
        }
        try {
            OperationJobLifecycleRecord next = current.transition(status, gameTick);
            jobs.put(jobId, next);
            String normalizedDetail = detail == null || detail.isBlank() ? status.name() : detail.trim();
            appendEvent(OperationPlantEvent.Type.JOB, gameTick, next.processId(), jobId, normalizedDetail);
            if (status == OperationJobLifecycleRecord.Status.COMPLETED) {
                String deliveryDetail;
                if (!next.hasDueDate()) {
                    deliveryDetail = "COMPLETED_NO_DUE_DATE";
                } else if (next.onTime()) {
                    deliveryDetail = "ON_TIME";
                } else {
                    deliveryDetail = "LATE_BY_" + next.latenessTicks() + "_TICKS";
                }
                appendEvent(OperationPlantEvent.Type.DELIVERY, gameTick, next.processId(), jobId, deliveryDetail);
            }
            trimTerminalJobs();
            setDirty();
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public boolean recordPlantEvent(OperationPlantEvent.Type type, long gameTick, String subjectId, long jobId, String detail) {
        if (type == null || gameTick < 0 || jobId < -1) return false;
        appendEvent(type, gameTick, subjectId, jobId, detail);
        setDirty();
        return true;
    }

    public List<OperationPlantEvent> plantEvents() {
        return List.copyOf(plantEvents);
    }

    public List<OperationPlantEvent> plantEvents(OperationPlantEvent.Type type) {
        if (type == null) return List.of();
        return plantEvents.stream().filter(event -> event.type() == type).toList();
    }

    public long onTimeDeliveryCount() {
        return plantEvents.stream()
                .filter(event -> event.type() == OperationPlantEvent.Type.DELIVERY)
                .filter(event -> "ON_TIME".equals(event.detail()))
                .count();
    }

    public long lateDeliveryCount() {
        return plantEvents.stream()
                .filter(event -> event.type() == OperationPlantEvent.Type.DELIVERY)
                .filter(event -> event.detail().startsWith("LATE_BY_"))
                .count();
    }

    private void appendEvent(OperationPlantEvent.Type type, long gameTick, String subjectId, long jobId, String detail) {
        plantEvents.add(new OperationPlantEvent(nextEventSequence++, gameTick, type, subjectId, jobId, detail));
        trimPlantEvents();
    }

    private void trimPlantEvents() {
        while (plantEvents.size() > MAX_PLANT_EVENTS) {
            plantEvents.remove(0);
        }
    }

    private void trimTerminalJobs() {
        int terminalCount = 0;
        for (OperationJobLifecycleRecord record : jobs.values()) {
            if (record.status().terminal()) terminalCount++;
        }
        if (terminalCount <= MAX_TERMINAL_JOB_RECORDS) return;

        Iterator<Map.Entry<Long, OperationJobLifecycleRecord>> iterator = jobs.entrySet().iterator();
        while (iterator.hasNext() && terminalCount > MAX_TERMINAL_JOB_RECORDS) {
            Map.Entry<Long, OperationJobLifecycleRecord> entry = iterator.next();
            if (entry.getValue().status().terminal()) {
                iterator.remove();
                terminalCount--;
            }
        }
    }
}
