package dev.redstoneengineering.operations;

/**
 * Pure bridge from a confirmed quality disposition into an explicitly planned rework job.
 *
 * <p>This class never invents a rework process, job identity, priority, or due date. Those remain
 * explicit planning inputs supplied by OperationReworkDemand. It also does not enqueue the job.</p>
 */
public final class OperationReworkReleaseAssessment {
    private OperationReworkReleaseAssessment() {}

    public enum Verdict {
        JOB_READY,
        WAIT,
        SAFE_STOP
    }

    public record Decision(Verdict verdict, String reason, OperationJob job) {
        public Decision {
            if (verdict == null) verdict = Verdict.SAFE_STOP;
            if (reason == null || reason.isBlank()) reason = "UNSPECIFIED";
            if (verdict != Verdict.JOB_READY) job = null;
        }

        public boolean ready() {
            return verdict == Verdict.JOB_READY && job != null;
        }
    }

    public static Decision evaluate(
            OperationQualityDispositionAssessment.Snapshot quality,
            OperationReworkDemand demand
    ) {
        if (quality == null) return safeStop("QUALITY_DISPOSITION_MISSING");
        if (demand == null) return safeStop("REWORK_DEMAND_MISSING");
        if (quality.verdict() == OperationQualityDispositionAssessment.Verdict.WAIT) {
            return waitFor("QUALITY_DISPOSITION_INCOMPLETE");
        }
        if (quality.verdict() == OperationQualityDispositionAssessment.Verdict.FAULT) {
            return safeStop("QUALITY_DISPOSITION_FAULTED");
        }
        if (quality.verdict() != OperationQualityDispositionAssessment.Verdict.COMPLETE) {
            return safeStop("QUALITY_DISPOSITION_INVALID");
        }
        if (quality.reworkUnits() <= 0) return waitFor("NO_REWORK_UNITS");
        if (quality.outputId() != demand.sourceOutputId()) return safeStop("REWORK_OUTPUT_ID_MISMATCH");
        if (quality.jobId() != demand.sourceJobId()) return safeStop("REWORK_SOURCE_JOB_MISMATCH");
        if (quality.reworkUnits() != demand.units()) return safeStop("REWORK_QUANTITY_MISMATCH");

        OperationJob job = new OperationJob(
                demand.reworkJobId(),
                demand.processId(),
                demand.units(),
                demand.priority(),
                demand.releaseTick(),
                demand.dueTick()
        );
        return new Decision(Verdict.JOB_READY, "REWORK_JOB_READY", job);
    }

    private static Decision waitFor(String reason) {
        return new Decision(Verdict.WAIT, reason, null);
    }

    private static Decision safeStop(String reason) {
        return new Decision(Verdict.SAFE_STOP, reason, null);
    }
}
