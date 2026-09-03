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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Non-contact occupancy/count sensor: one signal level per nearby living entity, capped at 15. */
public class EntityDensitySensorBlock extends Block implements EngineeringPortProvider {
    public static final IntegerProperty POWER = IntegerProperty.create("power", 0, 15);

    public EntityDensitySensorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWER, 0));
    }

    @Override public MapCodec<EntityDensitySensorBlock> codec() { return RedstoneEngineering.ENTITY_DENSITY_SENSOR_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(POWER); }
    @Override protected boolean isSignalSource(BlockState state) { return true; }
    @Override protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return state.getValue(POWER); }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return Arrays.stream(Direction.values())
                .map(side -> new EngineeringPort("DENSITY_OUTPUT", side, EngineeringDomain.REDSTONE,
                        PortKind.SENSOR, PortDirection.OUTPUT, true, "signal"))
                .toList();
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        return engineeringPort(state, side)
                .map(port -> EngineeringPortSnapshot.redstone(port, state.getValue(POWER), PortQuality.VALID));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int count = Math.min(15, level.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(pos).inflate(4.0, 2.0, 4.0)
        ).size());
        if (count != state.getValue(POWER)) {
            level.setBlock(pos, state.setValue(POWER, count), Block.UPDATE_CLIENTS);
            level.updateNeighborsAt(pos, this);
        }
        level.scheduleTick(pos, this, 10);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) player.displayClientMessage(Component.literal("Entity Density Sensor = " + state.getValue(POWER) + " nearby"), true);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
