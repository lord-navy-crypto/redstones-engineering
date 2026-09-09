package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.MagneticFieldSensorBlock;
import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Measurement-quality lifecycle for free-space magnetic sensing. */
public final class RseMagneticMeasurementSystemGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseMagneticMeasurementSystemGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void magneticSensorAwaitsFirstSampleAsStaleThenEstablishesValidZero(GameTestHelper helper) {
        BlockPos sensor = new BlockPos(2, 1, 2);
        helper.setBlock(sensor, RedstoneEngineering.MAGNETIC_FIELD_SENSOR.get().defaultBlockState());

        BlockPos world = helper.absolutePos(sensor);
        MagneticFieldSensorBlock.Observation initial = MagneticFieldSensorBlock.observation(
                helper.getLevel(), world, helper.getBlockState(sensor));
        PortQuality initialQuality = RedstoneEngineering.MAGNETIC_FIELD_SENSOR.get()
                .engineeringSnapshot(helper.getLevel(), world, helper.getBlockState(sensor), Direction.NORTH)
                .orElseThrow().quality();

        if (initial.initialized() || initial.complete()
                || initial.field() != 0
                || initialQuality != PortQuality.STALE) {
            helper.fail("Magnetic sensor first-sample state must be STALE, not NO_SIGNAL", sensor);
            return;
        }

        helper.runAfterDelay(3, () -> {
            MagneticFieldSensorBlock.Observation sampled = MagneticFieldSensorBlock.observation(
                    helper.getLevel(), world, helper.getBlockState(sensor));
            var snapshot = RedstoneEngineering.MAGNETIC_FIELD_SENSOR.get()
                    .engineeringSnapshot(helper.getLevel(), world, helper.getBlockState(sensor), Direction.NORTH)
                    .orElseThrow();
            if (!sampled.initialized() || !sampled.complete()
                    || sampled.field() != 0
                    || snapshot.quality() != PortQuality.VALID
                    || Math.round(snapshot.value()) != 0) {
                helper.fail("Magnetic sensor did not transition from STALE to a complete VALID zero sample", sensor);
                return;
            }
            helper.succeed();
        });
    }
}
