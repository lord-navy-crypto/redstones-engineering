package dev.redstoneengineering.diagnostics;

import dev.redstoneengineering.operations.world.OperationJobLifecycleRecord;
import dev.redstoneengineering.operations.world.OperationPlantEvent;
import dev.redstoneengineering.operations.world.OperationPlantSavedData;
import net.minecraft.server.level.ServerLevel;

/**
 * Read-only observer projection of the world-backed Persistent Plant Runtime.
 *
 * <p>This assessment never dispatches work, mutates maintenance, moves material, or changes plant
 * state. It summarizes only evidence already retained by {@link OperationPlantSavedData}. Quality
 * rates are computed only from explicit inspection events that carry inspected/good/reject/rework
 * unit counts; quality-cleared receipts remain visible history but do not fabricate inspection KPIs.</p>
 */
public final class OperationPersistentPlantRuntimeAssessment {
    private OperationPersistentPlantRuntimeAssessment() {}

    public enum Coverage {
        COMPLETE,
        PARTIAL,
        EMPTY,
        INVALID
    }

    public record Snapshot(
            Coverage coverage,
            int retainedJobs,
            int activeJobs,
            int completedJobs,
            int retainedEvents,
            int queueEvents,
            int qualityEvents,
            int maintenanceEvents,
            int deliveryEvents,
            int logisticsEvents,
            int onTimeDeliveries,
            int lateDeliveries,
            int undatedDeliveries,
            int onTimeDeliveryPercent,
            int inspectedUnits,
            int goodUnits,
            int rejectUnits,
            int reworkUnits,
            int firstPassYieldPercent,
            int rejectRatePercent,
            int reworkRatePercent,
            int maintenanceFaultEvents,
            int outstandingWithDueDate,
            int overdueOutstandingJobs
    ) {
        public Snapshot {
            if (coverage == null) coverage = Coverage.INVALID;
            retainedJobs = nonNegative(retainedJobs);
            activeJobs = nonNegative(activeJobs);
            completedJobs = nonNegative(completedJobs);
            retainedEvents = nonNegative(retainedEvents);
            queueEvents = nonNegative(queueEvents);
            qualityEvents = nonNegative(qualityEvents);
            maintenanceEvents = nonNegative(maintenanceEvents);
            deliveryEvents = nonNegative(deliveryEvents);
            logisticsEvents = nonNegative(logisticsEvents);
            onTimeDeliveries = nonNegative(onTimeDeliveries);
            lateDeliveries = nonNegative(lateDeliveries);
            undatedDeliveries = nonNegative(undatedDeliveries);
            onTimeDeliveryPercent = percent(onTimeDeliveryPercent);
            inspectedUnits = nonNegative(inspectedUnits);
            goodUnits = nonNegative(goodUnits);
            rejectUnits = nonNegative(rejectUnits);
            reworkUnits = nonNegative(reworkUnits);
            firstPassYieldPercent = percent(firstPassYieldPercent);
            rejectRatePercent = percent(rejectRatePercent);
            reworkRatePercent = percent(reworkRatePercent);
            maintenanceFaultEvents = nonNegative(maintenanceFaultEvents);
            outstandingWithDueDate = nonNegative(outstandingWithDueDate);
            overdueOutstandingJobs = Math.max(0, Math.min(outstandingWithDueDate, overdueOutstandingJobs));
        }

        public boolean authoritative() {
            return coverage == Coverage.COMPLETE || coverage == Coverage.PARTIAL;
        }

        public boolean hasQualityInspectionMetrics() {
            return inspectedUnits > 0;
        }

        public boolean hasDatedDeliveryMetrics() {
            return onTimeDeliveries + lateDeliveries > 0;
        }
    }

