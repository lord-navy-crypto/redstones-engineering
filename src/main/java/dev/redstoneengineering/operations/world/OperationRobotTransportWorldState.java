package dev.redstoneengineering.operations.world;

import dev.redstoneengineering.entity.EngineeringMobileRobotEntity;
import dev.redstoneengineering.integration.OperationTransportBinding;
import dev.redstoneengineering.integration.OperationsRobotTransportBridge;
import dev.redstoneengineering.operations.OperationOutputSnapshot;
import dev.redstoneengineering.operations.OperationTransportDemand;
import dev.redstoneengineering.robotics.RobotMission;
import dev.redstoneengineering.robotics.RobotNavigationGraph;
import dev.redstoneengineering.robotics.RobotPayloadSnapshot;
import net.minecraft.server.level.ServerLevel;

/**
 * Server-owned Operations commit boundary for AMR transport lifecycle correlation.
 *
 * <p>Robotics remains authoritative for route admission and robot motion. This facade persists only
 * READY/STARTED/terminal transport correlation and durable plant history. It never plans routes,
 * scans for robots, moves entities, or mutates blocks.</p>
 */
public final class OperationRobotTransportWorldState {
    private OperationRobotTransportWorldState() {}

    public enum Verdict {
        READY,
        STARTED,
        DELIVERED,
        WAIT,
        SAFE_STOP,
        FAULT
    }

    public record Decision(
            Verdict verdict,
            String reason,
            OperationTransportRuntimeRecord record
    ) {
        public Decision {
            if (verdict == null) verdict = Verdict.SAFE_STOP;
            if (reason == null || reason.isBlank()) reason = "UNSPECIFIED";
        }

        public boolean changed() {
            return verdict == Verdict.READY || verdict == Verdict.STARTED || verdict == Verdict.DELIVERED;
        }
    }

    public static Decision prepare(
            ServerLevel level,
            OperationOutputSnapshot output,
            OperationTransportDemand demand,
            long gameTick
    ) {
        if (level == null) return safeStop("SERVER_LEVEL_MISSING", null);
        if (gameTick < 0) return safeStop("GAME_TICK_INVALID", null);

        OperationsRobotTransportBridge.Decision bridgeDecision = OperationsRobotTransportBridge.evaluate(output, demand);
        if (!bridgeDecision.ready()) {
            return switch (bridgeDecision.verdict()) {
                case WAIT -> waitFor(bridgeDecision.reason(), null);
                case FAULT -> fault(bridgeDecision.reason(), null);
                case SAFE_STOP, MISSION_READY -> safeStop(bridgeDecision.reason(), null);
            };
        }

        OperationPlantSavedData data = OperationPlantSavedData.get(level);
        if (data.transportRecord(demand.missionId()) != null) {
            return safeStop("TRANSPORT_MISSION_ALREADY_EXISTS", data.transportRecord(demand.missionId()));
        }
        for (OperationTransportRuntimeRecord existing : data.transportRecords()) {
            if (!existing.terminal() && existing.outputId() == output.outputId()) {
                return safeStop("OUTPUT_ALREADY_HAS_ACTIVE_TRANSPORT", existing);
            }
        }

        final OperationTransportRuntimeRecord record;
        try {
            record = OperationTransportRuntimeRecord.ready(bridgeDecision.binding(), demand, gameTick);
        } catch (IllegalArgumentException ex) {
            return safeStop("TRANSPORT_CORRELATION_INVALID", null);
        }
        if (!data.putTransportRecord(record)) {
            return safeStop("TRANSPORT_PERSISTENCE_REJECTED", null);
        }

        boolean demandLogged = OperationPlantRuntimeRecorder.recordLogisticsDemand(level, demand, output.jobId(), gameTick);
        boolean readyLogged = demandLogged && OperationPlantRuntimeRecorder.recordLogisticsEvent(
                level,
                record.missionId(),
                record.outputId(),
                record.jobId(),
                gameTick,
                "MISSION_READY",
                "source=" + record.source().asLong()
                        + " target=" + record.target().asLong()
                        + " units=" + record.units()
                        + " priority=" + record.priority()
        );
        if (!readyLogged) {
            if (!data.removeTransportRecord(record.missionId())) {
                throw new IllegalStateException("TRANSPORT_HISTORY_REJECTED_AND_READY_ROLLBACK_FAILED");
            }
            throw new IllegalStateException("TRANSPORT_HISTORY_REJECTED");
        }
        return new Decision(Verdict.READY, "MISSION_READY", record);
    }

