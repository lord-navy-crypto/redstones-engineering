package dev.redstoneengineering.operations;

import dev.redstoneengineering.core.port.PortQuality;

/** Immutable evidence that an assigned operation has produced a completed quantity. */
public record OperationCompletionEvidence(
        long jobId,
        String resourceId,
        int completedQuantity,
        PortQuality evidenceQuality,
        boolean processConfirmed,
        boolean outputConfirmed,
        boolean completionConfirmed,
        boolean faultActive
) {
    public OperationCompletionEvidence {
        if (jobId < 0) throw new IllegalArgumentException("jobId must be non-negative");
        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException("resourceId must be non-blank");
        }
        resourceId = resourceId.trim();
        if (completedQuantity < 0) {
            throw new IllegalArgumentException("completedQuantity must be non-negative");
        }
        if (evidenceQuality == null) evidenceQuality = PortQuality.NO_SIGNAL;
    }
}
