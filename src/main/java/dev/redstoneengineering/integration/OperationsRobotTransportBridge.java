package dev.redstoneengineering.integration;

import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.operations.OperationOutputSnapshot;
import dev.redstoneengineering.operations.OperationTransportDemand;
import dev.redstoneengineering.robotics.RobotMission;

/**
 * Pure boundary adapter from Industrial Operations logistics demand into a Robotics mission.
 *
 * <p>Operations owns the demand and completed-output evidence. Robotics still owns robot selection,
 * routing, docking, safety, loading, transport, and unloading. This bridge never mutates either side.</p>
 */
public final class OperationsRobotTransportBridge {
    private OperationsRobotTransportBridge() {}

    public enum Verdict {
        MISSION_READY,
        WAIT,
        SAFE_STOP,
        FAULT
    }

    public record Decision(
            Verdict verdict,
            String reason,
            RobotMission mission,
            OperationTransportBinding binding
    ) {
        public Decision {
            if (verdict == null) verdict = Verdict.SAFE_STOP;
            if (reason == null || reason.isBlank()) reason = "UNSPECIFIED";
            if (verdict != Verdict.MISSION_READY) {
                mission = null;
                binding = null;
            }
        }

        public boolean ready() {
            return verdict == Verdict.MISSION_READY && mission != null && binding != null;
        }
    }

    public static Decision evaluate(OperationOutputSnapshot output, OperationTransportDemand demand) {
        if (output == null) return safeStop("OUTPUT_EVIDENCE_MISSING");
        if (demand == null) return safeStop("TRANSPORT_DEMAND_MISSING");
        if (output.faultActive()) return new Decision(Verdict.FAULT, "OUTPUT_FAULT_ACTIVE", null, null);
        if (output.evidenceQuality() != PortQuality.VALID) {
            return safeStop("OUTPUT_EVIDENCE_INVALID");
        }
        if (!output.completionConfirmed()) return waitFor("OUTPUT_COMPLETION_UNCONFIRMED");
        if (!output.materialReady()) return waitFor("MATERIAL_NOT_READY");
        if (output.outputId() != demand.outputId()) return safeStop("OUTPUT_ID_MISMATCH");
        if (!output.source().equals(demand.source())) return safeStop("SOURCE_IDENTITY_MISMATCH");
        if (output.units() != demand.units()) return safeStop("TRANSPORT_QUANTITY_MISMATCH");
        if (demand.source().equals(demand.target())) return waitFor("TRANSPORT_NOT_REQUIRED");

        RobotMission mission = new RobotMission(
                demand.missionId(),
                RobotMission.MissionType.TRANSFER,
                demand.source(),
                demand.target(),
                demand.priority(),
                demand.units()
        );
        OperationTransportBinding binding = new OperationTransportBinding(
                demand.missionId(),
                output.outputId(),
                output.jobId(),
                demand.source(),
                demand.target(),
                demand.units()
        );
        return new Decision(Verdict.MISSION_READY, "TRANSPORT_MISSION_READY", mission, binding);
    }

    private static Decision waitFor(String reason) {
        return new Decision(Verdict.WAIT, reason, null, null);
    }

    private static Decision safeStop(String reason) {
        return new Decision(Verdict.SAFE_STOP, reason, null, null);
    }
}
