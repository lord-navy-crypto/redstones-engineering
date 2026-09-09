package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.LapisLowPassFilterBlock;
import dev.redstoneengineering.block.LapisPrecisionMeterBlock;
import dev.redstoneengineering.block.LapisPrecisionSourceBlock;
import dev.redstoneengineering.block.LapisSignalLineBlock;
import dev.redstoneengineering.block.LapisToRedstoneQuantizerBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.RedstoneToLapisScalerBlock;
import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Five-block precision-medium validation.
 *
 * <p>Main path: Redstone-to-Lapis Scaler -> Lapis Line A -> Low-pass Filter ->
 * Lapis Line B -> Lapis-to-Redstone Quantizer. A reference source feeds the
 * scaler and a precision meter observes Line B without becoming a driver.</p>
 */
public final class RseFiveBlockLapisMediumToolsGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private static final BlockPos SOURCE = new BlockPos(0, 1, 2);
    private static final BlockPos SCALER = new BlockPos(1, 1, 2);
    private static final BlockPos LINE_A = new BlockPos(2, 1, 2);
    private static final BlockPos FILTER = new BlockPos(3, 1, 2);
    private static final BlockPos LINE_B = new BlockPos(4, 1, 2);
    private static final BlockPos METER = new BlockPos(4, 1, 1);
    private static final BlockPos QUANTIZER = new BlockPos(4, 1, 3);

    private RseFiveBlockLapisMediumToolsGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 110)
    public static void precisionConversionFilterPathPreservesDrivenZeroAndObserverNeutrality(GameTestHelper helper) {
        buildPath(helper, 9);

        helper.runAfterDelay(12, () -> {
            if (!assertPath(helper, 60, 9, "initial 9/15 -> 0.60 conversion")) return;

            helper.setBlock(SOURCE, reference(0));
            helper.runAfterDelay(14, () -> {
                if (!assertPath(helper, 0, 0, "connected valid zero")) return;

                helper.setBlock(SOURCE, reference(12));
                helper.runAfterDelay(14, () -> {
                    if (!assertPath(helper, 80, 12, "post-zero 12/15 -> 0.80 recovery")) return;
                    helper.succeed();
                });
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 150)
    public static void lapisMediumBreakConflictAndRecoveryPreserveSourceEvidence(GameTestHelper helper) {
        buildPath(helper, 12);

        helper.runAfterDelay(12, () -> {
            if (!assertPath(helper, 80, 12, "single-source baseline")) return;

            helper.setBlock(LINE_A, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(8, () -> {
                BlockPos scalerWorld = helper.absolutePos(SCALER);
                if (RedstoneToLapisScalerBlock.outputValue(helper.getLevel(), scalerWorld) != 80
                        || RedstoneToLapisScalerBlock.outputQuality(helper.getLevel(), scalerWorld) != PortQuality.VALID) {
                    helper.fail("Breaking Lapis Line A back-drove or invalidated the upstream scaler source", SCALER);
                    return;
                }

                var filterInput = RedstoneEngineering.LAPIS_LOW_PASS_FILTER.get().engineeringSnapshot(
                        helper.getLevel(), helper.absolutePos(FILTER), helper.getBlockState(FILTER), Direction.WEST).orElse(null);
                if (filterInput == null || filterInput.quality() != PortQuality.NO_SIGNAL
                        || LapisSignalLineBlock.quality(helper.getLevel(), helper.absolutePos(LINE_B)) != PortQuality.NO_SIGNAL
                        || LapisSignalLineBlock.sourceCount(helper.getLevel(), helper.absolutePos(LINE_B)) != 0
                        || LapisToRedstoneQuantizerBlock.outputQuality(helper.getLevel(), helper.absolutePos(QUANTIZER)) != PortQuality.NO_SIGNAL
                        || helper.getBlockState(QUANTIZER).getValue(LapisToRedstoneQuantizerBlock.POWER) != 0) {
                    helper.fail("Upstream Lapis medium break did not become downstream NO_SIGNAL", FILTER);
                    return;
                }

                helper.setBlock(LINE_A, RedstoneEngineering.LAPIS_SIGNAL_LINE.get().defaultBlockState());
                helper.runAfterDelay(12, () -> {
                    if (!assertPath(helper, 80, 12, "Line A reconnect")) return;

                    helper.setBlock(LINE_B, Blocks.AIR.defaultBlockState());
                    helper.runAfterDelay(8, () -> {
                        if (LapisSignalLineBlock.quality(helper.getLevel(), helper.absolutePos(LINE_A)) != PortQuality.VALID
                                || LapisSignalLineBlock.value(helper.getLevel(), helper.absolutePos(LINE_A)) != 80
                                || !LapisLowPassFilterBlock.filterState(helper.getLevel(), helper.absolutePos(FILTER)).valid()) {
                            helper.fail("Receiver-side Lapis medium break corrupted the upstream filtered path", LINE_A);
                            return;
                        }
                        LapisPrecisionMeterBlock.MeterReading missingReading = LapisPrecisionMeterBlock.reading(
                                helper.getLevel(), helper.absolutePos(METER), helper.getBlockState(METER));
                        if (missingReading.quality() != PortQuality.NO_SIGNAL
                                || LapisToRedstoneQuantizerBlock.outputQuality(helper.getLevel(), helper.absolutePos(QUANTIZER)) != PortQuality.NO_SIGNAL
                                || helper.getBlockState(QUANTIZER).getValue(LapisToRedstoneQuantizerBlock.POWER) != 0) {
                            helper.fail("Removing Lapis Line B did not clear both observer and quantizer evidence", LINE_B);
                            return;
                        }

                        helper.setBlock(LINE_B, RedstoneEngineering.LAPIS_SIGNAL_LINE.get().defaultBlockState());
                        helper.runAfterDelay(12, () -> {
                            if (!assertPath(helper, 80, 12, "Line B reconnect")) return;

                            helper.setBlock(METER, RedstoneEngineering.LAPIS_PRECISION_SOURCE.get().defaultBlockState()
                                    .setValue(LapisPrecisionSourceBlock.VALUE, 20));
                            helper.runAfterDelay(8, () -> {
                                BlockPos lineBWorld = helper.absolutePos(LINE_B);
                                if (LapisSignalLineBlock.quality(helper.getLevel(), lineBWorld) != PortQuality.TOPOLOGY_ERROR
                                        || LapisSignalLineBlock.sourceCount(helper.getLevel(), lineBWorld) != 2
                                        || LapisSignalLineBlock.value(helper.getLevel(), lineBWorld) != 0) {
                                    helper.fail("Filtered Lapis source plus a real second source did not surface a two-source conflict", LINE_B);
                                    return;
                                }
                                LapisLowPassFilterBlock.FilterState filterState = LapisLowPassFilterBlock.filterState(
                                        helper.getLevel(), helper.absolutePos(FILTER));
                                if (!filterState.valid() || filterState.output() != 80) {
                                    helper.fail("Downstream Lapis source conflict back-propagated into the upstream filter state", FILTER);
                                    return;
                                }
                                if (LapisToRedstoneQuantizerBlock.outputQuality(helper.getLevel(), helper.absolutePos(QUANTIZER)) != PortQuality.TOPOLOGY_ERROR
                                        || helper.getBlockState(QUANTIZER).getValue(LapisToRedstoneQuantizerBlock.POWER) != 0) {
                                    helper.fail("Lapis quantizer collapsed source conflict into an ordinary zero/no-signal state", QUANTIZER);
                                    return;
                                }

                                helper.setBlock(METER, Blocks.AIR.defaultBlockState());
                                helper.setBlock(METER, meter());
                                helper.runAfterDelay(12, () -> {
                                    if (!assertPath(helper, 80, 12, "source-conflict recovery")) return;
                                    helper.succeed();
                                });
                            });
                        });
                    });
                });
            });
        });
    }

    private static void buildPath(GameTestHelper helper, int power) {
        helper.setBlock(SOURCE, reference(power));
        helper.setBlock(SCALER, RedstoneEngineering.REDSTONE_TO_LAPIS_SCALER.get().defaultBlockState()
                .setValue(RedstoneToLapisScalerBlock.FACING, Direction.EAST));
        helper.setBlock(LINE_A, RedstoneEngineering.LAPIS_SIGNAL_LINE.get().defaultBlockState());
        helper.setBlock(FILTER, RedstoneEngineering.LAPIS_LOW_PASS_FILTER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                .setValue(LapisLowPassFilterBlock.ALPHA, 3));
        helper.setBlock(LINE_B, RedstoneEngineering.LAPIS_SIGNAL_LINE.get().defaultBlockState());
        helper.setBlock(METER, meter());
        helper.setBlock(QUANTIZER, RedstoneEngineering.LAPIS_TO_REDSTONE_QUANTIZER.get().defaultBlockState()
                .setValue(LapisToRedstoneQuantizerBlock.FACING, Direction.SOUTH));
    }

    private static BlockState reference(int power) {
        return RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.EAST)
                .setValue(RedstoneReferenceSourceBlock.POWER, power);
    }

    private static BlockState meter() {
        return RedstoneEngineering.LAPIS_PRECISION_METER.get().defaultBlockState()
                .setValue(LapisPrecisionMeterBlock.FACING, Direction.SOUTH);
    }

    private static boolean assertPath(GameTestHelper helper, int expectedLapis, int expectedRedstone, String phase) {
        BlockPos scalerWorld = helper.absolutePos(SCALER);
        BlockPos lineAWorld = helper.absolutePos(LINE_A);
        BlockPos filterWorld = helper.absolutePos(FILTER);
        BlockPos lineBWorld = helper.absolutePos(LINE_B);
        BlockPos quantizerWorld = helper.absolutePos(QUANTIZER);

        if (RedstoneToLapisScalerBlock.outputValue(helper.getLevel(), scalerWorld) != expectedLapis
                || RedstoneToLapisScalerBlock.outputQuality(helper.getLevel(), scalerWorld) != PortQuality.VALID) {
            helper.fail("Scaler conversion mismatch during " + phase, SCALER);
            return false;
        }
        if (LapisSignalLineBlock.value(helper.getLevel(), lineAWorld) != expectedLapis
                || LapisSignalLineBlock.quality(helper.getLevel(), lineAWorld) != PortQuality.VALID
                || LapisSignalLineBlock.sourceCount(helper.getLevel(), lineAWorld) != 1) {
            helper.fail("Lapis Line A source/value mismatch during " + phase, LINE_A);
            return false;
        }
        LapisLowPassFilterBlock.FilterState filterState = LapisLowPassFilterBlock.filterState(helper.getLevel(), filterWorld);
        if (!filterState.valid() || filterState.output() != expectedLapis) {
            helper.fail("Low-pass filtered output mismatch during " + phase + ": expected="
                    + expectedLapis + " actual=" + filterState.output() + " valid=" + filterState.valid(), FILTER);
            return false;
        }
        if (LapisSignalLineBlock.value(helper.getLevel(), lineBWorld) != expectedLapis
                || LapisSignalLineBlock.quality(helper.getLevel(), lineBWorld) != PortQuality.VALID
                || LapisSignalLineBlock.sourceCount(helper.getLevel(), lineBWorld) != 1) {
            helper.fail("Lapis Line B source/value mismatch during " + phase, LINE_B);
            return false;
        }
        LapisPrecisionMeterBlock.MeterReading reading = LapisPrecisionMeterBlock.reading(
                helper.getLevel(), helper.absolutePos(METER), helper.getBlockState(METER));
        if (reading.value() != expectedLapis || reading.quality() != PortQuality.VALID
                || LapisSignalLineBlock.sourceCount(helper.getLevel(), lineBWorld) != 1) {
            helper.fail("Precision meter became invasive or read the wrong value during " + phase, METER);
            return false;
        }
        if (helper.getBlockState(QUANTIZER).getValue(LapisToRedstoneQuantizerBlock.POWER) != expectedRedstone
                || LapisToRedstoneQuantizerBlock.outputQuality(helper.getLevel(), quantizerWorld) != PortQuality.VALID) {
            helper.fail("Lapis quantizer conversion/quality mismatch during " + phase, QUANTIZER);
            return false;
        }
        return true;
    }
}
