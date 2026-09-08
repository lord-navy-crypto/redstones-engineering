package dev.redstoneengineering.gametest;

import dev.redstoneengineering.EngineeringSystemsModule;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.AlarmProcessorBlock;
import dev.redstoneengineering.block.AnalogIndicatorBlock;
import dev.redstoneengineering.block.ConnectedCableBlock;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.FaultInjectorBlock;
import dev.redstoneengineering.block.InstrumentCableBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.ServoActuatorBlock;
import dev.redstoneengineering.block.ServoPositionSensorBlock;
import dev.redstoneengineering.block.SignalConditionerBlock;
import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * System-Level Closure Phase 1: deterministic multi-block chains and recovery behavior.
 *
 * <p>These tests intentionally cross component boundaries. The preceding 122-block closure proves
 * local contracts; this suite proves that representative contracts compose without stale state,
 * ghost runtime, unsafe loss-of-signal behavior, or broken cross-domain handoff.</p>
 */
public final class RseSystemLevelClosurePhase1GameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseSystemLevelClosurePhase1GameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void conditionedSignalChainConvergesAcrossRapidBoundaryChanges(GameTestHelper helper) {
        BlockPos source = new BlockPos(0, 1, 2);
        BlockPos conditioner = new BlockPos(1, 1, 2);
        BlockPos indicator = new BlockPos(2, 1, 2);

        helper.setBlock(source, reference(Direction.EAST, 0));
        helper.setBlock(conditioner, conditioner(Direction.EAST, 0, 4));
        helper.setBlock(indicator, indicator(Direction.EAST));

