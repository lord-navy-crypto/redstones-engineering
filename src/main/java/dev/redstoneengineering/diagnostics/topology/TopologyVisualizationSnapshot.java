package dev.redstoneengineering.diagnostics.topology;

import java.util.List;

/** Read-only all-face topology projection used by Jade and engineering diagnostics. */
public record TopologyVisualizationSnapshot(
        List<TopologyFaceSnapshot> faces,
        int portCount,
        int connectedCount,
        int issueCount
) {
    public TopologyVisualizationSnapshot {
        faces = List.copyOf(faces);
        if (portCount < 0 || connectedCount < 0 || issueCount < 0) {
            throw new IllegalArgumentException("topology counts must be non-negative");
        }
    }

    public static TopologyVisualizationSnapshot empty() {
        return new TopologyVisualizationSnapshot(List.of(), 0, 0, 0);
    }

    public String summary() {
        return "ports=" + portCount + " | connected=" + connectedCount + " | issues=" + issueCount;
    }
}
