package dev.redstoneengineering.operations;

/** Explicit planning demand that converts a confirmed rework quantity into a new operation job. */
public record OperationReworkDemand(
        long reworkJobId,
        long sourceOutputId,
        long sourceJobId,
        String processId,
        int units,
        int priority,
        long releaseTick,
        long dueTick
) {
    public OperationReworkDemand {
        if (reworkJobId < 0) throw new IllegalArgumentException("reworkJobId must be non-negative");
        if (sourceOutputId < 0) throw new IllegalArgumentException("sourceOutputId must be non-negative");
        if (sourceJobId < 0) throw new IllegalArgumentException("sourceJobId must be non-negative");
        if (processId == null || processId.isBlank()) {
            throw new IllegalArgumentException("processId must be non-blank");
        }
        processId = processId.trim();
        if (units <= 0) throw new IllegalArgumentException("units must be positive");
        if (priority < 0 || priority > 100) {
            throw new IllegalArgumentException("priority must be in 0..100");
        }
        if (releaseTick < 0) throw new IllegalArgumentException("releaseTick must be non-negative");
        if (dueTick < 0) throw new IllegalArgumentException("dueTick must be non-negative");
        if (dueTick != 0 && dueTick < releaseTick) {
            throw new IllegalArgumentException("dueTick must be zero or not earlier than releaseTick");
        }
        if (reworkJobId == sourceJobId) {
            throw new IllegalArgumentException("rework job must use a new job identity");
        }
    }
}
