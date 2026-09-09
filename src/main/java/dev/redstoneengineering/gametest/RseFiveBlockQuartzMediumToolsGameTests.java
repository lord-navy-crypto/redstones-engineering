package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.block.QuartzClockDividerBlock;
import dev.redstoneengineering.block.QuartzLabOscillatorBlock;
import dev.redstoneengineering.block.QuartzStabilityMonitorBlock;
import dev.redstoneengineering.block.QuartzTimingLineBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.DomainNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Five-block Quartz timing-medium validation.
 *
 * <p>Main path: deterministic Lab Oscillator -> Timing Line A -> Clock Divider ->
 * Timing Line B -> Stability Monitor. The monitor is observational only. A second
 * lab oscillator is introduced only as a bounded fault injector for source contention.</p>
 */
public final class RseFiveBlockQuartzMediumToolsGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private static final BlockPos OSCILLATOR = new BlockPos(0, 1, 2);
    private static final BlockPos LINE_A = new BlockPos(1, 1, 2);
    private static final BlockPos DIVIDER = new BlockPos(2, 1, 2);
    private static final BlockPos LINE_B = new BlockPos(3, 1, 2);
    private static final BlockPos MONITOR = new BlockPos(4, 1, 2);
    private static final BlockPos CONFLICT_OSCILLATOR = new BlockPos(3, 1, 1);

    private static final int INPUT_PERIOD = 4;
    private static final int DIVISION = 2;
    private static final int OUTPUT_PERIOD = INPUT_PERIOD * DIVISION;

    private RseFiveBlockQuartzMediumToolsGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 90)
    public static void dividerTransformsPeriodAndMonitorMeasuresRealEdgesWithoutBackDrive(GameTestHelper helper) {
        buildPath(helper);

        helper.runAfterDelay(36, () -> {
            if (!assertLivePath(helper, "settled divided clock")) return;

            BlockPos lineBWorld = helper.absolutePos(LINE_B);
            int sourcesBefore = QuartzTimingLineBlock.sourceCount(helper.getLevel(), lineBWorld);
            QuartzStabilityMonitorBlock monitor = RedstoneEngineering.QUARTZ_STABILITY_MONITOR.get();
            var snapshot = monitor.engineeringSnapshot(
                    helper.getLevel(), helper.absolutePos(MONITOR), helper.getBlockState(MONITOR), Direction.WEST).orElse(null);
            QuartzStabilityMonitorBlock.TimingMeasurement measurement = QuartzStabilityMonitorBlock.measurement(
                    helper.getLevel(), helper.absolutePos(MONITOR));
            int sourcesAfter = QuartzTimingLineBlock.sourceCount(helper.getLevel(), lineBWorld);

            if (snapshot == null || snapshot.quality() != PortQuality.VALID
                    || Math.round(snapshot.value()) != OUTPUT_PERIOD
                    || !measurement.initialized() || !measurement.referenceEdgeSeen()
                    || !measurement.currentMeasurement() || measurement.period() != OUTPUT_PERIOD
                    || measurement.nominalError() != 0) {
                helper.fail("Stability Monitor did not measure the real divided 8-tick period", MONITOR);
                return;
            }
            if (sourcesBefore != 1 || sourcesAfter != 1) {
                helper.fail("Reading the Quartz Stability Monitor changed source ownership", MONITOR);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 180)
    public static void quartzBreakConflictAndRecoverySeparateLiveStateFromRetainedEvidence(GameTestHelper helper) {
        buildPath(helper);

        helper.runAfterDelay(36, () -> {
            if (!assertLivePath(helper, "single-source baseline")) return;

            helper.setBlock(LINE_A, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(8, () -> {
                BlockState oscillatorState = helper.getBlockState(OSCILLATOR);
                var oscillatorSnapshot = RedstoneEngineering.QUARTZ_LAB_OSCILLATOR.get().engineeringSnapshot(
                        helper.getLevel(), helper.absolutePos(OSCILLATOR), oscillatorState, Direction.EAST).orElse(null);
                if (oscillatorSnapshot == null || oscillatorSnapshot.quality() != PortQuality.VALID
                        || Math.round(oscillatorSnapshot.value()) != INPUT_PERIOD) {
                    helper.fail("Breaking Quartz Line A invalidated the upstream oscillator source", OSCILLATOR);
                    return;
                }

                BlockPos lineBWorld = helper.absolutePos(LINE_B);
                if (QuartzTimingLineBlock.quality(helper.getLevel(), lineBWorld) != PortQuality.NO_SIGNAL
                        || QuartzTimingLineBlock.sourceCount(helper.getLevel(), lineBWorld) != 0
                        || QuartzTimingLineBlock.valid(helper.getLevel(), lineBWorld)) {
                    helper.fail("Divider failed to release downstream Quartz ownership after Line A break", LINE_B);
                    return;
                }
                var staleMonitor = RedstoneEngineering.QUARTZ_STABILITY_MONITOR.get().engineeringSnapshot(
                        helper.getLevel(), helper.absolutePos(MONITOR), helper.getBlockState(MONITOR), Direction.WEST).orElse(null);
                if (staleMonitor == null || staleMonitor.quality() != PortQuality.STALE
                        || Math.round(staleMonitor.value()) != OUTPUT_PERIOD) {
                    helper.fail("Monitor erased or mislabeled retained timing evidence after upstream clock loss", MONITOR);
                    return;
                }

                helper.setBlock(LINE_A, RedstoneEngineering.QUARTZ_TIMING_LINE.get().defaultBlockState());
                helper.runAfterDelay(32, () -> {
                    if (!assertLivePath(helper, "Line A reconnect")) return;

                    helper.setBlock(LINE_B, Blocks.AIR.defaultBlockState());
                    helper.runAfterDelay(6, () -> {
                        BlockPos lineAWorld = helper.absolutePos(LINE_A);
                        if (QuartzTimingLineBlock.quality(helper.getLevel(), lineAWorld) != PortQuality.VALID
                                || QuartzTimingLineBlock.sourceCount(helper.getLevel(), lineAWorld) != 1
                                || QuartzTimingLineBlock.period(helper.getLevel(), lineAWorld) != INPUT_PERIOD) {
                            helper.fail("Receiver-side Quartz break corrupted the upstream oscillator segment", LINE_A);
                            return;
                        }
                        var staleAfterReceiverBreak = RedstoneEngineering.QUARTZ_STABILITY_MONITOR.get().engineeringSnapshot(
                                helper.getLevel(), helper.absolutePos(MONITOR), helper.getBlockState(MONITOR), Direction.WEST).orElse(null);
                        if (staleAfterReceiverBreak == null || staleAfterReceiverBreak.quality() != PortQuality.STALE
                                || Math.round(staleAfterReceiverBreak.value()) != OUTPUT_PERIOD) {
                            helper.fail("Monitor failed to retain old timing as STALE after its physical medium was removed", MONITOR);
                            return;
                        }

                        helper.setBlock(LINE_B, RedstoneEngineering.QUARTZ_TIMING_LINE.get().defaultBlockState());
                        helper.runAfterDelay(32, () -> {
                            if (!assertLivePath(helper, "Line B reconnect")) return;

                            helper.setBlock(CONFLICT_OSCILLATOR, oscillator(1));
                            helper.runAfterDelay(8, () -> {
                                BlockPos conflictedLine = helper.absolutePos(LINE_B);
                                if (QuartzTimingLineBlock.quality(helper.getLevel(), conflictedLine) != PortQuality.TOPOLOGY_ERROR
                                        || QuartzTimingLineBlock.sourceCount(helper.getLevel(), conflictedLine) != 2
                                        || QuartzTimingLineBlock.valid(helper.getLevel(), conflictedLine)) {
                                    helper.fail("Divider plus second real Quartz oscillator did not surface a two-source conflict", LINE_B);
                                    return;
                                }
                                BlockPos upstreamLineWorld = helper.absolutePos(LINE_A);
                                if (QuartzTimingLineBlock.quality(helper.getLevel(), upstreamLineWorld) != PortQuality.VALID
                                        || QuartzTimingLineBlock.sourceCount(helper.getLevel(), upstreamLineWorld) != 1
                                        || QuartzTimingLineBlock.period(helper.getLevel(), upstreamLineWorld) != INPUT_PERIOD) {
                                    helper.fail("Downstream Quartz contention back-propagated into the upstream oscillator segment", LINE_A);
                                    return;
                                }
                                var conflictSnapshot = RedstoneEngineering.QUARTZ_STABILITY_MONITOR.get().engineeringSnapshot(
                                        helper.getLevel(), helper.absolutePos(MONITOR), helper.getBlockState(MONITOR), Direction.WEST).orElse(null);
                                if (conflictSnapshot == null || conflictSnapshot.quality() != PortQuality.TOPOLOGY_ERROR) {
                                    helper.fail("Stability Monitor collapsed Quartz source contention into ordinary stale/no-signal evidence", MONITOR);
                                    return;
                                }

                                helper.setBlock(CONFLICT_OSCILLATOR, Blocks.AIR.defaultBlockState());
                                helper.runAfterDelay(32, () -> {
                                    if (!assertLivePath(helper, "contention recovery")) return;
                                    helper.succeed();
                                });
                            });
                        });
                    });
                });
            });
        });
    }

    private static void buildPath(GameTestHelper helper) {
        helper.setBlock(OSCILLATOR, oscillator(1));
        helper.setBlock(LINE_A, RedstoneEngineering.QUARTZ_TIMING_LINE.get().defaultBlockState());
        helper.setBlock(DIVIDER, RedstoneEngineering.QUARTZ_CLOCK_DIVIDER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                .setValue(QuartzClockDividerBlock.DIV_INDEX, 0));
        helper.setBlock(LINE_B, RedstoneEngineering.QUARTZ_TIMING_LINE.get().defaultBlockState());
        helper.setBlock(MONITOR, RedstoneEngineering.QUARTZ_STABILITY_MONITOR.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
    }

    private static BlockState oscillator(int periodIndex) {
        return RedstoneEngineering.QUARTZ_LAB_OSCILLATOR.get().defaultBlockState()
                .setValue(QuartzLabOscillatorBlock.PERIOD_INDEX, periodIndex)
                .setValue(QuartzLabOscillatorBlock.JITTER, 0)
                .setValue(QuartzLabOscillatorBlock.ACTIVE, false);
    }

    private static boolean assertLivePath(GameTestHelper helper, String phase) {
        BlockPos lineAWorld = helper.absolutePos(LINE_A);
        BlockPos lineBWorld = helper.absolutePos(LINE_B);

        if (QuartzTimingLineBlock.quality(helper.getLevel(), lineAWorld) != PortQuality.VALID
                || QuartzTimingLineBlock.sourceCount(helper.getLevel(), lineAWorld) != 1
                || QuartzTimingLineBlock.period(helper.getLevel(), lineAWorld) != INPUT_PERIOD) {
            helper.fail("Quartz input segment mismatch during " + phase, LINE_A);
            return false;
        }
        if (!QuartzClockDividerBlock.initialized(helper.getLevel(), helper.absolutePos(DIVIDER))) {
            helper.fail("Quartz divider never initialized from a real clock during " + phase, DIVIDER);
            return false;
        }
        if (QuartzTimingLineBlock.quality(helper.getLevel(), lineBWorld) != PortQuality.VALID
                || QuartzTimingLineBlock.sourceCount(helper.getLevel(), lineBWorld) != 1
                || QuartzTimingLineBlock.period(helper.getLevel(), lineBWorld) != OUTPUT_PERIOD) {
            helper.fail("Quartz divided output segment mismatch during " + phase, LINE_B);
            return false;
        }

        DomainNetwork.QuartzSample sample = DomainNetwork.sampleQuartz(helper.getLevel(), lineBWorld);
        if (!sample.valid() || sample.periodTicks() != OUTPUT_PERIOD) {
            helper.fail("Quartz network sample disagreed with line evidence during " + phase, LINE_B);
            return false;
        }

        QuartzStabilityMonitorBlock.TimingMeasurement measurement = QuartzStabilityMonitorBlock.measurement(
                helper.getLevel(), helper.absolutePos(MONITOR));
        var monitorSnapshot = RedstoneEngineering.QUARTZ_STABILITY_MONITOR.get().engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(MONITOR), helper.getBlockState(MONITOR), Direction.WEST).orElse(null);
        if (monitorSnapshot == null || monitorSnapshot.quality() != PortQuality.VALID
                || Math.round(monitorSnapshot.value()) != OUTPUT_PERIOD
                || !measurement.currentMeasurement() || measurement.period() != OUTPUT_PERIOD) {
            helper.fail("Quartz stability readback mismatch during " + phase, MONITOR);
            return false;
        }
        return true;
    }
}
