package dev.redstoneengineering.diagnostics;

import dev.redstoneengineering.diagnostics.acceptance.EngineeringAcceptanceSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Bounded transient storage for one explicit commissioning trial per controller.
 *
 * A trial is intentionally two-point and operator-owned: capture BASELINE, change or disturb the
 * system, wait for trustworthy evidence, then capture CANDIDATE. Starting a new baseline discards
 * the previous candidate. Read-only inspection never changes retention priority.
 */
public final class CommissioningTrialStore {
    public static final int MAX_CONTROLLERS_PER_LEVEL = 256;

    private static final Map<Level, LinkedHashMap<Long, TrialState>> DATA = new WeakHashMap<>();

    private CommissioningTrialStore() {}

    public static synchronized CommissioningTrialRecord captureBaseline(
            Level level,
            BlockPos pos,
            long gameTick,
            int tuningPreset,
            CommissioningSnapshot commissioning,
            EngineeringAcceptanceSnapshot acceptance
    ) {
        TrialState state = state(level, pos, true);
        CommissioningTrialRecord baseline = new CommissioningTrialRecord(
                state.nextSequence++, gameTick, tuningPreset, commissioning, acceptance);
        state.baseline = baseline;
        state.candidate = null;
        return baseline;
    }

    public static synchronized Optional<CommissioningTrialRecord> captureCandidate(
            Level level,
            BlockPos pos,
            long gameTick,
            int tuningPreset,
            CommissioningSnapshot commissioning,
            EngineeringAcceptanceSnapshot acceptance
    ) {
        TrialState state = state(level, pos, false);
        if (state == null || state.baseline == null) return Optional.empty();
        CommissioningTrialRecord candidate = new CommissioningTrialRecord(
                state.nextSequence++, gameTick, tuningPreset, commissioning, acceptance);
        state.candidate = candidate;
        return Optional.of(candidate);
    }

    public static synchronized Optional<CommissioningTrialRecord> baseline(Level level, BlockPos pos) {
        TrialState state = state(level, pos, false);
        return state == null ? Optional.empty() : Optional.ofNullable(state.baseline);
    }

    public static synchronized Optional<CommissioningTrialRecord> candidate(Level level, BlockPos pos) {
        TrialState state = state(level, pos, false);
        return state == null ? Optional.empty() : Optional.ofNullable(state.candidate);
    }

    public static synchronized Optional<CommissioningTrialComparison> comparison(Level level, BlockPos pos) {
        TrialState state = state(level, pos, false);
        if (state == null || state.baseline == null || state.candidate == null) return Optional.empty();
        return Optional.of(CommissioningTrialComparison.between(state.baseline, state.candidate));
    }

    public static synchronized void clear(Level level, BlockPos pos) {
        LinkedHashMap<Long, TrialState> byPos = DATA.get(level);
        if (byPos == null) return;
        byPos.remove(pos.asLong());
        if (byPos.isEmpty()) DATA.remove(level);
    }

    public static synchronized void clear(Level level) {
        DATA.remove(level);
    }

    public static synchronized int controllerCount(Level level) {
        LinkedHashMap<Long, TrialState> byPos = DATA.get(level);
        return byPos == null ? 0 : byPos.size();
    }

    private static TrialState state(Level level, BlockPos pos, boolean create) {
        LinkedHashMap<Long, TrialState> byPos = DATA.get(level);
        if (byPos == null && create) {
            byPos = new LinkedHashMap<>();
            DATA.put(level, byPos);
        }
        if (byPos == null) return null;

        long key = pos.asLong();
        TrialState state = byPos.get(key);
        if (state == null && create) {
            if (byPos.size() >= MAX_CONTROLLERS_PER_LEVEL) {
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
        private CommissioningTrialRecord baseline;
        private CommissioningTrialRecord candidate;
    }
}
