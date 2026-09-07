package dev.redstoneengineering.physics;

import dev.redstoneengineering.block.ThermalHeaterBlock;
import dev.redstoneengineering.block.ThermalMassBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/** Bounded lumped thermal helpers used by the coarse RSE thermal domain. */
public final class ThermalPhysics {
    public static final int AMBIENT = 20;

    private ThermalPhysics() {}

    /**
     * Resolve the local environmental target without depending on Direction.values()
     * iteration order. Hot and cold boundary conditions are accumulated separately;
     * when both are present the reduced model uses their midpoint.
     */
    public static int environmentTarget(Level level, BlockPos pos) {
        int hottest = AMBIENT;
        int coldest = AMBIENT;
        boolean hasHot = false;
        boolean hasCold = false;

        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (!level.hasChunkAt(neighbor)) continue;
            var state = level.getBlockState(neighbor);

            int hot = AMBIENT;
            if (state.getBlock() instanceof ThermalHeaterBlock) {
                hot = Math.max(hot, state.getValue(ThermalHeaterBlock.TEMPERATURE));
            }
            if (level.getFluidState(neighbor).is(FluidTags.LAVA)) hot = Math.max(hot, 100);
            if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) hot = Math.max(hot, 95);
            if (state.is(Blocks.MAGMA_BLOCK)) hot = Math.max(hot, 75);
            if (state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)) hot = Math.max(hot, 70);
            if (hot > AMBIENT) {
                hasHot = true;
                hottest = Math.max(hottest, hot);
            }

            int cold = AMBIENT;
            if (state.is(Blocks.BLUE_ICE)) cold = Math.min(cold, 0);
            else if (state.is(Blocks.PACKED_ICE)) cold = Math.min(cold, 4);
            else if (state.is(Blocks.ICE) || state.is(Blocks.SNOW_BLOCK)) cold = Math.min(cold, 8);
            if (cold < AMBIENT) {
                hasCold = true;
                coldest = Math.min(coldest, cold);
            }
        }

        if (hasHot && hasCold) return (hottest + coldest) / 2;
        if (hasHot) return hottest;
        if (hasCold) return coldest;
        return AMBIENT;
    }

    public static int neighborThermalAverage(Level level, BlockPos pos, int fallback) {
        int sum = 0;
        int count = 0;
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (!level.hasChunkAt(neighbor)) continue;
            var state = level.getBlockState(neighbor);
            if (state.getBlock() instanceof ThermalMassBlock) {
                sum += state.getValue(ThermalMassBlock.TEMPERATURE);
                count++;
            }
        }
        return count == 0 ? fallback : sum / count;
    }

    public static int approach(int current, int target, int maxStep) {
        if (current == target) return current;
        int delta = target - current;
        int step = Math.min(Math.max(1, maxStep), Math.abs(delta));
        return current + Integer.signum(delta) * step;
    }
}