        helper.runAfterDelay(4, () -> {
            assertConditionedSignal(helper, conditioner, indicator, 0, "initial LOW did not settle");
            helper.setBlock(source, reference(Direction.EAST, 15));
            helper.runAfterDelay(3, () -> {
                assertConditionedSignal(helper, conditioner, indicator, 15, "LOW->HIGH did not converge");
                helper.setBlock(source, reference(Direction.EAST, 0));
                helper.runAfterDelay(3, () -> {
                    assertConditionedSignal(helper, conditioner, indicator, 0, "HIGH->LOW retained stale output");
                    helper.setBlock(source, reference(Direction.EAST, 15));
                    helper.runAfterDelay(3, () -> {
                        assertConditionedSignal(helper, conditioner, indicator, 15, "second LOW->HIGH did not converge");
                        helper.setBlock(source, reference(Direction.EAST, 0));
                        helper.runAfterDelay(3, () -> {
                            assertConditionedSignal(helper, conditioner, indicator, 0, "second HIGH->LOW retained stale state");
                            helper.succeed();
                        });
                    });
                });
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 120)
    public static void servoActuationMeasurementChainCrossesDomainsAndSettles(GameTestHelper helper) {
        BlockPos source = new BlockPos(0, 1, 2);
        BlockPos servo = new BlockPos(1, 1, 2);
        BlockPos sensor = new BlockPos(2, 1, 2);
        BlockPos indicator = new BlockPos(3, 1, 2);

        helper.setBlock(source, reference(Direction.EAST, 12));
        helper.setBlock(servo, RedstoneEngineering.SERVO_ACTUATOR.get().defaultBlockState()
                .setValue(ServoActuatorBlock.FACING, Direction.EAST)
                .setValue(ServoActuatorBlock.SLEW, 2));
        helper.setBlock(sensor, RedstoneEngineering.SERVO_POSITION_SENSOR.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));
        helper.setBlock(indicator, indicator(Direction.EAST));

        helper.runAfterDelay(28, () -> {
            int position = ServoActuatorBlock.position(helper.getLevel(), helper.absolutePos(servo));
            int sensorOutput = helper.getBlockState(sensor).getValue(DirectionalSignalBlock.OUTPUT);
            int displayed = helper.getBlockState(indicator).getValue(AnalogIndicatorBlock.LEVEL);
            PortQuality quality = ServoPositionSensorBlock.sourceQuality(
                    helper.getLevel(), helper.absolutePos(sensor), helper.getBlockState(sensor));

            if (position != 12) {
                helper.fail("Servo plant did not settle at commanded position 12; position=" + position, servo); return;
            }
            if (quality != PortQuality.VALID) {
                helper.fail("Servo-position mechanical handoff was not VALID; quality=" + quality, sensor); return;
            }
            if (Math.abs(sensorOutput - position) > 1) {
                helper.fail("Position sensor exceeded bounded metrology error; position=" + position
                        + ", sensor=" + sensorOutput, sensor); return;
            }
            if (displayed != sensorOutput) {
                helper.fail("Indicator did not faithfully display measured servo feedback; sensor="
                        + sensorOutput + ", display=" + displayed, indicator); return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 150)
    public static void servoCommandLossBrakesPlantAndRecoveryResumesMeasurementChain(GameTestHelper helper) {
        BlockPos source = new BlockPos(0, 1, 2);
        BlockPos servo = new BlockPos(1, 1, 2);
        BlockPos sensor = new BlockPos(2, 1, 2);
        BlockPos indicator = new BlockPos(3, 1, 2);

        helper.setBlock(source, reference(Direction.EAST, 10));
        helper.setBlock(servo, RedstoneEngineering.SERVO_ACTUATOR.get().defaultBlockState()
                .setValue(ServoActuatorBlock.FACING, Direction.EAST)
                .setValue(ServoActuatorBlock.SLEW, 2));
        helper.setBlock(sensor, RedstoneEngineering.SERVO_POSITION_SENSOR.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));
        helper.setBlock(indicator, indicator(Direction.EAST));

        helper.runAfterDelay(24, () -> {
            BlockPos servoWorld = helper.absolutePos(servo);
            if (ServoActuatorBlock.position(helper.getLevel(), servoWorld) != 10) {
                helper.fail("Servo did not reach pre-fault command 10", servo); return;
            }
            helper.setBlock(source, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(8, () -> {
                int held = ServoActuatorBlock.position(helper.getLevel(), servoWorld);
                if (!ServoActuatorBlock.braking(helper.getLevel(), servoWorld) || held != 10) {
                    helper.fail("Command loss did not fail safe to brake-and-hold; braking="
                            + ServoActuatorBlock.braking(helper.getLevel(), servoWorld) + ", position=" + held, servo); return;
                }

                helper.setBlock(source, reference(Direction.EAST, 4));
                helper.runAfterDelay(24, () -> {
                    int recovered = ServoActuatorBlock.position(helper.getLevel(), servoWorld);
                    int sensorOutput = helper.getBlockState(sensor).getValue(DirectionalSignalBlock.OUTPUT);
                    int displayed = helper.getBlockState(indicator).getValue(AnalogIndicatorBlock.LEVEL);
                    if (ServoActuatorBlock.braking(helper.getLevel(), servoWorld)) {
                        helper.fail("Servo remained braked after command evidence recovered", servo); return;
                    }
                    if (recovered != 4) {
                        helper.fail("Servo did not reconverge to recovered command 4; position=" + recovered, servo); return;
                    }
                    if (Math.abs(sensorOutput - recovered) > 1 || displayed != sensorOutput) {
                        helper.fail("Recovered plant measurement chain did not reconverge; position=" + recovered
                                + ", sensor=" + sensorOutput + ", display=" + displayed, sensor); return;
                    }
                    helper.succeed();
                });
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 140)
    public static void injectedFaultAlarmLifecycleRecoversWithoutGhostRelatch(GameTestHelper helper) {
        BlockPos injector = new BlockPos(1, 1, 2);
        BlockPos alarm = new BlockPos(2, 1, 2);
        BlockPos arm = new BlockPos(1, 1, 3);
        BlockPos reset = new BlockPos(2, 1, 3);

        helper.setBlock(injector, EngineeringSystemsModule.FAULT_INJECTOR.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(FaultInjectorBlock.MODE, 1));
        helper.setBlock(alarm, EngineeringSystemsModule.ALARM_PROCESSOR.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(AlarmProcessorBlock.SEVERITY, 2));
        helper.setBlock(arm, Blocks.REDSTONE_BLOCK.defaultBlockState());

        helper.runAfterDelay(6, () -> {
            if (helper.getBlockState(injector).getValue(DirectionalSignalBlock.OUTPUT) != 15
                    || !AlarmProcessorBlock.latched(helper.getLevel(), helper.absolutePos(alarm))) {
                helper.fail("Injected fault did not propagate into the alarm chain", alarm); return;
            }

            helper.setBlock(injector, Blocks.AIR.defaultBlockState());
            helper.setBlock(arm, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(5, () -> {
                if (!AlarmProcessorBlock.latched(helper.getLevel(), helper.absolutePos(alarm))) {
                    helper.fail("Alarm lost its latch before explicit healthy reset", alarm); return;
                }
                helper.setBlock(reset, Blocks.REDSTONE_BLOCK.defaultBlockState());
                helper.runAfterDelay(4, () -> {
                    if (AlarmProcessorBlock.latched(helper.getLevel(), helper.absolutePos(alarm))
                            || helper.getBlockState(alarm).getValue(DirectionalSignalBlock.OUTPUT) != 0) {
                        helper.fail("Healthy reset did not clear recovered fault chain", alarm); return;
                    }

                    helper.setBlock(reset, Blocks.AIR.defaultBlockState());
                    helper.setBlock(injector, EngineeringSystemsModule.FAULT_INJECTOR.get().defaultBlockState()
                            .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                            .setValue(FaultInjectorBlock.MODE, 1));
                    helper.runAfterDelay(5, () -> {
                        BlockPos injectorWorld = helper.absolutePos(injector);
                        if (FaultInjectorBlock.active(helper.getLevel(), injectorWorld)
                                || helper.getBlockState(injector).getValue(DirectionalSignalBlock.OUTPUT) != 0
                                || AlarmProcessorBlock.latched(helper.getLevel(), helper.absolutePos(alarm))) {
                            helper.fail("Recovered chain re-latched from ghost injector runtime", alarm); return;
                        }
                        helper.succeed();
                    });
                });
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void instrumentCableRemovalAndReplacementRecoversSymmetricTopology(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 1, 2);
        BlockPos b = new BlockPos(2, 1, 2);
        helper.setBlock(a, RedstoneEngineering.INSTRUMENT_CABLE.get().defaultBlockState());
        helper.setBlock(b, RedstoneEngineering.INSTRUMENT_CABLE.get().defaultBlockState());

        helper.runAfterDelay(3, () -> {
            BlockState aState = helper.getBlockState(a);
            BlockState bState = helper.getBlockState(b);
            if (!ConnectedCableBlock.connected(aState, Direction.EAST)
                    || !ConnectedCableBlock.connected(bState, Direction.WEST)) {
                helper.fail("Initial instrument cable link was not symmetric", a); return;
            }
            if (!(aState.getBlock() instanceof InstrumentCableBlock aCable)
                    || !(bState.getBlock() instanceof InstrumentCableBlock bCable)) {
                helper.fail("Instrument cable registry resolved to an unexpected block", a); return;
            }
            var aSnapshot = aCable.engineeringSnapshot(
                    helper.getLevel(), helper.absolutePos(a), aState, Direction.EAST);
            var bSnapshot = bCable.engineeringSnapshot(
                    helper.getLevel(), helper.absolutePos(b), bState, Direction.WEST);
            if (aSnapshot.isEmpty() || bSnapshot.isEmpty()
                    || aSnapshot.get().quality() != PortQuality.NO_SIGNAL
                    || bSnapshot.get().quality() != PortQuality.NO_SIGNAL) {
                helper.fail("Connected empty instrument bus must retain topology while reporting symmetric NO_SIGNAL evidence", a); return;
            }

            helper.setBlock(b, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(3, () -> {
                BlockState detached = helper.getBlockState(a);
                if (ConnectedCableBlock.connected(detached, Direction.EAST)) {
                    helper.fail("Cable retained ghost physical connection after endpoint removal", a); return;
                }
                InstrumentCableBlock detachedCable = (InstrumentCableBlock) detached.getBlock();
                var detachedSnapshot = detachedCable.engineeringSnapshot(
                        helper.getLevel(), helper.absolutePos(a), detached, Direction.EAST);
                if (detachedSnapshot.isEmpty() || detachedSnapshot.get().quality() != PortQuality.NO_SIGNAL) {
                    helper.fail("Open planar cable capability must remain observable as NO_SIGNAL rather than ghost VALID evidence", a); return;
                }

                helper.setBlock(b, RedstoneEngineering.INSTRUMENT_CABLE.get().defaultBlockState());
                helper.runAfterDelay(3, () -> {
                    BlockState rebuiltA = helper.getBlockState(a);
                    BlockState rebuiltB = helper.getBlockState(b);
                    if (!ConnectedCableBlock.connected(rebuiltA, Direction.EAST)
                            || !ConnectedCableBlock.connected(rebuiltB, Direction.WEST)) {
                        helper.fail("Instrument link did not rebuild symmetrically", a); return;
                    }
                    InstrumentCableBlock rebuiltACable = (InstrumentCableBlock) rebuiltA.getBlock();
                    InstrumentCableBlock rebuiltBCable = (InstrumentCableBlock) rebuiltB.getBlock();
                    var rebuiltASnapshot = rebuiltACable.engineeringSnapshot(
                            helper.getLevel(), helper.absolutePos(a), rebuiltA, Direction.EAST);
                    var rebuiltBSnapshot = rebuiltBCable.engineeringSnapshot(
                            helper.getLevel(), helper.absolutePos(b), rebuiltB, Direction.WEST);
                    if (rebuiltASnapshot.isEmpty() || rebuiltBSnapshot.isEmpty()
                            || rebuiltASnapshot.get().quality() != PortQuality.NO_SIGNAL
                            || rebuiltBSnapshot.get().quality() != PortQuality.NO_SIGNAL) {
                        helper.fail("Rebuilt empty bus must restore symmetric topology without fabricating measurement evidence", a); return;
                    }
                    helper.succeed();
                });
            });
        });
    }

    private static BlockState reference(Direction facing, int power) {
        return RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, facing)
                .setValue(RedstoneReferenceSourceBlock.POWER, power);
    }

    private static BlockState conditioner(Direction facing, int mode, int param) {
        return RedstoneEngineering.SIGNAL_CONDITIONER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, facing)
                .setValue(SignalConditionerBlock.MODE, mode)
                .setValue(SignalConditionerBlock.PARAM, param);
    }

    private static BlockState indicator(Direction facing) {
        return RedstoneEngineering.ANALOG_INDICATOR.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, facing);
    }

    private static void assertConditionedSignal(
            GameTestHelper helper, BlockPos conditioner, BlockPos indicator, int expected, String message
    ) {
        int conditionerOutput = helper.getBlockState(conditioner).getValue(DirectionalSignalBlock.OUTPUT);
        int indicatorLevel = helper.getBlockState(indicator).getValue(AnalogIndicatorBlock.LEVEL);
        if (conditionerOutput != expected || indicatorLevel != expected) {
            helper.fail(message + " (conditioner=" + conditionerOutput + ", indicator=" + indicatorLevel
                    + ", expected=" + expected + ")", conditioner);
        }
    }
}
