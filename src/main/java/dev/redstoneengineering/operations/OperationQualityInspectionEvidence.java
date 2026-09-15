package dev.redstoneengineering.operations;

import dev.redstoneengineering.core.port.PortQuality;

/** Immutable quality-inspection evidence for one completed output lot. */
public record OperationQualityInspectionEvidence(
        long outputId,
        long jobId,
        int inspectedUnits,
        int goodUnits,
        int rejectUnits,
        int reworkUnits,
        PortQuality evidenceQuality,
        boolean inspectionConfirmed,
        boolean faultActive
) {
    public OperationQualityInspectionEvidence {
        if (outputId < 0) throw new IllegalArgumentException("outputId must be non-negative");
        if (jobId < 0) throw new IllegalArgumentException("jobId must be non-negative");
        if (inspectedUnits < 0) throw new IllegalArgumentException("inspectedUnits must be non-negative");
        if (goodUnits < 0 || rejectUnits < 0 || reworkUnits < 0) {
            throw new IllegalArgumentException("quality disposition counts must be non-negative");
        }
        if (evidenceQuality == null) evidenceQuality = PortQuality.NO_SIGNAL;
    }

    public int dispositionUnits() {
        return goodUnits + rejectUnits + reworkUnits;
    }
}
