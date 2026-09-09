package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.ConnectedCableBlock;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.DataBusNetwork;
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

/** Strict proof that a budget-truncated 8-bit bus never publishes a partial component as trusted. */
public final class RseDataBusNetworkBudgetSystemGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseDataBusNetworkBudgetSystemGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 140)
    public static void truncatedDataBusMustFailClosedAsStale(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 1, 2));
        List<BlockPos> path = planarSnake(anchor);
        BlockPos firstBus = path.get(0);
        BlockPos encoder = firstBus.west();
        BlockPos source = encoder.west();

        if (!level.hasChunkAt(source)) {
            helper.fail("Precondition failed: DATA_BUS_8 source/encoder fixture is not fully loaded");
            return;
        }
        for (BlockPos pos : path) {
            if (!level.hasChunkAt(pos)) {
                helper.fail("Precondition failed: DATA_BUS_8 budget path crossed unloaded terrain at " + pos);
                return;
            }
        }

        level.setBlock(source, RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.EAST)
                .setValue(RedstoneReferenceSourceBlock.POWER, 11), Block.UPDATE_CLIENTS);
        level.setBlock(encoder, RedstoneEngineering.REDSTONE_BYTE_ENCODER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST), Block.UPDATE_CLIENTS);
        for (BlockPos pos : path) {
            level.setBlock(pos, RedstoneEngineering.EIGHT_BIT_DATA_BUS.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        wireBusPath(level, path);

        helper.runAfterDelay(8, () -> {
            DataBusNetwork.resolve(level, DataBusNetwork.collect(level, firstBus));
            NetworkKernel.ScanStats stats = NetworkKernel.stats(level, "bus8");
            DataBusNetwork.Diagnostics diagnostics = DataBusNetwork.getDiagnostics(level, firstBus);
            PortQuality quality = DataBusNetwork.quality(level, firstBus);
            int value = DataBusNetwork.sample(level, firstBus);

            cleanup(level, source, encoder, path);

            if (!stats.lastTruncated() || stats.lastNodes() != NetworkKernel.MAX_NODES) {
                helper.fail("Precondition failed: 135-node DATA_BUS_8 component did not hit the 128-node budget"
                        + " | nodes=" + stats.lastNodes() + " truncated=" + stats.lastTruncated());
                return;
            }
            if (!diagnostics.truncated()
                    || diagnostics.nodes() != NetworkKernel.MAX_NODES
                    || quality != PortQuality.STALE
                    || diagnostics.valid()
                    || value != 0) {
                helper.fail("Budget-truncated DATA_BUS_8 solve published partial evidence instead of fail-closed STALE"
                        + " | quality=" + quality
                        + " value=" + value
                        + " diagNodes=" + diagnostics.nodes()
                        + " diagDrivers=" + diagnostics.driverCount()
                        + " diagDistinct=" + diagnostics.distinctValues()
                        + " diagValid=" + diagnostics.valid()
                        + " diagTruncated=" + diagnostics.truncated());
                return;
            }
            helper.succeed();
        });
    }

    private static List<BlockPos> planarSnake(BlockPos anchor) {
        int minX = anchor.getX() & ~15;
        int minZ = anchor.getZ() & ~15;
        int y = Math.min(anchor.getY() + 16, anchor.getY() + 16);
        List<BlockPos> path = new ArrayList<>(135);
        for (int row = 0; row < 11 && path.size() < 135; row++) {
            int z = minZ + row;
            if ((row & 1) == 0) {
                for (int x = minX + 2; x <= minX + 14 && path.size() < 135; x++) {
                    path.add(new BlockPos(x, y, z));
                }
            } else {
                for (int x = minX + 14; x >= minX + 2 && path.size() < 135; x--) {
                    path.add(new BlockPos(x, y, z));
                }
            }
        }
        return path;
    }

    private static void wireBusPath(ServerLevel level, List<BlockPos> path) {
        for (int i = 0; i < path.size(); i++) {
            BlockPos pos = path.get(i);
            BlockState state = RedstoneEngineering.EIGHT_BIT_DATA_BUS.get().defaultBlockState();
            if (i > 0) state = setArm(state, direction(pos, path.get(i - 1)), true);
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
        throw new IllegalArgumentException("Non-adjacent DATA_BUS_8 path nodes: " + from + " -> " + to);
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

    private static void cleanup(ServerLevel level, BlockPos source, BlockPos encoder, List<BlockPos> path) {
        for (int i = path.size() - 1; i >= 0; i--) {
            level.setBlock(path.get(i), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        level.setBlock(encoder, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(source, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }
}
