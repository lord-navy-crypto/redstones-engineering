package dev.redstoneengineering.physics;

import dev.redstoneengineering.block.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/** Lumped compressed-air network with line loss, regulators, isolation, one-way protection and safety relief. */
public final class PneumaticNetwork {
    private PneumaticNetwork() {}
    private record Node(BlockPos pos, int pressure) {}

    private static boolean isNode(Level level, BlockPos pos) {
        var block = level.getBlockState(pos).getBlock();
        return block instanceof PneumaticPipeBlock || block instanceof AirReservoirBlock ||
                block instanceof PressureRegulatorBlock || block instanceof PneumaticReceiverBlock ||
                block instanceof AirCompressorBlock || block instanceof PneumaticValveBlock ||
                block instanceof PneumaticCheckValveBlock || block instanceof PneumaticFlowMeterBlock ||
                block instanceof PneumaticProportionalValveBlock || block instanceof PneumaticReliefValveBlock ||
                block instanceof PneumaticCylinderBlock;
    }

    /**
     * Physical network discovery is mostly undirected, but terminal actuators must
     * not bridge otherwise separate networks. A cylinder participates only through
     * its BACK/input face.
     */
    private static boolean discoveryConnects(Level level, BlockPos aPos, BlockPos bPos) {
        BlockState a = level.getBlockState(aPos);
        BlockState b = level.getBlockState(bPos);

        if (a.getBlock() instanceof PneumaticCylinderBlock) {
            Direction input = a.getValue(DirectionalDomainBlock.FACING).getOpposite();
            return bPos.equals(aPos.relative(input));
        }
        if (b.getBlock() instanceof PneumaticCylinderBlock) {
            Direction input = b.getValue(DirectionalDomainBlock.FACING).getOpposite();
            return aPos.equals(bPos.relative(input));
        }
        return true;
    }

