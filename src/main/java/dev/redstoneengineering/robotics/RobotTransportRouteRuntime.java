package dev.redstoneengineering.robotics;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure post-load transport-route admission contract.
 *
 * <p>This class owns no world mutation. It resolves explicit topology through
 * {@link RobotRoutePlanner}, validates the loaded-payload handoff, and returns
 * immutable waypoints that a world-running AMR may consume.</p>
 */
public final class RobotTransportRouteRuntime {
    private RobotTransportRouteRuntime() {}

    public enum Verdict {
        PERMIT,
        WAIT,
        SAFE_STOP,
        FAULT
    }

    public record Decision(
            Verdict verdict,
            String reason,
            RobotRoutePlanner.Route route,
            List<RobotNavigationGraph.Node> waypoints
    ) {
        public Decision {
            if (verdict == null) verdict = Verdict.FAULT;
            if (reason == null || reason.isBlank()) reason = "TRANSPORT_ROUTE_DECISION_INVALID";
            route = route == null
                    ? new RobotRoutePlanner.Route(RobotRoutePlanner.Status.ROUTE_UNAVAILABLE, List.of(), Double.POSITIVE_INFINITY, "ROUTE_NOT_EVALUATED")
                    : route;
            waypoints = waypoints == null ? List.of() : List.copyOf(waypoints);
            if (verdict != Verdict.PERMIT) waypoints = List.of();
        }

        public boolean permitted() {
            return verdict == Verdict.PERMIT;
        }
    }

    public static Decision evaluate(
            RobotOperatingState current,
            RobotMission mission,
            RobotPayloadSnapshot payload,
            RobotNavigationGraph graph,
            String sourceId,
            String targetId,
            String robotId
    ) {
        if (current != RobotOperatingState.TRANSPORTING) {
            return decision(Verdict.SAFE_STOP, "TRANSPORT_STATE_REQUIRED", null, List.of());
        }

        RobotRoutePlanner.Route route = RobotRoutePlanner.plan(graph, sourceId, targetId);
        RobotTransportHandoffAssessment.Snapshot handoff =
                RobotTransportHandoffAssessment.inspect(current, mission, payload, route, robotId);

        if (handoff.verdict() == RobotTransportHandoffAssessment.Verdict.FAULT) {
            return decision(Verdict.FAULT, handoff.reason(), route, List.of());
        }
        if (handoff.verdict() == RobotTransportHandoffAssessment.Verdict.SAFE_STOP) {
            return decision(Verdict.SAFE_STOP, handoff.reason(), route, List.of());
        }
        if (handoff.verdict() == RobotTransportHandoffAssessment.Verdict.WAIT) {
            return decision(Verdict.WAIT, handoff.reason(), route, List.of());
        }
        if (!route.available() || graph == null) {
            return decision(Verdict.WAIT, route.reason(), route, List.of());
        }

        ArrayList<RobotNavigationGraph.Node> resolved = new ArrayList<>(route.nodeIds().size());
        for (String nodeId : route.nodeIds()) {
            RobotNavigationGraph.Node node = graph.node(nodeId).orElse(null);
            if (node == null) {
                return decision(Verdict.SAFE_STOP, "ROUTE_NODE_EVIDENCE_MISSING", route, List.of());
            }
            resolved.add(node);
        }
        if (resolved.isEmpty()) {
            return decision(Verdict.SAFE_STOP, "EMPTY_TRANSPORT_ROUTE", route, List.of());
        }

        return decision(Verdict.PERMIT, "TRANSPORT_ROUTE_PERMIT", route, resolved);
    }

    private static Decision decision(
            Verdict verdict,
            String reason,
            RobotRoutePlanner.Route route,
            List<RobotNavigationGraph.Node> waypoints
    ) {
        return new Decision(verdict, reason, route, waypoints);
    }
}
