package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.CalibrationModuleBlock;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.LogicAnalyzerBlock;
import dev.redstoneengineering.block.PidControllerBlock;
import dev.redstoneengineering.block.PwmControllerBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.SampleHoldBlock;
import dev.redstoneengineering.block.SignalAnalyzerBlock;
import dev.redstoneengineering.block.SignalProbeBlock;
import dev.redstoneengineering.blockentity.LogicAnalyzerBlockEntity;
import dev.redstoneengineering.blockentity.OscilloscopeBlockEntity;
import dev.redstoneengineering.metrology.MeasurementSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * First-eight acceptance campaign for the UI-first RSE era.
 *
 * <p>The Analog Signal Conditioner already has detailed in-world functional and UI-authority
 * coverage in {@link RseFunctionalCorrectnessGameTests} and {@link RseEngineeringUiGameTests}.
 * This class closes the highest-value gaps for the remaining first-eight devices with actual
 * world placement, scheduled ticks, instrument-network sampling, runtime state, and safety
 * precedence. Client rendering remains a manual acceptance gate.</p>
 */
public final class RseFirstEightAcceptanceGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseFirstEightAcceptanceGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void pidManualModeAndInhibitPreserveSafetyPrecedence(GameTestHelper helper) {
        BlockPos pidPos = new BlockPos(2, 2, 2);

        helper.setBlock(pidPos, RedstoneEngineering.PID_CONTROLLER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(PidControllerBlock.TUNING, 2));

