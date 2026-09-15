package dev.redstoneengineering.operations;

import dev.redstoneengineering.core.port.PortQuality;

/** Pure fail-closed assessment of process-completion evidence for one active assignment. */
public final class OperationCompletionAssessment {
    private OperationCompletionAssessment() {}

    public enum Verdict {
        COMPLETE,
        WAIT,
        SAFE_STOP,
        FAULT
    }

    public record Snapshot(Verdict verdict, String reason) {
        public Snapshot {
            if (verdict == null) verdict = Verdict.SAFE_STOP;
            if (reason == null || reason.isBlank()) reason = "UNSPECIFIED";
        }

        public boolean complete() {
            return verdict == Verdict.COMPLETE;
        }
    }

    public static Snapshot inspect(
            OperationAssignment assignment,
            OperationCompletionEvidence evidence
    ) {
        if (assignment == null) return new Snapshot(Verdict.SAFE_STOP, "ASSIGNMENT_MISSING");
        if (evidence == null) return new Snapshot(Verdict.SAFE_STOP, "COMPLETION_EVIDENCE_MISSING");
        if (evidence.faultActive()) return new Snapshot(Verdict.FAULT, "PROCESS_FAULT_ACTIVE");
        if (evidence.evidenceQuality() != PortQuality.VALID) {
            return new Snapshot(Verdict.SAFE_STOP, "COMPLETION_EVIDENCE_" + qualityName(evidence.evidenceQuality()));
        }
        if (assignment.job().jobId() != evidence.jobId()) {
            return new Snapshot(Verdict.SAFE_STOP, "COMPLETION_JOB_MISMATCH");
        }
        if (!assignment.resourceId().equals(evidence.resourceId())) {
            return new Snapshot(Verdict.SAFE_STOP, "COMPLETION_RESOURCE_MISMATCH");
        }
        if (!evidence.processConfirmed()) return new Snapshot(Verdict.WAIT, "PROCESS_NOT_CONFIRMED");
        if (!evidence.outputConfirmed()) return new Snapshot(Verdict.WAIT, "OUTPUT_NOT_CONFIRMED");
        if (!evidence.completionConfirmed()) return new Snapshot(Verdict.WAIT, "COMPLETION_NOT_CONFIRMED");
        if (evidence.completedQuantity() != assignment.job().quantity()) {
            return new Snapshot(Verdict.SAFE_STOP, "COMPLETION_QUANTITY_MISMATCH");
        }
        return new Snapshot(Verdict.COMPLETE, "PROCESS_COMPLETE");
    }

    private static String qualityName(PortQuality quality) {
        return quality == null ? "MISSING" : quality.name();
    }
}
