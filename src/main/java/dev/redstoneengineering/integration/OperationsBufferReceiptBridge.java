package dev.redstoneengineering.integration;

import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.operations.OperationBufferReceiptEvidence;
import dev.redstoneengineering.robotics.RobotMaterialTransferSnapshot;
import dev.redstoneengineering.robotics.RobotMaterialUnloadRuntime;
import dev.redstoneengineering.robotics.RobotMission;

/**
 * Pure evidence adapter from a completed Robotics unload into an Industrial Operations buffer receipt.
 * It preserves transport correlation but does not mutate a buffer, inventory, robot, dock, or world.
 */
public final class OperationsBufferReceiptBridge {
    private OperationsBufferReceiptBridge() {}

    public enum Verdict {
        RECEIPT_READY,
        WAIT,
        SAFE_STOP,
        FAULT
    }

    public record Decision(Verdict verdict, String reason, OperationBufferReceiptEvidence receipt) {
        public Decision {
            if (verdict == null) verdict = Verdict.SAFE_STOP;
            if (reason == null || reason.isBlank()) reason = "UNSPECIFIED";
            if (verdict != Verdict.RECEIPT_READY) receipt = null;
        }

        public boolean ready() {
            return verdict == Verdict.RECEIPT_READY && receipt != null;
        }
    }

    public static Decision evaluate(
            OperationTransportBinding binding,
            RobotMission mission,
            RobotMaterialUnloadRuntime.Decision unload,
            RobotMaterialTransferSnapshot transfer,
            String bufferId
    ) {
        if (binding == null) return safeStop("TRANSPORT_BINDING_MISSING");
        if (mission == null) return safeStop("ROBOT_MISSION_MISSING");
        if (unload == null) return safeStop("UNLOAD_DECISION_MISSING");
        if (bufferId == null || bufferId.isBlank()) return safeStop("BUFFER_ID_MISSING");

        if (unload.verdict() == RobotMaterialUnloadRuntime.Verdict.FAULT) {
            return new Decision(Verdict.FAULT, "ROBOT_UNLOAD_FAULT", null);
        }
        if (unload.verdict() == RobotMaterialUnloadRuntime.Verdict.SAFE_STOP) {
            return safeStop("ROBOT_UNLOAD_SAFE_STOP");
        }
        if (unload.verdict() == RobotMaterialUnloadRuntime.Verdict.WAIT) {
            return waitFor("ROBOT_UNLOAD_INCOMPLETE");
        }
        if (!unload.unloadComplete()) return safeStop("ROBOT_UNLOAD_NOT_COMPLETE");

        if (mission.missionId() != binding.missionId()) return safeStop("MISSION_BINDING_MISMATCH");
        if (mission.type() != RobotMission.MissionType.TRANSFER
                && mission.type() != RobotMission.MissionType.DELIVERY) {
            return safeStop("MISSION_NOT_MATERIAL_TRANSPORT");
        }
        if (!mission.source().equals(binding.source())) return safeStop("MISSION_SOURCE_MISMATCH");
        if (!mission.target().equals(binding.target())) return safeStop("MISSION_TARGET_MISMATCH");
        if (mission.payloadUnits() != binding.units()) return safeStop("MISSION_QUANTITY_MISMATCH");

        if (transfer == null) return safeStop("UNLOAD_TRANSFER_EVIDENCE_MISSING");
        if (transfer.faultActive()) return new Decision(Verdict.FAULT, "UNLOAD_TRANSFER_FAULT_ACTIVE", null);
        if (transfer.evidenceQuality() != PortQuality.VALID) return safeStop("UNLOAD_TRANSFER_EVIDENCE_INVALID");
        if (!transfer.sourceConfirmed() || !transfer.destinationConfirmed() || !transfer.completionConfirmed()) {
            return waitFor("UNLOAD_TRANSFER_UNCONFIRMED");
        }
        if (transfer.requestedUnits() != binding.units() || transfer.transferredUnits() != binding.units()) {
            return safeStop("UNLOAD_TRANSFER_QUANTITY_MISMATCH");
        }

        OperationBufferReceiptEvidence receipt = new OperationBufferReceiptEvidence(
                binding.missionId(),
                binding.outputId(),
                binding.jobId(),
                bufferId.trim(),
                binding.target(),
                binding.units(),
                transfer.evidenceQuality(),
                true,
                false
        );
        return new Decision(Verdict.RECEIPT_READY, "BUFFER_RECEIPT_READY", receipt);
    }

    private static Decision waitFor(String reason) {
        return new Decision(Verdict.WAIT, reason, null);
    }

    private static Decision safeStop(String reason) {
        return new Decision(Verdict.SAFE_STOP, reason, null);
    }
}
