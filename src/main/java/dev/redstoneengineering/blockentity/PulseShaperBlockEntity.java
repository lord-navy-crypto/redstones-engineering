package dev.redstoneengineering.blockentity;

import dev.redstoneengineering.RedstoneEngineering;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Persistent operator configuration and retained trigger evidence for the Pulse Shaper.
 *
 * Short-lived pulse timing intentionally remains outside this entity so save/reload never
 * resumes half of a transient pulse. The precise 1..15 threshold belongs here instead of
 * multiplying BlockState variants.
 */
public final class PulseShaperBlockEntity extends BlockEntity {
    private int threshold = 1;
    private int acceptedTriggerCount;
    private int suppressedTriggerCount;
    private long lastTriggerTick = -1L;

    public PulseShaperBlockEntity(BlockPos pos, BlockState state) {
        super(RedstoneEngineering.PULSE_SHAPER_BLOCK_ENTITY.get(), pos, state);
    }

    public int threshold() {
        return threshold;
    }

    public void setThreshold(int value) {
        int next = Math.max(1, Math.min(15, value));
        if (threshold == next) return;
        threshold = next;
        setChanged();
    }

    public int stepThreshold(boolean forward) {
        int next = forward ? (threshold >= 15 ? 1 : threshold + 1)
                : (threshold <= 1 ? 15 : threshold - 1);
        setThreshold(next);
        return threshold;
    }

    public void recordAcceptedTrigger(long gameTime) {
        if (acceptedTriggerCount < Integer.MAX_VALUE) acceptedTriggerCount++;
        lastTriggerTick = Math.max(0L, gameTime);
        setChanged();
    }

    public void recordSuppressedTrigger() {
        if (suppressedTriggerCount < Integer.MAX_VALUE) suppressedTriggerCount++;
        setChanged();
    }

    public int acceptedTriggerCount() {
        return acceptedTriggerCount;
    }

    public int suppressedTriggerCount() {
        return suppressedTriggerCount;
    }

    public long lastTriggerTick() {
        return lastTriggerTick;
    }

    public int lastTriggerAgeTicks(long gameTime) {
        if (lastTriggerTick < 0L) return -1;
        long age = Math.max(0L, gameTime - lastTriggerTick);
        return (int) Math.min(Integer.MAX_VALUE, age);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        threshold = Math.max(1, Math.min(15, tag.contains("threshold") ? tag.getInt("threshold") : 1));
        acceptedTriggerCount = Math.max(0, tag.getInt("acceptedTriggerCount"));
        suppressedTriggerCount = Math.max(0, tag.getInt("suppressedTriggerCount"));
        lastTriggerTick = tag.contains("lastTriggerTick") ? tag.getLong("lastTriggerTick") : -1L;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("threshold", threshold);
        tag.putInt("acceptedTriggerCount", acceptedTriggerCount);
        tag.putInt("suppressedTriggerCount", suppressedTriggerCount);
        tag.putLong("lastTriggerTick", lastTriggerTick);
    }
}
