package dev.redstoneengineering.operations;

/** Immutable logical WIP lot retained by an Industrial Operations buffer. */
public record OperationBufferLot(
        long outputId,
        long jobId,
        int units
) {
    public OperationBufferLot {
        if (outputId < 0) throw new IllegalArgumentException("outputId must be non-negative");
        if (jobId < 0) throw new IllegalArgumentException("jobId must be non-negative");
        if (units <= 0) throw new IllegalArgumentException("units must be positive");
    }
}
