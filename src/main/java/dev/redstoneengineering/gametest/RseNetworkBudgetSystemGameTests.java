package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.LapisPrecisionSourceBlock;
import dev.redstoneengineering.block.LapisSignalLineBlock;
import dev.redstoneengineering.block.OpticalEmitterBlock;
import dev.redstoneengineering.block.OpticalFiberBlock;
import dev.redstoneengineering.block.OpticalFiberJunctionBlock;
import dev.redstoneengineering.block.OpticalReceiverBlock;
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

/** Runtime regressions for bounded network-scan correctness. */
public final class RseNetworkBudgetSystemGameTests {
    private static final String TEMPLATE = "empty5x4x5";
    private static final int FIBERS = 129;
    private static final int LAPIS_LINES = 129;

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
        int y = Math.min(level.getMaxBuildHeight() - 2,
                Math.max(level.getMinBuildHeight() + 2, anchor.getY() + 16));
        BlockPos sourceA = new BlockPos(anchor.getX(), y, anchor.getZ());
        BlockPos firstLine = sourceA.east();
        BlockPos secondLine = sourceA.east(2);
        BlockPos sourceB = sourceA.east(LAPIS_LINES + 1);

        for (int i = 0; i <= LAPIS_LINES + 1; i++) {
            BlockPos pos = sourceA.east(i);
            level.getChunkAt(pos);
            if (!level.hasChunkAt(pos)) {
                helper.fail("Precondition failed: forced Lapis test chunk did not load at " + pos);
                return;
            }
        }

        level.setBlock(sourceA, RedstoneEngineering.LAPIS_PRECISION_SOURCE.get().defaultBlockState()
                .setValue(LapisPrecisionSourceBlock.VALUE, 23), Block.UPDATE_ALL);
        for (int i = 1; i <= LAPIS_LINES; i++) {
            BlockState line = RedstoneEngineering.LAPIS_SIGNAL_LINE.get().defaultBlockState()
                    .setValue(SurfaceTraceBlock.WEST, true)
                    .setValue(SurfaceTraceBlock.EAST, true);
            level.setBlock(sourceA.east(i), line, Block.UPDATE_CLIENTS);
        }
        level.setBlock(sourceB, RedstoneEngineering.LAPIS_PRECISION_SOURCE.get().defaultBlockState()
                .setValue(LapisPrecisionSourceBlock.VALUE, 81), Block.UPDATE_ALL);

        BlockState firstState = level.getBlockState(firstLine);
        BlockState secondState = level.getBlockState(secondLine);
        boolean firstWest = SurfaceTraceBlock.connected(firstState, Direction.WEST);
        boolean firstEast = SurfaceTraceBlock.connected(firstState, Direction.EAST);
        boolean secondWest = SurfaceTraceBlock.connected(secondState, Direction.WEST);
        boolean secondEast = SurfaceTraceBlock.connected(secondState, Direction.EAST);

        // Seed the solver on the medium itself so source-start fallback cannot hide a traversal defect.
        DomainNetwork.recomputeLapis(level, firstLine);
        NetworkKernel.ScanStats stats = NetworkKernel.stats(level, "lapis");
        PortQuality quality = LapisSignalLineBlock.quality(level, firstLine);
        int drivers = LapisSignalLineBlock.sourceCount(level, firstLine);
        int value = LapisSignalLineBlock.value(level, firstLine);
        cleanupHorizontal(level, sourceA, LAPIS_LINES + 2);

        if (!stats.lastTruncated() || stats.lastNodes() != NetworkKernel.MAX_NODES) {
            helper.fail("Precondition failed: loaded straight Lapis component did not hit the 128-node budget"
                    + " | nodes=" + stats.lastNodes() + " truncated=" + stats.lastTruncated()
                    + " first=" + firstState.getBlock().getClass().getSimpleName()
                    + "[W=" + firstWest + ",E=" + firstEast + "]"
                    + " second=" + secondState.getBlock().getClass().getSimpleName()
                    + "[W=" + secondWest + ",E=" + secondEast + "]");
            return;
        }
        if (quality != PortQuality.STALE) {
            helper.fail("Budget-truncated Lapis solve published partial evidence instead of STALE"
                    + " | quality=" + quality + " drivers=" + drivers + " value=" + value
                    + " componentNodes=" + (LAPIS_LINES + 2));
            return;
        }
        helper.succeed();
    }

    private static void cleanupVertical(ServerLevel level, BlockPos bottomSource, int blocks) {
        for (int i = blocks - 1; i >= 0; i--) {
            level.setBlock(bottomSource.above(i), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static void cleanupHorizontal(ServerLevel level, BlockPos start, int blocks) {
        for (int i = blocks - 1; i >= 0; i--) {
            level.setBlock(start.east(i), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
