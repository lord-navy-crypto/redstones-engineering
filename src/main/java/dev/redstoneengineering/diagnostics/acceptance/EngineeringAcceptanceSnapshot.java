package dev.redstoneengineering.diagnostics.acceptance;

import dev.redstoneengineering.diagnostics.CommissioningStatus;

import java.util.List;

/**
 * Immutable acceptance projection assembled from existing authoritative diagnostic snapshots.
 * This record is evidence only; it cannot drive simulation, topology, or controller state.
 */
public record EngineeringAcceptanceSnapshot(
        EngineeringAcceptanceStatus status,
        int topologyPorts,
        int connectedPorts,
        int topologyIssues,
        CommissioningStatus commissioningStatus,
        int commissioningScore,
        List<EngineeringAcceptanceIssue> issues
) {
    public EngineeringAcceptanceSnapshot {
        if (status == null) throw new IllegalArgumentException("acceptance status is required");
        if (commissioningStatus == null) throw new IllegalArgumentException("commissioning status is required");
        if (topologyPorts < 0 || connectedPorts < 0 || topologyIssues < 0) {
            throw new IllegalArgumentException("topology counts must be non-negative");
        }
        if (connectedPorts > topologyPorts) throw new IllegalArgumentException("connected ports cannot exceed port count");
        if (commissioningScore < 0 || commissioningScore > 100) {
            throw new IllegalArgumentException("commissioning score must be 0..100");
        }
        issues = List.copyOf(issues == null ? List.of() : issues);
    }

    public boolean ready() {
        return status != EngineeringAcceptanceStatus.NOT_READY;
    }

    public boolean accepted() {
        return status == EngineeringAcceptanceStatus.PASS;
    }

    /** Deterministic compact trace suitable for logs, HUDs, and regression assertions. */
    public String traceKey() {
        return "topology=" + connectedPorts + "/" + topologyPorts + ":issues=" + topologyIssues
                + "|commissioning=" + commissioningStatus + ":score=" + commissioningScore
                + "|acceptance=" + status;
    }

    public String summary() {
        return status + " | connected=" + connectedPorts + "/" + topologyPorts
                + " | topologyIssues=" + topologyIssues
                + " | commissioning=" + commissioningStatus + "(" + commissioningScore + ")"
                + " | issues=" + issues.size();
    }
}
