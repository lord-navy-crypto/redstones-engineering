package dev.redstoneengineering.integration;

import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.operations.OperationOutputSnapshot;
import dev.redstoneengineering.operations.OperationTransportDemand;
import dev.redstoneengineering.robotics.RobotDockAssessment;
import dev.redstoneengineering.robotics.RobotDockSnapshot;
import dev.redstoneengineering.robotics.RobotLocalizationQuality;
import dev.redstoneengineering.robotics.RobotMaterialTransferAssessment;
import dev.redstoneengineering.robotics.RobotMaterialTransferSnapshot;
import dev.redstoneengineering.robotics.RobotMission;
import dev.redstoneengineering.robotics.RobotMissionTelemetrySnapshot;
import dev.redstoneengineering.robotics.RobotOperatingState;
import dev.redstoneengineering.robotics.RobotPayloadSnapshot;

/**
 * Pure end-to-end acceptance evaluator for one Operations→AMR material-flow mission.
 *
 * <p>The evaluator only correlates already-authoritative evidence. It never selects a robot,
 * plans a route, admits a dock, moves inventory, changes robot state, or issues safety commands.</p>
 */
public final class OperationsRobotMaterialFlowAcceptance {
    private OperationsRobotMaterialFlowAcceptance() {}

    public enum Stage {
        DISPATCH,
        CORRELATION,
        SOURCE_DOCK,
        LOAD_TRANSFER,
        PAYLOAD,
        TARGET_DOCK,
        UNLOAD_TRANSFER,
        MISSION,
        COMPLETE
    }

    public enum Verdict {
        PASS,
        MARGINAL,
        WAIT,
        SAFE_STOP,
        FAULT
    }

    public record Snapshot(
            Verdict verdict,
            Stage stage,
            String reason,
            long missionId,
            long outputId,
            long jobId,
            int units,
            String robotId,
            int operationalHolds,
            int obstacleWaits,
            RobotLocalizationQuality worstLocalization
    ) {
        public Snapshot {
            if (verdict == null) verdict = Verdict.SAFE_STOP;
            if (stage == null) stage = Stage.CORRELATION;
            if (reason == null || reason.isBlank()) reason = "UNSPECIFIED";
            if (missionId < -1 || outputId < -1 || jobId < -1) {
                throw new IllegalArgumentException("trace ids must be >= -1");
            }
            units = Math.max(0, units);
            robotId = robotId == null ? "" : robotId.trim();
            operationalHolds = Math.max(0, operationalHolds);
            obstacleWaits = Math.max(0, obstacleWaits);
            if (worstLocalization == null) worstLocalization = RobotLocalizationQuality.LOST;
        }

        public boolean accepted() {
            return verdict == Verdict.PASS || verdict == Verdict.MARGINAL;
        }

        public String traceKey() {
            return "mission=" + missionId
                    + "|output=" + outputId
                    + "|job=" + jobId
                    + "|units=" + units
                    + "|robot=" + (robotId.isBlank() ? "UNKNOWN" : robotId)
                    + "|stage=" + stage
                    + "|verdict=" + verdict
                    + "|reason=" + reason;
        }
    }

