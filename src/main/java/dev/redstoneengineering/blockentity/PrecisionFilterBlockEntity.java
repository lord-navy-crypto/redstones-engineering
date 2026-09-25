package dev.redstoneengineering.blockentity;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.PrecisionFilterBlock;
import dev.redstoneengineering.signal.PrecisionFilterLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Persistent asymmetric fall-rate configuration for the Precision Filter. */
public final class PrecisionFilterBlockEntity extends BlockEntity {
    private int fallRate;

    public PrecisionFilterBlockEntity(BlockPos pos, BlockState state) {
        super(RedstoneEngineering.PRECISION_FILTER_BLOCK_ENTITY.get(), pos, state);
        fallRate = state.hasProperty(PrecisionFilterBlock.RATE)
                ? state.getValue(PrecisionFilterBlock.RATE)
                : 1;
    }

    public int fallRate() {
        return fallRate;
    }

    public void setFallRate(int value) {
        int next = PrecisionFilterLogic.boundedRate(value);
        if (fallRate == next) return;
        fallRate = next;
        setChanged();
    }

    public int stepFallRate(boolean forward) {
        int next = forward
                ? (fallRate >= PrecisionFilterLogic.MAX_RATE ? PrecisionFilterLogic.MIN_RATE : fallRate + 1)
                : (fallRate <= PrecisionFilterLogic.MIN_RATE ? PrecisionFilterLogic.MAX_RATE : fallRate - 1);
        setFallRate(next);
        return fallRate;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("fallRate")) {
            fallRate = PrecisionFilterLogic.boundedRate(tag.getInt("fallRate"));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("fallRate", fallRate);
    }
}
