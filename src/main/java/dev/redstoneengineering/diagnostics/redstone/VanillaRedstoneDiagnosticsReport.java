package dev.redstoneengineering.diagnostics.redstone;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** Immutable observer-only summary of a bounded vanilla-redstone component cluster. */
public record VanillaRedstoneDiagnosticsReport(
        BlockPos anchor,
        int nodeCount,
        int dustCount,
        int poweredDustCount,
        int repeaterCount,
        int comparatorCount,
        int observerCount,
        int actuatorCount,
        int sourceCount,
        int maxDustPower,
        int configuredRepeaterDelayGameTicks,
        int maxAdjacentRelevantDegree,
        int possibleQcDependencyCount,
        int unloadedBoundaryCount,
        boolean traversalCapped,
        BlockPos structuralHotspot
) {
    public static VanillaRedstoneDiagnosticsReport notRedstone(BlockPos anchor) {
        return new VanillaRedstoneDiagnosticsReport(anchor.immutable(), 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, false, anchor.immutable());
    }

    public boolean isVanillaRedstone() {
        return nodeCount > 0;
    }

    /**
     * Structural advisories, not proven runtime faults. These names intentionally distinguish
     * bounded static evidence from exact update-order or performance measurements.
     */
    public List<String> advisories() {
        List<String> advisories = new ArrayList<>();
        if (possibleQcDependencyCount > 0) advisories.add("POSSIBLE_QC_DEPENDENCY");
        if (observerCount >= 8) advisories.add("OBSERVER_DENSE");
        if (maxAdjacentRelevantDegree >= 5) advisories.add("HIGH_LOCAL_FANOUT");
        if (nodeCount >= 192) advisories.add("DENSE_COMPONENT_CLUSTER");
        if (unloadedBoundaryCount > 0) advisories.add("UNLOADED_BOUNDARY");
        if (traversalCapped) advisories.add("PROFILE_TRUNCATED");
        return List.copyOf(advisories);
    }

    /** Only incomplete-observation conditions are treated as topology-alarm faults in phase one. */
    public boolean hasTopologyIssue() {
        return unloadedBoundaryCount > 0 || traversalCapped;
    }

    public int timingComponentCount() {
        return repeaterCount + comparatorCount + observerCount;
    }

    public String summary() {
        if (!isVanillaRedstone()) return "VANILLA REDSTONE | target not recognized";
        String advisory = advisories().isEmpty() ? "NONE" : String.join(",", advisories());
        return "VANILLA REDSTONE | nodes=" + nodeCount
                + " | dust=" + dustCount
                + " powered=" + poweredDustCount
                + " max=" + maxDustPower
                + " | timing=" + timingComponentCount()
                + " delayCfg=" + configuredRepeaterDelayGameTicks + "gt"
                + " | fanoutProxy=" + maxAdjacentRelevantDegree
                + " | QC-risk=" + possibleQcDependencyCount
                + " | advisory=" + advisory;
    }
}
