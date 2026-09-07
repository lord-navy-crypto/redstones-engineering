package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.AmethystFrequencyFilterBlock;
import dev.redstoneengineering.block.AmethystResonanceDustBlock;
import dev.redstoneengineering.block.AmethystTunedResonatorBlock;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.block.LapisLowPassFilterBlock;
import dev.redstoneengineering.block.LapisNoiseSourceBlock;
import dev.redstoneengineering.block.QuartzClockDividerBlock;
import dev.redstoneengineering.block.QuartzLabOscillatorBlock;
import dev.redstoneengineering.block.QuartzPhaseDelayBlock;
import dev.redstoneengineering.block.QuartzStabilityMonitorBlock;
import dev.redstoneengineering.block.QuartzTimingLineBlock;
import dev.redstoneengineering.block.TemperatureSensorBlock;
import dev.redstoneengineering.block.ThermalMassBlock;
import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Design + bug-regression contracts for registered blocks 31-40. */
public final class RseFourthTenDesignBugGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseFourthTenDesignBugGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void noiseSourceZeroSampleInspectionIsObserverNeutral(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(2, 1, 2);
        helper.setBlock(sourcePos, RedstoneEngineering.LAPIS_NOISE_SOURCE.get().defaultBlockState()
                .setValue(LapisNoiseSourceBlock.BASELINE, 10)
                .setValue(LapisNoiseSourceBlock.NOISE, 0));
        BlockPos world = helper.absolutePos(sourcePos);
        BlockState state = helper.getBlockState(sourcePos);

        LapisNoiseSourceBlock.setSample(helper.getLevel(), world, 0);
        var snapshot = RedstoneEngineering.LAPIS_NOISE_SOURCE.get().engineeringSnapshot(
                helper.getLevel(), world, state, Direction.NORTH).orElse(null);
        if (!LapisNoiseSourceBlock.sampleInitialized(helper.getLevel(), world)
                || LapisNoiseSourceBlock.currentValue(helper.getLevel(), world, state) != 0
                || snapshot == null
                || snapshot.value() != 0.0
                || snapshot.quality() != PortQuality.VALID) {
            helper.fail("Legitimate Lapis noise sample 0 was treated as uninitialized or invalid", sourcePos);
            return;
        }
        if (LapisNoiseSourceBlock.currentValue(helper.getLevel(), world, state) != 0) {
            helper.fail("Repeated observation rewrote legitimate zero noise sample back to baseline", sourcePos);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void lowPassInspectionDoesNotCreateRuntimeState(GameTestHelper helper) {
        BlockPos filterPos = new BlockPos(2, 1, 2);
        helper.setBlock(filterPos, RedstoneEngineering.LAPIS_LOW_PASS_FILTER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        BlockPos world = helper.absolutePos(filterPos);
        BlockState state = helper.getBlockState(filterPos);

        if (LapisLowPassFilterBlock.runtimePresent(helper.getLevel(), world)) {
            helper.fail("Low-pass runtime existed before its first server physics tick", filterPos);
            return;
        }
        var snapshot = RedstoneEngineering.LAPIS_LOW_PASS_FILTER.get().engineeringSnapshot(
                helper.getLevel(), world, state, Direction.EAST).orElse(null);
        if (snapshot == null || LapisLowPassFilterBlock.runtimePresent(helper.getLevel(), world)) {
            helper.fail("Reading low-pass output created transient filter state", filterPos);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void temperatureSensorSeparatesAmbientCoverageFromDirectThermalBody(GameTestHelper helper) {
        BlockPos sensorPos = new BlockPos(2, 1, 2);
        BlockPos bodyPos = new BlockPos(2, 1, 1);
        helper.setBlock(sensorPos, RedstoneEngineering.TEMPERATURE_SENSOR.get().defaultBlockState());

        helper.runAfterDelay(2, () -> {
            BlockPos sensorWorld = helper.absolutePos(sensorPos);
            TemperatureSensorBlock.ThermalObservation observation = TemperatureSensorBlock.observe(helper.getLevel(), sensorWorld);
            var ambient = RedstoneEngineering.TEMPERATURE_SENSOR.get().engineeringSnapshot(
                    helper.getLevel(), sensorWorld, helper.getBlockState(sensorPos), Direction.NORTH).orElse(null);
            if (!observation.complete() || observation.thermalBodies() != 0
                    || ambient == null || ambient.quality() != PortQuality.VALID) {
                helper.fail("Complete local ambient coverage was not exposed as valid thermal observation", sensorPos);
                return;
            }

            helper.setBlock(bodyPos, RedstoneEngineering.THERMAL_MASS.get().defaultBlockState()
                    .setValue(ThermalMassBlock.TEMPERATURE, 80)
                    .setValue(ThermalMassBlock.HEAT_CAPACITY, 4));
            helper.runAfterDelay(2, () -> {
                var direct = RedstoneEngineering.TEMPERATURE_SENSOR.get().engineeringSnapshot(
                        helper.getLevel(), sensorWorld, helper.getBlockState(sensorPos), Direction.NORTH).orElse(null);
                if (direct == null || direct.quality() != PortQuality.VALID || Math.round(direct.value()) != 80) {
                    helper.fail("Temperature sensor did not preserve direct adjacent Thermal Mass measurement", bodyPos);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void dividerHighAttachDoesNotFabricateRisingEdge(GameTestHelper helper) {
        BlockPos linePos = new BlockPos(1, 1, 2);
        BlockPos dividerPos = new BlockPos(2, 1, 2);
        helper.setBlock(linePos, RedstoneEngineering.QUARTZ_TIMING_LINE.get().defaultBlockState());
        helper.setBlock(dividerPos, RedstoneEngineering.QUARTZ_CLOCK_DIVIDER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        BlockPos lineWorld = helper.absolutePos(linePos);
        BlockPos dividerWorld = helper.absolutePos(dividerPos);

        helper.runAfterDelay(2, () -> {
            QuartzTimingLineBlock.setTiming(helper.getLevel(), lineWorld, true, 8, true, 1);
            helper.runAfterDelay(2, () -> {
                if (!QuartzClockDividerBlock.initialized(helper.getLevel(), dividerWorld)
                        || QuartzClockDividerBlock.countedEdges(helper.getLevel(), dividerWorld) != 0) {
                    helper.fail("Divider fabricated a rising edge merely because first valid sample was HIGH", dividerPos);
                    return;
                }
                QuartzTimingLineBlock.setTiming(helper.getLevel(), lineWorld, false, 8, true, 1);
                helper.runAfterDelay(2, () -> {
                    QuartzTimingLineBlock.setTiming(helper.getLevel(), lineWorld, true, 8, true, 1);
                    helper.runAfterDelay(2, () -> {
                        if (QuartzClockDividerBlock.countedEdges(helper.getLevel(), dividerWorld) != 1) {
                            helper.fail("Divider failed to count exactly one real LOW-to-HIGH transition", dividerPos);
                            return;
                        }
                        helper.succeed();
                    });
                });
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void phaseDelayRequiresRealPostInitializationRisingEdge(GameTestHelper helper) {
        BlockPos linePos = new BlockPos(1, 1, 2);
        BlockPos delayPos = new BlockPos(2, 1, 2);
        helper.setBlock(linePos, RedstoneEngineering.QUARTZ_TIMING_LINE.get().defaultBlockState());
        helper.setBlock(delayPos, RedstoneEngineering.QUARTZ_PHASE_DELAY.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                .setValue(QuartzPhaseDelayBlock.DELAY, 3));
        BlockPos lineWorld = helper.absolutePos(linePos);
        BlockPos delayWorld = helper.absolutePos(delayPos);

        helper.runAfterDelay(2, () -> {
            QuartzTimingLineBlock.setTiming(helper.getLevel(), lineWorld, true, 8, true, 1);
            helper.runAfterDelay(2, () -> {
                if (!QuartzPhaseDelayBlock.initialized(helper.getLevel(), delayWorld)
                        || QuartzPhaseDelayBlock.pendingTicks(helper.getLevel(), delayWorld) != 0) {
                    helper.fail("Phase delay scheduled a pulse from first valid HIGH observation", delayPos);
                    return;
                }
                QuartzTimingLineBlock.setTiming(helper.getLevel(), lineWorld, false, 8, true, 1);
                helper.runAfterDelay(2, () -> {
                    QuartzTimingLineBlock.setTiming(helper.getLevel(), lineWorld, true, 8, true, 1);
                    helper.runAfterDelay(2, () -> {
                        if (QuartzPhaseDelayBlock.pendingTicks(helper.getLevel(), delayWorld) <= 0) {
                            helper.fail("Phase delay did not schedule latency after a real rising edge", delayPos);
                            return;
                        }
                        helper.succeed();
                    });
                });
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 90)
    public static void stabilityMonitorNeedsTwoRealEdgesAndInspectionIsNeutral(GameTestHelper helper) {
        BlockPos linePos = new BlockPos(1, 1, 2);
        BlockPos monitorPos = new BlockPos(2, 1, 2);
        helper.setBlock(linePos, RedstoneEngineering.QUARTZ_TIMING_LINE.get().defaultBlockState());
        helper.setBlock(monitorPos, RedstoneEngineering.QUARTZ_STABILITY_MONITOR.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        BlockPos lineWorld = helper.absolutePos(linePos);
        BlockPos monitorWorld = helper.absolutePos(monitorPos);
        QuartzTimingLineBlock.setTiming(helper.getLevel(), lineWorld, true, 8, true, 1);

        var before = QuartzStabilityMonitorBlock.measurement(helper.getLevel(), monitorWorld);
        RedstoneEngineering.QUARTZ_STABILITY_MONITOR.get().engineeringSnapshot(
                helper.getLevel(), monitorWorld, helper.getBlockState(monitorPos), Direction.WEST);
        var afterInspection = QuartzStabilityMonitorBlock.measurement(helper.getLevel(), monitorWorld);
        if (before.initialized() || afterInspection.initialized() || afterInspection.period() != 0) {
            helper.fail("Stability-monitor inspection created measurement runtime", monitorPos);
            return;
        }

        helper.runAfterDelay(2, () -> {
            if (QuartzStabilityMonitorBlock.measurement(helper.getLevel(), monitorWorld).period() != 0) {
                helper.fail("First observed HIGH was incorrectly published as a complete period", monitorPos);
                return;
            }
            QuartzTimingLineBlock.setTiming(helper.getLevel(), lineWorld, false, 8, true, 1);
            helper.runAfterDelay(2, () -> {
                QuartzTimingLineBlock.setTiming(helper.getLevel(), lineWorld, true, 8, true, 1);
                helper.runAfterDelay(2, () -> {
                    var reference = QuartzStabilityMonitorBlock.measurement(helper.getLevel(), monitorWorld);
                    if (!reference.referenceEdgeSeen() || reference.period() != 0) {
                        helper.fail("First real rising edge must establish reference only", monitorPos);
                        return;
                    }
                    QuartzTimingLineBlock.setTiming(helper.getLevel(), lineWorld, false, 8, true, 1);
                    helper.runAfterDelay(3, () -> {
                        QuartzTimingLineBlock.setTiming(helper.getLevel(), lineWorld, true, 8, true, 1);
                        helper.runAfterDelay(2, () -> {
                            var measured = QuartzStabilityMonitorBlock.measurement(helper.getLevel(), monitorWorld);
                            if (!measured.currentMeasurement() || measured.period() <= 0) {
                                helper.fail("Second real rising edge did not produce a complete timing measurement", monitorPos);
                                return;
                            }
                            helper.succeed();
                        });
                    });
                });
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void labOscillatorPublishesRealizedJitterEvidence(GameTestHelper helper) {
        BlockPos oscillatorPos = new BlockPos(2, 1, 2);
        helper.setBlock(oscillatorPos, RedstoneEngineering.QUARTZ_LAB_OSCILLATOR.get().defaultBlockState()
                .setValue(QuartzLabOscillatorBlock.PERIOD_INDEX, 2)
                .setValue(QuartzLabOscillatorBlock.JITTER, 3));
        BlockPos world = helper.absolutePos(oscillatorPos);
        helper.runAfterDelay(3, () -> {
            BlockState state = helper.getBlockState(oscillatorPos);
            QuartzLabOscillatorBlock.TimingEvidence evidence = QuartzLabOscillatorBlock.timingEvidence(helper.getLevel(), world, state);
            if (!evidence.available()
                    || evidence.nominalPeriod() != 8
                    || evidence.lastHalfInterval() < 1
                    || evidence.lastHalfInterval() > 7
                    || Math.abs(evidence.lastJitterOffset()) > 3) {
                helper.fail("Lab oscillator did not expose bounded realized half-period/jitter evidence", oscillatorPos);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void amethystFilterAndTunedResonatorKeepDistinctResponses(GameTestHelper helper) {
        BlockPos filterInput = new BlockPos(1, 1, 1);
        BlockPos filterPos = new BlockPos(2, 1, 1);
        BlockPos tunedInput = new BlockPos(1, 1, 3);
        BlockPos tunedPos = new BlockPos(2, 1, 3);
        helper.setBlock(filterInput, RedstoneEngineering.AMETHYST_RESONANCE_DUST.get().defaultBlockState());
        helper.setBlock(filterPos, RedstoneEngineering.AMETHYST_FREQUENCY_FILTER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                .setValue(AmethystFrequencyFilterBlock.TARGET, 8));
        helper.setBlock(tunedInput, RedstoneEngineering.AMETHYST_RESONANCE_DUST.get().defaultBlockState());
        helper.setBlock(tunedPos, RedstoneEngineering.AMETHYST_TUNED_RESONATOR.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                .setValue(AmethystTunedResonatorBlock.NATURAL, 8)
                .setValue(AmethystTunedResonatorBlock.Q_INDEX, 2));

        BlockPos filterInputWorld = helper.absolutePos(filterInput);
        BlockPos tunedInputWorld = helper.absolutePos(tunedInput);
        AmethystResonanceDustBlock.setResonance(helper.getLevel(), filterInputWorld, 7, 10);
        AmethystResonanceDustBlock.setResonance(helper.getLevel(), tunedInputWorld, 7, 10);

        var filtered = AmethystFrequencyFilterBlock.evidence(
                helper.getLevel(), helper.absolutePos(filterPos), helper.getBlockState(filterPos));
        var tuned = AmethystTunedResonatorBlock.response(
                helper.getLevel(), helper.absolutePos(tunedPos), helper.getBlockState(tunedPos));
        if (filtered.matched() || filtered.expectedOutputAmplitude() != 0) {
            helper.fail("Exact frequency filter passed an off-target f=7 signal while configured for f=8", filterPos);
            return;
        }
        if (!tuned.responding() || tuned.frequencyError() != 1 || tuned.outputAmplitude() <= 0) {
            helper.fail("Tuned resonator failed to respond inside its finite Q-defined bandwidth", tunedPos);
            return;
        }

        AmethystResonanceDustBlock.setResonance(helper.getLevel(), filterInputWorld, 8, 14);
        AmethystResonanceDustBlock.setResonance(helper.getLevel(), tunedInputWorld, 8, 14);
        filtered = AmethystFrequencyFilterBlock.evidence(
                helper.getLevel(), helper.absolutePos(filterPos), helper.getBlockState(filterPos));
        tuned = AmethystTunedResonatorBlock.response(
                helper.getLevel(), helper.absolutePos(tunedPos), helper.getBlockState(tunedPos));
        if (!filtered.matched() || filtered.expectedOutputAmplitude() != 13) {
            helper.fail("Exact filter did not apply one-amplitude insertion loss on target frequency", filterPos);
            return;
        }
        if (!tuned.saturated() || tuned.outputAmplitude() != 15) {
            helper.fail("Tuned resonator failed to expose resonance-gain saturation at exact f0", tunedPos);
            return;
        }
        helper.succeed();
    }
}
