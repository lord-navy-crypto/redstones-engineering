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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Four-step finite-state sequence controller.
 * BACK=RUN/ENABLE, LEFT=ADVANCE edge, RIGHT=RESET, UP=HOLD, FRONT=step code 0..4.
 */
public class SequenceControllerBlock extends PassiveDirectionalSignalBlock implements OperationWorldResourceProvider {
    private static final String KEY = "sequence_controller";
    // [step, prevAdvance, prevRun, transitions, completedCycles, resets, holdTicks, advanceReacquire, runReacquire]
    private static final int ADVANCE_REACQUIRE = 7;
    private static final int RUN_REACQUIRE = 8;
    private static final int RUNTIME_SIZE = 9;

    public SequenceControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<SequenceControllerBlock> codec() {
        return EngineeringSystemsModule.SEQUENCE_CONTROLLER_CODEC.value();
    }

    @Override
    protected boolean isEngineeringPort(BlockState state, Direction side) {
        Direction front = outputSide(state);
        return side == inputSide(state)
                || side == leftOf(front)
                || side == rightOf(front)
                || side == Direction.UP
                || side == front;
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        Direction front = outputSide(state);
        return List.of(
                new EngineeringPort("RUN / ENABLE", inputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.CONTROL, PortDirection.INPUT, true, "enable"),
                new EngineeringPort("ADVANCE", leftOf(front), EngineeringDomain.REDSTONE,
                        PortKind.TRIGGER, PortDirection.INPUT, true, "advance"),
                new EngineeringPort("RESET", rightOf(front), EngineeringDomain.REDSTONE,
                        PortKind.RESET, PortDirection.INPUT, true, "reset"),
                new EngineeringPort("HOLD", Direction.UP, EngineeringDomain.REDSTONE,
                        PortKind.CONTROL, PortDirection.INPUT, true, "hold"),
                new EngineeringPort("STEP CODE", front, EngineeringDomain.REDSTONE,
                        PortKind.CONTROL, PortDirection.OUTPUT, true, "step")
        );
    }

    private record ControlEvidence(
            RedstoneObservationSupport.Observation run,
            RedstoneObservationSupport.Observation advance,
            RedstoneObservationSupport.Observation reset,
            RedstoneObservationSupport.Observation hold
    ) {}

    private ControlEvidence controlEvidence(Level level, BlockPos pos, BlockState state) {
        Direction front = outputSide(state);
        return new ControlEvidence(
                RedstoneObservationSupport.observe(level, pos, inputSide(state)),
                RedstoneObservationSupport.observe(level, pos, leftOf(front)),
                RedstoneObservationSupport.observe(level, pos, rightOf(front)),
                RedstoneObservationSupport.observe(level, pos, Direction.UP)
        );
    }

    private static boolean controlEvidenceUnusable(PortQuality quality) {
        return quality == PortQuality.STALE
                || quality == PortQuality.FAULT
                || quality == PortQuality.DOMAIN_MISMATCH
                || quality == PortQuality.TOPOLOGY_ERROR;
    }

    private static PortQuality controllerQuality(ControlEvidence evidence) {
        // RUN is the primary authority input, so missing RUN must remain visible as NO_SIGNAL.
        PortQuality quality = evidence.run().quality();
        for (RedstoneObservationSupport.Observation observation : List.of(
                evidence.advance(), evidence.reset(), evidence.hold())) {
            if (observation.quality() == PortQuality.NO_SIGNAL) continue;
            quality = RedstoneObservationSupport.combineQuality(quality, observation.quality());
        }
        return quality;
    }

    public static PortQuality runQuality(Level level, BlockPos pos, BlockState state) {
        return RedstoneObservationSupport.observe(
                level, pos, DirectionalSignalBlock.seriesInputSide(state)).quality();
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        ControlEvidence evidence = controlEvidence(level, pos, state);
        if (side == outputSide(state)) {
            return Optional.of(EngineeringPortSnapshot.redstone(
                    port.get(), state.getValue(OUTPUT), controllerQuality(evidence)));
        }
        RedstoneObservationSupport.Observation observed;
        Direction front = outputSide(state);
        if (side == inputSide(state)) observed = evidence.run();
        else if (side == leftOf(front)) observed = evidence.advance();
        else if (side == rightOf(front)) observed = evidence.reset();
        else if (side == Direction.UP) observed = evidence.hold();
        else return Optional.empty();
        return Optional.of(EngineeringPortSnapshot.redstone(
                port.get(), observed.value(), observed.quality()));
    }

