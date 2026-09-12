package dev.redstoneengineering.physics;

import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.PhononConduitBlock;
import dev.redstoneengineering.block.ThermalPulseReceiverBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** End-game high-conductance thermal/phonon signalling with deliberately finite bandwidth. */
public final class ThermalPulseKernel {
    private ThermalPulseKernel() {}

    private record Node(BlockPos pos, int amplitude, Direction arrivalSide) {}
    private record Pending(int amplitude, int ttlTicks) {}

    /** Compatibility entry point for older omnidirectional emitters. */
    public static void send(ServerLevel level, BlockPos source, int heat) {
        send(level, source, heat, Direction.values());
    }

    /** Send a bounded thermal pulse only through explicitly declared output faces. */
    public static void send(ServerLevel level, BlockPos source, int heat, Direction... outputSides) {
        int boundedHeat = Math.max(0, Math.min(15, heat));
        ArrayDeque<Node> queue = new ArrayDeque<>();
        Map<BlockPos, Integer> best = new HashMap<>();
        Map<BlockPos, Pending> pending = new HashMap<>();
        Set<BlockPos> visitedNodes = new HashSet<>();
        for (Direction side : outputSides) {
            queue.add(new Node(source.relative(side), boundedHeat, side.getOpposite()));
        }

        boolean truncated = false;
        while (!queue.isEmpty()) {
            Node node = queue.removeFirst();
            if (node.amplitude <= 0 || !level.hasChunkAt(node.pos)) continue;

            var state = level.getBlockState(node.pos);
            var block = state.getBlock();
            boolean receiver = block instanceof ThermalPulseReceiverBlock;
            boolean conduit = block instanceof PhononConduitBlock;
            if (!receiver && !conduit) continue;
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

            pending.put(node.pos.immutable(), new Pending(node.amplitude, PhononConduitBlock.PACKET_TTL_TICKS));
            int next = node.amplitude - 1;
            if (next <= 0) continue;
            for (Direction side : Direction.values()) {
                queue.addLast(new Node(node.pos.relative(side), next, side.getOpposite()));
            }
        }

        NetworkKernel.recordScan(level, "phonon", visitedNodes.size(), truncated);
        for (Map.Entry<BlockPos, Pending> entry : pending.entrySet()) {
            BlockPos pos = entry.getKey();
            Pending packet = entry.getValue();
            var block = level.getBlockState(pos).getBlock();
            if (truncated) {
                InformationRuntime.write(level, "thermal_pulse", pos, 0, 0, false, 0);
            } else {
                InformationRuntime.write(level, "thermal_pulse", pos,
                        packet.amplitude(), 0, true, 100);
            }
            level.updateNeighborsAt(pos, block);
            level.scheduleTick(pos, block, packet.ttlTicks());
        }
    }
}
