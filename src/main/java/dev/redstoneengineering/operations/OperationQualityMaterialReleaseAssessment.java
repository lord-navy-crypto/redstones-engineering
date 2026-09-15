package dev.redstoneengineering.operations;

import dev.redstoneengineering.core.port.PortQuality;

/**
 * Pure bridge from a completed output plus confirmed quality disposition into a new
 * quality-cleared accepted-material output lot.
 *
 * <p>Reject and rework quantities are deliberately excluded. The caller supplies a new output
 * identity so downstream logistics cannot confuse the accepted lot with the original mixed lot.</p>
 */
public final class OperationQualityMaterialReleaseAssessment {
    private OperationQualityMaterialReleaseAssessment() {}

    public enum Verdict {
        RELEASED,
        WAIT,
        SAFE_STOP,
        FAULT
    }

    public record Decision(Verdict verdict, String reason, OperationOutputSnapshot acceptedOutput) {
        public Decision {
            if (verdict == null) verdict = Verdict.SAFE_STOP;
            if (reason == null || reason.isBlank()) reason = "UNSPECIFIED";
            if (verdict != Verdict.RELEASED) acceptedOutput = null;
        }

        public boolean released() {
            return verdict == Verdict.RELEASED && acceptedOutput != null;
        }
    }

    public static Decision evaluate(
            OperationOutputSnapshot sourceOutput,
            OperationQualityDispositionAssessment.Snapshot quality,
            long acceptedOutputId
    ) {
        if (sourceOutput == null) return safeStop("SOURCE_OUTPUT_MISSING");
        if (quality == null) return safeStop("QUALITY_DISPOSITION_MISSING");
        if (acceptedOutputId < 0) return safeStop("ACCEPTED_OUTPUT_ID_INVALID");
        if (acceptedOutputId == sourceOutput.outputId()) return safeStop("ACCEPTED_OUTPUT_ID_NOT_DISTINCT");
        if (sourceOutput.faultActive()) return fault("SOURCE_OUTPUT_FAULT_ACTIVE");
        if (sourceOutput.evidenceQuality() != PortQuality.VALID) return safeStop("SOURCE_OUTPUT_EVIDENCE_INVALID");
        if (!sourceOutput.completionConfirmed()) return waitFor("SOURCE_OUTPUT_COMPLETION_UNCONFIRMED");
        if (!sourceOutput.materialReady()) return waitFor("SOURCE_MATERIAL_NOT_READY");

        if (quality.verdict() == OperationQualityDispositionAssessment.Verdict.WAIT) {
            return waitFor("QUALITY_DISPOSITION_INCOMPLETE");
        }
        if (quality.verdict() == OperationQualityDispositionAssessment.Verdict.FAULT) {
            return fault("QUALITY_DISPOSITION_FAULTED");
        }
        if (quality.verdict() != OperationQualityDispositionAssessment.Verdict.COMPLETE) {
            return safeStop("QUALITY_DISPOSITION_INVALID");
        }
        if (quality.outputId() != sourceOutput.outputId()) return safeStop("QUALITY_OUTPUT_ID_MISMATCH");
        if (quality.jobId() != sourceOutput.jobId()) return safeStop("QUALITY_JOB_ID_MISMATCH");
        if (quality.goodUnits() <= 0) return waitFor("NO_ACCEPTED_UNITS");
        if (quality.goodUnits() > sourceOutput.units()) return safeStop("ACCEPTED_QUANTITY_EXCEEDS_SOURCE");

        OperationOutputSnapshot accepted = new OperationOutputSnapshot(
                acceptedOutputId,
                sourceOutput.jobId(),
                sourceOutput.resourceId(),
                quality.goodUnits(),
                sourceOutput.source(),
                PortQuality.VALID,
                true,
                true,
                false
        );
        return new Decision(Verdict.RELEASED, "QUALITY_CLEARED_OUTPUT_RELEASED", accepted);
    }

    private static Decision waitFor(String reason) {
        return new Decision(Verdict.WAIT, reason, null);
    }

    private static Decision safeStop(String reason) {
        return new Decision(Verdict.SAFE_STOP, reason, null);
    }

    private static Decision fault(String reason) {
        return new Decision(Verdict.FAULT, reason, null);
    }
}
