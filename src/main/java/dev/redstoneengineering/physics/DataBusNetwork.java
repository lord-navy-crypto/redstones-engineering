package dev.redstoneengineering.physics;

import dev.redstoneengineering.block.EightBitDataBusBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Bounded 8-bit bus resolver.
 * Multiple different values produce BUS-CONFLICT; multiple drivers carrying the
 * same value remain electrically usable but are recorded as same-value-multidriver contention.
 */
public final class DataBusNetwork {
    private DataBusNetwork() {}

    public static final int MAX_NODES = NetworkKernel.MAX_NODES;
    private static final String DIAG_KEY = "bus8_diag";
    private static final int DIAG_SIZE = 12;

    public record Diagnostics(
            int updates,
            int nodes,
            int driverCount,
            int distinctValues,
            int contentionFrames,
            int conflictFrames,
            int sameValueMultiDriverFrames,
            int interarrivalTicks,
            int activityPercent,
            boolean valid
    ) {}

    public static Set<BlockPos> collect(Level level, BlockPos start) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        if (!(level.getBlockState(start).getBlock() instanceof EightBitDataBusBlock)) return seen;
        queue.add(start);
        while (!queue.isEmpty() && seen.size() < MAX_NODES) {
            BlockPos pos = queue.removeFirst();
            if (!seen.add(pos)) continue;
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (!level.hasChunkAt(next)) continue;
                if (level.getBlockState(next).getBlock() instanceof EightBitDataBusBlock && !seen.contains(next)) {
                    queue.addLast(next);
                }
            }
        }
        return seen;
    }

    public static void drive(ServerLevel level, BlockPos start, BlockPos driver, int value, boolean valid) {
        Set<BlockPos> nodes = collect(level, start);
        if (nodes.isEmpty()) return;
        String key = "bus8_driver:" + driver.asLong();
        for (BlockPos pos : nodes) {
            InformationRuntime.write(level, key, pos, value & 0xFF, 0, valid, 100);
        }
        resolve(level, nodes);
    }

    public static void releaseDriver(ServerLevel level, BlockPos driver, BlockPos busStart) {
        InformationRuntime.clear(level, "bus8_out", driver);
        Set<BlockPos> nodes = collect(level, busStart);
        if (!nodes.isEmpty()) resolve(level, nodes);
    }

    public static void clearNode(Level level, BlockPos pos) {
        InformationRuntime.clear(level, "bus8", pos);
        RuntimeIntStore.remove(level, DIAG_KEY, pos);
    }

    public static void resolve(ServerLevel level, Set<BlockPos> nodes) {
        Set<Integer> values = new HashSet<>();
        Set<BlockPos> drivers = new HashSet<>();

        // Runtime payload alone is never proof of a live driver. The adjacent block
        // must implement DataBusDriver and its declared physical output must actually
        // terminate on this bus node. This rejects stale data and wrong-face adjacency.
        for (BlockPos pos : nodes) {
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                BlockState neighborState = level.getBlockState(neighbor);
                if (neighborState.getBlock() instanceof DataBusDriver driver
                        && driver.drivesDataBusAt(neighbor, neighborState, pos)
                        && InformationRuntime.valid(level, "bus8_out", neighbor)) {
                    drivers.add(neighbor.immutable());
                    values.add(InformationRuntime.value(level, "bus8_out", neighbor) & 0xFF);
                }
            }
        }

        int driverCount = drivers.size();
        int distinctValues = values.size();
        boolean valid = driverCount > 0 && distinctValues == 1;
        boolean contention = driverCount > 1;
        boolean conflict = distinctValues > 1;
        boolean sameValueMultiDriver = driverCount > 1 && distinctValues == 1;
        int value = values.isEmpty() ? 0 : values.iterator().next();
        int resolvedValue = valid ? value : 0;
        int resolvedQuality = valid ? 100 : 0;

        NetworkKernel.recordDriverState(level, "bus8", driverCount);
        int now = (int) Math.min(Integer.MAX_VALUE, level.getGameTime());

        for (BlockPos pos : nodes) {
            int oldValue = InformationRuntime.value(level, "bus8", pos) & 0xFF;
            boolean oldValid = InformationRuntime.valid(level, "bus8", pos);
            int oldQuality = InformationRuntime.quality(level, "bus8", pos);
            boolean effectiveChanged = oldValue != resolvedValue
                    || oldValid != valid
                    || oldQuality != resolvedQuality;

            InformationRuntime.write(level, "bus8", pos, resolvedValue, 0, valid, resolvedQuality);
            int[] diagnostics = RuntimeIntStore.get(level, DIAG_KEY, pos, DIAG_SIZE);
            diagnostics[0]++;
            diagnostics[1] = nodes.size();
            diagnostics[2] = driverCount;
            diagnostics[3] = distinctValues;
            if (diagnostics[4] > 0) diagnostics[5] = Math.max(1, now - diagnostics[4]);
            diagnostics[4] = now;
            diagnostics[6] = value;
            diagnostics[7] = diagnostics[5] == 0 ? 0 : Math.min(100, 100 / Math.max(1, diagnostics[5]));
            if (contention) diagnostics[8]++;
            if (conflict) diagnostics[9]++;
            if (sameValueMultiDriver) diagnostics[10]++;
            diagnostics[11] = valid ? 1 : 0;

            // A network recompute is often triggered by a neighbor notification. Emitting
            // another notification when the effective bus state is identical creates an
            // artificial feedback loop. Notify endpoints only for an observable state change.
            if (effectiveChanged) {
                level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
            }
        }
    }

    public static int sample(Level level, BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof EightBitDataBusBlock) {
            return InformationRuntime.value(level, "bus8", pos) & 0xFF;
        }
        return InformationRuntime.value(level, "bus8_out", pos) & 0xFF;
    }

    public static Diagnostics getDiagnostics(Level level, BlockPos pos) {
        int[] diagnostics = RuntimeIntStore.get(level, DIAG_KEY, pos, DIAG_SIZE);
        return new Diagnostics(
                diagnostics[0], diagnostics[1], diagnostics[2], diagnostics[3],
                diagnostics[8], diagnostics[9], diagnostics[10], diagnostics[5],
                diagnostics[7], diagnostics[11] != 0
        );
    }

    public static String diagnostics(Level level, BlockPos pos) {
        Diagnostics diagnostics = getDiagnostics(level, pos);
        return "updates=" + diagnostics.updates()
                + " nodes=" + diagnostics.nodes()
                + " driverCount=" + diagnostics.driverCount()
                + " distinct=" + diagnostics.distinctValues()
                + " contention=" + diagnostics.contentionFrames()
                + " conflicts=" + diagnostics.conflictFrames()
                + " same-value-multidriver=" + diagnostics.sameValueMultiDriverFrames()
                + " interarrival=" + diagnostics.interarrivalTicks() + "t"
                + " activity≈" + diagnostics.activityPercent() + "%"
                + " valid=" + diagnostics.valid();
    }

    public static boolean valid(Level level, BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof EightBitDataBusBlock) {
            return InformationRuntime.valid(level, "bus8", pos);
        }
        return InformationRuntime.valid(level, "bus8_out", pos);
    }
}
