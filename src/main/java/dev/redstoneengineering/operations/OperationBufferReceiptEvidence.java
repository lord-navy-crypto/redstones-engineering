package dev.redstoneengineering.operations;

import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;

/** Immutable evidence that one transported output has been unloaded at an Operations buffer. */
public record OperationBufferReceiptEvidence(
        long missionId,
        long outputId,
        long jobId,
        String bufferId,
        BlockPos bufferLocation,
        int units,
        PortQuality evidenceQuality,
        boolean unloadConfirmed,
        boolean faultActive
) {
    public OperationBufferReceiptEvidence {
        if (missionId < 0) throw new IllegalArgumentException("missionId must be non-negative");
        if (outputId < 0) throw new IllegalArgumentException("outputId must be non-negative");
        if (jobId < 0) throw new IllegalArgumentException("jobId must be non-negative");
        if (bufferId == null || bufferId.isBlank()) throw new IllegalArgumentException("bufferId must be non-blank");
        bufferId = bufferId.trim();
        if (bufferLocation == null) throw new IllegalArgumentException("bufferLocation is required");
        if (units <= 0) throw new IllegalArgumentException("units must be positive");
        if (evidenceQuality == null) evidenceQuality = PortQuality.NO_SIGNAL;
    }
}
