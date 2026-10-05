package dev.redstoneengineering.operations;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Pure immutable queue lifecycle for Industrial Operations.
 *
 * <p>Admission, dispatch, and completion all return replacement snapshots. This
 * class never mutates machines, resources, inventory, robots, world state, or
 * diagnostic KPIs. Active work is released only by explicit completion evidence.</p>
 */
public final class OperationQueueRuntime {
    private OperationQueueRuntime() {}

    public enum Verdict {
        ENQUEUED,
        ASSIGNED,
        COMPLETED,
        WAIT,
        SAFE_STOP,
        FAULT
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
            if (verdict != Verdict.ASSIGNED && verdict != Verdict.COMPLETED) assignment = null;
        }

        public boolean changed() {
            return verdict == Verdict.ENQUEUED
                    || verdict == Verdict.ASSIGNED
                    || verdict == Verdict.COMPLETED;
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
        return applyAssignment(state, dispatch.job(), dispatch.resource(), gameTick, dispatch.reason());
    }

    /**
     * Queue dispatch with explicit maintenance evidence. Maintenance gating owns only resource
     * availability projection; final job/resource ranking still delegates to OperationDispatchRuntime.
     */
    public static Decision dispatchMaintenanceAware(
            OperationQueueSnapshot state,
            Collection<OperationResourceSnapshot> resources,
            Collection<OperationResourceMaintenanceSnapshot> maintenanceStates,
            long gameTick,
            OperationDispatchRuntime.Policy policy
    ) {
        if (state == null) throw new IllegalArgumentException("state is required");
        OperationMaintenanceAwareDispatchRuntime.Decision dispatch =
                OperationMaintenanceAwareDispatchRuntime.evaluate(
                        state.queued(), resources, maintenanceStates, gameTick, policy);
        if (dispatch.verdict() == OperationMaintenanceAwareDispatchRuntime.Verdict.FAULT) {
            return new Decision(Verdict.FAULT, dispatch.reason(), state, null);
        }
        if (dispatch.verdict() == OperationMaintenanceAwareDispatchRuntime.Verdict.SAFE_STOP) {
            return safeStop(state, dispatch.reason());
        }
        if (dispatch.verdict() != OperationMaintenanceAwareDispatchRuntime.Verdict.ASSIGN) {
            return waitFor(state, dispatch.reason());
        }
        return applyAssignment(state, dispatch.job(), dispatch.resource(), gameTick, dispatch.reason());
    }

    public static Decision complete(
            OperationQueueSnapshot state,
            OperationCompletionEvidence evidence
    ) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (evidence == null) return safeStop(state, "COMPLETION_EVIDENCE_MISSING");

        OperationAssignment assignment = null;
        for (OperationAssignment active : state.active()) {
            if (active.job().jobId() == evidence.jobId()) {
                assignment = active;
                break;
            }
        }
        if (assignment == null) return safeStop(state, "ACTIVE_ASSIGNMENT_NOT_FOUND");

        OperationCompletionAssessment.Snapshot assessment =
                OperationCompletionAssessment.inspect(assignment, evidence);
        return switch (assessment.verdict()) {
            case WAIT -> waitFor(state, assessment.reason());
            case SAFE_STOP -> safeStop(state, assessment.reason());
            case FAULT -> new Decision(Verdict.FAULT, assessment.reason(), state, null);
            case COMPLETE -> {
                ArrayList<OperationAssignment> active = new ArrayList<>();
                for (OperationAssignment candidate : state.active()) {
                    if (candidate.job().jobId() != assignment.job().jobId()) active.add(candidate);
                }
                OperationQueueSnapshot next = new OperationQueueSnapshot(
                        state.capacity(), state.queued(), active);
                yield new Decision(Verdict.COMPLETED, assessment.reason(), next, assignment);
            }
        };
    }

    private static Decision applyAssignment(
            OperationQueueSnapshot state,
            OperationJob job,
            OperationResourceSnapshot resource,
            long gameTick,
            String reason
    ) {
        if (job == null || resource == null) return safeStop(state, "DISPATCH_ASSIGNMENT_EVIDENCE_MISSING");
        for (OperationAssignment active : state.active()) {
            if (active.resourceId().equals(resource.resourceId())) {
                return safeStop(state, "RESOURCE_ALREADY_ASSIGNED");
            }
        }

        ArrayList<OperationJob> queued = new ArrayList<>();
        boolean removed = false;
        for (OperationJob candidate : state.queued()) {
            if (candidate.jobId() == job.jobId()) {
                removed = true;
            } else {
                queued.add(candidate);
            }
        }
        if (!removed) return safeStop(state, "DISPATCH_JOB_NOT_IN_QUEUE");

        OperationAssignment assignment = new OperationAssignment(job, resource.resourceId(), gameTick);
        ArrayList<OperationAssignment> active = new ArrayList<>(state.active());
        active.add(assignment);
        OperationQueueSnapshot next = new OperationQueueSnapshot(state.capacity(), queued, active);
        return new Decision(Verdict.ASSIGNED, reason, next, assignment);
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
