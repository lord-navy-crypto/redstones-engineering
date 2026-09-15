package dev.redstoneengineering.operations;

import dev.redstoneengineering.core.port.PortQuality;

/** Immutable maintenance state evidence for one production resource. */
public record OperationResourceMaintenanceSnapshot(
        String resourceId,
        State state,
        String maintenanceId,
        PortQuality evidenceQuality,
        boolean faultActive
) {
    public enum State {
        AVAILABLE,
        MAINTENANCE_DUE,
        IN_PROGRESS,
        FAULTED
    }

    public OperationResourceMaintenanceSnapshot {
        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException("resourceId must be non-blank");
        }
        resourceId = resourceId.trim();
        if (state == null) state = State.FAULTED;
        if (maintenanceId != null) {
            maintenanceId = maintenanceId.trim();
            if (maintenanceId.isBlank()) maintenanceId = null;
        }
        if ((state == State.MAINTENANCE_DUE || state == State.IN_PROGRESS) && maintenanceId == null) {
            throw new IllegalArgumentException("maintenance due/in-progress state requires maintenanceId");
        }
        if (evidenceQuality == null) evidenceQuality = PortQuality.NO_SIGNAL;
    }

    public boolean productionReady() {
        return !faultActive && evidenceQuality == PortQuality.VALID && state == State.AVAILABLE;
    }
}
