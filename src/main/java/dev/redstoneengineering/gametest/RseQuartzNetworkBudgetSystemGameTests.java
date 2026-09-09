package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.QuartzTimingLineBlock;
import dev.redstoneengineering.block.SurfaceTraceBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.NetworkKernel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** Strict runtime proof that a bounded Quartz solve never publishes partial timing as VALID. */
public final class RseQuartzNetworkBudgetSystemGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseQuartzNetworkBudgetSystemGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void truncatedQuartzScanMustNotPublishPartialNetworkAsValid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 1, 2));
        if (!level.hasChunkAt(anchor)) {
            helper.fail("Precondition failed: GameTest anchor chunk is not loaded");
            return;
        }

        int chunkMinX = anchor.getX() & ~15;
        int chunkMinZ = anchor.getZ() & ~15;
        int y = Math.min(level.getMaxBuildHeight() - 2,
                Math.max(level.getMinBuildHeight() + 2, anchor.getY() + 16));
        List<BlockPos> path = planarSnake(chunkMinX, y, chunkMinZ);
        BlockPos sourceA = path.get(0);
        BlockPos firstLine = path.get(1);
        BlockPos sourceB = path.get(path.size() - 1);

        for (BlockPos pos : path) {
            if (!level.hasChunkAt(pos)) {
                helper.fail("Precondition failed: single-chunk Quartz path unexpectedly crosses unloaded terrain at " + pos);
                return;
            }
        }

        level.setBlock(sourceA, RedstoneEngineering.QUARTZ_OSCILLATOR.get().defaultBlockState(), Block.UPDATE_ALL);
        for (int i = 1; i < path.size() - 1; i++) {
            level.setBlock(path.get(i), RedstoneEngineering.QUARTZ_TIMING_LINE.get().defaultBlockState(), Block.UPDATE_ALL);
        }
        level.setBlock(sourceB, RedstoneEngineering.QUARTZ_OSCILLATOR.get().defaultBlockState(), Block.UPDATE_ALL);
        wireSurfaceTracePath(level, path);

        // Inspect synchronously in the same tick so the oscillators' scheduled toggles cannot alter the evidence.
        DomainNetwork.recomputeQuartz(level, firstLine);
        NetworkKernel.ScanStats stats = NetworkKernel.stats(level, "quartz");
        PortQuality quality = QuartzTimingLineBlock.quality(level, firstLine);
        int sources = QuartzTimingLineBlock.sourceCount(level, firstLine);
        boolean active = QuartzTimingLineBlock.active(level, firstLine);
        int period = QuartzTimingLineBlock.period(level, firstLine);
        cleanupPath(level, path);

        if (!stats.lastTruncated() || stats.lastNodes() != NetworkKernel.MAX_NODES) {
            helper.fail("Precondition failed: 135-node single-chunk Quartz component did not hit the 128-node budget"
                    + " | nodes=" + stats.lastNodes() + " truncated=" + stats.lastTruncated());
            return;
        }
        if (quality != PortQuality.STALE) {
            helper.fail("Budget-truncated Quartz solve published partial timing instead of STALE"
                    + " | quality=" + quality + " sources=" + sources
                    + " active=" + active + " period=" + period
                    + " pathNodes=" + path.size());
            return;
        }
        helper.succeed();
    }

    private static List<BlockPos> planarSnake(int minX, int y, int minZ) {
        List<BlockPos> path = new ArrayList<>(135);
        for (int row = 0; row < 8; row++) {
            int z = minZ + row * 2;
            if ((row & 1) == 0) {
                for (int x = minX; x <= minX + 15; x++) path.add(new BlockPos(x, y, z));
                if (row < 7) path.add(new BlockPos(minX + 15, y, z + 1));
            } else {
                for (int x = minX + 15; x >= minX; x--) path.add(new BlockPos(x, y, z));
                if (row < 7) path.add(new BlockPos(minX, y, z + 1));
            }
        }
        return path;
    }

    private static void wireSurfaceTracePath(ServerLevel level, List<BlockPos> path) {
        for (int i = 1; i < path.size() - 1; i++) {
            BlockPos pos = path.get(i);
            BlockState state = RedstoneEngineering.QUARTZ_TIMING_LINE.get().defaultBlockState();
            state = setTraceArm(state, horizontalDirection(pos, path.get(i - 1)), true);
            state = setTraceArm(state, horizontalDirection(pos, path.get(i + 1)), true);
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }

    private static Direction horizontalDirection(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        if (dx == 1 && dz == 0) return Direction.EAST;
        if (dx == -1 && dz == 0) return Direction.WEST;
        if (dx == 0 && dz == 1) return Direction.SOUTH;
        if (dx == 0 && dz == -1) return Direction.NORTH;
        throw new IllegalArgumentException("Non-adjacent planar path nodes: " + from + " -> " + to);
    }

    private static BlockState setTraceArm(BlockState state, Direction direction, boolean value) {
        return switch (direction) {
            case NORTH -> state.setValue(SurfaceTraceBlock.NORTH, value);
            case EAST -> state.setValue(SurfaceTraceBlock.EAST, value);
            case SOUTH -> state.setValue(SurfaceTraceBlock.SOUTH, value);
            case WEST -> state.setValue(SurfaceTraceBlock.WEST, value);
            default -> throw new IllegalArgumentException("Vertical direction is invalid for a surface trace: " + direction);
        };
    }

    private static void cleanupPath(ServerLevel level, List<BlockPos> path) {
        for (int i = path.size() - 1; i >= 0; i--) {
            level.setBlock(path.get(i), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
