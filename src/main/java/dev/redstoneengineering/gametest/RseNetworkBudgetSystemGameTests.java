package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.OpticalEmitterBlock;
import dev.redstoneengineering.block.OpticalFiberBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.NetworkKernel;
import net.minecraft.core.BlockPos;
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

    private RseNetworkBudgetSystemGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void truncatedOpticalScanMustNotPublishPartialNetworkAsValid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 1, 2));
        int startY = Math.max(
                level.getMinBuildHeight() + 2,
                Math.min(anchor.getY() + 16, level.getMaxBuildHeight() - (FIBERS + 3)));
        BlockPos bottomSource = new BlockPos(anchor.getX(), startY, anchor.getZ());
        BlockPos firstFiber = bottomSource.above();
        BlockPos topSource = bottomSource.above(FIBERS + 1);

        BlockState sourceA = RedstoneEngineering.OPTICAL_EMITTER.get().defaultBlockState()
                .setValue(OpticalEmitterBlock.INTENSITY, 9)
                .setValue(OpticalEmitterBlock.CHANNEL, 2);
        BlockState sourceB = RedstoneEngineering.OPTICAL_EMITTER.get().defaultBlockState()
                .setValue(OpticalEmitterBlock.INTENSITY, 12)
                .setValue(OpticalEmitterBlock.CHANNEL, 7);

        level.setBlock(bottomSource, sourceA, Block.UPDATE_ALL);
        for (int i = 1; i <= FIBERS; i++) {
            level.setBlock(bottomSource.above(i), RedstoneEngineering.OPTICAL_FIBER.get().defaultBlockState(), Block.UPDATE_ALL);
        }
        level.setBlock(topSource, sourceB, Block.UPDATE_ALL);

        // Seed the bounded solver from one end. The second real driver is beyond the 128-node budget.
        DomainNetwork.recomputeOptical(level, bottomSource);
        NetworkKernel.ScanStats stats = NetworkKernel.stats(level, "optical");
        BlockState fiberState = level.getBlockState(firstFiber);
        PortQuality quality = OpticalFiberBlock.quality(level, firstFiber, fiberState);
        int drivers = OpticalFiberBlock.driverCount(level, firstFiber);
        int intensity = OpticalFiberBlock.intensity(level, firstFiber);

        cleanup(level, bottomSource, FIBERS + 2);

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

    private static void cleanup(ServerLevel level, BlockPos bottomSource, int blocks) {
        for (int i = blocks - 1; i >= 0; i--) {
            level.setBlock(bottomSource.above(i), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
