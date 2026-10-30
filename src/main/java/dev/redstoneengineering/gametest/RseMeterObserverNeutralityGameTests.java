package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.CopperCircuitMeterBlock;
import dev.redstoneengineering.block.OpticalPowerMeterBlock;
import dev.redstoneengineering.block.SignalAnalyzerBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Snapshot reads are observations, not writes or active scan commands. */
public final class RseMeterObserverNeutralityGameTests {
    private static final String TEMPLATE = "empty5x4x5";
    private RseMeterObserverNeutralityGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void analyzerUiSnapshotDoesNotInventSampleHistory(GameTestHelper helper) {
        BlockPos loc = new BlockPos(2, 1, 2);
        BlockState analyzer = RedstoneEngineering.SIGNAL_ANALYZER.get().defaultBlockState()
                .setValue(SignalAnalyzerBlock.FACING, Direction.EAST);
        helper.setBlock(loc, analyzer);
        BlockPos world = helper.absolutePos(loc);
        int entriesBefore = RuntimeIntStore.entryCount(helper.getLevel());
        var snapshot = SignalAnalyzerBlock.uiSnapshot(helper.getLevel(), world);
        if (RuntimeIntStore.entryCount(helper.getLevel()) != entriesBefore
                || snapshot.totalSamples() != 0 || snapshot.windowCount() != 0
                || snapshot.measurementQuality() != PortQuality.NO_SIGNAL) {
            helper.fail("Opening analyzer history allocated runtime or fabricated an observed sample", loc);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void opticalAndCopperMetersRejectEmptyMeasurementTargets(GameTestHelper helper) {
        BlockPos optical = new BlockPos(1, 1, 2);
        BlockPos copper = new BlockPos(3, 1, 2);
        helper.setBlock(optical, RedstoneEngineering.OPTICAL_POWER_METER.get().defaultBlockState()
                .setValue(OpticalPowerMeterBlock.FACING, Direction.NORTH));
        helper.setBlock(copper, RedstoneEngineering.COPPER_CIRCUIT_METER.get().defaultBlockState()
                .setValue(CopperCircuitMeterBlock.FACING, Direction.NORTH));
        var opticalResult = OpticalPowerMeterBlock.measurement(
                helper.getLevel(), helper.absolutePos(optical), helper.getBlockState(optical));
        var copperResult = CopperCircuitMeterBlock.targetObservation(
                helper.getLevel(), helper.absolutePos(copper), helper.getBlockState(copper));
        if (opticalResult.quality() != PortQuality.NO_SIGNAL
                || opticalResult.intensity() != 0
                || copperResult.quality() != PortQuality.NO_SIGNAL
                || copperResult.voltage() != 0) {
            helper.fail("Unconnected meters fabricated a valid physical source", optical);
            return;
        }
        helper.succeed();
    }
}
