package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.physics.CircuitPhysics;
import dev.redstoneengineering.physics.CopperNetworkSupport;
import dev.redstoneengineering.physics.EngineeringMath;
import dev.redstoneengineering.physics.MagneticPhysics;
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
import dev.redstoneengineering.ui.FieldDeviceUi;

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

    public static int targetTemperature(int voltage, int resistance) {
        double power = CircuitPhysics.power(voltage, resistance);
        return EngineeringMath.clamp(20 + (int) Math.round(power / 3.0), 20, 100);
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
        CopperNetworkSupport.TerminalInput input = CopperNetworkSupport.terminalInputOnSide(level, pos, side);
        return Optional.of(new EngineeringPortSnapshot(
                port.get(), input.voltage(), 0.0, 15.0, input.quality()
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
        CopperNetworkSupport.TerminalInput input = CopperNetworkSupport.terminalInput(level, pos);
        int voltage = input.quality() == dev.redstoneengineering.core.port.PortQuality.VALID ? input.voltage() : 0;
        int resistance = resistance(state);
        int target = targetTemperature(voltage, resistance);
        int current = state.getValue(TEMPERATURE);
        int next = EngineeringMath.approach(current, target, 3);
        BlockState updated = state.setValue(TEMPERATURE, next);
        if (updated != state) level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
        level.scheduleTick(pos, this, 2);
    }

    public boolean adjustResistance(Level level, BlockPos pos, int delta) {
        if (!(level instanceof ServerLevel serverLevel)) return false;
        BlockState state = level.getBlockState(pos);
        if (!state.is(this)) return false;
        int nextIndex = Math.floorMod(state.getValue(RESISTANCE_INDEX) + delta, R_VALUES.length);
        BlockState next = state.setValue(RESISTANCE_INDEX, nextIndex);
        level.setBlock(pos, next, Block.UPDATE_CLIENTS);
        level.scheduleTick(pos, this, 1);
        CopperNetworkSupport.recomputeAround(serverLevel, pos);
        return true;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                FieldDeviceUi.openUniversal(serverPlayer, pos);
                return InteractionResult.sidedSuccess(false);
            }
            adjustResistance(level, pos, 1);
            state = level.getBlockState(pos);
            CopperNetworkSupport.TerminalInput input = CopperNetworkSupport.terminalInput(level, pos);
            int voltage = input.voltage();
            int resistance = resistance(state);
            double current = CircuitPhysics.current(voltage, resistance);
            double power = CircuitPhysics.power(voltage, resistance);
            player.displayClientMessage(Component.literal(String.format(
                    "Thermal heater | V=%d | quality=%s | R=%d | I=%.2f | P=%.2f | T-index=%d/100 | shift=Pioneer HMI",
                    voltage, input.quality(), resistance, current, power, state.getValue(TEMPERATURE))), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
