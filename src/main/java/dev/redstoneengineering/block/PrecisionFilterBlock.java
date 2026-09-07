package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.signal.SignalMath;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Dynamic slew filter. It intentionally owns response speed, not static gain/offset or
 * reference calibration, so a temporary input/output lag is expected engineering behavior.
 */
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
        if (nextValue != input) level.scheduleTick(pos, this, 1);
    }

    public static int input(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof PrecisionFilterBlock filter)) return 0;
        return filter.readBackInput(level, pos, state);
    }

    /** Absolute distance still to travel before the bounded slew response reaches the input. */
    public static int lag(Level level, BlockPos pos, BlockState state) {
        return Math.abs(input(level, pos, state) - state.getValue(OUTPUT));
    }

    public static boolean settled(Level level, BlockPos pos, BlockState state) {
        return lag(level, pos, state) == 0;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                int rate = state.getValue(RATE);
                rate = rate >= 4 ? 1 : rate + 1;
                BlockState next = state.setValue(RATE, rate);
                level.setBlock(pos, next, Block.UPDATE_CLIENTS);
                level.scheduleTick(pos, this, 1);
                int in = input(level, pos, next);
                int out = next.getValue(OUTPUT);
                int lag = Math.abs(in - out);
                player.displayClientMessage(
                        Component.literal(
                                "Precision Filter | slew=" + rate
                                        + " signal-step/tick | IN=" + in
                                        + " OUT=" + out
                                        + " | lag=" + lag
                                        + " | " + (lag == 0 ? "SETTLED" : "SETTLING")
                        ),
                        true
                );
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
