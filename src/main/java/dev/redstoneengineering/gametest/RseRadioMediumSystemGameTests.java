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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** System-level radio contention and coverage lifecycles. */
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

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 120)
    public static void unloadedRegisteredTransmitterIsStaleThenLoadedTransmitterRecovers(GameTestHelper helper) {
        final int channel = 3;
        ServerLevel level = helper.getLevel();
        BlockPos remoteRx = findRemoteUnloadedReceiver(level, helper.absolutePos(new BlockPos(2, 1, 2)));
        if (remoteRx == null) {
            helper.fail("Precondition failed: could not find an unloaded remote chunk for radio coverage fixture");
            return;
        }

        // Stage 1: synchronously load only the receiver position and take the coverage assertion in this
        // same tick. This isolates the production question from delayed chunk-ticket/ticking behavior.
        level.getChunkAt(remoteRx);
        BlockPos staleTx = findUnloadedCandidate(level, remoteRx);
        if (staleTx == null) {
            helper.fail("Precondition failed: loading the remote receiver chunk also loaded every Tx candidate within range");
            return;
        }

        level.setBlock(remoteRx, RedstoneEngineering.RADIO_RECEIVER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(RadioReceiverBlock.CHANNEL, channel), Block.UPDATE_CLIENTS);

        // Model the reachable lifecycle hazard: registry evidence survives while the transmitter chunk is
        // unavailable. Unknown transmitter/LOS coverage must never become an authoritative decoded frame.
        RadioKernel.updateTransmitter(level, staleTx, channel, 15);
        if (!level.hasChunkAt(remoteRx)) {
            RadioKernel.removeTransmitter(level, staleTx);
            helper.fail("Precondition failed: remote receiver chunk was unavailable during same-tick coverage assertion");
            return;
        }
        if (level.hasChunkAt(staleTx)) {
            RadioKernel.removeTransmitter(level, staleTx);
            level.setBlock(remoteRx, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            helper.fail("Precondition failed: stale transmitter chunk was already loaded during same-tick coverage assertion");
            return;
        }

        PortQuality staleQuality = RedstoneEngineering.RADIO_RECEIVER.get()
                .engineeringSnapshot(level, remoteRx, level.getBlockState(remoteRx), Direction.UP)
                .orElseThrow().quality();
        int staleOutput = level.getBlockState(remoteRx).getValue(DirectionalSignalBlock.OUTPUT);
        RadioKernel.Reception staleReception = RadioKernel.receivePacket(level, remoteRx, channel);

        if (staleQuality != PortQuality.STALE
                || staleOutput != 0
                || staleReception.coverageComplete()
                || staleReception.valid()
                || staleReception.value() != 0) {
            RadioKernel.removeTransmitter(level, staleTx);
            level.setBlock(remoteRx, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            helper.fail("Unloaded registered transmitter was trusted as definitive radio evidence instead of STALE"
                    + " | quality=" + staleQuality
                    + " output=" + staleOutput
                    + " coverageComplete=" + staleReception.coverageComplete()
                    + " valid=" + staleReception.valid()
                    + " value=" + staleReception.value()
                    + " drivers=" + staleReception.drivers()
                    + " linkQuality=" + staleReception.quality());
            return;
        }

        // Remove all remote evidence before recovery. The recovery stage deliberately uses the normal
        // in-template loaded/ticking environment so OUTPUT must be produced by RadioReceiverBlock.tick(),
        // not by a test write and not by special forced-chunk scheduling behavior.
        RadioKernel.removeTransmitter(level, staleTx);
        level.setBlock(remoteRx, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);

        BlockPos loadedTx = new BlockPos(0, 1, 2);
        BlockPos loadedPower = new BlockPos(0, 0, 2);
        BlockPos loadedRx = new BlockPos(3, 1, 2);
        helper.setBlock(loadedPower, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(loadedTx, RedstoneEngineering.RADIO_TRANSMITTER.get().defaultBlockState()
                .setValue(RadioTransmitterBlock.CHANNEL, channel));
        helper.setBlock(loadedRx, RedstoneEngineering.RADIO_RECEIVER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(RadioReceiverBlock.CHANNEL, channel));

        BlockPos loadedRxWorld = helper.absolutePos(loadedRx);
        helper.runAfterDelay(8, () -> {
            PortQuality recoveredQuality = RedstoneEngineering.RADIO_RECEIVER.get()
                    .engineeringSnapshot(level, loadedRxWorld, helper.getBlockState(loadedRx), Direction.UP)
                    .orElseThrow().quality();
            RadioKernel.Reception recovered = RadioKernel.receivePacket(level, loadedRxWorld, channel);
            int recoveredOutput = helper.getBlockState(loadedRx).getValue(DirectionalSignalBlock.OUTPUT);

            if (recoveredQuality != PortQuality.VALID
                    || !recovered.coverageComplete()
                    || !recovered.valid()
                    || recovered.value() != 15
                    || recoveredOutput != 15) {
                helper.fail("Radio did not recover through a fully loaded transmitter/receiver path after STALE coverage"
                        + " | quality=" + recoveredQuality
                        + " coverageComplete=" + recovered.coverageComplete()
                        + " valid=" + recovered.valid()
                        + " value=" + recovered.value()
                        + " output=" + recoveredOutput, loadedRx);
                return;
            }
            helper.succeed();
        });
    }

    private static BlockPos findRemoteUnloadedReceiver(ServerLevel level, BlockPos origin) {
        for (int chunks = 16; chunks <= 96; chunks += 8) {
            BlockPos[] candidates = {
                    origin.offset(chunks * 16, 0, 0),
                    origin.offset(-chunks * 16, 0, 0),
                    origin.offset(0, 0, chunks * 16),
                    origin.offset(0, 0, -chunks * 16)
            };
            for (BlockPos candidate : candidates) {
                BlockPos centered = new BlockPos((candidate.getX() & ~15) + 8, origin.getY(), (candidate.getZ() & ~15) + 8);
                if (!level.hasChunkAt(centered)) return centered;
            }
        }
        return null;
    }

    private static BlockPos findUnloadedCandidate(ServerLevel level, BlockPos receiver) {
        Direction[] directions = {Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH};
        for (int distance = RadioKernel.RANGE; distance >= 16; distance--) {
            for (Direction direction : directions) {
                BlockPos candidate = receiver.relative(direction, distance);
                if (!level.hasChunkAt(candidate)) return candidate;
            }
        }
        return null;
    }
}
