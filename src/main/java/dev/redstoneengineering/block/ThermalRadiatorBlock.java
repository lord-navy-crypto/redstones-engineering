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
import dev.redstoneengineering.physics.ThermalPhysics;
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

/** Passive heat sink: removes heat above ambient, never actively refrigerates below ambient. */
public class ThermalRadiatorBlock extends DomainBlock implements EngineeringPortProvider {
    public static final IntegerProperty COOLING = IntegerProperty.create("cooling", 1, 4);

    public ThermalRadiatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(COOLING, 2));
    }

    @Override public MapCodec<ThermalRadiatorBlock> codec() { return RedstoneEngineering.THERMAL_RADIATOR_CODEC.value(); }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(COOLING); }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return Arrays.stream(Direction.values())
                .map(side -> new EngineeringPort(
                        "THERMAL SINK",
                        side,
                        EngineeringDomain.THERMAL,
                        PortKind.ACTUATOR,
                        PortDirection.INPUT,
                        false,
                        "T-index"
                ))
                .toList();
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        BlockState target = level.getBlockState(pos.relative(side));
        if (target.getBlock() instanceof ThermalMassBlock) {
            return Optional.of(new EngineeringPortSnapshot(
                    port.get(), target.getValue(ThermalMassBlock.TEMPERATURE), 0.0, 100.0, PortQuality.VALID));
        }
        return Optional.of(new EngineeringPortSnapshot(
                port.get(), ThermalPhysics.environmentTarget(level, pos), 0.0, 100.0, PortQuality.NO_SIGNAL));
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, movedByPiston);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int cooling = state.getValue(COOLING);
        for (Direction direction : Direction.values()) {
            BlockPos targetPos = pos.relative(direction);
            BlockState target = level.getBlockState(targetPos);
            if (target.getBlock() instanceof ThermalMassBlock) {
                int t = target.getValue(ThermalMassBlock.TEMPERATURE);
                if (t > ThermalPhysics.AMBIENT) {
                    int next = Math.max(ThermalPhysics.AMBIENT, t - cooling);
                    level.setBlock(targetPos, target.setValue(ThermalMassBlock.TEMPERATURE, next), Block.UPDATE_CLIENTS);
                }
            }
        }
        level.scheduleTick(pos, this, 10);
    }

    public record CoolingObservation(int adjacentMasses, int averageTemperature, int hottestTemperature) {}

    public static CoolingObservation observation(Level level, BlockPos pos) {
        int count = 0;
        int sum = 0;
        int hottest = ThermalPhysics.AMBIENT;
        for (Direction direction : Direction.values()) {
            BlockState target = level.getBlockState(pos.relative(direction));
            if (target.getBlock() instanceof ThermalMassBlock) {
                int t = target.getValue(ThermalMassBlock.TEMPERATURE);
                count++;
                sum += t;
                hottest = Math.max(hottest, t);
            }
        }
        return new CoolingObservation(count, count == 0 ? ThermalPhysics.AMBIENT : sum / count, hottest);
    }

    public boolean adjustCooling(Level level, BlockPos pos, int delta) {
        if (!(level instanceof ServerLevel)) return false;
        BlockState state = level.getBlockState(pos);
        if (!state.is(this)) return false;
        int nextCooling = 1 + Math.floorMod(state.getValue(COOLING) - 1 + delta, 4);
        level.setBlock(pos, state.setValue(COOLING, nextCooling), Block.UPDATE_CLIENTS);
        level.scheduleTick(pos, this, 1);
        return true;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                FieldDeviceUi.openUniversal(serverPlayer, pos);
                return InteractionResult.sidedSuccess(false);
            }
            adjustCooling(level, pos, 1);
            int cooling = level.getBlockState(pos).getValue(COOLING);
            CoolingObservation observation = observation(level, pos);
            player.displayClientMessage(Component.literal(
                    "Thermal radiator | cooling=" + cooling
                            + " | masses=" + observation.adjacentMasses()
                            + " | average T=" + observation.averageTemperature()
                            + " | hottest T=" + observation.hottestTemperature()
                            + " | ambient floor=" + ThermalPhysics.AMBIENT
                            + " | shift=Pioneer HMI"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
