package dev.redstoneengineering.diagnostics;

import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.operations.OperationWorkcellCapacitySnapshot;

import java.util.Collection;
import java.util.Comparator;

/**
 * Observer-only finite-capacity bottleneck projection.
 *
 * <p>This class never dispatches work, changes queue order, mutates buffers, or controls machines.
 * It only classifies explicit workcell-capacity evidence and identifies the strongest current
 * constraint signal. The result is diagnostic evidence, not a claim of globally optimal plant design.</p>
 */
public final class OperationBottleneckAssessment {
    private OperationBottleneckAssessment() {}

    public enum Constraint {
        NONE,
        INPUT_STARVED,
        RESOURCE_SATURATED,
        OUTPUT_BLOCKED,
        FAULTED,
        EVIDENCE_INVALID
    }

    public record WorkcellSignal(
            String workcellId,
            Constraint constraint,
            int severityScore,
            int resourceUtilizationPercent,
            int queuedJobs,
            int inputWipPressurePercent,
            int outputWipPressurePercent,
            String reason
    ) {
        public WorkcellSignal {
            if (workcellId == null || workcellId.isBlank()) workcellId = "UNKNOWN";
            if (constraint == null) constraint = Constraint.EVIDENCE_INVALID;
            severityScore = clamp(severityScore, 0, 1000);
            resourceUtilizationPercent = clamp(resourceUtilizationPercent, 0, 100);
            queuedJobs = Math.max(0, queuedJobs);
            inputWipPressurePercent = clamp(inputWipPressurePercent, 0, 100);
            outputWipPressurePercent = clamp(outputWipPressurePercent, 0, 100);
            if (reason == null || reason.isBlank()) reason = "UNSPECIFIED";
        }
    }

    public record Snapshot(WorkcellSignal dominant, int evaluatedWorkcells, int constrainedWorkcells) {
        public Snapshot {
            evaluatedWorkcells = Math.max(0, evaluatedWorkcells);
            constrainedWorkcells = Math.max(0, Math.min(evaluatedWorkcells, constrainedWorkcells));
        }

        public boolean hasConstraint() {
            return dominant != null && dominant.constraint() != Constraint.NONE;
        }
    }

    public static Snapshot inspect(Collection<OperationWorkcellCapacitySnapshot> workcells) {
        if (workcells == null || workcells.isEmpty()) return new Snapshot(null, 0, 0);

        WorkcellSignal dominant = null;
        int evaluated = 0;
        int constrained = 0;
        for (OperationWorkcellCapacitySnapshot workcell : workcells) {
            WorkcellSignal signal = inspect(workcell);
            evaluated++;
            if (signal.constraint() != Constraint.NONE) constrained++;
            if (dominant == null || signalComparator().compare(signal, dominant) < 0) {
                dominant = signal;
            }
        }
        return new Snapshot(dominant, evaluated, constrained);
    }

    public static WorkcellSignal inspect(OperationWorkcellCapacitySnapshot workcell) {
        if (workcell == null) {
            return new WorkcellSignal("UNKNOWN", Constraint.EVIDENCE_INVALID, 1000,
                    0, 0, 0, 0, "WORKCELL_EVIDENCE_MISSING");
        }
        if (workcell.faultActive()) {
            return signal(workcell, Constraint.FAULTED, 950, "WORKCELL_FAULT_ACTIVE");
        }
        if (workcell.evidenceQuality() != PortQuality.VALID) {
            return signal(workcell, Constraint.EVIDENCE_INVALID, 900, "WORKCELL_EVIDENCE_INVALID");
        }
        if (workcell.outputBlocked()) {
            int score = 700 + Math.min(200, workcell.queuedJobs() * 10)
                    + workcell.resourceUtilizationPercent() / 10;
            return signal(workcell, Constraint.OUTPUT_BLOCKED, score, "OUTPUT_BUFFER_AT_CAPACITY");
        }
        if (workcell.resourcesSaturated() && workcell.queuedJobs() > 0) {
            int score = 500 + Math.min(300, workcell.queuedJobs() * 10)
                    + workcell.outputWipPressurePercent() / 10;
            return signal(workcell, Constraint.RESOURCE_SATURATED, score, "RESOURCE_CAPACITY_SATURATED_WITH_QUEUE");
        }
        if (workcell.inputStarved() && workcell.activeAssignments() == 0) {
            return signal(workcell, Constraint.INPUT_STARVED, 400, "INPUT_WIP_EMPTY");
        }
        return signal(workcell, Constraint.NONE, 0, "NO_FINITE_CAPACITY_CONSTRAINT");
    }

    private static WorkcellSignal signal(
            OperationWorkcellCapacitySnapshot workcell,
            Constraint constraint,
            int score,
            String reason
    ) {
        return new WorkcellSignal(
                workcell.workcellId(),
                constraint,
                score,
                workcell.resourceUtilizationPercent(),
                workcell.queuedJobs(),
                workcell.inputWipPressurePercent(),
                workcell.outputWipPressurePercent(),
                reason
        );
    }

    private static Comparator<WorkcellSignal> signalComparator() {
        return Comparator.comparingInt(WorkcellSignal::severityScore).reversed()
                .thenComparing(WorkcellSignal::workcellId);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
