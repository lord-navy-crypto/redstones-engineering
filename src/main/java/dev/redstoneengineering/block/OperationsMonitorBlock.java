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
import dev.redstoneengineering.diagnostics.IndustrialOperationsAssessment;
import dev.redstoneengineering.diagnostics.OperationsDashboardSnapshot;
import dev.redstoneengineering.diagnostics.events.FirstOutAnalysis;
import dev.redstoneengineering.diagnostics.events.SystemEventKind;
import dev.redstoneengineering.diagnostics.events.SystemEventTimeline;
import dev.redstoneengineering.physics.RedstoneObservationSupport;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.ui.OperationsMonitorUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Observer-only IOE monitor with explicit ports:
 * DOWN=machine running, UP=completed-cycle pulse, horizontal sides=QUEUE/WIP proxy (0..15).
 * Missing instrumentation never becomes a fabricated stopped machine or zero-queue observation.
 */
public class OperationsMonitorBlock extends Block implements EngineeringPortProvider {
    private static final String KEY = "ops_monitor";
    // Original 0..25 slots are retained. 26=cycle armed after known LOW, 27=cycle timing baseline valid.
    private static final int RUNTIME_SIZE = 28;
    private static final int WINDOW_TICKS = 1200;

    public enum SystemState { NOMINAL, CONGESTED, NOISY, UNSTABLE, OVERLOADED, SAFETY_LIMITED, FAILED }

    public record InputEvidence(
            RedstoneObservationSupport.Observation run,
            RedstoneObservationSupport.Observation cycle,
            int queueValue,
            int queueSources,
            boolean queueStale
    ) {
        public boolean queueValid() { return queueSources > 0; }
        public boolean operationalReady() { return run.valid() && queueValid(); }
        public PortQuality queueQuality() {
            if (queueValid()) return PortQuality.VALID;
            return queueStale ? PortQuality.STALE : PortQuality.NO_SIGNAL;
        }
    }

