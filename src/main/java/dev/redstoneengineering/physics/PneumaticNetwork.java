package dev.redstoneengineering.physics;

import dev.redstoneengineering.block.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/** Lumped compressed-air network with explicit physical ports, line loss, regulation and safety devices. */
public final class PneumaticNetwork {
    private PneumaticNetwork() {}
    private record Node(BlockPos pos, int pressure) {}

    private static final String DIAG_KEY = "pneumatic_diag";
    private static final int DIAG_SIZE = 1; // budget truncation flag

    private static boolean isNode(Level level, BlockPos pos) {
        var block = level.getBlockState(pos).getBlock();
        return block instanceof PneumaticPipeBlock || block instanceof AirReservoirBlock ||
                block instanceof PressureRegulatorBlock || block instanceof PneumaticReceiverBlock ||
                block instanceof AirCompressorBlock || block instanceof PneumaticValveBlock ||
                block instanceof PneumaticCheckValveBlock || block instanceof PneumaticFlowMeterBlock ||
                block instanceof PneumaticProportionalValveBlock || block instanceof PneumaticReliefValveBlock ||
                block instanceof PneumaticCylinderBlock;
    }

    private static Direction directionalOutput(BlockState state) {
        if (state.getBlock() instanceof PneumaticReceiverBlock) {
            return DirectionalSignalBlock.seriesOutputSide(state);
        }
        return DirectionalDomainBlock.seriesOutputSide(state);
    }

    private static Direction directionalInput(BlockState state) {
        if (state.getBlock() instanceof PneumaticReceiverBlock) {
            return DirectionalSignalBlock.seriesInputSide(state);
        }
        return DirectionalDomainBlock.seriesInputSide(state);
    }

    private static boolean exposesPneumaticEdge(BlockState state, BlockPos self, BlockPos other) {
        var block = state.getBlock();
        if (block instanceof AirCompressorBlock) return other.equals(self.above());
        if (block instanceof PneumaticReceiverBlock) {
            return other.equals(self.relative(directionalInput(state)));
        }
        if (block instanceof PneumaticCylinderBlock) {
            return other.equals(self.relative(directionalInput(state)));
        }
        if (block instanceof PressureRegulatorBlock ||
                block instanceof PneumaticValveBlock || block instanceof PneumaticCheckValveBlock ||
                block instanceof PneumaticFlowMeterBlock || block instanceof PneumaticProportionalValveBlock ||
                block instanceof PneumaticReliefValveBlock) {
            Direction input = directionalInput(state);
            Direction output = directionalOutput(state);
            return other.equals(self.relative(input)) || other.equals(self.relative(output));
        }
        return true;
    }

    private static boolean discoveryConnects(Level level, BlockPos aPos, BlockPos bPos) {
        BlockState a = level.getBlockState(aPos);
        BlockState b = level.getBlockState(bPos);
        if (a.getBlock() instanceof PneumaticCylinderBlock) {
            Direction input = directionalInput(a);
            return bPos.equals(aPos.relative(input)) && exposesPneumaticEdge(b, bPos, aPos);
        }
        if (b.getBlock() instanceof PneumaticCylinderBlock) {
            Direction input = directionalInput(b);
            return aPos.equals(bPos.relative(input)) && exposesPneumaticEdge(a, aPos, bPos);
        }
        return exposesPneumaticEdge(a, aPos, bPos) && exposesPneumaticEdge(b, bPos, aPos);
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
        NetworkKernel.recordScan(level, "pneumatic", seen.size(), !queue.isEmpty());
        return seen;
    }

    private static boolean directionalForward(BlockState state, BlockPos from, BlockPos to) {
        return to.equals(from.relative(directionalOutput(state)));
    }

    private static boolean directionalBackwardEntry(BlockState state, BlockPos from, BlockPos to) {
        return from.equals(to.relative(directionalInput(state)));
    }

