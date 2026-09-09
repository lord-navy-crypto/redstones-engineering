package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.TemperatureSensorBlock;
import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** System regressions for thermal measurement coverage quality. */
public final class RseThermalMeasurementSystemGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseThermalMeasurementSystemGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void temperatureSensorDistinguishesUnknownCoverageFromValidAmbient(GameTestHelper helper) {
        BlockState sensorState = RedstoneEngineering.TEMPERATURE_SENSOR.get().defaultBlockState();

        // Exercise the production snapshot against a genuinely unloaded remote aperture.
        BlockPos remote = helper.absolutePos(new BlockPos(2, 1, 2)).offset(1_000_000, 0, 1_000_000);
        if (helper.getLevel().hasChunkAt(remote) || helper.getLevel().hasChunkAt(remote.north())) {
            helper.fail("Precondition failed: remote thermal aperture unexpectedly loaded");
            return;
        }
        var unknown = RedstoneEngineering.TEMPERATURE_SENSOR.get()
                .engineeringSnapshot(helper.getLevel(), remote, sensorState, Direction.NORTH).orElse(null);
        if (unknown == null || unknown.quality() != PortQuality.STALE) {
            helper.fail("Unloaded thermal coverage must be STALE, not NO_SIGNAL");
            return;
        }

        // A real placed sensor with complete loaded empty-space coverage must still be VALID ambient data.
        BlockPos sensorPos = new BlockPos(2, 1, 2);
        helper.setBlock(sensorPos, sensorState);
        helper.runAfterDelay(3, () -> {
            BlockPos world = helper.absolutePos(sensorPos);
            BlockState state = helper.getBlockState(sensorPos);
            TemperatureSensorBlock.ThermalObservation observation = TemperatureSensorBlock.observe(helper.getLevel(), world);
            var ambient = RedstoneEngineering.TEMPERATURE_SENSOR.get()
                    .engineeringSnapshot(helper.getLevel(), world, state, Direction.NORTH).orElse(null);
            if (!observation.complete()
                    || ambient == null
                    || ambient.quality() != PortQuality.VALID) {
                helper.fail("Complete thermal coverage must recover to VALID ambient evidence", sensorPos);
                return;
            }
            helper.succeed();
        });
    }
}
