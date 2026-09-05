package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.block.ElectromagnetBlock;
import dev.redstoneengineering.block.InductionCoilBlock;
import dev.redstoneengineering.block.MagneticFieldSensorBlock;
import dev.redstoneengineering.block.MagneticGradientMeterBlock;
import dev.redstoneengineering.block.PermanentMagnetBlock;
import dev.redstoneengineering.block.PneumaticCylinderBlock;
import dev.redstoneengineering.block.PneumaticReliefValveBlock;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.MagneticPhysics;
import dev.redstoneengineering.physics.PneumaticNetwork;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Eleventh block-by-block campaign: pneumatic actuation/safety and magnetic field integrity. */
public final class RseEleventhEightAcceptanceGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseEleventhEightAcceptanceGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 70)
    public static void proportionalValveUsesAxialPressureAndUpCommand(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 2);
        BlockPos valve = new BlockPos(2, 2, 2);
        BlockPos front = new BlockPos(3, 2, 2);
        BlockPos side = new BlockPos(2, 2, 1);
        placePoweredCompressor(helper, source);
        helper.setBlock(valve, RedstoneEngineering.PNEUMATIC_PROPORTIONAL_VALVE.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(valve.above(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(front, RedstoneEngineering.PNEUMATIC_PIPE.get().defaultBlockState());
        helper.setBlock(side, RedstoneEngineering.PNEUMATIC_PIPE.get().defaultBlockState());
        recomputePneumatic(helper, source);

        helper.runAfterDelay(3, () -> {
            BlockState state = helper.getBlockState(valve);
            EngineeringPortProvider provider = RedstoneEngineering.PNEUMATIC_PROPORTIONAL_VALVE.get();
            if (provider.engineeringPorts(state).size() != 3
                    || provider.engineeringPort(state, Direction.WEST).orElseThrow().domain() != EngineeringDomain.PNEUMATIC
                    || provider.engineeringPort(state, Direction.WEST).orElseThrow().direction() != PortDirection.INPUT
                    || provider.engineeringPort(state, Direction.EAST).orElseThrow().direction() != PortDirection.OUTPUT
                    || provider.engineeringPort(state, Direction.UP).orElseThrow().domain() != EngineeringDomain.REDSTONE
                    || PneumaticNetwork.pressure(helper.getLevel(), helper.absolutePos(front)) <= 0
                    || PneumaticNetwork.pressure(helper.getLevel(), helper.absolutePos(side)) != 0) {
                helper.fail("Proportional valve domain or axial flow contract is false", valve);
                return;
            }
            helper.setBlock(valve.above(), Blocks.AIR.defaultBlockState());
            PneumaticNetwork.recomputeAround(helper.getLevel(), helper.absolutePos(valve));
            helper.runAfterDelay(2, () -> {
                if (PneumaticNetwork.pressure(helper.getLevel(), helper.absolutePos(front)) != 0) {
                    helper.fail("Zero opening command did not isolate proportional-valve output", front);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 70)
    public static void reliefValveClampsAndCountsOneOverpressureEpisode(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 2);
        BlockPos valve = new BlockPos(2, 2, 2);
        BlockPos front = new BlockPos(3, 2, 2);
        placePoweredCompressor(helper, source);
        helper.setBlock(valve, RedstoneEngineering.PNEUMATIC_RELIEF_VALVE.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                .setValue(PneumaticReliefValveBlock.SETPOINT, 2));
        helper.setBlock(front, RedstoneEngineering.PNEUMATIC_PIPE.get().defaultBlockState());
        recomputePneumatic(helper, source);

        helper.runAfterDelay(3, () -> {
            BlockPos world = helper.absolutePos(valve);
            int events = PneumaticReliefValveBlock.ventEvents(helper.getLevel(), world);
            if (!PneumaticReliefValveBlock.venting(helper.getLevel(), world)
                    || events != 1
                    || PneumaticNetwork.pressure(helper.getLevel(), world) != 50
                    || PneumaticNetwork.pressure(helper.getLevel(), helper.absolutePos(front)) != 49) {
                helper.fail("Relief valve did not clamp 50/100 or edge-count its vent episode", valve);
                return;
            }
            PneumaticNetwork.recompute(helper.getLevel(), world);
            if (PneumaticReliefValveBlock.ventEvents(helper.getLevel(), world) != events) {
                helper.fail("Repeated solver pass double-counted one relief episode", valve);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void pneumaticCylinderPublishesFeedbackAndCleansRuntime(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 2);
        BlockPos cylinder = new BlockPos(2, 2, 2);
        placePoweredCompressor(helper, source);
        helper.setBlock(cylinder, RedstoneEngineering.PNEUMATIC_CYLINDER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        recomputePneumatic(helper, source);

        helper.runAfterDelay(12, () -> {
            BlockPos world = helper.absolutePos(cylinder);
            EngineeringPortProvider provider = RedstoneEngineering.PNEUMATIC_CYLINDER.get();
            BlockState state = helper.getBlockState(cylinder);
            if (provider.engineeringPorts(state).size() != 2
                    || provider.engineeringPort(state, Direction.WEST).orElseThrow().domain() != EngineeringDomain.PNEUMATIC
                    || provider.engineeringPort(state, Direction.EAST).orElseThrow().kind() != PortKind.FEEDBACK
                    || PneumaticCylinderBlock.position(helper.getLevel(), world) <= 0
                    || PneumaticCylinderBlock.target(helper.getLevel(), world) <= 0) {
                helper.fail("Cylinder failed PNEUMATIC BACK to REDSTONE FRONT feedback actuation", cylinder);
                return;
            }
            helper.setBlock(cylinder, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(2, () -> {
                if (RuntimeIntStore.peek(helper.getLevel(), "pneumatic_cylinder", world) != null) {
                    helper.fail("Removed cylinder retained actuator runtime", cylinder);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void electromagnetConvertsCopperWithoutRedstoneLeak(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 2);
        BlockPos magnet = new BlockPos(2, 1, 2);
        helper.setBlock(source, RedstoneEngineering.COPPER_VOLTAGE_SOURCE.get().defaultBlockState());
        helper.setBlock(magnet, RedstoneEngineering.ELECTROMAGNET.get().defaultBlockState());
        DomainNetwork.recomputeCopper(helper.getLevel(), helper.absolutePos(source));

        helper.runAfterDelay(3, () -> {
            BlockState state = helper.getBlockState(magnet);
            EngineeringPortProvider provider = RedstoneEngineering.ELECTROMAGNET.get();
            if (state.getValue(ElectromagnetBlock.FIELD) <= 0
                    || provider.engineeringPorts(state).size() != 6
                    || provider.engineeringPorts(state).stream().anyMatch(port ->
                    port.domain() != EngineeringDomain.COPPER || port.direction() != PortDirection.INPUT)
                    || RedstoneEngineering.ELECTROMAGNET.get().canConnectRedstone(
                    state, helper.getLevel(), helper.absolutePos(magnet), Direction.WEST)) {
                helper.fail("Electromagnet did not expose six copper load inputs without redstone leakage", magnet);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void permanentMagnetIsFreeSpaceSourceNotWiredPort(GameTestHelper helper) {
        BlockPos magnet = new BlockPos(1, 1, 2);
        BlockPos sample = new BlockPos(2, 1, 2);
        helper.setBlock(magnet, RedstoneEngineering.PERMANENT_MAGNET.get().defaultBlockState()
                .setValue(PermanentMagnetBlock.STRENGTH, 15));
        EngineeringPortProvider provider = RedstoneEngineering.PERMANENT_MAGNET.get();
        BlockState state = helper.getBlockState(magnet);
        if (provider.engineeringPorts(state).size() != 6
                || provider.engineeringPorts(state).stream().anyMatch(port ->
                port.domain() != EngineeringDomain.IRON_MAGNETIC
                        || port.direction() != PortDirection.OUTPUT
                        || port.redstoneConnectable())
                || MagneticPhysics.fieldAt(helper.getLevel(), helper.absolutePos(sample), 6) <= 0
                || RedstoneEngineering.PERMANENT_MAGNET.get().canConnectRedstone(
                state, helper.getLevel(), helper.absolutePos(magnet), Direction.WEST)) {
            helper.fail("Permanent magnet free-space interface became wired/redstone or failed its field", magnet);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void inductionCoilPulsesOnFluxChangeAndHasAxialDomains(GameTestHelper helper) {
        BlockPos magnet = new BlockPos(1, 1, 2);
        BlockPos coil = new BlockPos(2, 1, 2);
        BlockPos output = new BlockPos(3, 1, 2);
        helper.setBlock(magnet, RedstoneEngineering.PERMANENT_MAGNET.get().defaultBlockState()
                .setValue(PermanentMagnetBlock.STRENGTH, 1));
        helper.setBlock(coil, RedstoneEngineering.INDUCTION_COIL.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                .setValue(InductionCoilBlock.TURNS, 4));
        helper.setBlock(output, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState());

        helper.runAfterDelay(3, () -> {
            BlockState changed = helper.getBlockState(magnet).setValue(PermanentMagnetBlock.STRENGTH, 15);
            helper.setBlock(magnet, changed);
            helper.getLevel().scheduleTick(helper.absolutePos(coil), RedstoneEngineering.INDUCTION_COIL.get(), 1);
            helper.runAfterDelay(1, () -> {
                EngineeringPortProvider provider = RedstoneEngineering.INDUCTION_COIL.get();
                BlockState state = helper.getBlockState(coil);
                if (provider.engineeringPort(state, Direction.WEST).orElseThrow().domain() != EngineeringDomain.IRON_MAGNETIC
                        || provider.engineeringPort(state, Direction.EAST).orElseThrow().domain() != EngineeringDomain.COPPER
                        || InductionCoilBlock.outputVoltage(helper.getLevel(), helper.absolutePos(coil)) <= 0) {
                    helper.fail("Induction coil did not convert a flux change into axial copper output", coil);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void magneticFieldSensorTracksSourceRemovalWithoutOutput(GameTestHelper helper) {
        BlockPos magnet = new BlockPos(1, 1, 2);
        BlockPos sensor = new BlockPos(2, 1, 2);
        helper.setBlock(magnet, RedstoneEngineering.PERMANENT_MAGNET.get().defaultBlockState()
                .setValue(PermanentMagnetBlock.STRENGTH, 15));
        helper.setBlock(sensor, RedstoneEngineering.MAGNETIC_FIELD_SENSOR.get().defaultBlockState());

        helper.runAfterDelay(7, () -> {
            EngineeringPortProvider provider = RedstoneEngineering.MAGNETIC_FIELD_SENSOR.get();
            BlockState state = helper.getBlockState(sensor);
            if (state.getValue(MagneticFieldSensorBlock.FIELD) <= 0
                    || provider.engineeringPorts(state).size() != 6
                    || provider.engineeringPorts(state).stream().anyMatch(port ->
                    port.domain() != EngineeringDomain.IRON_MAGNETIC
                            || port.kind() != PortKind.MEASUREMENT
                            || port.direction() != PortDirection.INPUT
                            || port.redstoneConnectable())) {
                helper.fail("Magnetic field sensor failed non-wired observer-only free-space sensing", sensor);
                return;
            }
            helper.setBlock(magnet, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(6, () -> {
                if (helper.getBlockState(sensor).getValue(MagneticFieldSensorBlock.FIELD) != 0) {
                    helper.fail("Magnetic field sensor retained stale field after source removal", sensor);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void magneticGradientMeterReportsSpatialDifferenceWithoutDriving(GameTestHelper helper) {
        BlockPos meter = new BlockPos(2, 1, 2);
        BlockPos magnet = meter.east();
        helper.setBlock(meter, RedstoneEngineering.MAGNETIC_GRADIENT_METER.get().defaultBlockState());
        helper.setBlock(magnet, RedstoneEngineering.PERMANENT_MAGNET.get().defaultBlockState()
                .setValue(PermanentMagnetBlock.STRENGTH, 15));
        BlockPos world = helper.absolutePos(meter);
        EngineeringPortProvider provider = RedstoneEngineering.MAGNETIC_GRADIENT_METER.get();
        BlockState state = helper.getBlockState(meter);
        if (MagneticPhysics.fieldAt(helper.getLevel(), world, 6) <= 0
                || MagneticGradientMeterBlock.gradientX(helper.getLevel(), world) == 0
                || provider.engineeringPorts(state).size() != 6
                || provider.engineeringPorts(state).stream().anyMatch(port ->
                port.domain() != EngineeringDomain.IRON_MAGNETIC
                        || port.kind() != PortKind.MEASUREMENT
                        || port.direction() != PortDirection.INPUT
                        || port.redstoneConnectable())
                || RedstoneEngineering.MAGNETIC_GRADIENT_METER.get().canConnectRedstone(
                state, helper.getLevel(), world, Direction.WEST)) {
            helper.fail("Gradient meter failed non-wired observer-only spatial-field measurement", meter);
            return;
        }
        helper.succeed();
    }

    private static void placePoweredCompressor(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, RedstoneEngineering.AIR_COMPRESSOR.get().defaultBlockState());
        helper.setBlock(pos.below(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(pos.above(), RedstoneEngineering.PNEUMATIC_PIPE.get().defaultBlockState());
    }

    private static void recomputePneumatic(GameTestHelper helper, BlockPos pos) {
        PneumaticNetwork.recompute(helper.getLevel(), helper.absolutePos(pos));
    }
}
