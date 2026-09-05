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
import dev.redstoneengineering.physics.DomainNetwork;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

public class QuartzLabOscillatorBlock extends DomainBlock implements EngineeringPortProvider {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final IntegerProperty PERIOD_INDEX = IntegerProperty.create("period", 0, 4);
    public static final IntegerProperty JITTER = IntegerProperty.create("jitter", 0, 3);

    public QuartzLabOscillatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ACTIVE, false).setValue(PERIOD_INDEX, 2).setValue(JITTER, 1));
    }

    @Override public MapCodec<QuartzLabOscillatorBlock> codec() { return RedstoneEngineering.QUARTZ_LAB_OSCILLATOR_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(ACTIVE, PERIOD_INDEX, JITTER); }

    private static EngineeringPort port(Direction side) {
        return new EngineeringPort(
                "QUARTZ LAB CLOCK OUT",
                side,
                EngineeringDomain.QUARTZ,
                PortKind.TRIGGER,
                PortDirection.OUTPUT,
                false,
                "ticks"
        );
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(port(Direction.NORTH), port(Direction.SOUTH), port(Direction.WEST), port(Direction.EAST));
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        return engineeringPort(state, side).map(port -> new EngineeringPortSnapshot(
                port,
                QuartzTimingLineBlock.periodTicks(state.getValue(PERIOD_INDEX)),
                1.0,
                4096.0,
                PortQuality.VALID
        ));
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (level instanceof ServerLevel serverLevel && !state.is(newState.getBlock())) {
            DomainNetwork.recomputeQuartzAround(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState next = state.setValue(ACTIVE, !state.getValue(ACTIVE));
        level.setBlock(pos, next, Block.UPDATE_CLIENTS);
        DomainNetwork.recomputeQuartz(level, pos);
        int half = Math.max(1, QuartzTimingLineBlock.periodTicks(next.getValue(PERIOD_INDEX)) / 2);
        int jitter = next.getValue(JITTER);
        int offset = jitter == 0 ? 0 : random.nextInt(jitter * 2 + 1) - jitter;
        level.scheduleTick(pos, this, Math.max(1, half + offset));
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockState next;
            if (player.isShiftKeyDown()) {
                int jitter = state.getValue(JITTER);
                next = state.setValue(JITTER, jitter >= 3 ? 0 : jitter + 1);
            } else {
                int periodIndex = state.getValue(PERIOD_INDEX);
                next = state.setValue(PERIOD_INDEX, (periodIndex + 1) % 5);
            }
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            if (level instanceof ServerLevel serverLevel) DomainNetwork.recomputeQuartz(serverLevel, pos);
            level.scheduleTick(pos, this, 1);
            player.displayClientMessage(Component.literal(
                    "Quartz lab oscillator | four-way QUARTZ clock | nominal="
                            + QuartzTimingLineBlock.periodTicks(next.getValue(PERIOD_INDEX))
                            + "t | jitter=±" + next.getValue(JITTER) + "t"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