    public static Set<BlockPos> collect(Level level, BlockPos start) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        if (!isNode(level, start)) return seen;
        queue.add(start);
        while (!queue.isEmpty() && seen.size() < NetworkKernel.MAX_NODES) {
            BlockPos pos = queue.removeFirst();
            if (!seen.add(pos)) continue;
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (level.hasChunkAt(next)
                        && isNode(level, next)
                        && discoveryConnects(level, pos, next)
                        && !seen.contains(next)) {
                    queue.addLast(next);
                }
            }
        }
        return seen;
    }

    private static boolean permits(Level level, BlockPos from, BlockPos to) {
        BlockState a = level.getBlockState(from), b = level.getBlockState(to);
        if (a.getBlock() instanceof PneumaticValveBlock && !a.getValue(PneumaticValveBlock.OPEN)) return false;
        if (b.getBlock() instanceof PneumaticValveBlock && !b.getValue(PneumaticValveBlock.OPEN)) return false;

        if (a.getBlock() instanceof PneumaticCheckValveBlock) {
            Direction facing = a.getValue(DirectionalDomainBlock.FACING);
            if (!to.equals(from.relative(facing))) return false;
        }
        if (b.getBlock() instanceof PneumaticCheckValveBlock) {
            Direction facing = b.getValue(DirectionalDomainBlock.FACING);
            if (!from.equals(to.relative(facing.getOpposite()))) return false;
        }

        // A proportional valve is an inline BACK -> FRONT device. Reverse propagation is intentionally blocked.
        if (a.getBlock() instanceof PneumaticProportionalValveBlock) {
            Direction facing = a.getValue(DirectionalDomainBlock.FACING);
            if (!to.equals(from.relative(facing))) return false;
        }
        if (b.getBlock() instanceof PneumaticProportionalValveBlock) {
            Direction facing = b.getValue(DirectionalDomainBlock.FACING);
            if (!from.equals(to.relative(facing.getOpposite()))) return false;
        }

        // A pneumatic cylinder is a terminal one-port sink/actuator, not an inline pipe.
        // Flow may enter only through BACK/input. Once pressure reaches the cylinder,
        // it is consumed as actuator state and never propagates through FRONT or a side.
        if (a.getBlock() instanceof PneumaticCylinderBlock) {
            return false;
        }
        if (b.getBlock() instanceof PneumaticCylinderBlock) {
            Direction input = b.getValue(DirectionalDomainBlock.FACING).getOpposite();
            if (!from.equals(to.relative(input))) return false;
        }
        return true;
    }

    private static int localLimit(Level level, BlockPos pos, int pressure) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof PressureRegulatorBlock)
            pressure = Math.min(pressure, state.getValue(PressureRegulatorBlock.SETPOINT) * 25);
        if (state.getBlock() instanceof PneumaticProportionalValveBlock) {
            int opening = PneumaticProportionalValveBlock.opening(level, pos);
            pressure = (pressure * opening + 7) / 15;
        }
        if (state.getBlock() instanceof PneumaticReliefValveBlock) {
            int setpoint = state.getValue(PneumaticReliefValveBlock.SETPOINT) * 25;
            if (pressure > setpoint) {
                int excess = pressure - setpoint;
                int[] diag = RuntimeIntStore.get(level, "pneumatic_relief", pos, 3);
                diag[0]++;
                diag[1] = excess;
                diag[2] += excess;
                pressure = setpoint;
            }
        }
        return Math.max(0, Math.min(100, pressure));
    }

    public static void recompute(ServerLevel level, BlockPos start) {
        Set<BlockPos> nodes = collect(level, start);
        if (nodes.isEmpty()) return;
        Map<BlockPos, Integer> best = new HashMap<>();
        ArrayDeque<Node> queue = new ArrayDeque<>();
        int sources = 0;

        for (BlockPos pos : nodes) {
            var block = level.getBlockState(pos).getBlock();
            if (block instanceof AirCompressorBlock) {
                int command = AirCompressorBlock.commandedPressure(level, pos);
                if (command > 0) { queue.add(new Node(pos, command)); sources++; }
            } else if (block instanceof AirReservoirBlock) {
                int stored = InformationRuntime.value(level, "air_reservoir", pos);
                if (stored > 0) { queue.add(new Node(pos, stored)); sources++; }
            }
        }

        while (!queue.isEmpty()) {
            Node node = queue.removeFirst();
            if (!nodes.contains(node.pos) || node.pressure <= best.getOrDefault(node.pos, -1)) continue;
            int pressure = localLimit(level, node.pos, node.pressure);
            best.put(node.pos, pressure);
            int nextPressure = Math.max(0, pressure - 1);
            if (nextPressure <= 0) continue;
            for (Direction direction : Direction.values()) {
                BlockPos next = node.pos.relative(direction);
                if (nodes.contains(next)
                        && permits(level, node.pos, next)
                        && nextPressure > best.getOrDefault(next, -1)) {
                    queue.addLast(new Node(next, nextPressure));
                }
            }
        }

        NetworkKernel.recordDriverState(level, "pneumatic", sources);
        NetworkKernel.recordScan(level, "pneumatic", nodes.size(), nodes.size() >= NetworkKernel.MAX_NODES);
        int quality = Math.max(10, 100 - nodes.size() / 2);
        for (BlockPos pos : nodes) {
            int pressure = best.getOrDefault(pos, 0);
            var block = level.getBlockState(pos).getBlock();
            InformationRuntime.write(level, "pneumatic", pos, pressure, 0, true, quality);
            level.updateNeighborsAt(pos, block);
        }

        for (BlockPos pos : nodes) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof PneumaticFlowMeterBlock)) continue;
            Direction facing = state.getValue(DirectionalDomainBlock.FACING);
            int pin = best.getOrDefault(pos.relative(facing.getOpposite()), 0);
            int pout = best.getOrDefault(pos.relative(facing), 0);
            int dp = Math.max(0, pin - pout);
            int[] runtime = RuntimeIntStore.get(level, "pneumatic_flow", pos, 4);
            runtime[0] = Math.min(100, dp * 12);
            runtime[1] = dp;
            runtime[2] = pin;
            runtime[3] = pout;
        }
    }

    public static int pressure(Level level, BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof AirReservoirBlock)
            return Math.max(InformationRuntime.value(level, "air_reservoir", pos), InformationRuntime.value(level, "pneumatic", pos));
        return InformationRuntime.value(level, "pneumatic", pos);
    }
}
