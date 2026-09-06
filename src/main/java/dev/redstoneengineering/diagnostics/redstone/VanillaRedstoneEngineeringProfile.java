package dev.redstoneengineering.diagnostics.redstone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Read-only, bounded structural profile for vanilla redstone around one target component.
 *
 * <p>This is deliberately not a second redstone solver. It follows only directly adjacent
 * redstone-relevant blocks, caps traversal, and reports evidence/proxies rather than claiming
 * an exact Minecraft update graph. Vanilla state is never mutated.</p>
 */
public final class VanillaRedstoneEngineeringProfile {
    public static final int MAX_NODES = 256;
    public static final int MAX_MANHATTAN_DISTANCE = 24;

    private VanillaRedstoneEngineeringProfile() {}

    public static boolean isVanillaRedstoneTarget(BlockState state) {
        return isRelevant(state);
    }

    public static VanillaRedstoneDiagnosticsReport inspect(Level level, BlockPos anchor) {
        BlockState anchorState = level.getBlockState(anchor);
        if (!isRelevant(anchorState)) return VanillaRedstoneDiagnosticsReport.notRedstone(anchor);

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> queued = new HashSet<>();
        Set<BlockPos> unloadedBoundaries = new HashSet<>();
        queue.add(anchor.immutable());
        queued.add(anchor.immutable());

        int dust = 0;
        int poweredDust = 0;
        int repeaters = 0;
        int comparators = 0;
        int observers = 0;
        int actuators = 0;
        int sources = 0;
        int maxDustPower = 0;
        int configuredRepeaterDelayGameTicks = 0;
        int maxAdjacentRelevantDegree = 0;
        int possibleQcDependencies = 0;
        BlockPos hotspot = anchor;
        boolean capped = false;

        while (!queue.isEmpty()) {
            if (visited.size() >= MAX_NODES) {
                capped = true;
                break;
            }
            BlockPos pos = queue.removeFirst();
            if (!visited.add(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (!isRelevant(state)) continue;

            if (state.is(Blocks.REDSTONE_WIRE)) {
                dust++;
                int power = state.getValue(RedStoneWireBlock.POWER);
                if (power > 0) poweredDust++;
                maxDustPower = Math.max(maxDustPower, power);
            } else if (state.is(Blocks.REPEATER)) {
                repeaters++;
                // A repeater setting is 1..4 redstone ticks, or 2..8 game ticks.
                configuredRepeaterDelayGameTicks += state.getValue(RepeaterBlock.DELAY) * 2;
            } else if (state.is(Blocks.COMPARATOR)) {
                comparators++;
            } else if (state.is(Blocks.OBSERVER)) {
                observers++;
            } else if (isActuator(state)) {
                actuators++;
            } else if (isSource(state)) {
                sources++;
            }

            if (isQcCapable(state)
                    && !level.hasNeighborSignal(pos)
                    && level.hasNeighborSignal(pos.above())) {
                possibleQcDependencies++;
            }

            int degree = 0;
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (anchor.distManhattan(next) > MAX_MANHATTAN_DISTANCE) continue;
                if (!level.hasChunkAt(next)) {
                    unloadedBoundaries.add(next.immutable());
                    continue;
                }
                BlockState nextState = level.getBlockState(next);
                if (!isRelevant(nextState)) continue;
                degree++;
                if (queued.add(next.immutable())) queue.addLast(next.immutable());
            }
            if (degree > maxAdjacentRelevantDegree) {
                maxAdjacentRelevantDegree = degree;
                hotspot = pos.immutable();
            }
        }

        return new VanillaRedstoneDiagnosticsReport(
                anchor.immutable(), visited.size(), dust, poweredDust, repeaters, comparators, observers,
                actuators, sources, maxDustPower, configuredRepeaterDelayGameTicks,
                maxAdjacentRelevantDegree, possibleQcDependencies, unloadedBoundaries.size(), capped,
                hotspot.immutable());
    }

    private static boolean isRelevant(BlockState state) {
        return state.is(Blocks.REDSTONE_WIRE)
                || state.is(Blocks.REPEATER)
                || state.is(Blocks.COMPARATOR)
                || state.is(Blocks.OBSERVER)
                || state.is(Blocks.PISTON)
                || state.is(Blocks.STICKY_PISTON)
                || state.is(Blocks.DISPENSER)
                || state.is(Blocks.DROPPER)
                || state.is(Blocks.REDSTONE_TORCH)
                || state.is(Blocks.REDSTONE_WALL_TORCH)
                || state.is(Blocks.LEVER)
                || state.is(Blocks.REDSTONE_BLOCK)
                || state.is(Blocks.REDSTONE_LAMP)
                || state.is(Blocks.TARGET);
    }

    private static boolean isActuator(BlockState state) {
        return state.is(Blocks.PISTON)
                || state.is(Blocks.STICKY_PISTON)
                || state.is(Blocks.DISPENSER)
                || state.is(Blocks.DROPPER);
    }

    private static boolean isSource(BlockState state) {
        return state.is(Blocks.REDSTONE_BLOCK)
                || state.is(Blocks.REDSTONE_TORCH)
                || state.is(Blocks.REDSTONE_WALL_TORCH)
                || state.is(Blocks.LEVER);
    }

    private static boolean isQcCapable(BlockState state) {
        return state.is(Blocks.PISTON)
                || state.is(Blocks.STICKY_PISTON)
                || state.is(Blocks.DISPENSER)
                || state.is(Blocks.DROPPER);
    }
}
