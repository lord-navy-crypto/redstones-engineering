package dev.redstoneengineering.physics;

import dev.redstoneengineering.block.ConnectedCableBlock;
import dev.redstoneengineering.block.DigitalRegeneratorBlock;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.block.RedstoneCableJunctionBlock;
import dev.redstoneengineering.block.SerialDataLineBlock;
import dev.redstoneengineering.block.SerializerBlock;
import dev.redstoneengineering.block.TransmissionTopology;
import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** Bounded serial line propagation with frame/period/quality/utilization diagnostics. */
public final class SerialNetwork {
    private static final String DIAG_KEY = "serial_diag";
    private static final int DIAG_SIZE = 9;

    private SerialNetwork() {}

    public record Diagnostics(
            int frames,
            int periodTicks,
            int qualityPercent,
            int nodes,
            int interarrivalTicks,
            int utilizationPercent,
            boolean valid,
            int driverCount
    ) {}

    private record Driver(BlockPos pos, int value, int period, int quality) {}

    public static boolean isNode(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return false;
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof SerialDataLineBlock
                || state.getBlock() instanceof RedstoneCableJunctionBlock
                && state.getValue(RedstoneCableJunctionBlock.MEDIUM) == TransmissionTopology.SignalMedium.SERIAL;
    }

    private static boolean edgeAllowed(Level level, BlockPos from, BlockPos to, Direction direction) {
        BlockState a = level.getBlockState(from);
        BlockState b = level.getBlockState(to);
        if (!isNode(level, from) || !isNode(level, to)) return false;
        boolean aJunction = a.getBlock() instanceof RedstoneCableJunctionBlock;
        boolean bJunction = b.getBlock() instanceof RedstoneCableJunctionBlock;
        if (aJunction && bJunction) return false;
        if (!aJunction && !bJunction && direction.getAxis() == Direction.Axis.Y) return false;
        return ConnectedCableBlock.connected(a, direction)
                && ConnectedCableBlock.connected(b, direction.getOpposite());
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
                        && edgeAllowed(level, pos, next, direction)
                        && !seen.contains(next)) {
                    queue.addLast(next);
                }
            }
        }
        return seen;
    }

    public static void drive(
            ServerLevel level,
            BlockPos start,
            int value,
            int period,
            boolean valid,
            int quality
    ) {
        Set<BlockPos> nodes = collect(level, start);
        if (nodes.isEmpty()) return;
        int resolvedValue = value & 0xFF;
        int resolvedPeriod = Math.max(1, period);
        int resolvedQuality = valid ? Math.max(0, Math.min(100, quality - nodes.size() / 4)) : 0;
        int now = (int) Math.min(Integer.MAX_VALUE, level.getGameTime());
        for (BlockPos pos : nodes) {
            InformationRuntime.Snapshot old = InformationRuntime.snapshot(level, "serial", pos);
            int oldValue = old.value() & 0xFF;
            int oldPeriod = Math.max(1, old.selector());
            int oldQuality = old.qualityPercent();
            boolean oldValid = old.valid();
            boolean effectiveChanged = oldValue != resolvedValue
                    || oldPeriod != resolvedPeriod
                    || oldQuality != resolvedQuality
                    || oldValid != valid;

            InformationRuntime.write(
                    level,
                    "serial",
                    pos,
                    resolvedValue,
                    resolvedPeriod,
                    valid,
                    resolvedQuality
            );
            int[] diagnostics = RuntimeIntStore.get(level, DIAG_KEY, pos, DIAG_SIZE);
            diagnostics[0]++;
            diagnostics[1] = resolvedPeriod;
            diagnostics[2] = resolvedQuality;
            diagnostics[3] = nodes.size();
            if (diagnostics[4] > 0) diagnostics[5] = Math.max(1, now - diagnostics[4]);
            diagnostics[4] = now;
            diagnostics[6] = valid ? 1 : 0;
            diagnostics[7] = diagnostics[5] == 0
                    ? 0
                    : Math.min(100, (resolvedPeriod * 100) / Math.max(1, diagnostics[5]));
            diagnostics[8] = valid ? 1 : 0;
            if (effectiveChanged) {
                level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
            }
        }
        NetworkKernel.recordScan(level, "serial", nodes.size(), nodes.size() >= NetworkKernel.MAX_NODES);
    }

    public static void recompute(ServerLevel level, BlockPos start) {
        Set<BlockPos> nodes = collect(level, start);
        if (nodes.isEmpty()) return;

        Set<BlockPos> seenDrivers = new HashSet<>();
        Driver driver = null;
        for (BlockPos linePos : nodes) {
            if (!(level.getBlockState(linePos).getBlock() instanceof SerialDataLineBlock)) continue;
            for (Direction direction : Direction.values()) {
                BlockPos candidatePos = linePos.relative(direction);
                if (!level.hasChunkAt(candidatePos) || !seenDrivers.add(candidatePos.immutable())) continue;
                BlockState candidateState = level.getBlockState(candidatePos);
                if (!(candidateState.getBlock() instanceof SerializerBlock)
                        && !(candidateState.getBlock() instanceof DigitalRegeneratorBlock)) {
                    continue;
                }
                Direction output = candidateState.getValue(DirectionalDomainBlock.FACING);
                if (!candidatePos.relative(output).equals(linePos)) continue;
                InformationRuntime.Snapshot source = InformationRuntime.snapshot(level, "serial", candidatePos);
                if (!source.valid()) continue;

                Driver candidate = new Driver(
                        candidatePos.immutable(),
                        source.value() & 0xFF,
                        Math.max(1, source.selector()),
                        Math.max(0, Math.min(100, source.qualityPercent()))
                );
                if (driver != null && !driver.pos().equals(candidate.pos())) {
                    NetworkKernel.recordDriverState(level, "serial", 2);
                    invalidate(level, nodes, 2);
                    return;
                }
                driver = candidate;
            }
        }

        if (driver == null) {
            NetworkKernel.recordDriverState(level, "serial", 0);
            invalidate(level, nodes, 0);
            return;
        }

        NetworkKernel.recordDriverState(level, "serial", 1);
        drive(level, start, driver.value(), driver.period(), true, driver.quality());
    }

    public static void invalidate(ServerLevel level, Set<BlockPos> nodes) {
        invalidate(level, nodes, 0);
    }

    private static void invalidate(ServerLevel level, Set<BlockPos> nodes, int driverCount) {
        for (BlockPos pos : nodes) {
            InformationRuntime.Snapshot old = InformationRuntime.snapshot(level, "serial", pos);
            int oldValue = old.value() & 0xFF;
            int oldPeriod = Math.max(1, old.selector());
            int oldQuality = old.qualityPercent();
            boolean oldValid = old.valid();
            boolean effectiveChanged = oldValue != 0 || oldPeriod != 1 || oldQuality != 0 || oldValid;

            InformationRuntime.write(level, "serial", pos, 0, 1, false, 0);
            int[] diagnostics = RuntimeIntStore.get(level, DIAG_KEY, pos, DIAG_SIZE);
            diagnostics[2] = 0;
            diagnostics[3] = nodes.size();
            diagnostics[6] = 0;
            diagnostics[8] = Math.max(0, driverCount);
            if (effectiveChanged) {
                level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
            }
        }
    }

    public static void clearNode(Level level, BlockPos pos) {
        InformationRuntime.clear(level, "serial", pos);
        RuntimeIntStore.remove(level, DIAG_KEY, pos);
    }

    public static Diagnostics getDiagnostics(Level level, BlockPos pos) {
        int[] diagnostics = RuntimeIntStore.peek(level, DIAG_KEY, pos);
        if (diagnostics == null || diagnostics.length != DIAG_SIZE) {
            return new Diagnostics(0, 0, 0, 0, 0, 0, false, 0);
        }
        return new Diagnostics(
                diagnostics[0],
                diagnostics[1],
                diagnostics[2],
                diagnostics[3],
                diagnostics[5],
                diagnostics[7],
                diagnostics[6] != 0,
                diagnostics[8]
        );
    }

    /** Observer-neutral serial quality with explicit no-driver versus conflict evidence. */
    public static PortQuality quality(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return PortQuality.STALE;
        InformationRuntime.Snapshot snapshot = InformationRuntime.snapshot(level, "serial", pos);
        if (snapshot.ageTicks() < 0) return PortQuality.STALE;
        Diagnostics diagnostics = getDiagnostics(level, pos);
        if (diagnostics.driverCount() == 0) return PortQuality.NO_SIGNAL;
        if (diagnostics.driverCount() > 1) return PortQuality.TOPOLOGY_ERROR;
        return snapshot.valid() ? PortQuality.VALID : PortQuality.FAULT;
    }

    public static String diagnostics(Level level, BlockPos pos) {
        Diagnostics diagnostics = getDiagnostics(level, pos);
        return "frames=" + diagnostics.frames()
                + " period=" + diagnostics.periodTicks() + "t"
                + " quality=" + diagnostics.qualityPercent() + "%"
                + " nodes=" + diagnostics.nodes()
                + " driverCount=" + diagnostics.driverCount()
                + " interarrival=" + diagnostics.interarrivalTicks() + "t"
                + " utilization≈" + diagnostics.utilizationPercent() + "%"
                + " valid=" + diagnostics.valid();
    }
}
