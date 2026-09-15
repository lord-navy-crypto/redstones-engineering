package dev.redstoneengineering.operations.world;

import dev.redstoneengineering.core.port.PortQuality;

import java.util.Map;
import java.util.Set;

/**
 * Immutable server-observed Operations view of one explicitly addressed world resource.
 * Native machine/control logic remains authoritative; this record only carries evidence.
 */
public record OperationWorldResourceSnapshot(
        String resourceId,
        Set<String> processCapabilities,
        boolean available,
        boolean running,
        boolean completionEvidenceAvailable,
        boolean faultActive,
        PortQuality evidenceQuality,
        Map<String, Long> numericEvidence
) {
    public OperationWorldResourceSnapshot {
        resourceId = resourceId == null ? "" : resourceId.trim();
        processCapabilities = processCapabilities == null ? Set.of() : Set.copyOf(processCapabilities);
        evidenceQuality = evidenceQuality == null ? PortQuality.FAULT : evidenceQuality;
        numericEvidence = numericEvidence == null ? Map.of() : Map.copyOf(numericEvidence);

        // Contradictory runtime evidence must never be projected as healthy/usable.
        if (running && !available) {
            evidenceQuality = PortQuality.FAULT;
        }
        if (faultActive && running) {
            evidenceQuality = PortQuality.FAULT;
        }
    }

    /** True only when the snapshot is sufficiently identified and trustworthy for admission decisions. */
    public boolean validEvidence() {
        return !resourceId.isBlank()
                && !processCapabilities.isEmpty()
                && evidenceQuality == PortQuality.VALID
                && !(running && !available)
                && !(faultActive && running);
    }

    public boolean healthy() {
        return validEvidence() && !faultActive;
    }
}
