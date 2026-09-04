package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.AbstractLapisTransducerBlock;
import dev.redstoneengineering.block.LapisPrecisionRangeSensorBlock;
import dev.redstoneengineering.block.LapisPrecisionSourceBlock;
import dev.redstoneengineering.block.LapisToRedstoneQuantizerBlock;
import dev.redstoneengineering.block.OpticalEmitterBlock;
import dev.redstoneengineering.block.PermanentMagnetBlock;
import dev.redstoneengineering.block.QuartzOscillatorBlock;
import dev.redstoneengineering.block.QuartzTriggeredLapisSamplerBlock;
import dev.redstoneengineering.block.RedstoneToLapisScalerBlock;
import dev.redstoneengineering.block.ThermalMassBlock;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Third block-by-block acceptance campaign: physical transduction, precision sensing,
 * explicit domain conversion and edge-triggered sampling.
 */
public final class RseThirdEightAcceptanceGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseThirdEightAcceptanceGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void temperatureTransducerPublishesThermalInputAndLapisOutput(GameTestHelper helper) {
        BlockPos thermalPos = new BlockPos(1, 1, 2);
        BlockPos sensorPos = new BlockPos(2, 1, 2);

        helper.setBlock(thermalPos, RedstoneEngineering.THERMAL_MASS.get().defaultBlockState()
                .setValue(ThermalMassBlock.TEMPERATURE, 80)
                .setValue(ThermalMassBlock.HEAT_CAPACITY, 4));
        helper.setBlock(sensorPos, fastTransducer(RedstoneEngineering.LAPIS_TEMPERATURE_TRANSDUCER.get().defaultBlockState()));

