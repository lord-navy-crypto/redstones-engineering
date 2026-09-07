package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.AmethystResonatorBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.LapisPrecisionSourceBlock;
import dev.redstoneengineering.block.LapisSignalLineBlock;
import dev.redstoneengineering.block.PulseShaperBlock;
import dev.redstoneengineering.block.PwmControllerBlock;
import dev.redstoneengineering.block.QuartzOscillatorBlock;
import dev.redstoneengineering.block.QuartzTimingLineBlock;
import dev.redstoneengineering.block.RangeSensorBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Design-contract tests for registered blocks 11-20. */
public final class RseSecondTenDesignGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseSecondTenDesignGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 20)
    public static void pwmPeriodExposesDeterministicDutyQuantization(GameTestHelper helper) {
        int command = 7;
        int requested = PwmControllerBlock.requestedDutyPermille(command);
        int shortPeriod = PwmControllerBlock.periodFor(0);
        int longPeriod = PwmControllerBlock.periodFor(3);
        int shortOn = PwmControllerBlock.quantizedOnTicks(command, shortPeriod);
        int longOn = PwmControllerBlock.quantizedOnTicks(command, longPeriod);
        int shortEffective = PwmControllerBlock.effectiveDutyPermille(command, shortPeriod);
        int longEffective = PwmControllerBlock.effectiveDutyPermille(command, longPeriod);

        if (shortOn != 2 || longOn != 15) {
            helper.fail("PWM quantization did not map command 7/15 to the expected discrete on-tick counts");
            return;
        }
        if (Math.abs(longEffective - requested) >= Math.abs(shortEffective - requested)) {
            helper.fail("Longer PWM period must offer finer duty realization for command 7/15");
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void rangeSensorClearScanIsValidZeroThenTargetBecomesValidMeasurement(GameTestHelper helper) {
        BlockPos sensorPos = new BlockPos(0, 1, 2);
        BlockState sensor = RedstoneEngineering.RANGE_SENSOR.get().defaultBlockState()
                .setValue(RangeSensorBlock.FACING, Direction.EAST)
                .setValue(RangeSensorBlock.RANGE_MODE, 0)
                .setValue(RangeSensorBlock.MODE, 0)
                .setValue(RangeSensorBlock.RESPONSE, 0);
        helper.setBlock(sensorPos, sensor);

        helper.runAfterDelay(6, () -> {
            BlockState state = helper.getBlockState(sensorPos);
            RangeSensorBlock.ScanResult clear = RangeSensorBlock.lastScan(
                    helper.getLevel(), helper.absolutePos(sensorPos), state);
            var clearSnapshot = RedstoneEngineering.RANGE_SENSOR.get().engineeringSnapshot(
                    helper.getLevel(), helper.absolutePos(sensorPos), state, Direction.WEST).orElse(null);
            if (clear.status() != RangeSensorBlock.ScanStatus.CLEAR || clear.scannedCells() != 4
                    || clear.distance() != 0 || clearSnapshot == null
                    || clearSnapshot.quality() != PortQuality.VALID || Math.round(clearSnapshot.value()) != 0) {
                helper.fail("A complete empty Range Sensor scan must be valid zero evidence, not NO_SIGNAL", sensorPos);
                return;
            }

            BlockPos target = new BlockPos(3, 1, 2);
            helper.setBlock(target, Blocks.STONE.defaultBlockState());
            helper.getLevel().scheduleTick(helper.absolutePos(sensorPos), RedstoneEngineering.RANGE_SENSOR.get(), 1);
            helper.runAfterDelay(4, () -> {
                BlockState measuredState = helper.getBlockState(sensorPos);
                RangeSensorBlock.ScanResult measured = RangeSensorBlock.lastScan(
                        helper.getLevel(), helper.absolutePos(sensorPos), measuredState);
                var snapshot = RedstoneEngineering.RANGE_SENSOR.get().engineeringSnapshot(
                        helper.getLevel(), helper.absolutePos(sensorPos), measuredState, Direction.WEST).orElse(null);
                if (measured.status() != RangeSensorBlock.ScanStatus.TARGET || measured.distance() != 3
                        || snapshot == null || snapshot.quality() != PortQuality.VALID
                        || Math.round(snapshot.value()) <= 0) {
                    helper.fail("Range Sensor did not convert a real target into valid measured output", sensorPos);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void lapisTraceDistinguishesNoSourceSingleSourceAndConflict(GameTestHelper helper) {
        BlockPos line = new BlockPos(2, 1, 2);
        BlockPos west = new BlockPos(1, 1, 2);
        BlockPos east = new BlockPos(3, 1, 2);
        helper.setBlock(line, RedstoneEngineering.LAPIS_SIGNAL_LINE.get().defaultBlockState());

        helper.runAfterDelay(3, () -> {
            BlockPos world = helper.absolutePos(line);
            if (LapisSignalLineBlock.sourceCount(helper.getLevel(), world) != 0
                    || LapisSignalLineBlock.quality(helper.getLevel(), world) != PortQuality.NO_SIGNAL) {
                helper.fail("Undriven Lapis trace must report NO SOURCE", line);
                return;
            }

            helper.setBlock(west, RedstoneEngineering.LAPIS_PRECISION_SOURCE.get().defaultBlockState()
                    .setValue(LapisPrecisionSourceBlock.VALUE, 25));
            helper.runAfterDelay(3, () -> {
                if (LapisSignalLineBlock.sourceCount(helper.getLevel(), world) != 1
                        || LapisSignalLineBlock.quality(helper.getLevel(), world) != PortQuality.VALID
                        || LapisSignalLineBlock.value(helper.getLevel(), world) != 25) {
                    helper.fail("Single-source Lapis trace did not resolve valid ownership", line);
                    return;
                }

                helper.setBlock(east, RedstoneEngineering.LAPIS_PRECISION_SOURCE.get().defaultBlockState()
                        .setValue(LapisPrecisionSourceBlock.VALUE, 75));
                helper.runAfterDelay(3, () -> {
                    if (LapisSignalLineBlock.sourceCount(helper.getLevel(), world) < 2
                            || LapisSignalLineBlock.quality(helper.getLevel(), world) != PortQuality.TOPOLOGY_ERROR
                            || LapisSignalLineBlock.valid(helper.getLevel(), world)) {
                        helper.fail("Multi-source Lapis trace must surface SOURCE CONFLICT rather than generic no-signal", line);
                        return;
                    }
                    helper.succeed();
                });
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void quartzTraceDistinguishesNoClockSingleClockAndConflict(GameTestHelper helper) {
        BlockPos line = new BlockPos(2, 1, 2);
        BlockPos west = new BlockPos(1, 1, 2);
        BlockPos east = new BlockPos(3, 1, 2);
        helper.setBlock(line, RedstoneEngineering.QUARTZ_TIMING_LINE.get().defaultBlockState());

        helper.runAfterDelay(3, () -> {
            BlockPos world = helper.absolutePos(line);
            if (QuartzTimingLineBlock.sourceCount(helper.getLevel(), world) != 0
                    || QuartzTimingLineBlock.quality(helper.getLevel(), world) != PortQuality.NO_SIGNAL) {
                helper.fail("Undriven Quartz trace must report NO CLOCK SOURCE", line);
                return;
            }

            helper.setBlock(west, RedstoneEngineering.QUARTZ_OSCILLATOR.get().defaultBlockState()
                    .setValue(QuartzOscillatorBlock.PERIOD_INDEX, 2));
            helper.runAfterDelay(4, () -> {
                if (QuartzTimingLineBlock.sourceCount(helper.getLevel(), world) != 1
                        || QuartzTimingLineBlock.quality(helper.getLevel(), world) != PortQuality.VALID
                        || QuartzTimingLineBlock.period(helper.getLevel(), world) != 8) {
                    helper.fail("Single-source Quartz trace did not resolve one valid clock period", line);
                    return;
                }

                helper.setBlock(east, RedstoneEngineering.QUARTZ_OSCILLATOR.get().defaultBlockState()
                        .setValue(QuartzOscillatorBlock.PERIOD_INDEX, 0));
                helper.runAfterDelay(4, () -> {
                    if (QuartzTimingLineBlock.sourceCount(helper.getLevel(), world) < 2
                            || QuartzTimingLineBlock.quality(helper.getLevel(), world) != PortQuality.TOPOLOGY_ERROR
                            || QuartzTimingLineBlock.valid(helper.getLevel(), world)) {
                        helper.fail("Multiple Quartz oscillators must surface CLOCK CONFLICT", line);
                        return;
                    }
                    helper.succeed();
                });
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 20)
    public static void pulseAndResonatorObservationDoesNotCreateTransientActivity(GameTestHelper helper) {
        BlockPos pulse = new BlockPos(1, 1, 2);
        BlockPos resonator = new BlockPos(3, 1, 2);
        helper.setBlock(pulse, RedstoneEngineering.PULSE_SHAPER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));
        helper.setBlock(resonator, RedstoneEngineering.AMETHYST_RESONATOR.get().defaultBlockState());
        BlockPos pulseWorld = helper.absolutePos(pulse);
        BlockPos resonatorWorld = helper.absolutePos(resonator);

        if (RuntimeIntStore.peek(helper.getLevel(), "redstone_pulse_shaper", pulseWorld) != null
                || RuntimeIntStore.peek(helper.getLevel(), "amethyst_resonator", resonatorWorld) != null) {
            helper.fail("Test devices unexpectedly began with transient runtime state");
            return;
        }
        PulseShaperBlock.lastInput(helper.getLevel(), pulseWorld);
        PulseShaperBlock.pulseRemaining(helper.getLevel(), pulseWorld);
        PulseShaperBlock.initialized(helper.getLevel(), pulseWorld);
        AmethystResonatorBlock.isActive(helper.getLevel(), resonatorWorld);
        if (RuntimeIntStore.peek(helper.getLevel(), "redstone_pulse_shaper", pulseWorld) != null
                || RuntimeIntStore.peek(helper.getLevel(), "amethyst_resonator", resonatorWorld) != null) {
            helper.fail("Read-only Pulse Shaper / Resonator diagnostics created transient activity");
            return;
        }
        helper.succeed();
    }
}
