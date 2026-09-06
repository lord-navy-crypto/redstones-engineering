package dev.redstoneengineering.diagnostics.redstone;

import net.minecraft.core.BlockPos;

/**
 * Read-only tick-timing and listener-observation-order evidence for a bounded vanilla-redstone area.
 *
 * <p>All timing uses server game ticks. Observation sequence values preserve only the order in
 * which the RSE listener received retained events; they are not causal update order, scheduler
 * priority, or sub-tick timestamps.</p>
 */
public record VanillaRedstoneTimingReport(
        BlockPos anchor,
        int radiusBlocks,
        long windowTicks,
        int observationCount,
        int transitionObservationCount,
        int activeTickCount,
        long firstObservedTick,
        long lastObservedTick,
        long observedSpanTicks,
        long minInterObservationTicks,
        long maxInterObservationTicks,
        long minInterTransitionTicks,
        long maxInterTransitionTicks,
        int sameTickOrderedPairCount,
        int sameTickDistinctSourcePairCount,
        long firstObservationSequence,
        long lastObservationSequence
) {
    public static VanillaRedstoneTimingReport empty(BlockPos anchor, int radiusBlocks, long windowTicks) {
        return new VanillaRedstoneTimingReport(
                anchor.immutable(), radiusBlocks, windowTicks,
                0, 0, 0,
                -1L, -1L, 0L,
                -1L, -1L, -1L, -1L,
                0, 0, -1L, -1L);
    }

    public boolean hasTimingEvidence() {
        return observationCount > 0;
    }

    public boolean hasInterObservationEvidence() {
        return minInterObservationTicks >= 0L;
    }

    public boolean hasTransitionIntervalEvidence() {
        return minInterTransitionTicks >= 0L;
    }

    public boolean hasSameTickOrderEvidence() {
        return sameTickOrderedPairCount > 0;
    }

    private static String interval(long min, long max) {
        return min < 0L || max < 0L ? "n/a" : min + ".." + max + "gt";
    }

    public String summary() {
        if (!hasTimingEvidence()) {
            return "TIMING | observations=0/" + windowTicks + "t | ORDER=OBSERVED_EVENT_ORDER_ONLY";
        }
        return "TIMING | obs=" + observationCount
                + " activeTicks=" + activeTickCount
                + " span=" + observedSpanTicks + "gt"
                + " | interObs=" + interval(minInterObservationTicks, maxInterObservationTicks)
                + " | transitions=" + transitionObservationCount
                + " interTransition=" + interval(minInterTransitionTicks, maxInterTransitionTicks)
                + " | sameTickPairs=" + sameTickOrderedPairCount
                + " distinctSources=" + sameTickDistinctSourcePairCount
                + " | seq=" + firstObservationSequence + ".." + lastObservationSequence
                + " | ORDER=OBSERVED_EVENT_ORDER_ONLY";
    }
}
