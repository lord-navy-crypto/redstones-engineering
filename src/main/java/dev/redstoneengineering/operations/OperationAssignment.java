package dev.redstoneengineering.operations;

/** Immutable record that one operation job has been assigned to one resource. */
public record OperationAssignment(
        OperationJob job,
        String resourceId,
        long assignedTick
) {
    public OperationAssignment {
        if (job == null) throw new IllegalArgumentException("job is required");
        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException("resourceId must be non-blank");
        }
        resourceId = resourceId.trim();
        if (assignedTick < 0) throw new IllegalArgumentException("assignedTick must be non-negative");
    }
}
