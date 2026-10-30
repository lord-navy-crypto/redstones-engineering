package dev.redstoneengineering.block;

import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortQuality;

/** Pure observer-side gating. A stale or conflicted measurement is not a live number. */
public final class MeterEvidenceSupport {
    private MeterEvidenceSupport() {}
    public static boolean usable(PortQuality quality) {
        return quality == PortQuality.VALID || quality == PortQuality.SATURATED;
    }
    public static int readable(int value, int maximum, PortQuality quality) {
        return usable(quality) ? Math.max(0, Math.min(maximum, value)) : 0;
    }
    public static EngineeringPortSnapshot snapshot(EngineeringPort port, int value,
                                                    int maximum, PortQuality quality) {
        return new EngineeringPortSnapshot(port, readable(value, maximum, quality),
                0.0, maximum, quality);
    }
    /** A topology alarm may be physically asserted even when its target is unobservable. */
    public static EngineeringPortSnapshot alarmSnapshot(EngineeringPort port, int value,
                                                         PortQuality quality) {
        return EngineeringPortSnapshot.redstone(port, value, quality);
    }
}
