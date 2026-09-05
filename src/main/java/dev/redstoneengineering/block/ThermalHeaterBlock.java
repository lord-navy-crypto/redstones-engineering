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
import dev.redstoneengineering.physics.CircuitPhysics;
import dev.redstoneengineering.physics.CopperNetworkSupport;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.EngineeringMath;
import dev.redstoneengineering.physics.MagneticPhysics;
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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Electrical-to-thermal transducer. Uses a reduced macroscopic P=V^2/R model. */
public class ThermalHeaterBlock extends DomainBlock implements EngineeringPortProvider {
    public static final IntegerProperty RESISTANCE_INDEX = IntegerProperty.create("resistance", 0, 3);
    public static final IntegerProperty TEMPERATURE = IntegerProperty.create("temperature", 0, 100);
    private static final int[] R_VALUES = {1, 2, 4, 8};

    public ThermalHeaterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(RESISTANCE_INDEX, 1).setValue(TEMPERATURE, 20));
    }

    @Override public MapCodec<ThermalHeaterBlock> codec() { return RedstoneEngineering.THERMAL_HEATER_CODEC.value(); }

    public static int resistance(BlockState state) {
        return R_VALUES[state.getValue(RESISTANCE_INDEX)];
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RESISTANCE_INDEX, TEMPERATURE);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return Arrays.stream(Direction.values())
                .map(side -> new EngineeringPort(
                        "COPPER POWER IN",
                        side,
                        EngineeringDomain.COPPER,
                        PortKind.CONVERTER,
                        PortDirection.INPUT,
                        false,
                        "V-eq"
                ))
                .toList();
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level,
            BlockPos pos,
            BlockState state,
            Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        int voltage = DomainNetwork.sampleCopperVoltage(level, pos.relative(side), pos);
        return Optional.of(new EngineeringPortSnapshot(
                port.get(), voltage, 0.0, 15.0,
                voltage > 0 ? PortQuality.VALID : PortQuality.NO_SIGNAL
        ));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) level.scheduleTick(pos, this, 2);
        if (level instanceof ServerLevel serverLevel) CopperNetworkSupport.recomputeAround(serverLevel, pos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, movedByPiston);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            CopperNetworkSupport.recomputeAround(serverLevel, pos);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int voltage = MagneticPhysics.adjacentCopperLevel(level, pos);
        int resistance = resistance(state);
        double power = CircuitPhysics.power(voltage, resistance);
        int target = EngineeringMath.clamp(20 + (int) Math.round(power / 3.0), 20, 100);
        int current = state.getValue(TEMPERATURE);
        int next = EngineeringMath.approach(current, target, 3);
        BlockState updated = state.setValue(TEMPERATURE, next);
        if (updated != state) level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
        level.scheduleTick(pos, this, 2);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            if (!player.isShiftKeyDown()) {
                int next = (state.getValue(RESISTANCE_INDEX) + 1) % R_VALUES.length;
                state = state.setValue(RESISTANCE_INDEX, next);
                level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                level.scheduleTick(pos, this, 1);
                if (level instanceof ServerLevel serverLevel) CopperNetworkSupport.recomputeAround(serverLevel, pos);
            }
            int voltage = MagneticPhysics.adjacentCopperLevel(level, pos);
            int resistance = resistance(state);
            double current = CircuitPhysics.current(voltage, resistance);
            double power = CircuitPhysics.power(voltage, resistance);
            player.displayClientMessage(Component.literal(String.format(
                    "Thermal heater | COPPER terminal converter | V=%d | R=%d | I=%.2f | P=%.2f | T-index=%d/100",
                    voltage, resistance, current, power, state.getValue(TEMPERATURE))), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
