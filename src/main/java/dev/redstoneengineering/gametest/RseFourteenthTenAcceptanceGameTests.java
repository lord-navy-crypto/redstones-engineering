package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.*;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.physics.ThermalPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Fourteenth block-by-block campaign: thermal, Lapis, Quartz, magnetic-material, and light-sensor boundaries. */
public final class RseFourteenthTenAcceptanceGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseFourteenthTenAcceptanceGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void thermalRadiatorPublishesSixPassiveSinkPorts(GameTestHelper helper) {
        BlockPos radiator = new BlockPos(1, 1, 2);
        BlockPos mass = new BlockPos(2, 1, 2);
        helper.setBlock(mass, RedstoneEngineering.THERMAL_MASS.get().defaultBlockState()
                .setValue(ThermalMassBlock.TEMPERATURE, 60)
                .setValue(ThermalMassBlock.HEAT_CAPACITY, 4));
        helper.setBlock(radiator, RedstoneEngineering.THERMAL_RADIATOR.get().defaultBlockState()
                .setValue(ThermalRadiatorBlock.COOLING, 4));

        helper.runAfterDelay(3, () -> {
            ThermalRadiatorBlock block = RedstoneEngineering.THERMAL_RADIATOR.get();
            BlockState state = helper.getBlockState(radiator);
            for (Direction side : Direction.values()) {
                if (!hasPort(block, state, side, EngineeringDomain.THERMAL, PortKind.ACTUATOR, PortDirection.INPUT)) {
                    helper.fail("Thermal radiator must expose six passive THERMAL sink inputs", radiator);
                    return;
                }
            }
            int temperature = helper.getBlockState(mass).getValue(ThermalMassBlock.TEMPERATURE);
            if (temperature >= 60 || temperature < ThermalPhysics.AMBIENT) {
                helper.fail("Passive radiator did not cool toward ambient without crossing its floor", mass);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 30)
    public static void thermalCalorimeterIsSixFaceObserver(GameTestHelper helper) {
        BlockPos calorimeter = new BlockPos(1, 1, 2);
        BlockPos mass = new BlockPos(2, 1, 2);
        helper.setBlock(mass, RedstoneEngineering.THERMAL_MASS.get().defaultBlockState()
                .setValue(ThermalMassBlock.TEMPERATURE, 55)
                .setValue(ThermalMassBlock.HEAT_CAPACITY, 4));
        helper.setBlock(calorimeter, RedstoneEngineering.THERMAL_CALORIMETER.get().defaultBlockState());

        helper.runAfterDelay(1, () -> {
            ThermalCalorimeterBlock block = RedstoneEngineering.THERMAL_CALORIMETER.get();
            BlockState state = helper.getBlockState(calorimeter);
            for (Direction side : Direction.values()) {
                if (!hasPort(block, state, side, EngineeringDomain.THERMAL, PortKind.MEASUREMENT, PortDirection.INPUT)) {
                    helper.fail("Thermal calorimeter must expose six observer-only THERMAL measurement inputs", calorimeter);
                    return;
                }
            }
            var snapshot = block.engineeringSnapshot(
                    helper.getLevel(), helper.absolutePos(calorimeter), state, Direction.EAST);
            int currentTemperature = helper.getBlockState(mass).getValue(ThermalMassBlock.TEMPERATURE);
            if (snapshot.isEmpty() || Math.round(snapshot.get().value()) != currentTemperature) {
                helper.fail("Calorimeter EAST snapshot did not match the contacted thermal mass", calorimeter);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 30)
    public static void temperatureSensorAveragesAdjacentThermalBodies(GameTestHelper helper) {
        BlockPos sensor = new BlockPos(2, 1, 2);
        BlockPos westMass = new BlockPos(1, 1, 2);
        BlockPos eastMass = new BlockPos(3, 1, 2);
        helper.setBlock(westMass, RedstoneEngineering.THERMAL_MASS.get().defaultBlockState()
                .setValue(ThermalMassBlock.TEMPERATURE, 40)
                .setValue(ThermalMassBlock.HEAT_CAPACITY, 4));
        helper.setBlock(eastMass, RedstoneEngineering.THERMAL_MASS.get().defaultBlockState()
                .setValue(ThermalMassBlock.TEMPERATURE, 60)
                .setValue(ThermalMassBlock.HEAT_CAPACITY, 4));
        helper.setBlock(sensor, RedstoneEngineering.TEMPERATURE_SENSOR.get().defaultBlockState());

        helper.runAfterDelay(2, () -> {
            TemperatureSensorBlock block = RedstoneEngineering.TEMPERATURE_SENSOR.get();
            BlockState state = helper.getBlockState(sensor);
            if (state.getValue(TemperatureSensorBlock.TEMPERATURE) != 50) {
                helper.fail("Temperature sensor did not average its two adjacent thermal bodies", sensor);
                return;
            }
            if (!hasPort(block, state, Direction.WEST, EngineeringDomain.THERMAL, PortKind.SENSOR, PortDirection.INPUT)
                    || !hasPort(block, state, Direction.EAST, EngineeringDomain.THERMAL, PortKind.SENSOR, PortDirection.INPUT)) {
                helper.fail("Temperature sensor did not expose thermal sensing faces", sensor);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 30)
    public static void ironCoreRemanenceIsFreeSpaceNotWired(GameTestHelper helper) {
        BlockPos core = new BlockPos(2, 1, 2);
        helper.setBlock(core, RedstoneEngineering.IRON_CORE.get().defaultBlockState()
                .setValue(IronCoreBlock.MAGNETIZED, true));

        helper.runAfterDelay(7, () -> {
            IronCoreBlock block = RedstoneEngineering.IRON_CORE.get();
            BlockState state = helper.getBlockState(core);
            if (!block.engineeringPorts(state).isEmpty()) {
                helper.fail("Iron core incorrectly exposed wired adjacency ports for a free-space magnetic material", core);
                return;
            }
            if (!state.getValue(IronCoreBlock.MAGNETIZED)) {
                helper.fail("Iron core lost intended remanence without a demagnetization action", core);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void lapisNoiseSourcePublishesFourOutputsAndClearsOnRemoval(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 2);
        BlockPos line = new BlockPos(2, 1, 2);
        helper.setBlock(source, RedstoneEngineering.LAPIS_NOISE_SOURCE.get().defaultBlockState());
        helper.setBlock(line, RedstoneEngineering.LAPIS_SIGNAL_LINE.get().defaultBlockState());

        helper.runAfterDelay(6, () -> {
            LapisNoiseSourceBlock block = RedstoneEngineering.LAPIS_NOISE_SOURCE.get();
            BlockState state = helper.getBlockState(source);
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (!hasPort(block, state, side, EngineeringDomain.LAPIS, PortKind.BUS, PortDirection.OUTPUT)) {
                    helper.fail("Lapis noise source did not expose all four horizontal outputs", source);
                    return;
                }
            }
            if (!LapisSignalLineBlock.valid(helper.getLevel(), helper.absolutePos(line))) {
                helper.fail("Lapis noise source did not energize its connected precision trace", line);
                return;
            }
            helper.setBlock(source, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(3, () -> {
                if (LapisSignalLineBlock.valid(helper.getLevel(), helper.absolutePos(line))) {
                    helper.fail("Removing Lapis noise source left a stale valid trace", line);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void lapisLowPassFilterIsDirectionalAndReleasesOutput(GameTestHelper helper) {
        BlockPos source = new BlockPos(0, 1, 2);
        BlockPos filter = new BlockPos(1, 1, 2);
        BlockPos output = new BlockPos(2, 1, 2);
        helper.setBlock(source, RedstoneEngineering.LAPIS_PRECISION_SOURCE.get().defaultBlockState()
                .setValue(LapisPrecisionSourceBlock.VALUE, 80));
        helper.setBlock(filter, RedstoneEngineering.LAPIS_LOW_PASS_FILTER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(output, RedstoneEngineering.LAPIS_SIGNAL_LINE.get().defaultBlockState());

        helper.runAfterDelay(6, () -> {
            LapisLowPassFilterBlock block = RedstoneEngineering.LAPIS_LOW_PASS_FILTER.get();
            BlockState state = helper.getBlockState(filter);
            if (!hasPort(block, state, Direction.WEST, EngineeringDomain.LAPIS, PortKind.BUS, PortDirection.INPUT)
                    || !hasPort(block, state, Direction.EAST, EngineeringDomain.LAPIS, PortKind.BUS, PortDirection.OUTPUT)) {
                helper.fail("Lapis low-pass did not expose BACK input and FRONT output", filter);
                return;
            }
            if (!LapisSignalLineBlock.valid(helper.getLevel(), helper.absolutePos(output))) {
                helper.fail("Lapis low-pass did not drive a valid filtered output", output);
                return;
            }
            helper.setBlock(filter, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(3, () -> {
                if (LapisSignalLineBlock.valid(helper.getLevel(), helper.absolutePos(output))) {
                    helper.fail("Removing Lapis low-pass left its output driver alive", output);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void lapisPrecisionMeterObservesWithoutBridging(GameTestHelper helper) {
        BlockPos source = new BlockPos(0, 1, 2);
        BlockPos meter = new BlockPos(1, 1, 2);
        BlockPos isolatedLine = new BlockPos(2, 1, 2);
        helper.setBlock(source, RedstoneEngineering.LAPIS_PRECISION_SOURCE.get().defaultBlockState()
                .setValue(LapisPrecisionSourceBlock.VALUE, 70));
        helper.setBlock(meter, RedstoneEngineering.LAPIS_PRECISION_METER.get().defaultBlockState()
                .setValue(LapisPrecisionMeterBlock.FACING, Direction.WEST));
        helper.setBlock(isolatedLine, RedstoneEngineering.LAPIS_SIGNAL_LINE.get().defaultBlockState());

        helper.runAfterDelay(5, () -> {
            LapisPrecisionMeterBlock block = RedstoneEngineering.LAPIS_PRECISION_METER.get();
            BlockState state = helper.getBlockState(meter);
            if (!hasPort(block, state, Direction.WEST, EngineeringDomain.LAPIS, PortKind.MEASUREMENT, PortDirection.INPUT)
                    || block.engineeringPort(state, Direction.EAST).isPresent()) {
                helper.fail("Lapis meter did not remain a single-face observer", meter);
                return;
            }
            var sample = LapisPrecisionMeterBlock.sampledValue(helper.getLevel(), helper.absolutePos(meter), state);
            if (!sample.valid() || sample.value() != 70) {
                helper.fail("Lapis meter did not read its facing precision source", meter);
                return;
            }
            if (LapisSignalLineBlock.valid(helper.getLevel(), helper.absolutePos(isolatedLine))) {
                helper.fail("Lapis precision meter incorrectly bridged signal to the opposite side", isolatedLine);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void quartzLabOscillatorPublishesFourOutputsAndClearsOnRemoval(GameTestHelper helper) {
        BlockPos oscillator = new BlockPos(1, 1, 2);
        BlockPos line = new BlockPos(2, 1, 2);
        helper.setBlock(oscillator, RedstoneEngineering.QUARTZ_LAB_OSCILLATOR.get().defaultBlockState()
                .setValue(QuartzLabOscillatorBlock.JITTER, 0));
        helper.setBlock(line, RedstoneEngineering.QUARTZ_TIMING_LINE.get().defaultBlockState());

        helper.runAfterDelay(6, () -> {
            QuartzLabOscillatorBlock block = RedstoneEngineering.QUARTZ_LAB_OSCILLATOR.get();
            BlockState state = helper.getBlockState(oscillator);
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (!hasPort(block, state, side, EngineeringDomain.QUARTZ, PortKind.TRIGGER, PortDirection.OUTPUT)) {
                    helper.fail("Quartz lab oscillator did not expose all four horizontal clock outputs", oscillator);
                    return;
                }
            }
            if (!QuartzTimingLineBlock.valid(helper.getLevel(), helper.absolutePos(line))) {
                helper.fail("Quartz lab oscillator did not establish a valid timing trace", line);
                return;
            }
            helper.setBlock(oscillator, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(3, () -> {
                if (QuartzTimingLineBlock.valid(helper.getLevel(), helper.absolutePos(line))) {
                    helper.fail("Removing Quartz lab oscillator left a stale valid timing trace", line);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void quartzPhaseDelayIsDirectionalAndReleasesOutput(GameTestHelper helper) {
        BlockPos oscillator = new BlockPos(0, 1, 2);
        BlockPos delay = new BlockPos(1, 1, 2);
        BlockPos output = new BlockPos(2, 1, 2);
        helper.setBlock(oscillator, RedstoneEngineering.QUARTZ_LAB_OSCILLATOR.get().defaultBlockState()
                .setValue(QuartzLabOscillatorBlock.JITTER, 0));
        helper.setBlock(delay, RedstoneEngineering.QUARTZ_PHASE_DELAY.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                .setValue(QuartzPhaseDelayBlock.DELAY, 2));
        helper.setBlock(output, RedstoneEngineering.QUARTZ_TIMING_LINE.get().defaultBlockState());

        helper.runAfterDelay(8, () -> {
            QuartzPhaseDelayBlock block = RedstoneEngineering.QUARTZ_PHASE_DELAY.get();
            BlockState state = helper.getBlockState(delay);
            if (!hasPort(block, state, Direction.WEST, EngineeringDomain.QUARTZ, PortKind.TRIGGER, PortDirection.INPUT)
                    || !hasPort(block, state, Direction.EAST, EngineeringDomain.QUARTZ, PortKind.TRIGGER, PortDirection.OUTPUT)) {
                helper.fail("Quartz phase delay did not expose BACK input and FRONT output", delay);
                return;
            }
            if (!QuartzTimingLineBlock.valid(helper.getLevel(), helper.absolutePos(output))) {
                helper.fail("Quartz phase delay did not drive a valid timing-domain output", output);
                return;
            }
            helper.setBlock(delay, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(3, () -> {
                if (QuartzTimingLineBlock.valid(helper.getLevel(), helper.absolutePos(output))) {
                    helper.fail("Removing Quartz phase delay left its output driver alive", output);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 30)
    public static void engineeringLightSensorSeparatesOpticalApertureAndRedstoneOutput(GameTestHelper helper) {
        BlockPos sensor = new BlockPos(2, 1, 2);
        helper.setBlock(sensor, RedstoneEngineering.ENGINEERING_LIGHT_SENSOR.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.EAST));

        helper.runAfterDelay(2, () -> {
            EngineeringLightSensorBlock block = RedstoneEngineering.ENGINEERING_LIGHT_SENSOR.get();
            BlockState state = helper.getBlockState(sensor);
            if (!hasPort(block, state, Direction.UP, EngineeringDomain.OPTICAL, PortKind.SENSOR, PortDirection.INPUT)) {
                helper.fail("Engineering light sensor did not expose its UP optical aperture", sensor);
                return;
            }
            if (!hasPort(block, state, Direction.EAST, EngineeringDomain.REDSTONE, PortKind.SENSOR, PortDirection.OUTPUT)) {
                helper.fail("Engineering light sensor did not expose its FRONT redstone output", sensor);
                return;
            }
            if (block.engineeringPort(state, Direction.WEST).isPresent()) {
                helper.fail("Engineering light sensor exposed a false wired port on its BACK face", sensor);
                return;
            }
            if (block.engineeringSnapshot(helper.getLevel(), helper.absolutePos(sensor), state, Direction.UP).isEmpty()) {
                helper.fail("Engineering light sensor aperture did not publish an optical snapshot", sensor);
                return;
            }
            helper.succeed();
        });
    }

    private static boolean hasPort(
            EngineeringPortProvider provider,
            BlockState state,
            Direction side,
            EngineeringDomain domain,
            PortKind kind,
            PortDirection direction
    ) {
        return provider.engineeringPort(state, side)
                .map(port -> port.domain() == domain && port.kind() == kind && port.direction() == direction)
                .orElse(false);
    }
}
