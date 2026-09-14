package dev.redstoneengineering.robotics;

import dev.redstoneengineering.core.port.PortQuality;

/** Robotics-facing localization quality; never silently upgrades missing evidence. */
public enum RobotLocalizationQuality {
    VALID,
    DEGRADED,
    LOST,
    STALE;

    public static RobotLocalizationQuality fromEvidence(PortQuality quality) {
        if (quality == null) return LOST;
        return switch (quality) {
            case VALID -> VALID;
            case STALE -> STALE;
            case SATURATED -> DEGRADED;
            case NO_SIGNAL, FAULT, DOMAIN_MISMATCH, TOPOLOGY_ERROR -> LOST;
        };
    }

    public boolean authoritativeForMotion() {
        return this == VALID;
    }
}
