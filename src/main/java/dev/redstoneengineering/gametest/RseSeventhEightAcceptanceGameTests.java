package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.HydroacousticExciterBlock;
import dev.redstoneengineering.block.HydroacousticTubeBlock;
import dev.redstoneengineering.physics.HydroacousticNetwork;
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.ThermalPulseKernel;
import dev.redstoneengineering.physics.VibrationNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Seventh block-by-block acceptance campaign: damping, hydroacoustics and phonon-thermal signalling. */
public final class RseSeventhEightAcceptanceGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseSeventhEightAcceptanceGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 70)
    public static void honeyVibrationDamperAppliesHighLoss(GameTestHelper helper) {
        BlockPos damper = new BlockPos(2, 1, 2);
        BlockPos receiver = new BlockPos(3, 1, 2);
        helper.setBlock(damper, RedstoneEngineering.HONEY_VIBRATION_DAMPER.get().defaultBlockState());
        helper.setBlock(receiver, RedstoneEngineering.MECHANICAL_VIBRATION_RECEIVER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));

        VibrationNetwork.propagate(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 2)), 10, 5, Direction.EAST);
        int damperAmplitude = VibrationNetwork.sample(helper.getLevel(), helper.absolutePos(damper)).amplitude();
        int receiverAmplitude = VibrationNetwork.sample(helper.getLevel(), helper.absolutePos(receiver)).amplitude();
        if (damperAmplitude != 10 || receiverAmplitude != 6) {
            helper.fail("Honey damper did not apply the expected four-amplitude propagation loss", damper);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 70)
    public static void sculkVibrationInterfaceCapturesDirectionalEventCode(GameTestHelper helper) {
        BlockPos device = new BlockPos(2, 1, 2);
        BlockPos input = new BlockPos(1, 1, 2);
        helper.setBlock(device, RedstoneEngineering.SCULK_VIBRATION_INTERFACE.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));
        helper.setBlock(input, Blocks.REDSTONE_BLOCK.defaultBlockState());

        helper.runAfterDelay(3, () -> {
            var block = RedstoneEngineering.SCULK_VIBRATION_INTERFACE.get();
            if (helper.getBlockState(device).getValue(DirectionalSignalBlock.OUTPUT) != 15
                    || block.eventCount(helper.getLevel(), helper.absolutePos(device)) < 1
                    || block.lastEventCode(helper.getLevel(), helper.absolutePos(device)) != 15) {
                helper.fail("Sculk interface failed directional event-code capture", device);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 70)
    public static void hydroacousticTubePropagatesWithMediumLoss(GameTestHelper helper) {
        BlockPos tube = new BlockPos(2, 1, 2);
        BlockPos receiver = new BlockPos(3, 1, 2);
        helper.setBlock(tube, RedstoneEngineering.HYDROACOUSTIC_TUBE.get().defaultBlockState()
                .setValue(HydroacousticTubeBlock.MEDIUM, 0));
        helper.setBlock(receiver, RedstoneEngineering.HYDROACOUSTIC_RECEIVER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));

        HydroacousticNetwork.propagate(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 2)), 10, 7, Direction.EAST);
        int tubeAmplitude = InformationRuntime.value(helper.getLevel(), "hydro", helper.absolutePos(tube));
        int receiverAmplitude = InformationRuntime.value(helper.getLevel(), "hydro", helper.absolutePos(receiver));
        if (tubeAmplitude != 10 || receiverAmplitude != 9
                || InformationRuntime.aux(helper.getLevel(), "hydro", helper.absolutePos(receiver)) != 7) {
            helper.fail("Water hydroacoustic tube did not propagate with one-amplitude loss", tube);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void hydroacousticExciterUsesDownDrive(GameTestHelper helper) {
        BlockPos exciter = new BlockPos(2, 1, 2);
        BlockPos tube = new BlockPos(3, 1, 2);
        BlockPos sidePower = new BlockPos(1, 1, 2);
        BlockPos downPower = new BlockPos(2, 0, 2);
        helper.setBlock(tube, RedstoneEngineering.HYDROACOUSTIC_TUBE.get().defaultBlockState());
        helper.setBlock(exciter, RedstoneEngineering.HYDROACOUSTIC_EXCITER.get().defaultBlockState()
                .setValue(HydroacousticExciterBlock.FREQUENCY, 6));
        helper.setBlock(sidePower, Blocks.REDSTONE_BLOCK.defaultBlockState());

        helper.runAfterDelay(2, () -> {
            if (InformationRuntime.valid(helper.getLevel(), "hydro", helper.absolutePos(tube))) {
                helper.fail("Side redstone incorrectly drove a DOWN-input hydroacoustic exciter", exciter);
                return;
            }
            helper.setBlock(downPower, Blocks.REDSTONE_BLOCK.defaultBlockState());
            helper.runAfterDelay(2, () -> {
                if (!InformationRuntime.valid(helper.getLevel(), "hydro", helper.absolutePos(tube))
                        || InformationRuntime.aux(helper.getLevel(), "hydro", helper.absolutePos(tube)) != 6) {
                    helper.fail("DOWN-powered hydroacoustic exciter did not publish f=6", tube);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 70)
    public static void hydroacousticReceiverRejectsWrongSide(GameTestHelper helper) {
        BlockPos receiver = new BlockPos(2, 1, 2);
        helper.setBlock(receiver, RedstoneEngineering.HYDROACOUSTIC_RECEIVER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));

        HydroacousticNetwork.propagate(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 2)), 10, 4, Direction.WEST);
        if (InformationRuntime.valid(helper.getLevel(), "hydro", helper.absolutePos(receiver))) {
            helper.fail("Hydroacoustic receiver accepted a FRONT-side arrival", receiver);
            return;
        }
        HydroacousticNetwork.propagate(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 2)), 10, 4, Direction.EAST);
        if (!InformationRuntime.valid(helper.getLevel(), "hydro", helper.absolutePos(receiver))) {
            helper.fail("Hydroacoustic receiver rejected its WEST/BACK input", receiver);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void phononConduitCarriesAndExpiresThermalPulse(GameTestHelper helper) {
        BlockPos conduit = new BlockPos(2, 1, 2);
        BlockPos receiver = new BlockPos(3, 1, 2);
        helper.setBlock(conduit, RedstoneEngineering.PHONON_CONDUIT.get().defaultBlockState());
        helper.setBlock(receiver, RedstoneEngineering.THERMAL_PULSE_RECEIVER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));
        ThermalPulseKernel.send(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 2)), 10, Direction.EAST);

        if (InformationRuntime.value(helper.getLevel(), "thermal_pulse", helper.absolutePos(conduit)) != 10
                || InformationRuntime.value(helper.getLevel(), "thermal_pulse", helper.absolutePos(receiver)) != 9) {
            helper.fail("Phonon conduit did not carry the bounded thermal pulse", conduit);
            return;
        }
        helper.runAfterDelay(56, () -> {
            if (InformationRuntime.valid(helper.getLevel(), "thermal_pulse", helper.absolutePos(conduit))) {
                helper.fail("Phonon conduit retained a ghost pulse after bounded decay", conduit);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void thermalPulseEncoderUsesDownDrive(GameTestHelper helper) {
        BlockPos encoder = new BlockPos(2, 1, 2);
        BlockPos conduit = new BlockPos(3, 1, 2);
        BlockPos sidePower = new BlockPos(1, 1, 2);
        BlockPos downPower = new BlockPos(2, 0, 2);
        helper.setBlock(conduit, RedstoneEngineering.PHONON_CONDUIT.get().defaultBlockState());
        helper.setBlock(encoder, RedstoneEngineering.THERMAL_PULSE_ENCODER.get().defaultBlockState());
        helper.setBlock(sidePower, Blocks.REDSTONE_BLOCK.defaultBlockState());

        helper.runAfterDelay(2, () -> {
            if (InformationRuntime.valid(helper.getLevel(), "thermal_pulse", helper.absolutePos(conduit))) {
                helper.fail("Side redstone incorrectly drove a DOWN-input thermal pulse encoder", encoder);
                return;
            }
            helper.setBlock(downPower, Blocks.REDSTONE_BLOCK.defaultBlockState());
            helper.runAfterDelay(2, () -> {
                if (!InformationRuntime.valid(helper.getLevel(), "thermal_pulse", helper.absolutePos(conduit))
                        || InformationRuntime.value(helper.getLevel(), "thermal_pulse", helper.absolutePos(conduit)) <= 0) {
                    helper.fail("DOWN-powered thermal pulse encoder did not publish into phonon conduit", conduit);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 70)
    public static void thermalPulseReceiverRejectsWrongSide(GameTestHelper helper) {
        BlockPos receiver = new BlockPos(2, 1, 2);
        helper.setBlock(receiver, RedstoneEngineering.THERMAL_PULSE_RECEIVER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));

        ThermalPulseKernel.send(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 2)), 10, Direction.WEST);
        if (InformationRuntime.valid(helper.getLevel(), "thermal_pulse", helper.absolutePos(receiver))) {
            helper.fail("Thermal pulse receiver accepted a FRONT-side arrival", receiver);
            return;
        }
        ThermalPulseKernel.send(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 2)), 10, Direction.EAST);
        if (!InformationRuntime.valid(helper.getLevel(), "thermal_pulse", helper.absolutePos(receiver))) {
            helper.fail("Thermal pulse receiver rejected its WEST/BACK input", receiver);
            return;
        }
        helper.succeed();
    }
}
