package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class SignalTapBlock extends DirectionalSignalBlock {
    public SignalTapBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<SignalTapBlock> codec() {
        return RedstoneEngineering.SIGNAL_TAP_CODEC.value();
    }

    @Override
    protected boolean isEngineeringPort(BlockState state, Direction side) {
        Direction facing = state.getValue(FACING);
        return side == inputSide(state)
                || side == outputSide(state)
                || side == leftOf(facing);
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        Direction facing = state.getValue(FACING);
        if (direction == outputSide(state).getOpposite()
                || direction == leftOf(facing).getOpposite()) {
            return state.getValue(OUTPUT);
        }
        return 0;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        updateOutput(level, pos, state, readBackInput(level, pos, state));
        level.updateNeighborsAt(pos.relative(leftOf(state.getValue(FACING))), this);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (!level.isClientSide) {
            player.displayClientMessage(
                    Component.literal(
                            "Signal Tap | IN=" + inputSide(state).getName()
                                    + " | THROUGH=" + outputSide(state).getName()
                                    + " | TAP=" + leftOf(state.getValue(FACING)).getName()
                                    + " | value=" + state.getValue(OUTPUT) + "/15"
                    ),
                    true
            );
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
