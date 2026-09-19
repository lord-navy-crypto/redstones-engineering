package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.EngineeringSystemsModule;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.diagnostics.events.SystemEventKind;
import dev.redstoneengineering.diagnostics.events.SystemEventTimeline;
import dev.redstoneengineering.operations.world.OperationWorldResourceProvider;
import dev.redstoneengineering.operations.world.OperationWorldResourceSnapshot;
import dev.redstoneengineering.physics.RedstoneObservationSupport;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.ui.FieldDeviceUi;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Industrial alarm annunciator with latching, acknowledge and guarded reset semantics. */
public class AlarmProcessorBlock extends PassiveDirectionalSignalBlock implements OperationWorldResourceProvider {
    public static final IntegerProperty SEVERITY = IntegerProperty.create("severity", 1, 3);
    private static final String KEY = "alarm_processor";

    private static final int LATCHED = 0;
    private static final int UNACKNOWLEDGED = 1;
    private static final int ACTIVATION_COUNT = 2;
    private static final int ACK_COUNT = 3;
    private static final int CLEAR_COUNT = 4;
    private static final int PREVIOUS_CONDITION = 5;
    private static final int PREVIOUS_ACK = 6;
    private static final int PREVIOUS_RESET = 7;
    private static final int LATCHED_SEVERITY = 8;
    private static final int ACTIVE_TICKS = 9;
    private static final int CONDITION_REACQUIRE = 10;
    private static final int ACK_REACQUIRE = 11;
    private static final int RESET_REACQUIRE = 12;
    private static final int CONDITION_BAD_ACTIVE = 13;
    private static final int RUNTIME_SIZE = 14;

    private record AlarmEvidence(
            RedstoneObservationSupport.Observation condition,
            RedstoneObservationSupport.Observation acknowledge,
            RedstoneObservationSupport.Observation reset
    ) {}