    @Override
    public OperationWorldResourceSnapshot operationResourceSnapshot(Level level, BlockPos pos, BlockState state) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        int step = step(level, pos);
        int completedCycles = completedCycles(level, pos);
        int transitions = transitions(level, pos);
        PortQuality quality = runtime == null || runtime.length < RUNTIME_SIZE
                ? PortQuality.STALE
                : controllerQuality(controlEvidence(level, pos, state));
        return new OperationWorldResourceSnapshot(
                "sequence_controller:" + pos.asLong(),
                Set.of("sequence_control"),
                true,
                step > 0,
                true,
                false,
                quality,
                Map.of(
                        "sequence_step", (long) step,
                        "completed_cycles", (long) completedCycles,
                        "transitions", (long) transitions
                )
        );
    }

    @Override
    protected int computeOutput(Level level, BlockPos pos, BlockState state) {
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        ControlEvidence evidence = controlEvidence(level, pos, state);

        boolean runBad = controlEvidenceUnusable(evidence.run().quality());
        boolean advanceBad = controlEvidenceUnusable(evidence.advance().quality());
        boolean resetBad = controlEvidenceUnusable(evidence.reset().quality());
        boolean holdBad = controlEvidenceUnusable(evidence.hold().quality());

        int run = evidence.run().valid() ? evidence.run().value() : 0;
        int advance = evidence.advance().valid() ? evidence.advance().value() : 0;
        boolean resetActive = (evidence.reset().valid() && evidence.reset().value() > 0) || resetBad;
        boolean holdActive = (evidence.hold().valid() && evidence.hold().value() > 0) || holdBad;

        if (advanceBad) runtime[ADVANCE_REACQUIRE] = 1;
        if (runBad) runtime[RUN_REACQUIRE] = 1;

        if (resetActive) {
            if (runtime[0] != 0) {
                int oldStep = runtime[0];
                runtime[0] = 0;
                runtime[3]++;
                runtime[5]++;
                SystemEventTimeline.record(level, pos, SystemEventKind.SEQUENCE_RESET, 1,
                        "SEQUENCE_RESET",
                        (resetBad ? "RESET evidence unusable; fail-safe reset from step=" : "Reset forced sequence from step=")
                                + oldStep + " to IDLE");
            }
            if (!advanceBad) {
                runtime[1] = advance > 0 ? 1 : 0;
                runtime[ADVANCE_REACQUIRE] = 0;
            }
            if (runBad) {
                runtime[2] = 0;
            } else {
                runtime[2] = run > 0 ? 1 : 0;
                runtime[RUN_REACQUIRE] = 0;
            }
            return 0;
        }

        if (runBad || run <= 0) {
            if (runtime[0] != 0) {
                int oldStep = runtime[0];
                runtime[0] = 0;
                runtime[3]++;
                SystemEventTimeline.record(level, pos, SystemEventKind.SEQUENCE_RESET, 1,
                        "SEQUENCE_STOPPED",
                        (runBad ? "RUN evidence unusable at step=" : "RUN removed at step=")
                                + oldStep + "; sequence returned to IDLE");
            }
            if (!advanceBad) {
                runtime[1] = advance > 0 ? 1 : 0;
                runtime[ADVANCE_REACQUIRE] = 0;
            }
            runtime[2] = 0;
            if (!runBad) runtime[RUN_REACQUIRE] = 0;
            return 0;
        }

        if (runtime[RUN_REACQUIRE] != 0) {
            // Reacquire RUN state without manufacturing a start edge after a telemetry fault.
            runtime[2] = run > 0 ? 1 : 0;
            runtime[RUN_REACQUIRE] = 0;
            return 0;
        }

        if (runtime[2] == 0 && runtime[0] == 0) {
            runtime[0] = 1;
            runtime[3]++;
            SystemEventTimeline.record(level, pos, SystemEventKind.SEQUENCE_STARTED, 0,
                    "SEQUENCE_STARTED", "RUN rising edge entered STEP 1");
        }
        runtime[2] = 1;

        if (holdActive) {
            runtime[6]++;
            if (!advanceBad) {
                runtime[1] = advance > 0 ? 1 : 0;
                runtime[ADVANCE_REACQUIRE] = 0;
            }
            return runtime[0];
        }

        if (advanceBad) {
            return runtime[0];
        }
        if (runtime[ADVANCE_REACQUIRE] != 0) {
            // Reacquire ADVANCE level first so recovery cannot fabricate an edge.
            runtime[1] = advance > 0 ? 1 : 0;
            runtime[ADVANCE_REACQUIRE] = 0;
            return runtime[0];
        }

        boolean risingAdvance = advance > 0 && runtime[1] == 0;
        if (risingAdvance && runtime[0] > 0) {
            int oldStep = runtime[0];
            if (runtime[0] < 4) {
                runtime[0]++;
                SystemEventTimeline.record(level, pos, SystemEventKind.SEQUENCE_STEP, 0,
                        "SEQUENCE_STEP", "Advance edge moved step=" + oldStep + " -> " + runtime[0]);
            } else {
                runtime[0] = 0;
                runtime[4]++;
                SystemEventTimeline.record(level, pos, SystemEventKind.SEQUENCE_COMPLETED, 0,
                        "SEQUENCE_COMPLETED", "STEP 4 completed cycle=" + runtime[4]);
            }
            runtime[3]++;
        }
        runtime[1] = advance > 0 ? 1 : 0;
        return runtime[0];
    }

    public static int step(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length == 0 ? 0 : runtime[0];
    }

    public static int completedCycles(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length < 5 ? 0 : runtime[4];
    }

    public static int transitions(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length < 4 ? 0 : runtime[3];
    }

    /** Shared operator action for HMI and shift-use. Server state remains authoritative. */
    public boolean operatorReset(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(this)) return false;
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        int oldStep = runtime[0];
        runtime[0] = 0;
        runtime[1] = 0;
        runtime[2] = 0;
        runtime[ADVANCE_REACQUIRE] = 0;
        // Reacquire the physical RUN level before another start edge can be accepted.
        runtime[RUN_REACQUIRE] = 1;
        runtime[5]++;
        updateOutput(level, pos, state, 0);
        if (oldStep != 0) {
            SystemEventTimeline.record(level, pos, SystemEventKind.SEQUENCE_RESET, 1,
                    "SEQUENCE_OPERATOR_RESET", "Operator reset sequence from step=" + oldStep);
        }
        return true;
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                operatorReset(level, pos);
                player.displayClientMessage(Component.literal("Sequence reset | waiting for RUN rising edge"), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
