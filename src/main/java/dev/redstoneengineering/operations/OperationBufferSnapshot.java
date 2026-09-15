package dev.redstoneengineering.operations;

import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Immutable bounded logical WIP buffer owned by Industrial Operations. */
public record OperationBufferSnapshot(
        String bufferId,
        BlockPos location,
        int capacityUnits,
        List<OperationBufferLot> lots
) {
    public static final int MAX_CAPACITY_UNITS = 4096;

    public OperationBufferSnapshot {
        if (bufferId == null || bufferId.isBlank()) throw new IllegalArgumentException("bufferId must be non-blank");
        bufferId = bufferId.trim();
        if (location == null) throw new IllegalArgumentException("location is required");
        if (capacityUnits <= 0 || capacityUnits > MAX_CAPACITY_UNITS) {
            throw new IllegalArgumentException("capacityUnits must be in 1.." + MAX_CAPACITY_UNITS);
        }
        lots = lots == null ? List.of() : List.copyOf(lots);
        Set<Long> outputs = new HashSet<>();
        int used = 0;
        for (OperationBufferLot lot : lots) {
            if (lot == null) throw new IllegalArgumentException("buffer lots cannot contain null");
            if (!outputs.add(lot.outputId())) throw new IllegalArgumentException("buffer output identities must be unique");
            used = Math.addExact(used, lot.units());
        }
        if (used > capacityUnits) throw new IllegalArgumentException("buffer WIP exceeds capacity");
    }

    public int usedUnits() {
        int used = 0;
        for (OperationBufferLot lot : lots) used += lot.units();
        return used;
    }

    public int availableUnits() {
        return capacityUnits - usedUnits();
    }

    public boolean containsOutput(long outputId) {
        for (OperationBufferLot lot : lots) if (lot.outputId() == outputId) return true;
        return false;
    }
}
