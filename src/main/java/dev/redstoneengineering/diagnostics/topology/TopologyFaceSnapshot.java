package dev.redstoneengineering.diagnostics.topology;

import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import net.minecraft.core.Direction;

/** Immutable projection of one physical face; null port/observation means no descriptor/live sample. */
public record TopologyFaceSnapshot(
        Direction side,
        EngineeringPort port,
        EngineeringPortSnapshot observation,
        TopologyLinkStatus linkStatus,
        String neighborId,
        String detail
) {
    public TopologyFaceSnapshot {
        if (side == null) throw new IllegalArgumentException("side is required");
        if (linkStatus == null) throw new IllegalArgumentException("linkStatus is required");
        neighborId = neighborId == null || neighborId.isBlank() ? "none" : neighborId;
        detail = detail == null ? "" : detail;
    }

    public boolean hasPort() {
        return port != null;
    }

    public boolean hasObservation() {
        return observation != null;
    }

    public boolean topologyIssue() {
        return linkStatus == TopologyLinkStatus.DOMAIN_MISMATCH
                || linkStatus == TopologyLinkStatus.DIRECTION_MISMATCH;
    }

    public String compact() {
        String face = side.getName().toUpperCase(java.util.Locale.ROOT);
        if (port == null) return face + "=ISOLATED";
        StringBuilder out = new StringBuilder(face)
                .append('=')
                .append(port.domain().label())
                .append('/')
                .append(port.direction())
                .append(" → ")
                .append(linkStatus);
        if (observation != null) out.append(" [").append(observation.quality()).append(']');
        return out.toString();
    }
}
