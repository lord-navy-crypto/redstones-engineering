package dev.redstoneengineering.physics;

import dev.redstoneengineering.block.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** Discrete pressure-wave propagation through configured fluid-filled tubes. */
public final class HydroacousticNetwork {
    private HydroacousticNetwork() {}

    private record Node(BlockPos pos, int amplitude, Direction arrivalSide) {}

    /** Compatibility entry point: legacy sources radiate through every face. */
    public static void propagate(ServerLevel level, BlockPos source, int amplitude, int frequency) {
        propagate(level, source, amplitude, frequency, Direction.values());
    }

    /** Propagate only through the source faces explicitly declared by the endpoint. */
    public static void propagate(
            ServerLevel level,
            BlockPos source,
            int amplitude,
            int frequency,
            Direction... outputSides
    ) {
        int boundedAmplitude = Math.max(0, Math.min(15, amplitude));
        int boundedFrequency = Math.max(1, Math.min(15, frequency));
        ArrayDeque<Node> queue = new ArrayDeque<>();
        Map<BlockPos, Integer> best = new HashMap<>();
        for (Direction side : outputSides) {
            queue.add(new Node(source.relative(side), boundedAmplitude, side.getOpposite()));
        }

        int count = 0;
        while (!queue.isEmpty() && count < NetworkKernel.MAX_NODES) {
            Node node = queue.removeFirst();
            if (node.amplitude <= 0 || !level.hasChunkAt(node.pos)) continue;
            if (best.getOrDefault(node.pos, -1) >= node.amplitude) continue;
            best.put(node.pos, node.amplitude);
            count++;

            var state = level.getBlockState(node.pos);
            var block = state.getBlock();
            if (block instanceof HydroacousticReceiverBlock) {
                Direction expectedInput = state.getValue(DirectionalSignalBlock.FACING).getOpposite();
                if (node.arrivalSide != expectedInput) continue;
                InformationRuntime.write(level, "hydro", node.pos,
                        node.amplitude, boundedFrequency, true, 100);
                level.scheduleTick(node.pos, block, 1);
                continue;
            }
            if (!(block instanceof HydroacousticTubeBlock)) continue;

            InformationRuntime.write(level, "hydro", node.pos,
                    node.amplitude, boundedFrequency, true, 100);
            level.scheduleTick(node.pos, block, HydroacousticTubeBlock.PACKET_TTL_TICKS);

            int medium = state.getValue(HydroacousticTubeBlock.MEDIUM);
            int loss = medium == 0 ? 1 : medium == 1 ? 2 : 3;
            int next = node.amplitude - loss;
            if (next <= 0) continue;
            for (Direction side : Direction.values()) {
                queue.addLast(new Node(node.pos.relative(side), next, side.getOpposite()));
            }
        }
        NetworkKernel.recordScan(level, "hydro", count, count >= NetworkKernel.MAX_NODES);
    }
}
