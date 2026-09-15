package dev.redstoneengineering.diagnostics;

import dev.redstoneengineering.operations.OperationBufferSnapshot;
import dev.redstoneengineering.operations.world.OperationPlantSavedData;
import dev.redstoneengineering.operations.world.OperationWorkcellBinding;
import dev.redstoneengineering.operations.world.OperationWorkcellBufferBinding;
import dev.redstoneengineering.operations.world.OperationWorkcellStore;
import net.minecraft.server.level.ServerLevel;

/**
 * Observer-only projection of server-owned world Operations configuration and WIP.
 *
 * <p>This assessment intentionally does not infer queue depth, dispatch state, quality, reliability,
 * or delivery performance. It reports only evidence that already exists in explicit workcell and
 * Industrial Buffer persistence.</p>
 */
public final class OperationWorldPlantStateAssessment {
    private OperationWorldPlantStateAssessment() {}

    public enum Coverage {
        COMPLETE,
        PARTIAL,
        INVALID
    }

    public record Snapshot(
            Coverage coverage,
            int totalWorkcells,
            int configuredWorkcells,
            int totalBuffers,
            int usedBufferUnits,
            int bufferCapacityUnits,
            int wipPressurePercent,
            int boundResources,
            int validResources,
            int faultResources
    ) {
        public Snapshot {
            if (coverage == null) coverage = Coverage.INVALID;
            totalWorkcells = Math.max(0, totalWorkcells);
            configuredWorkcells = Math.max(0, Math.min(totalWorkcells, configuredWorkcells));
            totalBuffers = Math.max(0, totalBuffers);
            usedBufferUnits = Math.max(0, usedBufferUnits);
            bufferCapacityUnits = Math.max(0, bufferCapacityUnits);
            wipPressurePercent = Math.max(0, Math.min(100, wipPressurePercent));
            boundResources = Math.max(0, boundResources);
            validResources = Math.max(0, Math.min(boundResources, validResources));
            faultResources = Math.max(0, Math.min(boundResources, faultResources));
        }

        public boolean authoritativeWorldEvidence() {
            return coverage != Coverage.INVALID;
        }
    }

    public static Snapshot inspect(ServerLevel level) {
        if (level == null) return invalid();
        OperationPlantSavedData data = OperationPlantSavedData.get(level);
        int totalWorkcells = data.workcells().size();
        int totalBuffers = data.buffers().size();
        if (totalWorkcells == 0 && totalBuffers == 0) return invalid();

        int usedUnits = 0;
        int capacityUnits = 0;
        boolean overflow = false;
        for (OperationBufferSnapshot buffer : data.buffers()) {
            try {
                usedUnits = Math.addExact(usedUnits, buffer.usedUnits());
                capacityUnits = Math.addExact(capacityUnits, buffer.capacityUnits());
            } catch (ArithmeticException ignored) {
                overflow = true;
                break;
            }
        }
        if (overflow) return invalid();

        int configuredWorkcells = 0;
        int boundResources = 0;
        int validResources = 0;
        int faultResources = 0;
        boolean incomplete = false;

        for (OperationWorkcellBinding workcell : data.workcells()) {
            OperationWorkcellBufferBinding buffers = data.workcellBufferBinding(workcell.workcellId());
            if (buffers != null
                    && data.buffer(buffers.inputBufferId()) != null
                    && data.buffer(buffers.outputBufferId()) != null) {
                configuredWorkcells++;
            } else {
                incomplete = true;
            }

            var resolved = OperationWorkcellStore.resolveBoundResources(level, workcell.workcellId());
            boundResources += workcell.resourceBindings().size();
            if (resolved.size() != workcell.resourceBindings().size()) incomplete = true;
            for (OperationWorkcellStore.ResolvedResource resource : resolved) {
                if (resource.valid()) validResources++;
                else incomplete = true;
                if (resource.snapshot() != null && resource.snapshot().faultActive()) faultResources++;
            }
        }

        int pressure = capacityUnits <= 0 ? 0 : (int) Math.min(100L, (long) usedUnits * 100L / capacityUnits);
        Coverage coverage = incomplete ? Coverage.PARTIAL : Coverage.COMPLETE;
        return new Snapshot(coverage, totalWorkcells, configuredWorkcells, totalBuffers,
                usedUnits, capacityUnits, pressure, boundResources, validResources, faultResources);
    }

    private static Snapshot invalid() {
        return new Snapshot(Coverage.INVALID, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
