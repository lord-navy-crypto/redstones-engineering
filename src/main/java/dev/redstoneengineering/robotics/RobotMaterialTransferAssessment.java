package dev.redstoneengineering.robotics;

import dev.redstoneengineering.core.port.PortQuality;

/** Pure deterministic assessment for completing AMR loading from transfer evidence. */
public final class RobotMaterialTransferAssessment {
    private RobotMaterialTransferAssessment() {}

    public enum Verdict {
        COMPLETE,
        WAIT,
        SAFE_STOP,
        FAULT
    }

    public record Snapshot(Verdict verdict, String reason) {
        public Snapshot {
            if (verdict == null) verdict = Verdict.SAFE_STOP;
            if (reason == null || reason.isBlank()) reason = "UNSPECIFIED";
        }

        public boolean transferComplete() {
            return verdict == Verdict.COMPLETE;
        }
    }

    public static Snapshot inspect(
            RobotMaterialTransferSnapshot transfer,
            String expectedDockId,
            String expectedRobotId
    ) {
        if (transfer == null) return new Snapshot(Verdict.SAFE_STOP, "NO_TRANSFER_EVIDENCE");
        if (expectedDockId == null || expectedDockId.isBlank()) {
            return new Snapshot(Verdict.SAFE_STOP, "EXPECTED_DOCK_ID_MISSING");
        }
        if (expectedRobotId == null || expectedRobotId.isBlank()) {
            return new Snapshot(Verdict.SAFE_STOP, "EXPECTED_ROBOT_ID_MISSING");
        }
        if (transfer.faultActive()) return new Snapshot(Verdict.FAULT, "TRANSFER_FAULT_ACTIVE");
        if (transfer.evidenceQuality() != PortQuality.VALID) {
            return new Snapshot(Verdict.SAFE_STOP, "TRANSFER_EVIDENCE_" + qualityName(transfer.evidenceQuality()));
        }
        if (!expectedDockId.trim().equals(transfer.dockId())) {
            return new Snapshot(Verdict.SAFE_STOP, "TRANSFER_DOCK_MISMATCH");
        }
        if (!expectedRobotId.trim().equals(transfer.robotId())) {
            return new Snapshot(Verdict.SAFE_STOP, "TRANSFER_ROBOT_MISMATCH");
        }
        if (!transfer.sourceConfirmed()) return new Snapshot(Verdict.WAIT, "SOURCE_NOT_CONFIRMED");
        if (!transfer.destinationConfirmed()) return new Snapshot(Verdict.WAIT, "DESTINATION_NOT_CONFIRMED");
        if (!transfer.completionConfirmed()) return new Snapshot(Verdict.WAIT, "TRANSFER_NOT_CONFIRMED_COMPLETE");
        if (!transfer.completeQuantity()) return new Snapshot(Verdict.SAFE_STOP, "TRANSFER_COUNT_MISMATCH");
        return new Snapshot(Verdict.COMPLETE, "TRANSFER_COMPLETE");
    }

    private static String qualityName(PortQuality quality) {
        return quality == null ? "MISSING" : quality.name();
    }
}
