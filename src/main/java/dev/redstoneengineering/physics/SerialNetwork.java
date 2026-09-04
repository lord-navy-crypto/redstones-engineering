package dev.redstoneengineering.physics;

import dev.redstoneengineering.block.DigitalRegeneratorBlock;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.block.SerialDataLineBlock;
import dev.redstoneengineering.block.SerializerBlock;
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
    private static final int DIAG_SIZE = 8;

    private SerialNetwork() {}

    public record Diagnostics(
            int frames,
            int periodTicks,
            int qualityPercent,
            int nodes,
            int interarrivalTicks,
            int utilizationPercent,
            boolean valid
    ) {}

    private record Driver(BlockPos pos, int value, int period, int quality) {}

    public static Set<BlockPos> collect(Level level, BlockPos start) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        if (!(level.getBlockState(start).getBlock() instanceof SerialDataLineBlock)) return seen;
        queue.add(start);
        while (!queue.isEmpty() && seen.size() < NetworkKernel.MAX_NODES) {
            BlockPos pos = queue.removeFirst();
            if (!seen.add(pos)) continue;
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (level.hasChunkAt(next)
                        && level.getBlockState(next).getBlock() instanceof SerialDataLineBlock
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
        int resolvedQuality = Math.max(0, Math.min(100, quality - nodes.size() / 4));
        int now = (int) Math.min(Integer.MAX_VALUE, level.getGameTime());
        for (BlockPos pos : nodes) {
            InformationRuntime.write(
                    level,
                    "serial",
                    pos,
                    value & 0xFF,
                    Math.max(1, period),
                    valid,
                    valid ? resolvedQuality : 0
            );
            int[] diagnostics = RuntimeIntStore.get(level, DIAG_KEY, pos, DIAG_SIZE);
            diagnostics[0]++;
            diagnostics[1] = Math.max(1, period);
            diagnostics[2] = valid ? resolvedQuality : 0;
            diagnostics[3] = nodes.size();
            if (diagnostics[4] > 0) diagnostics[5] = Math.max(1, now - diagnostics[4]);
            diagnostics[4] = now;
            diagnostics[6] = valid ? 1 : 0;
            diagnostics[7] = diagnostics[5] == 0
                    ? 0
                    : Math.min(100, (Math.max(1, period) * 100) / Math.max(1, diagnostics[5]));
            level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
        }
        NetworkKernel.recordScan(level, "serial", nodes.size(), nodes.size() >= NetworkKernel.MAX_NODES);
    }

    public static void recompute(ServerLevel level, BlockPos start) {
        Set<BlockPos> nodes = collect(level, start);
        if (nodes.isEmpty()) return;

        Set<BlockPos> seenDrivers = new HashSet<>();
        Driver driver = null;
        for (BlockPos linePos : nodes) {
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
                if (!InformationRuntime.valid(level, "serial", candidatePos)) continue;

                Driver candidate = new Driver(
                        candidatePos.immutable(),
                        InformationRuntime.value(level, "serial", candidatePos) & 0xFF,
                        Math.max(1, InformationRuntime.aux(level, "serial", candidatePos)),
                        Math.max(0, Math.min(100, InformationRuntime.quality(level, "serial", candidatePos)))
                );
                if (driver != null && !driver.pos().equals(candidate.pos())) {
                    NetworkKernel.recordDriverState(level, "serial", 2);
                    invalidate(level, nodes);
                    return;
                }
                driver = candidate;
            }
        }

        if (driver == null) {
            NetworkKernel.recordDriverState(level, "serial", 0);
            invalidate(level, nodes);
            return;
        }

        NetworkKernel.recordDriverState(level, "serial", 1);
        drive(level, start, driver.value(), driver.period(), true, driver.quality());
    }

    public static void invalidate(ServerLevel level, Set<BlockPos> nodes) {
        for (BlockPos pos : nodes) {
            InformationRuntime.write(level, "serial", pos, 0, 1, false, 0);
            int[] diagnostics = RuntimeIntStore.get(level, DIAG_KEY, pos, DIAG_SIZE);
            diagnostics[2] = 0;
            diagnostics[3] = nodes.size();
            diagnostics[6] = 0;
            level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
        }
    }

    public static void clearNode(Level level, BlockPos pos) {
        InformationRuntime.clear(level, "serial", pos);
        RuntimeIntStore.remove(level, DIAG_KEY, pos);
    }

    public static Diagnostics getDiagnostics(Level level, BlockPos pos) {
        int[] diagnostics = RuntimeIntStore.get(level, DIAG_KEY, pos, DIAG_SIZE);
        return new Diagnostics(
                diagnostics[0],
                diagnostics[1],
                diagnostics[2],
                diagnostics[3],
                diagnostics[5],
                diagnostics[7],
                diagnostics[6] != 0
        );
    }

    public static String diagnostics(Level level, BlockPos pos) {
        Diagnostics diagnostics = getDiagnostics(level, pos);
        return "frames=" + diagnostics.frames()
                + " period=" + diagnostics.periodTicks() + "t"
                + " quality=" + diagnostics.qualityPercent() + "%"
                + " nodes=" + diagnostics.nodes()
                + " interarrival=" + diagnostics.interarrivalTicks() + "t"
                + " utilization≈" + diagnostics.utilizationPercent() + "%"
                + " valid=" + diagnostics.valid();
    }
}
