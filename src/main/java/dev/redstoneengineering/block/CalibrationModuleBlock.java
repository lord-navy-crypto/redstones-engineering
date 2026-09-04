package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.signal.SignalMath;
import dev.redstoneengineering.metrology.MeasurementSnapshot;
import dev.redstoneengineering.metrology.MetrologySupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

import java.util.List;

/**
 * Calibration processor.
 * BACK = observed instrument signal, LEFT = known reference, FRONT = calibrated output.
 * The five historical transfer profiles remain available while Alpha 1.0.15 adds
 * live residual/uncertainty validation against the independent reference input.
 */
public class CalibrationModuleBlock extends DirectionalSignalBlock {
    public static final IntegerProperty PROFILE = IntegerProperty.create("profile", 0, 4);
    private static final String CHANNEL = "calibration_module";

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

    private Direction referenceSide(BlockState state) {
        return leftOf(outputSide(state));
    }

    @Override
    protected boolean isEngineeringPort(BlockState state, Direction side) {
        return super.isEngineeringPort(state, side) || side == referenceSide(state);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("OBSERVED", inputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.MEASUREMENT, PortDirection.INPUT, true, "signal"),
                new EngineeringPort("REFERENCE", referenceSide(state), EngineeringDomain.REDSTONE,
                        PortKind.MEASUREMENT, PortDirection.INPUT, true, "signal"),
                new EngineeringPort("CALIBRATED", outputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.REDSTONE_ANALOG, PortDirection.OUTPUT, true, "signal")
        );
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int observed = readBackInput(level, pos, state);
        int reference = readInputFrom(level, pos, referenceSide(state));
        int corrected = calibrate(observed, state.getValue(PROFILE));
        MetrologySupport.sample(level, CHANNEL, pos, corrected, reference, false, 1.0, 30L);
        updateOutput(level, pos, state, corrected);
    }

    public static MeasurementSnapshot measurement(Level level, BlockPos pos) {
        return MetrologySupport.snapshot(level, CHANNEL, pos, 1.0, 30L);
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

            int observed = readBackInput(level, pos, next);
            int reference = readInputFrom(level, pos, referenceSide(next));
            int corrected = calibrate(observed, profile);
            MeasurementSnapshot m = MetrologySupport.sample(
                    level, CHANNEL, pos, corrected, reference, false, 1.0, 30L
            );

            player.displayClientMessage(Component.literal(
                    "Calibration | " + profileName(profile)
                            + " | OBSERVED=" + observed
                            + " REF=" + reference
                            + " → OUT=" + corrected
                            + " | " + MetrologySupport.compactDiagnostics(m)
            ), true);
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
