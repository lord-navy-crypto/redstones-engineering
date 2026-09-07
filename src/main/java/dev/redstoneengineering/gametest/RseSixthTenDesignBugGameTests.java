package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.InductionCoilBlock;
import dev.redstoneengineering.block.MagneticGradientMeterBlock;
import dev.redstoneengineering.block.PermanentMagnetBlock;
import dev.redstoneengineering.block.RedstoneCableTerminalBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.RedstoneSignalCableBlock;
import dev.redstoneengineering.block.TankLevelSensorBlock;
import dev.redstoneengineering.block.ThermalMassBlock;
import dev.redstoneengineering.block.ThermalRadiatorBlock;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.metrology.MeasurementSnapshot;
import dev.redstoneengineering.metrology.MetrologySupport;
import dev.redstoneengineering.physics.RedstoneCableNetwork;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.physics.ThermalPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Sixth 10-block design/bug campaign: induction, thermal engineering, insulated redstone and field sensors. */
public final class RseSixthTenDesignBugGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseSixthTenDesignBugGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void inductionInspectionIsNeutralAndStaticFieldDoesNotBecomeSustainedEmf(GameTestHelper helper) {
        BlockPos magnet = new BlockPos(1, 1, 2);
        BlockPos coil = new BlockPos(2, 1, 2);
        helper.setBlock(magnet, RedstoneEngineering.PERMANENT_MAGNET.get().defaultBlockState()
                .setValue(PermanentMagnetBlock.STRENGTH, 15));
        helper.setBlock(coil, RedstoneEngineering.INDUCTION_COIL.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        BlockPos world = helper.absolutePos(coil);

        helper.runAfterDelay(4, () -> {
            if (InductionCoilBlock.outputVoltage(helper.getLevel(), world) != 0) {
                helper.fail("Static magnetic field became a sustained induction output instead of a delta-flux transient", coil);
                return;
            }
            RuntimeIntStore.remove(helper.getLevel(), "induction_coil", world);
            int before = RuntimeIntStore.entryCount(helper.getLevel());
            InductionCoilBlock.outputVoltage(helper.getLevel(), world);
            RedstoneEngineering.INDUCTION_COIL.get().engineeringSnapshot(
                    helper.getLevel(), world, helper.getBlockState(coil), Direction.EAST);
            if (RuntimeIntStore.entryCount(helper.getLevel()) != before) {
                helper.fail("Induction output inspection allocated runtime state", coil);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void magneticGradientZeroIsAValidCompleteMeasurement(GameTestHelper helper) {
        BlockPos meter = new BlockPos(2, 1, 2);
        helper.setBlock(meter, RedstoneEngineering.MAGNETIC_GRADIENT_METER.get().defaultBlockState());
        BlockPos world = helper.absolutePos(meter);
        var sample = MagneticGradientMeterBlock.gradient(helper.getLevel(), world, Direction.Axis.X);
        var snapshot = RedstoneEngineering.MAGNETIC_GRADIENT_METER.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(meter), Direction.EAST).orElseThrow();
        if (!sample.complete() || sample.value() != 0 || snapshot.value() != 0.0 || snapshot.quality() != PortQuality.VALID) {
            helper.fail("Complete uniform/zero magnetic gradient was confused with no signal", meter);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void thermalHeaterDistinguishesValidZeroCopperFromAbsentFeed(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 2);
        BlockPos heater = new BlockPos(2, 1, 2);
        helper.setBlock(source, RedstoneEngineering.COPPER_VOLTAGE_SOURCE.get().defaultBlockState()
                .setValue(dev.redstoneengineering.block.CopperVoltageSourceBlock.VOLTAGE, 0));
        helper.setBlock(heater, RedstoneEngineering.THERMAL_HEATER.get().defaultBlockState());
        BlockPos world = helper.absolutePos(heater);
        var fed = RedstoneEngineering.THERMAL_HEATER.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(heater), Direction.WEST).orElseThrow();
        var empty = RedstoneEngineering.THERMAL_HEATER.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(heater), Direction.EAST).orElseThrow();
        if (fed.value() != 0.0 || fed.quality() != PortQuality.VALID || empty.quality() != PortQuality.NO_SIGNAL) {
            helper.fail("Thermal heater collapsed valid 0V and no Copper source into one state", heater);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void thermalRadiatorStopsAtAmbientFloor(GameTestHelper helper) {
        BlockPos mass = new BlockPos(2, 1, 2);
        BlockPos radiator = new BlockPos(1, 1, 2);
        helper.setBlock(mass, RedstoneEngineering.THERMAL_MASS.get().defaultBlockState()
                .setValue(ThermalMassBlock.TEMPERATURE, ThermalPhysics.AMBIENT + 1));
        helper.setBlock(radiator, RedstoneEngineering.THERMAL_RADIATOR.get().defaultBlockState()
                .setValue(ThermalRadiatorBlock.COOLING, 4));
        helper.runAfterDelay(2, () -> {
            int temperature = helper.getBlockState(mass).getValue(ThermalMassBlock.TEMPERATURE);
            if (temperature != ThermalPhysics.AMBIENT) {
                helper.fail("Passive radiator overshot or failed to approach its ambient floor", mass);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void thermalCalorimeterInspectionDoesNotCreateHistory(GameTestHelper helper) {
        BlockPos calorimeter = new BlockPos(2, 1, 2);
        helper.setBlock(calorimeter, RedstoneEngineering.THERMAL_CALORIMETER.get().defaultBlockState());
        BlockPos world = helper.absolutePos(calorimeter);
        RuntimeIntStore.remove(helper.getLevel(), "thermal_calorimeter", world);
        int before = RuntimeIntStore.entryCount(helper.getLevel());
        var history = dev.redstoneengineering.block.ThermalCalorimeterBlock.history(helper.getLevel(), world);
        if (history.initialized() || RuntimeIntStore.entryCount(helper.getLevel()) != before) {
            helper.fail("Calorimeter history inspection created retained measurement state", calorimeter);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void redstoneCableDistinguishesDrivenZeroFromUndrivenZeroWithoutObserverMutation(GameTestHelper helper) {
        BlockPos reference = new BlockPos(0, 1, 1);
        BlockPos terminal = new BlockPos(1, 1, 1);
        BlockPos cable = new BlockPos(2, 1, 1);
        BlockPos isolated = new BlockPos(2, 1, 3);
        helper.setBlock(reference, RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.EAST)
                .setValue(RedstoneReferenceSourceBlock.POWER, 0));
        helper.setBlock(terminal, RedstoneEngineering.REDSTONE_CABLE_TERMINAL.get().defaultBlockState()
                .setValue(RedstoneCableTerminalBlock.FACING, Direction.WEST)
                .setValue(RedstoneCableTerminalBlock.OUTPUT_MODE, false));
        helper.setBlock(cable, RedstoneEngineering.REDSTONE_SIGNAL_CABLE.get().defaultBlockState());
        helper.setBlock(isolated, RedstoneEngineering.REDSTONE_SIGNAL_CABLE.get().defaultBlockState());

        helper.runAfterDelay(3, () -> {
            BlockPos cableWorld = helper.absolutePos(cable);
            BlockPos isolatedWorld = helper.absolutePos(isolated);
            RedstoneCableNetwork.SourceEvidence driven = RedstoneCableNetwork.sourceEvidence(helper.getLevel(), cableWorld);
            RedstoneCableNetwork.SourceEvidence none = RedstoneCableNetwork.sourceEvidence(helper.getLevel(), isolatedWorld);
            if (RedstoneSignalCableBlock.power(helper.getLevel(), cableWorld) != 0
                    || driven.sourceCount() < 1 || driven.quality() != PortQuality.VALID
                    || none.sourceCount() != 0 || none.quality() != PortQuality.NO_SIGNAL) {
                helper.fail("Insulated redstone cable failed valid-zero versus no-source evidence", cable);
                return;
            }

            RuntimeIntStore.remove(helper.getLevel(), "redstone_cable", isolatedWorld);
            RedstoneCableNetwork.removeEvidence(helper.getLevel(), isolatedWorld);
            int before = RuntimeIntStore.entryCount(helper.getLevel());
            RedstoneSignalCableBlock.power(helper.getLevel(), isolatedWorld);
            RedstoneEngineering.REDSTONE_SIGNAL_CABLE.get().engineeringSnapshot(
                    helper.getLevel(), isolatedWorld, helper.getBlockState(isolated), Direction.NORTH);
            if (RuntimeIntStore.entryCount(helper.getLevel()) != before) {
                helper.fail("Cable inspection allocated signal/evidence runtime", isolated);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void redstoneTerminalModeChangeClearsCachedRoleValue(GameTestHelper helper) {
        BlockPos terminal = new BlockPos(2, 1, 2);
        helper.setBlock(terminal, RedstoneEngineering.REDSTONE_CABLE_TERMINAL.get().defaultBlockState()
                .setValue(RedstoneCableTerminalBlock.FACING, Direction.WEST)
                .setValue(RedstoneCableTerminalBlock.OUTPUT_MODE, false)
                .setValue(RedstoneCableTerminalBlock.POWER, 12));
        BlockState toggled = helper.getBlockState(terminal)
                .setValue(RedstoneCableTerminalBlock.OUTPUT_MODE, true);
        helper.setBlock(terminal, toggled);
        helper.runAfterDelay(1, () -> {
            BlockState state = helper.getBlockState(terminal);
            if (!state.getValue(RedstoneCableTerminalBlock.OUTPUT_MODE)
                    || state.getValue(RedstoneCableTerminalBlock.POWER) != 0) {
                helper.fail("Terminal role change retained the old role's cached signal", terminal);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void redstoneReferenceZeroIsValidAndSingleEnded(GameTestHelper helper) {
        BlockPos source = new BlockPos(2, 1, 2);
        helper.setBlock(source, RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.EAST)
                .setValue(RedstoneReferenceSourceBlock.POWER, 0));
        BlockState state = helper.getBlockState(source);
        EngineeringPortProvider provider = RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get();
        var snapshot = provider.engineeringSnapshot(helper.getLevel(), helper.absolutePos(source), state, Direction.EAST).orElseThrow();
        if (provider.engineeringPorts(state).size() != 1
                || provider.engineeringPort(state, Direction.WEST).isPresent()
                || snapshot.value() != 0.0 || snapshot.quality() != PortQuality.VALID) {
            helper.fail("Redstone reference source lost valid-zero or single-FRONT output semantics", source);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void engineeringLightSensorIsStaleBeforeFirstSampleAndZeroCanBeValid(GameTestHelper helper) {
        BlockPos sensor = new BlockPos(2, 1, 2);
        helper.setBlock(sensor, RedstoneEngineering.ENGINEERING_LIGHT_SENSOR.get().defaultBlockState());
        BlockPos world = helper.absolutePos(sensor);
        BlockState state = helper.getBlockState(sensor);
        Direction front = state.getValue(DirectionalRedstoneEndpointBlock.FACING);
        var initial = RedstoneEngineering.ENGINEERING_LIGHT_SENSOR.get().engineeringSnapshot(
                helper.getLevel(), world, state, front).orElseThrow();
        if (initial.quality() != PortQuality.STALE) {
            helper.fail("Light sensor reported a hard fault before its first scheduled sample", sensor);
            return;
        }
        MetrologySupport.sample(helper.getLevel(), "light_sensor", world, 0.0, 0.0, false, 1.0, 30L);
        var zero = RedstoneEngineering.ENGINEERING_LIGHT_SENSOR.get().engineeringSnapshot(
                helper.getLevel(), world, state, front).orElseThrow();
        if (zero.value() != 0.0 || zero.quality() != PortQuality.VALID) {
            helper.fail("A legitimate zero-light measurement was confused with no signal/fault", sensor);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void tankColumnCoverageSeparatesUnknownFromLoadedEmptyZero(GameTestHelper helper) {
        TankLevelSensorBlock.ColumnSample incomplete = new TankLevelSensorBlock.ColumnSample(3, 3, 16, false);
        if (incomplete.quality() != PortQuality.STALE) {
            helper.fail("Incomplete tank coverage was treated as a trustworthy low-level reading");
            return;
        }
        BlockPos sensor = new BlockPos(2, 1, 2);
        helper.setBlock(sensor, RedstoneEngineering.TANK_LEVEL_SENSOR.get().defaultBlockState());
        TankLevelSensorBlock.ColumnSample loaded = TankLevelSensorBlock.columnSample(helper.getLevel(), helper.absolutePos(sensor));
        if (!loaded.complete() || loaded.fluidBlocks() != 0 || loaded.quality() != PortQuality.VALID) {
            helper.fail("Loaded empty tank column failed to remain a valid zero measurement", sensor);
            return;
        }
        helper.succeed();
    }
}
