package dev.redstoneengineering.operations;

import dev.redstoneengineering.core.port.PortQuality;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure deterministic dispatch decision for Industrial Operations.
 *
 * <p>This runtime never mutates queues, resources, inventory, machines, robots,
 * or world state. It selects one released job and one evidence-valid available
 * resource. Metrics and dashboards are deliberately absent from the decision path.</p>
 */
public final class OperationDispatchRuntime {
    private OperationDispatchRuntime() {}

    public enum Policy {
        FIFO,
        PRIORITY_THEN_FIFO
    }

    public enum Verdict {
        ASSIGN,
        WAIT,
        SAFE_STOP
    }

    public record Decision(
            Verdict verdict,
            String reason,
            OperationJob job,
            OperationResourceSnapshot resource
    ) {
        public Decision {
            if (verdict == null) verdict = Verdict.SAFE_STOP;
            if (reason == null || reason.isBlank()) reason = "UNSPECIFIED";
            if (verdict != Verdict.ASSIGN) {
                job = null;
                resource = null;
            }
        }

        public boolean assigned() {
            return verdict == Verdict.ASSIGN && job != null && resource != null;
        }
    }

    public static Decision evaluate(
            Collection<OperationJob> jobs,
            Collection<OperationResourceSnapshot> resources,
            long gameTick,
            Policy policy
    ) {
        if (gameTick < 0) return safeStop("INVALID_GAME_TICK");
        if (policy == null) return safeStop("DISPATCH_POLICY_MISSING");
        if (jobs == null) return safeStop("JOB_QUEUE_MISSING");
        if (resources == null) return safeStop("RESOURCE_SET_MISSING");
        if (!uniqueJobIds(jobs)) return safeStop("DUPLICATE_JOB_ID");
        if (!uniqueResourceIds(resources)) return safeStop("DUPLICATE_RESOURCE_ID");

        List<OperationJob> released = jobs.stream()
                .filter(job -> job != null && job.releasedAt(gameTick))
                .sorted(jobComparator(policy))
                .toList();
        if (released.isEmpty()) return waitFor("NO_RELEASED_JOB");

        boolean invalidResourceEvidenceSeen = false;
        for (OperationJob job : released) {
            List<OperationResourceSnapshot> capable = new ArrayList<>();
            for (OperationResourceSnapshot resource : resources) {
                if (resource == null || !resource.supports(job.processId())) continue;
                capable.add(resource);
                if (resource.evidenceQuality() != PortQuality.VALID) invalidResourceEvidenceSeen = true;
            }
            capable.sort(Comparator.comparing(OperationResourceSnapshot::resourceId));

            for (OperationResourceSnapshot resource : capable) {
                if (resource.dispatchable()) {
                    return new Decision(Verdict.ASSIGN, "DISPATCH_PERMIT", job, resource);
                }
            }
        }

        if (invalidResourceEvidenceSeen) return safeStop("RESOURCE_EVIDENCE_INVALID");
        return waitFor("NO_DISPATCHABLE_RESOURCE");
    }

    private static Comparator<OperationJob> jobComparator(Policy policy) {
        Comparator<OperationJob> fifo = Comparator
                .comparingLong(OperationJob::releaseTick)
                .thenComparingLong(OperationJob::jobId);
        if (policy == Policy.PRIORITY_THEN_FIFO) {
            return Comparator.comparingInt(OperationJob::priority).reversed().thenComparing(fifo);
        }
        return fifo;
    }

    private static boolean uniqueJobIds(Collection<OperationJob> jobs) {
        Set<Long> ids = new HashSet<>();
        for (OperationJob job : jobs) {
            if (job == null || !ids.add(job.jobId())) return false;
        }
        return true;
    }

    private static boolean uniqueResourceIds(Collection<OperationResourceSnapshot> resources) {
        Set<String> ids = new HashSet<>();
        for (OperationResourceSnapshot resource : resources) {
            if (resource == null || !ids.add(resource.resourceId())) return false;
        }
        return true;
    }

    private static Decision waitFor(String reason) {
        return new Decision(Verdict.WAIT, reason, null, null);
    }

    private static Decision safeStop(String reason) {
        return new Decision(Verdict.SAFE_STOP, reason, null, null);
    }
}
