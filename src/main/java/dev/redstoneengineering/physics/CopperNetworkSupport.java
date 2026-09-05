package dev.redstoneengineering.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * Small lifecycle helper for copper-domain topology changes.
 *
 * <p>Copper components may split one energized graph into several independent islands when a
 * wire, junction, source, load, or processor is removed. Recomputing only the changed position
 * is not sufficient after the old block is gone, so every loaded neighbor is used as a seed.
 * Each seed remains bounded by {@link NetworkKernel#MAX_NODES} inside {@link DomainNetwork}.</p>
 */
public final class CopperNetworkSupport {
    private CopperNetworkSupport() {}

    public static void recomputeAround(ServerLevel level, BlockPos changedPos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = changedPos.relative(direction);
            if (level.hasChunkAt(neighbor)) {
                DomainNetwork.recomputeCopper(level, neighbor);
            }
        }
    }
}
