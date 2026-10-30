package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DeserializerBlock;
import dev.redstoneengineering.block.DifferentialDriverBlock;
import dev.redstoneengineering.block.SerializerBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.InformationRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A transient packet is never evidence of a current physical driver.
 * Tests exercise scheduled watchdog withdrawal after the input disappears.
 */
public final class RseDigitalEndpointWatchdogGameTests {
    private static final String TEMPLATE = "empty5x4x5";
    private RseDigitalEndpointWatchdogGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void serializerWatchdogRevokesOrphanedFrame(GameTestHelper helper) {
        BlockPos local = new BlockPos(2, 1, 2);
        BlockPos world = helper.absolutePos(local);
        BlockState state = RedstoneEngineering.SERIALIZER.get().defaultBlockState();
        helper.setBlock(local, state);
        // A previous, correctly received byte is no longer backed by any bus.
        InformationRuntime.write(helper.getLevel(), "serial", world, 173, 8, true, 100);
        helper.runAfterDelay(18, () -> {
            var packet = InformationRuntime.snapshot(helper.getLevel(), "serial", world);
            var shown = RedstoneEngineering.SERIALIZER.get().engineeringSnapshot(
                    helper.getLevel(), world, helper.getBlockState(local), state.getValue(SerializerBlock.FACING))
                    .orElseThrow();
            if (packet.valid() || packet.value() != 0
                    || shown.value() != 0.0 || shown.quality() == PortQuality.VALID) {
                helper.fail("Serializer replayed a stale byte after the input bus disappeared", local);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void deserializerWatchdogWithdrawsOldBusDriver(GameTestHelper helper) {
        BlockPos local = new BlockPos(2, 1, 2);
        BlockPos world = helper.absolutePos(local);
        BlockState state = RedstoneEngineering.DESERIALIZER.get().defaultBlockState();
        helper.setBlock(local, state);
        InformationRuntime.write(helper.getLevel(), "bus8_out", world, 219, 0, true, 100);
        helper.runAfterDelay(18, () -> {
            var packet = InformationRuntime.snapshot(helper.getLevel(), "bus8_out", world);
            var shown = RedstoneEngineering.DESERIALIZER.get().engineeringSnapshot(
                    helper.getLevel(), world, helper.getBlockState(local), state.getValue(DeserializerBlock.FACING))
                    .orElseThrow();
            if (packet.valid() || packet.value() != 0
                    || shown.value() != 0.0 || shown.quality() == PortQuality.VALID) {
                helper.fail("Deserializer retained invalid byte as a bus driver", local);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void differentialDriverRechecksLostSource(GameTestHelper helper) {
        BlockPos local = new BlockPos(2, 1, 2);
        BlockPos world = helper.absolutePos(local);
        BlockState state = RedstoneEngineering.DIFFERENTIAL_DRIVER.get().defaultBlockState();
        helper.setBlock(local, state);
        InformationRuntime.write(helper.getLevel(), "diff_out", world, 1, 0, true, 100);
        helper.runAfterDelay(18, () -> {
            var packet = InformationRuntime.snapshot(helper.getLevel(), "diff_out", world);
            var shown = RedstoneEngineering.DIFFERENTIAL_DRIVER.get().engineeringSnapshot(
                    helper.getLevel(), world, helper.getBlockState(local), state.getValue(DifferentialDriverBlock.FACING))
                    .orElseThrow();
            if (packet.valid() || packet.value() != 0
                    || shown.value() != 0.0 || shown.quality() == PortQuality.VALID) {
                helper.fail("Differential driver retained a high bit without an upstream input", local);
                return;
            }
            helper.succeed();
        });
    }
}
