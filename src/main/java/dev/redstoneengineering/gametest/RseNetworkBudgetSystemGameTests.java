package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.LapisPrecisionSourceBlock;
import dev.redstoneengineering.block.LapisSignalLineBlock;
import dev.redstoneengineering.block.OpticalEmitterBlock;
import dev.redstoneengineering.block.OpticalFiberBlock;
import dev.redstoneengineering.block.OpticalFiberJunctionBlock;
import dev.redstoneengineering.block.OpticalReceiverBlock;
import dev.redstoneengineering.block.QuartzOscillatorBlock;
import dev.redstoneengineering.block.QuartzTimingLineBlock;
import dev.redstoneengineering.block.SurfaceTraceBlock;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
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

/** Runtime regressions for bounded network-scan correctness. */
public final class RseNetworkBudgetSystemGameTests {
    private static final String TEMPLATE = "empty5x4x5";
    private static final int FIBERS = 129;

    private RseNetworkBudgetSystemGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void truncatedOpticalScanMustNotPublishPartialNetworkAsValid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 1, 2));
        int startY = Math.max(level.getMinBuildHeight() + 2,
                Math.min(anchor.getY() + 16, level.getMaxBuildHeight() - (FIBERS + 3)));
        BlockPos bottomSource = new BlockPos(anchor.getX(), startY, anchor.getZ());
        BlockPos firstFiber = bottomSource.above();
        BlockPos topSource = bottomSource.above(FIBERS + 1);

        BlockState sourceA = RedstoneEngineering.OPTICAL_EMITTER.get().defaultBlockState()
                .setValue(OpticalEmitterBlock.INTENSITY, 9).setValue(OpticalEmitterBlock.CHANNEL, 2);
        BlockState sourceB = RedstoneEngineering.OPTICAL_EMITTER.get().defaultBlockState()
                .setValue(OpticalEmitterBlock.INTENSITY, 12).setValue(OpticalEmitterBlock.CHANNEL, 7);
        level.setBlock(bottomSource, sourceA, Block.UPDATE_ALL);
        for (int i = 1; i <= FIBERS; i++) {
            level.setBlock(bottomSource.above(i), RedstoneEngineering.OPTICAL_FIBER.get().defaultBlockState(), Block.UPDATE_ALL);
        }
        level.setBlock(topSource, sourceB, Block.UPDATE_ALL);

        DomainNetwork.recomputeOptical(level, bottomSource);
        NetworkKernel.ScanStats stats = NetworkKernel.stats(level, "optical");
        BlockState fiberState = level.getBlockState(firstFiber);
        PortQuality quality = OpticalFiberBlock.quality(level, firstFiber, fiberState);
        int drivers = OpticalFiberBlock.driverCount(level, firstFiber);
        int intensity = OpticalFiberBlock.intensity(level, firstFiber);
        cleanupVertical(level, bottomSource, FIBERS + 2);

        if (!stats.lastTruncated() || stats.lastNodes() != NetworkKernel.MAX_NODES) {
            helper.fail("Precondition failed: long optical component did not hit the 128-node scan budget; nodes="
                    + stats.lastNodes() + " truncated=" + stats.lastTruncated());
            return;
        }
        if (quality != PortQuality.STALE) {
            helper.fail("Budget-truncated optical solve published partial evidence instead of STALE"
                    + " | quality=" + quality + " drivers=" + drivers + " intensity=" + intensity);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void truncatedOpticalScanPropagatesStaleAcrossVisitedEndpoints(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 1, 2));
        int startY = Math.max(level.getMinBuildHeight() + 2,
                Math.min(anchor.getY() + 16, level.getMaxBuildHeight() - (FIBERS + 4)));
        BlockPos receiverPos = new BlockPos(anchor.getX(), startY, anchor.getZ());
        BlockPos junctionPos = receiverPos.above();
        BlockPos firstFiber = receiverPos.above(2);
        BlockPos topSource = receiverPos.above(FIBERS + 2);
        level.setBlock(receiverPos, RedstoneEngineering.OPTICAL_RECEIVER.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(junctionPos, RedstoneEngineering.OPTICAL_FIBER_JUNCTION.get().defaultBlockState(), Block.UPDATE_ALL);
        for (int i = 2; i <= FIBERS + 1; i++) {
            level.setBlock(receiverPos.above(i), RedstoneEngineering.OPTICAL_FIBER.get().defaultBlockState(), Block.UPDATE_ALL);
        }
        level.setBlock(topSource, RedstoneEngineering.OPTICAL_EMITTER.get().defaultBlockState()
                .setValue(OpticalEmitterBlock.INTENSITY, 11).setValue(OpticalEmitterBlock.CHANNEL, 4), Block.UPDATE_ALL);

        DomainNetwork.recomputeOptical(level, junctionPos);
        NetworkKernel.ScanStats stats = NetworkKernel.stats(level, "optical");
        PortQuality fiberQuality = OpticalFiberBlock.quality(level, firstFiber, level.getBlockState(firstFiber));
        PortQuality receiverQuality = OpticalReceiverBlock.quality(level, receiverPos);
        BlockState junctionState = level.getBlockState(junctionPos);
        EngineeringPortSnapshot junctionSnapshot = OpticalFiberJunctionBlock.class.cast(junctionState.getBlock())
                .engineeringSnapshot(level, junctionPos, junctionState, Direction.UP).orElse(null);
        int fiberIntensity = OpticalFiberBlock.intensity(level, firstFiber);
        int receiverIntensity = OpticalReceiverBlock.intensity(level, receiverPos);
        cleanupVertical(level, receiverPos, FIBERS + 3);

        if (!stats.lastTruncated() || stats.lastNodes() != NetworkKernel.MAX_NODES) {
            helper.fail("Precondition failed: endpoint optical component did not hit the 128-node scan budget; nodes="
                    + stats.lastNodes() + " truncated=" + stats.lastTruncated());
            return;
        }
        if (junctionSnapshot == null) {
            helper.fail("Precondition failed: optical service splice did not expose its connected UP engineering port");
            return;
        }
        if (fiberQuality != PortQuality.STALE || receiverQuality != PortQuality.STALE
                || junctionSnapshot.quality() != PortQuality.STALE || fiberIntensity != 0
                || receiverIntensity != 0 || junctionSnapshot.value() != 0.0) {
            helper.fail("Budget-truncated optical solve did not fail closed across visited endpoints"
                    + " | fiber=" + fiberQuality + "/" + fiberIntensity
                    + " receiver=" + receiverQuality + "/" + receiverIntensity
                    + " junction=" + junctionSnapshot.quality() + "/" + junctionSnapshot.value());
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void truncatedLapisScanMustNotPublishPartialNetworkAsValid(GameTestHelper helper) {
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
                helper.fail("Precondition failed: single-chunk Lapis path unexpectedly crosses unloaded terrain at " + pos);
                return;
            }
        }

        level.setBlock(sourceA, RedstoneEngineering.LAPIS_PRECISION_SOURCE.get().defaultBlockState()
                .setValue(LapisPrecisionSourceBlock.VALUE, 23), Block.UPDATE_ALL);
        for (int i = 1; i < path.size() - 1; i++) {
            level.setBlock(path.get(i), RedstoneEngineering.LAPIS_SIGNAL_LINE.get().defaultBlockState(), Block.UPDATE_ALL);
        }
        level.setBlock(sourceB, RedstoneEngineering.LAPIS_PRECISION_SOURCE.get().defaultBlockState()
                .setValue(LapisPrecisionSourceBlock.VALUE, 81), Block.UPDATE_ALL);
        wireLapisSurfaceTracePath(level, path);

        DomainNetwork.recomputeLapis(level, sourceA);
        NetworkKernel.ScanStats stats = NetworkKernel.stats(level, "lapis");
        PortQuality quality = LapisSignalLineBlock.quality(level, firstLine);
        int drivers = LapisSignalLineBlock.sourceCount(level, firstLine);
        int value = LapisSignalLineBlock.value(level, firstLine);
        cleanupPath(level, path);

        if (!stats.lastTruncated() || stats.lastNodes() != NetworkKernel.MAX_NODES) {
            helper.fail("Precondition failed: 135-node single-chunk Lapis component did not hit the 128-node budget"
                    + " | nodes=" + stats.lastNodes() + " truncated=" + stats.lastTruncated());
            return;
        }
        if (quality != PortQuality.STALE) {
            helper.fail("Budget-truncated Lapis solve published partial evidence instead of STALE"
                    + " | quality=" + quality + " drivers=" + drivers + " value=" + value
                    + " pathNodes=" + path.size());
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void truncatedQuartzScanMustNotPublishPartialTimingAsValid(GameTestHelper helper) {
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

        level.setBlock(sourceA, RedstoneEngineering.QUARTZ_OSCILLATOR.get().defaultBlockState()
                .setValue(QuartzOscillatorBlock.ACTIVE, true)
                .setValue(QuartzOscillatorBlock.PERIOD_INDEX, 0), Block.UPDATE_ALL);
        for (int i = 1; i < path.size() - 1; i++) {
            level.setBlock(path.get(i), RedstoneEngineering.QUARTZ_TIMING_LINE.get().defaultBlockState(), Block.UPDATE_ALL);
        }
        level.setBlock(sourceB, RedstoneEngineering.QUARTZ_OSCILLATOR.get().defaultBlockState()
                .setValue(QuartzOscillatorBlock.ACTIVE, false)
                .setValue(QuartzOscillatorBlock.PERIOD_INDEX, 3), Block.UPDATE_ALL);
        wireQuartzSurfaceTracePath(level, path);

        DomainNetwork.recomputeQuartz(level, sourceA);
        NetworkKernel.ScanStats stats = NetworkKernel.stats(level, "quartz");
        PortQuality quality = QuartzTimingLineBlock.quality(level, firstLine);
        int drivers = QuartzTimingLineBlock.sourceCount(level, firstLine);
        int period = QuartzTimingLineBlock.period(level, firstLine);
        boolean active = QuartzTimingLineBlock.active(level, firstLine);
        cleanupPath(level, path);

        if (!stats.lastTruncated() || stats.lastNodes() != NetworkKernel.MAX_NODES) {
            helper.fail("Precondition failed: 135-node single-chunk Quartz component did not hit the 128-node budget"
                    + " | nodes=" + stats.lastNodes() + " truncated=" + stats.lastTruncated());
            return;
        }
        if (quality != PortQuality.STALE) {
            helper.fail("Budget-truncated Quartz solve published partial timing evidence instead of STALE"
                    + " | quality=" + quality + " drivers=" + drivers + " active=" + active
                    + " period=" + period + " pathNodes=" + path.size());
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

    /** Explicitly fixes Lapis trace arm state after bulk placement so the test graph itself is deterministic. */
    private static void wireLapisSurfaceTracePath(ServerLevel level, List<BlockPos> path) {
        wireSurfaceTracePath(level, path, RedstoneEngineering.LAPIS_SIGNAL_LINE.get().defaultBlockState());
    }

    /** Explicitly fixes Quartz trace arm state after bulk placement so the test graph itself is deterministic. */
    private static void wireQuartzSurfaceTracePath(ServerLevel level, List<BlockPos> path) {
        wireSurfaceTracePath(level, path, RedstoneEngineering.QUARTZ_TIMING_LINE.get().defaultBlockState());
    }

    private static void wireSurfaceTracePath(ServerLevel level, List<BlockPos> path, BlockState defaultTraceState) {
        for (int i = 1; i < path.size() - 1; i++) {
            BlockPos pos = path.get(i);
            BlockState state = defaultTraceState;
            Direction prev = horizontalDirection(pos, path.get(i - 1));
            Direction next = horizontalDirection(pos, path.get(i + 1));
            state = setTraceArm(state, prev, true);
            state = setTraceArm(state, next, true);
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

    private static void cleanupVertical(ServerLevel level, BlockPos bottomSource, int blocks) {
        for (int i = blocks - 1; i >= 0; i--) {
            level.setBlock(bottomSource.above(i), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static void cleanupPath(ServerLevel level, List<BlockPos> path) {
        for (int i = path.size() - 1; i >= 0; i--) {
            level.setBlock(path.get(i), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
