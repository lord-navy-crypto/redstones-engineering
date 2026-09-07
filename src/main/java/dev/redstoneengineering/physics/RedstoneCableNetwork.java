package dev.redstoneengineering.physics;

import dev.redstoneengineering.block.ConnectedCableBlock;
import dev.redstoneengineering.block.RedstoneCableJunctionBlock;
import dev.redstoneengineering.block.RedstoneCableTerminalBlock;
import dev.redstoneengineering.block.RedstoneSignalCableBlock;
import dev.redstoneengineering.block.TransmissionTopology;
import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Bounded 0..15 propagation for the insulated-redstone domain.
 * Direct cable branches are planar; only a Signal Junction Point resolved to
 * REDSTONE may carry the network vertically.
 *
 * <p>Multiple terminal inputs intentionally retain Vanilla-like strongest-value
 * resolution. Source count is nevertheless retained as observer evidence so a
 * real zero driven by a terminal can be distinguished from an undriven cable.</p>
 */
public final class RedstoneCableNetwork {
    private static final int MAX_NODES = NetworkKernel.MAX_NODES;
    private static final String EVIDENCE_KEY = "redstone_cable_source_evidence";

    private RedstoneCableNetwork() {}

    public record SourceEvidence(int sourceCount, boolean initialized) {
        public PortQuality quality() {
            if (!initialized) return PortQuality.STALE;
            return sourceCount > 0 ? PortQuality.VALID : PortQuality.NO_SIGNAL;
        }
    }