    public static Decision startRoute(
            ServerLevel level,
            EngineeringMobileRobotEntity robot,
            long missionId,
            RobotPayloadSnapshot payload,
            RobotNavigationGraph graph,
            String sourceId,
            String targetId,
            long gameTick
    ) {
        if (level == null) return safeStop("SERVER_LEVEL_MISSING", null);
        if (robot == null) return safeStop("ROBOT_MISSING", null);
        if (robot.level() != level) return safeStop("ROBOT_LEVEL_MISMATCH", null);
        if (gameTick < 0) return safeStop("GAME_TICK_INVALID", null);

        OperationPlantSavedData data = OperationPlantSavedData.get(level);
        OperationTransportRuntimeRecord record = data.transportRecord(missionId);
        if (record == null) return safeStop("TRANSPORT_MISSION_NOT_FOUND", null);
        if (record.status() != OperationTransportRuntimeRecord.Status.READY) {
            return waitFor("TRANSPORT_MISSION_NOT_READY", record);
        }

        OperationTransportBinding binding = record.binding();
        RobotMission mission = new RobotMission(
                binding.missionId(),
                RobotMission.MissionType.TRANSFER,
                binding.source(),
                binding.target(),
                record.priority(),
                binding.units()
        );
        if (!robot.assignTransportRoute(mission, payload, graph, sourceId, targetId)) {
            return waitFor(robot.routeReason(), record);
        }

        final OperationTransportRuntimeRecord started;
        try {
            started = record.transition(OperationTransportRuntimeRecord.Status.STARTED, gameTick, robot.robotIdentity());
        } catch (IllegalArgumentException ex) {
            return safeStop("TRANSPORT_START_TRANSITION_INVALID", record);
        }
        if (!data.putTransportRecord(started)) {
            throw new IllegalStateException("TRANSPORT_START_PERSISTENCE_REJECTED_AFTER_ROBOT_ROUTE_COMMIT");
        }
        if (!OperationPlantRuntimeRecorder.recordLogisticsEvent(
                level,
                started.missionId(),
                started.outputId(),
                started.jobId(),
                gameTick,
                "MISSION_STARTED",
                "robot=" + started.robotId() + " route=" + robot.routeReason()
        )) {
            // The robot route already committed. Keep STARTED authoritative rather than lying by rollback.
            throw new IllegalStateException("TRANSPORT_START_HISTORY_REJECTED_AFTER_ROBOT_ROUTE_COMMIT");
        }
        return new Decision(Verdict.STARTED, "MISSION_STARTED", started);
    }

    public static Decision markDelivered(
            ServerLevel level,
            long missionId,
            long outputId,
            long jobId,
            long gameTick
    ) {
        if (level == null) return safeStop("SERVER_LEVEL_MISSING", null);
        if (missionId < 0 || outputId < 0 || jobId < 0 || gameTick < 0) {
            return safeStop("DELIVERY_IDENTITY_INVALID", null);
        }
        OperationPlantSavedData data = OperationPlantSavedData.get(level);
        OperationTransportRuntimeRecord record = data.transportRecord(missionId);
        if (record == null) return safeStop("TRANSPORT_MISSION_NOT_FOUND", null);
        OperationTransportBinding binding = record.binding();
        if (binding.outputId() != outputId) return safeStop("TRANSPORT_OUTPUT_ID_MISMATCH", record);
        if (binding.jobId() != jobId) return safeStop("TRANSPORT_JOB_ID_MISMATCH", record);
        if (record.status() != OperationTransportRuntimeRecord.Status.STARTED) {
            return waitFor("TRANSPORT_MISSION_NOT_STARTED", record);
        }

        final OperationTransportRuntimeRecord delivered;
        try {
            delivered = record.transition(OperationTransportRuntimeRecord.Status.DELIVERED, gameTick, record.robotId());
        } catch (IllegalArgumentException ex) {
            return safeStop("TRANSPORT_DELIVERY_TRANSITION_INVALID", record);
        }
        if (!data.putTransportRecord(delivered)) {
            return safeStop("TRANSPORT_DELIVERY_PERSISTENCE_REJECTED", record);
        }
        if (!OperationPlantRuntimeRecorder.recordLogisticsEvent(
                level,
                delivered.missionId(),
                delivered.outputId(),
                delivered.jobId(),
                gameTick,
                "DELIVERED",
                "robot=" + delivered.robotId()
                        + " target=" + delivered.target().asLong()
                        + " units=" + delivered.units()
        )) {
            // Delivery evidence is already accepted at the world boundary. Preserve terminal state.
            throw new IllegalStateException("TRANSPORT_DELIVERY_HISTORY_REJECTED_AFTER_STATE_COMMIT");
        }
        return new Decision(Verdict.DELIVERED, "DELIVERED", delivered);
    }

    private static Decision waitFor(String reason, OperationTransportRuntimeRecord record) {
        return new Decision(Verdict.WAIT, reason, record);
    }

    private static Decision safeStop(String reason, OperationTransportRuntimeRecord record) {
        return new Decision(Verdict.SAFE_STOP, reason, record);
    }

    private static Decision fault(String reason, OperationTransportRuntimeRecord record) {
        return new Decision(Verdict.FAULT, reason, record);
    }
}