    private static boolean permits(Level level, BlockPos from, BlockPos to) {
        BlockState a = level.getBlockState(from), b = level.getBlockState(to);
        if (!discoveryConnects(level, from, to)) return false;
        if (a.getBlock() instanceof PneumaticValveBlock && !a.getValue(PneumaticValveBlock.OPEN)) return false;
        if (b.getBlock() instanceof PneumaticValveBlock && !b.getValue(PneumaticValveBlock.OPEN)) return false;
        if (a.getBlock() instanceof PneumaticReceiverBlock) return false;
        if (b.getBlock() instanceof PneumaticReceiverBlock) return directionalBackwardEntry(b, from, to);
        if (a.getBlock() instanceof PneumaticCheckValveBlock && !directionalForward(a, from, to)) return false;
        if (b.getBlock() instanceof PneumaticCheckValveBlock && !directionalBackwardEntry(b, from, to)) return false;
        if ((a.getBlock() instanceof PressureRegulatorBlock ||
                a.getBlock() instanceof PneumaticFlowMeterBlock ||
                a.getBlock() instanceof PneumaticProportionalValveBlock ||
                a.getBlock() instanceof PneumaticReliefValveBlock) && !directionalForward(a, from, to)) return false;
        if ((b.getBlock() instanceof PressureRegulatorBlock ||
                b.getBlock() instanceof PneumaticFlowMeterBlock ||
                b.getBlock() instanceof PneumaticProportionalValveBlock ||
                b.getBlock() instanceof PneumaticReliefValveBlock) && !directionalBackwardEntry(b, from, to)) return false;
        if (a.getBlock() instanceof PneumaticCylinderBlock) return false;
        if (b.getBlock() instanceof PneumaticCylinderBlock) {
            return from.equals(to.relative(directionalInput(b)));
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
                PneumaticReliefValveBlock.recordVent(level, pos, excess);
                if (level instanceof ServerLevel server) {
                    int count = excess >= 25 ? 3 : 1;
                    server.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.9,
                            pos.getZ() + 0.5, count, 0.18, 0.08, 0.18, 0.02);
                }
                pressure = setpoint;
            } else {
                PneumaticReliefValveBlock.clearVenting(level, pos);
            }
        }
        return Math.max(0, Math.min(100, pressure));
    }

    public static void recompute(ServerLevel level, BlockPos start) {
        Set<BlockPos> nodes = collect(level, start);
        if (nodes.isEmpty()) return;
        boolean truncated = NetworkKernel.stats(level, "pneumatic").lastTruncated();

        int sources = 0;
        for (BlockPos pos : nodes) {
            var block = level.getBlockState(pos).getBlock();
            if (block instanceof AirCompressorBlock) {
                if (AirCompressorBlock.commandedPressure(level, pos) > 0) sources++;
            } else if (block instanceof AirReservoirBlock) {
                if (InformationRuntime.value(level, "air_reservoir", pos) > 0) sources++;
            }
        }
        NetworkKernel.recordDriverState(level, "pneumatic", sources);

        if (truncated) {
            failClosedTruncated(level, nodes);
            return;
        }

        Map<BlockPos, Integer> best = new HashMap<>();
        ArrayDeque<Node> queue = new ArrayDeque<>();
        for (BlockPos pos : nodes) {
            var block = level.getBlockState(pos).getBlock();
            if (block instanceof AirCompressorBlock) {
                int command = AirCompressorBlock.commandedPressure(level, pos);
                if (command > 0) queue.add(new Node(pos, command));
            } else if (block instanceof AirReservoirBlock) {
                int stored = InformationRuntime.value(level, "air_reservoir", pos);
                if (stored > 0) queue.add(new Node(pos, stored));
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
                if (nodes.contains(next) && permits(level, node.pos, next)
                        && nextPressure > best.getOrDefault(next, -1)) {
                    queue.addLast(new Node(next, nextPressure));
                }
            }
        }

        int quality = Math.max(10, 100 - nodes.size() / 2);
        for (BlockPos pos : nodes) {
            int pressure = best.getOrDefault(pos, 0);
            var block = level.getBlockState(pos).getBlock();
            if (block instanceof PneumaticReliefValveBlock && pressure <= 0) {
                PneumaticReliefValveBlock.clearVenting(level, pos);
            }
            int oldPressure = InformationRuntime.value(level, "pneumatic", pos);
            int oldQuality = InformationRuntime.quality(level, "pneumatic", pos);
            boolean oldValid = InformationRuntime.valid(level, "pneumatic", pos);
            boolean effectiveChanged = oldPressure != pressure || oldQuality != quality || !oldValid;
            InformationRuntime.write(level, "pneumatic", pos, pressure, 0, true, quality);
            RuntimeIntStore.get(level, DIAG_KEY, pos, DIAG_SIZE)[0] = 0;
            if (effectiveChanged) {
                level.updateNeighborsAt(pos, block);
            }
        }

        for (BlockPos pos : nodes) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof PneumaticFlowMeterBlock)) continue;
            Direction input = directionalInput(state);
            Direction output = directionalOutput(state);
            int pin = best.getOrDefault(pos.relative(input), 0);
            int pout = best.getOrDefault(pos.relative(output), 0);
            int dp = Math.max(0, pin - pout);
            int[] runtime = RuntimeIntStore.get(level, "pneumatic_flow", pos, 4);
            runtime[0] = Math.min(100, dp * 12);
            runtime[1] = dp;
            runtime[2] = pin;
            runtime[3] = pout;
        }
    }

    private static void failClosedTruncated(ServerLevel level, Set<BlockPos> nodes) {
        for (BlockPos pos : nodes) {
            var block = level.getBlockState(pos).getBlock();
            int oldPressure = InformationRuntime.value(level, "pneumatic", pos);
            int oldQuality = InformationRuntime.quality(level, "pneumatic", pos);
            boolean oldValid = InformationRuntime.valid(level, "pneumatic", pos);
            InformationRuntime.write(level, "pneumatic", pos, 0, 0, false, 0);
            RuntimeIntStore.get(level, DIAG_KEY, pos, DIAG_SIZE)[0] = 1;
            if (block instanceof PneumaticReliefValveBlock) PneumaticReliefValveBlock.clearVenting(level, pos);
            if (block instanceof PneumaticFlowMeterBlock) {
                int[] runtime = RuntimeIntStore.get(level, "pneumatic_flow", pos, 4);
                Arrays.fill(runtime, 0);
            }
            boolean effectiveChanged = oldPressure != 0 || oldQuality != 0 || oldValid;
            if (effectiveChanged) {
                level.updateNeighborsAt(pos, block);
            }
        }
    }

    public static boolean truncated(Level level, BlockPos pos) {
        int[] diagnostics = RuntimeIntStore.peek(level, DIAG_KEY, pos);
        return diagnostics != null && diagnostics.length == DIAG_SIZE && diagnostics[0] != 0;
    }

    public static void recomputeAround(ServerLevel level, BlockPos changedPos) {
        if (!isNode(level, changedPos)) RuntimeIntStore.remove(level, DIAG_KEY, changedPos);
        Set<BlockPos> covered = new HashSet<>();
        if (isNode(level, changedPos)) {
            Set<BlockPos> component = collect(level, changedPos);
            if (!component.isEmpty()) {
                recompute(level, changedPos);
                covered.addAll(component);
            }
        }
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = changedPos.relative(direction);
            if (!isNode(level, neighbor) || covered.contains(neighbor)) continue;
            Set<BlockPos> component = collect(level, neighbor);
            recompute(level, neighbor);
            covered.addAll(component);
        }
    }

    public static int pressure(Level level, BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof AirReservoirBlock)
            return Math.max(InformationRuntime.value(level, "air_reservoir", pos), InformationRuntime.value(level, "pneumatic", pos));
        return InformationRuntime.value(level, "pneumatic", pos);
    }
}
