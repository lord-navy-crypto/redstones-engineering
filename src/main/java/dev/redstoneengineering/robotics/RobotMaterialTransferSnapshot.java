package dev.redstoneengineering.robotics;

import dev.redstoneengineering.core.port.PortQuality;

/** Immutable authoritative evidence for one dock-to-AMR material transfer. */
public record RobotMaterialTransferSnapshot(
        String transferId,
        String dockId,
        String robotId,
        int requestedUnits,
        int transferredUnits,
        PortQuality evidenceQuality,
        boolean sourceConfirmed,
        boolean destinationConfirmed,
        boolean completionConfirmed,
        boolean faultActive
) {
    public RobotMaterialTransferSnapshot {
        transferId = requireIdentity(transferId, "transfer id");
        dockId = requireIdentity(dockId, "dock id");
        robotId = requireIdentity(robotId, "robot id");
        if (requestedUnits <= 0) throw new IllegalArgumentException("requested units must be positive");
        if (transferredUnits < 0) throw new IllegalArgumentException("transferred units must be non-negative");
        if (transferredUnits > requestedUnits) throw new IllegalArgumentException("transferred units cannot exceed requested units");
    }

    public boolean completeQuantity() {
        return transferredUnits == requestedUnits;
    }

    private static String requireIdentity(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " must be non-blank");
        return value.trim();
    }
}
