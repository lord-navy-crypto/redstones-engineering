package dev.redstoneengineering.physics;

import dev.redstoneengineering.block.ConnectedCableBlock;
import dev.redstoneengineering.block.DifferentialDataPairBlock;
import dev.redstoneengineering.block.DifferentialDriverBlock;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.block.RedstoneCableJunctionBlock;
import dev.redstoneengineering.block.TransmissionTopology;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Bounded one-bit high-integrity link.
 *
 * <p>RSE differential data intentionally sacrifices payload density for stronger link margin than
 * framed serial wiring. It is suited to discrete control, heartbeat and protection-state signals;
 * it is not a byte-stream replacement and does not attempt to reproduce real-world line voltages.</p>
 *
 * <p>Direct pair-to-pair continuity is planar. Vertical transitions require a Signal Junction
 * Point resolved to DIFFERENTIAL, and mixed-media junctions are excluded from the graph.</p>
 */
public final class DifferentialNetwork {
    private DifferentialNetwork() {}

    private static boolean isNode(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof DifferentialDataPairBlock
                || state.getBlock() instanceof RedstoneCableJunctionBlock
                && state.getValue(RedstoneCableJunctionBlock.MEDIUM) == TransmissionTopology.SignalMedium.DIFFERENTIAL;
    }

    private static boolean edgeAllowed(Level level, BlockPos from, BlockPos to, Direction direction) {
        BlockState a = level.getBlockState(from);
        BlockState b = level.getBlockState(to);
        if (!isNode(level, from) || !isNode(level, to)) return false;
        boolean aJunction = a.getBlock() instanceof RedstoneCableJunctionBlock;
        boolean bJunction = b.getBlock() instanceof RedstoneCableJunctionBlock;
        if (aJunction && bJunction) return false;
        if (!aJunction && !bJunction && direction.getAxis() == Direction.Axis.Y) return false;
        return ConnectedCableBlock.connected(a, direction)
                && ConnectedCableBlock.connected(b, direction.getOpposite());
    }

    public static Set<BlockPos> collect(Level level, BlockPos start) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        if (!isNode(level, start)) return seen;
        queue.add(start);
        while (!queue.isEmpty() && seen.size() < NetworkKernel.MAX_NODES) {
            BlockPos pos = queue.removeFirst();
            if (!seen.add(pos)) continue;
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (level.hasChunkAt(next)
                        && isNode(level, next)
                        && edgeAllowed(level, pos, next, direction)
                        && !seen.contains(next)) {
                    queue.addLast(next);
                }
            }
        }
        return seen;
    }

    public static void drive(ServerLevel level, BlockPos start, int bit) {
        Set<BlockPos> nodes = collect(level, start);
        if (nodes.isEmpty()) return;
        int resolvedBit = bit & 1;
        int quality = Math.max(70, 100 - Math.max(0, nodes.size() - 1) / 8);
        for (BlockPos pos : nodes) {
            int oldBit = InformationRuntime.value(level, "diff", pos) & 1;
            int oldQuality = InformationRuntime.quality(level, "diff", pos);
            boolean oldValid = InformationRuntime.valid(level, "diff", pos);
            boolean effectiveChanged = oldBit != resolvedBit || oldQuality != quality || !oldValid;
            InformationRuntime.write(level, "diff", pos, resolvedBit, 0, true, quality);
            if (effectiveChanged) {
                level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
            }
        }
        NetworkKernel.recordScan(level, "diff", nodes.size(), nodes.size() >= NetworkKernel.MAX_NODES);
    }

    public static void recompute(ServerLevel level, BlockPos start) {
        Set<BlockPos> nodes = collect(level, start);
        if (nodes.isEmpty()) return;

        BlockPos driverPos = null;
        int bit = 0;
        for (BlockPos pairPos : nodes) {
            if (!(level.getBlockState(pairPos).getBlock() instanceof DifferentialDataPairBlock)) continue;
            for (Direction direction : Direction.values()) {
                BlockPos candidatePos = pairPos.relative(direction);
                if (!level.hasChunkAt(candidatePos)) continue;
                BlockState candidateState = level.getBlockState(candidatePos);
                if (!(candidateState.getBlock() instanceof DifferentialDriverBlock)) continue;
                Direction output = candidateState.getValue(DirectionalDomainBlock.FACING);
                if (!candidatePos.relative(output).equals(pairPos)) continue;
                if (!InformationRuntime.valid(level, "diff_out", candidatePos)) continue;
                if (driverPos != null && !driverPos.equals(candidatePos)) {
                    NetworkKernel.recordDriverState(level, "diff", 2);
                    invalidate(level, nodes);
                    return;
                }
                driverPos = candidatePos.immutable();
                bit = InformationRuntime.value(level, "diff_out", candidatePos) & 1;
            }
        }

        if (driverPos == null) {
            NetworkKernel.recordDriverState(level, "diff", 0);
            invalidate(level, nodes);
            return;
        }

        NetworkKernel.recordDriverState(level, "diff", 1);
        drive(level, start, bit);
    }

    public static void invalidate(ServerLevel level, Set<BlockPos> nodes) {
        for (BlockPos pos : nodes) {
            int oldBit = InformationRuntime.value(level, "diff", pos) & 1;
            int oldQuality = InformationRuntime.quality(level, "diff", pos);
            boolean oldValid = InformationRuntime.valid(level, "diff", pos);
            boolean effectiveChanged = oldBit != 0 || oldQuality != 0 || oldValid;
            InformationRuntime.write(level, "diff", pos, 0, 0, false, 0);
            if (effectiveChanged) {
                level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
            }
        }
    }

    public static void clearNode(Level level, BlockPos pos) {
        InformationRuntime.clear(level, "diff", pos);
    }
}
