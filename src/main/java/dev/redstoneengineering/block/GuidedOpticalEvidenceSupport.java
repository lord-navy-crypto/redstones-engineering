package dev.redstoneengineering.block;

import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortQuality;

/** Guided optical terminal evidence only; never alters fiber propagation. */
public final class GuidedOpticalEvidenceSupport {
    private GuidedOpticalEvidenceSupport() {}
    public static EngineeringPortSnapshot source(EngineeringPort port, int configuredIntensity) {
        // A configured zero-intensity emitter remains an observable valid source setting.
        return new EngineeringPortSnapshot(port, Math.max(0, Math.min(15, configuredIntensity)),
                0.0, 15.0, PortQuality.VALID);
    }
    public static PortQuality sinkQuality(int inputCount, int driverCount,
                                          PortQuality stored, boolean valid) {
        if (inputCount > 1 || driverCount > 1) return PortQuality.TOPOLOGY_ERROR;
        if (stored == PortQuality.STALE) return PortQuality.STALE;
        return valid ? PortQuality.VALID : PortQuality.NO_SIGNAL;
    }
    public static EngineeringPortSnapshot sink(EngineeringPort port, int intensity, PortQuality quality) {
        return MeterEvidenceSupport.snapshot(port, intensity, 15, quality);
    }
}
