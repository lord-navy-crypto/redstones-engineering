package dev.redstoneengineering.operations;

import dev.redstoneengineering.core.port.PortQuality;

/** Pure request/start/complete lifecycle for explicit resource maintenance. */
public final class OperationMaintenanceRuntime {
    private OperationMaintenanceRuntime() {}

    public enum Verdict { STARTED, COMPLETED, WAIT, SAFE_STOP, FAULT }

    public record Decision(Verdict verdict, String reason, OperationResourceMaintenanceSnapshot nextState) {
        public Decision {
            if (verdict == null) verdict = Verdict.SAFE_STOP;
            if (reason == null || reason.isBlank()) reason = "UNSPECIFIED";
            if (nextState == null) throw new IllegalArgumentException("nextState is required");
        }

        public boolean changed() {
            return verdict == Verdict.STARTED || verdict == Verdict.COMPLETED;
        }
    }

    public static Decision start(OperationResourceMaintenanceSnapshot current) {
        if (current == null) throw new IllegalArgumentException("current is required");
        if (current.faultActive() || current.state() == OperationResourceMaintenanceSnapshot.State.FAULTED) {
            return new Decision(Verdict.FAULT, "MAINTENANCE_RESOURCE_FAULT_ACTIVE", current);
        }
        if (current.evidenceQuality() != PortQuality.VALID) {
            return safeStop(current, "MAINTENANCE_EVIDENCE_INVALID");
        }
        if (current.state() == OperationResourceMaintenanceSnapshot.State.AVAILABLE) {
            return waitFor(current, "MAINTENANCE_NOT_DUE");
        }
        if (current.state() == OperationResourceMaintenanceSnapshot.State.IN_PROGRESS) {
            return waitFor(current, "MAINTENANCE_ALREADY_IN_PROGRESS");
        }
        if (current.state() != OperationResourceMaintenanceSnapshot.State.MAINTENANCE_DUE) {
            return safeStop(current, "MAINTENANCE_START_STATE_INVALID");
        }

        OperationResourceMaintenanceSnapshot next = new OperationResourceMaintenanceSnapshot(
                current.resourceId(),
                OperationResourceMaintenanceSnapshot.State.IN_PROGRESS,
                current.maintenanceId(),
                PortQuality.VALID,
                false
        );
        return new Decision(Verdict.STARTED, "MAINTENANCE_STARTED", next);
    }

    public static Decision complete(
            OperationResourceMaintenanceSnapshot current,
            OperationMaintenanceCompletionEvidence evidence
    ) {
        if (current == null) throw new IllegalArgumentException("current is required");
        if (evidence == null) return safeStop(current, "MAINTENANCE_COMPLETION_EVIDENCE_MISSING");
        if (current.faultActive() || current.state() == OperationResourceMaintenanceSnapshot.State.FAULTED) {
            return new Decision(Verdict.FAULT, "MAINTENANCE_RESOURCE_FAULT_ACTIVE", current);
        }
        if (evidence.faultActive()) {
            return new Decision(Verdict.FAULT, "MAINTENANCE_COMPLETION_FAULT_ACTIVE", current);
        }
        if (current.evidenceQuality() != PortQuality.VALID || evidence.evidenceQuality() != PortQuality.VALID) {
            return safeStop(current, "MAINTENANCE_COMPLETION_EVIDENCE_INVALID");
        }
        if (current.state() != OperationResourceMaintenanceSnapshot.State.IN_PROGRESS) {
            return safeStop(current, "MAINTENANCE_NOT_IN_PROGRESS");
        }
        if (!current.resourceId().equals(evidence.resourceId())) {
            return safeStop(current, "MAINTENANCE_RESOURCE_ID_MISMATCH");
        }
        if (!current.maintenanceId().equals(evidence.maintenanceId())) {
            return safeStop(current, "MAINTENANCE_ID_MISMATCH");
        }
        if (!evidence.workConfirmed()) return waitFor(current, "MAINTENANCE_WORK_UNCONFIRMED");
        if (!evidence.completionConfirmed()) return waitFor(current, "MAINTENANCE_COMPLETION_UNCONFIRMED");

        OperationResourceMaintenanceSnapshot next = new OperationResourceMaintenanceSnapshot(
                current.resourceId(),
                OperationResourceMaintenanceSnapshot.State.AVAILABLE,
                null,
                PortQuality.VALID,
                false
        );
        return new Decision(Verdict.COMPLETED, "MAINTENANCE_COMPLETE", next);
    }

    private static Decision waitFor(OperationResourceMaintenanceSnapshot current, String reason) {
        return new Decision(Verdict.WAIT, reason, current);
    }

    private static Decision safeStop(OperationResourceMaintenanceSnapshot current, String reason) {
        return new Decision(Verdict.SAFE_STOP, reason, current);
    }
}
