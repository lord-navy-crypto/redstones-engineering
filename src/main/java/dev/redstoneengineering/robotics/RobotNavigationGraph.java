package dev.redstoneengineering.robotics;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Explicit, immutable AMR navigation topology.
 *
 * <p>The graph contains only declared nodes and directed edges. It never scans
 * the world, discovers neighbors by radius, or force-loads chunks. Runtime
 * systems may build a graph from authoritative infrastructure evidence, but
 * route planning consumes only this snapshot.</p>
 */
public final class RobotNavigationGraph {
    public record Node(String id, BlockPos position) {
        public Node {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("navigation node id must be non-blank");
            if (position == null) throw new IllegalArgumentException("navigation node position is required");
            id = id.trim();
            position = position.immutable();
        }
    }

    public record Edge(String fromId, String toId, double cost, boolean enabled) {
        public Edge {
            if (fromId == null || fromId.isBlank()) throw new IllegalArgumentException("edge source id must be non-blank");
            if (toId == null || toId.isBlank()) throw new IllegalArgumentException("edge target id must be non-blank");
            fromId = fromId.trim();
            toId = toId.trim();
            if (fromId.equals(toId)) throw new IllegalArgumentException("navigation self-edge is not allowed: " + fromId);
            if (!Double.isFinite(cost) || cost <= 0.0D) throw new IllegalArgumentException("edge cost must be finite and positive");
        }
    }

    private static final Comparator<Edge> EDGE_ORDER = Comparator
            .comparing(Edge::toId)
            .thenComparingDouble(Edge::cost)
            .thenComparing(Edge::fromId);

    private final Map<String, Node> nodes;
    private final Map<String, List<Edge>> outgoing;

    public RobotNavigationGraph(Collection<Node> nodes, Collection<Edge> edges) {
        if (nodes == null) throw new IllegalArgumentException("navigation nodes are required");
        if (edges == null) throw new IllegalArgumentException("navigation edges are required");

        TreeMap<String, Node> nodeMap = new TreeMap<>();
        for (Node node : nodes) {
            if (node == null) throw new IllegalArgumentException("navigation node cannot be null");
            if (nodeMap.putIfAbsent(node.id(), node) != null) {
                throw new IllegalArgumentException("duplicate navigation node id: " + node.id());
            }
        }

        TreeMap<String, List<Edge>> adjacency = new TreeMap<>();
        for (String id : nodeMap.keySet()) adjacency.put(id, new ArrayList<>());

        for (Edge edge : edges) {
            if (edge == null) throw new IllegalArgumentException("navigation edge cannot be null");
            if (!nodeMap.containsKey(edge.fromId())) {
                throw new IllegalArgumentException("edge references unknown source node: " + edge.fromId());
            }
            if (!nodeMap.containsKey(edge.toId())) {
                throw new IllegalArgumentException("edge references unknown target node: " + edge.toId());
            }
            adjacency.get(edge.fromId()).add(edge);
        }

        TreeMap<String, List<Edge>> frozenAdjacency = new TreeMap<>();
        for (Map.Entry<String, List<Edge>> entry : adjacency.entrySet()) {
            entry.getValue().sort(EDGE_ORDER);
            frozenAdjacency.put(entry.getKey(), List.copyOf(entry.getValue()));
        }

        this.nodes = Collections.unmodifiableMap(new TreeMap<>(nodeMap));
        this.outgoing = Collections.unmodifiableMap(frozenAdjacency);
    }

    public Optional<Node> node(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(nodes.get(id));
    }

    public List<Node> nodes() {
        return List.copyOf(nodes.values());
    }

    public List<Edge> outgoing(String nodeId) {
        if (nodeId == null) return List.of();
        return outgoing.getOrDefault(nodeId, List.of());
    }

    public boolean containsNode(String nodeId) {
        return nodeId != null && nodes.containsKey(nodeId);
    }

    public int nodeCount() {
        return nodes.size();
    }
}
