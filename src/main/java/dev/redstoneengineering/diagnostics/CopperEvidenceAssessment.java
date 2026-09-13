package dev.redstoneengineering.diagnostics;

import dev.redstoneengineering.diagnostics.events.SystemEventKind;
import dev.redstoneengineering.diagnostics.events.SystemEventRecord;
import dev.redstoneengineering.diagnostics.events.SystemEventScope;
import dev.redstoneengineering.diagnostics.events.SystemEventTimeline;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only plant-scoped projection of Copper meter commissioning-evidence transitions.
 *
 * <p>This is intentionally separate from {@link ElectricalReliabilityAssessment}: measurement
 * evidence readiness is not fuse protection reliability and must not contribute to trip downtime,
 * repeat-trip counts, MTBF, or MTTR semantics.</p>
 */
public final class CopperEvidenceAssessment {
    private CopperEvidenceAssessment() {}

    private enum SourceState { DEGRADED, FAILED }

    public record Snapshot(
            int degradedTransitions,
            int failedTransitions,
            int restoredTransitions,
            int activeDegradedSources,
            int activeFailedSources,
            long lastFailureAgeTicks,
            long lastRestoreAgeTicks
    ) {
        public boolean hasEvidenceTransitions() {
            return degradedTransitions > 0 || failedTransitions > 0 || restoredTransitions > 0;
        }
        public boolean hasActiveFailure() { return activeFailedSources > 0; }
        public boolean hasActiveDegradation() { return activeDegradedSources > 0; }
    }

    public static Snapshot inspect(Level level, SystemEventScope scope) {
        if (level == null || scope == null) return none();
        return project(SystemEventTimeline.within(level, scope), level.getGameTime());
    }

    /** Pure chronological projection; input is expected oldest-to-newest. */
    public static Snapshot project(List<SystemEventRecord> events, long nowTick) {
        if (events == null || events.isEmpty()) return none();

        int degraded = 0;
        int failed = 0;
        int restored = 0;
        long lastFailureTick = -1L;
        long lastRestoreTick = -1L;
        Map<BlockPos, SourceState> states = new HashMap<>();

        for (SystemEventRecord event : events) {
            if (event == null) continue;
            if (event.kind() == SystemEventKind.ELECTRICAL_EVIDENCE_DEGRADED) {
                degraded++;
                states.put(event.source(), SourceState.DEGRADED);
            } else if (event.kind() == SystemEventKind.ELECTRICAL_EVIDENCE_FAILED) {
                failed++;
                states.put(event.source(), SourceState.FAILED);
                lastFailureTick = Math.max(lastFailureTick, event.tick());
            } else if (event.kind() == SystemEventKind.ELECTRICAL_EVIDENCE_RESTORED) {
                restored++;
                states.remove(event.source());
                lastRestoreTick = Math.max(lastRestoreTick, event.tick());
            }
        }

        int activeDegraded = 0;
        int activeFailed = 0;
        for (SourceState state : states.values()) {
            if (state == SourceState.FAILED) activeFailed++;
            else activeDegraded++;
        }
        long now = Math.max(0L, nowTick);
        long failureAge = lastFailureTick < 0 ? -1L : Math.max(0L, now - lastFailureTick);
        long restoreAge = lastRestoreTick < 0 ? -1L : Math.max(0L, now - lastRestoreTick);
        return new Snapshot(degraded, failed, restored, activeDegraded, activeFailed, failureAge, restoreAge);
    }

    private static Snapshot none() {
        return new Snapshot(0, 0, 0, 0, 0, -1L, -1L);
    }
}
