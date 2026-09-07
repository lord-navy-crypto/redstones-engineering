package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.metrology.MeasurementSnapshot;
import dev.redstoneengineering.metrology.MetrologyStore;
import dev.redstoneengineering.metrology.MetrologySupport;
import dev.redstoneengineering.physics.CircuitPhysics;
import dev.redstoneengineering.physics.CopperObservationSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/** Non-invasive copper-domain meter with scheduled Alpha 1.0.15 metrology sampling. */
public class CopperCircuitMeterBlock extends DomainBlock implements EngineeringPortProvider {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static final String CHANNEL = "copper_circuit_meter";
    private static final int SENSOR_PROFILE = 2; // PRECISION
    private static final int SAMPLE_PERIOD = 10;

    public CopperCircuitMeterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override public MapCodec<CopperCircuitMeterBlock> codec() { return RedstoneEngineering.COPPER_CIRCUIT_METER_CODEC.value(); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }

    public static CopperObservationSupport.Observation targetObservation(Level level, BlockPos pos, BlockState state) {
        BlockPos target = pos.relative(state.getValue(FACING));
        return CopperObservationSupport.measure(level, target, pos);
    }

    public static int sampledVoltage(Level level, BlockPos pos, BlockState state) {
        return targetObservation(level, pos, state).voltage();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide && !state.is(oldState.getBlock())) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean moved) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, moved);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        CopperObservationSupport.Observation target = targetObservation(level, pos, state);
        // A topology/no-source condition is not fabricated into a valid instrument sample.
        if (target.quality() == PortQuality.VALID) {
            double reading = MetrologySupport.conditionRedstone(level, pos, target.voltage(), SENSOR_PROFILE);
            MetrologySupport.sample(level, CHANNEL, pos, reading, target.voltage(), false, 1.0, 30L);
        }
        level.scheduleTick(pos, this, SAMPLE_PERIOD);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) MetrologyStore.remove(level, CHANNEL, pos);
        super.onRemove(state, level, pos, newState, moved);
    }

    public static MeasurementSnapshot measurement(Level level, BlockPos pos) {
        return MetrologySupport.snapshot(level, CHANNEL, pos, 1.0, 30L);
    }

    public static PortQuality measurementQuality(Level level, BlockPos pos, BlockState state) {
        CopperObservationSupport.Observation target = targetObservation(level, pos, state);
        MeasurementSnapshot measurement = measurement(level, pos);
        if (target.quality() == PortQuality.TOPOLOGY_ERROR
                || target.quality() == PortQuality.FAULT
                || target.quality() == PortQuality.DOMAIN_MISMATCH) {
            return target.quality();
        }
        if (measurement.sampleCount() == 0) {
            return target.quality() == PortQuality.VALID ? PortQuality.STALE : target.quality();
        }
        if (target.quality() != PortQuality.VALID) {
            // Retained measurement exists, but it no longer describes a live connected target.
            return PortQuality.STALE;
        }
        return MetrologySupport.portQuality(measurement);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(new EngineeringPort(
                "MEASURE", state.getValue(FACING), EngineeringDomain.COPPER,
                PortKind.MEASUREMENT, PortDirection.INPUT, false, "V-eq"));
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> descriptor = engineeringPort(state, side);
        if (descriptor.isEmpty()) return Optional.empty();
        MeasurementSnapshot measurement = measurement(level, pos);
        CopperObservationSupport.Observation target = targetObservation(level, pos, state);
        double value = measurement.sampleCount() > 0 ? measurement.reading() : target.voltage();
        return Optional.of(new EngineeringPortSnapshot(
                descriptor.get(), value, 0.0, 15.0, measurementQuality(level, pos, state)
        ));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockPos targetPos = pos.relative(state.getValue(FACING));
            BlockState targetState = level.getBlockState(targetPos);
            CopperObservationSupport.Observation target = targetObservation(level, pos, state);
            double resistance = targetState.getBlock() instanceof CopperResistiveLoadBlock
                    ? targetState.getValue(CopperResistiveLoadBlock.RESISTANCE)
                    : CircuitPhysics.equivalentLoadResistance(level, targetPos, 128);
            double current = CircuitPhysics.current(target.voltage(), resistance);
            double power = target.voltage() * current;
            player.displayClientMessage(Component.literal(String.format(
                    "Copper circuit meter | observer-only | live=%s V=%.2f | Req=%.2f | I≈%.3f | P≈%.3f | meter=%s | %s",
                    target.quality(),
                    (double) target.voltage(),
                    resistance,
                    current,
                    power,
                    measurementQuality(level, pos, state),
                    MetrologySupport.compactDiagnostics(measurement(level, pos))
            )), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
