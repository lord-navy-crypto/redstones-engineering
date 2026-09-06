package dev.redstoneengineering.diagnostics.redstone;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Immutable advisory report derived from bounded vanilla-redstone observations.
 *
 * <p>Pulse widths are complete observed active-to-inactive intervals only. Feedback, QC, and
 * order-sensitivity fields are evidence/candidates and must not be interpreted as causal proof.</p>
 */
public record VanillaRedstoneBehaviorReport(
        BlockPos anchor,
        int radiusBlocks,
        long windowTicks,
        int observations,
        int observedStateTransitions,
        int completeObservedPulses,
        long minObservedPulseWidthTicks,
        long maxObservedPulseWidthTicks,
        int narrowObservedPulses,
        int observerFeedbackCandidates,
        int possibleQcDependencyCount,
        int qcCorrelatedRuntimeEvents,
        int recurrentObservedOrderPairs,
        int orderSensitivityEvidenceScore,
        String orderSensitivityConfidence,
        List<String> advisories
) {
    public boolean hasCompletePulseEvidence() {
        return completeObservedPulses > 0;
    }

    public boolean hasQcActivityCorrelation() {
        return possibleQcDependencyCount > 0 && qcCorrelatedRuntimeEvents > 0;
    }

    public boolean hasOrderSensitivityCandidate() {
        return !"NONE".equals(orderSensitivityConfidence);
    }

    public String summary() {
        String pulse = hasCompletePulseEvidence()
                ? completeObservedPulses + " complete " + minObservedPulseWidthTicks + ".." + maxObservedPulseWidthTicks + "gt"
                : "none-complete";
        String advisory = advisories.isEmpty() ? "NONE" : String.join(",", advisories);
        return "BEHAVIOR | pulses=" + pulse
                + " | observerReturns=" + observerFeedbackCandidates
                + " | QC=" + possibleQcDependencyCount + "/activity=" + qcCorrelatedRuntimeEvents
                + " | recurrentOrderPairs=" + recurrentObservedOrderPairs
                + " | orderCandidate=" + orderSensitivityConfidence
                + " score=" + orderSensitivityEvidenceScore
                + " | advisory=" + advisory
                + " | PULSE=OBSERVED_COMPLETE_ONLY"
                + " | QC=CORRELATION_NOT_CAUSATION"
                + " | ORDER=OBSERVED_EVENT_ORDER_ONLY";
    }
}
