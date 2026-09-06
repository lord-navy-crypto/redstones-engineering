package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.ConnectedCableBlock;
import dev.redstoneengineering.block.OpticalEmitterBlock;
import dev.redstoneengineering.block.OpticalFiberJunctionBlock;
import dev.redstoneengineering.block.OpticalReceiverBlock;
import dev.redstoneengineering.block.RedstoneCableTerminalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Runtime regressions for the early-interconnect engineering refactor. */
public final class RseInterconnectRefactorGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseInterconnectRefactorGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void insulatedRedstoneCutClearsSeparatedOutput(GameTestHelper helper) {
        BlockPos vanillaSource = new BlockPos(0, 1, 2);
        BlockPos inputTerminal = new BlockPos(1, 1, 2);
        BlockPos cable = new BlockPos(2, 1, 2);
        BlockPos outputTerminal = new BlockPos(3, 1, 2);

        helper.setBlock(vanillaSource, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(inputTerminal, RedstoneEngineering.REDSTONE_CABLE_TERMINAL.get().defaultBlockState()
                .setValue(RedstoneCableTerminalBlock.FACING, Direction.WEST)
                .setValue(RedstoneCableTerminalBlock.OUTPUT_MODE, false));
        helper.setBlock(outputTerminal, RedstoneEngineering.REDSTONE_CABLE_TERMINAL.get().defaultBlockState()
                .setValue(RedstoneCableTerminalBlock.FACING, Direction.EAST)
                .setValue(RedstoneCableTerminalBlock.OUTPUT_MODE, true));
        helper.setBlock(cable, RedstoneEngineering.REDSTONE_SIGNAL_CABLE.get().defaultBlockState());

        helper.runAfterDelay(4, () -> {
            BlockState cableState = helper.getBlockState(cable);
            BlockState outputState = helper.getBlockState(outputTerminal);
            if (ConnectedCableBlock.connectionCount(cableState) != 2
                    || outputState.getValue(RedstoneCableTerminalBlock.POWER) != 15) {
                helper.fail("Insulated redstone link did not establish a full-strength two-ended path", cable);
                return;
            }

            helper.setBlock(cable, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(3, () -> {
                BlockState separatedOutput = helper.getBlockState(outputTerminal);
                if (separatedOutput.getValue(RedstoneCableTerminalBlock.POWER) != 0) {
                    helper.fail("Cut insulated redstone cable left stale power on the separated output component", outputTerminal);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void opticalServiceSpliceIsolatesAndRestores(GameTestHelper helper) {
        BlockPos emitter = new BlockPos(1, 1, 2);
        BlockPos splicePos = new BlockPos(2, 1, 2);
        BlockPos receiver = new BlockPos(3, 1, 2);

        helper.setBlock(emitter, RedstoneEngineering.OPTICAL_EMITTER.get().defaultBlockState()
                .setValue(OpticalEmitterBlock.INTENSITY, 11)
                .setValue(OpticalEmitterBlock.CHANNEL, 3));
        helper.setBlock(receiver, RedstoneEngineering.OPTICAL_RECEIVER.get().defaultBlockState());
        helper.setBlock(splicePos, RedstoneEngineering.OPTICAL_FIBER_JUNCTION.get().defaultBlockState());

        helper.runAfterDelay(4, () -> {
            BlockState closed = helper.getBlockState(splicePos);
            if (closed.getValue(OpticalFiberJunctionBlock.SERVICE_OPEN)
                    || ConnectedCableBlock.connectionCount(closed) != 2
                    || RedstoneEngineering.OPTICAL_FIBER_JUNCTION.get().engineeringPorts(closed).size() != 2
                    || !OpticalReceiverBlock.valid(helper.getLevel(), helper.absolutePos(receiver))
                    || OpticalReceiverBlock.intensity(helper.getLevel(), helper.absolutePos(receiver)) != 11
                    || OpticalReceiverBlock.channel(helper.getLevel(), helper.absolutePos(receiver)) != 3) {
                helper.fail("Closed optical service splice did not preserve the two-ended optical path", splicePos);
                return;
            }

            RedstoneEngineering.OPTICAL_FIBER_JUNCTION.get().setServiceOpen(
                    helper.getLevel(), helper.absolutePos(splicePos), true);
            helper.runAfterDelay(3, () -> {
                BlockState open = helper.getBlockState(splicePos);
                if (!open.getValue(OpticalFiberJunctionBlock.SERVICE_OPEN)
                        || ConnectedCableBlock.connectionCount(open) != 0
                        || !RedstoneEngineering.OPTICAL_FIBER_JUNCTION.get().engineeringPorts(open).isEmpty()
                        || OpticalReceiverBlock.valid(helper.getLevel(), helper.absolutePos(receiver))
                        || OpticalReceiverBlock.intensity(helper.getLevel(), helper.absolutePos(receiver)) != 0) {
                    helper.fail("SERVICE_OPEN optical splice did not isolate and clear the downstream segment", splicePos);
                    return;
                }

                RedstoneEngineering.OPTICAL_FIBER_JUNCTION.get().setServiceOpen(
                        helper.getLevel(), helper.absolutePos(splicePos), false);
                helper.runAfterDelay(3, () -> {
                    BlockState restored = helper.getBlockState(splicePos);
                    if (restored.getValue(OpticalFiberJunctionBlock.SERVICE_OPEN)
                            || ConnectedCableBlock.connectionCount(restored) != 2
                            || RedstoneEngineering.OPTICAL_FIBER_JUNCTION.get().engineeringPorts(restored).size() != 2
                            || !OpticalReceiverBlock.valid(helper.getLevel(), helper.absolutePos(receiver))
                            || OpticalReceiverBlock.intensity(helper.getLevel(), helper.absolutePos(receiver)) != 11
                            || OpticalReceiverBlock.channel(helper.getLevel(), helper.absolutePos(receiver)) != 3) {
                        helper.fail("Closing the optical service splice did not restore continuity", splicePos);
                        return;
                    }
                    helper.succeed();
                });
            });
        });
    }
}
