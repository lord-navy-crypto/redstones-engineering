package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.metrology.MeasurementSnapshot;
import dev.redstoneengineering.metrology.MetrologyStore;
import dev.redstoneengineering.metrology.MetrologySupport;
import dev.redstoneengineering.physics.CircuitPhysics;
import dev.redstoneengineering.physics.DomainNetwork;
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

    @Override
    public MapCodec<CopperCircuitMeterBlock> codec() {
        return RedstoneEngineering.COPPER_CIRCUIT_METER_CODEC.value();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        if (!level.isClientSide && !state.is(oldState.getBlock())) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos target = pos.relative(state.getValue(FACING));
        int referenceVoltage = DomainNetwork.sampleCopperVoltage(level, target, pos);
        double reading = MetrologySupport.conditionRedstone(level, pos, referenceVoltage, SENSOR_PROFILE);
        MetrologySupport.sample(level, CHANNEL, pos, reading, referenceVoltage, false, 1.0, 30L);
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

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(new EngineeringPort(
                "MEASURE",
                state.getValue(FACING),
                EngineeringDomain.COPPER,
                PortKind.MEASUREMENT,
                PortDirection.INPUT,
                false,
                "V-eq"
        ));
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level,
            BlockPos pos,
            BlockState state,
            Direction side
    ) {
        Optional<EngineeringPort> descriptor = engineeringPort(state, side);
        if (descriptor.isEmpty()) return Optional.empty();
        MeasurementSnapshot m = measurement(level, pos);
        double value = m.sampleCount() > 0
                ? m.reading()
                : DomainNetwork.sampleCopperVoltage(level, pos.relative(side), pos);
        return Optional.of(new EngineeringPortSnapshot(
                descriptor.get(), value, 0.0, 15.0, MetrologySupport.portQuality(m)
        ));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockPos target = pos.relative(state.getValue(FACING));
            BlockState targetState = level.getBlockState(target);
            int voltage = DomainNetwork.sampleCopperVoltage(level, target, pos);
            double resistance = targetState.getBlock() instanceof CopperResistiveLoadBlock
                    ? targetState.getValue(CopperResistiveLoadBlock.RESISTANCE)
                    : CircuitPhysics.equivalentLoadResistance(level, target, 128);
            double current = CircuitPhysics.current(voltage, resistance);
            double power = voltage * current;
            player.displayClientMessage(Component.literal(String.format(
                    "Copper circuit meter | V=%.2f | Req=%.2f | I≈%.3f | P≈%.3f | %s",
                    (double) voltage,
                    resistance,
                    current,
                    power,
                    MetrologySupport.compactDiagnostics(measurement(level, pos))
            )), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
