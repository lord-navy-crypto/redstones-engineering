package dev.redstoneengineering.operations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Pure immutable queue lifecycle for Industrial Operations.
 *
 * <p>Admission and dispatch return a replacement snapshot. This class never
 * mutates machines, resources, inventory, robots, world state, or diagnostic KPIs.
 * Completion is intentionally not modeled here until explicit process-completion
 * evidence exists.</p>
 */
public final class OperationQueueRuntime {
    private OperationQueueRuntime() {}

    public enum Verdict {
        ENQUEUED,
        ASSIGNED,
        WAIT,
        SAFE_STOP
    }

    public record Decision(
            Verdict verdict,
            String reason,
            OperationQueueSnapshot nextState,
            OperationAssignment assignment
    ) {
        public Decision {
            if (verdict == null) verdict = Verdict.SAFE_STOP;
            if (reason == null || reason.isBlank()) reason = "UNSPECIFIED";
            if (nextState == null) throw new IllegalArgumentException("nextState is required");
            if (verdict != Verdict.ASSIGNED) assignment = null;
        }

        public boolean changed() {
            return verdict == Verdict.ENQUEUED || verdict == Verdict.ASSIGNED;
        }
    }

    public static Decision enqueue(OperationQueueSnapshot state, OperationJob job) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (job == null) return safeStop(state, "JOB_MISSING");
        if (containsJob(state, job.jobId())) return safeStop(state, "DUPLICATE_JOB_ID");
        if (state.queued().size() >= state.capacity()) return waitFor(state, "QUEUE_CAPACITY_REACHED");

        ArrayList<OperationJob> queued = new ArrayList<>(state.queued());
        queued.add(job);
        return new Decision(
                Verdict.ENQUEUED,
                "JOB_ENQUEUED",
                new OperationQueueSnapshot(state.capacity(), queued, state.active()),
                null
        );
    }

    public static Decision dispatch(
            OperationQueueSnapshot state,
            Collection<OperationResourceSnapshot> resources,
            long gameTick,
            OperationDispatchRuntime.Policy policy
    ) {
        if (state == null) throw new IllegalArgumentException("state is required");
        OperationDispatchRuntime.Decision dispatch = OperationDispatchRuntime.evaluate(
                state.queued(), resources, gameTick, policy);
        if (dispatch.verdict() == OperationDispatchRuntime.Verdict.SAFE_STOP) {
            return safeStop(state, dispatch.reason());
        }
        if (!dispatch.assigned()) return waitFor(state, dispatch.reason());

        for (OperationAssignment active : state.active()) {
            if (active.resourceId().equals(dispatch.resource().resourceId())) {
                return safeStop(state, "RESOURCE_ALREADY_ASSIGNED");
            }
        }

        ArrayList<OperationJob> queued = new ArrayList<>();
        for (OperationJob job : state.queued()) {
            if (job.jobId() != dispatch.job().jobId()) queued.add(job);
        }
        OperationAssignment assignment = new OperationAssignment(
                dispatch.job(), dispatch.resource().resourceId(), gameTick);
        ArrayList<OperationAssignment> active = new ArrayList<>(state.active());
        active.add(assignment);
        OperationQueueSnapshot next = new OperationQueueSnapshot(state.capacity(), queued, active);
        return new Decision(Verdict.ASSIGNED, dispatch.reason(), next, assignment);
    }

    private static boolean containsJob(OperationQueueSnapshot state, long jobId) {
        for (OperationJob job : state.queued()) if (job.jobId() == jobId) return true;
        for (OperationAssignment assignment : state.active()) {
            if (assignment.job().jobId() == jobId) return true;
        }
        return false;
    }

    private static Decision waitFor(OperationQueueSnapshot state, String reason) {
        return new Decision(Verdict.WAIT, reason, state, null);
    }

    private static Decision safeStop(OperationQueueSnapshot state, String reason) {
        return new Decision(Verdict.SAFE_STOP, reason, state, null);
    }
}
