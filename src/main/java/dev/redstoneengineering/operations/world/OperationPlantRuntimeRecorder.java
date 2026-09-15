package dev.redstoneengineering.operations.world;

import dev.redstoneengineering.operations.OperationJob;
import dev.redstoneengineering.operations.OperationMaintenanceRuntime;
import dev.redstoneengineering.operations.OperationQualityInspectionEvidence;
import dev.redstoneengineering.operations.OperationQueueRuntime;
import dev.redstoneengineering.operations.OperationResourceMaintenanceSnapshot;
import dev.redstoneengineering.operations.OperationTransportDemand;
import net.minecraft.server.level.ServerLevel;

/**
 * Thin world adapter between pure Industrial Operations runtimes and durable plant history.
 *
 * <p>The queue, quality, maintenance and logistics models intentionally remain pure/immutable.
 * This adapter records only meaningful lifecycle transitions and evidence into
 * {@link OperationPlantSavedData}; it never changes the underlying decision.</p>
 */
public final class OperationPlantRuntimeRecorder {
    private OperationPlantRuntimeRecorder() {}

    public static boolean recordQueueEnqueue(
            ServerLevel level,
            String queueId,
            OperationJob job,
            OperationQueueRuntime.Decision decision,
            long gameTick
    ) {
        if (level == null || job == null || decision == null
                || decision.verdict() != OperationQueueRuntime.Verdict.ENQUEUED) {
            return false;
        }
        OperationPlantSavedData data = OperationPlantSavedData.get(level);
        if (data.jobLifecycle(job.jobId()) == null && !data.recordJobAdmitted(job, gameTick)) {
            return false;
        }
        if (!data.transitionJob(job.jobId(), OperationJobLifecycleRecord.Status.QUEUED, gameTick, decision.reason())) {
            return false;
        }
        return data.recordPlantEvent(
                OperationPlantEvent.Type.QUEUE,
                gameTick,
                normalizeSubject(queueId, "plant_queue"),
                job.jobId(),
                queueDetail("ENQUEUED", decision)
        );
    }

    public static boolean recordQueueDispatch(
            ServerLevel level,
            String queueId,
            OperationQueueRuntime.Decision decision,
            long gameTick
    ) {
        if (level == null || decision == null
                || decision.verdict() != OperationQueueRuntime.Verdict.ASSIGNED
                || decision.assignment() == null) {
            return false;
        }
        long jobId = decision.assignment().job().jobId();
        OperationPlantSavedData data = OperationPlantSavedData.get(level);
        if (data.jobLifecycle(jobId) == null) {
            if (!data.recordJobAdmitted(decision.assignment().job(), gameTick)) return false;
            if (!data.transitionJob(jobId, OperationJobLifecycleRecord.Status.QUEUED, gameTick, "RECOVERED_QUEUE_ADMISSION")) {
                return false;
            }
        }
        if (!data.transitionJob(jobId, OperationJobLifecycleRecord.Status.DISPATCHED, gameTick,
                decision.reason() + " resource=" + decision.assignment().resourceId())) {
            return false;
        }
        return data.recordPlantEvent(
                OperationPlantEvent.Type.QUEUE,
                gameTick,
                normalizeSubject(queueId, "plant_queue"),
                jobId,
                queueDetail("DISPATCHED resource=" + decision.assignment().resourceId(), decision)
        );
    }

    public static boolean recordJobInProcess(
            ServerLevel level,
            long jobId,
            String resourceId,
            long gameTick
    ) {
        if (level == null) return false;
        OperationPlantSavedData data = OperationPlantSavedData.get(level);
        return data.transitionJob(
                jobId,
                OperationJobLifecycleRecord.Status.IN_PROCESS,
                gameTick,
                "IN_PROCESS resource=" + normalizeSubject(resourceId, "unknown_resource")
        );
    }

    public static boolean recordQueueCompletion(
            ServerLevel level,
            String queueId,
            OperationQueueRuntime.Decision decision,
            long gameTick
    ) {
        if (level == null || decision == null
                || decision.verdict() != OperationQueueRuntime.Verdict.COMPLETED
                || decision.assignment() == null) {
            return false;
        }
        long jobId = decision.assignment().job().jobId();
        OperationPlantSavedData data = OperationPlantSavedData.get(level);
        if (!data.transitionJob(jobId, OperationJobLifecycleRecord.Status.COMPLETED, gameTick, decision.reason())) {
            return false;
        }
        return data.recordPlantEvent(
                OperationPlantEvent.Type.QUEUE,
                gameTick,
                normalizeSubject(queueId, "plant_queue"),
                jobId,
                queueDetail("COMPLETED", decision)
        );
    }

