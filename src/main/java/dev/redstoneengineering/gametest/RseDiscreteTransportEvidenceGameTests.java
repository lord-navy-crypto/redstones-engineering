package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.HydroacousticTubeBlock;
import dev.redstoneengineering.physics.HydroacousticNetwork;
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.ThermalPulseKernel;
import dev.redstoneengineering.physics.VibrationNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Runtime behavioral evidence for Pioneer Wave 6 discrete transport models.
 *
 * <p>These tests exercise the actual server kernels rather than mirroring their source constants.
 * They protect the implemented game-domain contracts shown by the shared engineering inspector:
 * per-hop attenuation, medium ordering, bounded packet amplitude and server-retained evidence.</p>
 */
public final class RseDiscreteTransportEvidenceGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseDiscreteTransportEvidenceGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 20)
    public static void mechanicalTransportDistinguishesSlimeAndHoneyLoss(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 2);
        BlockPos slimeA = new BlockPos(2, 1, 2);
        BlockPos slimeB = new BlockPos(3, 1, 2);
        helper.setBlock(slimeA, RedstoneEngineering.SLIME_VIBRATION_CONDUIT.get().defaultBlockState());
        helper.setBlock(slimeB, RedstoneEngineering.SLIME_VIBRATION_CONDUIT.get().defaultBlockState());

        VibrationNetwork.propagate(helper.getLevel(), helper.absolutePos(source), 10, 6, Direction.EAST);
        require(helper, packet(helper, "mech_wave", slimeA).value() == 10,
                "first slime node must retain source amplitude 10");
        require(helper, packet(helper, "mech_wave", slimeB).value() == 9,
                "second slime node must receive one-hop amplitude 9");

        helper.setBlock(slimeA, RedstoneEngineering.HONEY_VIBRATION_DAMPER.get().defaultBlockState());
        helper.setBlock(slimeB, RedstoneEngineering.HONEY_VIBRATION_DAMPER.get().defaultBlockState());
        VibrationNetwork.propagate(helper.getLevel(), helper.absolutePos(source), 10, 6, Direction.EAST);
        require(helper, packet(helper, "mech_wave", slimeA).value() == 10,
                "first honey node must retain source amplitude 10");
        require(helper, packet(helper, "mech_wave", slimeB).value() == 6,
                "second honey node must receive four-loss hop amplitude 6");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 20)
    public static void hydroTransportHonorsConfiguredMediumLoss(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 2);
        BlockPos first = new BlockPos(2, 1, 2);
        BlockPos second = new BlockPos(3, 1, 2);

        int[] expected = {9, 8, 7};
        for (int medium = 0; medium < expected.length; medium++) {
            helper.setBlock(first, RedstoneEngineering.HYDROACOUSTIC_TUBE.get().defaultBlockState()
                    .setValue(HydroacousticTubeBlock.MEDIUM, medium));
            helper.setBlock(second, RedstoneEngineering.HYDROACOUSTIC_TUBE.get().defaultBlockState());
            HydroacousticNetwork.propagate(helper.getLevel(), helper.absolutePos(source), 10, 4, Direction.EAST);
            require(helper, packet(helper, "hydro", first).value() == 10,
                    "first hydro node must retain source amplitude 10 for medium " + medium);
            require(helper, packet(helper, "hydro", second).value() == expected[medium],
                    "hydro medium " + medium + " must produce next-hop amplitude " + expected[medium]);
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 20)
    public static void thermalTransportAppliesOneLossPerConduitHop(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 2);
        BlockPos first = new BlockPos(2, 1, 2);
        BlockPos second = new BlockPos(3, 1, 2);
        helper.setBlock(first, RedstoneEngineering.PHONON_CONDUIT.get().defaultBlockState());
        helper.setBlock(second, RedstoneEngineering.PHONON_CONDUIT.get().defaultBlockState());

        ThermalPulseKernel.send(helper.getLevel(), helper.absolutePos(source), 10, Direction.EAST);
        InformationRuntime.Snapshot firstPacket = packet(helper, "thermal_pulse", first);
        InformationRuntime.Snapshot secondPacket = packet(helper, "thermal_pulse", second);
        require(helper, firstPacket.valid() && firstPacket.value() == 10,
                "first phonon conduit must retain valid source amplitude 10");
        require(helper, secondPacket.valid() && secondPacket.value() == 9,
                "second phonon conduit must receive one-hop amplitude 9");
        require(helper, firstPacket.qualityPercent() == 100 && secondPacket.qualityPercent() == 100,
                "fresh thermal transport evidence must begin at 100% quality");
        helper.succeed();
    }

    private static InformationRuntime.Snapshot packet(GameTestHelper helper, String key, BlockPos relativePos) {
        return InformationRuntime.snapshot(helper.getLevel(), key, helper.absolutePos(relativePos));
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
