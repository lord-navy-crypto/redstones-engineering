package dev.redstoneengineering.operations;

/** Immutable work demand admitted to the Industrial Operations runtime. */
public record OperationJob(
        long jobId,
        String processId,
        int quantity,
        int priority,
        long releaseTick,
        long dueTick
) {
    public OperationJob {
        if (jobId < 0) throw new IllegalArgumentException("jobId must be non-negative");
        if (processId == null || processId.isBlank()) {
            throw new IllegalArgumentException("processId must be non-blank");
        }
        processId = processId.trim();
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        if (priority < 0 || priority > 100) {
            throw new IllegalArgumentException("priority must be in 0..100");
        }
        if (releaseTick < 0) throw new IllegalArgumentException("releaseTick must be non-negative");
        if (dueTick < 0) throw new IllegalArgumentException("dueTick must be non-negative");
        if (dueTick != 0 && dueTick < releaseTick) {
            throw new IllegalArgumentException("dueTick must be zero or not earlier than releaseTick");
        }
    }

    public boolean releasedAt(long gameTick) {
        return gameTick >= releaseTick;
    }

    public boolean hasDueDate() {
        return dueTick != 0;
    }
}
