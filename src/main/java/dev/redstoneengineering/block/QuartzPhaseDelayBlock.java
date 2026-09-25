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
import dev.redstoneengineering.physics.EngineeringDeviceParameters;
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

/**
 * Quartz timing-line rising-edge delay with a bounded in-flight event queue.
 *
 * <p>Every genuine post-initialization rising edge is delayed independently. Slow delay settings
 * therefore preserve a faster input clock instead of dropping edges while one event is pending.</p>
 */
public class QuartzPhaseDelayBlock extends DirectionalDomainBlock implements EngineeringPortProvider {
    public static final IntegerProperty DELAY = IntegerProperty.create("delay", 1, 8);
    public static final int MIN_DELAY_TICKS = 1;
    public static final int MAX_DELAY_TICKS = 32;
    public static final int PARAMETER_STEP_TICKS = 1;
    private static final String KEY = "quartz_phase_delay";
    private static final int PREVIOUS_SLOT = 0;
    private static final int OUTPUT_SLOT = 1;
    private static final int INITIALIZED_SLOT = 2;
    private static final int QUEUE_COUNT_SLOT = 3;
    private static final int DROPPED_EDGE_SLOT = 4;
    private static final int LAST_VALID_PERIOD_SLOT = 5;
    private static final int QUEUE_BASE = 6;
    public static final int QUEUE_CAPACITY = 8;
    private static final int RUNTIME_SIZE = QUEUE_BASE + QUEUE_CAPACITY;

