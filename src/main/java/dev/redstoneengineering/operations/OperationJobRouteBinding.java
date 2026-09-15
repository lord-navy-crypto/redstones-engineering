package dev.redstoneengineering.operations;

import java.util.Set;
import java.util.TreeSet;

/** Immutable routing/precedence contract for one operation job. */
public record OperationJobRouteBinding(
        long jobId,
        String routeId,
        int stepIndex,
        Set<Long> predecessorJobIds
) {
    public OperationJobRouteBinding {
        if (jobId < 0) throw new IllegalArgumentException("jobId must be non-negative");
        if (routeId == null || routeId.isBlank()) throw new IllegalArgumentException("routeId must be non-blank");
        routeId = routeId.trim();
        if (stepIndex < 0) throw new IllegalArgumentException("stepIndex must be non-negative");
        TreeSet<Long> normalized = new TreeSet<>();
        if (predecessorJobIds != null) {
            for (Long predecessor : predecessorJobIds) {
                if (predecessor == null || predecessor < 0) {
                    throw new IllegalArgumentException("predecessor job ids must be non-negative");
                }
                if (predecessor == jobId) {
                    throw new IllegalArgumentException("job cannot depend on itself");
                }
                normalized.add(predecessor);
            }
        }
        predecessorJobIds = Set.copyOf(normalized);
    }

    public boolean predecessorsSatisfied(Set<Long> completedJobIds) {
        return completedJobIds != null && completedJobIds.containsAll(predecessorJobIds);
    }
}
