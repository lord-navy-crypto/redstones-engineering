package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.ConnectedCableBlock;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.RedstoneCableTerminalBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
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

/** Strict runtime proof that bounded insulated-redstone scans fail closed and recover. */
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
        wireCablePath(level, path, 0, path.size());

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

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 120)
    public static void truncatedRedstoneCableClearsPriorPowerThenCompleteSolveRecovers(GameTestHelper helper) {
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
        BlockPos sourcePos = path.get(0);
        BlockPos terminalPos = path.get(1);
        BlockPos firstCable = path.get(2);
        int shortEndExclusive = 8;

        // Reuse the production-proven vanilla -> terminal orientation used by the existing
        // valid-zero cable tests: source faces EAST, input terminal faces WEST, cable exits EAST.
        level.setBlock(sourcePos, RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.EAST)
                .setValue(RedstoneReferenceSourceBlock.POWER, 15), Block.UPDATE_ALL);
        level.setBlock(terminalPos, RedstoneEngineering.REDSTONE_CABLE_TERMINAL.get().defaultBlockState()
                .setValue(RedstoneCableTerminalBlock.FACING, Direction.WEST)
                .setValue(RedstoneCableTerminalBlock.OUTPUT_MODE, false), Block.UPDATE_ALL);
        for (int i = 2; i < shortEndExclusive; i++) {
            level.setBlock(path.get(i), RedstoneEngineering.REDSTONE_SIGNAL_CABLE.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        wireCablePath(level, path, 2, shortEndExclusive);

        RedstoneCableNetwork.recompute(level, firstCable);
        NetworkKernel.ScanStats initialStats = NetworkKernel.stats(level, "redstone_cable");
        RedstoneCableNetwork.SourceEvidence initialEvidence = RedstoneCableNetwork.sourceEvidence(level, firstCable);
        int initialPower = RedstoneSignalCableBlock.power(level, firstCable);
        if (initialStats.lastTruncated() || initialEvidence.quality() != PortQuality.VALID
                || initialEvidence.sourceCount() != 1 || initialPower != 15) {
            cleanupPath(level, path);
            helper.fail("Precondition failed: short insulated-redstone network did not establish one valid 15/15 source"
                    + " | nodes=" + initialStats.lastNodes() + " truncated=" + initialStats.lastTruncated()
                    + " quality=" + initialEvidence.quality() + " sources=" + initialEvidence.sourceCount()
                    + " power=" + initialPower);
            return;
        }

        // Grow the already-powered component past the 128-node budget. This intentionally
        // creates prior non-zero runtime that a safe truncated solve must invalidate.
        for (int i = shortEndExclusive; i < path.size(); i++) {
            level.setBlock(path.get(i), RedstoneEngineering.REDSTONE_SIGNAL_CABLE.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        wireCablePath(level, path, 2, path.size());
        RedstoneCableNetwork.recompute(level, firstCable);

        NetworkKernel.ScanStats truncatedStats = NetworkKernel.stats(level, "redstone_cable");
        RedstoneCableNetwork.SourceEvidence truncatedEvidence = RedstoneCableNetwork.sourceEvidence(level, firstCable);
        int truncatedPower = RedstoneSignalCableBlock.power(level, firstCable);
        if (!truncatedStats.lastTruncated() || truncatedStats.lastNodes() != NetworkKernel.MAX_NODES) {
            cleanupPath(level, path);
            helper.fail("Precondition failed: grown insulated-redstone component did not hit the 128-node budget"
                    + " | nodes=" + truncatedStats.lastNodes() + " truncated=" + truncatedStats.lastTruncated());
            return;
        }
        if (truncatedEvidence.quality() != PortQuality.STALE || truncatedEvidence.initialized()
                || truncatedPower != 0) {
            cleanupPath(level, path);
            helper.fail("Budget truncation retained ghost insulated-redstone state instead of failing closed"
                    + " | quality=" + truncatedEvidence.quality()
                    + " initialized=" + truncatedEvidence.initialized()
                    + " sources=" + truncatedEvidence.sourceCount() + " power=" + truncatedPower);
            return;
        }

        // Shrink back below the budget and force a complete recompute. STALE must not stick:
        // the same physical source must become trustworthy again with its original power.
        for (int i = path.size() - 1; i >= shortEndExclusive; i--) {
            level.setBlock(path.get(i), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        wireCablePath(level, path, 2, shortEndExclusive);
        RedstoneCableNetwork.recompute(level, firstCable);

        NetworkKernel.ScanStats recoveredStats = NetworkKernel.stats(level, "redstone_cable");
        RedstoneCableNetwork.SourceEvidence recoveredEvidence = RedstoneCableNetwork.sourceEvidence(level, firstCable);
        int recoveredPower = RedstoneSignalCableBlock.power(level, firstCable);
        cleanupPath(level, path);

        if (recoveredStats.lastTruncated() || recoveredEvidence.quality() != PortQuality.VALID
                || recoveredEvidence.sourceCount() != 1 || recoveredPower != 15) {
            helper.fail("Complete insulated-redstone recompute did not recover after a truncated STALE state"
                    + " | nodes=" + recoveredStats.lastNodes() + " truncated=" + recoveredStats.lastTruncated()
                    + " quality=" + recoveredEvidence.quality() + " sources=" + recoveredEvidence.sourceCount()
                    + " power=" + recoveredPower);
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

    private static void wireCablePath(ServerLevel level, List<BlockPos> path, int startInclusive, int endExclusive) {
        for (int i = startInclusive; i < endExclusive; i++) {
            BlockPos pos = path.get(i);
            BlockState state = RedstoneEngineering.REDSTONE_SIGNAL_CABLE.get().defaultBlockState();
            if (i > startInclusive) state = setCableArm(state, horizontalDirection(pos, path.get(i - 1)), true);
            if (i + 1 < endExclusive) state = setCableArm(state, horizontalDirection(pos, path.get(i + 1)), true);
            if (i == startInclusive && startInclusive > 0) {
                state = setCableArm(state, horizontalDirection(pos, path.get(i - 1)), true);
            }
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
