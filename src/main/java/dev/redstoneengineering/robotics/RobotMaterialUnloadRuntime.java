package dev.redstoneengineering.robotics;

import dev.redstoneengineering.core.port.PortQuality;

/** Pure evidence bridge for completing material unloading at the destination. */
public final class RobotMaterialUnloadRuntime {
    private RobotMaterialUnloadRuntime() {}

    public enum Verdict { COMPLETE, WAIT, SAFE_STOP, FAULT }

    public record Decision(
            Verdict verdict,
            RobotOperatingState nextState,
            String dockReason,
            String materialReason,
            String payloadReason
    ) {
        public Decision {
            if (verdict == null) verdict = Verdict.SAFE_STOP;
            if (nextState == null) nextState = RobotOperatingState.SAFE_STOP;
            if (dockReason == null || dockReason.isBlank()) dockReason = "DOCK_NOT_EVALUATED";
            if (materialReason == null || materialReason.isBlank()) materialReason = "TRANSFER_NOT_EVALUATED";
            if (payloadReason == null || payloadReason.isBlank()) payloadReason = "PAYLOAD_NOT_EVALUATED";
        }

        public boolean unloadComplete() {
            return verdict == Verdict.COMPLETE && nextState == RobotOperatingState.COMPLETE;
        }
    }

    public static Decision evaluate(
            RobotOperatingState current,
            RobotMission mission,
            RobotPayloadSnapshot payload,
            RobotDockSnapshot dock,
            RobotMaterialTransferSnapshot transfer,
            String robotId
    ) {
        if (current != RobotOperatingState.UNLOADING) {
            return safeStop(current, "STATE_NOT_UNLOADING", "TRANSFER_NOT_EVALUATED", "PAYLOAD_NOT_EVALUATED");
        }
        if (mission == null) {
            return safeStop(current, "MISSION_MISSING", "TRANSFER_NOT_EVALUATED", "PAYLOAD_NOT_EVALUATED");
        }
        if (mission.type() != RobotMission.MissionType.DELIVERY
                && mission.type() != RobotMission.MissionType.TRANSFER) {
            return safeStop(current, "MISSION_NOT_MATERIAL_TRANSPORT", "TRANSFER_NOT_EVALUATED", "PAYLOAD_NOT_EVALUATED");
        }
        if (mission.payloadUnits() <= 0) {
            return safeStop(current, "MISSION_PAYLOAD_EMPTY", "TRANSFER_NOT_EVALUATED", "PAYLOAD_NOT_EVALUATED");
        }
        if (robotId == null || robotId.isBlank()) {
            return safeStop(current, "EXPECTED_ROBOT_ID_MISSING", "TRANSFER_NOT_EVALUATED", "PAYLOAD_NOT_EVALUATED");
        }

        Decision payloadDecision = assessPayload(current, mission, payload, robotId);
        if (payloadDecision != null) return payloadDecision;

        RobotDockAssessment.Snapshot dockAssessment =
                RobotDockAssessment.inspect(dock, robotId, RobotDockAssessment.Phase.TRANSFER);
        if (dockAssessment.verdict() == RobotDockAssessment.Verdict.FAULT) {
            return fault(current, dockAssessment.reason(), "TRANSFER_NOT_EVALUATED", "PAYLOAD_VALID");
        }
        if (dockAssessment.verdict() == RobotDockAssessment.Verdict.SAFE_STOP) {
            return safeStop(current, dockAssessment.reason(), "TRANSFER_NOT_EVALUATED", "PAYLOAD_VALID");
        }
        if (dockAssessment.verdict() == RobotDockAssessment.Verdict.WAIT) {
            return waitAt(current, dockAssessment.reason(), "TRANSFER_NOT_EVALUATED", "PAYLOAD_VALID");
        }

        String dockId = dock == null ? null : dock.dockId();
        RobotMaterialTransferAssessment.Snapshot material =
                RobotMaterialTransferAssessment.inspect(transfer, dockId, robotId);
        if (material.verdict() == RobotMaterialTransferAssessment.Verdict.FAULT) {
            return fault(current, dockAssessment.reason(), material.reason(), "PAYLOAD_VALID");
        }
        if (material.verdict() == RobotMaterialTransferAssessment.Verdict.SAFE_STOP) {
            return safeStop(current, dockAssessment.reason(), material.reason(), "PAYLOAD_VALID");
        }
        if (material.verdict() == RobotMaterialTransferAssessment.Verdict.WAIT) {
            return waitAt(current, dockAssessment.reason(), material.reason(), "PAYLOAD_VALID");
        }
        if (transfer == null || transfer.requestedUnits() != mission.payloadUnits()
                || transfer.requestedUnits() != payload.units()) {
            return safeStop(current, dockAssessment.reason(), "UNLOAD_QUANTITY_MISMATCH", "PAYLOAD_VALID");
        }

        RobotOperatingState next = RobotStateMachine.next(current, RobotStateMachine.Event.UNLOAD_COMPLETE);
        if (next != RobotOperatingState.COMPLETE) {
            return fault(current, dockAssessment.reason(), "UNLOAD_TRANSITION_REJECTED", "PAYLOAD_VALID");
        }
        return new Decision(Verdict.COMPLETE, next, dockAssessment.reason(), material.reason(), "PAYLOAD_VALID");
    }

    private static Decision assessPayload(
            RobotOperatingState current,
            RobotMission mission,
            RobotPayloadSnapshot payload,
            String robotId
    ) {
        if (payload == null) return safeStop(current, "DOCK_NOT_EVALUATED", "TRANSFER_NOT_EVALUATED", "PAYLOAD_EVIDENCE_MISSING");
        if (payload.faultActive()) return fault(current, "DOCK_NOT_EVALUATED", "TRANSFER_NOT_EVALUATED", "PAYLOAD_FAULT_ACTIVE");
        if (payload.evidenceQuality() != PortQuality.VALID) {
            return safeStop(current, "DOCK_NOT_EVALUATED", "TRANSFER_NOT_EVALUATED",
                    "PAYLOAD_EVIDENCE_" + qualityName(payload.evidenceQuality()));
        }
        if (!robotId.trim().equals(payload.robotId())) {
            return safeStop(current, "DOCK_NOT_EVALUATED", "TRANSFER_NOT_EVALUATED", "PAYLOAD_ROBOT_MISMATCH");
        }
        if (payload.units() != mission.payloadUnits()) {
            return safeStop(current, "DOCK_NOT_EVALUATED", "TRANSFER_NOT_EVALUATED", "PAYLOAD_COUNT_MISMATCH");
        }
        return null;
    }

    private static Decision waitAt(RobotOperatingState current, String dock, String material, String payload) {
        return new Decision(Verdict.WAIT, current, dock, material, payload);
    }

    private static Decision safeStop(RobotOperatingState current, String dock, String material, String payload) {
        return new Decision(
                Verdict.SAFE_STOP,
                RobotStateMachine.next(current, RobotStateMachine.Event.SAFETY_STOP_REQUESTED),
                dock,
                material,
                payload
        );
    }

    private static Decision fault(RobotOperatingState current, String dock, String material, String payload) {
        return new Decision(
                Verdict.FAULT,
                RobotStateMachine.next(current, RobotStateMachine.Event.CRITICAL_FAULT),
                dock,
                material,
                payload
        );
    }

    private static String qualityName(PortQuality quality) {
        return quality == null ? "MISSING" : quality.name();
    }
}