    public static Snapshot inspect(
            OperationOutputSnapshot output,
            OperationTransportDemand demand,
            OperationTransportBinding binding,
            RobotMission mission,
            String robotId,
            RobotDockSnapshot sourceDock,
            RobotMaterialTransferSnapshot loadTransfer,
            RobotPayloadSnapshot loadedPayload,
            RobotDockSnapshot targetDock,
            RobotMaterialTransferSnapshot unloadTransfer,
            RobotMissionTelemetrySnapshot telemetry
    ) {
        OperationsRobotTransportBridge.Decision dispatch = OperationsRobotTransportBridge.evaluate(output, demand);
        if (!dispatch.ready()) {
            return fromDispatch(dispatch, output, demand, robotId);
        }

        OperationTransportBinding expected = dispatch.binding();
        if (binding == null) {
            return stop(Stage.CORRELATION, "TRANSPORT_BINDING_MISSING", expected, robotId);
        }
        if (!expected.equals(binding) || !binding.matches(output, demand)) {
            return stop(Stage.CORRELATION, "TRANSPORT_BINDING_MISMATCH", expected, robotId);
        }
        if (mission == null || !binding.matches(mission) || !dispatch.mission().equals(mission)) {
            return stop(Stage.CORRELATION, "ROBOT_MISSION_MISMATCH", binding, robotId);
        }
        if (robotId == null || robotId.isBlank()) {
            return stop(Stage.CORRELATION, "ROBOT_IDENTITY_MISSING", binding, robotId);
        }

        Snapshot sourceDockGate = dockGate(Stage.SOURCE_DOCK, binding, sourceDock, binding.source(), robotId);
        if (sourceDockGate != null) return sourceDockGate;

        if (loadTransfer == null) {
            return stop(Stage.LOAD_TRANSFER, "LOAD_TRANSFER_EVIDENCE_MISSING", binding, robotId);
        }
        if (!binding.loadTransferId().equals(loadTransfer.transferId())) {
            return stop(Stage.LOAD_TRANSFER, "LOAD_TRANSFER_CORRELATION_MISMATCH", binding, robotId);
        }
        if (loadTransfer.requestedUnits() != binding.units()) {
            return stop(Stage.LOAD_TRANSFER, "LOAD_TRANSFER_QUANTITY_MISMATCH", binding, robotId);
        }
        RobotMaterialTransferAssessment.Snapshot load = RobotMaterialTransferAssessment.inspect(
                loadTransfer, sourceDock.dockId(), robotId);
        Snapshot loadGate = materialGate(Stage.LOAD_TRANSFER, binding, robotId, load);
        if (loadGate != null) return loadGate;

        Snapshot payloadGate = payloadGate(binding, robotId, loadedPayload);
        if (payloadGate != null) return payloadGate;

        Snapshot targetDockGate = dockGate(Stage.TARGET_DOCK, binding, targetDock, binding.target(), robotId);
        if (targetDockGate != null) return targetDockGate;

        if (unloadTransfer == null) {
            return stop(Stage.UNLOAD_TRANSFER, "UNLOAD_TRANSFER_EVIDENCE_MISSING", binding, robotId);
        }
        if (!binding.unloadTransferId().equals(unloadTransfer.transferId())) {
            return stop(Stage.UNLOAD_TRANSFER, "UNLOAD_TRANSFER_CORRELATION_MISMATCH", binding, robotId);
        }
        if (unloadTransfer.requestedUnits() != binding.units()) {
            return stop(Stage.UNLOAD_TRANSFER, "UNLOAD_TRANSFER_QUANTITY_MISMATCH", binding, robotId);
        }
        RobotMaterialTransferAssessment.Snapshot unload = RobotMaterialTransferAssessment.inspect(
                unloadTransfer, targetDock.dockId(), robotId);
        Snapshot unloadGate = materialGate(Stage.UNLOAD_TRANSFER, binding, robotId, unload);
        if (unloadGate != null) return unloadGate;

        return missionGate(binding, robotId, telemetry);
    }

    private static Snapshot fromDispatch(
            OperationsRobotTransportBridge.Decision dispatch,
            OperationOutputSnapshot output,
            OperationTransportDemand demand,
            String robotId
    ) {
        long missionId = demand == null ? -1 : demand.missionId();
        long outputId = output == null ? (demand == null ? -1 : demand.outputId()) : output.outputId();
        long jobId = output == null ? -1 : output.jobId();
        int units = demand == null ? (output == null ? 0 : output.units()) : demand.units();
        return new Snapshot(
                switch (dispatch.verdict()) {
                    case MISSION_READY -> Verdict.SAFE_STOP;
                    case WAIT -> Verdict.WAIT;
                    case SAFE_STOP -> Verdict.SAFE_STOP;
                    case FAULT -> Verdict.FAULT;
                },
                Stage.DISPATCH,
                dispatch.reason(),
                missionId,
                outputId,
                jobId,
                units,
                robotId,
                0,
                0,
                RobotLocalizationQuality.LOST
        );
    }

    private static Snapshot dockGate(
            Stage stage,
            OperationTransportBinding binding,
            RobotDockSnapshot dock,
            net.minecraft.core.BlockPos expectedPosition,
            String robotId
    ) {
        if (dock == null) return stop(stage, "DOCK_EVIDENCE_MISSING", binding, robotId);
        if (!dock.position().equals(expectedPosition)) {
            return stop(stage, "DOCK_POSITION_MISMATCH", binding, robotId);
        }
        RobotDockAssessment.Snapshot assessment =
                RobotDockAssessment.inspect(dock, robotId, RobotDockAssessment.Phase.TRANSFER);
        return switch (assessment.verdict()) {
            case PERMIT -> null;
            case WAIT -> waitAt(stage, assessment.reason(), binding, robotId);
            case SAFE_STOP -> stop(stage, assessment.reason(), binding, robotId);
            case FAULT -> fault(stage, assessment.reason(), binding, robotId);
        };
    }

    private static Snapshot materialGate(
            Stage stage,
            OperationTransportBinding binding,
            String robotId,
            RobotMaterialTransferAssessment.Snapshot assessment
    ) {
        return switch (assessment.verdict()) {
            case COMPLETE -> null;
            case WAIT -> waitAt(stage, assessment.reason(), binding, robotId);
            case SAFE_STOP -> stop(stage, assessment.reason(), binding, robotId);
            case FAULT -> fault(stage, assessment.reason(), binding, robotId);
        };
    }

