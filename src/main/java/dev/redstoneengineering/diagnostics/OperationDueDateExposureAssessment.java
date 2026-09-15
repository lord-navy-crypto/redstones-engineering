package dev.redstoneengineering.diagnostics;

import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.operations.OperationCompletionLedger;
import dev.redstoneengineering.operations.OperationJob;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Observer-only due-date exposure over current jobs and explicit completion evidence.
 *
 * <p>This class does not claim on-time delivery performance because the current completion ledger
 * does not retain completion timestamps. It only reports whether unfinished released work is
 * currently past its explicit due tick.</p>
 */
public final class OperationDueDateExposureAssessment {
    private OperationDueDateExposureAssessment() {}

    public enum Coverage {
        COMPLETE,
        PARTIAL,
        INVALID
    }

    public record Snapshot(
            Coverage coverage,
            int observedJobs,
            int completedJobs,
            int outstandingJobs,
            int outstandingWithDueDate,
            int overdueOutstandingJobs,
            int notYetDueOutstandingJobs,
            int undatedOutstandingJobs
    ) {
        public Snapshot {
            if (coverage == null) coverage = Coverage.INVALID;
            observedJobs = Math.max(0, observedJobs);
            completedJobs = Math.max(0, completedJobs);
            outstandingJobs = Math.max(0, outstandingJobs);
            outstandingWithDueDate = Math.max(0, outstandingWithDueDate);
            overdueOutstandingJobs = Math.max(0, overdueOutstandingJobs);
            notYetDueOutstandingJobs = Math.max(0, notYetDueOutstandingJobs);
            undatedOutstandingJobs = Math.max(0, undatedOutstandingJobs);
        }

        public boolean overdueWorkPresent() {
            return overdueOutstandingJobs > 0;
        }
    }

    public static Snapshot inspect(
            Collection<OperationJob> jobs,
            OperationCompletionLedger completionLedger,
            long gameTick
    ) {
        if (gameTick < 0) {
            return empty(Coverage.INVALID);
        }
        if (jobs == null || completionLedger == null) {
            return empty(Coverage.INVALID);
        }
        if (completionLedger.faultActive() || completionLedger.evidenceQuality() != PortQuality.VALID) {
            return empty(Coverage.INVALID);
        }
        if (jobs.isEmpty()) {
            return empty(Coverage.COMPLETE);
        }

        Set<Long> identities = new HashSet<>();
        int observed = 0;
        int completed = 0;
        int outstanding = 0;
        int withDueDate = 0;
        int overdue = 0;
        int notYetDue = 0;
        int undated = 0;
        boolean invalidJobEvidence = false;

        for (OperationJob job : jobs) {
            if (job == null || !identities.add(job.jobId())) {
                invalidJobEvidence = true;
                continue;
            }
            observed++;
            if (completionLedger.completed(job.jobId())) {
                completed++;
                continue;
            }
            outstanding++;
            if (!job.hasDueDate()) {
                undated++;
                continue;
            }
            withDueDate++;
            if (job.releasedAt(gameTick) && gameTick > job.dueTick()) {
                overdue++;
            } else {
                notYetDue++;
            }
        }

        Coverage coverage = invalidJobEvidence ? Coverage.INVALID : Coverage.COMPLETE;
        return new Snapshot(
                coverage,
                observed,
                completed,
                outstanding,
                withDueDate,
                overdue,
                notYetDue,
                undated
        );
    }

    private static Snapshot empty(Coverage coverage) {
        return new Snapshot(coverage, 0, 0, 0, 0, 0, 0, 0);
    }
}
