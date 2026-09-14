package dev.redstoneengineering.robotics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/** Deterministic shortest-path planner over an explicit {@link RobotNavigationGraph}. */
public final class RobotRoutePlanner {
    private RobotRoutePlanner() {}

    public enum Status {
        ROUTE_AVAILABLE,
        GRAPH_MISSING,
        SOURCE_UNKNOWN,
        TARGET_UNKNOWN,
        ROUTE_UNAVAILABLE
    }

    public record Route(Status status, List<String> nodeIds, double totalCost, String reason) {
        public Route {
            if (status == null) status = Status.ROUTE_UNAVAILABLE;
            nodeIds = nodeIds == null ? List.of() : List.copyOf(nodeIds);
            if (!Double.isFinite(totalCost) || totalCost < 0.0D) totalCost = Double.POSITIVE_INFINITY;
            if (reason == null || reason.isBlank()) reason = status.name();
            if (status != Status.ROUTE_AVAILABLE) {
                nodeIds = List.of();
                totalCost = Double.POSITIVE_INFINITY;
            }
        }

        public boolean available() {
            return status == Status.ROUTE_AVAILABLE;
        }
    }

    private record QueueEntry(String nodeId, double distance) {}

    private static final Comparator<QueueEntry> QUEUE_ORDER = Comparator
            .comparingDouble(QueueEntry::distance)
            .thenComparing(QueueEntry::nodeId);

    public static Route plan(RobotNavigationGraph graph, String sourceId, String targetId) {
        if (graph == null) return unavailable(Status.GRAPH_MISSING, "GRAPH_MISSING");
        if (!graph.containsNode(sourceId)) return unavailable(Status.SOURCE_UNKNOWN, "SOURCE_UNKNOWN");
        if (!graph.containsNode(targetId)) return unavailable(Status.TARGET_UNKNOWN, "TARGET_UNKNOWN");
        if (sourceId.equals(targetId)) {
            return new Route(Status.ROUTE_AVAILABLE, List.of(sourceId), 0.0D, "SOURCE_IS_TARGET");
        }

        Map<String, Double> distance = new HashMap<>();
        Map<String, String> previous = new HashMap<>();
        PriorityQueue<QueueEntry> frontier = new PriorityQueue<>(QUEUE_ORDER);
        distance.put(sourceId, 0.0D);
        frontier.add(new QueueEntry(sourceId, 0.0D));

        while (!frontier.isEmpty()) {
            QueueEntry current = frontier.poll();
            double bestKnown = distance.getOrDefault(current.nodeId(), Double.POSITIVE_INFINITY);
            if (Double.compare(current.distance(), bestKnown) != 0) continue;
            if (current.nodeId().equals(targetId)) break;

            for (RobotNavigationGraph.Edge edge : graph.outgoing(current.nodeId())) {
                if (!edge.enabled()) continue;
                double candidate = current.distance() + edge.cost();
                double known = distance.getOrDefault(edge.toId(), Double.POSITIVE_INFINITY);
                if (candidate < known) {
                    distance.put(edge.toId(), candidate);
                    previous.put(edge.toId(), current.nodeId());
                    frontier.add(new QueueEntry(edge.toId(), candidate));
                }
            }
        }

        Double total = distance.get(targetId);
        if (total == null || !Double.isFinite(total)) {
            return unavailable(Status.ROUTE_UNAVAILABLE, "NO_EXPLICIT_ROUTE");
        }

        ArrayDeque<String> reversed = new ArrayDeque<>();
        String cursor = targetId;
        reversed.addFirst(cursor);
        while (!cursor.equals(sourceId)) {
            cursor = previous.get(cursor);
            if (cursor == null) return unavailable(Status.ROUTE_UNAVAILABLE, "INCOMPLETE_ROUTE_EVIDENCE");
            reversed.addFirst(cursor);
        }

        return new Route(Status.ROUTE_AVAILABLE, new ArrayList<>(reversed), total, "SHORTEST_EXPLICIT_ROUTE");
    }

    private static Route unavailable(Status status, String reason) {
        return new Route(status, List.of(), Double.POSITIVE_INFINITY, reason);
    }
}
