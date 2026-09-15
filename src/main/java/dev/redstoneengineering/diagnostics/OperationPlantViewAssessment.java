package dev.redstoneengineering.diagnostics;

/**
 * Read-only plant-level Industrial Operations decision-support projection.
 *
 * <p>This class intentionally does not rank alternatives, dispatch work, change maintenance state,
 * move material, or claim an optimal plant configuration. It only composes already-authoritative
 * observer evidence into a single view so trade-offs remain visible to the player.</p>
 */
public final class OperationPlantViewAssessment {
    private OperationPlantViewAssessment() {}

    public enum EvidenceCoverage {
        COMPLETE,
        PARTIAL,
        INVALID
    }

    public record Snapshot(
            EvidenceCoverage coverage,
            int throughputCyclesPerMinute,
            int queueNow,
            int queuePressurePercent,
            OperationBottleneckAssessment.Constraint bottleneckConstraint,
            String bottleneckWorkcellId,
            int constrainedWorkcells,
            int firstPassYieldPercent,
            int rejectRatePercent,
            int reworkRatePercent,
            int observedAvailabilityPercent,
            int failureCount,
            long meanOperatingTicksPerFailure,
            long meanFaultDowntimeTicksPerCompletedRepair,
            int overdueOutstandingJobs,
            int outstandingWithDueDate,
            boolean bottleneckPresent,
            boolean qualityLossObserved,
            boolean reliabilityLossObserved,
            boolean overdueWorkPresent
    ) {
        public Snapshot {
            if (coverage == null) coverage = EvidenceCoverage.INVALID;
            throughputCyclesPerMinute = Math.max(0, throughputCyclesPerMinute);
            queueNow = Math.max(0, queueNow);
            queuePressurePercent = clamp(queuePressurePercent, 0, 100);
            if (bottleneckConstraint == null) bottleneckConstraint = OperationBottleneckAssessment.Constraint.EVIDENCE_INVALID;
            if (bottleneckWorkcellId == null || bottleneckWorkcellId.isBlank()) bottleneckWorkcellId = "NONE";
            constrainedWorkcells = Math.max(0, constrainedWorkcells);
            firstPassYieldPercent = clamp(firstPassYieldPercent, 0, 100);
            rejectRatePercent = clamp(rejectRatePercent, 0, 100);
            reworkRatePercent = clamp(reworkRatePercent, 0, 100);
            observedAvailabilityPercent = clamp(observedAvailabilityPercent, 0, 100);
            failureCount = Math.max(0, failureCount);
            meanOperatingTicksPerFailure = Math.max(0, meanOperatingTicksPerFailure);
            meanFaultDowntimeTicksPerCompletedRepair = Math.max(0, meanFaultDowntimeTicksPerCompletedRepair);
            overdueOutstandingJobs = Math.max(0, overdueOutstandingJobs);
            outstandingWithDueDate = Math.max(0, outstandingWithDueDate);
        }
    }

    public static Snapshot inspect(
            OperationsDashboardSnapshot dashboard,
            OperationBottleneckAssessment.Snapshot bottleneck,
            OperationQualityPerformanceAssessment.Snapshot quality,
            OperationReliabilityPerformanceAssessment.Snapshot reliability,
            OperationDueDateExposureAssessment.Snapshot dueDates
    ) {
        if (dashboard == null || bottleneck == null || quality == null || reliability == null || dueDates == null) {
            return invalid();
        }

        EvidenceCoverage coverage = combineCoverage(quality, reliability, dueDates, bottleneck);
        IndustrialOperationsAssessment.Snapshot ops = dashboard.operations();
        OperationBottleneckAssessment.WorkcellSignal dominant = bottleneck.dominant();
        OperationBottleneckAssessment.Constraint constraint = dominant == null
                ? OperationBottleneckAssessment.Constraint.NONE
                : dominant.constraint();
        String workcellId = dominant == null ? "NONE" : dominant.workcellId();

        boolean qualityLoss = quality.completeLots() > 0
                && (quality.rejectUnits() > 0 || quality.reworkUnits() > 0);
        boolean reliabilityLoss = reliability.validResources() > 0
                && (reliability.failureCount() > 0 || reliability.observedAvailabilityPercent() < 100);

        return new Snapshot(
                coverage,
                ops.throughputCyclesPerMinute(),
                ops.queueNow(),
                ops.queuePressurePercent(),
                constraint,
                workcellId,
                bottleneck.constrainedWorkcells(),
                quality.firstPassYieldPercent(),
                quality.rejectRatePercent(),
                quality.reworkRatePercent(),
                reliability.observedAvailabilityPercent(),
                reliability.failureCount(),
                reliability.meanOperatingTicksPerFailure(),
                reliability.meanFaultDowntimeTicksPerCompletedRepair(),
                dueDates.overdueOutstandingJobs(),
                dueDates.outstandingWithDueDate(),
                bottleneck.hasConstraint(),
                qualityLoss,
                reliabilityLoss,
                dueDates.overdueWorkPresent()
        );
    }

    private static EvidenceCoverage combineCoverage(
            OperationQualityPerformanceAssessment.Snapshot quality,
            OperationReliabilityPerformanceAssessment.Snapshot reliability,
            OperationDueDateExposureAssessment.Snapshot dueDates,
            OperationBottleneckAssessment.Snapshot bottleneck
    ) {
        if (quality.coverage() == OperationQualityPerformanceAssessment.Coverage.INVALID
                || reliability.coverage() == OperationReliabilityPerformanceAssessment.Coverage.INVALID
                || dueDates.coverage() == OperationDueDateExposureAssessment.Coverage.INVALID
                || (bottleneck.dominant() != null
                    && bottleneck.dominant().constraint() == OperationBottleneckAssessment.Constraint.EVIDENCE_INVALID)) {
            return EvidenceCoverage.INVALID;
        }
        if (quality.coverage() == OperationQualityPerformanceAssessment.Coverage.PARTIAL
                || reliability.coverage() == OperationReliabilityPerformanceAssessment.Coverage.PARTIAL
                || dueDates.coverage() == OperationDueDateExposureAssessment.Coverage.PARTIAL) {
            return EvidenceCoverage.PARTIAL;
        }
        return EvidenceCoverage.COMPLETE;
    }

    private static Snapshot invalid() {
        return new Snapshot(
                EvidenceCoverage.INVALID,
                0, 0, 0,
                OperationBottleneckAssessment.Constraint.EVIDENCE_INVALID,
                "UNKNOWN",
                0,
                0, 0, 0,
                0, 0, 0, 0,
                0, 0,
                false, false, false, false
        );
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