    public AlarmProcessorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(SEVERITY, 2));
    }

    @Override
    public MapCodec<AlarmProcessorBlock> codec() {
        return EngineeringSystemsModule.ALARM_PROCESSOR_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SEVERITY);
    }

    @Override
    protected boolean isEngineeringPort(BlockState state, Direction side) {
        Direction front = outputSide(state);
        return side == inputSide(state) || side == leftOf(front) || side == rightOf(front) || side == front;
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        Direction front = outputSide(state);
        return List.of(
                new EngineeringPort("ALARM CONDITION", inputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.SAFETY, PortDirection.INPUT, true, "condition"),
                new EngineeringPort("ACKNOWLEDGE", leftOf(front), EngineeringDomain.REDSTONE,
                        PortKind.CONTROL, PortDirection.INPUT, true, "ack"),
                new EngineeringPort("RESET / CLEAR", rightOf(front), EngineeringDomain.REDSTONE,
                        PortKind.RESET, PortDirection.INPUT, true, "reset"),
                new EngineeringPort("ALARM OUT", front, EngineeringDomain.REDSTONE,
                        PortKind.SAFETY, PortDirection.OUTPUT, true, "alarm")
        );
    }

    private AlarmEvidence evidence(Level level, BlockPos pos, BlockState state) {
        Direction front = outputSide(state);
        return new AlarmEvidence(
                RedstoneObservationSupport.observe(level, pos, inputSide(state)),
                RedstoneObservationSupport.observe(level, pos, leftOf(front)),
                RedstoneObservationSupport.observe(level, pos, rightOf(front))
        );
    }

    private static boolean evidenceUnusable(PortQuality quality) {
        return quality == PortQuality.STALE
                || quality == PortQuality.FAULT
                || quality == PortQuality.DOMAIN_MISMATCH
                || quality == PortQuality.TOPOLOGY_ERROR;
    }

    private static boolean high(RedstoneObservationSupport.Observation observation) {
        return observation.valid() && observation.value() > 0;
    }

    private static boolean conditionClearForReset(RedstoneObservationSupport.Observation condition) {
        if (evidenceUnusable(condition.quality())) return false;
        if (condition.quality() == PortQuality.NO_SIGNAL) return true;
        return condition.valid() && condition.value() <= 0;
    }

    public static boolean resetPermitted(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof AlarmProcessorBlock alarm)) return false;
        return conditionClearForReset(alarm.evidence(level, pos, state).condition());
    }

    private static PortQuality derivedQuality(AlarmEvidence evidence, boolean alarmLatched) {
        PortQuality quality = evidence.condition().quality();
        if (evidenceUnusable(evidence.acknowledge().quality())) {
            quality = RedstoneObservationSupport.combineQuality(quality, evidence.acknowledge().quality());
        }
        if (evidenceUnusable(evidence.reset().quality())) {
            quality = RedstoneObservationSupport.combineQuality(quality, evidence.reset().quality());
        }
        if (alarmLatched) {
            quality = RedstoneObservationSupport.combineQuality(quality, PortQuality.FAULT);
        }
        return quality;
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();

        AlarmEvidence evidence = evidence(level, pos, state);
        Direction front = outputSide(state);
        if (side == front) {
            return Optional.of(EngineeringPortSnapshot.redstone(
                    port.get(), state.getValue(OUTPUT), derivedQuality(evidence, latched(level, pos))));
        }

        RedstoneObservationSupport.Observation observation;
        if (side == inputSide(state)) observation = evidence.condition();
        else if (side == leftOf(front)) observation = evidence.acknowledge();
        else if (side == rightOf(front)) observation = evidence.reset();
        else return Optional.empty();

        return Optional.of(EngineeringPortSnapshot.redstone(
                port.get(), observation.value(), observation.quality()));
    }

    @Override
    public OperationWorldResourceSnapshot operationResourceSnapshot(Level level, BlockPos pos, BlockState state) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        boolean alarmLatched = latched(level, pos);
        boolean alarmUnacknowledged = unacknowledged(level, pos);
        int severity = runtime == null || runtime.length <= LATCHED_SEVERITY || runtime[LATCHED_SEVERITY] == 0
                ? state.getValue(SEVERITY)
                : runtime[LATCHED_SEVERITY];
        PortQuality quality = runtime == null || runtime.length < RUNTIME_SIZE
                ? PortQuality.STALE
                : derivedQuality(evidence(level, pos, state), alarmLatched);
        return new OperationWorldResourceSnapshot(
                "alarm_processor:" + pos.asLong(),
                Set.of("alarm_processing"),
                !alarmLatched,
                false,
                false,
                alarmLatched,
                quality,
                Map.of(
                        "alarm_severity", (long) severity,
                        "unacknowledged", alarmUnacknowledged ? 1L : 0L,
                        "activation_count", (long) activationCount(level, pos)
                )
        );
    }

    private static boolean risingEdge(
            int[] runtime,
            int previousSlot,
            int reacquireSlot,
            RedstoneObservationSupport.Observation observation
    ) {
        if (evidenceUnusable(observation.quality())) {
            runtime[reacquireSlot] = 1;
            return false;
        }

        boolean nowHigh = high(observation);
        if (runtime[reacquireSlot] != 0) {
            runtime[previousSlot] = nowHigh ? 1 : 0;
            runtime[reacquireSlot] = 0;
            return false;
        }

        boolean rising = nowHigh && runtime[previousSlot] == 0;
        runtime[previousSlot] = nowHigh ? 1 : 0;
        return rising;
    }

    private static void activate(
            Level level,
            BlockPos pos,
            BlockState state,
            int[] runtime,
            String message
    ) {
        int severity = state.getValue(SEVERITY);
        runtime[LATCHED] = 1;
        runtime[UNACKNOWLEDGED] = 1;
        if (runtime[ACTIVATION_COUNT] < Integer.MAX_VALUE) runtime[ACTIVATION_COUNT]++;
        runtime[LATCHED_SEVERITY] = Math.max(runtime[LATCHED_SEVERITY], severity);
        SystemEventTimeline.record(
                level,
                pos,
                SystemEventKind.ALARM_RAISED,
                severity,
                "ALARM_RAISED",
                message
        );
    }

    @Override
    protected int computeOutput(Level level, BlockPos pos, BlockState state) {
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        AlarmEvidence evidence = evidence(level, pos, state);

        boolean conditionBad = evidenceUnusable(evidence.condition().quality());
        if (conditionBad) {
            runtime[CONDITION_REACQUIRE] = 1;
            if (runtime[CONDITION_BAD_ACTIVE] == 0) {
                runtime[CONDITION_BAD_ACTIVE] = 1;
                activate(
                        level,
                        pos,
                        state,
                        runtime,
                        "Alarm condition evidence unusable; fail-safe latch; quality=" + evidence.condition().quality()
                );
            }
        } else {
            runtime[CONDITION_BAD_ACTIVE] = 0;
            boolean conditionRising = risingEdge(
                    runtime, PREVIOUS_CONDITION, CONDITION_REACQUIRE, evidence.condition());
            if (conditionRising) {
                activate(
                        level,
                        pos,
                        state,
                        runtime,
                        "Alarm condition rose; severity=" + state.getValue(SEVERITY)
                );
            }
        }

        boolean ackRising = risingEdge(runtime, PREVIOUS_ACK, ACK_REACQUIRE, evidence.acknowledge());
        boolean resetRising = risingEdge(runtime, PREVIOUS_RESET, RESET_REACQUIRE, evidence.reset());

        if (runtime[LATCHED] != 0 && runtime[ACTIVE_TICKS] < Integer.MAX_VALUE) {
            runtime[ACTIVE_TICKS]++;
        }

        if (ackRising && runtime[LATCHED] != 0 && runtime[UNACKNOWLEDGED] != 0) {
            runtime[UNACKNOWLEDGED] = 0;
            if (runtime[ACK_COUNT] < Integer.MAX_VALUE) runtime[ACK_COUNT]++;
            SystemEventTimeline.record(
                    level,
                    pos,
                    SystemEventKind.ALARM_ACKNOWLEDGED,
                    1,
                    "ALARM_ACK",
                    "Latched alarm acknowledged by control input"
            );
        }

        if (resetRising && conditionClearForReset(evidence.condition()) && runtime[LATCHED] != 0) {
            int clearedSeverity = runtime[LATCHED_SEVERITY];
            runtime[LATCHED] = 0;
            runtime[UNACKNOWLEDGED] = 0;
            if (runtime[CLEAR_COUNT] < Integer.MAX_VALUE) runtime[CLEAR_COUNT]++;
            runtime[LATCHED_SEVERITY] = 0;
            SystemEventTimeline.record(
                    level,
                    pos,
                    SystemEventKind.ALARM_CLEARED,
                    0,
                    "ALARM_CLEARED",
                    "Healthy reset cleared severity=" + clearedSeverity + " alarm"
            );
        }

        if (runtime[LATCHED] == 0) return 0;
        int severity = runtime[LATCHED_SEVERITY] == 0
                ? state.getValue(SEVERITY)
                : runtime[LATCHED_SEVERITY];
        return severity == 1 ? 5 : severity == 2 ? 10 : 15;
    }

    public static boolean latched(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime != null && runtime.length > LATCHED && runtime[LATCHED] != 0;
    }

    public static boolean unacknowledged(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime != null && runtime.length > UNACKNOWLEDGED && runtime[UNACKNOWLEDGED] != 0;
    }

    public static int activationCount(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length <= ACTIVATION_COUNT ? 0 : runtime[ACTIVATION_COUNT];
    }

    public static String compactDiagnostics(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        if (runtime == null || runtime.length < RUNTIME_SIZE) return "ALARM idle | no event history";
        if (runtime[LATCHED] == 0) {
            return "ALARM CLEAR | activations=" + runtime[ACTIVATION_COUNT]
                    + " | clears=" + runtime[CLEAR_COUNT];
        }
        return "ALARM LATCHED | severity=" + runtime[LATCHED_SEVERITY]
                + " | " + (runtime[UNACKNOWLEDGED] != 0 ? "UNACKNOWLEDGED" : "ACKNOWLEDGED")
                + " | activeTicks=" + runtime[ACTIVE_TICKS];
    }

    public boolean adjustSeverity(Level level, BlockPos pos, int delta) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(this)) return false;
        int current = state.getValue(SEVERITY) - 1;
        int next = Math.floorMod(current + delta, 3) + 1;
        level.setBlock(pos, state.setValue(SEVERITY, next), Block.UPDATE_CLIENTS);
        return true;
    }

    public boolean acknowledge(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(this)) return false;
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        if (runtime[LATCHED] == 0 || runtime[UNACKNOWLEDGED] == 0) return true;
        runtime[UNACKNOWLEDGED] = 0;
        if (runtime[ACK_COUNT] < Integer.MAX_VALUE) runtime[ACK_COUNT]++;
        SystemEventTimeline.record(
                level,
                pos,
                SystemEventKind.ALARM_ACKNOWLEDGED,
                1,
                "ALARM_ACK",
                "Latched alarm acknowledged by operator"
        );
        return true;
    }

    public void cycleSeverity(Level level, BlockPos pos) {
        adjustSeverity(level, pos, 1);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server) server.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        updateOutput(level, pos, state, outputValue(level, pos, state));
        level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, KEY, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit
    ) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                acknowledge(level, pos);
                player.displayClientMessage(Component.literal(compactDiagnostics(level, pos)), true);
            } else {
                FieldDeviceUi.openUniversal(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