        helper.runAfterDelay(3, () -> {
            AbstractLapisTransducerBlock sensor = RedstoneEngineering.LAPIS_TEMPERATURE_TRANSDUCER.get();
            BlockState state = helper.getBlockState(sensorPos);
            if (!hasPort(sensor, state, Direction.WEST, EngineeringDomain.THERMAL, PortKind.MEASUREMENT, PortDirection.INPUT)
                    || !hasPort(sensor, state, Direction.EAST, EngineeringDomain.LAPIS, PortKind.SENSOR, PortDirection.OUTPUT)) {
                helper.fail("Temperature transducer did not expose THERMAL input -> LAPIS output port contract", sensorPos);
                return;
            }
            int output = sensor.output(helper.getLevel(), helper.absolutePos(sensorPos));
            if (!sensor.valid(helper.getLevel(), helper.absolutePos(sensorPos)) || output < 77 || output > 83) {
                helper.fail("Temperature transducer did not publish the expected bounded FAST-profile reading near 0.80", sensorPos);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void magneticTransducerPublishesFieldMeasurement(GameTestHelper helper) {
        BlockPos magnetPos = new BlockPos(1, 1, 2);
        BlockPos sensorPos = new BlockPos(2, 1, 2);

        helper.setBlock(magnetPos, RedstoneEngineering.PERMANENT_MAGNET.get().defaultBlockState()
                .setValue(PermanentMagnetBlock.STRENGTH, 15));
        helper.setBlock(sensorPos, fastTransducer(RedstoneEngineering.LAPIS_MAGNETIC_TRANSDUCER.get().defaultBlockState()));

        helper.runAfterDelay(3, () -> {
            AbstractLapisTransducerBlock sensor = RedstoneEngineering.LAPIS_MAGNETIC_TRANSDUCER.get();
            BlockState state = helper.getBlockState(sensorPos);
            if (!hasPort(sensor, state, Direction.WEST, EngineeringDomain.IRON_MAGNETIC, PortKind.MEASUREMENT, PortDirection.INPUT)
                    || !hasPort(sensor, state, Direction.EAST, EngineeringDomain.LAPIS, PortKind.SENSOR, PortDirection.OUTPUT)) {
                helper.fail("Magnetic transducer did not expose magnetic measurement and Lapis output ports", sensorPos);
                return;
            }
            int output = sensor.output(helper.getLevel(), helper.absolutePos(sensorPos));
            if (!sensor.valid(helper.getLevel(), helper.absolutePos(sensorPos)) || output <= 0) {
                helper.fail("Magnetic transducer did not produce a valid nonzero field reading beside a strength-15 magnet", sensorPos);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void opticalTransducerPreservesValidityAndScaling(GameTestHelper helper) {
        BlockPos emitterPos = new BlockPos(1, 1, 2);
        BlockPos sensorPos = new BlockPos(2, 1, 2);

        helper.setBlock(emitterPos, RedstoneEngineering.OPTICAL_EMITTER.get().defaultBlockState()
                .setValue(OpticalEmitterBlock.INTENSITY, 12)
                .setValue(OpticalEmitterBlock.CHANNEL, 3));
        helper.setBlock(sensorPos, fastTransducer(RedstoneEngineering.LAPIS_OPTICAL_TRANSDUCER.get().defaultBlockState()));

        helper.runAfterDelay(3, () -> {
            AbstractLapisTransducerBlock sensor = RedstoneEngineering.LAPIS_OPTICAL_TRANSDUCER.get();
            BlockState state = helper.getBlockState(sensorPos);
            if (!hasPort(sensor, state, Direction.WEST, EngineeringDomain.OPTICAL, PortKind.MEASUREMENT, PortDirection.INPUT)) {
                helper.fail("Optical transducer input was not classified as an OPTICAL measurement port", sensorPos);
                return;
            }
            int output = sensor.output(helper.getLevel(), helper.absolutePos(sensorPos));
            if (!sensor.valid(helper.getLevel(), helper.absolutePos(sensorPos)) || output < 77 || output > 83) {
                helper.fail("Optical intensity 12/15 was not converted to a bounded Lapis reading near 0.80", sensorPos);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void voltageTransducerReadsCopperNodeWithoutRedstoneCoupling(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(1, 1, 2);
        BlockPos sensorPos = new BlockPos(2, 1, 2);

        helper.setBlock(sourcePos, RedstoneEngineering.COPPER_VOLTAGE_SOURCE.get().defaultBlockState());
        helper.setBlock(sensorPos, fastTransducer(RedstoneEngineering.LAPIS_VOLTAGE_TRANSDUCER.get().defaultBlockState()));

        helper.runAfterDelay(3, () -> {
            AbstractLapisTransducerBlock sensor = RedstoneEngineering.LAPIS_VOLTAGE_TRANSDUCER.get();
            BlockState state = helper.getBlockState(sensorPos);
            if (!hasPort(sensor, state, Direction.WEST, EngineeringDomain.COPPER, PortKind.MEASUREMENT, PortDirection.INPUT)) {
                helper.fail("Voltage transducer input was not classified as a COPPER measurement port", sensorPos);
                return;
            }
            int output = sensor.output(helper.getLevel(), helper.absolutePos(sensorPos));
            if (!sensor.valid(helper.getLevel(), helper.absolutePos(sensorPos)) || output < 77 || output > 83) {
                helper.fail("Copper 12/15 source was not converted to a bounded Lapis reading near 0.80", sensorPos);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void precisionRangeSensorUsesConfiguredRangeAndExplicitSensePort(GameTestHelper helper) {
        BlockPos targetPos = new BlockPos(0, 1, 2);
        BlockPos sensorPos = new BlockPos(2, 1, 2);

        helper.setBlock(targetPos, Blocks.IRON_BLOCK.defaultBlockState());
        helper.setBlock(sensorPos, fastTransducer(RedstoneEngineering.LAPIS_PRECISION_RANGE_SENSOR.get().defaultBlockState())
                .setValue(LapisPrecisionRangeSensorBlock.RANGE_INDEX, 0));

        helper.runAfterDelay(3, () -> {
            AbstractLapisTransducerBlock sensor = RedstoneEngineering.LAPIS_PRECISION_RANGE_SENSOR.get();
            BlockState state = helper.getBlockState(sensorPos);
            if (!hasPort(sensor, state, Direction.WEST, EngineeringDomain.GENERIC, PortKind.MEASUREMENT, PortDirection.INPUT)
                    || !sensor.engineeringPort(state, Direction.WEST).orElseThrow().label().contains("RANGE")) {
                helper.fail("Precision range sensor did not expose its directional RANGE SENSE boundary", sensorPos);
                return;
            }
            int output = sensor.output(helper.getLevel(), helper.absolutePos(sensorPos));
            if (!sensor.valid(helper.getLevel(), helper.absolutePos(sensorPos)) || output < 22 || output > 28) {
                helper.fail("2-block target on an 8-block range did not produce a bounded Lapis reading near 0.25", sensorPos);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void lapisQuantizerMapsContinuousPrecisionToVanillaBoundary(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(1, 1, 2);
        BlockPos quantizerPos = new BlockPos(2, 1, 2);

        helper.setBlock(sourcePos, RedstoneEngineering.LAPIS_PRECISION_SOURCE.get().defaultBlockState()
                .setValue(LapisPrecisionSourceBlock.VALUE, 60));
        helper.setBlock(quantizerPos, RedstoneEngineering.LAPIS_TO_REDSTONE_QUANTIZER.get().defaultBlockState()
                .setValue(LapisToRedstoneQuantizerBlock.FACING, Direction.EAST));

        helper.runAfterDelay(3, () -> {
            BlockState state = helper.getBlockState(quantizerPos);
            if (state.getValue(LapisToRedstoneQuantizerBlock.POWER) != 9) {
                helper.fail("Lapis 0.60 did not quantize to vanilla redstone 9/15", quantizerPos);
                return;
            }
            if (!hasPort(RedstoneEngineering.LAPIS_TO_REDSTONE_QUANTIZER.get(), state, Direction.WEST,
                    EngineeringDomain.LAPIS, PortKind.CONVERTER, PortDirection.INPUT)
                    || !hasPort(RedstoneEngineering.LAPIS_TO_REDSTONE_QUANTIZER.get(), state, Direction.EAST,
                    EngineeringDomain.REDSTONE, PortKind.CONVERTER, PortDirection.OUTPUT)) {
                helper.fail("Lapis -> Redstone quantizer ports do not expose the domain boundary", quantizerPos);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void redstoneScalerMapsVanillaBoundaryToPrecisionDomain(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(1, 1, 2);
        BlockPos scalerPos = new BlockPos(2, 1, 2);

        helper.setBlock(sourcePos, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(scalerPos, RedstoneEngineering.REDSTONE_TO_LAPIS_SCALER.get().defaultBlockState()
                .setValue(RedstoneToLapisScalerBlock.FACING, Direction.EAST));

        helper.runAfterDelay(3, () -> {
            BlockState state = helper.getBlockState(scalerPos);
            if (!hasPort(RedstoneEngineering.REDSTONE_TO_LAPIS_SCALER.get(), state, Direction.WEST,
                    EngineeringDomain.REDSTONE, PortKind.CONVERTER, PortDirection.INPUT)
                    || !hasPort(RedstoneEngineering.REDSTONE_TO_LAPIS_SCALER.get(), state, Direction.EAST,
                    EngineeringDomain.LAPIS, PortKind.CONVERTER, PortDirection.OUTPUT)) {
                helper.fail("Redstone -> Lapis scaler ports do not expose the domain boundary", scalerPos);
                return;
            }
            var snapshot = RedstoneEngineering.REDSTONE_TO_LAPIS_SCALER.get().engineeringSnapshot(
                    helper.getLevel(), helper.absolutePos(scalerPos), state, Direction.EAST).orElse(null);
            if (snapshot == null || snapshot.value() < 0.99) {
                helper.fail("Vanilla redstone 15/15 did not scale to Lapis precision 1.00", scalerPos);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void quartzTriggeredSamplerCapturesAndHoldsOnRisingEdge(GameTestHelper helper) {
        BlockPos lapisPos = new BlockPos(1, 1, 2);
        BlockPos samplerPos = new BlockPos(2, 1, 2);
        BlockPos quartzPos = new BlockPos(2, 1, 1);

        helper.setBlock(lapisPos, RedstoneEngineering.LAPIS_PRECISION_SOURCE.get().defaultBlockState()
                .setValue(LapisPrecisionSourceBlock.VALUE, 70));
        helper.setBlock(quartzPos, RedstoneEngineering.QUARTZ_OSCILLATOR.get().defaultBlockState()
                .setValue(QuartzOscillatorBlock.PERIOD_INDEX, 0));
        helper.setBlock(samplerPos, RedstoneEngineering.QUARTZ_TRIGGERED_LAPIS_SAMPLER.get().defaultBlockState()
                .setValue(QuartzTriggeredLapisSamplerBlock.FACING, Direction.EAST));

        helper.runAfterDelay(8, () -> {
            BlockState state = helper.getBlockState(samplerPos);
            EngineeringPortProvider sampler = RedstoneEngineering.QUARTZ_TRIGGERED_LAPIS_SAMPLER.get();
            if (!hasPort(sampler, state, Direction.WEST, EngineeringDomain.LAPIS, PortKind.MEASUREMENT, PortDirection.INPUT)
                    || !hasPort(sampler, state, Direction.NORTH, EngineeringDomain.QUARTZ, PortKind.TRIGGER, PortDirection.INPUT)
                    || !hasPort(sampler, state, Direction.EAST, EngineeringDomain.LAPIS, PortKind.MEASUREMENT, PortDirection.OUTPUT)) {
                helper.fail("Triggered sampler did not expose BACK Lapis, LEFT Quartz and FRONT held-output ports", samplerPos);
                return;
            }
            var output = sampler.engineeringSnapshot(
                    helper.getLevel(), helper.absolutePos(samplerPos), state, Direction.EAST).orElse(null);
            if (output == null || output.quality().name().equals("NO_SIGNAL") || Math.abs(output.value() - 0.70) > 0.001) {
                helper.fail("Quartz rising edge did not capture and hold the 0.70 Lapis sample", samplerPos);
                return;
            }
            helper.succeed();
        });
    }

    private static BlockState fastTransducer(BlockState state) {
        return state
                .setValue(AbstractLapisTransducerBlock.FACING, Direction.EAST)
                .setValue(AbstractLapisTransducerBlock.PROFILE, 0);
    }

    private static boolean hasPort(
            EngineeringPortProvider provider,
            BlockState state,
            Direction side,
            EngineeringDomain domain,
            PortKind kind,
            PortDirection direction
    ) {
        var port = provider.engineeringPort(state, side);
        return port.isPresent()
                && port.get().domain() == domain
                && port.get().kind() == kind
                && port.get().direction() == direction;
    }
}
