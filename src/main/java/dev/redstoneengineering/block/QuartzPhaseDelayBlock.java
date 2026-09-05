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
import dev.redstoneengineering.diagnostics.FaultInjectionModel;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.RuntimeIntStore;
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

import java.util.List;
import java.util.Optional;

/** Quartz timing-line edge delay that doubles as a bounded latency fault injector. */
public class QuartzPhaseDelayBlock extends DirectionalDomainBlock implements EngineeringPortProvider {
    public static final IntegerProperty DELAY = IntegerProperty.create("delay", 1, 8);
    private static final String KEY = "quartz_phase_delay";

    public QuartzPhaseDelayBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(DELAY, 2));
    }

    @Override public MapCodec<QuartzPhaseDelayBlock> codec() { return RedstoneEngineering.QUARTZ_PHASE_DELAY_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { super.createBlockStateDefinition(builder); builder.add(DELAY); }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("QUARTZ DELAY IN", inputSide(state), EngineeringDomain.QUARTZ,
                        PortKind.TRIGGER, PortDirection.INPUT, false, "ticks"),
                new EngineeringPort("QUARTZ DELAY OUT", outputSide(state), EngineeringDomain.QUARTZ,
                        PortKind.TRIGGER, PortDirection.OUTPUT, false, "ticks")
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        DomainNetwork.QuartzSample sample = side == inputSide(state)
                ? DomainNetwork.sampleQuartz(level, inputPos(pos, state))
                : DomainNetwork.sampleQuartz(level, outputPos(pos, state));
        return Optional.of(new EngineeringPortSnapshot(
                port.get(), sample.periodTicks(), 0.0, 4096.0,
                sample.valid() ? PortQuality.VALID : PortQuality.NO_SIGNAL));
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

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (level instanceof ServerLevel serverLevel) {
                DomainNetwork.driveQuartz(serverLevel, outputPos(pos, state), pos, false, 1, false);
                DomainNetwork.recomputeQuartzAround(serverLevel, pos);
            }
            RuntimeIntStore.remove(level, KEY, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        DomainNetwork.QuartzSample input = DomainNetwork.sampleQuartz(level, inputPos(pos, state));
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, 3); // pending, prev, out
        runtime[2] = 0;
        if (runtime[0] > 0) {
            runtime[0]--;
            if (runtime[0] == 0) runtime[2] = 1;
        }
        int injectedDelay = FaultInjectionModel.latencyTicks(state.getValue(DELAY), 8);
        if (input.valid() && input.active() && runtime[1] == 0 && runtime[0] == 0 && runtime[2] == 0) {
            runtime[0] = injectedDelay;
        }
        runtime[1] = input.active() ? 1 : 0;
        DomainNetwork.driveQuartz(
                level,
                outputPos(pos, state),
                pos,
                runtime[2] == 1,
                input.periodTicks(),
                input.valid()
        );
        level.scheduleTick(pos, this, 1);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            int delay = state.getValue(DELAY);
            delay = delay >= 8 ? 1 : delay + 1;
            BlockState next = state.setValue(DELAY, delay);
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            level.scheduleTick(pos, this, 1);
            player.displayClientMessage(Component.literal(
                    "Fault injection [LATENCY] | BACK QUARTZ in → FRONT QUARTZ out | rising-edge delay=" + delay + " ticks"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
