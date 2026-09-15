package dev.redstoneengineering.operations;

import dev.redstoneengineering.core.port.PortQuality;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable finite-capacity evidence for one production workcell.
 *
 * <p>A workcell is a logical group of single-assignment resources. This record does not
 * dispatch jobs or infer production completion. It only states bounded resource, input-WIP,
 * and output-WIP capacity so downstream diagnostics can distinguish starvation, saturation,
 * and blocking without guessing from dashboard KPIs.</p>
 */
public record OperationWorkcellCapacitySnapshot(
        String workcellId,
        Set<String> resourceIds,
        int activeAssignments,
        int queuedJobs,
        int inputCapacityUnits,
        int inputWipUnits,
        int outputCapacityUnits,
        int outputWipUnits,
        PortQuality evidenceQuality,
        boolean faultActive
) {
    public static final int MAX_RESOURCE_COUNT = 64;
    public static final int MAX_BUFFER_UNITS = 4096;
    public static final int MAX_QUEUE_JOBS = 4096;

    public OperationWorkcellCapacitySnapshot {
        if (workcellId == null || workcellId.isBlank()) {
            throw new IllegalArgumentException("workcellId must be non-blank");
        }
        workcellId = workcellId.trim();
        if (resourceIds == null || resourceIds.isEmpty()) {
            throw new IllegalArgumentException("resourceIds must not be empty");
        }
        if (resourceIds.size() > MAX_RESOURCE_COUNT) {
            throw new IllegalArgumentException("resourceIds exceed bounded workcell capacity");
        }
        resourceIds = resourceIds.stream().map(id -> {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("resource id must be non-blank");
            }
            return id.trim();
        }).collect(Collectors.toUnmodifiableSet());
        if (resourceIds.isEmpty()) {
            throw new IllegalArgumentException("resourceIds must contain identities");
        }
        if (activeAssignments < 0 || activeAssignments > resourceIds.size()) {
            throw new IllegalArgumentException("activeAssignments must fit resource capacity");
        }
        if (queuedJobs < 0 || queuedJobs > MAX_QUEUE_JOBS) {
            throw new IllegalArgumentException("queuedJobs outside bounded range");
        }
        if (inputCapacityUnits < 0 || inputCapacityUnits > MAX_BUFFER_UNITS
                || outputCapacityUnits < 0 || outputCapacityUnits > MAX_BUFFER_UNITS) {
            throw new IllegalArgumentException("buffer capacity outside bounded range");
        }
        if (inputWipUnits < 0 || inputWipUnits > inputCapacityUnits) {
            throw new IllegalArgumentException("input WIP exceeds input capacity");
        }
        if (outputWipUnits < 0 || outputWipUnits > outputCapacityUnits) {
            throw new IllegalArgumentException("output WIP exceeds output capacity");
        }
        if (evidenceQuality == null) evidenceQuality = PortQuality.NO_SIGNAL;
    }

    public int resourceCapacity() {
        return resourceIds.size();
    }

    public int availableResourceSlots() {
        return resourceCapacity() - activeAssignments;
    }

    public boolean resourcesSaturated() {
        return activeAssignments == resourceCapacity();
    }

    public boolean inputStarved() {
        return inputCapacityUnits > 0 && inputWipUnits == 0;
    }

    public boolean outputBlocked() {
        return outputCapacityUnits > 0 && outputWipUnits == outputCapacityUnits;
    }

    public int resourceUtilizationPercent() {
        return Math.round(activeAssignments * 100.0F / resourceCapacity());
    }

    public int inputWipPressurePercent() {
        return inputCapacityUnits == 0 ? 0 : Math.round(inputWipUnits * 100.0F / inputCapacityUnits);
    }

    public int outputWipPressurePercent() {
        return outputCapacityUnits == 0 ? 0 : Math.round(outputWipUnits * 100.0F / outputCapacityUnits);
    }
}
