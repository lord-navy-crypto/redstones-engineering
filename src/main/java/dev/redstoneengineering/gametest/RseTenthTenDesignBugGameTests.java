package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.PneumaticFlowMeterBlock;
import dev.redstoneengineering.block.PneumaticProportionalValveBlock;
import dev.redstoneengineering.block.PneumaticReliefValveBlock;
import dev.redstoneengineering.block.PneumaticCylinderBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.PneumaticNetwork;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.physics.VibrationNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Tenth 10-block design/bug campaign: pneumatic evidence, observer neutrality and mechanical vibration. */
public final class RseTenthTenDesignBugGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseTenthTenDesignBugGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void pneumaticReceiverSeparatesUnknownFromSolvedZero(GameTestHelper helper) {
        BlockPos pipe = new BlockPos(1, 1, 2);
        BlockPos receiver = new BlockPos(2, 1, 2);
        helper.setBlock(receiver, RedstoneEngineering.PNEUMATIC_RECEIVER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));

        var unknownInput = RedstoneEngineering.PNEUMATIC_RECEIVER.get().engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(receiver), helper.getBlockState(receiver), Direction.WEST).orElseThrow();
        var unknownOutput = RedstoneEngineering.PNEUMATIC_RECEIVER.get().engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(receiver), helper.getBlockState(receiver), Direction.EAST).orElseThrow();
        if (unknownInput.quality() != PortQuality.STALE || unknownOutput.quality() != PortQuality.STALE) {
            helper.fail("Receiver fabricated a valid zero-pressure conversion from an unsolved BACK input", receiver);
            return;
        }

        helper.setBlock(pipe, RedstoneEngineering.PNEUMATIC_PIPE.get().defaultBlockState());
        PneumaticNetwork.recompute(helper.getLevel(), helper.absolutePos(pipe));
        var zeroInput = RedstoneEngineering.PNEUMATIC_RECEIVER.get().engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(receiver), helper.getBlockState(receiver), Direction.WEST).orElseThrow();
        var zeroOutput = RedstoneEngineering.PNEUMATIC_RECEIVER.get().engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(receiver), helper.getBlockState(receiver), Direction.EAST).orElseThrow();
        if (zeroInput.value() != 0.0 || zeroInput.quality() != PortQuality.VALID
                || zeroOutput.value() != 0.0 || zeroOutput.quality() != PortQuality.VALID) {
            helper.fail("Receiver collapsed a solved zero-pressure input into missing data", receiver);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void pneumaticValvePortsPreserveSolvedZero(GameTestHelper helper) {
        BlockPos valve = new BlockPos(2, 1, 2);
        helper.setBlock(valve, RedstoneEngineering.PNEUMATIC_VALVE.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(1, 1, 2), RedstoneEngineering.PNEUMATIC_PIPE.get().defaultBlockState());
        helper.setBlock(new BlockPos(3, 1, 2), RedstoneEngineering.PNEUMATIC_PIPE.get().defaultBlockState());
        PneumaticNetwork.recompute(helper.getLevel(), helper.absolutePos(valve));

        var back = RedstoneEngineering.PNEUMATIC_VALVE.get().engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(valve), helper.getBlockState(valve), Direction.WEST).orElseThrow();
        var front = RedstoneEngineering.PNEUMATIC_VALVE.get().engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(valve), helper.getBlockState(valve), Direction.EAST).orElseThrow();
        if (back.value() != 0.0 || front.value() != 0.0
                || back.quality() != PortQuality.VALID || front.quality() != PortQuality.VALID) {
            helper.fail("Manual valve mislabeled solved zero-pressure ports as absent", valve);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void pneumaticCheckValvePortsPreserveSolvedZero(GameTestHelper helper) {
        BlockPos valve = new BlockPos(2, 1, 2);
        helper.setBlock(valve, RedstoneEngineering.PNEUMATIC_CHECK_VALVE.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(1, 1, 2), RedstoneEngineering.PNEUMATIC_PIPE.get().defaultBlockState());
        helper.setBlock(new BlockPos(3, 1, 2), RedstoneEngineering.PNEUMATIC_PIPE.get().defaultBlockState());
        PneumaticNetwork.recompute(helper.getLevel(), helper.absolutePos(valve));

        var back = RedstoneEngineering.PNEUMATIC_CHECK_VALVE.get().engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(valve), helper.getBlockState(valve), Direction.WEST).orElseThrow();
        var front = RedstoneEngineering.PNEUMATIC_CHECK_VALVE.get().engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(valve), helper.getBlockState(valve), Direction.EAST).orElseThrow();
        if (back.value() != 0.0 || front.value() != 0.0
                || back.quality() != PortQuality.VALID || front.quality() != PortQuality.VALID) {
            helper.fail("Check valve mislabeled solved zero-pressure ports as absent", valve);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void pneumaticFlowMeterInspectionDoesNotCreateRuntimeOrSamples(GameTestHelper helper) {
        BlockPos meter = new BlockPos(2, 1, 2);
        helper.setBlock(meter, RedstoneEngineering.PNEUMATIC_FLOW_METER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        BlockPos world = helper.absolutePos(meter);
        RuntimeIntStore.remove(helper.getLevel(), "pneumatic_flow", world);
        int before = RuntimeIntStore.entryCount(helper.getLevel());

        int flow = PneumaticFlowMeterBlock.flowProxy(helper.getLevel(), world);
        int drop = PneumaticFlowMeterBlock.pressureDrop(helper.getLevel(), world);
        int inlet = PneumaticFlowMeterBlock.inletPressure(helper.getLevel(), world);
        int outlet = PneumaticFlowMeterBlock.outletPressure(helper.getLevel(), world);
        var port = RedstoneEngineering.PNEUMATIC_FLOW_METER.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(meter), Direction.WEST).orElseThrow();
        if (flow != 0 || drop != 0 || inlet != 0 || outlet != 0
                || port.quality() != PortQuality.STALE
                || RuntimeIntStore.entryCount(helper.getLevel()) != before) {
            helper.fail("Flow-meter inspection allocated runtime or fabricated an unsolved pressure sample", meter);
            return;
        }

        helper.runAfterDelay(3, () -> {
            if (PneumaticFlowMeterBlock.measurement(helper.getLevel(), world).sampleCount() != 0) {
                helper.fail("Isolated flow meter sampled missing pneumatic evidence as a real zero flow", meter);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void proportionalValveSeparatesMissingCommandFromDrivenZero(GameTestHelper helper) {
        BlockPos source = new BlockPos(2, 2, 2);
        BlockPos valve = new BlockPos(2, 1, 2);
        helper.setBlock(valve, RedstoneEngineering.PNEUMATIC_PROPORTIONAL_VALVE.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));

        var missing = RedstoneEngineering.PNEUMATIC_PROPORTIONAL_VALVE.get().engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(valve), helper.getBlockState(valve), Direction.UP).orElseThrow();
        if (missing.quality() != PortQuality.NO_SIGNAL || PneumaticProportionalValveBlock.opening(helper.getLevel(), helper.absolutePos(valve)) != 0) {
            helper.fail("Proportional valve treated an empty command face as a valid zero source", valve);
            return;
        }

        helper.setBlock(source, Blocks.REDSTONE_WIRE.defaultBlockState());
        var drivenZero = RedstoneEngineering.PNEUMATIC_PROPORTIONAL_VALVE.get().engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(valve), helper.getBlockState(valve), Direction.UP).orElseThrow();
        if (drivenZero.value() != 0.0 || drivenZero.quality() != PortQuality.VALID
                || PneumaticProportionalValveBlock.opening(helper.getLevel(), helper.absolutePos(valve)) != 0) {
            helper.fail("Proportional valve collapsed a configured zero command into NO_SIGNAL", valve);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void reliefValveDiagnosticsAreObserverNeutral(GameTestHelper helper) {
        BlockPos relief = new BlockPos(2, 1, 2);
        helper.setBlock(relief, RedstoneEngineering.PNEUMATIC_RELIEF_VALVE.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        BlockPos world = helper.absolutePos(relief);
        RuntimeIntStore.remove(helper.getLevel(), "pneumatic_relief", world);
        int before = RuntimeIntStore.entryCount(helper.getLevel());

        if (PneumaticReliefValveBlock.ventEvents(helper.getLevel(), world) != 0
                || PneumaticReliefValveBlock.lastExcess(helper.getLevel(), world) != 0
                || PneumaticReliefValveBlock.totalVentedProxy(helper.getLevel(), world) != 0
                || PneumaticReliefValveBlock.venting(helper.getLevel(), world)
                || RuntimeIntStore.entryCount(helper.getLevel()) != before) {
            helper.fail("Reading relief-valve diagnostics fabricated vent history", relief);
            return;
        }
        PneumaticReliefValveBlock.clearVenting(helper.getLevel(), world);
        if (RuntimeIntStore.entryCount(helper.getLevel()) != before) {
            helper.fail("Re-arming an idle relief valve fabricated diagnostic runtime", relief);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void pneumaticCylinderInspectionIsNeutralAndZeroInputIsValid(GameTestHelper helper) {
        BlockPos pipe = new BlockPos(1, 1, 2);
        BlockPos cylinder = new BlockPos(2, 1, 2);
        helper.setBlock(cylinder, RedstoneEngineering.PNEUMATIC_CYLINDER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        BlockPos world = helper.absolutePos(cylinder);
        RuntimeIntStore.remove(helper.getLevel(), "pneumatic_cylinder", world);
        int before = RuntimeIntStore.entryCount(helper.getLevel());
        if (PneumaticCylinderBlock.position(helper.getLevel(), world) != 0
                || PneumaticCylinderBlock.target(helper.getLevel(), world) != 0
                || PneumaticCylinderBlock.pressure(helper.getLevel(), world) != 0
                || PneumaticCylinderBlock.travel(helper.getLevel(), world) != 0
                || RuntimeIntStore.entryCount(helper.getLevel()) != before) {
            helper.fail("Cylinder diagnostics allocated actuator runtime before the actuator ticked", cylinder);
            return;
        }

        helper.setBlock(pipe, RedstoneEngineering.PNEUMATIC_PIPE.get().defaultBlockState());
        PneumaticNetwork.recompute(helper.getLevel(), helper.absolutePos(pipe));
        var input = RedstoneEngineering.PNEUMATIC_CYLINDER.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(cylinder), Direction.WEST).orElseThrow();
        if (input.value() != 0.0 || input.quality() != PortQuality.VALID) {
            helper.fail("Cylinder mislabeled solved zero-pressure input as missing", cylinder);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void slimeVibrationInspectionDoesNotCreateWaveRuntime(GameTestHelper helper) {
        BlockPos slime = new BlockPos(2, 1, 2);
        helper.setBlock(slime, RedstoneEngineering.SLIME_VIBRATION_CONDUIT.get().defaultBlockState());
        BlockPos world = helper.absolutePos(slime);
        InformationRuntime.clear(helper.getLevel(), "mech_wave", world);
        int before = RuntimeIntStore.entryCount(helper.getLevel());
        VibrationNetwork.Wave wave = VibrationNetwork.sample(helper.getLevel(), world);
        var port = RedstoneEngineering.SLIME_VIBRATION_CONDUIT.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(slime), Direction.NORTH).orElseThrow();
        if (wave.valid() || wave.amplitude() != 0 || port.quality() != PortQuality.NO_SIGNAL
                || RuntimeIntStore.entryCount(helper.getLevel()) != before) {
            helper.fail("Inspecting idle slime vibration conduit created a wave packet", slime);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void honeyDamperInspectionDoesNotCreateWaveRuntime(GameTestHelper helper) {
        BlockPos honey = new BlockPos(2, 1, 2);
        helper.setBlock(honey, RedstoneEngineering.HONEY_VIBRATION_DAMPER.get().defaultBlockState());
        BlockPos world = helper.absolutePos(honey);
        InformationRuntime.clear(helper.getLevel(), "mech_wave", world);
        int before = RuntimeIntStore.entryCount(helper.getLevel());
        VibrationNetwork.Wave wave = VibrationNetwork.sample(helper.getLevel(), world);
        var port = RedstoneEngineering.HONEY_VIBRATION_DAMPER.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(honey), Direction.NORTH).orElseThrow();
        if (wave.valid() || wave.amplitude() != 0 || port.quality() != PortQuality.NO_SIGNAL
                || RuntimeIntStore.entryCount(helper.getLevel()) != before) {
            helper.fail("Inspecting idle honey damper created a wave packet", honey);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void mechanicalExciterSeparatesMissingDriveFromDrivenZero(GameTestHelper helper) {
        BlockPos source = new BlockPos(2, 0, 2);
        BlockPos exciter = new BlockPos(2, 1, 2);
        helper.setBlock(exciter, RedstoneEngineering.MECHANICAL_EXCITER.get().defaultBlockState());
        BlockPos world = helper.absolutePos(exciter);

        var missing = RedstoneEngineering.MECHANICAL_EXCITER.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(exciter), Direction.DOWN).orElseThrow();
        if (missing.quality() != PortQuality.NO_SIGNAL) {
            helper.fail("Mechanical exciter treated an empty drive face as valid LOW", exciter);
            return;
        }

        helper.setBlock(source, Blocks.REDSTONE_WIRE.defaultBlockState());
        var drivenZero = RedstoneEngineering.MECHANICAL_EXCITER.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(exciter), Direction.DOWN).orElseThrow();
        var mechanicalOut = RedstoneEngineering.MECHANICAL_EXCITER.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(exciter), Direction.UP).orElseThrow();
        if (drivenZero.value() != 0.0 || drivenZero.quality() != PortQuality.VALID
                || mechanicalOut.value() != 0.0 || mechanicalOut.quality() != PortQuality.NO_SIGNAL
                || InformationRuntime.snapshot(helper.getLevel(), "mech_exciter", world).ageTicks() >= 0) {
            helper.fail("Mechanical exciter collapsed configured LOW or fabricated a zero-amplitude wave", exciter);
            return;
        }
        helper.succeed();
    }
}
