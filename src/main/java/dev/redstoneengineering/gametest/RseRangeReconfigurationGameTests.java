package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.PrecisionFilterBlock;
import dev.redstoneengineering.block.RangeSensorBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises real configuration invalidation rather than just source token checks. */
public final class RseRangeReconfigurationGameTests {
    private static final String TEMPLATE = "empty5x4x5";
    private static final String SCAN_KEY = "range_sensor_scan";

    private RseRangeReconfigurationGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void changingRangeRevokesPreviouslyValidMeasurement(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        BlockPos world = helper.absolutePos(pos);
        BlockState initial = RedstoneEngineering.RANGE_SENSOR.get().defaultBlockState()
                .setValue(RangeSensorBlock.FACING, Direction.EAST)
                .setValue(RangeSensorBlock.OUTPUT, 12);
        helper.setBlock(pos, initial);
        int[] runtime = RuntimeIntStore.get(helper.getLevel(), SCAN_KEY, world, 3);
        runtime[0] = 2;
        runtime[1] = RangeSensorBlock.ScanStatus.TARGET.ordinal();
        runtime[2] = 2;
        if (RedstoneEngineering.RANGE_SENSOR.get().engineeringSnapshot(helper.getLevel(),
                world, initial, Direction.WEST).orElseThrow().quality() != PortQuality.VALID) {
            helper.fail("Expected existing target scan to be authoritative", pos);
            return;
        }

        BlockState changed = initial.setValue(RangeSensorBlock.RANGE_MODE, 0);
        if (!RangeSensorBlock.applyConfiguration(helper.getLevel(), world, changed)) {
            helper.fail("Range configuration refused a legitimate range change", pos);
            return;
        }
        BlockState updated = helper.getBlockState(pos);
        var latest = RangeSensorBlock.lastScan(helper.getLevel(), world, updated);
        var port = RedstoneEngineering.RANGE_SENSOR.get().engineeringSnapshot(
                helper.getLevel(), world, updated, Direction.WEST).orElseThrow();
        if (updated.getValue(RangeSensorBlock.OUTPUT) != 0
                || latest.status() != RangeSensorBlock.ScanStatus.UNINITIALIZED
                || RuntimeIntStore.peek(helper.getLevel(), SCAN_KEY, world) != null
                || port.quality() != PortQuality.STALE) {
            helper.fail("Range mode change leaked pre-configuration scan or output", pos);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void rotatingSensorDropsOldOutputAndScanHistory(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        BlockPos world = helper.absolutePos(pos);
        BlockState initial = RedstoneEngineering.RANGE_SENSOR.get().defaultBlockState()
                .setValue(RangeSensorBlock.FACING, Direction.EAST)
                .setValue(RangeSensorBlock.OUTPUT, 15);
        helper.setBlock(pos, initial);
        int[] runtime = RuntimeIntStore.get(helper.getLevel(), SCAN_KEY, world, 3);
        runtime[0] = 1;
        runtime[1] = RangeSensorBlock.ScanStatus.TARGET.ordinal();
        runtime[2] = 1;

        if (!RangeSensorBlock.rotateSensingAxis(helper.getLevel(), world, true)) {
            helper.fail("Sensor facing could not rotate", pos);
            return;
        }
        BlockState updated = helper.getBlockState(pos);
        if (RangeSensorBlock.sensingSide(updated) != Direction.SOUTH
                || updated.getValue(RangeSensorBlock.OUTPUT) != 0
                || RangeSensorBlock.lastScan(helper.getLevel(), world, updated).complete()
                || RuntimeIntStore.peek(helper.getLevel(), SCAN_KEY, world) != null) {
            helper.fail("Rotated sensing axis kept a measurement from the previous direction", pos);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void filterInputReportsAbsentVersusDrivenZeroWithoutAllocatingRuntime(GameTestHelper helper) {
        BlockPos inputPos = new BlockPos(1, 1, 2);
        BlockPos pos = new BlockPos(2, 1, 2);
        BlockPos world = helper.absolutePos(pos);
        BlockState state = RedstoneEngineering.PRECISION_FILTER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(DirectionalSignalBlock.INPUT_FACING, Direction.WEST);
        helper.setBlock(pos, state);
        int before = RuntimeIntStore.entryCount(helper.getLevel());
        var empty = RedstoneEngineering.PRECISION_FILTER.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(pos), Direction.WEST).orElseThrow();
        if (empty.quality() != PortQuality.NO_SIGNAL || RuntimeIntStore.entryCount(helper.getLevel()) != before) {
            helper.fail("Slew filter invented input source evidence on an empty connector", pos);
            return;
        }
        helper.setBlock(inputPos, RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.EAST)
                .setValue(RedstoneReferenceSourceBlock.POWER, 0));
        var driven = RedstoneEngineering.PRECISION_FILTER.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(pos), Direction.WEST).orElseThrow();
        if (driven.value() != 0.0 || driven.quality() != PortQuality.VALID) {
            helper.fail("Driven zero should be VALID, not NO_SIGNAL", pos);
            return;
        }
        helper.succeed();
    }
}
