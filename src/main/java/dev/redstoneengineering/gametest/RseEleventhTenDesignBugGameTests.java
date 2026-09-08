package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.FreeSpaceOpticalReceiverBlock;
import dev.redstoneengineering.block.RadioReceiverBlock;
import dev.redstoneengineering.block.RadioTransmitterBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.FreeSpaceOpticsKernel;
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.RadioKernel;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.physics.SoulFluxNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Eleventh 10-block design/bug campaign: mechanical, hydro, radio, optical and Soul-Flux evidence semantics. */
public final class RseEleventhTenDesignBugGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseEleventhTenDesignBugGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void mechanicalReceiverOutputQualityTracksWaveEvidence(GameTestHelper helper) {
        BlockPos receiver = new BlockPos(2, 1, 2);
        helper.setBlock(receiver, RedstoneEngineering.MECHANICAL_VIBRATION_RECEIVER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));
        BlockPos world = helper.absolutePos(receiver);
        InformationRuntime.clear(helper.getLevel(), "mech_wave", world);

        var idleInput = RedstoneEngineering.MECHANICAL_VIBRATION_RECEIVER.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(receiver), Direction.WEST).orElseThrow();
        var idleOutput = RedstoneEngineering.MECHANICAL_VIBRATION_RECEIVER.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(receiver), Direction.EAST).orElseThrow();
        if (idleInput.quality() != PortQuality.NO_SIGNAL || idleOutput.quality() != PortQuality.NO_SIGNAL) {
            helper.fail("Mechanical receiver output claimed VALID without a vibration packet", receiver);
            return;
        }

        InformationRuntime.write(helper.getLevel(), "mech_wave", world, 5, 7, true, 80);
        var activeInput = RedstoneEngineering.MECHANICAL_VIBRATION_RECEIVER.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(receiver), Direction.WEST).orElseThrow();
        var activeOutput = RedstoneEngineering.MECHANICAL_VIBRATION_RECEIVER.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(receiver), Direction.EAST).orElseThrow();
        if (activeInput.quality() != PortQuality.VALID || activeOutput.quality() != PortQuality.VALID) {
            helper.fail("Mechanical receiver failed to propagate real wave evidence to its output quality", receiver);
            return;
        }
        InformationRuntime.clear(helper.getLevel(), "mech_wave", world);
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void hydroTubeExpiresPacketWithoutGhostEnvelope(GameTestHelper helper) {
        BlockPos tube = new BlockPos(2, 1, 2);
        helper.setBlock(tube, RedstoneEngineering.HYDROACOUSTIC_TUBE.get().defaultBlockState());
        BlockPos world = helper.absolutePos(tube);
        InformationRuntime.write(helper.getLevel(), "hydro", world, 2, 6, true, 90);
        helper.getLevel().scheduleTick(world, helper.getBlockState(tube).getBlock(), 1);

        helper.runAfterDelay(3, () -> {
            InformationRuntime.Snapshot packet = InformationRuntime.snapshot(helper.getLevel(), "hydro", world);
            var port = RedstoneEngineering.HYDROACOUSTIC_TUBE.get().engineeringSnapshot(
                    helper.getLevel(), world, helper.getBlockState(tube), Direction.NORTH).orElseThrow();
            if (packet.ageTicks() >= 0 || port.quality() != PortQuality.NO_SIGNAL) {
                helper.fail("Hydro tube left a zero/invalid ghost packet after expiry", tube);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void hydroExciterSeparatesMissingDriveFromDrivenZero(GameTestHelper helper) {
        BlockPos source = new BlockPos(2, 0, 2);
        BlockPos exciter = new BlockPos(2, 1, 2);
        helper.setBlock(exciter, RedstoneEngineering.HYDROACOUSTIC_EXCITER.get().defaultBlockState());
        BlockPos world = helper.absolutePos(exciter);

        var missing = RedstoneEngineering.HYDROACOUSTIC_EXCITER.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(exciter), Direction.DOWN).orElseThrow();
        if (missing.quality() != PortQuality.NO_SIGNAL) {
            helper.fail("Hydro exciter treated an empty drive face as valid LOW", exciter);
            return;
        }

        helper.setBlock(source, Blocks.REDSTONE_WIRE.defaultBlockState());
        var drivenZero = RedstoneEngineering.HYDROACOUSTIC_EXCITER.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(exciter), Direction.DOWN).orElseThrow();
        var hydroOut = RedstoneEngineering.HYDROACOUSTIC_EXCITER.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(exciter), Direction.UP).orElseThrow();
        if (drivenZero.value() != 0.0 || drivenZero.quality() != PortQuality.VALID
                || hydroOut.value() != 0.0 || hydroOut.quality() != PortQuality.NO_SIGNAL
                || InformationRuntime.snapshot(helper.getLevel(), "hydro_exciter", world).ageTicks() >= 0) {
            helper.fail("Hydro exciter collapsed configured LOW or fabricated a zero-amplitude wave", exciter);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void hydroReceiverClearsZeroAmplitudePacket(GameTestHelper helper) {
        BlockPos receiver = new BlockPos(2, 1, 2);
        helper.setBlock(receiver, RedstoneEngineering.HYDROACOUSTIC_RECEIVER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));
        BlockPos world = helper.absolutePos(receiver);
        InformationRuntime.write(helper.getLevel(), "hydro", world, 1, 4, true, 100);
        helper.getLevel().scheduleTick(world, helper.getBlockState(receiver).getBlock(), 1);

        helper.runAfterDelay(3, () -> {
            InformationRuntime.Snapshot packet = InformationRuntime.snapshot(helper.getLevel(), "hydro", world);
            var output = RedstoneEngineering.HYDROACOUSTIC_RECEIVER.get().engineeringSnapshot(
                    helper.getLevel(), world, helper.getBlockState(receiver), Direction.EAST).orElseThrow();
            if (packet.ageTicks() >= 0 || output.quality() != PortQuality.NO_SIGNAL || output.value() != 0.0) {
                helper.fail("Hydro receiver retained a ghost zero envelope instead of clearing the expired wave", receiver);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void radioTransmitterKeepsDrivenZeroFrameActive(GameTestHelper helper) {
        BlockPos source = new BlockPos(2, 0, 2);
        BlockPos transmitter = new BlockPos(2, 1, 2);
        helper.setBlock(transmitter, RedstoneEngineering.RADIO_TRANSMITTER.get().defaultBlockState()
                .setValue(RadioTransmitterBlock.CHANNEL, 3));
        BlockPos world = helper.absolutePos(transmitter);

        var missing = RedstoneEngineering.RADIO_TRANSMITTER.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(transmitter), Direction.UP).orElseThrow();
        if (missing.quality() != PortQuality.NO_SIGNAL) {
            helper.fail("Radio TX fabricated an antenna frame without a payload source", transmitter);
            return;
        }

        helper.setBlock(source, Blocks.REDSTONE_WIRE.defaultBlockState());
        var input = RedstoneEngineering.RADIO_TRANSMITTER.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(transmitter), Direction.DOWN).orElseThrow();
        var antenna = RedstoneEngineering.RADIO_TRANSMITTER.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(transmitter), Direction.UP).orElseThrow();
        if (input.value() != 0.0 || input.quality() != PortQuality.VALID
                || antenna.value() != 0.0 || antenna.quality() != PortQuality.VALID) {
            helper.fail("Radio TX collapsed a configured zero payload into transmitter absence", transmitter);
            return;
        }
        RadioKernel.removeTransmitter(helper.getLevel(), world);
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void radioReceiverAcceptsZeroFrameWithoutInventingOutput(GameTestHelper helper) {
        BlockPos receiver = new BlockPos(3, 1, 2);
        helper.setBlock(receiver, RedstoneEngineering.RADIO_RECEIVER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(RadioReceiverBlock.CHANNEL, 2));
        BlockPos rxWorld = helper.absolutePos(receiver);
        BlockPos txWorld = helper.absolutePos(new BlockPos(1, 1, 2));
        RadioKernel.updateTransmitter(helper.getLevel(), txWorld, 2, 0);

        RadioKernel.Reception reception = RadioKernel.receivePacket(helper.getLevel(), rxWorld, 2);
        var antenna = RedstoneEngineering.RADIO_RECEIVER.get().engineeringSnapshot(
                helper.getLevel(), rxWorld, helper.getBlockState(receiver), Direction.UP).orElseThrow();
        var output = RedstoneEngineering.RADIO_RECEIVER.get().engineeringSnapshot(
                helper.getLevel(), rxWorld, helper.getBlockState(receiver), Direction.EAST).orElseThrow();
        RadioKernel.removeTransmitter(helper.getLevel(), txWorld);
        if (!reception.valid() || reception.value() != 0
                || antenna.value() != 0.0 || antenna.quality() != PortQuality.VALID
                || output.value() != 0.0 || output.quality() != PortQuality.VALID) {
            helper.fail("Radio RX treated a valid zero frame as no transmitter or invented nonzero output", receiver);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void opticalTransmitterSeparatesMissingInputFromDrivenZero(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 2);
        BlockPos transmitter = new BlockPos(2, 1, 2);
        helper.setBlock(transmitter, RedstoneEngineering.FREE_SPACE_OPTICAL_TRANSMITTER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        BlockPos world = helper.absolutePos(transmitter);

        var missing = RedstoneEngineering.FREE_SPACE_OPTICAL_TRANSMITTER.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(transmitter), Direction.WEST).orElseThrow();
        if (missing.quality() != PortQuality.NO_SIGNAL) {
            helper.fail("Optical TX treated an empty BACK input as valid LOW", transmitter);
            return;
        }

        helper.setBlock(source, RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.EAST)
                .setValue(RedstoneReferenceSourceBlock.POWER, 0));
        var drivenZero = RedstoneEngineering.FREE_SPACE_OPTICAL_TRANSMITTER.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(transmitter), Direction.WEST).orElseThrow();
        var opticalOut = RedstoneEngineering.FREE_SPACE_OPTICAL_TRANSMITTER.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(transmitter), Direction.EAST).orElseThrow();
        if (drivenZero.value() != 0.0 || drivenZero.quality() != PortQuality.VALID
                || opticalOut.value() != 0.0 || opticalOut.quality() != PortQuality.NO_SIGNAL) {
            helper.fail("Optical TX collapsed a configured zero input or emitted zero-power light", transmitter);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void opticalReceiverReportsChannelMismatch(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 2);
        BlockPos receiver = new BlockPos(3, 1, 2);
        helper.setBlock(receiver, RedstoneEngineering.FREE_SPACE_OPTICAL_RECEIVER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(FreeSpaceOpticalReceiverBlock.CHANNEL, 0));
        BlockPos rxWorld = helper.absolutePos(receiver);
        FreeSpaceOpticsKernel.emit(helper.getLevel(), helper.absolutePos(source), Direction.EAST, 8, 1);

        var input = RedstoneEngineering.FREE_SPACE_OPTICAL_RECEIVER.get().engineeringSnapshot(
                helper.getLevel(), rxWorld, helper.getBlockState(receiver), Direction.WEST).orElseThrow();
        var output = RedstoneEngineering.FREE_SPACE_OPTICAL_RECEIVER.get().engineeringSnapshot(
                helper.getLevel(), rxWorld, helper.getBlockState(receiver), Direction.EAST).orElseThrow();
        InformationRuntime.clear(helper.getLevel(), "free_optical", rxWorld);
        if (input.value() <= 0.0 || input.quality() != PortQuality.DOMAIN_MISMATCH
                || output.value() != 0.0 || output.quality() != PortQuality.DOMAIN_MISMATCH) {
            helper.fail("Optical RX failed to expose an aligned wrong-channel beam as mismatch evidence", receiver);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void soulConduitDecayClearsZeroPacket(GameTestHelper helper) {
        BlockPos conduit = new BlockPos(2, 1, 2);
        helper.setBlock(conduit, RedstoneEngineering.SOUL_SOIL_CONDUIT.get().defaultBlockState());
        BlockPos world = helper.absolutePos(conduit);
        SoulFluxNetwork.inject(helper.getLevel(), world, 1);
        InformationRuntime.Snapshot before = SoulFluxNetwork.chargeSnapshot(helper.getLevel(), world);
        if (!before.valid() || before.value() != 1) {
            helper.fail("Soul conduit did not receive the injected transient packet", conduit);
            return;
        }

        SoulFluxNetwork.decay(helper.getLevel(), world);
        InformationRuntime.Snapshot after = SoulFluxNetwork.chargeSnapshot(helper.getLevel(), world);
        var port = RedstoneEngineering.SOUL_SOIL_CONDUIT.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(conduit), Direction.NORTH).orElseThrow();
        if (after.ageTicks() >= 0 || port.quality() != PortQuality.NO_SIGNAL) {
            helper.fail("Soul conduit retained valid-zero transient runtime after its packet decayed away", conduit);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void soulReservoirInitializesValidEmptyStorage(GameTestHelper helper) {
        BlockPos reservoir = new BlockPos(2, 1, 2);
        helper.setBlock(reservoir, RedstoneEngineering.SOUL_SAND_RESERVOIR.get().defaultBlockState());
        BlockPos world = helper.absolutePos(reservoir);
        InformationRuntime.Snapshot stored = SoulFluxNetwork.chargeSnapshot(helper.getLevel(), world);
        int entriesBeforeInspection = RuntimeIntStore.entryCount(helper.getLevel());
        var port = RedstoneEngineering.SOUL_SAND_RESERVOIR.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(reservoir), Direction.NORTH).orElseThrow();
        int entriesAfterInspection = RuntimeIntStore.entryCount(helper.getLevel());
        SoulFluxNetwork.decay(helper.getLevel(), world);
        InformationRuntime.Snapshot afterDecay = SoulFluxNetwork.chargeSnapshot(helper.getLevel(), world);

        if (!stored.valid() || stored.value() != 0 || stored.ageTicks() < 0
                || port.value() != 0.0 || port.quality() != PortQuality.VALID
                || entriesAfterInspection != entriesBeforeInspection
                || !afterDecay.valid() || afterDecay.value() != 0) {
            helper.fail("Soul reservoir failed to represent known empty storage as observer-neutral VALID zero", reservoir);
            return;
        }
        helper.succeed();
    }
}
