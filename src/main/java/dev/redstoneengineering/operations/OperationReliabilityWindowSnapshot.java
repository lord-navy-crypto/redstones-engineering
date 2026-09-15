package dev.redstoneengineering.operations;

import dev.redstoneengineering.core.port.PortQuality;

/**
 * Immutable observed reliability evidence for one resource over a bounded time window.
 * Planned maintenance is separated from fault downtime so diagnostics do not conflate them.
 */
public record OperationReliabilityWindowSnapshot(
        String resourceId,
        long observedTicks,
        long operatingTicks,
        long faultDowntimeTicks,
        long plannedMaintenanceTicks,
        long otherHeldTicks,
        int failureCount,
        int completedRepairCount,
        PortQuality evidenceQuality,
        boolean faultActive
) {
    public OperationReliabilityWindowSnapshot {
        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException("resourceId must be non-blank");
        }
        resourceId = resourceId.trim();
        if (observedTicks < 0 || operatingTicks < 0 || faultDowntimeTicks < 0
                || plannedMaintenanceTicks < 0 || otherHeldTicks < 0) {
            throw new IllegalArgumentException("reliability tick counts must be non-negative");
        }
        long accounted = operatingTicks + faultDowntimeTicks + plannedMaintenanceTicks + otherHeldTicks;
        if (accounted != observedTicks) {
            throw new IllegalArgumentException("reliability window tick accounting must equal observedTicks");
        }
        if (failureCount < 0 || completedRepairCount < 0) {
            throw new IllegalArgumentException("failure/repair counts must be non-negative");
        }
        if (completedRepairCount > failureCount) {
            throw new IllegalArgumentException("completed repairs cannot exceed observed failures");
        }
        if (evidenceQuality == null) evidenceQuality = PortQuality.NO_SIGNAL;
    }

    public long unplannedDowntimeTicks() {
        return faultDowntimeTicks;
    }
}
