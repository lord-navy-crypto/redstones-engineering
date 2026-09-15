package dev.redstoneengineering.operations;

/**
 * Pure atomic bridge from a specific buffer lot allocation into downstream queue admission.
 *
 * <p>The returned buffer and queue snapshots change together only when both material allocation
 * and queue admission succeed. A full queue therefore never consumes WIP.</p>
 */
public final class OperationMaterialReleaseRuntime {
    private OperationMaterialReleaseRuntime() {}

    public enum Verdict {
        RELEASED,
        WAIT,
        SAFE_STOP,
        FAULT
    }

    public record Decision(
            Verdict verdict,
            String reason,
            OperationBufferSnapshot nextBuffer,
            OperationQueueSnapshot nextQueue,
            OperationInputAllocation allocation
    ) {
        public Decision {
            if (verdict == null) verdict = Verdict.SAFE_STOP;
            if (reason == null || reason.isBlank()) reason = "UNSPECIFIED";
            if (nextBuffer == null) throw new IllegalArgumentException("nextBuffer is required");
            if (nextQueue == null) throw new IllegalArgumentException("nextQueue is required");
            if (verdict != Verdict.RELEASED) allocation = null;
        }

        public boolean released() {
            return verdict == Verdict.RELEASED && allocation != null;
        }
    }

    public static Decision release(
            OperationBufferSnapshot buffer,
            OperationQueueSnapshot queue,
            OperationJob downstreamJob,
            OperationInputRequirement requirement
    ) {
        if (buffer == null) throw new IllegalArgumentException("buffer is required");
        if (queue == null) throw new IllegalArgumentException("queue is required");
        if (downstreamJob == null) return safeStop(buffer, queue, "DOWNSTREAM_JOB_MISSING");
        if (requirement == null) return safeStop(buffer, queue, "INPUT_REQUIREMENT_MISSING");
        if (downstreamJob.jobId() != requirement.downstreamJobId()) {
            return safeStop(buffer, queue, "DOWNSTREAM_JOB_ID_MISMATCH");
        }

        OperationBufferRuntime.AllocationDecision allocation =
                OperationBufferRuntime.allocate(buffer, requirement);
        if (allocation.verdict() == OperationBufferRuntime.Verdict.FAULT) {
            return new Decision(Verdict.FAULT, allocation.reason(), buffer, queue, null);
        }
        if (allocation.verdict() == OperationBufferRuntime.Verdict.SAFE_STOP) {
            return safeStop(buffer, queue, allocation.reason());
        }
        if (!allocation.allocated()) {
            return waitFor(buffer, queue, allocation.reason());
        }

        OperationQueueRuntime.Decision admission = OperationQueueRuntime.enqueue(queue, downstreamJob);
        if (admission.verdict() == OperationQueueRuntime.Verdict.FAULT) {
            return new Decision(Verdict.FAULT, admission.reason(), buffer, queue, null);
        }
        if (admission.verdict() == OperationQueueRuntime.Verdict.SAFE_STOP) {
            return safeStop(buffer, queue, admission.reason());
        }
        if (admission.verdict() != OperationQueueRuntime.Verdict.ENQUEUED) {
            return waitFor(buffer, queue, admission.reason());
        }

        return new Decision(
                Verdict.RELEASED,
                "MATERIAL_JOB_RELEASED",
                allocation.nextState(),
                admission.nextState(),
                allocation.allocation()
        );
    }

    private static Decision waitFor(
            OperationBufferSnapshot buffer,
            OperationQueueSnapshot queue,
            String reason
    ) {
        return new Decision(Verdict.WAIT, reason, buffer, queue, null);
    }

    private static Decision safeStop(
            OperationBufferSnapshot buffer,
            OperationQueueSnapshot queue,
            String reason
    ) {
        return new Decision(Verdict.SAFE_STOP, reason, buffer, queue, null);
    }
}
