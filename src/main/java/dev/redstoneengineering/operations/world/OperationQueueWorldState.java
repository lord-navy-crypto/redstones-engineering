package dev.redstoneengineering.operations.world;

import dev.redstoneengineering.operations.OperationAssignment;
import dev.redstoneengineering.operations.OperationCompletionEvidence;
import dev.redstoneengineering.operations.OperationDispatchRuntime;
import dev.redstoneengineering.operations.OperationJob;
import dev.redstoneengineering.operations.OperationQueueRuntime;
import dev.redstoneengineering.operations.OperationQueueSnapshot;
import dev.redstoneengineering.operations.OperationResourceSnapshot;
import net.minecraft.server.level.ServerLevel;

import java.util.Collection;

/**
 * Server-owned commit boundary for persistent Operations queues.
 *
 * <p>{@link OperationQueueRuntime} remains the only queue lifecycle authority. This facade reads
 * the current immutable queue snapshot from {@link OperationPlantSavedData}, delegates the pure
 * decision, commits the replacement snapshot, and then records durable lifecycle/history evidence.
 * It never ranks jobs/resources itself and never infers machine execution from a dispatch permit.</p>
 */
public final class OperationQueueWorldState {
    private OperationQueueWorldState() {}

    public enum Verdict {
        CREATED,
        REMOVED,
        ENQUEUED,
        ASSIGNED,
        COMPLETED,
        WAIT,
        SAFE_STOP,
        FAULT
    }

    public record Decision(
            Verdict verdict,
            String reason,
            OperationQueueSnapshot snapshot,
            OperationAssignment assignment
    ) {
        public Decision {
            if (verdict == null) verdict = Verdict.SAFE_STOP;
            if (reason == null || reason.isBlank()) reason = "UNSPECIFIED";
            if (verdict != Verdict.ASSIGNED && verdict != Verdict.COMPLETED) assignment = null;
        }

        public boolean changed() {
            return verdict == Verdict.CREATED
                    || verdict == Verdict.REMOVED
                    || verdict == Verdict.ENQUEUED
                    || verdict == Verdict.ASSIGNED
                    || verdict == Verdict.COMPLETED;
        }
    }

    public static Decision create(ServerLevel level, String queueId, int capacity) {
        if (level == null) return safeStop("SERVER_LEVEL_MISSING", null);
        if (queueId == null || queueId.isBlank()) return safeStop("QUEUE_ID_MISSING", null);
        queueId = queueId.trim();
        OperationPlantSavedData data = OperationPlantSavedData.get(level);
        OperationQueueSnapshot existing = data.queue(queueId);
        if (existing != null) return safeStop("QUEUE_ID_ALREADY_EXISTS", existing);

        final OperationQueueSnapshot created;
        try {
            created = OperationQueueSnapshot.empty(capacity);
        } catch (IllegalArgumentException ex) {
            return safeStop("QUEUE_CONFIGURATION_INVALID", null);
        }
        if (!data.putQueue(queueId, created)) return safeStop("QUEUE_PERSISTENCE_REJECTED", null);
        return new Decision(Verdict.CREATED, "QUEUE_CREATED", created, null);
    }

    public static Decision remove(ServerLevel level, String queueId) {
        if (level == null) return safeStop("SERVER_LEVEL_MISSING", null);
        if (queueId == null || queueId.isBlank()) return safeStop("QUEUE_ID_MISSING", null);
        queueId = queueId.trim();
        OperationPlantSavedData data = OperationPlantSavedData.get(level);
        OperationQueueSnapshot current = data.queue(queueId);
        if (current == null) return waitFor("QUEUE_NOT_FOUND", null);
        if (current.wip() > 0) return waitFor("QUEUE_NOT_EMPTY", current);
        if (!data.removeQueue(queueId)) return safeStop("QUEUE_REMOVE_REJECTED", current);
        return new Decision(Verdict.REMOVED, "QUEUE_REMOVED", null, null);
    }

    public static OperationQueueSnapshot snapshot(ServerLevel level, String queueId) {
        if (level == null || queueId == null || queueId.isBlank()) return null;
        return OperationPlantSavedData.get(level).queue(queueId.trim());
    }

