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

public class CalibrationModuleBlock extends DirectionalSignalBlock {
    public static final IntegerProperty PROFILE = IntegerProperty.create("profile", 0, 4);

    public CalibrationModuleBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(PROFILE, 0));
    }

    @Override
    public MapCodec<CalibrationModuleBlock> codec() {
        return RedstoneEngineering.CALIBRATION_MODULE_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PROFILE);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int input = readBackInput(level, pos, state);
        updateOutput(level, pos, state, calibrate(input, state.getValue(PROFILE)));
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
            int profile = (state.getValue(PROFILE) + 1) % 5;
            BlockState next = state.setValue(PROFILE, profile);
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            level.scheduleTick(pos, this, 1);

            int input = readBackInput(level, pos, next);
            int output = calibrate(input, profile);

            player.displayClientMessage(
                    Component.literal(
                            "Calibration | " + profileName(profile)
                                    + " | IN=" + input + " → OUT=" + output
                    ),
                    true
            );
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static int calibrate(int input, int profile) {
        return switch (profile) {
            case 0 -> input;
            case 1 -> SignalMath.mapRange(input, 0, 7, 0, 15);
            case 2 -> SignalMath.mapRange(input, 4, 11, 0, 15);
            case 3 -> SignalMath.mapRange(input, 8, 15, 0, 15);
            case 4 -> 15 - input;
            default -> input;
        };
    }

    private static String profileName(int profile) {
        return switch (profile) {
            case 0 -> "FULL 0..15";
            case 1 -> "LOW 0..7→0..15";
            case 2 -> "MID 4..11→0..15";
            case 3 -> "HIGH 8..15→0..15";
            case 4 -> "INVERT";
            default -> "FULL";
        };
    }
}
