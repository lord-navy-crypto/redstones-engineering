package dev.redstoneengineering.operations;

/** Immutable evidence that a specific buffer lot quantity has been reserved for one downstream job. */
public record OperationInputAllocation(
        long downstreamJobId,
        String bufferId,
        long outputId,
        long upstreamJobId,
        int allocatedUnits
) {
    public OperationInputAllocation {
        if (downstreamJobId < 0) throw new IllegalArgumentException("downstreamJobId must be non-negative");
        if (bufferId == null || bufferId.isBlank()) throw new IllegalArgumentException("bufferId must be non-blank");
        bufferId = bufferId.trim();
        if (outputId < 0) throw new IllegalArgumentException("outputId must be non-negative");
        if (upstreamJobId < 0) throw new IllegalArgumentException("upstreamJobId must be non-negative");
        if (allocatedUnits <= 0) throw new IllegalArgumentException("allocatedUnits must be positive");
    }
}
