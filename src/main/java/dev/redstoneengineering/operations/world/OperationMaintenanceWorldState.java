package dev.redstoneengineering.operations.world;

import dev.redstoneengineering.operations.OperationMaintenanceCompletionEvidence;
import dev.redstoneengineering.operations.OperationMaintenanceRuntime;
import dev.redstoneengineering.operations.OperationResourceMaintenanceSnapshot;
import net.minecraft.server.level.ServerLevel;

/**
 * Server-owned commit boundary for resource maintenance lifecycle state.
 *
 * <p>{@link OperationMaintenanceRuntime} remains the pure decision authority. This facade owns only
 * world persistence and durable history: explicit observed maintenance evidence may be seeded,
 * start/complete decisions are evaluated by the pure runtime, and changed snapshots are committed
 * to {@link OperationPlantSavedData} together with one maintenance ledger event. A history failure
 * rolls the current snapshot back instead of leaving a half-committed lifecycle.</p>
 */
public final class OperationMaintenanceWorldState {
    private OperationMaintenanceWorldState() {}

    public enum Verdict {
        OBSERVED,
        STARTED,
        COMPLETED,
        WAIT,
        SAFE_STOP,
        FAULT
    }

    public record Decision(
            Verdict verdict,
            String reason,
            OperationResourceMaintenanceSnapshot snapshot
    ) {
        public Decision {
            if (verdict == null) verdict = Verdict.SAFE_STOP;
            if (reason == null || reason.isBlank()) reason = "UNSPECIFIED";
        }

        public boolean changed() {
            return verdict == Verdict.OBSERVED || verdict == Verdict.STARTED || verdict == Verdict.COMPLETED;
        }
    }

    public static OperationResourceMaintenanceSnapshot snapshot(ServerLevel level, String resourceId) {
        if (level == null || resourceId == null || resourceId.isBlank()) return null;
        return OperationPlantSavedData.get(level).maintenanceSnapshot(resourceId);
    }

    /**
     * Persist an explicit external observation such as AVAILABLE, MAINTENANCE_DUE or FAULTED.
     * No due state is inferred from elapsed time.
     */
    public static Decision observe(
            ServerLevel level,
            OperationResourceMaintenanceSnapshot observed,
            long gameTick,
            String reason
    ) {
        if (level == null) return safeStop("SERVER_LEVEL_MISSING", null);
        if (observed == null) return safeStop("MAINTENANCE_SNAPSHOT_MISSING", null);
        if (gameTick < 0) return safeStop("GAME_TICK_INVALID", observed);

        OperationPlantSavedData data = OperationPlantSavedData.get(level);
        OperationResourceMaintenanceSnapshot previous = data.maintenanceSnapshot(observed.resourceId());
        if (!data.putMaintenanceSnapshot(observed)) {
            return safeStop("MAINTENANCE_PERSISTENCE_REJECTED", previous);
        }
        if (!OperationPlantRuntimeRecorder.recordMaintenanceSnapshot(
                level,
                observed,
                gameTick,
                reason == null || reason.isBlank() ? "OBSERVED" : reason.trim()
        )) {
            rollback(data, previous, observed.resourceId());
            throw new IllegalStateException("MAINTENANCE_HISTORY_REJECTED");
        }
        return new Decision(Verdict.OBSERVED, "MAINTENANCE_SNAPSHOT_RECORDED", observed);
    }

    public static Decision start(ServerLevel level, String resourceId, long gameTick) {
        if (level == null) return safeStop("SERVER_LEVEL_MISSING", null);
        if (resourceId == null || resourceId.isBlank()) return safeStop("RESOURCE_ID_MISSING", null);
        if (gameTick < 0) return safeStop("GAME_TICK_INVALID", null);

        OperationPlantSavedData data = OperationPlantSavedData.get(level);
        OperationResourceMaintenanceSnapshot current = data.maintenanceSnapshot(resourceId);
        if (current == null) return safeStop("MAINTENANCE_STATE_MISSING", null);

        OperationMaintenanceRuntime.Decision runtimeDecision = OperationMaintenanceRuntime.start(current);
        return applyRuntimeDecision(level, data, current, runtimeDecision, gameTick);
    }

    public static Decision complete(
            ServerLevel level,
            OperationMaintenanceCompletionEvidence evidence,
            long gameTick
    ) {
        if (level == null) return safeStop("SERVER_LEVEL_MISSING", null);
        if (evidence == null) return safeStop("MAINTENANCE_COMPLETION_EVIDENCE_MISSING", null);
        if (gameTick < 0) return safeStop("GAME_TICK_INVALID", null);

        OperationPlantSavedData data = OperationPlantSavedData.get(level);
        OperationResourceMaintenanceSnapshot current = data.maintenanceSnapshot(evidence.resourceId());
        if (current == null) return safeStop("MAINTENANCE_STATE_MISSING", null);

        OperationMaintenanceRuntime.Decision runtimeDecision = OperationMaintenanceRuntime.complete(current, evidence);
        return applyRuntimeDecision(level, data, current, runtimeDecision, gameTick);
    }

    private static Decision applyRuntimeDecision(
            ServerLevel level,
            OperationPlantSavedData data,
            OperationResourceMaintenanceSnapshot previous,
            OperationMaintenanceRuntime.Decision runtimeDecision,
            long gameTick
    ) {
        if (!runtimeDecision.changed()) {
            if (runtimeDecision.verdict() == OperationMaintenanceRuntime.Verdict.FAULT) {
                OperationPlantRuntimeRecorder.recordMaintenanceDecision(level, runtimeDecision, gameTick);
            }
            return map(runtimeDecision);
        }

        OperationResourceMaintenanceSnapshot next = runtimeDecision.nextState();
        if (!data.putMaintenanceSnapshot(next)) {
            return safeStop("MAINTENANCE_PERSISTENCE_REJECTED", previous);
        }
        if (!OperationPlantRuntimeRecorder.recordMaintenanceDecision(level, runtimeDecision, gameTick)) {
            rollback(data, previous, next.resourceId());
            throw new IllegalStateException("MAINTENANCE_HISTORY_REJECTED");
        }
        return map(runtimeDecision);
    }

    private static Decision map(OperationMaintenanceRuntime.Decision runtimeDecision) {
        Verdict verdict = switch (runtimeDecision.verdict()) {
            case STARTED -> Verdict.STARTED;
            case COMPLETED -> Verdict.COMPLETED;
            case WAIT -> Verdict.WAIT;
            case SAFE_STOP -> Verdict.SAFE_STOP;
            case FAULT -> Verdict.FAULT;
        };
        return new Decision(verdict, runtimeDecision.reason(), runtimeDecision.nextState());
    }

    private static void rollback(
            OperationPlantSavedData data,
            OperationResourceMaintenanceSnapshot previous,
            String resourceId
    ) {
        boolean restored;
        if (previous == null) {
            restored = data.removeMaintenanceSnapshot(resourceId);
        } else {
            restored = data.putMaintenanceSnapshot(previous);
        }
        if (!restored) {
            throw new IllegalStateException("MAINTENANCE_ROLLBACK_FAILED");
        }
    }

    private static Decision safeStop(String reason, OperationResourceMaintenanceSnapshot snapshot) {
        return new Decision(Verdict.SAFE_STOP, reason, snapshot);
    }
}
