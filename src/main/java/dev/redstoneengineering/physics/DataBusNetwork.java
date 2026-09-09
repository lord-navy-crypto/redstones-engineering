package dev.redstoneengineering.physics;

import dev.redstoneengineering.block.ConnectedCableBlock;
import dev.redstoneengineering.block.EightBitDataBusBlock;
import dev.redstoneengineering.block.RedstoneCableJunctionBlock;
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

/**
 * Bounded local parallel 8-bit bus resolver.
 *
 * <p>The bus trades high payload density and immediate shared access for local loading and driver
 * coordination. Different driven values are a hard BUS-CONFLICT. Same-value multiple drivers remain
 * logically usable, but consume RSE bus margin instead of behaving as if redundant driving were free.</p>
 *
 * <p>Graph topology follows the visible cable arms. Direct bus-to-bus edges are horizontal;
 * vertical edges require a Signal Junction Point resolved specifically to DATA_BUS_8.</p>
 */
public final class DataBusNetwork {
    private DataBusNetwork() {}

    public static final int MAX_NODES = NetworkKernel.MAX_NODES;
    private static final String DIAG_KEY = "bus8_diag";
    private static final int DIAG_SIZE = 13;

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
            int qualityPercent,
            boolean valid
    ) {}

    private static boolean isNode(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return false;
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof EightBitDataBusBlock
                || state.getBlock() instanceof RedstoneCableJunctionBlock
                && state.getValue(RedstoneCableJunctionBlock.MEDIUM) == TransmissionTopology.SignalMedium.DATA_BUS_8;
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
        while (!queue.isEmpty() && seen.size() < MAX_NODES) {
            BlockPos pos = queue.removeFirst();
            if (!seen.add(pos)) continue;
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (!level.hasChunkAt(next)) continue;
                if (isNode(level, next) && edgeAllowed(level, pos, next, direction) && !seen.contains(next)) {
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
        // terminate on a real bus cable node. Junctions only route; they never become drivers.
        for (BlockPos pos : nodes) {
            if (!(level.getBlockState(pos).getBlock() instanceof EightBitDataBusBlock)) continue;
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

        int loadingPenalty = Math.max(0, nodes.size() - 1) / 2;
        int contentionPenalty = sameValueMultiDriver ? Math.min(30, (driverCount - 1) * 12) : 0;
        int resolvedQuality = valid
                ? Math.max(35, 100 - loadingPenalty - contentionPenalty)
                : 0;

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
            diagnostics[12] = resolvedQuality;

            if (effectiveChanged) {
                level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
            }
        }
    }

    public static int sample(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return 0;
        InformationRuntime.Snapshot snapshot = InformationRuntime.snapshot(
                level,
                isNode(level, pos) ? "bus8" : "bus8_out",
                pos
        );
        return snapshot.value() & 0xFF;
    }

    public static Diagnostics getDiagnostics(Level level, BlockPos pos) {
        int[] diagnostics = RuntimeIntStore.peek(level, DIAG_KEY, pos);
        if (diagnostics == null || diagnostics.length != DIAG_SIZE) {
            return new Diagnostics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false);
        }
        return new Diagnostics(
                diagnostics[0], diagnostics[1], diagnostics[2], diagnostics[3],
                diagnostics[8], diagnostics[9], diagnostics[10], diagnostics[5],
                diagnostics[7], diagnostics[12], diagnostics[11] != 0
        );
    }

    /** Observer-neutral bus quality: never creates payload or diagnostic runtime. */
    public static PortQuality quality(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return PortQuality.STALE;
        boolean node = isNode(level, pos);
        if (!node && level.getBlockState(pos).isAir()) return PortQuality.NO_SIGNAL;
        InformationRuntime.Snapshot snapshot = InformationRuntime.snapshot(level, node ? "bus8" : "bus8_out", pos);
        if (snapshot.ageTicks() < 0) return PortQuality.STALE;
        if (!node) return snapshot.valid() ? PortQuality.VALID : PortQuality.NO_SIGNAL;

        Diagnostics diagnostics = getDiagnostics(level, pos);
        if (diagnostics.driverCount() == 0) return PortQuality.NO_SIGNAL;
        if (diagnostics.distinctValues() > 1 || !diagnostics.valid()) return PortQuality.TOPOLOGY_ERROR;
        return PortQuality.VALID;
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
                + " quality=" + diagnostics.qualityPercent() + "%"
                + " age=" + InformationRuntime.ageTicks(level, "bus8", pos) + "t"
                + " valid=" + diagnostics.valid();
    }

    public static boolean valid(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return false;
        InformationRuntime.Snapshot snapshot = InformationRuntime.snapshot(
                level,
                isNode(level, pos) ? "bus8" : "bus8_out",
                pos
        );
        return snapshot.valid();
    }
}
