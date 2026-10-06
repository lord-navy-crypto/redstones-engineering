package dev.redstoneengineering.diagnostics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/** Bounded transient Baseline/Candidate storage for Signal Analyzer calibration trials. */
public final class SignalCalibrationTrialStore {
    public static final int MAX_ANALYZERS_PER_LEVEL = 256;

    private static final Map<Level, LinkedHashMap<Long, TrialState>> DATA = new WeakHashMap<>();

    private SignalCalibrationTrialStore() {}

    public static synchronized SignalCalibrationTrialRecord captureBaseline(
            Level level,
            BlockPos pos,
            SignalCalibrationTrialRecord evidence
    ) {
        if (evidence == null || !evidence.captureReady()) return null;
        TrialState state = state(level, pos, true);
        SignalCalibrationTrialRecord record = reSequence(state.nextSequence++, evidence);
        state.baseline = record;
        state.candidate = null;
        return record;
    }

    public static synchronized Optional<SignalCalibrationTrialRecord> captureCandidate(
            Level level,
            BlockPos pos,
            SignalCalibrationTrialRecord evidence
    ) {
        if (evidence == null || !evidence.captureReady()) return Optional.empty();
        TrialState state = state(level, pos, false);
        if (state == null || state.baseline == null) return Optional.empty();
        SignalCalibrationTrialRecord record = reSequence(state.nextSequence++, evidence);
        state.candidate = record;
        return Optional.of(record);
    }

    public static synchronized Optional<SignalCalibrationTrialRecord> baseline(Level level, BlockPos pos) {
        TrialState state = state(level, pos, false);
        return state == null ? Optional.empty() : Optional.ofNullable(state.baseline);
    }

    public static synchronized Optional<SignalCalibrationTrialRecord> candidate(Level level, BlockPos pos) {
        TrialState state = state(level, pos, false);
        return state == null ? Optional.empty() : Optional.ofNullable(state.candidate);
    }

    public static synchronized Optional<SignalCalibrationTrialComparison> comparison(Level level, BlockPos pos) {
        TrialState state = state(level, pos, false);
        if (state == null || state.baseline == null || state.candidate == null) return Optional.empty();
        return Optional.of(SignalCalibrationTrialComparison.between(state.baseline, state.candidate));
    }

    public static synchronized void clear(Level level, BlockPos pos) {
        LinkedHashMap<Long, TrialState> byPos = DATA.get(level);
        if (byPos == null) return;
        byPos.remove(pos.asLong());
        if (byPos.isEmpty()) DATA.remove(level);
    }

    private static SignalCalibrationTrialRecord reSequence(
            long sequence,
            SignalCalibrationTrialRecord source
    ) {
        return new SignalCalibrationTrialRecord(
                sequence,
                source.gameTick(),
                source.reference(),
                source.mode(),
                source.facingOrdinal(),
                source.calibrationOffset(),
                source.rawAverage100(),
                source.calibratedAverage100(),
                source.absoluteError100(),
                source.calibratedSpan(),
                source.calibratedMeanStep100(),
                source.clippingSamples(),
                source.windowCount(),
                source.validWindowCount(),
                source.sampleAgeTicks(),
                source.measurementQuality()
        );
    }

    private static TrialState state(Level level, BlockPos pos, boolean create) {
        if (level == null || pos == null) return null;
        LinkedHashMap<Long, TrialState> byPos = DATA.get(level);
        if (byPos == null && create) {
            byPos = new LinkedHashMap<>();
            DATA.put(level, byPos);
        }
        if (byPos == null) return null;

        long key = pos.asLong();
        TrialState state = byPos.get(key);
        if (state == null && create) {
            if (byPos.size() >= MAX_ANALYZERS_PER_LEVEL) {
                Iterator<Long> oldest = byPos.keySet().iterator();
                if (oldest.hasNext()) {
                    oldest.next();
                    oldest.remove();
                }
            }
            state = new TrialState();
            byPos.put(key, state);
        }
        return state;
    }

    private static final class TrialState {
        private long nextSequence = 1;
        private SignalCalibrationTrialRecord baseline;
        private SignalCalibrationTrialRecord candidate;
    }
}
