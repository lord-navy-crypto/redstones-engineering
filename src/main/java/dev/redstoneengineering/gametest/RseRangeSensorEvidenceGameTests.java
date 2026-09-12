package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.RangeSensorBlock;
import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Regression coverage for valid zero-valued range evidence. */
public final class RseRangeSensorEvidenceGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseRangeSensorEvidenceGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void clearCompletedScanPublishesValidZeroEvidence(GameTestHelper helper) {
        BlockPos sensorPos = new BlockPos(1, 1, 2);
        BlockPos worldPos = helper.absolutePos(sensorPos);
        helper.setBlock(sensorPos, RedstoneEngineering.RANGE_SENSOR.get()
                .defaultBlockState()
                .setValue(RangeSensorBlock.FACING, Direction.EAST));

        helper.runAfterDelay(6, () -> {
            BlockState state = helper.getBlockState(sensorPos);
            RangeSensorBlock.ScanResult scan = RangeSensorBlock.lastScan(helper.getLevel(), worldPos, state);
            if (scan.status() != RangeSensorBlock.ScanStatus.CLEAR || !scan.complete()) {
                helper.fail("Empty loaded range did not produce completed CLEAR evidence", sensorPos);
                return;
            }
            if (scan.distance() != 0 || state.getValue(RangeSensorBlock.OUTPUT) != 0) {
                helper.fail("CLEAR scan must preserve zero as the measurement payload", sensorPos);
                return;
            }

            var snapshot = ((RangeSensorBlock) state.getBlock()).engineeringSnapshot(
                    helper.getLevel(), worldPos, state, RangeSensorBlock.outputSide(state));
            if (snapshot.isEmpty()) {
                helper.fail("Range sensor output port did not expose an engineering snapshot", sensorPos);
                return;
            }
            if (snapshot.get().quality() != PortQuality.VALID || snapshot.get().value() != 0.0) {
                helper.fail("Completed CLEAR scan was not published as VALID zero-valued evidence", sensorPos);
                return;
            }
            helper.succeed();
        });
    }
}
