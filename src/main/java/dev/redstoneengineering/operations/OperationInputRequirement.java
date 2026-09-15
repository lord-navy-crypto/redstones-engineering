package dev.redstoneengineering.operations;

/** Immutable material requirement that gates release of one downstream operation job. */
public record OperationInputRequirement(
        long downstreamJobId,
        String bufferId,
        long outputId,
        int requiredUnits
) {
    public OperationInputRequirement {
        if (downstreamJobId < 0) throw new IllegalArgumentException("downstreamJobId must be non-negative");
        if (bufferId == null || bufferId.isBlank()) throw new IllegalArgumentException("bufferId must be non-blank");
        bufferId = bufferId.trim();
        if (outputId < 0) throw new IllegalArgumentException("outputId must be non-negative");
        if (requiredUnits <= 0) throw new IllegalArgumentException("requiredUnits must be positive");
    }
}
