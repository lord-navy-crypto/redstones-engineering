package dev.redstoneengineering.operations;

import dev.redstoneengineering.core.port.PortQuality;

import java.util.Set;
import java.util.TreeSet;

/** Immutable evidence snapshot of operation jobs whose completion has been confirmed. */
public record OperationCompletionLedger(
        Set<Long> completedJobIds,
        PortQuality evidenceQuality,
        boolean faultActive
) {
    public OperationCompletionLedger {
        TreeSet<Long> normalized = new TreeSet<>();
        if (completedJobIds != null) {
            for (Long jobId : completedJobIds) {
                if (jobId == null || jobId < 0) {
                    throw new IllegalArgumentException("completed job ids must be non-negative");
                }
                normalized.add(jobId);
            }
        }
        completedJobIds = Set.copyOf(normalized);
        if (evidenceQuality == null) evidenceQuality = PortQuality.NO_SIGNAL;
    }

    public boolean completed(long jobId) {
        return completedJobIds.contains(jobId);
    }
}
