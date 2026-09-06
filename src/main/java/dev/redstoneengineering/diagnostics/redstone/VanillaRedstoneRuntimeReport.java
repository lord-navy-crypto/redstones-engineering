package dev.redstoneengineering.diagnostics.redstone;

import net.minecraft.core.BlockPos;

/**
 * Read-only rolling telemetry derived from observed NeoForge NeighborNotifyEvent traffic.
 * Counts are event observations, not Minecraft internal solver-evaluation counts or TPS cost.
 */
public record VanillaRedstoneRuntimeReport(
        BlockPos anchor,
        int radiusBlocks,
        long windowTicks,
        int neighborNotificationEvents,
        int notifiedSideTotal,
        int forceRedstoneUpdateEvents,
        int observedStateTransitions,
        int uniqueSourcePositions,
        int hotspotEventCount,
        BlockPos hotspot
) {
    public boolean hasTelemetry() {
        return neighborNotificationEvents > 0;
    }

    public String summary() {
        if (!hasTelemetry()) {
            return "RUNTIME | NeighborNotifyEvent observations=0/" + windowTicks + "t";
        }
        return "RUNTIME | notifyEvents=" + neighborNotificationEvents + "/" + windowTicks + "t"
                + " | sides=" + notifiedSideTotal
                + " | forced=" + forceRedstoneUpdateEvents
                + " | observedTransitions=" + observedStateTransitions
                + " | sources=" + uniqueSourcePositions
                + " | hotspot=" + hotspot.getX() + "," + hotspot.getY() + "," + hotspot.getZ()
                + " (" + hotspotEventCount + ")";
    }
}
