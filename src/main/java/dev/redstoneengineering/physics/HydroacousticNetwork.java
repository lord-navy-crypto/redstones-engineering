package dev.redstoneengineering.physics;

import dev.redstoneengineering.block.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Discrete pressure-wave propagation through configured fluid-filled tubes. */
public final class HydroacousticNetwork {
    private HydroacousticNetwork() {}

    private record Node(BlockPos pos, int amplitude, Direction arrivalSide) {}
    private record Pending(int amplitude, int ttlTicks) {}

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
        Map<BlockPos, Pending> pending = new HashMap<>();
        Set<BlockPos> visitedNodes = new HashSet<>();
        for (Direction side : outputSides) {
            queue.add(new Node(source.relative(side), boundedAmplitude, side.getOpposite()));
        }

        boolean truncated = false;
        while (!queue.isEmpty()) {
            Node node = queue.removeFirst();
            if (node.amplitude <= 0 || !level.hasChunkAt(node.pos)) continue;

            var state = level.getBlockState(node.pos);
            var block = state.getBlock();
            boolean receiver = block instanceof HydroacousticReceiverBlock;
            boolean tube = block instanceof HydroacousticTubeBlock;
            if (!receiver && !tube) continue;
            if (receiver) {
                Direction expectedInput = state.getValue(DirectionalSignalBlock.FACING).getOpposite();
                if (node.arrivalSide != expectedInput) continue;
            }

            int previous = best.getOrDefault(node.pos, -1);
            if (previous >= node.amplitude) continue;
            boolean newNode = !visitedNodes.contains(node.pos);
            if (newNode && visitedNodes.size() >= NetworkKernel.MAX_NODES) {
                truncated = true;
                break;
            }
            visitedNodes.add(node.pos.immutable());
            best.put(node.pos.immutable(), node.amplitude);

            if (receiver) {
                pending.put(node.pos.immutable(), new Pending(node.amplitude, 1));
                continue;
            }

            pending.put(node.pos.immutable(), new Pending(node.amplitude, HydroacousticTubeBlock.PACKET_TTL_TICKS));
            int medium = state.getValue(HydroacousticTubeBlock.MEDIUM);
            int loss = medium == 0 ? 1 : medium == 1 ? 2 : 3;
            int next = node.amplitude - loss;
            if (next <= 0) continue;
            for (Direction side : Direction.values()) {
                queue.addLast(new Node(node.pos.relative(side), next, side.getOpposite()));
            }
        }

        NetworkKernel.recordScan(level, "hydro", visitedNodes.size(), truncated);
        for (Map.Entry<BlockPos, Pending> entry : pending.entrySet()) {
            BlockPos pos = entry.getKey();
            Pending packet = entry.getValue();
            var block = level.getBlockState(pos).getBlock();
            if (truncated) {
                InformationRuntime.write(level, "hydro", pos, 0, boundedFrequency, false, 0);
            } else {
                InformationRuntime.write(level, "hydro", pos,
                        packet.amplitude(), boundedFrequency, true, 100);
            }
            level.updateNeighborsAt(pos, block);
            level.scheduleTick(pos, block, packet.ttlTicks());
        }
    }
}
