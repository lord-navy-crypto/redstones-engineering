package dev.redstoneengineering.physics;

import dev.redstoneengineering.block.ThermalHeaterBlock;
import dev.redstoneengineering.block.ThermalMassBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

public final class ThermalPhysics {
    public static final int AMBIENT = 20;
    private ThermalPhysics() {}

    public static int environmentTarget(Level level, BlockPos pos) {
        int target = AMBIENT;
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            var state = level.getBlockState(n);
            if (state.getBlock() instanceof ThermalHeaterBlock) target = Math.max(target, state.getValue(ThermalHeaterBlock.TEMPERATURE));
            if (level.getFluidState(n).is(FluidTags.LAVA)) target = Math.max(target, 100);
            if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) target = Math.max(target, 95);
            if (state.is(Blocks.MAGMA_BLOCK)) target = Math.max(target, 75);
            if (state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)) target = Math.max(target, 70);
            if (state.is(Blocks.BLUE_ICE)) target = Math.min(target, 0);
            else if (state.is(Blocks.PACKED_ICE)) target = Math.min(target, 4);
            else if (state.is(Blocks.ICE) || state.is(Blocks.SNOW_BLOCK)) target = Math.min(target, 8);
        }
        return target;
    }

    public static int neighborThermalAverage(Level level, BlockPos pos, int fallback) {
        int sum = 0;
        int count = 0;
        for (Direction d : Direction.values()) {
            var s = level.getBlockState(pos.relative(d));
            if (s.getBlock() instanceof ThermalMassBlock) {
                sum += s.getValue(ThermalMassBlock.TEMPERATURE);
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
