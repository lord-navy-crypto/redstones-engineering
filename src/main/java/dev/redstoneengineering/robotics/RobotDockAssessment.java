package dev.redstoneengineering.robotics;

import dev.redstoneengineering.core.port.PortQuality;

/** Pure deterministic admission/handshake assessment for an AMR dock. */
public final class RobotDockAssessment {
    private RobotDockAssessment() {}

    public enum Phase {
        APPROACH,
        DOCK,
        TRANSFER
    }

    public enum Verdict {
        PERMIT,
        WAIT,
        SAFE_STOP,
        FAULT
    }

    public record Snapshot(Verdict verdict, Phase phase, String reason) {
        public Snapshot {
            if (verdict == null) verdict = Verdict.SAFE_STOP;
            if (phase == null) phase = Phase.APPROACH;
            if (reason == null || reason.isBlank()) reason = "UNSPECIFIED";
        }

        public boolean permitted() {
            return verdict == Verdict.PERMIT;
        }
    }

    public static Snapshot inspect(RobotDockSnapshot dock, String requestingRobotId, Phase phase) {
        Phase requestedPhase = phase == null ? Phase.APPROACH : phase;
        if (dock == null) return new Snapshot(Verdict.SAFE_STOP, requestedPhase, "NO_DOCK_EVIDENCE");
        if (requestingRobotId == null || requestingRobotId.isBlank()) {
            return new Snapshot(Verdict.SAFE_STOP, requestedPhase, "ROBOT_IDENTITY_MISSING");
        }
        if (dock.faultActive()) return new Snapshot(Verdict.FAULT, requestedPhase, "DOCK_FAULT_ACTIVE");
        if (!dock.emergencyStopClear()) return new Snapshot(Verdict.SAFE_STOP, requestedPhase, "DOCK_E_STOP_ACTIVE");
        if (dock.evidenceQuality() != PortQuality.VALID) {
            return new Snapshot(Verdict.SAFE_STOP, requestedPhase, "DOCK_EVIDENCE_" + qualityName(dock.evidenceQuality()));
        }
        if (dock.reservedByOther(requestingRobotId)) {
            return new Snapshot(Verdict.WAIT, requestedPhase, "DOCK_RESERVED_FOR_OTHER");
        }
        if (dock.occupiedByOther(requestingRobotId)) {
            return new Snapshot(Verdict.WAIT, requestedPhase, "DOCK_OCCUPIED_BY_OTHER");
        }

        return switch (requestedPhase) {
            case APPROACH -> dock.approachClear()
                    ? new Snapshot(Verdict.PERMIT, requestedPhase, "APPROACH_PERMIT")
                    : new Snapshot(Verdict.WAIT, requestedPhase, "APPROACH_BLOCKED");
            case DOCK -> {
                if (!dock.approachClear()) yield new Snapshot(Verdict.WAIT, requestedPhase, "APPROACH_BLOCKED");
                if (!dock.alignmentReady()) yield new Snapshot(Verdict.WAIT, requestedPhase, "ALIGNMENT_NOT_READY");
                yield new Snapshot(Verdict.PERMIT, requestedPhase, "DOCK_PERMIT");
            }
            case TRANSFER -> {
                if (!dock.occupiedBy(requestingRobotId)) {
                    yield new Snapshot(Verdict.SAFE_STOP, requestedPhase, "ROBOT_NOT_CONFIRMED_DOCKED");
                }
                if (!dock.transferReady()) yield new Snapshot(Verdict.WAIT, requestedPhase, "TRANSFER_NOT_READY");
                yield new Snapshot(Verdict.PERMIT, requestedPhase, "TRANSFER_PERMIT");
            }
        };
    }

    private static String qualityName(PortQuality quality) {
        return quality == null ? "MISSING" : quality.name();
    }
}
