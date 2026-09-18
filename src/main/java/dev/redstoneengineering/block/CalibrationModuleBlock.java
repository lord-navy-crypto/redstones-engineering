package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.core.signal.SignalMath;
import dev.redstoneengineering.metrology.MeasurementQuality;
import dev.redstoneengineering.metrology.MeasurementSnapshot;
import dev.redstoneengineering.metrology.MetrologySupport;
import dev.redstoneengineering.physics.RedstoneObservationSupport;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

import java.util.List;
import java.util.Optional;

/** Calibration processor with traceable OBSERVED, REFERENCE and CALIBRATED ports. */
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
        return referenceInputSide(state);
    }

    private static Direction referenceInputSide(BlockState state) {
        return leftOf(DirectionalSignalBlock.seriesOutputSide(state));
    }

    public static RedstoneObservationSupport.Observation observedEvidence(
            Level level, BlockPos pos, BlockState state
    ) {
        return RedstoneObservationSupport.observe(
                level, pos, DirectionalSignalBlock.seriesInputSide(state));
    }

    public static RedstoneObservationSupport.Observation referenceEvidence(
            Level level, BlockPos pos, BlockState state
    ) {
        return RedstoneObservationSupport.observe(level, pos, referenceInputSide(state));
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
        RedstoneObservationSupport.Observation observed = observedEvidence(level, pos, state);
        RedstoneObservationSupport.Observation reference = referenceEvidence(level, pos, state);

        // OBSERVED is the process path: bad evidence must never be reinterpreted as numerical zero.
        // REFERENCE remains independent calibration evidence rather than a hidden second control path.
        if (observed.valid()) {
            int corrected = calibrate(observed.value(), state.getValue(PROFILE));
            updateOutput(level, pos, state, corrected);
            if (reference.valid()) {
                MetrologySupport.sample(
                        level, CHANNEL, pos, corrected, reference.value(),
                        observed.quality() == PortQuality.SATURATED
                                || reference.quality() == PortQuality.SATURATED,
                        1.0, 30L);
            }
        }
    }

    public static MeasurementSnapshot measurement(Level level, BlockPos pos) {
        return MetrologySupport.snapshot(level, CHANNEL, pos, 1.0, 30L);
    }

    private static PortQuality traceabilityQuality(Level level, BlockPos pos) {
        MeasurementQuality quality = measurement(level, pos).quality();
        return switch (quality) {
            case GOOD, DEGRADED -> PortQuality.VALID;
            case SATURATED -> PortQuality.SATURATED;
            case STALE -> PortQuality.STALE;
            case INVALID -> PortQuality.NO_SIGNAL;
        };
    }

    public static PortQuality observedQuality(Level level, BlockPos pos, BlockState state) {
        return observedEvidence(level, pos, state).quality();
    }

    public static PortQuality referenceQuality(Level level, BlockPos pos, BlockState state) {
        return referenceEvidence(level, pos, state).quality();
    }

    public static PortQuality outputQuality(Level level, BlockPos pos, BlockState state) {
        return RedstoneObservationSupport.combineQuality(
                observedQuality(level, pos, state),
                referenceQuality(level, pos, state),
                traceabilityQuality(level, pos));
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();

        if (side == inputSide(state)) {
            RedstoneObservationSupport.Observation observed = observedEvidence(level, pos, state);
            return Optional.of(EngineeringPortSnapshot.redstone(
                    port.get(), observed.value(), observed.quality()));
        }
        if (side == referenceSide(state)) {
            RedstoneObservationSupport.Observation reference = referenceEvidence(level, pos, state);
            return Optional.of(EngineeringPortSnapshot.redstone(
                    port.get(), reference.value(), reference.quality()));
        }
        return Optional.of(EngineeringPortSnapshot.redstone(
                port.get(), state.getValue(OUTPUT), outputQuality(level, pos, state)));
    }

    public boolean adjustProfile(Level level, BlockPos pos, int delta) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(this)) return false;
        int profile = Math.floorMod(state.getValue(PROFILE) + delta, 5);
        BlockState next = state.setValue(PROFILE, profile);
        level.setBlock(pos, next, Block.UPDATE_CLIENTS);
        level.scheduleTick(pos, this, 1);
        return true;
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
                RedstoneObservationSupport.Observation observed = observedEvidence(level, pos, state);
                RedstoneObservationSupport.Observation reference = referenceEvidence(level, pos, state);
                int profile = state.getValue(PROFILE);
                int corrected = observed.valid()
                        ? calibrate(observed.value(), profile)
                        : state.getValue(OUTPUT);
                MeasurementSnapshot m = measurement(level, pos);
                player.displayClientMessage(Component.literal(
                        "Calibration | " + profileName(profile)
                                + " | OBSERVED=" + observed.value() + " " + observed.quality()
                                + " REF=" + reference.value() + " " + reference.quality()
                                + " → OUT=" + corrected + " " + outputQuality(level, pos, state)
                                + " | " + MetrologySupport.compactDiagnostics(m)
                ), true);
            } else {
                FieldDeviceUi.openUniversal(serverPlayer, pos);
            }
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

    public static String profileName(int profile) {
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