package dev.redstoneengineering.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Alpha 1.0 convenience base for processors whose redstone output is derived
 * from runtime/domain state. DirectionalSignalBlock owns the 0..15 output
 * BlockState; this class keeps the scheduled update path consistent.
 */
public abstract class PassiveDirectionalSignalBlock extends DirectionalSignalBlock {
    protected PassiveDirectionalSignalBlock(Properties properties) {
        super(properties);
    }

    protected abstract int computeOutput(Level level, BlockPos pos, BlockState state);

    protected BlockPos inputPos(BlockPos pos, BlockState state) {
        return pos.relative(inputSide(state));
    }

    protected BlockPos outputPos(BlockPos pos, BlockState state) {
        return pos.relative(outputSide(state));
    }

    protected int outputValue(Level level, BlockPos pos, BlockState state) {
        return Math.max(0, Math.min(15, computeOutput(level, pos, state)));
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        updateOutput(level, pos, state, outputValue(level, pos, state));
    }
}
