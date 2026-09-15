package dev.redstoneengineering.operations;

import dev.redstoneengineering.core.port.PortQuality;

/**
 * Pure evidence-bound lifecycle for production-resource changeover.
 *
 * <p>Requesting changeover records an explicit target process. READY is reached only from
 * identity-matched VALID completion evidence; elapsed time alone never completes setup.</p>
 */
public final class OperationChangeoverRuntime {
    private OperationChangeoverRuntime() {}

    public enum Verdict {
        REQUESTED,
        READY,
        WAIT,
        SAFE_STOP,
        FAULT
    }

    public record Decision(
            Verdict verdict,
            String reason,
            OperationResourceSetupSnapshot nextState
    ) {
        public Decision {
            if (verdict == null) verdict = Verdict.SAFE_STOP;
            if (reason == null || reason.isBlank()) reason = "UNSPECIFIED";
            if (nextState == null) throw new IllegalArgumentException("nextState is required");
        }

        public boolean changed() {
            return verdict == Verdict.REQUESTED || verdict == Verdict.READY;
        }
    }

    public static Decision request(
            OperationResourceSnapshot resource,
            OperationResourceSetupSnapshot current,
            String targetProcessId
    ) {
        if (resource == null) throw new IllegalArgumentException("resource is required");
        if (current == null) throw new IllegalArgumentException("current setup is required");
        if (targetProcessId == null || targetProcessId.isBlank()) {
            return safeStop(current, "CHANGEOVER_TARGET_MISSING");
        }
        String target = targetProcessId.trim();
        if (!resource.resourceId().equals(current.resourceId())) {
            return safeStop(current, "CHANGEOVER_RESOURCE_ID_MISMATCH");
        }
        if (current.faultActive() || current.state() == OperationResourceSetupSnapshot.State.FAULTED) {
            return new Decision(Verdict.FAULT, "RESOURCE_SETUP_FAULT_ACTIVE", current);
        }
        if (current.evidenceQuality() != PortQuality.VALID) {
            return safeStop(current, "RESOURCE_SETUP_EVIDENCE_INVALID");
        }
        if (resource.evidenceQuality() != PortQuality.VALID) {
            return safeStop(current, "RESOURCE_EVIDENCE_INVALID");
        }
        if (!resource.supports(target)) {
            return safeStop(current, "CHANGEOVER_TARGET_NOT_CAPABLE");
        }
        if (current.readyFor(target)) return waitFor(current, "ALREADY_CONFIGURED");
        if (current.state() == OperationResourceSetupSnapshot.State.CHANGEOVER_IN_PROGRESS) {
            if (target.equals(current.targetProcessId())) {
                return waitFor(current, "CHANGEOVER_ALREADY_IN_PROGRESS");
            }
            return safeStop(current, "CHANGEOVER_TARGET_CONFLICT");
        }

        OperationResourceSetupSnapshot next = new OperationResourceSetupSnapshot(
                current.resourceId(),
                current.configuredProcessId(),
                target,
                OperationResourceSetupSnapshot.State.CHANGEOVER_IN_PROGRESS,
                current.evidenceQuality(),
                false
        );
        return new Decision(Verdict.REQUESTED, "CHANGEOVER_REQUESTED", next);
    }

    public static Decision complete(
            OperationResourceSetupSnapshot current,
            OperationChangeoverEvidence evidence
    ) {
        if (current == null) throw new IllegalArgumentException("current setup is required");
        if (evidence == null) return safeStop(current, "CHANGEOVER_EVIDENCE_MISSING");
        if (current.state() != OperationResourceSetupSnapshot.State.CHANGEOVER_IN_PROGRESS) {
            return safeStop(current, "CHANGEOVER_NOT_IN_PROGRESS");
        }
        if (evidence.faultActive()) {
            OperationResourceSetupSnapshot faulted = new OperationResourceSetupSnapshot(
                    current.resourceId(),
                    current.configuredProcessId(),
                    current.targetProcessId(),
                    OperationResourceSetupSnapshot.State.FAULTED,
                    evidence.evidenceQuality(),
                    true
            );
            return new Decision(Verdict.FAULT, "CHANGEOVER_FAULT_ACTIVE", faulted);
        }
        if (evidence.evidenceQuality() != PortQuality.VALID) {
            return safeStop(current, "CHANGEOVER_EVIDENCE_INVALID");
        }
        if (!current.resourceId().equals(evidence.resourceId())) {
            return safeStop(current, "CHANGEOVER_RESOURCE_ID_MISMATCH");
        }
        if (!current.targetProcessId().equals(evidence.targetProcessId())) {
            return safeStop(current, "CHANGEOVER_TARGET_MISMATCH");
        }
        if (!evidence.setupWorkConfirmed()) return waitFor(current, "CHANGEOVER_WORK_UNCONFIRMED");
        if (!evidence.completionConfirmed()) return waitFor(current, "CHANGEOVER_COMPLETION_UNCONFIRMED");

        OperationResourceSetupSnapshot ready = new OperationResourceSetupSnapshot(
                current.resourceId(),
                current.targetProcessId(),
                null,
                OperationResourceSetupSnapshot.State.READY,
                evidence.evidenceQuality(),
                false
        );
        return new Decision(Verdict.READY, "CHANGEOVER_COMPLETE", ready);
    }

    private static Decision waitFor(OperationResourceSetupSnapshot current, String reason) {
        return new Decision(Verdict.WAIT, reason, current);
    }

    private static Decision safeStop(OperationResourceSetupSnapshot current, String reason) {
        return new Decision(Verdict.SAFE_STOP, reason, current);
    }
}
