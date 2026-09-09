package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.LogicAnalyzerBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.SignalAnalyzerBlock;
import dev.redstoneengineering.block.SignalProbeBlock;
import dev.redstoneengineering.blockentity.LogicAnalyzerBlockEntity;
import dev.redstoneengineering.blockentity.OscilloscopeBlockEntity;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.instrument.InstrumentNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Bounded system tests for registered blocks #1-#5:
 * Signal Analyzer -> Signal Probe -> Instrument Cable -> Oscilloscope + Logic Analyzer.
 *
 * <p>The observers form a measurement feedback path to the player, not a redstone control loop.
 * The tests deliberately exercise changing plant state and topology while proving the observer
 * branch cannot back-drive the measured redstone path.</p>
 */
public final class RseFiveBlockMeasurementFeedbackGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private static final BlockPos SOURCE = new BlockPos(0, 1, 2);
    private static final BlockPos ANALYZER = new BlockPos(1, 1, 2);
    private static final BlockPos PROBE = new BlockPos(2, 1, 2);
    private static final BlockPos CABLE = new BlockPos(3, 1, 2);
    private static final BlockPos SCOPE = new BlockPos(3, 1, 1);
    private static final BlockPos LOGIC = new BlockPos(3, 1, 3);

    private RseFiveBlockMeasurementFeedbackGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void fiveBlockMeasurementSystemTracksLowHighLowFeedback(GameTestHelper helper) {
        buildSystem(helper, 4);

        helper.runAfterDelay(6, () -> {
            if (!assertFrontEnd(helper, 4, "initial LOW")) return;
            if (!assertBus(helper, 4, PortQuality.VALID, "initial LOW")) return;
            if (!assertObservers(helper, 4, false, "initial LOW")) return;

            helper.setBlock(SOURCE, reference(12));
            helper.runAfterDelay(6, () -> {
                if (!assertFrontEnd(helper, 12, "HIGH transition")) return;
                if (!assertBus(helper, 12, PortQuality.VALID, "HIGH transition")) return;
                if (!assertObservers(helper, 12, true, "HIGH transition")) return;

                helper.setBlock(SOURCE, reference(0));
                helper.runAfterDelay(6, () -> {
                    if (!assertFrontEnd(helper, 0, "connected zero")) return;
                    if (!assertBus(helper, 0, PortQuality.VALID, "connected zero")) return;

                    var probeSnapshot = RedstoneEngineering.SIGNAL_PROBE.get().engineeringSnapshot(
                            helper.getLevel(), helper.absolutePos(PROBE), helper.getBlockState(PROBE), Direction.WEST).orElse(null);
                    if (probeSnapshot == null || probeSnapshot.quality() != PortQuality.VALID || Math.round(probeSnapshot.value()) != 0) {
                        helper.fail("Connected zero must remain a VALID measurement through the five-block system", PROBE);
                        return;
                    }

                    if (!(helper.getLevel().getBlockEntity(helper.absolutePos(SCOPE)) instanceof OscilloscopeBlockEntity scope)
                            || scope.current(0) != 0) {
                        helper.fail("Oscilloscope did not feed back the final zero-valued system state", SCOPE);
                        return;
                    }
                    if (!(helper.getLevel().getBlockEntity(helper.absolutePos(LOGIC)) instanceof LogicAnalyzerBlockEntity logic)
                            || logic.falling(0) < 1) {
                        helper.fail("Logic Analyzer did not record the HIGH-to-LOW transition", LOGIC);
                        return;
                    }
                    helper.succeed();
                });
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 70)
    public static void instrumentBusDisconnectRecoversWithoutBackDrivingFrontEnd(GameTestHelper helper) {
        buildSystem(helper, 12);

        helper.runAfterDelay(6, () -> {
            if (!assertFrontEnd(helper, 12, "pre-disconnect")) return;
            if (!assertBus(helper, 12, PortQuality.VALID, "pre-disconnect")) return;

            helper.setBlock(CABLE, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(5, () -> {
                if (!assertFrontEnd(helper, 12, "instrument bus disconnected")) return;

                var scopePort = RedstoneEngineering.OSCILLOSCOPE.get().engineeringSnapshot(
                        helper.getLevel(), helper.absolutePos(SCOPE), helper.getBlockState(SCOPE), Direction.SOUTH).orElse(null);
                var logicPort = RedstoneEngineering.LOGIC_ANALYZER.get().engineeringSnapshot(
                        helper.getLevel(), helper.absolutePos(LOGIC), helper.getBlockState(LOGIC), Direction.NORTH).orElse(null);
                if (scopePort == null || scopePort.quality() != PortQuality.NO_SIGNAL
                        || logicPort == null || logicPort.quality() != PortQuality.NO_SIGNAL) {
                    helper.fail("Removing the Instrument Cable must make both observers report NO_SIGNAL", CABLE);
                    return;
                }

                // The measurement branch is an observer only. Breaking it must not change the redstone path.
                if (helper.getBlockState(ANALYZER).getValue(SignalAnalyzerBlock.OUTPUT) != 12
                        || RedstoneEngineering.SIGNAL_PROBE.get().sample(
                                helper.getLevel(), helper.absolutePos(PROBE), helper.getBlockState(PROBE)) != 12) {
                    helper.fail("Instrument-bus failure back-drove the Analyzer/Probe front end", ANALYZER);
                    return;
                }

                // Place the cable last so it resolves all three planar attachments in one bounded refresh.
                helper.setBlock(CABLE, RedstoneEngineering.INSTRUMENT_CABLE.get().defaultBlockState());
                helper.runAfterDelay(6, () -> {
                    if (!assertFrontEnd(helper, 12, "instrument bus restored")) return;
                    if (!assertBus(helper, 12, PortQuality.VALID, "instrument bus restored")) return;
                    if (!assertObservers(helper, 12, true, "instrument bus restored")) return;
                    helper.succeed();
                });
            });
        });
    }

    private static void buildSystem(GameTestHelper helper, int sourcePower) {
        helper.setBlock(SOURCE, reference(sourcePower));
        helper.setBlock(ANALYZER, RedstoneEngineering.SIGNAL_ANALYZER.get().defaultBlockState()
                .setValue(SignalAnalyzerBlock.FACING, Direction.WEST)
                .setValue(SignalAnalyzerBlock.MODE, SignalAnalyzerBlock.INLINE));
        helper.setBlock(PROBE, RedstoneEngineering.SIGNAL_PROBE.get().defaultBlockState()
                .setValue(SignalProbeBlock.FACING, Direction.WEST)
                .setValue(SignalProbeBlock.CHANNEL, 0));
        helper.setBlock(SCOPE, RedstoneEngineering.OSCILLOSCOPE.get().defaultBlockState());
        helper.setBlock(LOGIC, RedstoneEngineering.LOGIC_ANALYZER.get().defaultBlockState()
                .setValue(LogicAnalyzerBlock.THRESHOLD, 8));
        helper.setBlock(CABLE, RedstoneEngineering.INSTRUMENT_CABLE.get().defaultBlockState());
    }

    private static BlockState reference(int power) {
        return RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.EAST)
                .setValue(RedstoneReferenceSourceBlock.POWER, power);
    }

    private static boolean assertFrontEnd(GameTestHelper helper, int expected, String phase) {
        BlockState analyzer = helper.getBlockState(ANALYZER);
        BlockState probe = helper.getBlockState(PROBE);
        int probeValue = RedstoneEngineering.SIGNAL_PROBE.get().sample(
                helper.getLevel(), helper.absolutePos(PROBE), probe);
        if (analyzer.getValue(SignalAnalyzerBlock.OUTPUT) != expected || probeValue != expected) {
            helper.fail("Front-end mismatch during " + phase + ": expected " + expected, ANALYZER);
            return false;
        }
        if (probe.isSignalSource()) {
            helper.fail("Signal Probe became invasive during " + phase, PROBE);
            return false;
        }
        return true;
    }

    private static boolean assertBus(GameTestHelper helper, int expected, PortQuality expectedQuality, String phase) {
        InstrumentNetwork.ProbeSnapshot fromScope = InstrumentNetwork.scan(helper.getLevel(), helper.absolutePos(SCOPE));
        InstrumentNetwork.ProbeSnapshot fromLogic = InstrumentNetwork.scan(helper.getLevel(), helper.absolutePos(LOGIC));
        if (!fromScope.bounded() || !fromLogic.bounded()
                || fromScope.cableNodes() != 1 || fromLogic.cableNodes() != 1
                || fromScope.probeNodes() != 1 || fromLogic.probeNodes() != 1
                || fromScope.valueOr(0, -1) != expected || fromLogic.valueOr(0, -1) != expected
                || fromScope.qualityForMask(0b0001) != expectedQuality
                || fromLogic.qualityForMask(0b0001) != expectedQuality) {
            helper.fail("Instrument bus did not report expected channel-A feedback during " + phase, CABLE);
            return false;
        }
        return true;
    }

    private static boolean assertObservers(GameTestHelper helper, int expectedAnalog, boolean expectDigitalHigh, String phase) {
        var scopePort = RedstoneEngineering.OSCILLOSCOPE.get().engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(SCOPE), helper.getBlockState(SCOPE), Direction.SOUTH).orElse(null);
        var logicPort = RedstoneEngineering.LOGIC_ANALYZER.get().engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(LOGIC), helper.getBlockState(LOGIC), Direction.NORTH).orElse(null);
        if (scopePort == null || scopePort.quality() != PortQuality.VALID
                || logicPort == null || logicPort.quality() != PortQuality.VALID) {
            helper.fail("Observers lost a healthy instrument bus during " + phase, CABLE);
            return false;
        }

        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(SCOPE)) instanceof OscilloscopeBlockEntity scope)
                || scope.sampleCount() == 0 || scope.current(0) != expectedAnalog) {
            helper.fail("Oscilloscope capture did not match " + phase, SCOPE);
            return false;
        }
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(LOGIC)) instanceof LogicAnalyzerBlockEntity logic)
                || logic.sampleCount() == 0 || logic.validSamples(0) == 0) {
            helper.fail("Logic Analyzer did not capture valid channel-A data during " + phase, LOGIC);
            return false;
        }
        if (expectDigitalHigh && logic.highSamples(0) == 0) {
            helper.fail("Logic Analyzer never observed channel A above threshold during " + phase, LOGIC);
            return false;
        }
        return true;
    }
}