    public QuartzPhaseDelayBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(DELAY, 2));
    }

    @Override public MapCodec<QuartzPhaseDelayBlock> codec() { return RedstoneEngineering.QUARTZ_PHASE_DELAY_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { super.createBlockStateDefinition(builder); builder.add(DELAY); }

    public static int configuredDelayTicks(Level level, BlockPos pos, BlockState state) {
        int fallback = state.getValue(DELAY);
        if (level instanceof ServerLevel serverLevel) {
            return Math.max(MIN_DELAY_TICKS, Math.min(MAX_DELAY_TICKS, EngineeringDeviceParameters.get(serverLevel)
                    .extendedParameters(serverLevel, pos,
                            new EngineeringDeviceParameters.ExtendedParameters(fallback, 0, 0, 0)).a()));
        }
        return fallback;
    }

    public static boolean setConfiguredDelayTicks(ServerLevel level, BlockPos pos, int ticks) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof QuartzPhaseDelayBlock)) return false;
        int bounded = Math.max(MIN_DELAY_TICKS, Math.min(MAX_DELAY_TICKS, ticks));
        boolean changed = EngineeringDeviceParameters.get(level).setExtendedParameters(
                level, pos, new EngineeringDeviceParameters.ExtendedParameters(bounded, 0, 0, 0));
        if (changed) level.scheduleTick(pos, state.getBlock(), 1);
        return changed;
    }

    public static int pendingTicks(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        if (runtime == null || runtime.length != RUNTIME_SIZE || runtime[QUEUE_COUNT_SLOT] <= 0) return 0;
        int min = Integer.MAX_VALUE;
        for (int i = 0; i < runtime[QUEUE_COUNT_SLOT]; i++) {
            min = Math.min(min, Math.max(0, runtime[QUEUE_BASE + i]));
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    public static int queuedEdges(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length != RUNTIME_SIZE
                ? 0 : Math.max(0, Math.min(QUEUE_CAPACITY, runtime[QUEUE_COUNT_SLOT]));
    }

    public static int droppedEdges(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length != RUNTIME_SIZE
                ? 0 : Math.max(0, runtime[DROPPED_EDGE_SLOT]);
    }

    private static void enqueue(int[] runtime, int delay) {
        int count = Math.max(0, Math.min(QUEUE_CAPACITY, runtime[QUEUE_COUNT_SLOT]));
        if (count >= QUEUE_CAPACITY) {
            if (runtime[DROPPED_EDGE_SLOT] < Integer.MAX_VALUE) runtime[DROPPED_EDGE_SLOT]++;
            return;
        }
        runtime[QUEUE_BASE + count] = Math.max(MIN_DELAY_TICKS, delay);
        runtime[QUEUE_COUNT_SLOT] = count + 1;
    }

    private static boolean advanceQueue(int[] runtime) {
        int count = Math.max(0, Math.min(QUEUE_CAPACITY, runtime[QUEUE_COUNT_SLOT]));
        if (count <= 0) return false;

        boolean emit = false;
        int write = 0;
        for (int i = 0; i < count; i++) {
            int remaining = Math.max(0, runtime[QUEUE_BASE + i] - 1);
            if (remaining <= 0) {
                emit = true;
                continue;
            }
            runtime[QUEUE_BASE + write++] = remaining;
        }
        for (int i = write; i < QUEUE_CAPACITY; i++) runtime[QUEUE_BASE + i] = 0;
        runtime[QUEUE_COUNT_SLOT] = write;
        return emit;
    }

    public static boolean initialized(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime != null && runtime.length == RUNTIME_SIZE && runtime[INITIALIZED_SLOT] == 1;
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("QUARTZ DELAY IN", inputSide(state), EngineeringDomain.QUARTZ,
                        PortKind.TRIGGER, PortDirection.INPUT, false, "ticks"),
                new EngineeringPort("QUARTZ DELAY OUT", outputSide(state), EngineeringDomain.QUARTZ,
                        PortKind.TRIGGER, PortDirection.OUTPUT, false, "ticks"));
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        BlockPos samplePos = side == inputSide(state) ? inputPos(pos, state) : outputPos(pos, state);
        DomainNetwork.QuartzSample sample = DomainNetwork.sampleQuartz(level, samplePos);
        PortQuality quality = level.getBlockState(samplePos).getBlock() instanceof QuartzTimingLineBlock
                ? QuartzTimingLineBlock.quality(level, samplePos)
                : (sample.valid() ? PortQuality.VALID : PortQuality.NO_SIGNAL);
        if (side == outputSide(state) && quality == PortQuality.NO_SIGNAL) {
            BlockPos upstreamPos = inputPos(pos, state);
            DomainNetwork.QuartzSample upstreamSample = DomainNetwork.sampleQuartz(level, upstreamPos);
            PortQuality upstreamQuality = level.getBlockState(upstreamPos).getBlock() instanceof QuartzTimingLineBlock
                    ? QuartzTimingLineBlock.quality(level, upstreamPos)
                    : (upstreamSample.valid() ? PortQuality.VALID : PortQuality.NO_SIGNAL);
            if (upstreamQuality == PortQuality.STALE || upstreamQuality == PortQuality.TOPOLOGY_ERROR) {
                quality = upstreamQuality;
            }
        }
        return Optional.of(new EngineeringPortSnapshot(port.get(), sample.periodTicks(), 0.0, 4096.0, quality));
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean movedByPiston) {
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
            if (level instanceof ServerLevel serverLevel) {
                EngineeringDeviceParameters.get(serverLevel).removeExtendedParameters(serverLevel, pos);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        DomainNetwork.QuartzSample input = DomainNetwork.sampleQuartz(level, inputPos(pos, state));
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);

        // Events that were captured while evidence was valid are already "inside" the delay line.
        // They continue to propagate even if the upstream source disappears afterwards.
        runtime[OUTPUT_SLOT] = advanceQueue(runtime) ? 1 : 0;

        if (input.valid()) {
            runtime[LAST_VALID_PERIOD_SLOT] = Math.max(1, input.periodTicks());
            if (runtime[INITIALIZED_SLOT] == 0) {
                runtime[PREVIOUS_SLOT] = input.active() ? 1 : 0;
                runtime[INITIALIZED_SLOT] = 1;
            } else {
                boolean rising = input.active() && runtime[PREVIOUS_SLOT] == 0;
                if (rising) {
                    enqueue(runtime, FaultInjectionModel.latencyTicks(configuredDelayTicks(level, pos, state), 32));
                }
                runtime[PREVIOUS_SLOT] = input.active() ? 1 : 0;
            }
        } else {
            // Reacquisition must establish a fresh phase baseline; never fabricate an edge.
            runtime[PREVIOUS_SLOT] = 0;
            runtime[INITIALIZED_SLOT] = 0;
        }

        boolean retainedEventEvidence = runtime[OUTPUT_SLOT] == 1 || runtime[QUEUE_COUNT_SLOT] > 0;
        boolean outputEvidenceValid = input.valid() || retainedEventEvidence;
        int outputPeriod = input.valid()
                ? Math.max(1, input.periodTicks())
                : Math.max(1, runtime[LAST_VALID_PERIOD_SLOT]);

        DomainNetwork.driveQuartz(
                level, outputPos(pos, state), pos,
                runtime[OUTPUT_SLOT] == 1,
                outputPeriod,
                outputEvidenceValid);
        level.scheduleTick(pos, this, 1);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            int delay = configuredDelayTicks(level, pos, state);
            delay = delay >= 32 ? 1 : delay + 1;
            if (level instanceof ServerLevel serverLevel) setConfiguredDelayTicks(serverLevel, pos, delay);
            player.displayClientMessage(Component.literal(
                    "Quartz phase delay | rising-edge delay=" + delay
                            + "t for NEW edges"
                            + " | queued=" + queuedEdges(level, pos)
                            + " next=" + pendingTicks(level, pos) + "t"
                            + " | dropped=" + droppedEdges(level, pos)
                            + " | in-flight edges retain their original delay"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
