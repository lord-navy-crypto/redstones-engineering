package dev.redstoneengineering.operations;

import dev.redstoneengineering.core.port.PortQuality;

/**
 * Pure admission gate for one job entering a finite-capacity workcell.
 *
 * <p>This class does not rank jobs, choose resources, mutate queues, or inspect diagnostic
 * bottleneck scores. It only verifies that the selected resource belongs to the workcell and
 * that authoritative finite-capacity evidence permits additional work to enter.</p>
 */
public final class OperationWorkcellAdmissionAssessment {
    private OperationWorkcellAdmissionAssessment() {}

    public enum Verdict {
        PERMIT,
        WAIT,
        SAFE_STOP,
        FAULT
    }

    public record Snapshot(Verdict verdict, String reason) {
        public Snapshot {
            if (verdict == null) verdict = Verdict.SAFE_STOP;
            if (reason == null || reason.isBlank()) reason = "UNSPECIFIED";
        }

        public boolean permitted() {
            return verdict == Verdict.PERMIT;
        }
    }

    public static Snapshot inspect(OperationWorkcellCapacitySnapshot workcell, String resourceId) {
        if (workcell == null) return safeStop("WORKCELL_CAPACITY_EVIDENCE_MISSING");
        if (resourceId == null || resourceId.isBlank()) return safeStop("RESOURCE_ID_MISSING");
        if (workcell.faultActive()) return new Snapshot(Verdict.FAULT, "WORKCELL_FAULT_ACTIVE");
        if (workcell.evidenceQuality() != PortQuality.VALID) {
            return safeStop("WORKCELL_CAPACITY_EVIDENCE_INVALID");
        }

        String normalizedResourceId = resourceId.trim();
        if (!workcell.resourceIds().contains(normalizedResourceId)) {
            return safeStop("RESOURCE_NOT_IN_WORKCELL");
        }
        if (workcell.outputBlocked()) {
            return waitFor("OUTPUT_BUFFER_CAPACITY_REACHED");
        }
        if (workcell.resourcesSaturated()) {
            return waitFor("WORKCELL_RESOURCE_CAPACITY_REACHED");
        }
        return new Snapshot(Verdict.PERMIT, "WORKCELL_ADMISSION_PERMIT");
    }

    private static Snapshot waitFor(String reason) {
        return new Snapshot(Verdict.WAIT, reason);
    }

    private static Snapshot safeStop(String reason) {
        return new Snapshot(Verdict.SAFE_STOP, reason);
    }
}
