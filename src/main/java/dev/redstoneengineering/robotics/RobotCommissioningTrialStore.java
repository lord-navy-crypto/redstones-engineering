package dev.redstoneengineering.robotics;

import net.minecraft.world.level.Level;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Bounded transient baseline/candidate storage for AMR mission commissioning.
 *
 * Read-only observation never changes retention priority. Starting a new baseline clears the prior
 * candidate. Candidate capture is rejected until a baseline exists for that robot.
 */
public final class RobotCommissioningTrialStore {
    public static final int MAX_ROBOTS_PER_LEVEL = 256;

    private static final Map<Level, LinkedHashMap<UUID, TrialState>> DATA = new WeakHashMap<>();

    private RobotCommissioningTrialStore() {}

    public static synchronized RobotCommissioningTrialRecord captureBaseline(
            Level level,
            UUID robotId,
            long gameTick,
            RobotMissionTelemetrySnapshot telemetry
    ) {
        if (telemetry == null || !telemetry.finished()) return null;
        TrialState state = state(level, robotId, true);
        RobotCommissioningTrialRecord record = new RobotCommissioningTrialRecord(
                state.nextSequence++, gameTick, robotId, telemetry);
        state.baseline = record;
        state.candidate = null;
        return record;
    }

    public static synchronized Optional<RobotCommissioningTrialRecord> captureCandidate(
            Level level,
            UUID robotId,
            long gameTick,
            RobotMissionTelemetrySnapshot telemetry
    ) {
        if (telemetry == null || !telemetry.finished()) return Optional.empty();
        TrialState state = state(level, robotId, false);
        if (state == null || state.baseline == null) return Optional.empty();
        RobotCommissioningTrialRecord record = new RobotCommissioningTrialRecord(
                state.nextSequence++, gameTick, robotId, telemetry);
        state.candidate = record;
        return Optional.of(record);
    }

    public static synchronized Optional<RobotCommissioningTrialRecord> baseline(Level level, UUID robotId) {
        TrialState state = state(level, robotId, false);
        return state == null ? Optional.empty() : Optional.ofNullable(state.baseline);
    }

    public static synchronized Optional<RobotCommissioningTrialRecord> candidate(Level level, UUID robotId) {
        TrialState state = state(level, robotId, false);
        return state == null ? Optional.empty() : Optional.ofNullable(state.candidate);
    }

    public static synchronized Optional<RobotCommissioningTrialComparison> comparison(Level level, UUID robotId) {
        TrialState state = state(level, robotId, false);
        if (state == null || state.baseline == null || state.candidate == null) return Optional.empty();
        return Optional.of(RobotCommissioningTrialComparison.between(state.baseline, state.candidate));
    }

    public static synchronized void clear(Level level, UUID robotId) {
        LinkedHashMap<UUID, TrialState> byRobot = DATA.get(level);
        if (byRobot == null) return;
        byRobot.remove(robotId);
        if (byRobot.isEmpty()) DATA.remove(level);
    }

    private static TrialState state(Level level, UUID robotId, boolean create) {
        if (level == null || robotId == null) return null;
        LinkedHashMap<UUID, TrialState> byRobot = DATA.get(level);
        if (byRobot == null && create) {
            byRobot = new LinkedHashMap<>();
            DATA.put(level, byRobot);
        }
        if (byRobot == null) return null;

        TrialState state = byRobot.get(robotId);
        if (state == null && create) {
            if (byRobot.size() >= MAX_ROBOTS_PER_LEVEL) {
                Iterator<UUID> oldest = byRobot.keySet().iterator();
                if (oldest.hasNext()) {
                    oldest.next();
                    oldest.remove();
                }
            }
            state = new TrialState();
            byRobot.put(robotId, state);
        }
        return state;
    }

    private static final class TrialState {
        private long nextSequence = 1;
        private RobotCommissioningTrialRecord baseline;
        private RobotCommissioningTrialRecord candidate;
    }
}
