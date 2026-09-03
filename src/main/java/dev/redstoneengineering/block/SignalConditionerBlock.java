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

public class SignalConditionerBlock extends DirectionalSignalBlock {
    public static final IntegerProperty MODE =
            IntegerProperty.create("mode", 0, 4);

    public static final IntegerProperty PARAM =
            IntegerProperty.create("param", 0, 15);

    public SignalConditionerBlock(Properties properties) {
        super(properties);
        registerDefaultState(
                defaultBlockState()
                        .setValue(MODE, 0)
                        .setValue(PARAM, 2)
        );
    }

    @Override
    public MapCodec<SignalConditionerBlock> codec() {
        return RedstoneEngineering.SIGNAL_CONDITIONER_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder
    ) {
        super.createBlockStateDefinition(builder);
        builder.add(MODE, PARAM);
    }

    @Override
    protected void tick(
            BlockState state,
            ServerLevel level,
            BlockPos pos,
            RandomSource random
    ) {
        int input = readBackInput(level, pos, state);

        int output = calculate(
                input,
                state.getValue(OUTPUT),
                state.getValue(MODE),
                state.getValue(PARAM)
        );

        updateOutput(level, pos, state, output);
    }

    private static int calculate(
            int input,
            int previousOutput,
            int mode,
            int param
    ) {
        return switch (mode) {
            case 0 -> SignalMath.gain(
                    input,
                    Math.max(1, Math.min(4, param))
            );
            case 1 -> SignalMath.offset(
                    input,
                    Math.min(10, param) - 5
            );
            case 2 -> Math.min(
                    input,
                    Math.max(1, param)
            );
            case 3 -> SignalMath.threshold(
                    input,
                    Math.max(1, param)
            );
            case 4 -> Math.abs(input - previousOutput)
                    >= Math.max(1, Math.min(4, param))
                    ? input
                    : previousOutput;
            default -> input;
        };
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
            BlockState next;

            if (player.isShiftKeyDown()) {
                next = state.setValue(
                        PARAM,
                        nextParam(
                                state.getValue(MODE),
                                state.getValue(PARAM)
                        )
                );
            } else {
                int mode = (state.getValue(MODE) + 1) % 5;

                next = state
                        .setValue(MODE, mode)
                        .setValue(PARAM, defaultParam(mode));
            }

            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            level.scheduleTick(pos, this, 1);

            int input = readBackInput(level, pos, next);
            int output = calculate(
                    input,
                    next.getValue(OUTPUT),
                    next.getValue(MODE),
                    next.getValue(PARAM)
            );

            player.displayClientMessage(
                    Component.literal(
                            "Conditioner | IN=" + input
                                    + " | " + modeName(next.getValue(MODE))
                                    + " " + parameterText(
                                            next.getValue(MODE),
                                            next.getValue(PARAM)
                                    )
                                    + " | OUT≈" + output
                                    + " | IN=" + inputSide(next).getName()
                                    + " OUT=" + outputSide(next).getName()
                    ),
                    true
            );
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static int defaultParam(int mode) {
        return switch (mode) {
            case 0 -> 2;
            case 1 -> 5;
            case 2 -> 10;
            case 3 -> 8;
            case 4 -> 2;
            default -> 1;
        };
    }

    private static int nextParam(int mode, int param) {
        return switch (mode) {
            case 0 -> param >= 4 ? 1 : Math.max(1, param + 1);
            case 1 -> param >= 10 ? 0 : param + 1;
            case 2, 3 -> param >= 15 ? 1 : Math.max(1, param + 1);
            case 4 -> param >= 4 ? 1 : Math.max(1, param + 1);
            default -> param;
        };
    }

    private static String modeName(int mode) {
        return switch (mode) {
            case 0 -> "GAIN";
            case 1 -> "OFFSET";
            case 2 -> "CLAMP";
            case 3 -> "THRESHOLD";
            case 4 -> "DEADBAND";
            default -> "UNKNOWN";
        };
    }

    private static String parameterText(int mode, int param) {
        return switch (mode) {
            case 0 -> "x" + Math.max(1, Math.min(4, param));
            case 1 -> "offset=" + (Math.min(10, param) - 5);
            case 2 -> "max=" + Math.max(1, param);
            case 3 -> "threshold=" + Math.max(1, param);
            case 4 -> "band=" + Math.max(1, Math.min(4, param));
            default -> "";
        };
    }
}
