package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.ConnectedCableBlock;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.NetworkKernel;
import dev.redstoneengineering.physics.SerialNetwork;
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

/** Strict lifecycle proof that a budget-truncated serial component never publishes partial trusted data. */
public final class RseSerialNetworkBudgetSystemGameTests {
    private static final String TEMPLATE = "empty5x4x5";
    private static final int SHORT_COMPONENT_NODES = 16;

    private RseSerialNetworkBudgetSystemGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 160)
    public static void truncatedSerialMustFailClosedAsStaleAndRecover(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 1, 2));
        List<BlockPos> path = planarSnake(anchor);
        BlockPos firstLine = path.get(0);
        BlockPos serializer = firstLine.west();

        if (!level.hasChunkAt(serializer)) {
            helper.fail("Precondition failed: SERIAL_DATA serializer fixture is not fully loaded");
            return;
        }
        for (BlockPos pos : path) {
            if (!level.hasChunkAt(pos)) {
                helper.fail("Precondition failed: SERIAL_DATA budget path crossed unloaded terrain at " + pos);
                return;
            }
        }

        level.setBlock(serializer, RedstoneEngineering.SERIALIZER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST), Block.UPDATE_CLIENTS);
        InformationRuntime.write(level, "serial", serializer, 11, 8, true, 100);
        placeLineRange(level, path, 0, SHORT_COMPONENT_NODES);
        wireLinePath(level, path.subList(0, SHORT_COMPONENT_NODES));
        SerialNetwork.recompute(level, firstLine);

        helper.runAfterDelay(5, () -> {
            InformationRuntime.Snapshot initial = InformationRuntime.snapshot(level, "serial", firstLine);
            SerialNetwork.Diagnostics initialDiagnostics = SerialNetwork.getDiagnostics(level, firstLine);
            if (SerialNetwork.quality(level, firstLine) != PortQuality.VALID
                    || !initial.valid() || (initial.value() & 0xFF) != 11
                    || initialDiagnostics.driverCount() != 1 || initialDiagnostics.truncated()) {
                cleanup(level, serializer, path);
                helper.fail("Precondition failed: short SERIAL_DATA component did not establish a trusted frame");
                return;
            }

            placeLineRange(level, path, SHORT_COMPONENT_NODES, path.size());
            wireLinePath(level, path);
            SerialNetwork.recompute(level, firstLine);

            helper.runAfterDelay(5, () -> {
                NetworkKernel.ScanStats stats = NetworkKernel.stats(level, "serial");
                InformationRuntime.Snapshot fault = InformationRuntime.snapshot(level, "serial", firstLine);
                SerialNetwork.Diagnostics faultDiagnostics = SerialNetwork.getDiagnostics(level, firstLine);
                PortQuality faultQuality = SerialNetwork.quality(level, firstLine);

                if (!stats.lastTruncated() || stats.lastNodes() != NetworkKernel.MAX_NODES
                        || !faultDiagnostics.truncated()
                        || faultDiagnostics.nodes() != NetworkKernel.MAX_NODES
                        || faultDiagnostics.driverCount() != 1
                        || faultQuality != PortQuality.STALE
                        || fault.valid() || (fault.value() & 0xFF) != 0) {
                    cleanup(level, serializer, path);
                    helper.fail("Budget-truncated SERIAL_DATA component published partial evidence instead of fail-closed STALE"
                            + " | nodes=" + stats.lastNodes() + " truncated=" + stats.lastTruncated()
                            + " quality=" + faultQuality + " value=" + (fault.value() & 0xFF)
                            + " runtimeValid=" + fault.valid()
                            + " diagNodes=" + faultDiagnostics.nodes()
                            + " diagDrivers=" + faultDiagnostics.driverCount()
                            + " diagTruncated=" + faultDiagnostics.truncated());
                    return;
                }

                for (int i = path.size() - 1; i >= SHORT_COMPONENT_NODES; i--) {
                    level.setBlock(path.get(i), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                wireLinePath(level, path.subList(0, SHORT_COMPONENT_NODES));
                SerialNetwork.recompute(level, firstLine);

                helper.runAfterDelay(5, () -> {
                    InformationRuntime.Snapshot recovered = InformationRuntime.snapshot(level, "serial", firstLine);
                    SerialNetwork.Diagnostics recoveredDiagnostics = SerialNetwork.getDiagnostics(level, firstLine);
                    PortQuality recoveredQuality = SerialNetwork.quality(level, firstLine);
                    if (recoveredQuality != PortQuality.VALID
                            || !recovered.valid() || (recovered.value() & 0xFF) != 11
                            || recoveredDiagnostics.driverCount() != 1 || recoveredDiagnostics.truncated()) {
                        cleanup(level, serializer, path);
                        helper.fail("SERIAL_DATA component did not recover exact trusted frame after returning below budget"
                                + " | quality=" + recoveredQuality
                                + " value=" + (recovered.value() & 0xFF)
                                + " valid=" + recovered.valid()
                                + " drivers=" + recoveredDiagnostics.driverCount()
                                + " truncated=" + recoveredDiagnostics.truncated());
                        return;
                    }
                    cleanup(level, serializer, path);
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

    private static void placeLineRange(ServerLevel level, List<BlockPos> path, int from, int to) {
        for (int i = from; i < to; i++) {
            level.setBlock(path.get(i), RedstoneEngineering.SERIAL_DATA_LINE.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static void wireLinePath(ServerLevel level, List<BlockPos> path) {
        for (int i = 0; i < path.size(); i++) {
            BlockPos pos = path.get(i);
            BlockState state = RedstoneEngineering.SERIAL_DATA_LINE.get().defaultBlockState();
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
        throw new IllegalArgumentException("Non-adjacent SERIAL_DATA path nodes: " + from + " -> " + to);
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

    private static void cleanup(ServerLevel level, BlockPos serializer, List<BlockPos> path) {
        for (int i = path.size() - 1; i >= 0; i--) {
            level.setBlock(path.get(i), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        level.setBlock(serializer, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }
}
