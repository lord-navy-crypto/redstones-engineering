package dev.redstoneengineering.physics;

import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.HoneyVibrationDamperBlock;
import dev.redstoneengineering.block.MechanicalVibrationReceiverBlock;
import dev.redstoneengineering.block.SlimeVibrationConduitBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** Guided solid-vibration model: slime is low damping, honey is high damping. */
public final class VibrationNetwork {
    private VibrationNetwork() {}

    public record Wave(int amplitude, int frequency, boolean valid) {}

    private record Node(BlockPos pos, int amplitude, Direction arrivalSide) {}

    /**
     * Propagate from a source through all six adjacent faces. Kept for compatibility
     * with older callers; engineered exciters use the directional overload below.
     */
    public static void propagate(ServerLevel level, BlockPos source, int amplitude, int frequency) {
        propagate(level, source, amplitude, frequency, Direction.values());
    }

    /** Propagate only through the explicitly declared source output faces. */
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

        int visited = 0;
        while (!queue.isEmpty() && visited < NetworkKernel.MAX_NODES) {
            Node node = queue.removeFirst();
            if (node.amplitude <= 0 || !level.hasChunkAt(node.pos)) continue;
            int previous = best.getOrDefault(node.pos, -1);
            if (previous >= node.amplitude) continue;
            best.put(node.pos, node.amplitude);
            visited++;

            var state = level.getBlockState(node.pos);
            var block = state.getBlock();
            int loss;

            if (block instanceof SlimeVibrationConduitBlock) {
                loss = 1;
                InformationRuntime.write(level, "mech_wave", node.pos,
                        node.amplitude, boundedFrequency, true, 100);
                level.scheduleTick(node.pos, block, SlimeVibrationConduitBlock.PACKET_TTL_TICKS);
            } else if (block instanceof HoneyVibrationDamperBlock) {
                loss = 4;
                InformationRuntime.write(level, "mech_wave", node.pos,
                        node.amplitude, boundedFrequency, true, 80);
                level.scheduleTick(node.pos, block, HoneyVibrationDamperBlock.PACKET_TTL_TICKS);
            } else if (block instanceof MechanicalVibrationReceiverBlock) {
                Direction expectedInput = state.getValue(DirectionalSignalBlock.FACING).getOpposite();
                if (node.arrivalSide != expectedInput) continue;
                InformationRuntime.write(level, "mech_wave", node.pos,
                        node.amplitude, boundedFrequency, true, 100);
                level.scheduleTick(node.pos, block, 1);
                continue;
            } else {
                continue;
            }

            int next = node.amplitude - loss;
            if (next <= 0) continue;
            for (Direction side : Direction.values()) {
                queue.addLast(new Node(node.pos.relative(side), next, side.getOpposite()));
            }
        }
        NetworkKernel.recordScan(level, "mechanical", visited, visited >= NetworkKernel.MAX_NODES);
    }

    public static Wave sample(Level level, BlockPos pos) {
        return new Wave(
                InformationRuntime.value(level, "mech_wave", pos),
                InformationRuntime.aux(level, "mech_wave", pos),
                InformationRuntime.valid(level, "mech_wave", pos)
        );
    }
}
