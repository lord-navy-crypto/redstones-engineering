package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** System regressions for optical measurement coverage quality. */
public final class RseOpticalMeasurementSystemGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseOpticalMeasurementSystemGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void opticalTransducerDistinguishesUnknownApertureFromLoadedNoSignal(GameTestHelper helper) {
        BlockState transducerState = RedstoneEngineering.LAPIS_OPTICAL_TRANSDUCER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.NORTH);

        // FACING NORTH means the measurement input is SOUTH. A truly unloaded aperture is unknown evidence.
        BlockPos remote = helper.absolutePos(new BlockPos(2, 1, 2)).offset(1_000_000, 0, 1_000_000);
        BlockPos remoteInput = remote.south();
        if (helper.getLevel().hasChunkAt(remoteInput)) {
            helper.fail("Precondition failed: remote optical aperture unexpectedly loaded");
            return;
        }
        var unknown = RedstoneEngineering.LAPIS_OPTICAL_TRANSDUCER.get()
                .engineeringSnapshot(helper.getLevel(), remote, transducerState, Direction.SOUTH).orElse(null);
        if (unknown == null || unknown.quality() != PortQuality.STALE) {
            helper.fail("Unloaded optical aperture must propagate STALE through the transducer input");
            return;
        }

        // Loaded empty space is a confirmed absence of an optical carrier, not stale evidence.
        BlockPos transducerPos = new BlockPos(2, 1, 2);
        helper.setBlock(transducerPos, transducerState);
        BlockPos world = helper.absolutePos(transducerPos);
        var loadedAir = RedstoneEngineering.LAPIS_OPTICAL_TRANSDUCER.get()
                .engineeringSnapshot(helper.getLevel(), world, helper.getBlockState(transducerPos), Direction.SOUTH).orElse(null);
        if (loadedAir == null || loadedAir.quality() != PortQuality.NO_SIGNAL) {
            helper.fail("Loaded empty optical aperture must remain NO_SIGNAL", transducerPos);
            return;
        }
        helper.succeed();
    }
}
