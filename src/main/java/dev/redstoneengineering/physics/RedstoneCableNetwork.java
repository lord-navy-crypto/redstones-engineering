package dev.redstoneengineering.physics;

import dev.redstoneengineering.block.ConnectedCableBlock;
import dev.redstoneengineering.block.RedstoneCableJunctionBlock;
import dev.redstoneengineering.block.RedstoneCableTerminalBlock;
import dev.redstoneengineering.block.RedstoneSignalCableBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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
 * Plain cable is two-ended; explicit Junctions provide branching.
 */
public final class RedstoneCableNetwork {
    private static final int MAX_NODES = NetworkKernel.MAX_NODES;

    private RedstoneCableNetwork() {}

    public static void recompute(ServerLevel level, BlockPos start) {
        Set<BlockPos> nodes = collect(level, start);
        if (!nodes.isEmpty()) recomputeComponent(level, nodes);
    }

    /**
     * Recompute every component adjacent to a removed or deliberately isolated node.
     * A single recompute(start-at-the-gap) is not sufficient because the gap may have
     * split one former network into several independent components.
     */
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

        for (BlockPos pos : nodes) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof RedstoneCableTerminalBlock terminal
                    && !state.getValue(RedstoneCableTerminalBlock.OUTPUT_MODE)) {
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
        var block = level.getBlockState(pos).getBlock();
        return block instanceof RedstoneSignalCableBlock
                || block instanceof RedstoneCableJunctionBlock
                || block instanceof RedstoneCableTerminalBlock;
    }

    private record Node(BlockPos pos, int power) {}
}
