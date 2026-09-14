package dev.redstoneengineering.robotics;

import dev.redstoneengineering.core.port.PortQuality;

/**
 * Deterministic motion-permit assessment for mobile robots.
 * The assessment is observer-only: it classifies evidence and never commands motion.
 */
public final class RobotSafetyAssessment {
    private RobotSafetyAssessment() {}

    public enum Verdict {
        PERMIT,
        DEGRADED_HOLD,
        SAFE_STOP,
        FAULT
    }

    public record Input(
            RobotLocalizationQuality localization,
            PortQuality obstacleEvidence,
            boolean obstacleClear,
            boolean driveReady,
            boolean emergencyStopClear
    ) {}

    public record Snapshot(
            Verdict verdict,
            boolean motionPermit,
            String primaryReason
    ) {
        public Snapshot {
            if (verdict == null) verdict = Verdict.SAFE_STOP;
            if (primaryReason == null || primaryReason.isBlank()) primaryReason = "UNSPECIFIED";
            if (verdict != Verdict.PERMIT) motionPermit = false;
        }
    }

    public static Snapshot inspect(Input input) {
        if (input == null) return new Snapshot(Verdict.SAFE_STOP, false, "NO_SAFETY_EVIDENCE");
        if (!input.emergencyStopClear()) return new Snapshot(Verdict.SAFE_STOP, false, "E_STOP_ACTIVE");
        if (!input.driveReady()) return new Snapshot(Verdict.FAULT, false, "DRIVE_NOT_READY");
        if (input.localization() == null || input.localization() == RobotLocalizationQuality.LOST) {
            return new Snapshot(Verdict.SAFE_STOP, false, "LOCALIZATION_LOST");
        }
        if (input.localization() == RobotLocalizationQuality.STALE) {
            return new Snapshot(Verdict.SAFE_STOP, false, "LOCALIZATION_STALE");
        }
        if (input.localization() == RobotLocalizationQuality.DEGRADED) {
            return new Snapshot(Verdict.DEGRADED_HOLD, false, "LOCALIZATION_DEGRADED");
        }
        if (input.obstacleEvidence() != PortQuality.VALID) {
            return new Snapshot(Verdict.SAFE_STOP, false, "OBSTACLE_EVIDENCE_" + qualityName(input.obstacleEvidence()));
        }
        if (!input.obstacleClear()) return new Snapshot(Verdict.SAFE_STOP, false, "OBSTACLE_UNSAFE");
        return new Snapshot(Verdict.PERMIT, true, "MOTION_PERMIT");
    }

    private static String qualityName(PortQuality quality) {
        return quality == null ? "MISSING" : quality.name();
    }
}
