package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.SignalAnalyzerBlock;
import dev.redstoneengineering.block.SignalProbeBlock;
import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Pairwise contract for registered blocks #1 Signal Analyzer and #2 Signal Probe. */
public final class RsePairwiseValidation01GameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RsePairwiseValidation01GameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 30)
    public static void analyzerSeparatesOpenApertureFromRealZero(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(2, 1, 1);
        BlockPos analyzerPos = new BlockPos(2, 1, 2);
        helper.setBlock(analyzerPos, analyzer(Direction.NORTH, SignalAnalyzerBlock.TAP));

        BlockState analyzerState = helper.getBlockState(analyzerPos);
        var open = RedstoneEngineering.SIGNAL_ANALYZER.get().engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(analyzerPos), analyzerState, Direction.NORTH).orElse(null);
        if (open == null || open.quality() != PortQuality.NO_SIGNAL || Math.round(open.value()) != 0) {
            helper.fail("Open Analyzer aperture must report NO_SIGNAL rather than confirmed zero", analyzerPos);
            return;
        }

        helper.setBlock(sourcePos, reference(Direction.SOUTH, 0));
        analyzerState = helper.getBlockState(analyzerPos);
        var realZero = RedstoneEngineering.SIGNAL_ANALYZER.get().engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(analyzerPos), analyzerState, Direction.NORTH).orElse(null);
        if (realZero == null || realZero.quality() != PortQuality.VALID || Math.round(realZero.value()) != 0) {
            helper.fail("Analyzer must preserve a real zero-valued target as VALID", analyzerPos);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void analyzerInlineFeedsProbeWithoutProbeBackDrive(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(2, 1, 0);
        BlockPos analyzerPos = new BlockPos(2, 1, 1);
        BlockPos probePos = new BlockPos(2, 1, 2);

        helper.setBlock(sourcePos, reference(Direction.SOUTH, 15));
        helper.setBlock(analyzerPos, analyzer(Direction.NORTH, SignalAnalyzerBlock.INLINE));
        helper.setBlock(probePos, probe(Direction.NORTH, 0));

        helper.runAfterDelay(4, () -> {
            BlockState analyzerState = helper.getBlockState(analyzerPos);
            BlockState probeState = helper.getBlockState(probePos);
            int probeValue = RedstoneEngineering.SIGNAL_PROBE.get().sample(
                    helper.getLevel(), helper.absolutePos(probePos), probeState);
            var probeSnapshot = RedstoneEngineering.SIGNAL_PROBE.get().engineeringSnapshot(
                    helper.getLevel(), helper.absolutePos(probePos), probeState, Direction.NORTH).orElse(null);

            if (analyzerState.getValue(SignalAnalyzerBlock.OUTPUT) != 15
                    || probeValue != 15
                    || probeSnapshot == null
                    || probeSnapshot.quality() != PortQuality.VALID
                    || Math.round(probeSnapshot.value()) != 15) {
                helper.fail("Analyzer INLINE output 15 must be observed by the adjacent Probe", probePos);
                return;
            }
            if (probeState.isSignalSource()) {
                helper.fail("Signal Probe must remain non-invasive and must not become a redstone source", probePos);
                return;
            }

            helper.setBlock(sourcePos, reference(Direction.SOUTH, 0));
            helper.getLevel().scheduleTick(
                    helper.absolutePos(analyzerPos), RedstoneEngineering.SIGNAL_ANALYZER.get(), 1);

            helper.runAfterDelay(4, () -> {
                BlockState zeroAnalyzer = helper.getBlockState(analyzerPos);
                BlockState zeroProbe = helper.getBlockState(probePos);
                int zeroProbeValue = RedstoneEngineering.SIGNAL_PROBE.get().sample(
                        helper.getLevel(), helper.absolutePos(probePos), zeroProbe);
                var zeroProbeSnapshot = RedstoneEngineering.SIGNAL_PROBE.get().engineeringSnapshot(
                        helper.getLevel(), helper.absolutePos(probePos), zeroProbe, Direction.NORTH).orElse(null);
                var zeroAnalyzerInput = RedstoneEngineering.SIGNAL_ANALYZER.get().engineeringSnapshot(
                        helper.getLevel(), helper.absolutePos(analyzerPos), zeroAnalyzer, Direction.NORTH).orElse(null);

                if (zeroAnalyzer.getValue(SignalAnalyzerBlock.OUTPUT) != 0
                        || zeroProbeValue != 0
                        || zeroProbeSnapshot == null
                        || zeroProbeSnapshot.quality() != PortQuality.VALID
                        || zeroAnalyzerInput == null
                        || zeroAnalyzerInput.quality() != PortQuality.VALID) {
                    helper.fail("Connected zero must propagate as VALID zero across Analyzer -> Probe", probePos);
                    return;
                }

                helper.setBlock(sourcePos, Blocks.AIR.defaultBlockState());
                helper.getLevel().scheduleTick(
                        helper.absolutePos(analyzerPos), RedstoneEngineering.SIGNAL_ANALYZER.get(), 1);
                helper.runAfterDelay(4, () -> {
                    BlockState disconnectedAnalyzer = helper.getBlockState(analyzerPos);
                    var disconnectedInput = RedstoneEngineering.SIGNAL_ANALYZER.get().engineeringSnapshot(
                            helper.getLevel(), helper.absolutePos(analyzerPos), disconnectedAnalyzer, Direction.NORTH).orElse(null);
                    if (disconnectedAnalyzer.getValue(SignalAnalyzerBlock.OUTPUT) != 0
                            || disconnectedInput == null
                            || disconnectedInput.quality() != PortQuality.NO_SIGNAL) {
                        helper.fail("Disconnect must clear Analyzer output and expose NO_SIGNAL at its input", analyzerPos);
                        return;
                    }
                    helper.succeed();
                });
            });
        });
    }

    private static BlockState analyzer(Direction facing, int mode) {
        return RedstoneEngineering.SIGNAL_ANALYZER.get().defaultBlockState()
                .setValue(SignalAnalyzerBlock.FACING, facing)
                .setValue(SignalAnalyzerBlock.MODE, mode);
    }

    private static BlockState probe(Direction target, int channel) {
        return RedstoneEngineering.SIGNAL_PROBE.get().defaultBlockState()
                .setValue(SignalProbeBlock.FACING, target)
                .setValue(SignalProbeBlock.CHANNEL, channel);
    }

    private static BlockState reference(Direction facing, int power) {
        return RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, facing)
                .setValue(RedstoneReferenceSourceBlock.POWER, power);
    }
}