    public static Snapshot inspect(ServerLevel level) {
        if (level == null) return invalid();
        OperationPlantSavedData data = OperationPlantSavedData.get(level);

        int retainedJobs = data.jobLifecycles().size();
        int activeJobs = 0;
        int completedJobs = 0;
        int outstandingWithDueDate = 0;
        int overdueOutstandingJobs = 0;
        long now = level.getGameTime();
        for (OperationJobLifecycleRecord job : data.jobLifecycles()) {
            if (job.status() == OperationJobLifecycleRecord.Status.COMPLETED) completedJobs++;
            if (!job.status().terminal()) {
                activeJobs++;
                if (job.hasDueDate()) {
                    outstandingWithDueDate++;
                    if (now > job.dueTick()) overdueOutstandingJobs++;
                }
            }
        }

        int queueEvents = 0;
        int qualityEvents = 0;
        int maintenanceEvents = 0;
        int deliveryEvents = 0;
        int logisticsEvents = 0;
        int onTimeDeliveries = 0;
        int lateDeliveries = 0;
        int undatedDeliveries = 0;
        int maintenanceFaultEvents = 0;
        long inspectedUnits = 0;
        long goodUnits = 0;
        long rejectUnits = 0;
        long reworkUnits = 0;

        for (OperationPlantEvent event : data.plantEvents()) {
            switch (event.type()) {
                case QUEUE -> queueEvents++;
                case QUALITY -> {
                    qualityEvents++;
                    InspectionUnits units = parseInspectionUnits(event.detail());
                    if (units != null) {
                        inspectedUnits = addBounded(inspectedUnits, units.inspected());
                        goodUnits = addBounded(goodUnits, units.good());
                        rejectUnits = addBounded(rejectUnits, units.reject());
                        reworkUnits = addBounded(reworkUnits, units.rework());
                    }
                }
                case MAINTENANCE -> {
                    maintenanceEvents++;
                    String detail = event.detail();
                    if (detail.contains("verdict=FAULT")
                            || detail.contains("state=FAILED")
                            || detail.contains("fault=true")) {
                        maintenanceFaultEvents++;
                    }
                }
                case DELIVERY -> {
                    deliveryEvents++;
                    if ("ON_TIME".equals(event.detail())) onTimeDeliveries++;
                    else if (event.detail().startsWith("LATE_BY_")) lateDeliveries++;
                    else if ("COMPLETED_NO_DUE_DATE".equals(event.detail())) undatedDeliveries++;
                }
                case LOGISTICS -> logisticsEvents++;
                case JOB -> { }
            }
        }

        int inspected = saturatingInt(inspectedUnits);
        int good = saturatingInt(goodUnits);
        int reject = saturatingInt(rejectUnits);
        int rework = saturatingInt(reworkUnits);
        int datedDeliveries = onTimeDeliveries + lateDeliveries;

        int firstPassYieldPercent = ratePercent(goodUnits, inspectedUnits);
        int rejectRatePercent = ratePercent(rejectUnits, inspectedUnits);
        int reworkRatePercent = ratePercent(reworkUnits, inspectedUnits);
        int onTimeDeliveryPercent = ratePercent(onTimeDeliveries, datedDeliveries);

        int retainedEvents = data.plantEvents().size();
        Coverage coverage;
        if (retainedJobs == 0 && retainedEvents == 0) {
            coverage = Coverage.EMPTY;
        } else if (retainedJobs > 0
                && queueEvents > 0
                && qualityEvents > 0
                && maintenanceEvents > 0
                && deliveryEvents > 0
                && logisticsEvents > 0) {
            coverage = Coverage.COMPLETE;
        } else {
            coverage = Coverage.PARTIAL;
        }

        return new Snapshot(
                coverage,
                retainedJobs,
                activeJobs,
                completedJobs,
                retainedEvents,
                queueEvents,
                qualityEvents,
                maintenanceEvents,
                deliveryEvents,
                logisticsEvents,
                onTimeDeliveries,
                lateDeliveries,
                undatedDeliveries,
                onTimeDeliveryPercent,
                inspected,
                good,
                reject,
                rework,
                firstPassYieldPercent,
                rejectRatePercent,
                reworkRatePercent,
                maintenanceFaultEvents,
                outstandingWithDueDate,
                overdueOutstandingJobs
        );
    }

    private record InspectionUnits(int inspected, int good, int reject, int rework) {}

    private static InspectionUnits parseInspectionUnits(String detail) {
        if (detail == null || detail.isBlank() || !detail.contains("inspected=")) return null;
        Integer inspected = tokenInt(detail, "inspected=");
        Integer good = tokenInt(detail, "good=");
        Integer reject = tokenInt(detail, "reject=");
        Integer rework = tokenInt(detail, "rework=");
        if (inspected == null || good == null || reject == null || rework == null) return null;
        if (inspected < 0 || good < 0 || reject < 0 || rework < 0) return null;
        if ((long) good + reject + rework != inspected) return null;
        return new InspectionUnits(inspected, good, reject, rework);
    }

    private static Integer tokenInt(String detail, String key) {
        int start = detail.indexOf(key);
        if (start < 0) return null;
        start += key.length();
        int end = start;
        while (end < detail.length() && Character.isDigit(detail.charAt(end))) end++;
        if (end == start) return null;
        try {
            return Integer.parseInt(detail.substring(start, end));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static long addBounded(long current, int delta) {
        return Math.min(Integer.MAX_VALUE, current + Math.max(0L, delta));
    }

    private static int ratePercent(long numerator, long denominator) {
        if (denominator <= 0) return 0;
        return percent((int) Math.round(numerator * 100.0 / denominator));
    }

    private static int saturatingInt(long value) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, value));
    }

    private static int nonNegative(int value) {
        return Math.max(0, value);
    }

    private static int percent(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static Snapshot invalid() {
        return new Snapshot(Coverage.INVALID,
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0,
                0, 0, 0);
    }
}
