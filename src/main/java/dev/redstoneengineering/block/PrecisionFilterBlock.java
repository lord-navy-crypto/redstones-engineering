package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.signal.SignalMath;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

public class PrecisionFilterBlock extends DirectionalSignalBlock {
    public static final IntegerProperty RATE = IntegerProperty.create("rate", 1, 4);

    public PrecisionFilterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(RATE, 1));
    }

    @Override
    public MapCodec<PrecisionFilterBlock> codec() {
        return RedstoneEngineering.PRECISION_FILTER_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RATE);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int input = readBackInput(level, pos, state);
        int current = state.getValue(OUTPUT);
        int nextValue = SignalMath.approach(current, input, state.getValue(RATE));

        updateOutput(level, pos, state, nextValue);

        if (nextValue != input) {
            level.scheduleTick(pos, this, 1);
        }
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
            int rate = state.getValue(RATE);
            rate = rate >= 4 ? 1 : rate + 1;
            BlockState next = state.setValue(RATE, rate);
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            level.scheduleTick(pos, this, 1);

            player.displayClientMessage(
                    Component.literal(
                            "Precision Filter | slew=" + rate
                                    + " signal-step/tick | current=" + next.getValue(OUTPUT)
                    ),
                    true
            );
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
