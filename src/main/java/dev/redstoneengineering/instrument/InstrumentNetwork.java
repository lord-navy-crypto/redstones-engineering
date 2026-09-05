package dev.redstoneengineering.instrument;

import dev.redstoneengineering.block.ConnectedCableBlock;
import dev.redstoneengineering.block.InstrumentCableBlock;
import dev.redstoneengineering.block.ShieldedInstrumentCableBlock;
import dev.redstoneengineering.block.SignalProbeBlock;
import dev.redstoneengineering.block.TransmissionTopology;
import dev.redstoneengineering.physics.NetworkKernel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** Bounded instrumentation network whose graph follows the same physical ports shown by cable BlockState. */
public final class InstrumentNetwork {
    private static final int MAX_VISITED = NetworkKernel.MAX_NODES;
    private InstrumentNetwork() {}
    private record CableVisit(BlockPos pos, int depth) {}

    public static ProbeSnapshot scan(Level level, BlockPos instrumentPos) {
        int[] values = {-1, -1, -1, -1};
        int[] counts = {0, 0, 0, 0};
        ArrayDeque<CableVisit> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> seenProbes = new HashSet<>();
        boolean truncated = false;
        int maxCableDepth = 0;
        int maxProbeDepth = 0;
        int shieldedCableNodes = 0;
        int unshieldedCableNodes = 0;

        for (Direction direction : Direction.values()) {
            BlockPos neighbor = instrumentPos.relative(direction);
            if (!level.hasChunkAt(neighbor)) continue;
            BlockState state = level.getBlockState(neighbor);
            if (state.getBlock() instanceof InstrumentCableBlock
                    && ConnectedCableBlock.connected(state, direction.getOpposite())) {
                queue.add(new CableVisit(neighbor, 1));
            } else if (state.getBlock() instanceof SignalProbeBlock probe
                    && TransmissionTopology.instrumentPort(state, direction)) {
                if (seenProbes.add(neighbor)) recordProbe(level, neighbor, state, probe, values, counts);
            }
        }

        while (!queue.isEmpty()) {
            if (visited.size() >= MAX_VISITED) {
                truncated = true;
                break;
            }
            CableVisit visit = queue.removeFirst();
            BlockPos cablePos = visit.pos();
            if (!visited.add(cablePos)) continue;
            maxCableDepth = Math.max(maxCableDepth, visit.depth());
            BlockState cableState = level.getBlockState(cablePos);
            if (cableState.getBlock() instanceof ShieldedInstrumentCableBlock) shieldedCableNodes++;
            else if (cableState.getBlock() instanceof InstrumentCableBlock) unshieldedCableNodes++;

            for (Direction direction : Direction.values()) {
                if (!ConnectedCableBlock.connected(cableState, direction)) continue;
                BlockPos neighbor = cablePos.relative(direction);
                if (neighbor.equals(instrumentPos) || !level.hasChunkAt(neighbor)) continue;
                BlockState state = level.getBlockState(neighbor);

                if (state.getBlock() instanceof InstrumentCableBlock) {
                    if (ConnectedCableBlock.connected(state, direction.getOpposite()) && !visited.contains(neighbor)) {
                        queue.addLast(new CableVisit(neighbor, visit.depth() + 1));
                    }
                } else if (state.getBlock() instanceof SignalProbeBlock probe
                        && TransmissionTopology.instrumentPort(state, direction)) {
                    if (seenProbes.add(neighbor)) {
                        recordProbe(level, neighbor, state, probe, values, counts);
                        maxProbeDepth = Math.max(maxProbeDepth, visit.depth());
                    }
                }
            }
        }

        NetworkKernel.recordScan(level, "instrument", visited.size(), truncated);
        return new ProbeSnapshot(
                values, counts, !truncated, visited.size(), seenProbes.size(),
                maxCableDepth, maxProbeDepth, shieldedCableNodes, unshieldedCableNodes);
    }

    private static void recordProbe(Level level, BlockPos pos, BlockState state, SignalProbeBlock probe, int[] values, int[] counts) {
        int channel = state.getValue(SignalProbeBlock.CHANNEL);
        int value = probe.sample(level, pos, state);
        counts[channel]++;
        if (counts[channel] == 1) values[channel] = value;
        else values[channel] = -1;
    }

    public record ProbeSnapshot(
            int[] values,
            int[] counts,
            boolean bounded,
            int cableNodes,
            int probeNodes,
            int maxCableDepth,
            int maxProbeDepth,
            int shieldedCableNodes,
            int unshieldedCableNodes
    ) {
        public boolean valid(int channel) { return channel >= 0 && channel < 4 && counts[channel] == 1 && values[channel] >= 0; }
        public int valueOr(int channel, int fallback) { return valid(channel) ? values[channel] : fallback; }
        public int duplicateChannels() { int duplicates = 0; for (int count : counts) if (count > 1) duplicates++; return duplicates; }
        public int duplicateProbes() { int duplicates = 0; for (int count : counts) duplicates += Math.max(0, count - 1); return duplicates; }
        public int activeChannels() { int active = 0; for (int channel = 0; channel < 4; channel++) if (counts[channel] > 0) active++; return active; }
        public int validChannels() { int valid = 0; for (int channel = 0; channel < 4; channel++) if (valid(channel)) valid++; return valid; }
        public int shieldingCoveragePercent() {
            int total = shieldedCableNodes + unshieldedCableNodes;
            return total == 0 ? 0 : (100 * shieldedCableNodes) / total;
        }
        public String shieldingIntegrity() {
            if (!bounded) return "TRUNCATED";
            if (cableNodes == 0) return "NO_CABLE";
            if (unshieldedCableNodes == 0) return "FULLY_SHIELDED";
            if (shieldedCableNodes == 0) return "UNSHIELDED";
            return "MIXED_SHIELDING";
        }
        public String status(int channel) {
            if (channel < 0 || channel >= 4) return "INVALID CHANNEL";
            if (counts[channel] == 0) return "NO PROBE";
            if (counts[channel] > 1) return "AMBIGUOUS";
            return Integer.toString(values[channel]);
        }
        public String integrity() {
            if (!bounded) return "TRUNCATED";
            if (duplicateChannels() > 0) return "AMBIGUOUS";
            if (probeNodes == 0) return "NO_PROBES";
            return "OK";
        }
        public String networkStatus() {
            return "instrumentNet cables=" + cableNodes
                    + " probes=" + probeNodes
                    + " channels=" + validChannels() + "/" + activeChannels() + "/4 valid/active"
                    + " duplicateChannels=" + duplicateChannels()
                    + " duplicateProbes=" + duplicateProbes()
                    + " depth=" + maxProbeDepth
                    + " cableDepth=" + maxCableDepth
                    + " shielded=" + shieldedCableNodes + "/" + cableNodes
                    + " shieldingCoverage=" + shieldingCoveragePercent() + "%"
                    + " shielding=" + shieldingIntegrity()
                    + " scan=" + (bounded ? "BOUNDED" : "TRUNCATED")
                    + " integrity=" + integrity();
        }
    }
}
