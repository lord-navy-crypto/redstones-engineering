package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.ConnectedCableBlock;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.DifferentialNetwork;
import dev.redstoneengineering.physics.InformationRuntime;
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

/** Strict lifecycle proof that a budget-truncated differential component never publishes partial trusted data. */
public final class RseDifferentialNetworkBudgetSystemGameTests {
    private static final String TEMPLATE = "empty5x4x5";
    private static final int SHORT_COMPONENT_NODES = 16;

    private RseDifferentialNetworkBudgetSystemGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 160)
    public static void truncatedDifferentialMustFailClosedAsStaleAndRecover(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 1, 2));
        List<BlockPos> path = planarSnake(anchor);
        BlockPos firstPair = path.get(0);
        BlockPos driver = firstPair.west();

        if (!level.hasChunkAt(driver)) {
            helper.fail("Precondition failed: DIFFERENTIAL_DATA driver fixture is not fully loaded");
            return;
        }
        for (BlockPos pos : path) {
            if (!level.hasChunkAt(pos)) {
                helper.fail("Precondition failed: DIFFERENTIAL_DATA budget path crossed unloaded terrain at " + pos);
                return;
            }
        }

        level.setBlock(driver, RedstoneEngineering.DIFFERENTIAL_DRIVER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST), Block.UPDATE_CLIENTS);
        InformationRuntime.write(level, "diff_out", driver, 1, 0, true, 100);
        placePairRange(level, path, 0, SHORT_COMPONENT_NODES);
        wirePairPath(level, path.subList(0, SHORT_COMPONENT_NODES));
        DifferentialNetwork.recompute(level, firstPair);

        helper.runAfterDelay(5, () -> {
            InformationRuntime.Snapshot initial = InformationRuntime.snapshot(level, "diff", firstPair);
            if (DifferentialNetwork.quality(level, firstPair) != PortQuality.VALID
                    || !initial.valid() || (initial.value() & 1) != 1
                    || DifferentialNetwork.driverCount(level, firstPair) != 1
                    || DifferentialNetwork.truncated(level, firstPair)) {
                cleanup(level, driver, path);
                helper.fail("Precondition failed: short DIFFERENTIAL_DATA component did not establish a trusted HIGH bit");
                return;
            }

            placePairRange(level, path, SHORT_COMPONENT_NODES, path.size());
            wirePairPath(level, path);
            DifferentialNetwork.recompute(level, firstPair);

            helper.runAfterDelay(5, () -> {
                NetworkKernel.ScanStats stats = NetworkKernel.stats(level, "diff");
                InformationRuntime.Snapshot fault = InformationRuntime.snapshot(level, "diff", firstPair);
                PortQuality faultQuality = DifferentialNetwork.quality(level, firstPair);

                if (!stats.lastTruncated() || stats.lastNodes() != NetworkKernel.MAX_NODES
                        || !DifferentialNetwork.truncated(level, firstPair)
                        || DifferentialNetwork.driverCount(level, firstPair) != 1
                        || faultQuality != PortQuality.STALE
                        || fault.valid() || (fault.value() & 1) != 0) {
                    cleanup(level, driver, path);
                    helper.fail("Budget-truncated DIFFERENTIAL_DATA component published partial evidence instead of fail-closed STALE"
                            + " | nodes=" + stats.lastNodes() + " truncated=" + stats.lastTruncated()
                            + " quality=" + faultQuality + " value=" + (fault.value() & 1)
                            + " runtimeValid=" + fault.valid()
                            + " drivers=" + DifferentialNetwork.driverCount(level, firstPair)
                            + " nodeTruncated=" + DifferentialNetwork.truncated(level, firstPair));
                    return;
                }

                for (int i = path.size() - 1; i >= SHORT_COMPONENT_NODES; i--) {
                    level.setBlock(path.get(i), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                wirePairPath(level, path.subList(0, SHORT_COMPONENT_NODES));
                DifferentialNetwork.recompute(level, firstPair);

                helper.runAfterDelay(5, () -> {
                    InformationRuntime.Snapshot recovered = InformationRuntime.snapshot(level, "diff", firstPair);
                    PortQuality recoveredQuality = DifferentialNetwork.quality(level, firstPair);
                    if (recoveredQuality != PortQuality.VALID
                            || !recovered.valid() || (recovered.value() & 1) != 1
                            || DifferentialNetwork.driverCount(level, firstPair) != 1
                            || DifferentialNetwork.truncated(level, firstPair)) {
                        cleanup(level, driver, path);
                        helper.fail("DIFFERENTIAL_DATA component did not recover exact trusted HIGH after returning below budget"
                                + " | quality=" + recoveredQuality
                                + " value=" + (recovered.value() & 1)
                                + " valid=" + recovered.valid()
                                + " drivers=" + DifferentialNetwork.driverCount(level, firstPair)
                                + " truncated=" + DifferentialNetwork.truncated(level, firstPair));
                        return;
                    }
                    cleanup(level, driver, path);
                    helper.succeed();
                });
            });
        });
    }

    private static List<BlockPos> planarSnake(BlockPos anchor) {
        int minX = anchor.getX() & ~15;
        int minZ = anchor.getZ() & ~15;
        int y = anchor.getY() + 16;
        List<BlockPos> path = new ArrayList<>(135);
        for (int row = 0; row < 11 && path.size() < 135; row++) {
            int z = minZ + row;
            if ((row & 1) == 0) {
                for (int x = minX + 2; x <= minX + 14 && path.size() < 135; x++) path.add(new BlockPos(x, y, z));
            } else {
                for (int x = minX + 14; x >= minX + 2 && path.size() < 135; x--) path.add(new BlockPos(x, y, z));
            }
        }
        return path;
    }

    private static void placePairRange(ServerLevel level, List<BlockPos> path, int from, int to) {
        for (int i = from; i < to; i++) {
            level.setBlock(path.get(i), RedstoneEngineering.DIFFERENTIAL_DATA_PAIR.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static void wirePairPath(ServerLevel level, List<BlockPos> path) {
        for (int i = 0; i < path.size(); i++) {
            BlockPos pos = path.get(i);
            BlockState state = RedstoneEngineering.DIFFERENTIAL_DATA_PAIR.get().defaultBlockState();
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
        throw new IllegalArgumentException("Non-adjacent DIFFERENTIAL_DATA path nodes: " + from + " -> " + to);
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

    private static void cleanup(ServerLevel level, BlockPos driver, List<BlockPos> path) {
        for (int i = path.size() - 1; i >= 0; i--) {
            level.setBlock(path.get(i), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        level.setBlock(driver, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }
}
