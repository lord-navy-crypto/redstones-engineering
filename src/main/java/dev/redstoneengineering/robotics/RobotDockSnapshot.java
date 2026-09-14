package dev.redstoneengineering.robotics;

import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;

/** Immutable authoritative evidence snapshot for one AMR dock. */
public record RobotDockSnapshot(
        String dockId,
        BlockPos position,
        PortQuality evidenceQuality,
        String reservedRobotId,
        String occupiedRobotId,
        boolean approachClear,
        boolean alignmentReady,
        boolean transferReady,
        boolean emergencyStopClear,
        boolean faultActive
) {
    public RobotDockSnapshot {
        if (dockId == null || dockId.isBlank()) throw new IllegalArgumentException("dock id must be non-blank");
        if (position == null) throw new IllegalArgumentException("dock position is required");
        dockId = dockId.trim();
        position = position.immutable();
        reservedRobotId = normalizeIdentity(reservedRobotId);
        occupiedRobotId = normalizeIdentity(occupiedRobotId);
    }

    public boolean reservedFor(String robotId) {
        String normalized = normalizeIdentity(robotId);
        return normalized != null && normalized.equals(reservedRobotId);
    }

    public boolean occupiedBy(String robotId) {
        String normalized = normalizeIdentity(robotId);
        return normalized != null && normalized.equals(occupiedRobotId);
    }

    public boolean reservedByOther(String robotId) {
        return reservedRobotId != null && !reservedFor(robotId);
    }

    public boolean occupiedByOther(String robotId) {
        return occupiedRobotId != null && !occupiedBy(robotId);
    }

    private static String normalizeIdentity(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
