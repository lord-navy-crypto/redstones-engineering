package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.ConnectedCableBlock;
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

import java.util.ArrayList;
import java.util.List;

/** Strict runtime proof that bounded insulated-redstone scans fail closed. */
public final class RseRedstoneCableNetworkBudgetSystemGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseRedstoneCableNetworkBudgetSystemGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void truncatedRedstoneCableScanMustPublishStaleEvidence(GameTestHelper helper) {
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
        BlockPos firstCable = path.get(0);

        for (BlockPos pos : path) {
            if (!level.hasChunkAt(pos)) {
                helper.fail("Precondition failed: single-chunk insulated-redstone path unexpectedly crosses unloaded terrain at " + pos);
                return;
            }
            level.setBlock(pos, RedstoneEngineering.REDSTONE_SIGNAL_CABLE.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        wireCablePath(level, path);

        RedstoneCableNetwork.recompute(level, firstCable);
        NetworkKernel.ScanStats stats = NetworkKernel.stats(level, "redstone_cable");
        RedstoneCableNetwork.SourceEvidence evidence = RedstoneCableNetwork.sourceEvidence(level, firstCable);
        PortQuality quality = evidence.quality();
        int signal = RedstoneSignalCableBlock.power(level, firstCable);
        cleanupPath(level, path);

        if (!stats.lastTruncated() || stats.lastNodes() != NetworkKernel.MAX_NODES) {
            helper.fail("Precondition failed: 135-node insulated-redstone component did not hit the 128-node budget"
                    + " | nodes=" + stats.lastNodes() + " truncated=" + stats.lastTruncated());
            return;
        }
        if (quality != PortQuality.STALE) {
            helper.fail("Budget-truncated insulated-redstone solve published partial source evidence instead of STALE"
                    + " | quality=" + quality + " initialized=" + evidence.initialized()
                    + " sources=" + evidence.sourceCount() + " signal=" + signal
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

    private static void wireCablePath(ServerLevel level, List<BlockPos> path) {
        for (int i = 0; i < path.size(); i++) {
            BlockPos pos = path.get(i);
            BlockState state = RedstoneEngineering.REDSTONE_SIGNAL_CABLE.get().defaultBlockState();
            if (i > 0) state = setCableArm(state, horizontalDirection(pos, path.get(i - 1)), true);
            if (i + 1 < path.size()) state = setCableArm(state, horizontalDirection(pos, path.get(i + 1)), true);
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

    private static BlockState setCableArm(BlockState state, Direction direction, boolean value) {
        return switch (direction) {
            case NORTH -> state.setValue(ConnectedCableBlock.NORTH, value);
            case EAST -> state.setValue(ConnectedCableBlock.EAST, value);
            case SOUTH -> state.setValue(ConnectedCableBlock.SOUTH, value);
            case WEST -> state.setValue(ConnectedCableBlock.WEST, value);
            default -> throw new IllegalArgumentException("Vertical direction is invalid for this planar cable regression: " + direction);
        };
    }

    private static void cleanupPath(ServerLevel level, List<BlockPos> path) {
        for (int i = path.size() - 1; i >= 0; i--) {
            level.setBlock(path.get(i), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
