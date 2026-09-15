package dev.redstoneengineering.operations;

import dev.redstoneengineering.core.port.PortQuality;

/** Immutable evidence that one resource changeover has been physically performed and confirmed. */
public record OperationChangeoverEvidence(
        String resourceId,
        String targetProcessId,
        PortQuality evidenceQuality,
        boolean setupWorkConfirmed,
        boolean completionConfirmed,
        boolean faultActive
) {
    public OperationChangeoverEvidence {
        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException("resourceId must be non-blank");
        }
        resourceId = resourceId.trim();
        if (targetProcessId == null || targetProcessId.isBlank()) {
            throw new IllegalArgumentException("targetProcessId must be non-blank");
        }
        targetProcessId = targetProcessId.trim();
        if (evidenceQuality == null) evidenceQuality = PortQuality.NO_SIGNAL;
    }
}
