package dev.redstoneengineering.physics;

import dev.redstoneengineering.block.EightBitDataBusBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

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
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        if (!(level.getBlockState(start).getBlock() instanceof EightBitDataBusBlock)) return seen;
        q.add(start);
        while (!q.isEmpty() && seen.size() < MAX_NODES) {
            BlockPos p = q.removeFirst();
            if (!seen.add(p)) continue;
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (!level.hasChunkAt(n)) continue;
                if (level.getBlockState(n).getBlock() instanceof EightBitDataBusBlock && !seen.contains(n)) {
                    q.addLast(n);
                }
            }
        }
        return seen;
    }

    public static void drive(ServerLevel level, BlockPos start, BlockPos driver, int value, boolean valid) {
        Set<BlockPos> nodes = collect(level, start);
        if (nodes.isEmpty()) return;
        String key = "bus8_driver:" + driver.asLong();
        for (BlockPos p : nodes) InformationRuntime.write(level, key, p, value & 0xFF, 0, valid, 100);
        resolve(level, nodes);
    }

    public static void resolve(ServerLevel level, Set<BlockPos> nodes) {
        Set<Integer> values = new HashSet<>();
        Set<BlockPos> drivers = new HashSet<>();

        // Count physical driver positions separately from distinct values. A driver
        // touching multiple bus nodes is still one driver, not several.
        for (BlockPos p : nodes) {
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (InformationRuntime.valid(level, "bus8_out", n)) {
                    drivers.add(n.immutable());
                    values.add(InformationRuntime.value(level, "bus8_out", n) & 0xFF);
                }
            }
        }

        int driverCount = drivers.size();
        int distinctValues = values.size();
        boolean valid = distinctValues <= 1;
        boolean contention = driverCount > 1;
        boolean conflict = distinctValues > 1;
        boolean sameValueMultiDriver = driverCount > 1 && distinctValues == 1;
        int value = values.isEmpty() ? 0 : values.iterator().next();

        NetworkKernel.recordDriverState(level, "bus8", driverCount);
        int now = (int) Math.min(Integer.MAX_VALUE, level.getGameTime());

        for (BlockPos p : nodes) {
            InformationRuntime.write(level, "bus8", p, valid ? value : 0, 0, valid, valid ? 100 : 0);
            int[] d = RuntimeIntStore.get(level, DIAG_KEY, p, DIAG_SIZE);
            d[0]++;
            d[1] = nodes.size();
            d[2] = driverCount;
            d[3] = distinctValues;
            if (d[4] > 0) d[5] = Math.max(1, now - d[4]);
            d[4] = now;
            d[6] = value;
            d[7] = d[5] == 0 ? 0 : Math.min(100, 100 / Math.max(1, d[5]));
            if (contention) d[8]++;
            if (conflict) d[9]++;
            if (sameValueMultiDriver) d[10]++;
            d[11] = valid ? 1 : 0;
            level.updateNeighborsAt(p, level.getBlockState(p).getBlock());
        }
    }

    public static int sample(Level level, BlockPos p) {
        if (level.getBlockState(p).getBlock() instanceof EightBitDataBusBlock) {
            return InformationRuntime.value(level, "bus8", p) & 0xFF;
        }
        return InformationRuntime.value(level, "bus8_out", p) & 0xFF;
    }

    public static Diagnostics getDiagnostics(Level level, BlockPos p) {
        int[] d = RuntimeIntStore.get(level, DIAG_KEY, p, DIAG_SIZE);
        return new Diagnostics(d[0], d[1], d[2], d[3], d[8], d[9], d[10], d[5], d[7], d[11] != 0);
    }

    public static String diagnostics(Level level, BlockPos p) {
        Diagnostics d = getDiagnostics(level, p);
        return "updates=" + d.updates()
                + " nodes=" + d.nodes()
                + " driverCount=" + d.driverCount()
                + " distinct=" + d.distinctValues()
                + " contention=" + d.contentionFrames()
                + " conflicts=" + d.conflictFrames()
                + " same-value-multidriver=" + d.sameValueMultiDriverFrames()
                + " interarrival=" + d.interarrivalTicks() + "t"
                + " activity≈" + d.activityPercent() + "%"
                + " valid=" + d.valid();
    }

    public static boolean valid(Level level, BlockPos p) {
        if (level.getBlockState(p).getBlock() instanceof EightBitDataBusBlock) {
            return InformationRuntime.valid(level, "bus8", p);
        }
        return InformationRuntime.valid(level, "bus8_out", p);
    }
}
