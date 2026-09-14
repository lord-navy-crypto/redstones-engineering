package dev.redstoneengineering.robotics;

import dev.redstoneengineering.core.port.PortQuality;

/** Immutable evidence describing payload currently carried by one AMR. */
public record RobotPayloadSnapshot(
        String payloadId,
        String robotId,
        int units,
        PortQuality evidenceQuality,
        boolean secured,
        boolean faultActive
) {
    public RobotPayloadSnapshot {
        payloadId = requireIdentity(payloadId, "payload id");
        robotId = requireIdentity(robotId, "robot id");
        if (units < 0) throw new IllegalArgumentException("payload units must be non-negative");
    }

    private static String requireIdentity(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " must be non-blank");
        return value.trim();
    }
}