    private static Snapshot payloadGate(
            OperationTransportBinding binding,
            String robotId,
            RobotPayloadSnapshot payload
    ) {
        if (payload == null) return stop(Stage.PAYLOAD, "PAYLOAD_EVIDENCE_MISSING", binding, robotId);
        if (payload.faultActive()) return fault(Stage.PAYLOAD, "PAYLOAD_FAULT_ACTIVE", binding, robotId);
        if (payload.evidenceQuality() != PortQuality.VALID) {
            return stop(Stage.PAYLOAD, "PAYLOAD_EVIDENCE_" + qualityName(payload.evidenceQuality()), binding, robotId);
        }
        if (!binding.payloadId().equals(payload.payloadId())) {
            return stop(Stage.PAYLOAD, "PAYLOAD_CORRELATION_MISMATCH", binding, robotId);
        }
        if (!robotId.trim().equals(payload.robotId())) {
            return stop(Stage.PAYLOAD, "PAYLOAD_ROBOT_MISMATCH", binding, robotId);
        }
        if (payload.units() != binding.units()) {
            return stop(Stage.PAYLOAD, "PAYLOAD_QUANTITY_MISMATCH", binding, robotId);
        }
        if (!payload.secured()) return waitAt(Stage.PAYLOAD, "PAYLOAD_NOT_SECURED", binding, robotId);
        return null;
    }

    private static Snapshot missionGate(
            OperationTransportBinding binding,
            String robotId,
            RobotMissionTelemetrySnapshot telemetry
    ) {
        if (telemetry == null || !telemetry.started()) {
            return waitAt(Stage.MISSION, "MISSION_TELEMETRY_NOT_STARTED", binding, robotId);
        }
        if (!telemetry.finished()) {
            return waitAt(Stage.MISSION, "MISSION_TELEMETRY_RUNNING", binding, robotId);
        }
        if (telemetry.finalTarget() == null || !telemetry.finalTarget().equals(binding.target())) {
            return stop(Stage.MISSION, "MISSION_FINAL_TARGET_MISMATCH", binding, robotId);
        }
        if (telemetry.faultEvents() > 0 || telemetry.terminalState() == RobotOperatingState.FAULT) {
            return fault(Stage.MISSION, "MISSION_TERMINAL_FAULT", binding, robotId);
        }
        if (!telemetry.completed() || telemetry.terminalState() != RobotOperatingState.COMPLETE) {
            return stop(Stage.MISSION, "MISSION_NOT_COMPLETE", binding, robotId);
        }

        int operationalHolds = telemetry.degradedEntries()
                + telemetry.safeStopEvents()
                + telemetry.routeRejectEvents()
                + telemetry.dockHoldEvents()
                + telemetry.materialHoldEvents();
        boolean marginal = operationalHolds > 0 || telemetry.worstLocalization() != RobotLocalizationQuality.VALID;
        return new Snapshot(
                marginal ? Verdict.MARGINAL : Verdict.PASS,
                Stage.COMPLETE,
                marginal ? "END_TO_END_ACCEPTED_WITH_RECOVERED_HOLDS" : "END_TO_END_ACCEPTED",
                binding.missionId(),
                binding.outputId(),
                binding.jobId(),
                binding.units(),
                robotId,
                operationalHolds,
                telemetry.obstacleWaitEvents(),
                telemetry.worstLocalization()
        );
    }

    private static Snapshot waitAt(Stage stage, String reason, OperationTransportBinding binding, String robotId) {
        return base(Verdict.WAIT, stage, reason, binding, robotId);
    }

    private static Snapshot stop(Stage stage, String reason, OperationTransportBinding binding, String robotId) {
        return base(Verdict.SAFE_STOP, stage, reason, binding, robotId);
    }

    private static Snapshot fault(Stage stage, String reason, OperationTransportBinding binding, String robotId) {
        return base(Verdict.FAULT, stage, reason, binding, robotId);
    }

    private static Snapshot base(
            Verdict verdict,
            Stage stage,
            String reason,
            OperationTransportBinding binding,
            String robotId
    ) {
        return new Snapshot(
                verdict,
                stage,
                reason,
                binding == null ? -1 : binding.missionId(),
                binding == null ? -1 : binding.outputId(),
                binding == null ? -1 : binding.jobId(),
                binding == null ? 0 : binding.units(),
                robotId,
                0,
                0,
                RobotLocalizationQuality.LOST
        );
    }

    private static String qualityName(PortQuality quality) {
        return quality == null ? "MISSING" : quality.name();
    }
}
