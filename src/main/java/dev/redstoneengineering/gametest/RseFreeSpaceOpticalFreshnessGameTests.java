package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.FreeSpaceOpticalChannelSupport;
import dev.redstoneengineering.block.FreeSpaceOpticalReceiverBlock;
import dev.redstoneengineering.block.FreeSpaceOpticalTransmitterBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.InformationRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-world witnesses for packet freshness and channel-switch fail-closed behavior. */
public final class RseFreeSpaceOpticalFreshnessGameTests {
    private static final String TEMPLATE = "empty5x4x5";
    private RseFreeSpaceOpticalFreshnessGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void receiverTimeoutCannotBeExtendedByOwnTicks(GameTestHelper helper) {
        BlockPos local = new BlockPos(2, 1, 2);
        BlockPos world = helper.absolutePos(local);
        BlockState state = RedstoneEngineering.FREE_SPACE_OPTICAL_RECEIVER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(FreeSpaceOpticalReceiverBlock.CHANNEL, 1)
                .setValue(DirectionalSignalBlock.OUTPUT, 12);
        helper.setBlock(local, state);
        InformationRuntime.write(helper.getLevel(), "free_optical", world, 12, 1, true, 90);

        var fresh = RedstoneEngineering.FREE_SPACE_OPTICAL_RECEIVER.get()
                .engineeringSnapshot(helper.getLevel(), world, helper.getBlockState(local), Direction.WEST)
                .orElseThrow();
        if (fresh.quality() != PortQuality.VALID) {
            helper.fail("Fresh packet should start VALID", local);
            return;
        }
        helper.runAfterDelay(FreeSpaceOpticalChannelSupport.FRESH_TICKS + 4, () -> {
            var now = RedstoneEngineering.FREE_SPACE_OPTICAL_RECEIVER.get()
                    .engineeringSnapshot(helper.getLevel(), world, helper.getBlockState(local), Direction.WEST)
                    .orElseThrow();
            int physical = helper.getBlockState(local).getValue(DirectionalSignalBlock.OUTPUT);
            int age = InformationRuntime.ageTicks(helper.getLevel(), "free_optical", world);
            if (now.quality() != PortQuality.STALE || now.value() != 0.0
                    || physical != 0 || age <= FreeSpaceOpticalChannelSupport.FRESH_TICKS) {
                helper.fail("RX maintenance refreshes missing-source packet or leaves output energized", local);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void receiverChannelRoundTripCannotResurrectOldPacket(GameTestHelper helper) {
        BlockPos local = new BlockPos(2, 1, 2);
        BlockPos world = helper.absolutePos(local);
        BlockState state = RedstoneEngineering.FREE_SPACE_OPTICAL_RECEIVER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(FreeSpaceOpticalReceiverBlock.CHANNEL, 0)
                .setValue(DirectionalSignalBlock.OUTPUT, 9);
        helper.setBlock(local, state);
        InformationRuntime.write(helper.getLevel(), "free_optical", world, 9, 0, true, 100);

        if (!FreeSpaceOpticalReceiverBlock.setChannel(helper.getLevel(), world, 1)
                || !FreeSpaceOpticalReceiverBlock.setChannel(helper.getLevel(), world, 0)) {
            helper.fail("Channel configuration was unexpectedly rejected", local);
            return;
        }
        var result = RedstoneEngineering.FREE_SPACE_OPTICAL_RECEIVER.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(local), Direction.WEST).orElseThrow();
        if (result.quality() != PortQuality.NO_SIGNAL || result.value() != 0.0
                || helper.getBlockState(local).getValue(DirectionalSignalBlock.OUTPUT) != 0
                || InformationRuntime.ageTicks(helper.getLevel(), "free_optical", world) >= 0) {
            helper.fail("Returning to old channel resurrected an invalidated packet", local);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void receiverRouteChangeInvalidatesOldEvidence(GameTestHelper helper) {
        BlockPos local = new BlockPos(2, 1, 2);
        BlockPos world = helper.absolutePos(local);
        BlockState state = RedstoneEngineering.FREE_SPACE_OPTICAL_RECEIVER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(DirectionalSignalBlock.OUTPUT, 13);
        helper.setBlock(local, state);
        InformationRuntime.write(helper.getLevel(), "free_optical", world, 13, 0, true, 100);
        if (!DirectionalSignalBlock.rotateWholeRoute(helper.getLevel(), world, true)) {
            helper.fail("Valid optical RX route change was unexpectedly rejected", local);
            return;
        }
        FreeSpaceOpticalReceiverBlock.invalidateReception(helper.getLevel(), world);
        var updated = helper.getBlockState(local);
        var result = RedstoneEngineering.FREE_SPACE_OPTICAL_RECEIVER.get()
                .engineeringSnapshot(helper.getLevel(), world, updated,
                        DirectionalSignalBlock.seriesInputSide(updated)).orElseThrow();
        if (updated.getValue(DirectionalSignalBlock.OUTPUT) != 0
                || result.quality() != PortQuality.NO_SIGNAL
                || InformationRuntime.ageTicks(helper.getLevel(), "free_optical", world) >= 0) {
            helper.fail("Rotating the optical RX retained an old ray's optical packet/output", local);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void transmitterNoOpChannelDoesNotScheduleOrChangeBlock(GameTestHelper helper) {
        BlockPos local = new BlockPos(2, 1, 2);
        BlockPos world = helper.absolutePos(local);
        BlockState state = RedstoneEngineering.FREE_SPACE_OPTICAL_TRANSMITTER.get().defaultBlockState()
                .setValue(FreeSpaceOpticalTransmitterBlock.CHANNEL, 2);
        helper.setBlock(local, state);
        if (!FreeSpaceOpticalTransmitterBlock.setChannel(helper.getLevel(), world, 2)
                || helper.getBlockState(local).getValue(FreeSpaceOpticalTransmitterBlock.CHANNEL) != 2
                || FreeSpaceOpticalTransmitterBlock.setChannel(helper.getLevel(), world, 4)) {
            helper.fail("Unchanged channel must acknowledge no-op; unsupported channel must be rejected", local);
            return;
        }
        helper.succeed();
    }
}
