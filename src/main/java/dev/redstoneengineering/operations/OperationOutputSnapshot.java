package dev.redstoneengineering.operations;

import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;

/**
 * Immutable completed-output evidence exposed by Industrial Operations to downstream logistics.
 * This record owns no inventory and does not move material.
 */
public record OperationOutputSnapshot(
        long outputId,
        long jobId,
        String resourceId,
        int units,
        BlockPos source,
        PortQuality evidenceQuality,
        boolean completionConfirmed,
        boolean materialReady,
        boolean faultActive
) {
    public OperationOutputSnapshot {
        if (outputId < 0) throw new IllegalArgumentException("outputId must be non-negative");
        if (jobId < 0) throw new IllegalArgumentException("jobId must be non-negative");
        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException("resourceId must be non-blank");
        }
        resourceId = resourceId.trim();
        if (units <= 0) throw new IllegalArgumentException("units must be positive");
        if (source == null) throw new IllegalArgumentException("source is required");
        if (evidenceQuality == null) evidenceQuality = PortQuality.NO_SIGNAL;
    }
}
