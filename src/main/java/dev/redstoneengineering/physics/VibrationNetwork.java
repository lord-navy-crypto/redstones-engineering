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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Guided solid-vibration model: slime is low damping, honey is high damping. */
public final class VibrationNetwork {
    private VibrationNetwork() {}

    public record Wave(int amplitude, int frequency, boolean valid) {}

    private record Node(BlockPos pos, int amplitude, Direction arrivalSide) {}
    private record Pending(int amplitude, int quality, int ttlTicks) {}

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
            boolean receiver = block instanceof MechanicalVibrationReceiverBlock;
            boolean conduit = block instanceof SlimeVibrationConduitBlock;
            boolean damper = block instanceof HoneyVibrationDamperBlock;
            if (!receiver && !conduit && !damper) continue;
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
                pending.put(node.pos.immutable(), new Pending(node.amplitude, 100, 1));
                continue;
            }

            int loss = conduit ? 1 : 4;
            int quality = conduit ? 100 : 80;
            int ttl = conduit ? SlimeVibrationConduitBlock.PACKET_TTL_TICKS
                    : HoneyVibrationDamperBlock.PACKET_TTL_TICKS;
            pending.put(node.pos.immutable(), new Pending(node.amplitude, quality, ttl));

            int next = node.amplitude - loss;
            if (next <= 0) continue;
            for (Direction side : Direction.values()) {
                queue.addLast(new Node(node.pos.relative(side), next, side.getOpposite()));
            }
        }

        NetworkKernel.recordScan(level, "mechanical", visitedNodes.size(), truncated);
        for (Map.Entry<BlockPos, Pending> entry : pending.entrySet()) {
            BlockPos pos = entry.getKey();
            Pending packet = entry.getValue();
            var block = level.getBlockState(pos).getBlock();
            if (truncated) {
                InformationRuntime.write(level, "mech_wave", pos, 0, boundedFrequency, false, 0);
            } else {
                InformationRuntime.write(level, "mech_wave", pos,
                        packet.amplitude(), boundedFrequency, true, packet.quality());
            }
            level.updateNeighborsAt(pos, block);
            level.scheduleTick(pos, block, packet.ttlTicks());
        }
    }

    /** Observer-neutral coherent sample of one wave envelope. */
    public static Wave sample(Level level, BlockPos pos) {
        InformationRuntime.Snapshot snapshot = InformationRuntime.snapshot(level, "mech_wave", pos);
        return new Wave(snapshot.value(), snapshot.selector(), snapshot.valid());
    }
}
