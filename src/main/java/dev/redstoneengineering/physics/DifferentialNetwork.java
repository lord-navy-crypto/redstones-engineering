package dev.redstoneengineering.physics;

import dev.redstoneengineering.block.DifferentialDataPairBlock;
import dev.redstoneengineering.block.DifferentialDriverBlock;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** Digital differential pair model with bounded propagation and topology recomputation. */
public final class DifferentialNetwork {
    private DifferentialNetwork() {}

    public static Set<BlockPos> collect(Level level, BlockPos start) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        if (!(level.getBlockState(start).getBlock() instanceof DifferentialDataPairBlock)) return seen;
        queue.add(start);
        while (!queue.isEmpty() && seen.size() < NetworkKernel.MAX_NODES) {
            BlockPos pos = queue.removeFirst();
            if (!seen.add(pos)) continue;
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (level.hasChunkAt(next)
                        && level.getBlockState(next).getBlock() instanceof DifferentialDataPairBlock
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
        int quality = Math.max(20, 100 - nodes.size() / 3);
        for (BlockPos pos : nodes) {
            InformationRuntime.write(level, "diff", pos, bit & 1, 0, true, quality);
            level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
        }
        NetworkKernel.recordScan(level, "diff", nodes.size(), nodes.size() >= NetworkKernel.MAX_NODES);
    }

    public static void recompute(ServerLevel level, BlockPos start) {
        Set<BlockPos> nodes = collect(level, start);
        if (nodes.isEmpty()) return;

        BlockPos driverPos = null;
        int bit = 0;
        for (BlockPos pairPos : nodes) {
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
            InformationRuntime.write(level, "diff", pos, 0, 0, false, 0);
            level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
        }
    }

    public static void clearNode(Level level, BlockPos pos) {
        InformationRuntime.clear(level, "diff", pos);
    }
}
