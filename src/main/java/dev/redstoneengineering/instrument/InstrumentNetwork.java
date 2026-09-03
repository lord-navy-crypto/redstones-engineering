package dev.redstoneengineering.instrument;

import dev.redstoneengineering.block.InstrumentCableBlock;
import dev.redstoneengineering.block.SignalProbeBlock;
import dev.redstoneengineering.physics.NetworkKernel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** Bounded instrumentation network carrying measurement probes rather than redstone power. */
public final class InstrumentNetwork {
    private static final int MAX_VISITED = NetworkKernel.MAX_NODES;

    private InstrumentNetwork() {}

    public static ProbeSnapshot scan(Level level, BlockPos instrumentPos) {
        int[] values = {-1, -1, -1, -1};
        int[] counts = {0, 0, 0, 0};

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> seenProbes = new HashSet<>();
        boolean truncated = false;

        for (Direction direction : Direction.values()) {
            BlockPos neighbor = instrumentPos.relative(direction);
            if (!level.hasChunkAt(neighbor)) continue;
            BlockState state = level.getBlockState(neighbor);

            if (state.getBlock() instanceof InstrumentCableBlock) {
                queue.add(neighbor);
            } else if (state.getBlock() instanceof SignalProbeBlock probe) {
                if (seenProbes.add(neighbor)) {
                    recordProbe(level, neighbor, state, probe, values, counts);
                }
            }
        }

        while (!queue.isEmpty()) {
            if (visited.size() >= MAX_VISITED) {
                truncated = true;
                break;
            }
            BlockPos cablePos = queue.removeFirst();
            if (!visited.add(cablePos)) continue;

            for (Direction direction : Direction.values()) {
                BlockPos neighbor = cablePos.relative(direction);
                if (neighbor.equals(instrumentPos) || !level.hasChunkAt(neighbor)) continue;

                BlockState state = level.getBlockState(neighbor);
                if (state.getBlock() instanceof InstrumentCableBlock) {
                    if (!visited.contains(neighbor)) queue.addLast(neighbor);
                } else if (state.getBlock() instanceof SignalProbeBlock probe) {
                    if (seenProbes.add(neighbor)) {
                        recordProbe(level, neighbor, state, probe, values, counts);
                    }
                }
            }
        }

        NetworkKernel.recordScan(level, "instrument", visited.size(), truncated);
        return new ProbeSnapshot(
                values,
                counts,
                !truncated,
                visited.size(),
                seenProbes.size()
        );
    }

    private static void recordProbe(
            Level level,
            BlockPos pos,
            BlockState state,
            SignalProbeBlock probe,
            int[] values,
            int[] counts
    ) {
        int channel = state.getValue(SignalProbeBlock.CHANNEL);
        int value = probe.sample(level, pos, state);
        counts[channel]++;
        if (counts[channel] == 1) values[channel] = value;
        else values[channel] = -1; // duplicate probes are deliberately invalid
    }

    public record ProbeSnapshot(
            int[] values,
            int[] counts,
            boolean bounded,
            int cableNodes,
            int probeNodes
    ) {
        public boolean valid(int channel) {
            return channel >= 0
                    && channel < 4
                    && counts[channel] == 1
                    && values[channel] >= 0;
        }

        public int valueOr(int channel, int fallback) {
            return valid(channel) ? values[channel] : fallback;
        }

        public int duplicateChannels() {
            int duplicates = 0;
            for (int count : counts) if (count > 1) duplicates++;
            return duplicates;
        }

        public int activeChannels() {
            int active = 0;
            for (int channel = 0; channel < 4; channel++) {
                if (counts[channel] > 0) active++;
            }
            return active;
        }

        public String status(int channel) {
            if (counts[channel] == 0) return "NO PROBE";
            if (counts[channel] > 1) return "AMBIGUOUS";
            return Integer.toString(values[channel]);
        }

        public String networkStatus() {
            return "instrumentNet cables=" + cableNodes
                    + " probes=" + probeNodes
                    + " channels=" + activeChannels() + "/4"
                    + " duplicateChannels=" + duplicateChannels()
                    + " scan=" + (bounded ? "BOUNDED" : "TRUNCATED");
        }
    }
}
