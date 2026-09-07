package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.CalibrationModuleBlock;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.EdgeDetectorBlock;
import dev.redstoneengineering.block.PrecisionFilterBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.SampleHoldBlock;
import dev.redstoneengineering.block.SignalConditionerBlock;
import dev.redstoneengineering.block.SignalProbeBlock;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.metrology.MeasurementSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Design-contract tests for the first ten registered RSE blocks.
 *
 * <p>Existing acceptance suites continue to prove their original transfer/capture behavior.
 * These tests target the deeper distinctions added by the fixed-content system-design audit:
 * zero-vs-no-signal, live Instrument Bus integrity, conditioning-vs-filtering, reference-based
 * calibration quality, and sampled/event chronology evidence.</p>
 */
public final class RseFirstTenDesignGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseFirstTenDesignGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 30)
    public static void probeDistinguishesOpenApertureFromRealZero(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(1, 1, 2);
        BlockPos probePos = new BlockPos(2, 1, 2);
        helper.setBlock(probePos, probe(Direction.WEST, 0));

        EngineeringPortProvider provider = RedstoneEngineering.SIGNAL_PROBE.get();
        BlockState probeState = helper.getBlockState(probePos);
        var open = provider.engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(probePos), probeState, Direction.WEST).orElse(null);
        if (open == null || open.quality() != PortQuality.NO_SIGNAL || Math.round(open.value()) != 0) {
            helper.fail("Open probe aperture must report NO_SIGNAL rather than confirmed zero", probePos);
            return;
        }

        helper.setBlock(sourcePos, reference(Direction.EAST, 0));
        var realZero = provider.engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(probePos), probeState, Direction.WEST).orElse(null);
        if (realZero == null || realZero.quality() != PortQuality.VALID || Math.round(realZero.value()) != 0) {
            helper.fail("A real zero-valued target must remain a valid engineering measurement", probePos);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void duplicateInstrumentChannelIsLiveTopologyErrorAcrossObservers(GameTestHelper helper) {
        BlockPos cablePos = new BlockPos(2, 1, 2);
        BlockPos scopePos = new BlockPos(2, 1, 1);
        BlockPos logicPos = new BlockPos(2, 1, 3);
        BlockPos westProbe = new BlockPos(1, 1, 2);
        BlockPos eastProbe = new BlockPos(3, 1, 2);

        helper.setBlock(new BlockPos(0, 1, 2), reference(Direction.EAST, 4));
        helper.setBlock(westProbe, probe(Direction.WEST, 0));
        helper.setBlock(cablePos, RedstoneEngineering.INSTRUMENT_CABLE.get().defaultBlockState());
        helper.setBlock(eastProbe, probe(Direction.EAST, 0));
        helper.setBlock(new BlockPos(4, 1, 2), reference(Direction.WEST, 12));
        helper.setBlock(scopePos, RedstoneEngineering.OSCILLOSCOPE.get().defaultBlockState());
        helper.setBlock(logicPos, RedstoneEngineering.LOGIC_ANALYZER.get().defaultBlockState());

        helper.runAfterDelay(4, () -> {
            BlockState cableState = helper.getBlockState(cablePos);
            var cable = RedstoneEngineering.INSTRUMENT_CABLE.get().engineeringSnapshot(
                    helper.getLevel(), helper.absolutePos(cablePos), cableState, Direction.NORTH).orElse(null);
            var scope = RedstoneEngineering.OSCILLOSCOPE.get().engineeringSnapshot(
                    helper.getLevel(), helper.absolutePos(scopePos), helper.getBlockState(scopePos), Direction.SOUTH).orElse(null);
            var logic = RedstoneEngineering.LOGIC_ANALYZER.get().engineeringSnapshot(
                    helper.getLevel(), helper.absolutePos(logicPos), helper.getBlockState(logicPos), Direction.NORTH).orElse(null);

            if (cable == null || cable.quality() != PortQuality.TOPOLOGY_ERROR) {
                helper.fail("Instrument Cable must surface duplicate channel ownership as TOPOLOGY_ERROR", cablePos);
                return;
            }
            if (scope == null || scope.quality() != PortQuality.TOPOLOGY_ERROR) {
                helper.fail("Oscilloscope live port health must expose duplicate A/B ownership", scopePos);
                return;
            }
            if (logic == null || logic.quality() != PortQuality.TOPOLOGY_ERROR) {
                helper.fail("Logic Analyzer live port health must expose duplicate channel ownership", logicPos);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 70)
    public static void conditionerLimitingAndFilterLagRemainDifferentEngineeringStates(GameTestHelper helper) {
        BlockPos conditionerPos = new BlockPos(1, 1, 1);
        BlockPos filterPos = new BlockPos(1, 1, 3);

        helper.setBlock(new BlockPos(0, 1, 1), reference(Direction.EAST, 8));
        helper.setBlock(conditionerPos, RedstoneEngineering.SIGNAL_CONDITIONER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(SignalConditionerBlock.MODE, 0)
                .setValue(SignalConditionerBlock.PARAM, 2));

        helper.setBlock(new BlockPos(0, 1, 3), reference(Direction.EAST, 15));
        helper.setBlock(filterPos, RedstoneEngineering.PRECISION_FILTER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(PrecisionFilterBlock.RATE, 1));

        helper.runAfterDelay(3, () -> {
            BlockState conditionerState = helper.getBlockState(conditionerPos);
            var conditioned = RedstoneEngineering.SIGNAL_CONDITIONER.get().engineeringSnapshot(
                    helper.getLevel(), helper.absolutePos(conditionerPos), conditionerState, Direction.EAST).orElse(null);
            if (conditioned == null || conditioned.quality() != PortQuality.SATURATED
                    || Math.round(conditioned.value()) != 15
                    || !SignalConditionerBlock.limitingActive(
                            helper.getLevel(), helper.absolutePos(conditionerPos), conditionerState)) {
                helper.fail("GAIN 2x on input 8 must expose bounded output 15 as active limiting", conditionerPos);
                return;
            }

            BlockState filterState = helper.getBlockState(filterPos);
            int lag = PrecisionFilterBlock.lag(helper.getLevel(), helper.absolutePos(filterPos), filterState);
            if (lag <= 0 || PrecisionFilterBlock.settled(helper.getLevel(), helper.absolutePos(filterPos), filterState)) {
                helper.fail("Rate-1 Precision Filter must expose expected dynamic lag while converging", filterPos);
                return;
            }

            helper.runAfterDelay(20, () -> {
                BlockState settled = helper.getBlockState(filterPos);
                if (!PrecisionFilterBlock.settled(helper.getLevel(), helper.absolutePos(filterPos), settled)
                        || PrecisionFilterBlock.lag(helper.getLevel(), helper.absolutePos(filterPos), settled) != 0) {
                    helper.fail("Precision Filter did not converge to a SETTLED zero-lag state", filterPos);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void calibrationOutputQualityComesFromReferenceMetrology(GameTestHelper helper) {
        BlockPos modulePos = new BlockPos(2, 1, 2);
        helper.setBlock(new BlockPos(1, 1, 2), reference(Direction.EAST, 7));
        helper.setBlock(new BlockPos(2, 1, 1), reference(Direction.SOUTH, 7));
        helper.setBlock(modulePos, RedstoneEngineering.CALIBRATION_MODULE.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(CalibrationModuleBlock.PROFILE, 0));

        BlockState state = helper.getBlockState(modulePos);
        var before = RedstoneEngineering.CALIBRATION_MODULE.get().engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(modulePos), state, Direction.EAST).orElse(null);
        if (before == null || before.quality() != PortQuality.NO_SIGNAL) {
            helper.fail("Calibration output must not claim validated quality before metrology samples exist", modulePos);
            return;
        }

        helper.runAfterDelay(5, () -> {
            BlockState sampledState = helper.getBlockState(modulePos);
            var after = RedstoneEngineering.CALIBRATION_MODULE.get().engineeringSnapshot(
                    helper.getLevel(), helper.absolutePos(modulePos), sampledState, Direction.EAST).orElse(null);
            MeasurementSnapshot measurement = CalibrationModuleBlock.measurement(
                    helper.getLevel(), helper.absolutePos(modulePos));
            if (after == null || after.quality() != PortQuality.VALID
                    || Math.round(after.value()) != 7 || measurement.sampleCount() <= 0) {
                helper.fail("Calibration output must become valid only with real OBSERVED-vs-REFERENCE evidence", modulePos);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void sampleAndEdgeModulesExposeRealEventChronologyWithoutObserverMutation(GameTestHelper helper) {
        BlockPos holdPos = new BlockPos(2, 1, 1);
        BlockPos triggerPos = new BlockPos(2, 1, 0);
        BlockPos edgePos = new BlockPos(2, 1, 3);
        BlockPos edgeInput = new BlockPos(1, 1, 3);

        helper.setBlock(new BlockPos(1, 1, 1), reference(Direction.EAST, 5));
        helper.setBlock(triggerPos, reference(Direction.SOUTH, 0));
        helper.setBlock(holdPos, RedstoneEngineering.SAMPLE_HOLD.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(SampleHoldBlock.TRIGGER_MODE, 0));

        helper.setBlock(edgeInput, reference(Direction.EAST, 0));
        helper.setBlock(edgePos, RedstoneEngineering.EDGE_DETECTOR.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(EdgeDetectorBlock.MODE, 0));

        // Read-only diagnostics before the first scheduled tick must not initialize the detector.
        if (EdgeDetectorBlock.edgeCount(helper.getLevel(), helper.absolutePos(edgePos)) != 0
                || EdgeDetectorBlock.lastEdgeAgeTicks(helper.getLevel(), helper.absolutePos(edgePos)) != -1
                || EdgeDetectorBlock.initialized(helper.getLevel(), helper.absolutePos(edgePos))) {
            helper.fail("Observer readback mutated an uninitialized Edge Detector", edgePos);
            return;
        }

        helper.runAfterDelay(3, () -> {
            if (SampleHoldBlock.captureCount(helper.getLevel(), helper.absolutePos(holdPos)) != 0
                    || EdgeDetectorBlock.edgeCount(helper.getLevel(), helper.absolutePos(edgePos)) != 0) {
                helper.fail("Initialization fabricated a sample or edge event", edgePos);
                return;
            }

            helper.setBlock(triggerPos, reference(Direction.SOUTH, 15));
            helper.setBlock(edgeInput, reference(Direction.EAST, 15));
            helper.getLevel().scheduleTick(helper.absolutePos(holdPos), RedstoneEngineering.SAMPLE_HOLD.get(), 1);
            helper.getLevel().scheduleTick(helper.absolutePos(edgePos), RedstoneEngineering.EDGE_DETECTOR.get(), 1);

            helper.runAfterDelay(4, () -> {
                int captures = SampleHoldBlock.captureCount(helper.getLevel(), helper.absolutePos(holdPos));
                int sampleAge = SampleHoldBlock.sampleAgeTicks(helper.getLevel(), helper.absolutePos(holdPos));
                int edges = EdgeDetectorBlock.edgeCount(helper.getLevel(), helper.absolutePos(edgePos));
                int edgeAge = EdgeDetectorBlock.lastEdgeAgeTicks(helper.getLevel(), helper.absolutePos(edgePos));
                if (captures != 1 || helper.getBlockState(holdPos).getValue(DirectionalSignalBlock.OUTPUT) != 5
                        || sampleAge < 0 || sampleAge > 6) {
                    helper.fail("Sample & Hold did not expose exactly one real capture with bounded age", holdPos);
                    return;
                }
                if (edges != 1 || edgeAge < 0 || edgeAge > 6) {
                    helper.fail("Edge Detector did not expose exactly one real edge with bounded age", edgePos);
                    return;
                }
                helper.succeed();
            });
        });
    }

    private static BlockState reference(Direction facing, int power) {
        return RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, facing)
                .setValue(RedstoneReferenceSourceBlock.POWER, power);
    }

    private static BlockState probe(Direction target, int channel) {
        return RedstoneEngineering.SIGNAL_PROBE.get().defaultBlockState()
                .setValue(SignalProbeBlock.FACING, target)
                .setValue(SignalProbeBlock.CHANNEL, channel);
    }
}
