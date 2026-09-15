package dev.redstoneengineering.operations;

import dev.redstoneengineering.core.port.PortQuality;

/**
 * Pure evidence assessment from completed output to accepted/reject/rework disposition.
 *
 * <p>This class does not mutate inventory, generate rework jobs, or alter dispatch policy.
 * It only validates identity, quantity, inspection completion, and disposition accounting.</p>
 */
public final class OperationQualityDispositionAssessment {
    private OperationQualityDispositionAssessment() {}

    public enum Verdict {
        COMPLETE,
        WAIT,
        SAFE_STOP,
        FAULT
    }

    public record Snapshot(
            Verdict verdict,
            String reason,
            long outputId,
            long jobId,
            int goodUnits,
            int rejectUnits,
            int reworkUnits
    ) {
        public Snapshot {
            if (verdict == null) verdict = Verdict.SAFE_STOP;
            if (reason == null || reason.isBlank()) reason = "UNSPECIFIED";
            if (verdict != Verdict.COMPLETE) {
                goodUnits = 0;
                rejectUnits = 0;
                reworkUnits = 0;
            }
        }

        public int acceptedUnits() {
            return verdict == Verdict.COMPLETE ? goodUnits : 0;
        }

        public int nonconformingUnits() {
            return verdict == Verdict.COMPLETE ? rejectUnits + reworkUnits : 0;
        }
    }

    public static Snapshot inspect(
            OperationOutputSnapshot output,
            OperationQualityInspectionEvidence inspection
    ) {
        if (output == null) return safeStop(-1, -1, "OUTPUT_EVIDENCE_MISSING");
        if (inspection == null) return safeStop(output.outputId(), output.jobId(), "QUALITY_INSPECTION_EVIDENCE_MISSING");
        if (output.faultActive()) return fault(output.outputId(), output.jobId(), "OUTPUT_FAULT_ACTIVE");
        if (inspection.faultActive()) return fault(output.outputId(), output.jobId(), "QUALITY_INSPECTION_FAULT_ACTIVE");
        if (output.evidenceQuality() != PortQuality.VALID) {
            return safeStop(output.outputId(), output.jobId(), "OUTPUT_EVIDENCE_INVALID");
        }
        if (inspection.evidenceQuality() != PortQuality.VALID) {
            return safeStop(output.outputId(), output.jobId(), "QUALITY_INSPECTION_EVIDENCE_INVALID");
        }
        if (!output.completionConfirmed()) {
            return waitFor(output.outputId(), output.jobId(), "OUTPUT_COMPLETION_UNCONFIRMED");
        }
        if (!inspection.inspectionConfirmed()) {
            return waitFor(output.outputId(), output.jobId(), "QUALITY_INSPECTION_UNCONFIRMED");
        }
        if (inspection.outputId() != output.outputId()) {
            return safeStop(output.outputId(), output.jobId(), "QUALITY_OUTPUT_ID_MISMATCH");
        }
        if (inspection.jobId() != output.jobId()) {
            return safeStop(output.outputId(), output.jobId(), "QUALITY_JOB_ID_MISMATCH");
        }
        if (inspection.inspectedUnits() != output.units()) {
            return safeStop(output.outputId(), output.jobId(), "QUALITY_INSPECTED_QUANTITY_MISMATCH");
        }
        if (inspection.dispositionUnits() != inspection.inspectedUnits()) {
            return safeStop(output.outputId(), output.jobId(), "QUALITY_DISPOSITION_ACCOUNTING_MISMATCH");
        }

        return new Snapshot(
                Verdict.COMPLETE,
                "QUALITY_DISPOSITION_CONFIRMED",
                output.outputId(),
                output.jobId(),
                inspection.goodUnits(),
                inspection.rejectUnits(),
                inspection.reworkUnits()
        );
    }

    private static Snapshot waitFor(long outputId, long jobId, String reason) {
        return new Snapshot(Verdict.WAIT, reason, outputId, jobId, 0, 0, 0);
    }

    private static Snapshot safeStop(long outputId, long jobId, String reason) {
        return new Snapshot(Verdict.SAFE_STOP, reason, outputId, jobId, 0, 0, 0);
    }

    private static Snapshot fault(long outputId, long jobId, String reason) {
        return new Snapshot(Verdict.FAULT, reason, outputId, jobId, 0, 0, 0);
    }
}