    public static boolean recordQualityInspection(
            ServerLevel level,
            String inspectionId,
            OperationQualityInspectionEvidence evidence,
            long gameTick
    ) {
        if (level == null || evidence == null) return false;
        String detail = "output=" + evidence.outputId()
                + " inspected=" + evidence.inspectedUnits()
                + " good=" + evidence.goodUnits()
                + " reject=" + evidence.rejectUnits()
                + " rework=" + evidence.reworkUnits()
                + " evidence=" + evidence.evidenceQuality().name()
                + " confirmed=" + evidence.inspectionConfirmed()
                + " fault=" + evidence.faultActive();
        return OperationPlantSavedData.get(level).recordPlantEvent(
                OperationPlantEvent.Type.QUALITY,
                gameTick,
                normalizeSubject(inspectionId, "output:" + evidence.outputId()),
                evidence.jobId(),
                detail
        );
    }

    public static boolean recordMaintenanceDecision(
            ServerLevel level,
            OperationMaintenanceRuntime.Decision decision,
            long gameTick
    ) {
        if (level == null || decision == null) return false;
        if (!decision.changed() && decision.verdict() != OperationMaintenanceRuntime.Verdict.FAULT) return false;
        OperationResourceMaintenanceSnapshot snapshot = decision.nextState();
        String maintenanceId = snapshot.maintenanceId() == null ? "none" : snapshot.maintenanceId();
        String detail = "verdict=" + decision.verdict().name()
                + " state=" + snapshot.state().name()
                + " maintenance=" + maintenanceId
                + " evidence=" + snapshot.evidenceQuality().name()
                + " fault=" + snapshot.faultActive()
                + " reason=" + decision.reason();
        return OperationPlantSavedData.get(level).recordPlantEvent(
                OperationPlantEvent.Type.MAINTENANCE,
                gameTick,
                snapshot.resourceId(),
                -1,
                detail
        );
    }

    public static boolean recordMaintenanceSnapshot(
            ServerLevel level,
            OperationResourceMaintenanceSnapshot snapshot,
            long gameTick,
            String reason
    ) {
        if (level == null || snapshot == null) return false;
        String maintenanceId = snapshot.maintenanceId() == null ? "none" : snapshot.maintenanceId();
        String detail = "state=" + snapshot.state().name()
                + " maintenance=" + maintenanceId
                + " evidence=" + snapshot.evidenceQuality().name()
                + " fault=" + snapshot.faultActive()
                + " reason=" + normalizeSubject(reason, "SNAPSHOT");
        return OperationPlantSavedData.get(level).recordPlantEvent(
                OperationPlantEvent.Type.MAINTENANCE,
                gameTick,
                snapshot.resourceId(),
                -1,
                detail
        );
    }

    public static boolean recordLogisticsDemand(
            ServerLevel level,
            OperationTransportDemand demand,
            long gameTick
    ) {
        if (level == null || demand == null) return false;
        String detail = "status=DEMAND_CREATED"
                + " output=" + demand.outputId()
                + " source=" + demand.source().asLong()
                + " target=" + demand.target().asLong()
                + " units=" + demand.units()
                + " priority=" + demand.priority();
        return OperationPlantSavedData.get(level).recordPlantEvent(
                OperationPlantEvent.Type.LOGISTICS,
                gameTick,
                "mission:" + demand.missionId(),
                -1,
                detail
        );
    }

    public static boolean recordLogisticsEvent(
            ServerLevel level,
            long missionId,
            long outputId,
            long gameTick,
            String status,
            String detail
    ) {
        if (level == null || missionId < 0 || outputId < 0 || gameTick < 0) return false;
        String normalizedStatus = normalizeSubject(status, "UNKNOWN");
        String normalizedDetail = detail == null || detail.isBlank() ? "" : " " + detail.trim();
        return OperationPlantSavedData.get(level).recordPlantEvent(
                OperationPlantEvent.Type.LOGISTICS,
                gameTick,
                "mission:" + missionId,
                -1,
                "status=" + normalizedStatus + " output=" + outputId + normalizedDetail
        );
    }

    private static String queueDetail(String action, OperationQueueRuntime.Decision decision) {
        return "action=" + action
                + " queued=" + decision.nextState().queued().size()
                + " active=" + decision.nextState().active().size()
                + " capacity=" + decision.nextState().capacity()
                + " reason=" + decision.reason();
    }

    private static String normalizeSubject(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value.trim();
    }
}
