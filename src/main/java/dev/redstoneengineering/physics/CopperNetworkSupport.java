package dev.redstoneengineering.physics;

import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lifecycle and terminal-input helpers for the simplified Copper domain.
 *
 * <p>Three rules are deliberately centralized here:</p>
 * <ul>
 *   <li>topology changes are recomputed from every loaded neighbor because a removed
 *       node may split one component into several independent islands;</li>
 *   <li>a terminal consumes only COPPER OUTPUT/BIDIRECTIONAL neighbors. INPUT-only
 *       loads are never allowed to back-drive another sink simply because they retain
 *       a measured voltage on their own body;</li>
 *   <li>a normal terminal accepts exactly one legitimate feed. Multiple feeds are an
 *       explicit topology error and must be combined by a Junction/aggregation device upstream.</li>
 * </ul>
 */
public final class CopperNetworkSupport {
    private CopperNetworkSupport() {}

    /** Observer-neutral aggregate of the physically adjacent Copper feeds of one terminal. */
    public record TerminalInput(int connectedFeeds, int voltage, PortQuality quality) {}

    public static void recomputeAround(ServerLevel level, BlockPos changedPos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = changedPos.relative(direction);
            if (level.hasChunkAt(neighbor)) {
                DomainNetwork.recomputeCopper(level, neighbor);
            }
        }
    }

    /** Read one physical terminal face. INPUT-only neighbors are never sources. */
    public static TerminalInput terminalInputOnSide(Level level, BlockPos consumerPos, Direction side) {
        BlockPos neighborPos = consumerPos.relative(side);
        if (!level.hasChunkAt(neighborPos)) return new TerminalInput(0, 0, PortQuality.NO_SIGNAL);
        BlockState neighborState = level.getBlockState(neighborPos);
        if (!(neighborState.getBlock() instanceof EngineeringPortProvider provider)) {
            return new TerminalInput(0, 0, PortQuality.NO_SIGNAL);
        }

        Direction neighborFace = side.getOpposite();
        var descriptor = provider.engineeringPort(neighborState, neighborFace).orElse(null);
        if (descriptor == null || descriptor.domain() != EngineeringDomain.COPPER
                || descriptor.direction() == PortDirection.INPUT) {
            return new TerminalInput(0, 0, PortQuality.NO_SIGNAL);
        }

        var snapshot = provider.engineeringSnapshot(level, neighborPos, neighborState, neighborFace).orElse(null);
        if (snapshot == null) return new TerminalInput(1, 0, PortQuality.NO_SIGNAL);
        int voltage = Math.max(0, Math.min(15, (int) Math.round(snapshot.value())));
        return new TerminalInput(1, voltage, snapshot.quality());
    }

    /**
     * Read exactly one legitimate adjacent Copper feed without ever treating an INPUT-only
     * device as a source. More than one physical feed is ambiguous parallel aggregation,
     * so the terminal fails closed with TOPOLOGY_ERROR instead of silently choosing max(V).
     */
    public static TerminalInput terminalInput(Level level, BlockPos consumerPos) {
        int feeds = 0;
        int voltage = 0;
        PortQuality quality = PortQuality.NO_SIGNAL;

        for (Direction side : Direction.values()) {
            TerminalInput face = terminalInputOnSide(level, consumerPos, side);
            if (face.connectedFeeds() == 0) continue;
            feeds += face.connectedFeeds();
            if (feeds == 1) {
                voltage = face.voltage();
                quality = face.quality();
            }
        }

        if (feeds == 0) return new TerminalInput(0, 0, PortQuality.NO_SIGNAL);
        if (feeds > 1) return new TerminalInput(feeds, 0, PortQuality.TOPOLOGY_ERROR);
        if (quality == PortQuality.FAULT || quality == PortQuality.TOPOLOGY_ERROR
                || quality == PortQuality.DOMAIN_MISMATCH || quality == PortQuality.STALE) {
            return new TerminalInput(feeds, 0, quality);
        }
        return new TerminalInput(feeds, Math.max(0, Math.min(15, voltage)), quality);
    }
}
