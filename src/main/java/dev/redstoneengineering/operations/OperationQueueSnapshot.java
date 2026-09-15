package dev.redstoneengineering.operations;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Immutable bounded Operations queue plus active resource assignments. */
public record OperationQueueSnapshot(
        int capacity,
        List<OperationJob> queued,
        List<OperationAssignment> active
) {
    public static final int MAX_CAPACITY = 64;

    public OperationQueueSnapshot {
        if (capacity <= 0 || capacity > MAX_CAPACITY) {
            throw new IllegalArgumentException("capacity must be in 1.." + MAX_CAPACITY);
        }
        queued = queued == null ? List.of() : List.copyOf(queued);
        active = active == null ? List.of() : List.copyOf(active);
        if (queued.size() > capacity) {
            throw new IllegalArgumentException("queued jobs exceed capacity");
        }

        Set<Long> jobIds = new HashSet<>();
        for (OperationJob job : queued) {
            if (job == null || !jobIds.add(job.jobId())) {
                throw new IllegalArgumentException("queued/active job identities must be unique");
            }
        }

        Set<String> resourceIds = new HashSet<>();
        for (OperationAssignment assignment : active) {
            if (assignment == null || !jobIds.add(assignment.job().jobId())) {
                throw new IllegalArgumentException("queued/active job identities must be unique");
            }
            if (!resourceIds.add(assignment.resourceId())) {
                throw new IllegalArgumentException("one resource cannot own multiple active assignments");
            }
        }
    }

    public static OperationQueueSnapshot empty(int capacity) {
        return new OperationQueueSnapshot(capacity, List.of(), List.of());
    }

    public int wip() {
        return queued.size() + active.size();
    }
}