    /** Observer-only source evidence; never creates network state. */
    public static SourceEvidence sourceEvidence(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, EVIDENCE_KEY, pos);
        return runtime == null || runtime.length < 1
                ? new SourceEvidence(0, false)
                : new SourceEvidence(Math.max(0, runtime[0]), true);
    }

    public static void removeEvidence(Level level, BlockPos pos) {
        RuntimeIntStore.remove(level, EVIDENCE_KEY, pos);
    }

    public static void recompute(ServerLevel level, BlockPos start) {
        Set<BlockPos> nodes = collect(level, start);
        if (!nodes.isEmpty()) recomputeComponent(level, nodes);
    }

    public static void recomputeAround(ServerLevel level, BlockPos changedPos) {
        Set<BlockPos> processed = new HashSet<>();
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = changedPos.relative(direction);
            if (!level.hasChunkAt(neighbor) || !allowed(level, neighbor) || processed.contains(neighbor)) continue;
            Set<BlockPos> component = collect(level, neighbor);
            if (component.isEmpty()) continue;
            processed.addAll(component);
            recomputeComponent(level, component);
        }
    }

    private static void recomputeComponent(ServerLevel level, Set<BlockPos> nodes) {
        Map<BlockPos, Integer> best = new HashMap<>();
        PriorityQueue<Node> queue = new PriorityQueue<>(Comparator.comparingInt((Node node) -> -node.power));
        int sourceCount = 0;

        for (BlockPos pos : nodes) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof RedstoneCableTerminalBlock terminal
                    && !state.getValue(RedstoneCableTerminalBlock.OUTPUT_MODE)) {
                sourceCount++;
                int power = terminal.externalInput(level, pos, state);
                best.put(pos, power);
                queue.add(new Node(pos, power));
            }
        }

        while (!queue.isEmpty()) {
            Node current = queue.poll();
            if (current.power < best.getOrDefault(current.pos, -1)) continue;
            for (Direction direction : Direction.values()) {
                BlockPos nextPos = current.pos.relative(direction);
                if (!nodes.contains(nextPos) || !edgeAllowed(level, current.pos, nextPos, direction)) continue;
                BlockState from = level.getBlockState(current.pos);
                BlockState to = level.getBlockState(nextPos);
                int loss = (to.getBlock() instanceof RedstoneSignalCableBlock
                        && !(from.getBlock() instanceof RedstoneCableTerminalBlock terminal
                        && !from.getValue(RedstoneCableTerminalBlock.OUTPUT_MODE))) ? 1 : 0;
                int nextPower = Math.max(0, current.power - loss);
                if (nextPower > best.getOrDefault(nextPos, -1)) {
                    best.put(nextPos, nextPower);
                    queue.add(new Node(nextPos, nextPower));
                }
            }
        }

        for (BlockPos pos : nodes) {
            RuntimeIntStore.get(level, EVIDENCE_KEY, pos, 1)[0] = sourceCount;
            BlockState state = level.getBlockState(pos);
            int power = Math.max(0, Math.min(15, best.getOrDefault(pos, 0)));
            if (state.getBlock() instanceof RedstoneSignalCableBlock) {
                RedstoneSignalCableBlock.setPower(level, pos, power);
            } else if (state.getBlock() instanceof RedstoneCableJunctionBlock) {
                RedstoneCableJunctionBlock.setPower(level, pos, power);
            } else if (state.getBlock() instanceof RedstoneCableTerminalBlock terminal) {
                int shown = state.getValue(RedstoneCableTerminalBlock.OUTPUT_MODE)
                        ? power
                        : terminal.externalInput(level, pos, state);
                BlockState next = state.setValue(RedstoneCableTerminalBlock.POWER, shown);
                if (next != state) {
                    level.setBlock(pos, next, Block.UPDATE_CLIENTS);
                    if (next.getValue(RedstoneCableTerminalBlock.OUTPUT_MODE)) {
                        level.updateNeighborsAt(pos, terminal);
                        level.updateNeighborsAt(pos.relative(terminal.vanillaSide(next)), terminal);
                    }
                }
            }
        }
    }

    private static Set<BlockPos> collect(ServerLevel level, BlockPos start) {
        Set<BlockPos> visited = new LinkedHashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        if (level.hasChunkAt(start) && allowed(level, start)) {
            queue.add(start);
        } else {
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = start.relative(direction);
                if (level.hasChunkAt(neighbor) && allowed(level, neighbor)) queue.add(neighbor);
            }
        }

        while (!queue.isEmpty() && visited.size() < MAX_NODES) {
            BlockPos pos = queue.removeFirst();
            if (!visited.add(pos)) continue;
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                if (!visited.contains(neighbor)
                        && level.hasChunkAt(neighbor)
                        && allowed(level, neighbor)
                        && edgeAllowed(level, pos, neighbor, direction)) {
                    queue.addLast(neighbor);
                }
            }
        }
        NetworkKernel.recordScan(level, "redstone_cable", visited.size(), !queue.isEmpty());
        return visited;
    }

    private static boolean edgeAllowed(ServerLevel level, BlockPos a, BlockPos b, Direction direction) {
        BlockState stateA = level.getBlockState(a);
        BlockState stateB = level.getBlockState(b);
        if (stateA.getBlock() instanceof ConnectedCableBlock cableA
                && (!cableA.topologyValid(stateA) || !ConnectedCableBlock.connected(stateA, direction))) return false;
        if (stateB.getBlock() instanceof ConnectedCableBlock cableB
                && (!cableB.topologyValid(stateB) || !ConnectedCableBlock.connected(stateB, direction.getOpposite()))) return false;
        if (stateA.getBlock() instanceof RedstoneCableTerminalBlock terminalA
                && terminalA.cableSide(stateA) != direction) return false;
        if (stateB.getBlock() instanceof RedstoneCableTerminalBlock terminalB
                && terminalB.cableSide(stateB) != direction.getOpposite()) return false;
        return true;
    }

    private static boolean allowed(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        var block = state.getBlock();
        return block instanceof RedstoneSignalCableBlock
                || block instanceof RedstoneCableTerminalBlock
                || block instanceof RedstoneCableJunctionBlock
                && state.getValue(RedstoneCableJunctionBlock.MEDIUM) == TransmissionTopology.SignalMedium.REDSTONE;
    }

    private record Node(BlockPos pos, int power) {}
}
