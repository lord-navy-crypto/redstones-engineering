package dev.redstoneengineering.operations.world;

import dev.redstoneengineering.operations.OperationJob;

/**
 * World-backed lifecycle snapshot for a plant job.
 *
 * <p>The plant keeps the latest state here while the bounded plant event ledger preserves
 * the transition history. A completion tick of {@code -1} means the job has not completed.</p>
 */
public record OperationJobLifecycleRecord(
        long jobId,
        String processId,
        int quantity,
        int priority,
        long releaseTick,
        long dueTick,
        long admittedTick,
        long stateTick,
        long completionTick,
        Status status
) {
    public enum Status {
        ADMITTED,
        QUEUED,
        DISPATCHED,
        IN_PROCESS,
        COMPLETED,
        CANCELLED,
        FAILED;

        public boolean terminal() {
            return this == COMPLETED || this == CANCELLED || this == FAILED;
        }
    }

    public OperationJobLifecycleRecord {
        if (jobId < 0) throw new IllegalArgumentException("jobId must be non-negative");
        if (processId == null || processId.isBlank()) {
            throw new IllegalArgumentException("processId must be non-blank");
        }
        processId = processId.trim();
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        if (priority < 0 || priority > 100) throw new IllegalArgumentException("priority must be in 0..100");
        if (releaseTick < 0 || dueTick < 0 || admittedTick < 0 || stateTick < 0) {
            throw new IllegalArgumentException("lifecycle ticks must be non-negative");
        }
        if (dueTick != 0 && dueTick < releaseTick) {
            throw new IllegalArgumentException("dueTick must be zero or not earlier than releaseTick");
        }
        if (completionTick < -1) throw new IllegalArgumentException("completionTick must be -1 or non-negative");
        if (status == null) status = Status.ADMITTED;
        if (status == Status.COMPLETED && completionTick < 0) {
            throw new IllegalArgumentException("completed jobs require completionTick");
        }
        if (completionTick >= 0 && completionTick < admittedTick) {
            throw new IllegalArgumentException("completionTick must not precede admittedTick");
        }
    }

    public static OperationJobLifecycleRecord admitted(OperationJob job, long admittedTick) {
        if (job == null) throw new IllegalArgumentException("job is required");
        return new OperationJobLifecycleRecord(
                job.jobId(), job.processId(), job.quantity(), job.priority(),
                job.releaseTick(), job.dueTick(), admittedTick, admittedTick, -1, Status.ADMITTED
        );
    }

    public OperationJobLifecycleRecord transition(Status nextStatus, long tick) {
        if (nextStatus == null) throw new IllegalArgumentException("nextStatus is required");
        if (tick < stateTick) throw new IllegalArgumentException("state transition tick must not move backwards");
        long nextCompletion = nextStatus == Status.COMPLETED ? tick : completionTick;
        return new OperationJobLifecycleRecord(
                jobId, processId, quantity, priority, releaseTick, dueTick,
                admittedTick, tick, nextCompletion, nextStatus
        );
    }

    public boolean hasDueDate() {
        return dueTick != 0;
    }

    public boolean completed() {
        return status == Status.COMPLETED && completionTick >= 0;
    }

    public boolean onTime() {
        return completed() && (!hasDueDate() || completionTick <= dueTick);
    }

    public long latenessTicks() {
        if (!completed() || !hasDueDate()) return 0;
        return Math.max(0, completionTick - dueTick);
    }
}
