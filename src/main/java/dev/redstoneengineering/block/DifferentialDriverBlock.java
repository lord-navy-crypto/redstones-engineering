package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.DifferentialNetwork;
import dev.redstoneengineering.physics.InformationRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Supporting source registration for recomputable differential-pair topology. */
public class DifferentialDriverBlock extends DirectionalDomainBlock {
    public DifferentialDriverBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<DifferentialDriverBlock> codec() {
        return RedstoneEngineering.DIFFERENTIAL_DRIVER_CODEC.value();
    }

    private void update(ServerLevel level, BlockPos pos, BlockState state) {
        int bit = level.getSignal(inputPos(pos, state), inputSide(state)) > 0 ? 1 : 0;
        InformationRuntime.write(level, "diff_out", pos, bit, 0, true, 100);
        BlockPos output = outputPos(pos, state);
        if (level.getBlockState(output).getBlock() instanceof DifferentialDataPairBlock) {
            DifferentialNetwork.recompute(level, output);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel serverLevel) update(serverLevel, pos, state);
    }

    @Override
    protected void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block neighbor,
            BlockPos neighborPos,
            boolean movedByPiston
    ) {
        if (level instanceof ServerLevel serverLevel) update(serverLevel, pos, state);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            InformationRuntime.clear(level, "diff_out", pos);
            BlockPos output = outputPos(pos, state);
            BlockState outputState = level.getBlockState(output);
            if (outputState.getBlock() instanceof DifferentialDataPairBlock pair) {
                serverLevel.scheduleTick(output, pair, 1);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
