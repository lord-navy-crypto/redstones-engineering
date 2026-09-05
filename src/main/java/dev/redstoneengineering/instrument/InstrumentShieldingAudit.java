package dev.redstoneengineering.instrument;

import dev.redstoneengineering.block.ConnectedCableBlock;
import dev.redstoneengineering.block.InstrumentCableBlock;
import dev.redstoneengineering.block.ShieldedInstrumentCableBlock;
import dev.redstoneengineering.physics.NetworkKernel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Observer-only shielding audit for a physical instrument-cable component.
 * It never changes probe values or creates a second measurement solver.
 */
public final class InstrumentShieldingAudit {
    private InstrumentShieldingAudit() {}

    public static ShieldingSnapshot inspect(Level level, BlockPos start) {
        if (!(level.getBlockState(start).getBlock() instanceof InstrumentCableBlock)) {
            return new ShieldingSnapshot(0, 0, 0, true);
        }

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(start);
        int shielded = 0;
        int unshielded = 0;
        boolean bounded = true;

        while (!queue.isEmpty()) {
            if (visited.size() >= NetworkKernel.MAX_NODES) {
                bounded = false;
                break;
            }
            BlockPos pos = queue.removeFirst();
            if (!visited.add(pos) || !level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof InstrumentCableBlock)) continue;

            if (state.getBlock() instanceof ShieldedInstrumentCableBlock) shielded++;
            else unshielded++;

            for (Direction side : Direction.values()) {
                if (!ConnectedCableBlock.connected(state, side)) continue;
                BlockPos neighborPos = pos.relative(side);
                if (!level.hasChunkAt(neighborPos)) continue;
                BlockState neighbor = level.getBlockState(neighborPos);
                if (!(neighbor.getBlock() instanceof InstrumentCableBlock)) continue;
                if (!ConnectedCableBlock.connected(neighbor, side.getOpposite())) continue;
                if (!visited.contains(neighborPos)) queue.addLast(neighborPos);
            }
        }

        return new ShieldingSnapshot(visited.size(), shielded, unshielded, bounded);
    }

    public record ShieldingSnapshot(int cableNodes, int shieldedNodes, int unshieldedNodes, boolean bounded) {
        public int coveragePercent() {
            int total = shieldedNodes + unshieldedNodes;
            return total == 0 ? 0 : 100 * shieldedNodes / total;
        }

        public String integrity() {
            if (!bounded) return "TRUNCATED";
            if (cableNodes == 0) return "NO_CABLE";
            if (unshieldedNodes == 0) return "FULLY_SHIELDED";
            if (shieldedNodes == 0) return "UNSHIELDED";
            return "MIXED_SHIELDING";
        }
    }
}
