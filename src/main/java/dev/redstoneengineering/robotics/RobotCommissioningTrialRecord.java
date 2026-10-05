package dev.redstoneengineering.robotics;

import java.util.Objects;
import java.util.UUID;

/** Frozen AMR mission-run evidence used by the explicit Diagnostic Tablet trial workflow. */
public record RobotCommissioningTrialRecord(
        long sequence,
        long captureTick,
        UUID robotId,
        RobotMissionTelemetrySnapshot telemetry
) {
    public RobotCommissioningTrialRecord {
        if (sequence < 1) throw new IllegalArgumentException("sequence must be >= 1");
        if (captureTick < 0) throw new IllegalArgumentException("captureTick must be >= 0");
        Objects.requireNonNull(robotId, "robotId");
        Objects.requireNonNull(telemetry, "telemetry");
        if (!telemetry.finished()) throw new IllegalArgumentException("trial evidence must be a finished mission run");
    }

    public String compact() {
        return "#" + sequence + " AMR-" + robotId + " " + telemetry.compact();
    }
}
