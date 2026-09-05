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

public class TemperatureSensorBlock extends DomainBlock implements EngineeringPortProvider {
    public static final IntegerProperty TEMPERATURE = IntegerProperty.create("temperature", 0, 100);

    public TemperatureSensorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(TEMPERATURE, ThermalPhysics.AMBIENT));
    }

    @Override public MapCodec<TemperatureSensorBlock> codec() { return RedstoneEngineering.TEMPERATURE_SENSOR_CODEC.value(); }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TEMPERATURE);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return Arrays.stream(Direction.values())
                .map(side -> new EngineeringPort(
                        "THERMAL SENSE",
                        side,
                        EngineeringDomain.THERMAL,
                        PortKind.SENSOR,
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
                port.get(), state.getValue(TEMPERATURE), 0.0, 100.0, PortQuality.NO_SIGNAL));
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, movedByPiston);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int sum = 0;
        int count = 0;
        for (Direction direction : Direction.values()) {
            BlockState neighbor = level.getBlockState(pos.relative(direction));
            if (neighbor.getBlock() instanceof ThermalMassBlock) {
                sum += neighbor.getValue(ThermalMassBlock.TEMPERATURE);
                count++;
            }
        }
        int temperature = count > 0 ? sum / count : ThermalPhysics.environmentTarget(level, pos);
        if (temperature != state.getValue(TEMPERATURE)) {
            level.setBlock(pos, state.setValue(TEMPERATURE, temperature), Block.UPDATE_CLIENTS);
        }
        level.scheduleTick(pos, this, 10);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            player.displayClientMessage(Component.literal(
                    "Temperature sensor | six-face THERMAL observer | T-index=" + state.getValue(TEMPERATURE) + "/100 | physical state only"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
