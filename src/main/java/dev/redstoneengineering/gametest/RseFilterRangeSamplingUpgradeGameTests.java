package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.LapisPrecisionRangeSensorBlock;
import dev.redstoneengineering.block.RangeSensorBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.SampleHoldBlock;
import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real-world regression witnesses for the shared bounded ray and sampled inputs. */
public final class RseFilterRangeSamplingUpgradeGameTests {
    private static final String TEMPLATE = "empty5x4x5";
    private RseFilterRangeSamplingUpgradeGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 35)
    public static void redstoneRangeSeparatesUninitializedCoverageFromActualTarget(GameTestHelper helper) {
        BlockPos sensor = new BlockPos(2, 1, 2);
        BlockPos obstacle = new BlockPos(3, 1, 2);
        helper.setBlock(sensor, RedstoneEngineering.RANGE_SENSOR.get().defaultBlockState()
                .setValue(RangeSensorBlock.FACING, Direction.EAST)
                .setValue(RangeSensorBlock.RANGE_MODE, 0)
                .setValue(RangeSensorBlock.MODE, 0));
        var device = RedstoneEngineering.RANGE_SENSOR.get();
        var before = device.engineeringSnapshot(helper.getLevel(), helper.absolutePos(sensor),
                helper.getBlockState(sensor), Direction.WEST).orElseThrow();
        if (before.quality() != PortQuality.STALE) {
            helper.fail("Range sensor must report STALE before its first authoritative scan", sensor);
            return;
        }
        helper.setBlock(obstacle, Blocks.STONE);
        helper.runAfterDelay(6, () -> {
            var scan = RangeSensorBlock.lastScan(helper.getLevel(), helper.absolutePos(sensor),
                    helper.getBlockState(sensor));
            var output = device.engineeringSnapshot(helper.getLevel(), helper.absolutePos(sensor),
                    helper.getBlockState(sensor), Direction.WEST).orElseThrow();
            if (!scan.complete() || !scan.targetDetected() || scan.distance() != 1
                    || output.quality() != PortQuality.VALID) {
                helper.fail("Shared ray scan failed to publish a complete target measurement", sensor);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 30)
    public static void lapisRangeRayKeepsOwnDirectionAndNormalizedDistance(GameTestHelper helper) {
        BlockPos sensor = new BlockPos(2, 1, 2);
        BlockPos obstacle = new BlockPos(3, 1, 2);
        helper.setBlock(sensor, RedstoneEngineering.LAPIS_PRECISION_RANGE_SENSOR.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.WEST)
                .setValue(LapisPrecisionRangeSensorBlock.RANGE_INDEX, 0));
        helper.setBlock(obstacle, Blocks.STONE);
        var result = LapisPrecisionRangeSensorBlock.rangeSample(helper.getLevel(),
                helper.absolutePos(sensor), helper.getBlockState(sensor));
        if (!result.complete() || result.distance() != 1
                || result.maxRange() != 8 || result.quality() != PortQuality.VALID) {
            helper.fail("Lapis precision range lost independent sensing direction or measured distance", sensor);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 65)
    public static void redstoneSamplerOnlyCapturesValidZeroAndRejectsMissingInput(GameTestHelper helper) {
        BlockPos sample = new BlockPos(2, 1, 2);
        BlockPos value = new BlockPos(1, 1, 2);
        BlockPos clock = new BlockPos(2, 1, 1);
        helper.setBlock(value, RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.EAST)
                .setValue(RedstoneReferenceSourceBlock.POWER, 0));
        var clockLow = RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.SOUTH)
                .setValue(RedstoneReferenceSourceBlock.POWER, 0);
        helper.setBlock(clock, clockLow);
        helper.setBlock(sample, RedstoneEngineering.SAMPLE_HOLD.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(DirectionalSignalBlock.INPUT_FACING, Direction.WEST));
        BlockPos world = helper.absolutePos(sample);
        helper.runAfterDelay(4, () -> {
            helper.setBlock(clock, clockLow.setValue(RedstoneReferenceSourceBlock.POWER, 15));
            helper.runAfterDelay(4, () -> {
                int baseline = SampleHoldBlock.captureCount(helper.getLevel(), world);
                if (baseline != 1 || SampleHoldBlock.outputQuality(helper.getLevel(), world) != PortQuality.VALID
                        || helper.getBlockState(sample).getValue(DirectionalSignalBlock.OUTPUT) != 0) {
                    helper.fail("Configured zero must be captured as valid data on a real trigger edge", sample);
                    return;
                }
                helper.setBlock(value, Blocks.AIR);
                helper.setBlock(clock, clockLow);
                helper.runAfterDelay(4, () -> {
                    helper.setBlock(clock, clockLow.setValue(RedstoneReferenceSourceBlock.POWER, 15));
                    helper.runAfterDelay(4, () -> {
                        if (SampleHoldBlock.captureCount(helper.getLevel(), world) != baseline
                                || SampleHoldBlock.outputQuality(helper.getLevel(), world) != PortQuality.VALID) {
                            helper.fail("Absent data input must not become a new valid zero capture", sample);
                            return;
                        }
                        helper.succeed();
                    });
                });
            });
        });
    }
}
