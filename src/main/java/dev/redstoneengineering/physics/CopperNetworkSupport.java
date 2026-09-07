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
 * <p>Two rules are deliberately centralized here:</p>
 * <ul>
 *   <li>topology changes are recomputed from every loaded neighbor because a removed
 *       node may split one component into several independent islands;</li>
 *   <li>a terminal consumes only COPPER OUTPUT/BIDIRECTIONAL neighbors. INPUT-only
 *       loads are never allowed to back-drive another sink simply because they retain
 *       a measured voltage on their own body.</li>
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

    /**
     * Read the strongest legitimate adjacent Copper feed without ever treating an
     * INPUT-only device as a source. Multiple real feeds are allowed in the reduced
     * macroscopic model; their local level resolves to the strongest observed V-eq.
     */
    public static TerminalInput terminalInput(Level level, BlockPos consumerPos) {
        int feeds = 0;
        int bestVoltage = 0;
        boolean valid = false;
        PortQuality worst = PortQuality.NO_SIGNAL;

        for (Direction side : Direction.values()) {
            BlockPos neighborPos = consumerPos.relative(side);
            if (!level.hasChunkAt(neighborPos)) continue;
            BlockState neighborState = level.getBlockState(neighborPos);
            if (!(neighborState.getBlock() instanceof EngineeringPortProvider provider)) continue;

            Direction neighborFace = side.getOpposite();
            var descriptor = provider.engineeringPort(neighborState, neighborFace).orElse(null);
            if (descriptor == null || descriptor.domain() != EngineeringDomain.COPPER) continue;
            if (descriptor.direction() == PortDirection.INPUT) continue;

            feeds++;
            var snapshot = provider.engineeringSnapshot(level, neighborPos, neighborState, neighborFace).orElse(null);
            if (snapshot == null) continue;
            bestVoltage = Math.max(bestVoltage, (int) Math.round(snapshot.value()));
            if (snapshot.quality() == PortQuality.VALID) valid = true;
            worst = combineQuality(worst, snapshot.quality());
        }

        if (feeds == 0) return new TerminalInput(0, 0, PortQuality.NO_SIGNAL);
        if (worst == PortQuality.FAULT || worst == PortQuality.TOPOLOGY_ERROR
                || worst == PortQuality.DOMAIN_MISMATCH) {
            return new TerminalInput(feeds, 0, worst);
        }
        return new TerminalInput(feeds, Math.max(0, Math.min(15, bestVoltage)),
                valid ? PortQuality.VALID : PortQuality.NO_SIGNAL);
    }

    private static PortQuality combineQuality(PortQuality current, PortQuality next) {
        if (next == PortQuality.FAULT || current == PortQuality.FAULT) return PortQuality.FAULT;
        if (next == PortQuality.DOMAIN_MISMATCH || current == PortQuality.DOMAIN_MISMATCH) return PortQuality.DOMAIN_MISMATCH;
        if (next == PortQuality.TOPOLOGY_ERROR || current == PortQuality.TOPOLOGY_ERROR) return PortQuality.TOPOLOGY_ERROR;
        if (next == PortQuality.SATURATED || current == PortQuality.SATURATED) return PortQuality.SATURATED;
        if (next == PortQuality.STALE || current == PortQuality.STALE) return PortQuality.STALE;
        if (next == PortQuality.VALID || current == PortQuality.VALID) return PortQuality.VALID;
        return PortQuality.NO_SIGNAL;
    }
}
