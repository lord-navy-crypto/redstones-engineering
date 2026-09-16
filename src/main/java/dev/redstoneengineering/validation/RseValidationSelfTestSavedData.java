package dev.redstoneengineering.validation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Persistent identity/origin registry for manually placed validation self-tests. */
public final class RseValidationSelfTestSavedData extends SavedData {
    private static final String DATA_NAME = "rse_validation_selftests";

    public record Placement(String testId, BlockPos origin, long placedTick) {
        public Placement {
            if (testId == null || testId.isBlank()) throw new IllegalArgumentException("testId required");
            if (origin == null) throw new IllegalArgumentException("origin required");
            if (placedTick < 0) throw new IllegalArgumentException("placedTick must be nonnegative");
            testId = testId.trim();
        }
    }

    private final Map<String, Placement> placements = new LinkedHashMap<>();

    public static RseValidationSelfTestSavedData get(ServerLevel level) {
        if (level == null || level.getServer() == null) throw new IllegalArgumentException("server level required");
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RseValidationSelfTestSavedData::new, RseValidationSelfTestSavedData::load),
                DATA_NAME
        );
    }

    public static RseValidationSelfTestSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        RseValidationSelfTestSavedData data = new RseValidationSelfTestSavedData();
        ListTag rows = tag.getList("Placements", Tag.TAG_COMPOUND);
        for (int i = 0; i < rows.size(); i++) {
            CompoundTag row = rows.getCompound(i);
            try {
                Placement placement = new Placement(
                        row.getString("TestId"),
                        BlockPos.of(row.getLong("Origin")),
                        row.getLong("PlacedTick")
                );
                data.placements.putIfAbsent(placement.testId(), placement);
            } catch (IllegalArgumentException ignored) {
                // Corrupt validation metadata fails closed by remaining absent.
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag rows = new ListTag();
        for (Placement placement : placements.values()) {
            CompoundTag row = new CompoundTag();
            row.putString("TestId", placement.testId());
            row.putLong("Origin", placement.origin().asLong());
            row.putLong("PlacedTick", placement.placedTick());
            rows.add(row);
        }
        tag.put("Placements", rows);
        return tag;
    }

    public Placement placement(String testId) {
        if (testId == null) return null;
        return placements.get(testId.trim());
    }

    public List<Placement> placements() {
        return List.copyOf(placements.values());
    }

    public void put(String testId, BlockPos origin, long placedTick) {
        Placement placement = new Placement(testId, origin, placedTick);
        placements.put(placement.testId(), placement);
        setDirty();
    }

    public boolean remove(String testId) {
        if (testId == null || testId.isBlank()) return false;
        if (placements.remove(testId.trim()) == null) return false;
        setDirty();
        return true;
    }
}
