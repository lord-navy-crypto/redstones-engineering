package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.RadioReceiverBlock;
import dev.redstoneengineering.block.RadioTransmitterBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.RadioKernel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** System-level radio contention lifecycle: collision, fail-safe clearing, physical removal, and recovery. */
public final class RseRadioMediumSystemGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseRadioMediumSystemGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void sameChannelCollisionClearsReceiverAndPhysicalRemovalRecovers(GameTestHelper helper) {
        final int channel = 2;
        BlockPos txA = new BlockPos(0, 1, 1);
        BlockPos txB = new BlockPos(0, 1, 3);
        BlockPos powerA = new BlockPos(0, 0, 1);
        BlockPos powerB = new BlockPos(0, 0, 3);
        BlockPos receiver = new BlockPos(3, 1, 2);

        helper.setBlock(powerA, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(txA, RedstoneEngineering.RADIO_TRANSMITTER.get().defaultBlockState()
                .setValue(RadioTransmitterBlock.CHANNEL, channel));
        helper.setBlock(receiver, RedstoneEngineering.RADIO_RECEIVER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(RadioReceiverBlock.CHANNEL, channel));

        BlockPos rxWorld = helper.absolutePos(receiver);
        helper.runAfterDelay(8, () -> {
            RadioKernel.Reception single = RadioKernel.receivePacket(helper.getLevel(), rxWorld, channel);
            if (!single.valid() || single.drivers() != 1 || single.collision()
                    || single.value() != 15
                    || helper.getBlockState(receiver).getValue(DirectionalSignalBlock.OUTPUT) != 15) {
                helper.fail("Radio baseline did not establish one valid transmitter and decoded payload", receiver);
                return;
            }

            helper.setBlock(powerB, Blocks.REDSTONE_BLOCK.defaultBlockState());
            helper.setBlock(txB, RedstoneEngineering.RADIO_TRANSMITTER.get().defaultBlockState()
                    .setValue(RadioTransmitterBlock.CHANNEL, channel));
            helper.runAfterDelay(8, () -> {
                RadioKernel.Reception collision = RadioKernel.receivePacket(helper.getLevel(), rxWorld, channel);
                PortQuality collisionQuality = RedstoneEngineering.RADIO_RECEIVER.get()
                        .engineeringSnapshot(helper.getLevel(), rxWorld, helper.getBlockState(receiver), Direction.UP)
                        .orElseThrow().quality();
                if (collision.drivers() != 2 || !collision.collision() || collision.valid()
                        || collision.value() != 0
                        || collisionQuality != PortQuality.TOPOLOGY_ERROR
                        || helper.getBlockState(receiver).getValue(DirectionalSignalBlock.OUTPUT) != 0) {
                    helper.fail("Radio collision was not diagnosed and fail-safe cleared at the receiver", receiver);
                    return;
                }

                helper.setBlock(txB, Blocks.AIR.defaultBlockState());
                helper.runAfterDelay(8, () -> {
                    RadioKernel.Reception recovered = RadioKernel.receivePacket(helper.getLevel(), rxWorld, channel);
                    PortQuality recoveredQuality = RedstoneEngineering.RADIO_RECEIVER.get()
                            .engineeringSnapshot(helper.getLevel(), rxWorld, helper.getBlockState(receiver), Direction.UP)
                            .orElseThrow().quality();
                    if (!recovered.valid() || recovered.drivers() != 1 || recovered.collision()
                            || recovered.value() != 15
                            || recoveredQuality != PortQuality.VALID
                            || helper.getBlockState(receiver).getValue(DirectionalSignalBlock.OUTPUT) != 15) {
                        helper.fail("Radio receiver did not recover after the conflicting transmitter was physically removed", receiver);
                        return;
                    }
                    helper.succeed();
                });
            });
        });
    }
}
