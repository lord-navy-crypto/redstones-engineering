package dev.redstoneengineering.diagnostics.topology;

import dev.redstoneengineering.core.port.PortQuality;

/**
 * Read-only explanation layer over EngineeringTopologyView.
 * It never solves or mutates a network; it only summarizes the authoritative port projection.
 */
public record TopologyDiagnosticsReport(
        int portCount,
        int connectedCount,
        int openCount,
        int mismatchCount,
        int unloadedCount,
        int faultSampleCount,
        boolean disconnectedIsland
) {
    public static TopologyDiagnosticsReport from(TopologyVisualizationSnapshot snapshot) {
        int open = 0;
        int mismatch = 0;
        int unloaded = 0;
        int faults = 0;
        for (TopologyFaceSnapshot face : snapshot.faces()) {
            if (!face.hasPort()) continue;
            switch (face.linkStatus()) {
                case OPEN -> open++;
                case DOMAIN_MISMATCH, DIRECTION_MISMATCH -> mismatch++;
                case UNLOADED -> unloaded++;
                default -> { }
            }
            if (face.observation() != null && face.observation().quality() == PortQuality.FAULT) faults++;
        }
        boolean disconnected = snapshot.portCount() > 0 && snapshot.connectedCount() == 0;
        return new TopologyDiagnosticsReport(snapshot.portCount(), snapshot.connectedCount(), open, mismatch, unloaded, faults, disconnected);
    }

    public boolean hasIssue() {
        return mismatchCount > 0 || unloadedCount > 0 || faultSampleCount > 0 || disconnectedIsland || openCount > 0;
    }

    public int issueCount() {
        return openCount + mismatchCount + unloadedCount + faultSampleCount + (disconnectedIsland ? 1 : 0);
    }

    public String summary() {
        if (portCount == 0) return "NO ENGINEERING PORTS | target is not an RSE port device";
        if (mismatchCount > 0) return "TOPOLOGY FAIL | incompatible domain/direction links=" + mismatchCount;
        if (faultSampleCount > 0) return "TOPOLOGY FAIL | runtime FAULT samples=" + faultSampleCount;
        if (unloadedCount > 0) return "TOPOLOGY UNKNOWN | unloaded neighbors=" + unloadedCount;
        if (disconnectedIsland) return "TOPOLOGY OPEN | device has no compatible connected ports";
        if (openCount > 0) return "TOPOLOGY OPEN | dangling ports=" + openCount + " | connected=" + connectedCount;
        return "TOPOLOGY OK | ports=" + portCount + " | connected=" + connectedCount;
    }
}
