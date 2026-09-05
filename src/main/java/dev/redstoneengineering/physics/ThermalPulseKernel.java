package dev.redstoneengineering.physics;

import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.PhononConduitBlock;
import dev.redstoneengineering.block.ThermalPulseReceiverBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** End-game high-conductance thermal/phonon signalling with deliberately finite bandwidth. */
public final class ThermalPulseKernel {
    private ThermalPulseKernel() {}

    private record Node(BlockPos pos, int amplitude, Direction arrivalSide) {}

    /** Compatibility entry point for older omnidirectional emitters. */
    public static void send(ServerLevel level, BlockPos source, int heat) {
        send(level, source, heat, Direction.values());
    }

    /** Send a bounded thermal pulse only through explicitly declared output faces. */
    public static void send(ServerLevel level, BlockPos source, int heat, Direction... outputSides) {
        int boundedHeat = Math.max(0, Math.min(15, heat));
        ArrayDeque<Node> queue = new ArrayDeque<>();
        Map<BlockPos, Integer> best = new HashMap<>();
        for (Direction side : outputSides) {
            queue.add(new Node(source.relative(side), boundedHeat, side.getOpposite()));
        }

        int visited = 0;
        while (!queue.isEmpty() && visited < NetworkKernel.MAX_NODES) {
            Node node = queue.removeFirst();
            if (node.amplitude <= 0 || !level.hasChunkAt(node.pos)) continue;
            if (best.getOrDefault(node.pos, -1) >= node.amplitude) continue;
            best.put(node.pos, node.amplitude);
            visited++;

            var state = level.getBlockState(node.pos);
            var block = state.getBlock();
            if (block instanceof ThermalPulseReceiverBlock) {
                Direction expectedInput = state.getValue(DirectionalSignalBlock.FACING).getOpposite();
                if (node.arrivalSide != expectedInput) continue;
                InformationRuntime.write(level, "thermal_pulse", node.pos,
                        node.amplitude, 0, true, 100);
                level.scheduleTick(node.pos, block, 1);
                continue;
            }
            if (!(block instanceof PhononConduitBlock)) continue;

            InformationRuntime.write(level, "thermal_pulse", node.pos,
                    node.amplitude, 0, true, 100);
            level.scheduleTick(node.pos, block, PhononConduitBlock.PACKET_TTL_TICKS);

            int next = node.amplitude - 1;
            if (next <= 0) continue;
            for (Direction side : Direction.values()) {
                queue.addLast(new Node(node.pos.relative(side), next, side.getOpposite()));
            }
        }
        NetworkKernel.recordScan(level, "phonon", visited, visited >= NetworkKernel.MAX_NODES);
    }
}
