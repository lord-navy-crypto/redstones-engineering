package dev.redstoneengineering.diagnostics;

import dev.redstoneengineering.operations.OperationQualityDispositionAssessment;

import java.util.Collection;

/**
 * Observer-only quality performance projection over confirmed quality dispositions.
 *
 * <p>This class computes directly supported counts and rates only. It does not change queue order,
 * generate rework, accept/reject material, or claim OEE. Incomplete or invalid source evidence is
 * surfaced through coverage rather than silently discarded.</p>
 */
public final class OperationQualityPerformanceAssessment {
    private OperationQualityPerformanceAssessment() {}

    public enum Coverage {
        COMPLETE,
        PARTIAL,
        INVALID
    }

    public record Snapshot(
            Coverage coverage,
            int observedLots,
            int completeLots,
            int incompleteLots,
            int invalidLots,
            int inspectedUnits,
            int goodUnits,
            int rejectUnits,
            int reworkUnits,
            int firstPassYieldPercent,
            int rejectRatePercent,
            int reworkRatePercent
    ) {
        public Snapshot {
            if (coverage == null) coverage = Coverage.INVALID;
            observedLots = Math.max(0, observedLots);
            completeLots = Math.max(0, completeLots);
            incompleteLots = Math.max(0, incompleteLots);
            invalidLots = Math.max(0, invalidLots);
            inspectedUnits = Math.max(0, inspectedUnits);
            goodUnits = Math.max(0, goodUnits);
            rejectUnits = Math.max(0, rejectUnits);
            reworkUnits = Math.max(0, reworkUnits);
            firstPassYieldPercent = clamp(firstPassYieldPercent, 0, 100);
            rejectRatePercent = clamp(rejectRatePercent, 0, 100);
            reworkRatePercent = clamp(reworkRatePercent, 0, 100);
        }

        public boolean fullyCovered() {
            return coverage == Coverage.COMPLETE;
        }
    }

    public static Snapshot inspect(Collection<OperationQualityDispositionAssessment.Snapshot> dispositions) {
        if (dispositions == null || dispositions.isEmpty()) {
            return new Snapshot(Coverage.PARTIAL, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        int observed = 0;
        int complete = 0;
        int incomplete = 0;
        int invalid = 0;
        int good = 0;
        int reject = 0;
        int rework = 0;

        for (OperationQualityDispositionAssessment.Snapshot disposition : dispositions) {
            observed++;
            if (disposition == null) {
                invalid++;
                continue;
            }
            switch (disposition.verdict()) {
                case COMPLETE -> {
                    complete++;
                    good += disposition.goodUnits();
                    reject += disposition.rejectUnits();
                    rework += disposition.reworkUnits();
                }
                case WAIT -> incomplete++;
                case SAFE_STOP, FAULT -> invalid++;
            }
        }

        int inspected = good + reject + rework;
        Coverage coverage = invalid > 0
                ? Coverage.INVALID
                : incomplete > 0 || complete != observed ? Coverage.PARTIAL : Coverage.COMPLETE;
        return new Snapshot(
                coverage,
                observed,
                complete,
                incomplete,
                invalid,
                inspected,
                good,
                reject,
                rework,
                percent(good, inspected),
                percent(reject, inspected),
                percent(rework, inspected)
        );
    }

    private static int percent(int value, int total) {
        return total <= 0 ? 0 : Math.round(value * 100.0F / total);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
