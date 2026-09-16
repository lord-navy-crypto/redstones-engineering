package dev.redstoneengineering.validation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent location and acceptance-sequence state for the modular Validation Plant v1. */
public final class RseValidationPlantSavedData extends SavedData {
    private static final String DATA_NAME = "rse_validation_plant_v1";

    public record Placement(
            BlockPos origin,
            long placedTick,
            int phase,
            long phaseStartedTick,
            boolean retestPressed
    ) {
        public Placement {
            if (origin == null) throw new IllegalArgumentException("origin required");
            if (placedTick < 0 || phaseStartedTick < 0) throw new IllegalArgumentException("ticks must be nonnegative");
            if (phase < 0) throw new IllegalArgumentException("phase must be nonnegative");
        }

        public static Placement fresh(BlockPos origin, long tick) {
            return new Placement(origin, tick, 0, tick, false);
        }

        public Placement advance(long tick) {
            return new Placement(origin, placedTick, phase + 1, tick, retestPressed);
        }

        public Placement reset(long tick, boolean pressed) {
            return new Placement(origin, tick, 0, tick, pressed);
        }

        public Placement withRetestPressed(boolean pressed) {
            return new Placement(origin, placedTick, phase, phaseStartedTick, pressed);
        }
    }

    private Placement placement;

    public static RseValidationPlantSavedData get(ServerLevel level) {
        if (level == null || level.getServer() == null) throw new IllegalArgumentException("server level required");
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RseValidationPlantSavedData::new, RseValidationPlantSavedData::load),
                DATA_NAME
        );
    }

    public static RseValidationPlantSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        RseValidationPlantSavedData data = new RseValidationPlantSavedData();
        if (!tag.getBoolean("Present")) return data;
        try {
            BlockPos origin = BlockPos.of(tag.getLong("Origin"));
            long placedTick = Math.max(0L, tag.getLong("PlacedTick"));
            int phase = Math.max(0, tag.getInt("Phase"));
            long phaseStartedTick = tag.contains("PhaseStartedTick")
                    ? Math.max(0L, tag.getLong("PhaseStartedTick"))
                    : placedTick;
            data.placement = new Placement(
                    origin, placedTick, phase, phaseStartedTick, tag.getBoolean("RetestPressed"));
        } catch (IllegalArgumentException ignored) {
            data.placement = null;
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        Placement current = placement;
        tag.putBoolean("Present", current != null);
        if (current == null) return tag;
        tag.putLong("Origin", current.origin().asLong());
        tag.putLong("PlacedTick", current.placedTick());
        tag.putInt("Phase", current.phase());
        tag.putLong("PhaseStartedTick", current.phaseStartedTick());
        tag.putBoolean("RetestPressed", current.retestPressed());
        return tag;
    }

    public Placement placement() {
        return placement;
    }

    public Placement place(BlockPos origin, long tick) {
        placement = Placement.fresh(origin, tick);
        setDirty();
        return placement;
    }

    public Placement advancePhase(long tick) {
        if (placement == null) return null;
        placement = placement.advance(tick);
        setDirty();
        return placement;
    }

    public Placement resetForRetest(long tick, boolean pressed) {
        if (placement == null) return null;
        placement = placement.reset(tick, pressed);
        setDirty();
        return placement;
    }

    public Placement setRetestPressed(boolean pressed) {
        if (placement == null || placement.retestPressed() == pressed) return placement;
        placement = placement.withRetestPressed(pressed);
        setDirty();
        return placement;
    }
}
