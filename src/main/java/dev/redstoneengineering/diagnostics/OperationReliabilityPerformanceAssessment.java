package dev.redstoneengineering.diagnostics;

import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.operations.OperationReliabilityWindowSnapshot;

import java.util.Collection;

/**
 * Observer-only reliability projection over explicit observation windows.
 *
 * <p>These are observed historical measures, not forecasts, certification claims, or automatic
 * maintenance triggers. Planned maintenance is excluded from fault downtime metrics.</p>
 */
public final class OperationReliabilityPerformanceAssessment {
    private OperationReliabilityPerformanceAssessment() {}

    public enum Coverage {
        COMPLETE,
        PARTIAL,
        INVALID
    }

    public record Snapshot(
            Coverage coverage,
            int observedResources,
            int validResources,
            int invalidResources,
            long observedTicks,
            long operatingTicks,
            long faultDowntimeTicks,
            long plannedMaintenanceTicks,
            int failureCount,
            int completedRepairCount,
            int observedAvailabilityPercent,
            long meanOperatingTicksPerFailure,
            long meanFaultDowntimeTicksPerCompletedRepair
    ) {
        public Snapshot {
            if (coverage == null) coverage = Coverage.INVALID;
            observedResources = Math.max(0, observedResources);
            validResources = Math.max(0, validResources);
            invalidResources = Math.max(0, invalidResources);
            observedTicks = Math.max(0, observedTicks);
            operatingTicks = Math.max(0, operatingTicks);
            faultDowntimeTicks = Math.max(0, faultDowntimeTicks);
            plannedMaintenanceTicks = Math.max(0, plannedMaintenanceTicks);
            failureCount = Math.max(0, failureCount);
            completedRepairCount = Math.max(0, completedRepairCount);
            observedAvailabilityPercent = clamp(observedAvailabilityPercent, 0, 100);
            meanOperatingTicksPerFailure = Math.max(0, meanOperatingTicksPerFailure);
            meanFaultDowntimeTicksPerCompletedRepair = Math.max(0, meanFaultDowntimeTicksPerCompletedRepair);
        }
    }

    public static Snapshot inspect(Collection<OperationReliabilityWindowSnapshot> windows) {
        if (windows == null || windows.isEmpty()) {
            return empty(Coverage.PARTIAL);
        }

        int observedResources = 0;
        int validResources = 0;
        int invalidResources = 0;
        long observedTicks = 0;
        long operatingTicks = 0;
        long faultDowntimeTicks = 0;
        long plannedMaintenanceTicks = 0;
        int failureCount = 0;
        int completedRepairs = 0;

        for (OperationReliabilityWindowSnapshot window : windows) {
            observedResources++;
            if (window == null || window.faultActive() || window.evidenceQuality() != PortQuality.VALID) {
                invalidResources++;
                continue;
            }
            validResources++;
            observedTicks += window.observedTicks();
            operatingTicks += window.operatingTicks();
            faultDowntimeTicks += window.faultDowntimeTicks();
            plannedMaintenanceTicks += window.plannedMaintenanceTicks();
            failureCount += window.failureCount();
            completedRepairs += window.completedRepairCount();
        }

        Coverage coverage = invalidResources > 0
                ? Coverage.INVALID
                : validResources == observedResources ? Coverage.COMPLETE : Coverage.PARTIAL;
        long productivePlusFault = operatingTicks + faultDowntimeTicks;
        int availability = percent(operatingTicks, productivePlusFault);
        long meanUptime = failureCount == 0 ? 0 : operatingTicks / failureCount;
        long meanRepair = completedRepairs == 0 ? 0 : faultDowntimeTicks / completedRepairs;

        return new Snapshot(
                coverage,
                observedResources,
                validResources,
                invalidResources,
                observedTicks,
                operatingTicks,
                faultDowntimeTicks,
                plannedMaintenanceTicks,
                failureCount,
                completedRepairs,
                availability,
                meanUptime,
                meanRepair
        );
    }

    private static Snapshot empty(Coverage coverage) {
        return new Snapshot(coverage, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static int percent(long value, long total) {
        return total <= 0 ? 0 : Math.round(value * 100.0F / total);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