    public OperationsMonitorBlock(Properties p) { super(p); }
    @Override public MapCodec<OperationsMonitorBlock> codec() { return RedstoneEngineering.OPERATIONS_MONITOR_CODEC.value(); }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        List<EngineeringPort> ports = new ArrayList<>();
        ports.add(new EngineeringPort("MACHINE RUNNING", Direction.DOWN, EngineeringDomain.REDSTONE,
                PortKind.MEASUREMENT, PortDirection.INPUT, true, "run"));
        ports.add(new EngineeringPort("CYCLE PULSE", Direction.UP, EngineeringDomain.REDSTONE,
                PortKind.TRIGGER, PortDirection.INPUT, true, "cycle"));
        for (Direction side : Direction.Plane.HORIZONTAL) {
            ports.add(new EngineeringPort("QUEUE / WIP", side, EngineeringDomain.REDSTONE,
                    PortKind.MEASUREMENT, PortDirection.INPUT, true, "queue"));
        }
        return List.copyOf(ports);
    }

    private static RedstoneObservationSupport.Observation observe(Level level, BlockPos pos, Direction side) {
        return RedstoneObservationSupport.observe(level, pos, side);
    }

    public static InputEvidence inputEvidence(Level level, BlockPos pos) {
        RedstoneObservationSupport.Observation run = observe(level, pos, Direction.DOWN);
        RedstoneObservationSupport.Observation cycle = observe(level, pos, Direction.UP);
        int queue = 0;
        int queueSources = 0;
        boolean queueStale = false;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            RedstoneObservationSupport.Observation observation = observe(level, pos, side);
            if (observation.valid()) {
                queueSources++;
                queue = Math.max(queue, observation.value());
            } else if (observation.quality() == PortQuality.STALE) {
                queueStale = true;
            }
        }
        return new InputEvidence(run, cycle, queue, queueSources, queueStale);
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        RedstoneObservationSupport.Observation observation = observe(level, pos, side);
        return Optional.of(EngineeringPortSnapshot.redstone(
                port.get(), observation.value(), observation.quality()));
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return direction != null;
    }

    @Override
    protected void onPlace(BlockState s, Level l, BlockPos p, BlockState o, boolean m) {
        super.onPlace(s, l, p, o, m);
        if (l instanceof ServerLevel sl) sl.scheduleTick(p, this, 1);
    }

    private static void updateCycleEvidence(int[] r, InputEvidence evidence, int gameTime) {
        if (!evidence.operationalReady() || !evidence.cycle().valid()) {
            // Unknown coverage breaks edge chronology. Require a newly observed LOW before re-arming.
            r[1] = 0;
            r[26] = 0;
            r[27] = 0;
            return;
        }

        int cycle = evidence.cycle().value() > 0 ? 1 : 0;
        if (cycle == 0) {
            r[26] = 1;
            r[1] = 0;
            return;
        }

        if (r[1] == 0 && r[26] != 0) {
            r[2]++;
            if (r[27] != 0) {
                int ct = Math.max(1, gameTime - r[7]);
                r[8] = ct;
                r[9] = r[9] == 0 ? ct : (r[9] * 7 + ct) / 8;
                r[10] = Math.max(r[10], ct);
            }
            // The first trustworthy pulse establishes a baseline; only later pulses yield a cycle interval.
            r[7] = gameTime;
            r[27] = 1;
            r[26] = 0;
        }
        r[1] = 1;
    }

    @Override
    protected void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource rnd) {
        int[] r = RuntimeIntStore.get(l, KEY, p, RUNTIME_SIZE);
        InputEvidence evidence = inputEvidence(l, p);
        int gt = (int) Math.min(Integer.MAX_VALUE, l.getGameTime());

        updateCycleEvidence(r, evidence, gt);

        // A current queue reading is useful on its own, but it must not create operations KPIs
        // until MACHINE RUNNING and at least one QUEUE/WIP source are both trustworthy.
        if (!evidence.operationalReady()) {
            if (evidence.queueValid()) r[13] = evidence.queueValue();
            l.scheduleTick(p, this, 1);
            return;
        }

        int run = evidence.run().value() > 0 ? 1 : 0;
        int queue = evidence.queueValue();
        int previousQueue = r[13];

        if (r[3] > 0 && run != r[0]) r[24]++;
        if (r[3] > 0) r[23] += Math.abs(queue - previousQueue);
        if (run == 0) { r[11]++; if (r[0] == 1) r[12]++; r[18]++; r[19] = Math.max(r[19], r[18]); }
        else r[18] = 0;
        if (run == 1 && queue == 0) r[20]++;
        if (run == 0 && queue > 0) r[21]++;
        if (run == 1 && queue >= 10) r[22]++;

        int previousStateOrdinal = r[25];
        r[0] = run; r[3]++; if (run == 1) r[4]++;
        r[13] = queue; r[14] += queue; r[15] = Math.max(r[15], queue);
        SystemState nextState = classifySystemState(run, queue, r);
        r[25] = nextState.ordinal();
        if (previousStateOrdinal != r[25]) {
            SystemState previous = SystemState.values()[Math.max(0, Math.min(SystemState.values().length - 1, previousStateOrdinal))];
            SystemEventTimeline.record(l, p, SystemEventKind.OPERATIONS_STATE_CHANGED, severity(nextState),
                    "OPERATIONS_" + nextState,
                    "Operations state " + previous + " -> " + nextState + "; queue=" + queue + "; run=" + run);
        }

        if (r[3] >= WINDOW_TICKS) {
            r[5] = r[2]; r[6] = r[4]; r[16] = r[14] / Math.max(1, r[3]); r[17] = r[15];
            r[2] = 0; r[3] = 0; r[4] = 0; r[14] = 0; r[15] = 0; r[23] = 0; r[24] = 0;
        }
        l.scheduleTick(p, this, 1);
    }

    private static int severity(SystemState state) {
        return switch (state) {
            case FAILED -> 3;
            case SAFETY_LIMITED, OVERLOADED -> 2;
            case CONGESTED, NOISY, UNSTABLE -> 1;
            case NOMINAL -> 0;
        };
    }

    private static SystemState classifySystemState(int run, int queue, int[] r) {
        if (run == 0 && queue > 0 && r[18] >= 600) return SystemState.FAILED;
        if (run == 0 && queue > 0) return SystemState.SAFETY_LIMITED;
        if (run == 1 && queue >= 13) return SystemState.OVERLOADED;
        if (run == 1 && queue >= 9) return SystemState.CONGESTED;
        if (r[23] >= 120) return SystemState.NOISY;
        if (r[24] >= 12) return SystemState.UNSTABLE;
        return SystemState.NOMINAL;
    }

    private static int runtime(Level level, BlockPos pos, int index) {
        int[] r = RuntimeIntStore.peek(level, KEY, pos);
        return r == null || r.length <= index ? 0 : r[index];
    }
    public static boolean running(Level level, BlockPos pos) { return runtime(level,pos,0) != 0; }
    public static int cyclesCurrentWindow(Level level, BlockPos pos) { return runtime(level,pos,2); }
    public static int throughputLastWindow(Level level, BlockPos pos) { return runtime(level,pos,5); }
    public static int queueNow(Level level, BlockPos pos) { return runtime(level,pos,13); }
    public static int downtimeTicks(Level level, BlockPos pos) { return runtime(level,pos,11); }
    public static int stateOrdinal(Level level, BlockPos pos) { return runtime(level,pos,25); }
    public static int starvedTicks(Level level, BlockPos pos) { return runtime(level,pos,20); }
    public static int blockedFaultTicks(Level level, BlockPos pos) { return runtime(level,pos,21); }
    public static int highQueueRunTicks(Level level, BlockPos pos) { return runtime(level,pos,22); }
    public static int lastCycleTicks(Level level, BlockPos pos) { return runtime(level,pos,8); }
    public static boolean monitoringReady(Level level, BlockPos pos) { return inputEvidence(level,pos).operationalReady(); }
    public static int queueEvidenceSources(Level level, BlockPos pos) { return inputEvidence(level,pos).queueSources(); }
    public static OperationsDashboardSnapshot dashboard(Level level, BlockPos pos) { return OperationsDashboardSnapshot.inspect(level, pos); }

    /** Shared expert/UI summary retained as an observer-only projection of server runtime. */
    public static String compactDiagnostics(Level level, BlockPos pos) {
        int[] r = RuntimeIntStore.peek(level, KEY, pos);
        InputEvidence evidence = inputEvidence(level, pos);
        String evidenceText = evidence.operationalReady()
                ? "READY"
                : "INCOMPLETE(run=" + evidence.run().quality() + ",queueSources=" + evidence.queueSources() + ")";
        if (r == null || r.length < RUNTIME_SIZE) {
            return "Operations state=NOMINAL | evidence=" + evidenceText
                    + " | throughput last60s=0 cycles/min | downtime=0.0s | QUEUE now=0"
                    + " | IOE constraint=NONE queuePressure=0%"
                    + " | starved=0 blocked/fault=0 highQueueRun=0";
        }
        SystemState state = SystemState.values()[Math.max(0, Math.min(SystemState.values().length - 1, r[25]))];
        IndustrialOperationsAssessment.Snapshot ioe = IndustrialOperationsAssessment.inspect(level, pos);
        OperationsDashboardSnapshot dashboard = OperationsDashboardSnapshot.inspect(level, pos);
        return "Operations state=" + state
                + " | evidence=" + evidenceText
                + " | throughput last60s=" + r[5] + " cycles/min"
                + " | downtime=" + String.format(java.util.Locale.ROOT, "%.1f", r[11] / 20.0) + "s"
                + " | QUEUE now=" + r[13]
                + " cycle last/avg/max=" + r[8] + "/" + r[9] + "/" + r[10] + "t"
                + " | IOE constraint=" + ioe.dominantConstraint()
                + " queuePressure=" + ioe.queuePressurePercent() + "%"
                + " | starved=" + r[20]
                + " blocked/fault=" + r[21]
                + " highQueueRun=" + r[22]
                + " | events recent=" + dashboard.recentEvents()
                + " abnormal=" + dashboard.recentAbnormalEvents()
                + " | " + dashboard.firstOut().map(FirstOutAnalysis.Snapshot::compact).orElse("FIRST OUT: none");
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, KEY, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player pl, BlockHitResult h) {
        if (!l.isClientSide && pl instanceof ServerPlayer serverPlayer) {
            if (pl.isShiftKeyDown()) {
                RuntimeIntStore.remove(l, KEY, p);
                pl.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "Operations monitor statistics reset; plant event evidence retained"), true);
            } else {
                OperationsMonitorUi.open(serverPlayer, p);
            }
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
