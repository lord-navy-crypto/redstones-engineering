package dev.redstoneengineering.diagnostics;

import dev.redstoneengineering.block.OperationsMonitorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Observer-only Industrial & Operations Engineering projection over the existing
 * Operations Monitor runtime. This class never owns simulation state, never
 * schedules work, and never writes the monitored process.
 *
 * <p>The vocabulary intentionally stays conservative. RSE does not report OEE
 * here because the current monitor does not own planned-production time,
 * good-count, reject-count, or an independently validated ideal cycle time.
 * Instead it exposes directly supported operations evidence: machine state,
 * throughput, queue/WIP pressure, cycle timing, downtime, starvation,
 * blocking/fault evidence and the dominant operational constraint.</p>
 */
public final class IndustrialOperationsAssessment {
    private IndustrialOperationsAssessment() {}

    /**
     * Evidence-backed machine operating state. MAINTENANCE is deliberately absent until RSE has
     * an explicit maintenance input/action; missing instrumentation is UNAVAILABLE, never IDLE.
     */
    public enum MachineState {
        UNAVAILABLE,
        IDLE,
        STARVED,
        RUNNING,
        BLOCKED,
        FAULTED
    }

    public enum Constraint {
        NONE,
        STARVED,
        BLOCKED,
        HIGH_WIP,
        UNSTABLE,
        SAFETY_LIMITED,
        FAILED
    }

    public record Snapshot(
            OperationsMonitorBlock.SystemState state,
            MachineState machineState,
            int throughputCyclesPerMinute,
            int lastCycleTicks,
            int queueNow,
            int queuePressurePercent,
            int downtimeTicks,
            int starvationEvidenceTicks,
            int blockedOrFaultEvidenceTicks,
            int highQueueRunTicks,
            Constraint dominantConstraint
    ) {
        public Snapshot {
            throughputCyclesPerMinute = Math.max(0, throughputCyclesPerMinute);
            lastCycleTicks = Math.max(0, lastCycleTicks);
            queueNow = clamp(queueNow, 0, 15);
            queuePressurePercent = clamp(queuePressurePercent, 0, 100);
            downtimeTicks = Math.max(0, downtimeTicks);
            starvationEvidenceTicks = Math.max(0, starvationEvidenceTicks);
            blockedOrFaultEvidenceTicks = Math.max(0, blockedOrFaultEvidenceTicks);
            highQueueRunTicks = Math.max(0, highQueueRunTicks);
        }

        public String compact() {
            return "IOE machine=" + machineState
                    + " system=" + state
                    + " | throughput=" + throughputCyclesPerMinute + " cycles/min"
                    + " | cycle=" + (lastCycleTicks > 0 ? lastCycleTicks + "t" : "—")
                    + " | queue=" + queueNow + "/15 (" + queuePressurePercent + "%)"
                    + " | constraint=" + dominantConstraint
                    + " | downtime=" + String.format(java.util.Locale.ROOT, "%.1f", downtimeTicks / 20.0) + "s"
                    + " | evidence starved/blocked/highWIP="
                    + starvationEvidenceTicks + "/" + blockedOrFaultEvidenceTicks + "/" + highQueueRunTicks;
        }
    }

    public static Snapshot inspect(Level level, BlockPos pos) {
        int stateOrdinal = OperationsMonitorBlock.stateOrdinal(level, pos);
        OperationsMonitorBlock.SystemState[] states = OperationsMonitorBlock.SystemState.values();
        OperationsMonitorBlock.SystemState state = states[clamp(stateOrdinal, 0, states.length - 1)];

        int throughput = OperationsMonitorBlock.throughputLastWindow(level, pos);
        int lastCycle = OperationsMonitorBlock.lastCycleTicks(level, pos);
        int queue = OperationsMonitorBlock.queueNow(level, pos);
        int downtime = OperationsMonitorBlock.downtimeTicks(level, pos);
        int starved = OperationsMonitorBlock.starvedTicks(level, pos);
        int blocked = OperationsMonitorBlock.blockedFaultTicks(level, pos);
        int highWip = OperationsMonitorBlock.highQueueRunTicks(level, pos);
        boolean ready = OperationsMonitorBlock.monitoringReady(level, pos);
        boolean running = OperationsMonitorBlock.running(level, pos);

        return new Snapshot(
                state,
                machineState(ready, running, queue, state),
                throughput,
                lastCycle,
                queue,
                Math.round(queue * 100.0F / 15.0F),
                downtime,
                starved,
                blocked,
                highWip,
                dominantConstraint(state, starved, blocked, highWip)
        );
    }

    static MachineState machineState(
            boolean ready,
            boolean running,
            int queue,
            OperationsMonitorBlock.SystemState systemState
    ) {
        if (!ready) return MachineState.UNAVAILABLE;
        if (systemState == OperationsMonitorBlock.SystemState.FAILED) return MachineState.FAULTED;
        int q = clamp(queue, 0, 15);
        if (running && q == 0) return MachineState.STARVED;
        if (running) return MachineState.RUNNING;
        if (q > 0) return MachineState.BLOCKED;
        return MachineState.IDLE;
    }

    static Constraint dominantConstraint(
            OperationsMonitorBlock.SystemState state,
            int starved,
            int blocked,
            int highWip
    ) {
        return switch (state) {
            case FAILED -> Constraint.FAILED;
            case SAFETY_LIMITED -> Constraint.SAFETY_LIMITED;
            case OVERLOADED, CONGESTED -> Constraint.HIGH_WIP;
            case NOISY, UNSTABLE -> Constraint.UNSTABLE;
            case NOMINAL -> {
                int s = Math.max(0, starved);
                int b = Math.max(0, blocked);
                int h = Math.max(0, highWip);
                if (s == 0 && b == 0 && h == 0) yield Constraint.NONE;
                if (s >= b && s >= h) yield Constraint.STARVED;
                if (b >= s && b >= h) yield Constraint.BLOCKED;
                yield Constraint.HIGH_WIP;
            }
        };
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}