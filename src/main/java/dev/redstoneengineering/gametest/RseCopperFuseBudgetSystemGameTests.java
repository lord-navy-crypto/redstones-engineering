package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.ConnectedCableBlock;
import dev.redstoneengineering.block.CopperFuseBlock;
import dev.redstoneengineering.block.CopperResistiveLoadBlock;
import dev.redstoneengineering.block.CopperVoltageSourceBlock;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.CircuitPhysics;
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

/** Protection proof: a bounded load estimate must never become a definitive fuse decision. */
public final class RseCopperFuseBudgetSystemGameTests {
    private static final String TEMPLATE = "empty5x4x5";
    private static final int LONG_WIRES = 130;
    private static final int SHORT_WIRES = 6;

    private RseCopperFuseBudgetSystemGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 120)
    public static void truncatedLoadScanFailsClosedUntilCompleteProtectionEvidence(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos fuse = anchor.offset(0, 80, 0);
        BlockPos source = fuse.west();
        BlockPos firstWire = fuse.east();
        List<BlockPos> wires = verticalWires(firstWire, LONG_WIRES);
        BlockPos farLoad = firstWire.above(LONG_WIRES);

        if (!allLoaded(level, source, fuse, wires, farLoad, helper)) return;

        level.setBlock(source, RedstoneEngineering.COPPER_VOLTAGE_SOURCE.get().defaultBlockState()
                .setValue(CopperVoltageSourceBlock.VOLTAGE, 15), Block.UPDATE_CLIENTS);
        level.setBlock(fuse, RedstoneEngineering.COPPER_FUSE.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                .setValue(CopperFuseBlock.RATING, 4)
                .setValue(CopperFuseBlock.TRIPPED, false), Block.UPDATE_CLIENTS);
        placeVerticalWireRun(level, fuse, wires);
        level.setBlock(farLoad, RedstoneEngineering.COPPER_RESISTIVE_LOAD.get().defaultBlockState()
                .setValue(CopperResistiveLoadBlock.RESISTANCE, 1), Block.UPDATE_CLIENTS);

        helper.runAfterDelay(8, () -> {
            // Refresh the load-scan evidence from the exact protected output component before asserting it.
            CircuitPhysics.equivalentLoadResistance(level, firstWire, NetworkKernel.MAX_NODES);
            NetworkKernel.ScanStats truncatedStats = NetworkKernel.stats(level, "copper_load");
            BlockState fuseState = level.getBlockState(fuse);
            var input = RedstoneEngineering.COPPER_FUSE.get().engineeringSnapshot(
                    level, fuse, fuseState, Direction.WEST).orElseThrow();
            var output = RedstoneEngineering.COPPER_FUSE.get().engineeringSnapshot(
                    level, fuse, fuseState, Direction.EAST).orElseThrow();

            if (!truncatedStats.lastTruncated() || truncatedStats.lastNodes() != NetworkKernel.MAX_NODES) {
                cleanup(level, source, fuse, wires, farLoad, null);
                helper.fail("Precondition failed: long fuse load graph did not hit the 128-node budget"
                        + " | nodes=" + truncatedStats.lastNodes()
                        + " truncated=" + truncatedStats.lastTruncated());
                return;
            }
            if (input.quality() != PortQuality.VALID || (int) input.value() != 15) {
                cleanup(level, source, fuse, wires, farLoad, null);
                helper.fail("Precondition failed: fuse input was not a real VALID 15 V source"
                        + " | input=" + input.value() + " quality=" + input.quality());
                return;
            }
            if (fuseState.getValue(CopperFuseBlock.TRIPPED)
                    || output.quality() != PortQuality.STALE
                    || CopperFuseBlock.outputVoltage(level, fuse) != 0) {
                cleanup(level, source, fuse, wires, farLoad, null);
                helper.fail("Budget-truncated load estimate became a definitive fuse decision instead of fail-closed STALE"
                        + " | tripped=" + fuseState.getValue(CopperFuseBlock.TRIPPED)
                        + " outputQuality=" + output.quality()
                        + " outputVoltage=" + CopperFuseBlock.outputVoltage(level, fuse)
                        + " nodes=" + truncatedStats.lastNodes());
                return;
            }

            // Move the same 1-ohm load inside a complete short graph. Full evidence must resume
            // normal protection evaluation and trip the 4 A fuse at 15 V.
            level.setBlock(farLoad, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            for (int i = wires.size() - 1; i >= SHORT_WIRES; i--) {
                level.setBlock(wires.get(i), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            BlockPos nearLoad = firstWire.above(SHORT_WIRES);
            level.setBlock(nearLoad, RedstoneEngineering.COPPER_RESISTIVE_LOAD.get().defaultBlockState()
                    .setValue(CopperResistiveLoadBlock.RESISTANCE, 1), Block.UPDATE_CLIENTS);

            helper.runAfterDelay(8, () -> {
                CircuitPhysics.equivalentLoadResistance(level, firstWire, NetworkKernel.MAX_NODES);
                NetworkKernel.ScanStats completeStats = NetworkKernel.stats(level, "copper_load");
                BlockState recoveredState = level.getBlockState(fuse);
                var recoveredOutput = RedstoneEngineering.COPPER_FUSE.get().engineeringSnapshot(
                        level, fuse, recoveredState, Direction.EAST).orElseThrow();
                boolean tripped = recoveredState.getValue(CopperFuseBlock.TRIPPED);
                PortQuality outputQuality = recoveredOutput.quality();
                int outputVoltage = CopperFuseBlock.outputVoltage(level, fuse);
                cleanup(level, source, fuse, wires.subList(0, SHORT_WIRES), null, nearLoad);

                if (completeStats.lastTruncated()) {
                    helper.fail("Short fuse load graph remained truncated after recovery"
                            + " | nodes=" + completeStats.lastNodes());
                    return;
                }
                if (!tripped || outputQuality != PortQuality.FAULT || outputVoltage != 0) {
                    helper.fail("Fuse did not resume definitive protection after complete 1-ohm load evidence"
                            + " | tripped=" + tripped
                            + " outputQuality=" + outputQuality
                            + " outputVoltage=" + outputVoltage
                            + " nodes=" + completeStats.lastNodes());
                    return;
                }
                helper.succeed();
            });
        });
    }

    private static List<BlockPos> verticalWires(BlockPos first, int count) {
        List<BlockPos> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) result.add(first.above(i));
        return result;
    }

    private static void placeVerticalWireRun(ServerLevel level, BlockPos fuse, List<BlockPos> wires) {
        for (int i = 0; i < wires.size(); i++) {
            BlockState state = RedstoneEngineering.COPPER_WIRE.get().defaultBlockState();
            if (i == 0) state = setArm(state, Direction.WEST, true);
            else state = setArm(state, Direction.DOWN, true);
            state = setArm(state, Direction.UP, true);
            level.setBlock(wires.get(i), state, Block.UPDATE_CLIENTS);
        }
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

    private static boolean allLoaded(
            ServerLevel level,
            BlockPos source,
            BlockPos fuse,
            List<BlockPos> wires,
            BlockPos load,
            GameTestHelper helper
    ) {
        if (!level.hasChunkAt(source) || !level.hasChunkAt(fuse) || !level.hasChunkAt(load)) {
            helper.fail("Precondition failed: fuse budget fixture endpoint chunk is not loaded");
            return false;
        }
        for (BlockPos wire : wires) {
            if (!level.hasChunkAt(wire)) {
                helper.fail("Precondition failed: vertical fuse budget fixture crossed unloaded terrain at " + wire);
                return false;
            }
        }
        return true;
    }

    private static void cleanup(
            ServerLevel level,
            BlockPos source,
            BlockPos fuse,
            List<BlockPos> wires,
            BlockPos farLoad,
            BlockPos nearLoad
    ) {
        if (farLoad != null) level.setBlock(farLoad, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        if (nearLoad != null) level.setBlock(nearLoad, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        for (int i = wires.size() - 1; i >= 0; i--) {
            level.setBlock(wires.get(i), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        level.setBlock(fuse, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(source, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }
}
