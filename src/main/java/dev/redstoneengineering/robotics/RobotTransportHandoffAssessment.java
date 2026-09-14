package dev.redstoneengineering.robotics;

import dev.redstoneengineering.core.port.PortQuality;

/**
 * Pure deterministic handoff gate from completed loading into transport work.
 * It validates payload evidence against the assigned mission and requires an
 * explicit available route before transport may proceed.
 */
public final class RobotTransportHandoffAssessment {
    private RobotTransportHandoffAssessment() {}

    public enum Verdict {
        PERMIT,
        WAIT,
        SAFE_STOP,
        FAULT
    }

    public record Snapshot(Verdict verdict, String reason) {
        public Snapshot {
            if (verdict == null) verdict = Verdict.SAFE_STOP;
            if (reason == null || reason.isBlank()) reason = "UNSPECIFIED";
        }

        public boolean permitted() {
            return verdict == Verdict.PERMIT;
        }
    }

    public static Snapshot inspect(
            RobotOperatingState current,
            RobotMission mission,
            RobotPayloadSnapshot payload,
            RobotRoutePlanner.Route route,
            String expectedRobotId
    ) {
        if (current != RobotOperatingState.TRANSPORTING) {
            return new Snapshot(Verdict.SAFE_STOP, "STATE_NOT_TRANSPORTING");
        }
        if (mission == null) return new Snapshot(Verdict.SAFE_STOP, "MISSION_MISSING");
        if (expectedRobotId == null || expectedRobotId.isBlank()) {
            return new Snapshot(Verdict.SAFE_STOP, "EXPECTED_ROBOT_ID_MISSING");
        }
        if (mission.type() != RobotMission.MissionType.DELIVERY
                && mission.type() != RobotMission.MissionType.TRANSFER) {
            return new Snapshot(Verdict.SAFE_STOP, "MISSION_NOT_MATERIAL_TRANSPORT");
        }
        if (mission.payloadUnits() <= 0) {
            return new Snapshot(Verdict.SAFE_STOP, "MISSION_PAYLOAD_EMPTY");
        }
        if (payload == null) return new Snapshot(Verdict.SAFE_STOP, "PAYLOAD_EVIDENCE_MISSING");
        if (payload.faultActive()) return new Snapshot(Verdict.FAULT, "PAYLOAD_FAULT_ACTIVE");
        if (payload.evidenceQuality() != PortQuality.VALID) {
            return new Snapshot(Verdict.SAFE_STOP, "PAYLOAD_EVIDENCE_" + qualityName(payload.evidenceQuality()));
        }
        if (!expectedRobotId.trim().equals(payload.robotId())) {
            return new Snapshot(Verdict.SAFE_STOP, "PAYLOAD_ROBOT_MISMATCH");
        }
        if (payload.units() != mission.payloadUnits()) {
            return new Snapshot(Verdict.SAFE_STOP, "PAYLOAD_COUNT_MISMATCH");
        }
        if (!payload.secured()) return new Snapshot(Verdict.WAIT, "PAYLOAD_NOT_SECURED");
        if (route == null) return new Snapshot(Verdict.SAFE_STOP, "TRANSPORT_ROUTE_EVIDENCE_MISSING");
        if (!route.available()) return new Snapshot(Verdict.WAIT, "TRANSPORT_ROUTE_UNAVAILABLE");
        return new Snapshot(Verdict.PERMIT, "TRANSPORT_HANDOFF_PERMIT");
    }

    private static String qualityName(PortQuality quality) {
        return quality == null ? "MISSING" : quality.name();
    }
}
