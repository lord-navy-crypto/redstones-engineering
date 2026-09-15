package dev.redstoneengineering.operations.world;

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

/** Server-owned persistent Operations plant identity/configuration state. */
public final class OperationPlantSavedData extends SavedData {
    private static final String DATA_NAME = "rse_operations_plant";
    private final Map<String, OperationWorkcellBinding> workcells = new LinkedHashMap<>();

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
}
