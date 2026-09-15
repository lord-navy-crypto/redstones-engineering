package dev.redstoneengineering.operations;

import dev.redstoneengineering.core.port.PortQuality;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure precedence gate in front of the authoritative OperationDispatchRuntime.
 *
 * <p>This layer decides only whether a job is route-eligible. Resource capability,
 * availability, dispatch policy, and final resource selection remain owned by
 * OperationDispatchRuntime.</p>
 */
public final class OperationRoutedDispatchRuntime {
    private OperationRoutedDispatchRuntime() {}

    public enum Verdict {
        ASSIGN,
        WAIT,
        SAFE_STOP,
        FAULT
    }

    public record Decision(
            Verdict verdict,
            String reason,
            OperationJob job,
            OperationResourceSnapshot resource,
            OperationJobRouteBinding routeBinding
    ) {
        public Decision {
            if (verdict == null) verdict = Verdict.SAFE_STOP;
            if (reason == null || reason.isBlank()) reason = "UNSPECIFIED";
            if (verdict != Verdict.ASSIGN) {
                job = null;
                resource = null;
                routeBinding = null;
            }
        }

        public boolean assigned() {
            return verdict == Verdict.ASSIGN
                    && job != null
                    && resource != null
                    && routeBinding != null;
        }
    }

    public static Decision evaluate(
            Collection<OperationJob> jobs,
            Collection<OperationResourceSnapshot> resources,
            long gameTick,
            OperationDispatchRuntime.Policy policy,
            Collection<OperationJobRouteBinding> bindings,
            OperationCompletionLedger completionLedger
    ) {
        if (jobs == null) return safeStop("JOB_QUEUE_MISSING");
        if (bindings == null) return safeStop("ROUTE_BINDINGS_MISSING");
        if (completionLedger == null) return safeStop("COMPLETION_LEDGER_MISSING");
        if (completionLedger.faultActive()) {
            return new Decision(Verdict.FAULT, "COMPLETION_LEDGER_FAULT_ACTIVE", null, null, null);
        }
        if (completionLedger.evidenceQuality() != PortQuality.VALID) {
            return safeStop("COMPLETION_LEDGER_EVIDENCE_INVALID");
        }

        Map<Long, OperationJobRouteBinding> byJob = new HashMap<>();
        for (OperationJobRouteBinding binding : bindings) {
            if (binding == null) return safeStop("ROUTE_BINDING_NULL");
            if (byJob.putIfAbsent(binding.jobId(), binding) != null) {
                return safeStop("DUPLICATE_ROUTE_BINDING");
            }
        }

        List<OperationJob> routeEligible = new ArrayList<>();
        for (OperationJob job : jobs) {
            if (job == null) return safeStop("JOB_NULL");
            OperationJobRouteBinding binding = byJob.get(job.jobId());
            if (binding == null) return safeStop("ROUTE_BINDING_MISSING_FOR_JOB");
            if (binding.predecessorsSatisfied(completionLedger.completedJobIds())) {
                routeEligible.add(job);
            }
        }
        if (routeEligible.isEmpty() && !jobs.isEmpty()) {
            return waitFor("PREDECESSORS_INCOMPLETE");
        }

        OperationDispatchRuntime.Decision dispatch = OperationDispatchRuntime.evaluate(
                routeEligible,
                resources,
                gameTick,
                policy
        );
        if (dispatch.verdict() == OperationDispatchRuntime.Verdict.SAFE_STOP) {
            return safeStop(dispatch.reason());
        }
        if (dispatch.verdict() == OperationDispatchRuntime.Verdict.WAIT) {
            return waitFor(dispatch.reason());
        }

        OperationJobRouteBinding selectedBinding = byJob.get(dispatch.job().jobId());
        if (selectedBinding == null) return safeStop("SELECTED_ROUTE_BINDING_MISSING");
        return new Decision(
                Verdict.ASSIGN,
                "ROUTE_ELIGIBLE_" + dispatch.reason(),
                dispatch.job(),
                dispatch.resource(),
                selectedBinding
        );
    }

    private static Decision waitFor(String reason) {
        return new Decision(Verdict.WAIT, reason, null, null, null);
    }

    private static Decision safeStop(String reason) {
        return new Decision(Verdict.SAFE_STOP, reason, null, null, null);
    }
}
