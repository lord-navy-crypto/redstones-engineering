package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.AnalogIndicatorBlock;
import dev.redstoneengineering.block.ConnectedCableBlock;
import dev.redstoneengineering.block.CopperCableJunctionBlock;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.EntityDensitySensorBlock;
import dev.redstoneengineering.block.LapisMagneticTransducerBlock;
import dev.redstoneengineering.block.LapisPrecisionRangeSensorBlock;
import dev.redstoneengineering.block.LapisTemperatureTransducerBlock;
import dev.redstoneengineering.block.LapisVoltageTransducerBlock;
import dev.redstoneengineering.block.OpticalFiberJunctionBlock;
import dev.redstoneengineering.block.RedstoneCableJunctionBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.TransmissionTopology;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.MagneticPhysics;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Seventh 10-block design/bug campaign: occupancy, indicators, junctions and precision transducers. */
public final class RseSeventhTenDesignBugGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseSeventhTenDesignBugGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void entityDensityIncompleteCoverageIsStaleInsteadOfLowCount(GameTestHelper helper) {
        EntityDensitySensorBlock.DensitySample incomplete = new EntityDensitySensorBlock.DensitySample(0, false);
        EntityDensitySensorBlock.DensitySample saturated = new EntityDensitySensorBlock.DensitySample(20, true);
        BlockState state = RedstoneEngineering.ENTITY_DENSITY_SENSOR.get().defaultBlockState();
        if (incomplete.quality() != PortQuality.STALE
                || saturated.quality() != PortQuality.SATURATED
                || RedstoneEngineering.ENTITY_DENSITY_SENSOR.get().engineeringPorts(state).size() != 2) {
            helper.fail("Entity density coverage/count quality contract regressed");
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void analogIndicatorDistinguishesDrivenZeroFromEmptyInput(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 1);
        BlockPos drivenIndicator = new BlockPos(2, 1, 1);
        BlockPos emptyIndicator = new BlockPos(2, 1, 3);
        helper.setBlock(source, RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.EAST)
                .setValue(RedstoneReferenceSourceBlock.POWER, 0));
        helper.setBlock(drivenIndicator, RedstoneEngineering.ANALOG_INDICATOR.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.EAST));
        helper.setBlock(emptyIndicator, RedstoneEngineering.ANALOG_INDICATOR.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.EAST));

        BlockPos drivenWorld = helper.absolutePos(drivenIndicator);
        BlockPos emptyWorld = helper.absolutePos(emptyIndicator);
        var driven = RedstoneEngineering.ANALOG_INDICATOR.get().engineeringSnapshot(
                helper.getLevel(), drivenWorld, helper.getBlockState(drivenIndicator), Direction.WEST).orElseThrow();
        var empty = RedstoneEngineering.ANALOG_INDICATOR.get().engineeringSnapshot(
                helper.getLevel(), emptyWorld, helper.getBlockState(emptyIndicator), Direction.WEST).orElseThrow();
        if (driven.value() != 0.0 || driven.quality() != PortQuality.VALID
                || empty.quality() != PortQuality.NO_SIGNAL) {
            helper.fail("Analog indicator collapsed driven-zero and empty input", drivenIndicator);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void signalJunctionNonRedstonePortHasObserverNeutralSnapshot(GameTestHelper helper) {
        BlockPos junction = new BlockPos(2, 1, 2);
        BlockPos world = helper.absolutePos(junction);
        BlockState state = RedstoneEngineering.REDSTONE_CABLE_JUNCTION.get().defaultBlockState()
                .setValue(RedstoneCableJunctionBlock.MEDIUM, TransmissionTopology.SignalMedium.DATA_BUS_8)
                .setValue(ConnectedCableBlock.UP, true);
        int before = RuntimeIntStore.entryCount(helper.getLevel());
        var snapshot = RedstoneEngineering.REDSTONE_CABLE_JUNCTION.get().engineeringSnapshot(
                helper.getLevel(), world, state, Direction.UP).orElseThrow();
        if (snapshot.quality() != PortQuality.STALE
                || RuntimeIntStore.entryCount(helper.getLevel()) != before) {
            helper.fail("Unified signal Junction Point advertised a vertical bus port without neutral diagnostics", junction);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void opticalServiceOpenClearsRetainedCarrierEvidence(GameTestHelper helper) {
        BlockPos junction = new BlockPos(2, 1, 2);
        helper.setBlock(junction, RedstoneEngineering.OPTICAL_FIBER_JUNCTION.get().defaultBlockState());
        BlockPos world = helper.absolutePos(junction);
        OpticalFiberJunctionBlock.setOptical(helper.getLevel(), world, 9, 3, true);
        RedstoneEngineering.OPTICAL_FIBER_JUNCTION.get().setServiceOpen(helper.getLevel(), world, true);
        BlockState open = helper.getBlockState(junction);
        if (!open.getValue(OpticalFiberJunctionBlock.SERVICE_OPEN)
                || OpticalFiberJunctionBlock.intensity(helper.getLevel(), world) != 0
                || OpticalFiberJunctionBlock.valid(helper.getLevel(), world)
                || OpticalFiberJunctionBlock.driverCount(helper.getLevel(), world) != 0
                || !RedstoneEngineering.OPTICAL_FIBER_JUNCTION.get().engineeringPorts(open).isEmpty()) {
            helper.fail("Open optical service splice retained hidden carrier state", junction);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void copperJunctionSeparatesNoSourceValidZeroAndConflict(GameTestHelper helper) {
        BlockPos junction = new BlockPos(2, 1, 2);
        BlockPos world = helper.absolutePos(junction);
        BlockState state = RedstoneEngineering.COPPER_CABLE_JUNCTION.get().defaultBlockState()
                .setValue(ConnectedCableBlock.EAST, true);
        RuntimeIntStore.remove(helper.getLevel(), "copper_junction", world);
        var none = RedstoneEngineering.COPPER_CABLE_JUNCTION.get().engineeringSnapshot(
                helper.getLevel(), world, state, Direction.EAST).orElseThrow();

        int[] runtime = RuntimeIntStore.get(helper.getLevel(), "copper_junction", world, 2);
        runtime[0] = 0;
        runtime[1] = 1;
        var drivenZero = RedstoneEngineering.COPPER_CABLE_JUNCTION.get().engineeringSnapshot(
                helper.getLevel(), world, state, Direction.EAST).orElseThrow();
        runtime[1] = 2;
        var conflict = RedstoneEngineering.COPPER_CABLE_JUNCTION.get().engineeringSnapshot(
                helper.getLevel(), world, state, Direction.EAST).orElseThrow();

        if (none.quality() != PortQuality.NO_SIGNAL
                || drivenZero.value() != 0.0 || drivenZero.quality() != PortQuality.VALID
                || conflict.quality() != PortQuality.TOPOLOGY_ERROR) {
            helper.fail("Copper junction value/source/conflict evidence regressed", junction);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void temperatureTransducerInspectionIsNeutralBeforeFirstSample(GameTestHelper helper) {
        BlockPos transducer = new BlockPos(2, 1, 2);
        helper.setBlock(transducer, RedstoneEngineering.LAPIS_TEMPERATURE_TRANSDUCER.get().defaultBlockState());
        BlockPos world = helper.absolutePos(transducer);
        RuntimeIntStore.remove(helper.getLevel(), "lapis_temperature_transducer", world);
        int before = RuntimeIntStore.entryCount(helper.getLevel());
        LapisTemperatureTransducerBlock block = RedstoneEngineering.LAPIS_TEMPERATURE_TRANSDUCER.get();
        BlockState state = helper.getBlockState(transducer);
        Direction outputSide = state.getValue(DirectionalDomainBlock.FACING);
        var output = block.engineeringSnapshot(helper.getLevel(), world, state, outputSide).orElseThrow();
        if (block.output(helper.getLevel(), world) != 0
                || block.outputQuality(helper.getLevel(), world) != PortQuality.STALE
                || output.quality() != PortQuality.STALE
                || RuntimeIntStore.entryCount(helper.getLevel()) != before) {
            helper.fail("Temperature transducer inspection created runtime or fabricated first-sample validity", transducer);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void magneticTransducerPropagatesFieldCoverageQuality(GameTestHelper helper) {
        BlockPos transducer = new BlockPos(2, 1, 2);
        BlockPos world = helper.absolutePos(transducer);
        BlockState state = RedstoneEngineering.LAPIS_MAGNETIC_TRANSDUCER.get().defaultBlockState();
        Direction inputSide = state.getValue(DirectionalDomainBlock.FACING).getOpposite();
        MagneticPhysics.FieldSample field = MagneticPhysics.fieldSample(helper.getLevel(), world, 6);
        var snapshot = RedstoneEngineering.LAPIS_MAGNETIC_TRANSDUCER.get().engineeringSnapshot(
                helper.getLevel(), world, state, inputSide).orElseThrow();
        PortQuality expected = field.complete() ? PortQuality.VALID : PortQuality.STALE;
        if (snapshot.quality() != expected) {
            helper.fail("Magnetic Lapis transducer discarded field scan coverage evidence", transducer);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void opticalTransducerPreservesUpstreamTopologyConflict(GameTestHelper helper) {
        BlockPos junction = new BlockPos(1, 1, 2);
        BlockPos transducer = new BlockPos(2, 1, 2);
        helper.setBlock(junction, RedstoneEngineering.OPTICAL_FIBER_JUNCTION.get().defaultBlockState());
        helper.setBlock(transducer, RedstoneEngineering.LAPIS_OPTICAL_TRANSDUCER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        BlockPos junctionWorld = helper.absolutePos(junction);
        int[] optical = RuntimeIntStore.get(helper.getLevel(), "optical_junction", junctionWorld, 4);
        optical[0] = 0;
        optical[1] = 0;
        optical[2] = 0;
        optical[3] = 2;
        var snapshot = RedstoneEngineering.LAPIS_OPTICAL_TRANSDUCER.get().engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(transducer), helper.getBlockState(transducer), Direction.WEST).orElseThrow();
        if (snapshot.quality() != PortQuality.TOPOLOGY_ERROR) {
            helper.fail("Optical transducer collapsed upstream driver conflict into NO_SIGNAL", transducer);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void voltageTransducerSeparatesUndrivenWireFromValidZeroSource(GameTestHelper helper) {
        BlockPos probe = new BlockPos(1, 1, 2);
        BlockPos transducer = new BlockPos(2, 1, 2);
        helper.setBlock(probe, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState());
        helper.setBlock(transducer, RedstoneEngineering.LAPIS_VOLTAGE_TRANSDUCER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        var emptyWire = RedstoneEngineering.LAPIS_VOLTAGE_TRANSDUCER.get().engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(transducer), helper.getBlockState(transducer), Direction.WEST).orElseThrow();
        if (emptyWire.quality() != PortQuality.NO_SIGNAL) {
            helper.fail("Voltage transducer promoted an undriven Copper wire to a valid zero source", transducer);
            return;
        }

        helper.setBlock(probe, RedstoneEngineering.COPPER_VOLTAGE_SOURCE.get().defaultBlockState()
                .setValue(dev.redstoneengineering.block.CopperVoltageSourceBlock.VOLTAGE, 0));
        var zeroSource = RedstoneEngineering.LAPIS_VOLTAGE_TRANSDUCER.get().engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(transducer), helper.getBlockState(transducer), Direction.WEST).orElseThrow();
        if (zeroSource.value() != 0.0 || zeroSource.quality() != PortQuality.VALID) {
            helper.fail("Voltage transducer lost configured 0V source evidence", transducer);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void precisionRangeSeparatesNoTargetFromUnknownCoverage(GameTestHelper helper) {
        LapisPrecisionRangeSensorBlock.RangeSample unknown =
                new LapisPrecisionRangeSensorBlock.RangeSample(-1, 64, false);
        LapisPrecisionRangeSensorBlock.RangeSample loadedNoTarget =
                new LapisPrecisionRangeSensorBlock.RangeSample(-1, 64, true);
        BlockPos sensor = new BlockPos(2, 1, 2);
        BlockPos world = helper.absolutePos(sensor);
        RuntimeIntStore.remove(helper.getLevel(), "lapis_precision_range_sensor", world);
        LapisPrecisionRangeSensorBlock block = RedstoneEngineering.LAPIS_PRECISION_RANGE_SENSOR.get();
        if (unknown.quality() != PortQuality.STALE
                || loadedNoTarget.quality() != PortQuality.NO_SIGNAL
                || block.outputQuality(helper.getLevel(), world) != PortQuality.STALE) {
            helper.fail("Precision range sensor collapsed unknown coverage into no-target/no-signal");
            return;
        }
        helper.succeed();
    }
}
