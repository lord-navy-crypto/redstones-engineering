package dev.redstoneengineering.block;

import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.instrument.InstrumentNetwork;

/** Shared read-only instrument coverage and shielding evidence; no extra scan. */
public final class InstrumentCableEvidenceSupport {
    private InstrumentCableEvidenceSupport() {}
    public static EngineeringPortSnapshot snapshot(EngineeringPort port,
                                                     InstrumentNetwork.ProbeSnapshot scan) {
        return MeterEvidenceSupport.snapshot(port, scan.validChannelsInMask(0xF), 4,
                scan.qualityForMask(0xF));
    }
    public static String interference(InstrumentNetwork.ProbeSnapshot scan) {
        return "shielding=" + scan.shieldingIntegrity()
                + " " + scan.shieldingCoveragePercent() + "%"
                + " | interference=" + scan.interferenceIntegrity()
                + " exposure=" + scan.interferenceExposurePercent() + "%"
                + " confidence=" + scan.interferenceConfidencePercent() + "%";
    }
}
