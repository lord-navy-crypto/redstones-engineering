package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.LapisPrecisionMeterBlock;
import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** System regressions for Lapis precision observation quality. */
public final class RseLapisMeasurementSystemGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseLapisMeasurementSystemGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void precisionMeterDistinguishesUnknownApertureFromLoadedNoSignal(GameTestHelper helper) {
        BlockState meterState = RedstoneEngineering.LAPIS_PRECISION_METER.get().defaultBlockState()
                .setValue(LapisPrecisionMeterBlock.FACING, Direction.NORTH);

        BlockPos remote = helper.absolutePos(new BlockPos(2, 1, 2)).offset(1_000_000, 0, 1_000_000);
        BlockPos remoteSample = remote.north();
        if (helper.getLevel().hasChunkAt(remoteSample)) {
            helper.fail("Precondition failed: remote Lapis aperture unexpectedly loaded");
            return;
        }
        var unknown = RedstoneEngineering.LAPIS_PRECISION_METER.get()
                .engineeringSnapshot(helper.getLevel(), remote, meterState, Direction.NORTH).orElse(null);
        if (unknown == null || unknown.quality() != PortQuality.STALE) {
            helper.fail("Unloaded Lapis measurement aperture must be STALE, not NO_SIGNAL");
            return;
        }

        BlockPos meterPos = new BlockPos(2, 1, 2);
        helper.setBlock(meterPos, meterState);
        BlockPos world = helper.absolutePos(meterPos);
        var loadedAir = RedstoneEngineering.LAPIS_PRECISION_METER.get()
                .engineeringSnapshot(helper.getLevel(), world, helper.getBlockState(meterPos), Direction.NORTH).orElse(null);
        if (loadedAir == null || loadedAir.quality() != PortQuality.NO_SIGNAL) {
            helper.fail("Loaded empty Lapis aperture must remain NO_SIGNAL", meterPos);
            return;
        }
        helper.succeed();
    }
}
