package dev.redstoneengineering.block;

import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.InformationRuntime;

/**
 * Pure byte/bit endpoint evidence shared by serial and differential converters.
 *
 * A retained payload is not a valid output unless the current upstream medium
 * AND the converter's authoritative output envelope are both valid.
 */
public final class DigitalEndpointEvidenceSupport {
    private DigitalEndpointEvidenceSupport() {}

    public static PortQuality outputQuality(
            PortQuality upstream, InformationRuntime.Snapshot output) {
        if (upstream != PortQuality.VALID) return upstream;
        if (output.ageTicks() < 0 || !output.valid()) return PortQuality.STALE;
        return PortQuality.VALID;
    }

    public static int valueWhenValid(
            PortQuality quality, InformationRuntime.Snapshot envelope, int mask) {
        return quality == PortQuality.VALID && envelope.valid()
                ? envelope.value() & mask : 0;
    }
}
