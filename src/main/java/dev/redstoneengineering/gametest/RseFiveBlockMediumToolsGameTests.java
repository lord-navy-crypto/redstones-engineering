package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.AnalogIndicatorBlock;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.RedstoneCableTerminalBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.RedstoneSignalCableBlock;
import dev.redstoneengineering.block.SignalConditionerBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.RedstoneCableNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Five-block medium/key-tool system validation:
 * Signal Conditioner -> Cable Terminal -> Insulated Redstone Cable -> Cable Terminal -> Analog Indicator.
 *
 * <p>A Redstone Reference Source is used only as the deterministic test fixture and is not one of the
 * five devices under validation. These tests exercise the real medium boundary, quality propagation,
 * saturation, valid zero, disconnect cleanup and recovery without creating a feedback loop.</p>
 */
public final class RseFiveBlockMediumToolsGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private static final BlockPos SOURCE = new BlockPos(0, 1, 2);
    private static final BlockPos CONDITIONER = new BlockPos(1, 1, 2);
    private static final BlockPos INPUT_TERMINAL = new BlockPos(2, 1, 2);
    private static final BlockPos CABLE = new BlockPos(3, 1, 2);
    private static final BlockPos OUTPUT_TERMINAL = new BlockPos(3, 1, 3);
    private static final BlockPos INDICATOR = new BlockPos(3, 1, 4);

    private RseFiveBlockMediumToolsGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 70)
    public static void conditionedSignalCrossesInsulatedMediumWithSaturationAndValidZero(GameTestHelper helper) {
        buildSystem(helper, 3);

        helper.runAfterDelay(7, () -> {
            if (!assertSystemValue(helper, 6, PortQuality.VALID, "gain x2 normal transfer")) return;

            helper.setBlock(SOURCE, reference(10));
            helper.runAfterDelay(7, () -> {
                if (!assertSystemValue(helper, 15, PortQuality.VALID, "gain saturation transfer")) return;

                var conditionerOut = RedstoneEngineering.SIGNAL_CONDITIONER.get().engineeringSnapshot(
                        helper.getLevel(), helper.absolutePos(CONDITIONER), helper.getBlockState(CONDITIONER), Direction.EAST).orElse(null);
                if (conditionerOut == null || conditionerOut.quality() != PortQuality.SATURATED
                        || Math.round(conditionerOut.value()) != 15) {
                    helper.fail("Conditioner saturation was not preserved as SATURATED at the tool boundary", CONDITIONER);
                    return;
                }

                helper.setBlock(SOURCE, reference(0));
                helper.runAfterDelay(7, () -> {
                    if (!assertSystemValue(helper, 0, PortQuality.VALID, "connected valid zero")) return;

                    RedstoneCableNetwork.SourceEvidence cableEvidence = RedstoneCableNetwork.sourceEvidence(
                            helper.getLevel(), helper.absolutePos(CABLE));
                    if (cableEvidence.sourceCount() < 1 || cableEvidence.quality() != PortQuality.VALID) {
                        helper.fail("A connected zero-valued source was lost by the insulated medium", CABLE);
                        return;
                    }
                    helper.succeed();
                });
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void mediumDisconnectPropagatesNoSignalAndRecoversWithoutGhostValue(GameTestHelper helper) {
        buildSystem(helper, 7);

        helper.runAfterDelay(7, () -> {
            if (!assertSystemValue(helper, 14, PortQuality.VALID, "pre-disconnect")) return;

            helper.setBlock(CABLE, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(6, () -> {
                BlockState outputTerminal = helper.getBlockState(OUTPUT_TERMINAL);
                var terminalOut = RedstoneEngineering.REDSTONE_CABLE_TERMINAL.get().engineeringSnapshot(
                        helper.getLevel(), helper.absolutePos(OUTPUT_TERMINAL), outputTerminal, Direction.SOUTH).orElse(null);
                var indicatorIn = RedstoneEngineering.ANALOG_INDICATOR.get().engineeringSnapshot(
                        helper.getLevel(), helper.absolutePos(INDICATOR), helper.getBlockState(INDICATOR), Direction.NORTH).orElse(null);

                if (outputTerminal.getValue(RedstoneCableTerminalBlock.POWER) != 0
                        || terminalOut == null || terminalOut.quality() != PortQuality.NO_SIGNAL) {
                    helper.fail("Disconnected output terminal retained a ghost cable value/source", OUTPUT_TERMINAL);
                    return;
                }
                if (indicatorIn == null || indicatorIn.quality() != PortQuality.NO_SIGNAL
                        || Math.round(indicatorIn.value()) != 0) {
                    helper.fail("Indicator did not propagate the disconnected terminal's NO_SIGNAL quality", INDICATOR);
                    return;
                }

                // Upstream processing must remain healthy even though the transport medium is broken.
                if (helper.getBlockState(CONDITIONER).getValue(DirectionalSignalBlock.OUTPUT) != 14) {
                    helper.fail("Breaking the transport medium back-drove the upstream Conditioner", CONDITIONER);
                    return;
                }

                helper.setBlock(CABLE, RedstoneEngineering.REDSTONE_SIGNAL_CABLE.get().defaultBlockState());
                helper.runAfterDelay(7, () -> {
                    if (!assertSystemValue(helper, 14, PortQuality.VALID, "medium recovery")) return;
                    helper.succeed();
                });
            });
        });
    }

    private static void buildSystem(GameTestHelper helper, int sourcePower) {
        helper.setBlock(SOURCE, reference(sourcePower));
        helper.setBlock(CONDITIONER, RedstoneEngineering.SIGNAL_CONDITIONER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(SignalConditionerBlock.MODE, 0)
                .setValue(SignalConditionerBlock.PARAM, 2));
        helper.setBlock(INPUT_TERMINAL, RedstoneEngineering.REDSTONE_CABLE_TERMINAL.get().defaultBlockState()
                .setValue(RedstoneCableTerminalBlock.FACING, Direction.WEST)
                .setValue(RedstoneCableTerminalBlock.OUTPUT_MODE, false));
        helper.setBlock(OUTPUT_TERMINAL, RedstoneEngineering.REDSTONE_CABLE_TERMINAL.get().defaultBlockState()
                .setValue(RedstoneCableTerminalBlock.FACING, Direction.SOUTH)
                .setValue(RedstoneCableTerminalBlock.OUTPUT_MODE, true));
        helper.setBlock(INDICATOR, RedstoneEngineering.ANALOG_INDICATOR.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.SOUTH));
        // Place the transport medium last so both terminal edges resolve in one bounded refresh.
        helper.setBlock(CABLE, RedstoneEngineering.REDSTONE_SIGNAL_CABLE.get().defaultBlockState());
    }

    private static BlockState reference(int power) {
        return RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.EAST)
                .setValue(RedstoneReferenceSourceBlock.POWER, power);
    }

    private static boolean assertSystemValue(
            GameTestHelper helper,
            int expected,
            PortQuality expectedTerminalQuality,
            String phase
    ) {
        BlockState conditioner = helper.getBlockState(CONDITIONER);
        BlockState inputTerminal = helper.getBlockState(INPUT_TERMINAL);
        BlockState outputTerminal = helper.getBlockState(OUTPUT_TERMINAL);
        BlockState indicator = helper.getBlockState(INDICATOR);

        int conditioned = conditioner.getValue(DirectionalSignalBlock.OUTPUT);
        int cablePower = RedstoneSignalCableBlock.power(helper.getLevel(), helper.absolutePos(CABLE));
        int inTerminalPower = inputTerminal.getValue(RedstoneCableTerminalBlock.POWER);
        int outTerminalPower = outputTerminal.getValue(RedstoneCableTerminalBlock.POWER);
        int indicated = indicator.getValue(AnalogIndicatorBlock.LEVEL);

        if (conditioned != expected || cablePower != expected || inTerminalPower != expected
                || outTerminalPower != expected || indicated != expected) {
            helper.fail("Five-block medium path mismatch during " + phase + ": expected " + expected
                    + " conditioner=" + conditioned + " inTerminal=" + inTerminalPower
                    + " cable=" + cablePower + " outTerminal=" + outTerminalPower
                    + " indicator=" + indicated, CABLE);
            return false;
        }

        RedstoneCableNetwork.SourceEvidence evidence = RedstoneCableNetwork.sourceEvidence(
                helper.getLevel(), helper.absolutePos(CABLE));
        var terminalOut = RedstoneEngineering.REDSTONE_CABLE_TERMINAL.get().engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(OUTPUT_TERMINAL), outputTerminal, Direction.SOUTH).orElse(null);
        var indicatorIn = RedstoneEngineering.ANALOG_INDICATOR.get().engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(INDICATOR), indicator, Direction.NORTH).orElse(null);

        if (evidence.quality() != expectedTerminalQuality
                || terminalOut == null || terminalOut.quality() != expectedTerminalQuality
                || indicatorIn == null || indicatorIn.quality() != expectedTerminalQuality) {
            helper.fail("Five-block medium quality mismatch during " + phase, OUTPUT_TERMINAL);
            return false;
        }
        return true;
    }
}
