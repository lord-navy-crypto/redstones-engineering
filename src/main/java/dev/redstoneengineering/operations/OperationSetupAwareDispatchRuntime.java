package dev.redstoneengineering.operations;

import dev.redstoneengineering.core.port.PortQuality;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure setup/changeover gate in front of the authoritative OperationDispatchRuntime.
 *
 * <p>This layer never chooses the winning job/resource pair. It projects resources that are not
 * presently configured for production into UNAVAILABLE evidence and delegates final selection to
 * OperationDispatchRuntime.</p>
 */
public final class OperationSetupAwareDispatchRuntime {
    private OperationSetupAwareDispatchRuntime() {}

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
            OperationResourceSetupSnapshot setup
    ) {
        public Decision {
            if (verdict == null) verdict = Verdict.SAFE_STOP;
            if (reason == null || reason.isBlank()) reason = "UNSPECIFIED";
            if (verdict != Verdict.ASSIGN) {
                job = null;
                resource = null;
                setup = null;
            }
        }

        public boolean assigned() {
            return verdict == Verdict.ASSIGN && job != null && resource != null && setup != null;
        }
    }

    public static Decision evaluate(
            Collection<OperationJob> jobs,
            Collection<OperationResourceSnapshot> resources,
            Collection<OperationResourceSetupSnapshot> setups,
            long gameTick,
            OperationDispatchRuntime.Policy policy
    ) {
        if (jobs == null) return safeStop("JOB_QUEUE_MISSING");
        if (resources == null) return safeStop("RESOURCE_SET_MISSING");
        if (setups == null) return safeStop("SETUP_EVIDENCE_MISSING");

        Map<String, OperationResourceSetupSnapshot> byResource = new HashMap<>();
        for (OperationResourceSetupSnapshot setup : setups) {
            if (setup == null) return safeStop("SETUP_EVIDENCE_NULL");
            if (byResource.putIfAbsent(setup.resourceId(), setup) != null) {
                return safeStop("DUPLICATE_SETUP_RESOURCE_ID");
            }
            if (setup.faultActive() || setup.state() == OperationResourceSetupSnapshot.State.FAULTED) {
                return new Decision(Verdict.FAULT, "RESOURCE_SETUP_FAULT_ACTIVE", null, null, null);
            }
            if (setup.evidenceQuality() != PortQuality.VALID) {
                return safeStop("RESOURCE_SETUP_EVIDENCE_INVALID");
            }
        }

        List<OperationResourceSnapshot> projected = new ArrayList<>();
        for (OperationResourceSnapshot resource : resources) {
            if (resource == null) return safeStop("RESOURCE_NULL");
            OperationResourceSetupSnapshot setup = byResource.get(resource.resourceId());
            if (setup == null) return safeStop("SETUP_EVIDENCE_MISSING_FOR_RESOURCE");
            if (setup.state() == OperationResourceSetupSnapshot.State.READY
                    && !resource.supports(setup.configuredProcessId())) {
                return safeStop("SETUP_PROCESS_CAPABILITY_MISMATCH");
            }

            OperationResourceSnapshot.State projectedState =
                    setup.state() == OperationResourceSetupSnapshot.State.READY
                            ? resource.state()
                            : OperationResourceSnapshot.State.UNAVAILABLE;
            Set<String> projectedProcesses = setup.state() == OperationResourceSetupSnapshot.State.READY
                    ? Set.of(setup.configuredProcessId())
                    : resource.processIds();
            projected.add(new OperationResourceSnapshot(
                    resource.resourceId(),
                    projectedProcesses,
                    projectedState,
                    resource.evidenceQuality()
            ));
        }

        OperationDispatchRuntime.Decision dispatch = OperationDispatchRuntime.evaluate(
                jobs, projected, gameTick, policy);
        if (dispatch.verdict() == OperationDispatchRuntime.Verdict.SAFE_STOP) {
            return safeStop(dispatch.reason());
        }
        if (dispatch.verdict() == OperationDispatchRuntime.Verdict.WAIT) {
            if (requiresSetup(jobs, resources, byResource, gameTick)) {
                return waitFor("SETUP_OR_CHANGEOVER_REQUIRED");
            }
            return waitFor(dispatch.reason());
        }

        OperationResourceSetupSnapshot selectedSetup = byResource.get(dispatch.resource().resourceId());
        if (selectedSetup == null || !selectedSetup.readyFor(dispatch.job().processId())) {
            return safeStop("SELECTED_RESOURCE_SETUP_INCONSISTENT");
        }
        return new Decision(
                Verdict.ASSIGN,
                "SETUP_READY_" + dispatch.reason(),
                dispatch.job(),
                dispatch.resource(),
                selectedSetup
        );
    }

    private static boolean requiresSetup(
            Collection<OperationJob> jobs,
            Collection<OperationResourceSnapshot> resources,
            Map<String, OperationResourceSetupSnapshot> setups,
            long gameTick
    ) {
        Set<String> requiredProcesses = new HashSet<>();
        for (OperationJob job : jobs) {
            if (job != null && job.releasedAt(gameTick)) requiredProcesses.add(job.processId());
        }
        for (OperationResourceSnapshot resource : resources) {
            if (resource == null) continue;
            OperationResourceSetupSnapshot setup = setups.get(resource.resourceId());
            for (String processId : requiredProcesses) {
                if (resource.supports(processId) && (setup == null || !setup.readyFor(processId))) return true;
            }
        }
        return false;
    }

    private static Decision waitFor(String reason) {
        return new Decision(Verdict.WAIT, reason, null, null, null);
    }

    private static Decision safeStop(String reason) {
        return new Decision(Verdict.SAFE_STOP, reason, null, null, null);
    }
}
