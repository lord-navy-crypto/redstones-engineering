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

/** Lumped thermal mass: temperature is a physical state, not a wire signal. */
public class ThermalMassBlock extends DomainBlock implements EngineeringPortProvider {
    public static final IntegerProperty TEMPERATURE = IntegerProperty.create("temperature", 0, 100);
    public static final IntegerProperty HEAT_CAPACITY = IntegerProperty.create("heat_capacity", 1, 4);

    public ThermalMassBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(TEMPERATURE, ThermalPhysics.AMBIENT).setValue(HEAT_CAPACITY, 2));
    }

    @Override public MapCodec<ThermalMassBlock> codec() { return RedstoneEngineering.THERMAL_MASS_CODEC.value(); }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TEMPERATURE, HEAT_CAPACITY);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return Arrays.stream(Direction.values())
                .map(side -> new EngineeringPort(
                        "THERMAL BODY",
                        side,
                        EngineeringDomain.THERMAL,
                        PortKind.BUS,
                        PortDirection.BIDIRECTIONAL,
                        false,
                        "T-index"
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
        return port.map(value -> new EngineeringPortSnapshot(
                value,
                state.getValue(TEMPERATURE),
                0.0,
                100.0,
                PortQuality.VALID
        ));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) level.scheduleTick(pos, this, 5);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, movedByPiston);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int current = state.getValue(TEMPERATURE);
        int env = ThermalPhysics.environmentTarget(level, pos);
        int neighbors = ThermalPhysics.neighborThermalAverage(level, pos, current);
        int target = (env * 2 + neighbors) / 3;
        int capacity = state.getValue(HEAT_CAPACITY);
        int maxStep = Math.max(1, 5 - capacity);
        int next = ThermalPhysics.approach(current, target, maxStep);
        if (next != current) level.setBlock(pos, state.setValue(TEMPERATURE, next), Block.UPDATE_CLIENTS);
        level.scheduleTick(pos, this, 5 * capacity);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            if (!player.isShiftKeyDown()) {
                int capacity = state.getValue(HEAT_CAPACITY);
                capacity = capacity >= 4 ? 1 : capacity + 1;
                state = state.setValue(HEAT_CAPACITY, capacity);
                level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                level.scheduleTick(pos, this, 1);
            }
            player.displayClientMessage(Component.literal(
                    "Thermal mass | six-face THERMAL body | T-index=" + state.getValue(TEMPERATURE)
                            + "/100 | heat-capacity index=" + state.getValue(HEAT_CAPACITY)
            ), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
