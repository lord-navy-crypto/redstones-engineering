package dev.redstoneengineering.operations;

import dev.redstoneengineering.core.port.PortQuality;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure maintenance gate in front of the single authoritative OperationDispatchRuntime.
 * Resources that are due for maintenance or under maintenance are projected UNAVAILABLE;
 * final job/resource ranking remains owned by OperationDispatchRuntime.
 */
public final class OperationMaintenanceAwareDispatchRuntime {
    private OperationMaintenanceAwareDispatchRuntime() {}

    public enum Verdict { ASSIGN, WAIT, SAFE_STOP, FAULT }

    public record Decision(
            Verdict verdict,
            String reason,
            OperationJob job,
            OperationResourceSnapshot resource,
            OperationResourceMaintenanceSnapshot maintenance
    ) {
        public Decision {
            if (verdict == null) verdict = Verdict.SAFE_STOP;
            if (reason == null || reason.isBlank()) reason = "UNSPECIFIED";
            if (verdict != Verdict.ASSIGN) {
                job = null;
                resource = null;
                maintenance = null;
            }
        }
    }

    public static Decision evaluate(
            Collection<OperationJob> jobs,
            Collection<OperationResourceSnapshot> resources,
            Collection<OperationResourceMaintenanceSnapshot> maintenanceStates,
            long gameTick,
            OperationDispatchRuntime.Policy policy
    ) {
        if (jobs == null) return safeStop("JOB_QUEUE_MISSING");
        if (resources == null) return safeStop("RESOURCE_SET_MISSING");
        if (maintenanceStates == null) return safeStop("MAINTENANCE_EVIDENCE_MISSING");

        Map<String, OperationResourceMaintenanceSnapshot> byResource = new HashMap<>();
        for (OperationResourceMaintenanceSnapshot maintenance : maintenanceStates) {
            if (maintenance == null) return safeStop("MAINTENANCE_EVIDENCE_NULL");
            if (byResource.putIfAbsent(maintenance.resourceId(), maintenance) != null) {
                return safeStop("DUPLICATE_MAINTENANCE_RESOURCE_ID");
            }
            if (maintenance.faultActive()
                    || maintenance.state() == OperationResourceMaintenanceSnapshot.State.FAULTED) {
                return new Decision(Verdict.FAULT, "RESOURCE_MAINTENANCE_FAULT_ACTIVE", null, null, null);
            }
            if (maintenance.evidenceQuality() != PortQuality.VALID) {
                return safeStop("RESOURCE_MAINTENANCE_EVIDENCE_INVALID");
            }
        }

        List<OperationResourceSnapshot> projected = new ArrayList<>();
        boolean productionHeldByMaintenance = false;
        for (OperationResourceSnapshot resource : resources) {
            if (resource == null) return safeStop("RESOURCE_NULL");
            OperationResourceMaintenanceSnapshot maintenance = byResource.get(resource.resourceId());
            if (maintenance == null) return safeStop("MAINTENANCE_EVIDENCE_MISSING_FOR_RESOURCE");
            OperationResourceSnapshot.State projectedState = resource.state();
            if (!maintenance.productionReady()) {
                projectedState = OperationResourceSnapshot.State.UNAVAILABLE;
                productionHeldByMaintenance = true;
            }
            projected.add(new OperationResourceSnapshot(
                    resource.resourceId(), resource.processIds(), projectedState, resource.evidenceQuality()));
        }

        OperationDispatchRuntime.Decision dispatch = OperationDispatchRuntime.evaluate(
                jobs, projected, gameTick, policy);
        if (dispatch.verdict() == OperationDispatchRuntime.Verdict.SAFE_STOP) {
            return safeStop(dispatch.reason());
        }
        if (dispatch.verdict() == OperationDispatchRuntime.Verdict.WAIT) {
            if (productionHeldByMaintenance) return waitFor("MAINTENANCE_HOLD");
            return waitFor(dispatch.reason());
        }

        OperationResourceMaintenanceSnapshot selected = byResource.get(dispatch.resource().resourceId());
        if (selected == null || !selected.productionReady()) {
            return safeStop("SELECTED_RESOURCE_MAINTENANCE_INCONSISTENT");
        }
        return new Decision(Verdict.ASSIGN, "MAINTENANCE_READY_" + dispatch.reason(),
                dispatch.job(), dispatch.resource(), selected);
    }

    private static Decision waitFor(String reason) {
        return new Decision(Verdict.WAIT, reason, null, null, null);
    }

    private static Decision safeStop(String reason) {
        return new Decision(Verdict.SAFE_STOP, reason, null, null, null);
    }
}
