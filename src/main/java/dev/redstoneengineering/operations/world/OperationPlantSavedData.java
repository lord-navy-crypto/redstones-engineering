package dev.redstoneengineering.operations.world;

import dev.redstoneengineering.operations.OperationBufferLot;
import dev.redstoneengineering.operations.OperationBufferSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Server-owned persistent Operations plant identity/configuration and logical WIP state. */
public final class OperationPlantSavedData extends SavedData {
    private static final String DATA_NAME = "rse_operations_plant";
    private final Map<String, OperationWorkcellBinding> workcells = new LinkedHashMap<>();
    private final Map<String, OperationBufferSnapshot> buffers = new LinkedHashMap<>();

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
            java.util.ArrayList<OperationWorkcellBinding.ResourceBinding> resources = new java.util.ArrayList<>();
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
            java.util.ArrayList<OperationBufferLot> lots = new java.util.ArrayList<>();
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
            // Never transform corrupt/unknown persisted WIP into an authoritative empty buffer.
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
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
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
        if (workcells.remove(workcellId.trim()) == null) return false;
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

    /** Replaces one immutable logical buffer snapshot and marks server SavedData dirty. */
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
        if (buffers.remove(bufferId.trim()) == null) return false;
        setDirty();
        return true;
    }
}