    public static Decision enqueue(
            ServerLevel level,
            String queueId,
            OperationJob job,
            long gameTick
    ) {
        if (level == null) return safeStop("SERVER_LEVEL_MISSING", null);
        if (queueId == null || queueId.isBlank()) return safeStop("QUEUE_ID_MISSING", null);
        queueId = queueId.trim();
        OperationPlantSavedData data = OperationPlantSavedData.get(level);
        OperationQueueSnapshot current = data.queue(queueId);
        if (current == null) return safeStop("QUEUE_NOT_FOUND", null);

        OperationQueueRuntime.Decision decision = OperationQueueRuntime.enqueue(current, job);
        if (decision.verdict() != OperationQueueRuntime.Verdict.ENQUEUED) {
            return fromRuntime(decision);
        }
        if (!data.putQueue(queueId, decision.nextState())) {
            return safeStop("QUEUE_PERSISTENCE_REJECTED", current);
        }
        if (!OperationPlantRuntimeRecorder.recordQueueEnqueue(level, queueId, job, decision, gameTick)) {
            if (!data.putQueue(queueId, current)) {
                throw new IllegalStateException("QUEUE_HISTORY_REJECTED_AND_ROLLBACK_FAILED");
            }
            throw new IllegalStateException("QUEUE_HISTORY_REJECTED");
        }
        return new Decision(Verdict.ENQUEUED, decision.reason(), decision.nextState(), null);
    }

    public static Decision dispatch(
            ServerLevel level,
            String queueId,
            Collection<OperationResourceSnapshot> resources,
            long gameTick,
            OperationDispatchRuntime.Policy policy
    ) {
        if (level == null) return safeStop("SERVER_LEVEL_MISSING", null);
        if (queueId == null || queueId.isBlank()) return safeStop("QUEUE_ID_MISSING", null);
        queueId = queueId.trim();
        OperationPlantSavedData data = OperationPlantSavedData.get(level);
        OperationQueueSnapshot current = data.queue(queueId);
        if (current == null) return safeStop("QUEUE_NOT_FOUND", null);

        OperationQueueRuntime.Decision decision = OperationQueueRuntime.dispatch(current, resources, gameTick, policy);
        if (decision.verdict() != OperationQueueRuntime.Verdict.ASSIGNED) {
            return fromRuntime(decision);
        }
        if (!data.putQueue(queueId, decision.nextState())) {
            return safeStop("QUEUE_PERSISTENCE_REJECTED", current);
        }
        if (!OperationPlantRuntimeRecorder.recordQueueDispatch(level, queueId, decision, gameTick)) {
            if (!data.putQueue(queueId, current)) {
                throw new IllegalStateException("DISPATCH_HISTORY_REJECTED_AND_QUEUE_ROLLBACK_FAILED");
            }
            throw new IllegalStateException("DISPATCH_HISTORY_REJECTED");
        }
        return new Decision(Verdict.ASSIGNED, decision.reason(), decision.nextState(), decision.assignment());
    }

    public static Decision complete(
            ServerLevel level,
            String queueId,
            OperationCompletionEvidence evidence,
            long gameTick
    ) {
        if (level == null) return safeStop("SERVER_LEVEL_MISSING", null);
        if (queueId == null || queueId.isBlank()) return safeStop("QUEUE_ID_MISSING", null);
        queueId = queueId.trim();
        OperationPlantSavedData data = OperationPlantSavedData.get(level);
        OperationQueueSnapshot current = data.queue(queueId);
        if (current == null) return safeStop("QUEUE_NOT_FOUND", null);

        OperationQueueRuntime.Decision decision = OperationQueueRuntime.complete(current, evidence);
        if (decision.verdict() != OperationQueueRuntime.Verdict.COMPLETED) {
            return fromRuntime(decision);
        }
        if (!data.putQueue(queueId, decision.nextState())) {
            return safeStop("QUEUE_PERSISTENCE_REJECTED", current);
        }
        if (!OperationPlantRuntimeRecorder.recordQueueCompletion(level, queueId, decision, gameTick)) {
            if (!data.putQueue(queueId, current)) {
                throw new IllegalStateException("COMPLETION_HISTORY_REJECTED_AND_QUEUE_ROLLBACK_FAILED");
            }
            throw new IllegalStateException("COMPLETION_HISTORY_REJECTED");
        }
        return new Decision(Verdict.COMPLETED, decision.reason(), decision.nextState(), decision.assignment());
    }

    private static Decision fromRuntime(OperationQueueRuntime.Decision decision) {
        Verdict verdict = switch (decision.verdict()) {
            case ENQUEUED -> Verdict.ENQUEUED;
            case ASSIGNED -> Verdict.ASSIGNED;
            case COMPLETED -> Verdict.COMPLETED;
            case WAIT -> Verdict.WAIT;
            case SAFE_STOP -> Verdict.SAFE_STOP;
            case FAULT -> Verdict.FAULT;
        };
        return new Decision(verdict, decision.reason(), decision.nextState(), decision.assignment());
    }

    private static Decision waitFor(String reason, OperationQueueSnapshot snapshot) {
        return new Decision(Verdict.WAIT, reason, snapshot, null);
    }

    private static Decision safeStop(String reason, OperationQueueSnapshot snapshot) {
        return new Decision(Verdict.SAFE_STOP, reason, snapshot, null);
    }
}