        // UP > 0 requests MANUAL; DOWN supplies the manual command. Both are 15 here.
        helper.setBlock(pidPos.above(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(pidPos.below(), Blocks.REDSTONE_BLOCK.defaultBlockState());

        helper.runAfterDelay(7, () -> {
            assertDirectionalOutput(helper, pidPos, 15,
                    "PID MANUAL mode did not reproduce the DOWN manual-output command");

            // Facing EAST => RIGHT/INHIBIT is SOUTH. Safety inhibit must dominate manual output.
            helper.setBlock(pidPos.south(), Blocks.REDSTONE_BLOCK.defaultBlockState());
            helper.runAfterDelay(5, () -> {
                assertDirectionalOutput(helper, pidPos, 0,
                        "PID INHIBIT failed to force output low in MANUAL mode");
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void analyzerInlinePassThroughRemainsRawDespiteDisplayCalibration(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(1, 1, 2);
        BlockPos analyzerPos = new BlockPos(2, 1, 2);

        helper.setBlock(sourcePos, reference(Direction.EAST, 9));
        helper.setBlock(analyzerPos, RedstoneEngineering.SIGNAL_ANALYZER.get().defaultBlockState()
                .setValue(SignalAnalyzerBlock.FACING, Direction.WEST)
                .setValue(SignalAnalyzerBlock.MODE, 1)
                .setValue(SignalAnalyzerBlock.CALIBRATION, 4)); // display offset +2

        helper.runAfterDelay(8, () -> {
            int rawOutput = helper.getBlockState(analyzerPos).getValue(SignalAnalyzerBlock.OUTPUT);
            if (rawOutput != 9) {
                helper.fail("INLINE analyzer must pass raw 9 even when display calibration is +2; actual=" + rawOutput,
                        analyzerPos);
                return;
            }

            // TAP mode must become non-invasive and stop sourcing the inline output.
            helper.setBlock(analyzerPos, helper.getBlockState(analyzerPos).setValue(SignalAnalyzerBlock.MODE, 0));
            helper.getLevel().scheduleTick(helper.absolutePos(analyzerPos), RedstoneEngineering.SIGNAL_ANALYZER.get(), 1);
            helper.runAfterDelay(4, () -> {
                int tapOutput = helper.getBlockState(analyzerPos).getValue(SignalAnalyzerBlock.OUTPUT);
                if (tapOutput != 0) {
                    helper.fail("TAP analyzer retained an invasive inline output; actual=" + tapOutput, analyzerPos);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 90)
    public static void oscilloscopeSamplesTwoProbeChannelsAndBoundsCapture(GameTestHelper helper) {
        BlockPos scopePos = new BlockPos(2, 1, 2);

        // Channel A: source x=0 -> probe x=1 -> scope x=2.
        helper.setBlock(new BlockPos(0, 1, 2), reference(Direction.EAST, 4));
        helper.setBlock(new BlockPos(1, 1, 2), probe(Direction.WEST, 0));

        // Channel B: source x=4 <- probe x=3 <- scope x=2.
        helper.setBlock(new BlockPos(4, 1, 2), reference(Direction.WEST, 12));
        helper.setBlock(new BlockPos(3, 1, 2), probe(Direction.EAST, 1));

        helper.setBlock(scopePos, RedstoneEngineering.OSCILLOSCOPE.get().defaultBlockState());

        helper.runAfterDelay(70, () -> {
            if (!(helper.getLevel().getBlockEntity(helper.absolutePos(scopePos)) instanceof OscilloscopeBlockEntity scope)) {
                helper.fail("Oscilloscope block entity was not created", scopePos);
                return;
            }
            if (scope.sampleCount() != 32) {
                helper.fail("Oscilloscope capture must remain bounded at 32 samples; actual=" + scope.sampleCount(), scopePos);
                return;
            }
            if (scope.current(0) != 4 || scope.current(1) != 12) {
                helper.fail("Oscilloscope did not preserve independent A=4 and B=12 probe readbacks", scopePos);
                return;
            }
            if (scope.coveragePercent(0) != 100 || scope.coveragePercent(1) != 100) {
                helper.fail("Oscilloscope valid direct probes must report 100% capture coverage", scopePos);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void logicAnalyzerThresholdPathCountsRealFallingAndRisingEdges(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(0, 1, 2);
        BlockPos logicPos = new BlockPos(2, 1, 2);

        helper.setBlock(sourcePos, reference(Direction.EAST, 12));
        helper.setBlock(new BlockPos(1, 1, 2), probe(Direction.WEST, 0));
        helper.setBlock(logicPos, RedstoneEngineering.LOGIC_ANALYZER.get().defaultBlockState()
                .setValue(LogicAnalyzerBlock.THRESHOLD, 8));

        helper.runAfterDelay(6, () -> {
            if (!(helper.getLevel().getBlockEntity(helper.absolutePos(logicPos)) instanceof LogicAnalyzerBlockEntity logic)) {
                helper.fail("Logic Analyzer block entity was not created", logicPos);
                return;
            }
            if (logic.validSamples(0) == 0 || logic.dutyPercent(0) != 100) {
                helper.fail("Logic Analyzer did not classify steady 12 >= threshold 8 as HIGH", logicPos);
                return;
            }

            helper.setBlock(sourcePos, reference(Direction.EAST, 0));
            helper.runAfterDelay(4, () -> {
                if (logic.falling(0) < 1) {
                    helper.fail("Logic Analyzer missed the real HIGH->LOW transition", logicPos);
                    return;
                }

                helper.setBlock(sourcePos, reference(Direction.EAST, 12));
                helper.runAfterDelay(4, () -> {
                    if (logic.rising(0) < 1) {
                        helper.fail("Logic Analyzer missed the real LOW->HIGH transition", logicPos);
                        return;
                    }
                    if (logic.edgeCount(0) < 2) {
                        helper.fail("Logic Analyzer edge accounting lost a transition", logicPos);
                        return;
                    }
                    helper.succeed();
                });
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void calibrationModuleTransformsObservedSignalAndKeepsReferenceAsMetrologyInput(GameTestHelper helper) {
        BlockPos observedPos = new BlockPos(1, 1, 2);
        BlockPos referencePos = new BlockPos(2, 1, 1);
        BlockPos modulePos = new BlockPos(2, 1, 2);

        helper.setBlock(observedPos, reference(Direction.EAST, 5));
        helper.setBlock(referencePos, reference(Direction.SOUTH, 10));
        helper.setBlock(modulePos, RedstoneEngineering.CALIBRATION_MODULE.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(CalibrationModuleBlock.PROFILE, 1));

        helper.runAfterDelay(8, () -> {
            assertDirectionalOutput(helper, modulePos, 11,
                    "LOW calibration profile must map observed 5 in 0..7 to rounded output 11 in 0..15");

            MeasurementSnapshot snapshot = CalibrationModuleBlock.measurement(
                    helper.getLevel(), helper.absolutePos(modulePos));
            if (snapshot.sampleCount() <= 0
                    || Math.abs(snapshot.reading() - 11.0) > 1.0e-9
                    || Math.abs(snapshot.bias() - 1.0) > 1.0e-9) {
                helper.fail("Calibration metrology must compare corrected reading 11 against independent reference 10",
                        modulePos);
                return;
            }

            BlockState inverted = helper.getBlockState(modulePos).setValue(CalibrationModuleBlock.PROFILE, 4);
            helper.setBlock(modulePos, inverted);
            helper.getLevel().scheduleTick(helper.absolutePos(modulePos), RedstoneEngineering.CALIBRATION_MODULE.get(), 1);
            helper.runAfterDelay(4, () -> {
                assertDirectionalOutput(helper, modulePos, 10,
                        "INVERT calibration profile must produce 15 - observed 5 = 10");
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 90)
    public static void sampleHoldCapturesOnlyOnConfiguredEdgeAndResetWins(GameTestHelper helper) {
        BlockPos valuePos = new BlockPos(1, 1, 2);
        BlockPos triggerPos = new BlockPos(2, 1, 1);
        BlockPos resetPos = new BlockPos(2, 1, 3);
        BlockPos holdPos = new BlockPos(2, 1, 2);

        helper.setBlock(valuePos, reference(Direction.EAST, 5));
        helper.setBlock(triggerPos, reference(Direction.SOUTH, 0));
        helper.setBlock(resetPos, reference(Direction.NORTH, 0));
        helper.setBlock(holdPos, RedstoneEngineering.SAMPLE_HOLD.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(SampleHoldBlock.TRIGGER_MODE, 0)); // rising edge

        helper.runAfterDelay(4, () -> {
            assertDirectionalOutput(helper, holdPos, 0, "Sample & Hold changed before a trigger edge");

            helper.setBlock(triggerPos, reference(Direction.SOUTH, 15));
            schedule(helper, holdPos, RedstoneEngineering.SAMPLE_HOLD.get());
            helper.runAfterDelay(3, () -> {
                assertDirectionalOutput(helper, holdPos, 5, "Rising trigger failed to capture VALUE=5");

                helper.setBlock(valuePos, reference(Direction.EAST, 11));
                schedule(helper, holdPos, RedstoneEngineering.SAMPLE_HOLD.get());
                helper.runAfterDelay(3, () -> {
                    assertDirectionalOutput(helper, holdPos, 5,
                            "Held output changed while trigger remained continuously HIGH");

                    helper.setBlock(triggerPos, reference(Direction.SOUTH, 0));
                    schedule(helper, holdPos, RedstoneEngineering.SAMPLE_HOLD.get());
                    helper.runAfterDelay(3, () -> {
                        assertDirectionalOutput(helper, holdPos, 5,
                                "RISING mode incorrectly sampled the falling edge");

                        helper.setBlock(triggerPos, reference(Direction.SOUTH, 15));
                        schedule(helper, holdPos, RedstoneEngineering.SAMPLE_HOLD.get());
                        helper.runAfterDelay(3, () -> {
                            assertDirectionalOutput(helper, holdPos, 11,
                                    "Second rising edge failed to capture updated VALUE=11");

                            helper.setBlock(resetPos, reference(Direction.NORTH, 15));
                            schedule(helper, holdPos, RedstoneEngineering.SAMPLE_HOLD.get());
                            helper.runAfterDelay(3, () -> {
                                assertDirectionalOutput(helper, holdPos, 0,
                                        "RESET input failed to clear held output");
                                helper.succeed();
                            });
                        });
                    });
                });
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 70)
    public static void pwmEndpointsInvertAndInhibitHaveDeterministicPrecedence(GameTestHelper helper) {
        BlockPos commandPos = new BlockPos(1, 1, 2);
        BlockPos inhibitPos = new BlockPos(2, 1, 1);
        BlockPos pwmPos = new BlockPos(2, 1, 2);

        helper.setBlock(commandPos, reference(Direction.EAST, 15));
        helper.setBlock(inhibitPos, reference(Direction.SOUTH, 0));
        helper.setBlock(pwmPos, RedstoneEngineering.PWM_CONTROLLER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(PwmControllerBlock.PERIOD_MODE, 2)
                .setValue(PwmControllerBlock.INVERT, false));

        helper.runAfterDelay(5, () -> {
            assertDirectionalOutput(helper, pwmPos, 15, "PWM 15/15 command must be continuously HIGH");

            helper.setBlock(pwmPos, helper.getBlockState(pwmPos).setValue(PwmControllerBlock.INVERT, true));
            schedule(helper, pwmPos, RedstoneEngineering.PWM_CONTROLLER.get());
            helper.runAfterDelay(3, () -> {
                assertDirectionalOutput(helper, pwmPos, 0, "PWM invert failed to flip the 15/15 endpoint LOW");

                // With command=0 and INVERT=true the pre-safety result would be HIGH.
                // INHIBIT must still win and force OFF.
                helper.setBlock(commandPos, reference(Direction.EAST, 0));
                helper.setBlock(inhibitPos, reference(Direction.SOUTH, 15));
                schedule(helper, pwmPos, RedstoneEngineering.PWM_CONTROLLER.get());
                helper.runAfterDelay(3, () -> {
                    assertDirectionalOutput(helper, pwmPos, 0,
                            "PWM INHIBIT failed to override inversion and force a safe LOW output");

                    helper.setBlock(inhibitPos, reference(Direction.SOUTH, 0));
                    schedule(helper, pwmPos, RedstoneEngineering.PWM_CONTROLLER.get());
                    helper.runAfterDelay(3, () -> {
                        assertDirectionalOutput(helper, pwmPos, 15,
                                "PWM invert path should return HIGH for command=0 after inhibit clears");
                        helper.succeed();
                    });
                });
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

    private static void schedule(GameTestHelper helper, BlockPos relativePos, net.minecraft.world.level.block.Block block) {
        helper.getLevel().scheduleTick(helper.absolutePos(relativePos), block, 1);
    }

    private static void assertDirectionalOutput(GameTestHelper helper, BlockPos pos, int expected, String message) {
        int actual = helper.getBlockState(pos).getValue(DirectionalSignalBlock.OUTPUT);
        if (actual != expected) {
            helper.fail(message + " (expected=" + expected + ", actual=" + actual + ")", pos);
        }
    }
}
