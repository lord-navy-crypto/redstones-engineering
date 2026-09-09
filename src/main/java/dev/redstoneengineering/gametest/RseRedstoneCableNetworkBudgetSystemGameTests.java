package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.RedstoneCableTerminalBlock;
import dev.redstoneengineering.block.RedstoneSignalCableBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.NetworkKernel;
import dev.redstoneengineering.physics.RedstoneCableNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Strict runtime proof for bounded insulated-redstone network correctness. */
public final class RseRedstoneCableNetworkBudgetSystemGameTests {
    private static final String TEMPLATE = "empty5x4x5";
    private static final int CABLES = 129;

    private RseRedstoneCableNetworkBudgetSystemGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void truncatedRedstoneCableScanMustNotPublishPartialNetworkAsValid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 1, 2));
        int y = Math.min(level.getMaxBuildHeight() - 2,
                Math.max(level.getMinBuildHeight() + 2, anchor.getY() + 16));

        BlockPos sourceA = new BlockPos(anchor.getX(), y, anchor.getZ());
        BlockPos terminalA = sourceA.east();
        BlockPos firstCable = terminalA.east();
        BlockPos terminalB = terminalA.east(CABLES + 1);
        BlockPos sourceB = terminalB.east();

        for (int i = 0; i <= CABLES + 2; i++) {
            BlockPos pos = sourceA.east(i);
            level.getChunkAt(pos);
            if (!level.hasChunkAt(pos)) {
                helper.fail("Precondition failed: forced Redstone cable test chunk did not load at " + pos);
                return;
            }
        }

        level.setBlock(sourceA, Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(terminalA, RedstoneEngineering.REDSTONE_CABLE_TERMINAL.get().defaultBlockState()
                .setValue(RedstoneCableTerminalBlock.FACING, Direction.WEST)
                .setValue(RedstoneCableTerminalBlock.OUTPUT_MODE, false), Block.UPDATE_ALL);
        for (int i = 1; i <= CABLES; i++) {
            level.setBlock(terminalA.east(i), RedstoneEngineering.REDSTONE_SIGNAL_CABLE.get().defaultBlockState(), Block.UPDATE_ALL);
        }
        level.setBlock(terminalB, RedstoneEngineering.REDSTONE_CABLE_TERMINAL.get().defaultBlockState()
                .setValue(RedstoneCableTerminalBlock.FACING, Direction.EAST)
                .setValue(RedstoneCableTerminalBlock.OUTPUT_MODE, false), Block.UPDATE_ALL);
        level.setBlock(sourceB, Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);

        // Seed from the medium itself. The second real terminal source is beyond the 128-node budget.
        RedstoneCableNetwork.recompute(level, firstCable);
        NetworkKernel.ScanStats stats = NetworkKernel.stats(level, "redstone_cable");
        RedstoneCableNetwork.SourceEvidence evidence = RedstoneCableNetwork.sourceEvidence(level, firstCable);
        int power = RedstoneSignalCableBlock.power(level, firstCable);

        cleanup(level, sourceA, CABLES + 3);

        if (!stats.lastTruncated() || stats.lastNodes() != NetworkKernel.MAX_NODES) {
            helper.fail("Precondition failed: long Redstone cable component did not hit the 128-node budget"
                    + " | nodes=" + stats.lastNodes() + " truncated=" + stats.lastTruncated()
                    + " sources=" + evidence.sourceCount() + " power=" + power);
            return;
        }
        if (evidence.quality() != PortQuality.STALE || power != 0) {
            helper.fail("Budget-truncated Redstone cable solve published partial evidence instead of failing closed"
                    + " | quality=" + evidence.quality()
                    + " sources=" + evidence.sourceCount() + " power=" + power
                    + " componentNodes=" + (CABLES + 2));
            return;
        }
        helper.succeed();
    }

    private static void cleanup(ServerLevel level, BlockPos start, int blocks) {
        for (int i = blocks - 1; i >= 0; i--) {
            level.setBlock(start.east(i), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
