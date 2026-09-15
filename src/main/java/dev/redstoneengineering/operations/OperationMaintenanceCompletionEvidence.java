package dev.redstoneengineering.operations;

import dev.redstoneengineering.core.port.PortQuality;

/** Immutable evidence that one explicit maintenance action has been completed. */
public record OperationMaintenanceCompletionEvidence(
        String resourceId,
        String maintenanceId,
        PortQuality evidenceQuality,
        boolean workConfirmed,
        boolean completionConfirmed,
        boolean faultActive
) {
    public OperationMaintenanceCompletionEvidence {
        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException("resourceId must be non-blank");
        }
        resourceId = resourceId.trim();
        if (maintenanceId == null || maintenanceId.isBlank()) {
            throw new IllegalArgumentException("maintenanceId must be non-blank");
        }
        maintenanceId = maintenanceId.trim();
        if (evidenceQuality == null) evidenceQuality = PortQuality.NO_SIGNAL;
    }
}
