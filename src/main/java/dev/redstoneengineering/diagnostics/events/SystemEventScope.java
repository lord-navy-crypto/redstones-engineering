package dev.redstoneengineering.diagnostics.events;

import net.minecraft.core.BlockPos;

/**
 * Bounded spatial scope used by plant-level operations views.
 *
 * <p>The timeline remains level-wide evidence storage, while consumers such as an Operations
 * Dashboard select only events near their plant anchor. This prevents unrelated factories in the
 * same dimension from contaminating first-out and incident views.</p>
 */
public record SystemEventScope(BlockPos anchor, int radiusBlocks) {
    public static final int DEFAULT_PLANT_RADIUS = 32;
    public static final int MAX_RADIUS = 128;

    public SystemEventScope {
        if (anchor == null) throw new IllegalArgumentException("scope anchor must not be null");
        anchor = anchor.immutable();
        radiusBlocks = Math.max(1, Math.min(MAX_RADIUS, radiusBlocks));
    }

    public static SystemEventScope around(BlockPos anchor) {
        return new SystemEventScope(anchor, DEFAULT_PLANT_RADIUS);
    }

    public boolean contains(BlockPos pos) {
        if (pos == null) return false;
        long dx = (long) pos.getX() - anchor.getX();
        long dy = (long) pos.getY() - anchor.getY();
        long dz = (long) pos.getZ() - anchor.getZ();
        long r = radiusBlocks;
        return dx * dx + dy * dy + dz * dz <= r * r;
    }
}
