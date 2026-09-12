package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.ConnectedCableBlock;
import dev.redstoneengineering.block.CopperVoltageSourceBlock;
import dev.redstoneengineering.block.CopperWireBlock;
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

/** Strict runtime proof that a budget-truncated Copper graph must not publish a partial voltage solve as trusted. */
public final class RseCopperNetworkBudgetSystemGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseCopperNetworkBudgetSystemGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 120)
    public static void truncatedCopperScanMustNotPublishPartialVoltageAsValid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 1, 2));
        if (!level.hasChunkAt(anchor)) {
            helper.fail("Precondition failed: GameTest anchor chunk is not loaded");
            return;
        }

        List<BlockPos> path = planarSnake(anchor);
        if (!allLoaded(level, path, helper)) return;
        BlockPos source = path.get(0);
        BlockPos observed = path.get(1);

        level.setBlock(source, RedstoneEngineering.COPPER_VOLTAGE_SOURCE.get().defaultBlockState()
                .setValue(CopperVoltageSourceBlock.VOLTAGE, 15), Block.UPDATE_CLIENTS);
        for (int i = 1; i < path.size(); i++) {
            level.setBlock(path.get(i), RedstoneEngineering.COPPER_WIRE.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        wireCopperPath(level, path);

        DomainNetwork.recomputeCopper(level, observed);
        NetworkKernel.ScanStats stats = NetworkKernel.stats(level, "copper");
        BlockState observedState = level.getBlockState(observed);
        PortQuality quality = CopperWireBlock.quality(level, observed, observedState);
        int voltage = CopperWireBlock.voltage(level, observed);
        int drivers = CopperWireBlock.driverCount(level, observed);
        int connections = ConnectedCableBlock.connectionCount(observedState);
        cleanup(level, path);

        if (!stats.lastTruncated() || stats.lastNodes() != NetworkKernel.MAX_NODES) {
            helper.fail("Precondition failed: 135-node legal Copper component did not hit the 128-node budget"
                    + " | nodes=" + stats.lastNodes() + " truncated=" + stats.lastTruncated()
                    + " observedConnections=" + connections);
            return;
        }
        if (connections > 2) {
            helper.fail("Precondition failed: Copper budget fixture accidentally created an illegal branch"
                    + " | observedConnections=" + connections);
            return;
        }
        if (quality != PortQuality.STALE || voltage != 0) {
            helper.fail("Budget-truncated Copper solve published partial voltage evidence instead of fail-closed STALE"
                    + " | quality=" + quality + " voltage=" + voltage + " drivers=" + drivers
                    + " nodes=" + stats.lastNodes() + " truncated=" + stats.lastTruncated());
            return;
        }
        helper.succeed();
    }

    private static List<BlockPos> planarSnake(BlockPos anchor) {
        int minX = anchor.getX() & ~15;
        int minZ = anchor.getZ() & ~15;
        int y = Math.min(anchor.getY() + 16, anchor.getY() + 16);
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

    private static void wireCopperPath(ServerLevel level, List<BlockPos> path) {
        for (int i = 1; i < path.size(); i++) {
            BlockPos pos = path.get(i);
            BlockState state = RedstoneEngineering.COPPER_WIRE.get().defaultBlockState();
            state = setArm(state, direction(pos, path.get(i - 1)), true);
            if (i + 1 < path.size()) state = setArm(state, direction(pos, path.get(i + 1)), true);
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }

    private static Direction direction(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        if (dx == 1 && dz == 0) return Direction.EAST;
        if (dx == -1 && dz == 0) return Direction.WEST;
        if (dx == 0 && dz == 1) return Direction.SOUTH;
        if (dx == 0 && dz == -1) return Direction.NORTH;
        throw new IllegalArgumentException("Non-adjacent Copper path nodes: " + from + " -> " + to);
    }

    private static BlockState setArm(BlockState state, Direction direction, boolean value) {
        return switch (direction) {
            case NORTH -> state.setValue(ConnectedCableBlock.NORTH, value);
            case EAST -> state.setValue(ConnectedCableBlock.EAST, value);
            case SOUTH -> state.setValue(ConnectedCableBlock.SOUTH, value);
            case WEST -> state.setValue(ConnectedCableBlock.WEST, value);
            case UP -> state.setValue(ConnectedCableBlock.UP, value);
            case DOWN -> state.setValue(ConnectedCableBlock.DOWN, value);
        };
    }

    private static boolean allLoaded(ServerLevel level, List<BlockPos> path, GameTestHelper helper) {
        for (BlockPos pos : path) {
            if (!level.hasChunkAt(pos)) {
                helper.fail("Precondition failed: single-chunk Copper path unexpectedly crosses unloaded terrain at " + pos);
                return false;
            }
        }
        return true;
    }

    private static void cleanup(ServerLevel level, List<BlockPos> path) {
        for (int i = path.size() - 1; i >= 0; i--) {
            level.setBlock(path.get(i), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
