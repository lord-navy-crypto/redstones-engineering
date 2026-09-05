package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.FaultLatchBlock;
import dev.redstoneengineering.block.OperationsMonitorBlock;
import dev.redstoneengineering.block.PidControllerBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.RedundantVoterBlock;
import dev.redstoneengineering.block.ServoActuatorBlock;
import dev.redstoneengineering.block.ServoPositionSensorBlock;
import dev.redstoneengineering.block.WatchdogBlock;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.instrument.InstrumentShieldingAudit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Eighth block-by-block acceptance campaign: CPS reliability, feedback and operations evidence. */
public final class RseEighthEightAcceptanceGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseEighthEightAcceptanceGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void pidExposesSixControlPortsAndInhibitDominates(GameTestHelper helper) {
        BlockPos pidPos = new BlockPos(2, 1, 2);
        BlockPos setpointPos = pidPos.west();
        BlockPos inhibitPos = pidPos.south();
        BlockState pidState = RedstoneEngineering.PID_CONTROLLER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST);
        helper.setBlock(setpointPos, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(inhibitPos, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(pidPos, pidState);

        helper.runAfterDelay(4, () -> {
            EngineeringPortProvider provider = RedstoneEngineering.PID_CONTROLLER.get();
            BlockState state = helper.getBlockState(pidPos);
            if (provider.engineeringPorts(state).size() != 6
                    || provider.engineeringPort(state, Direction.WEST).orElseThrow().direction() != PortDirection.INPUT
                    || provider.engineeringPort(state, Direction.NORTH).orElseThrow().direction() != PortDirection.INPUT
                    || provider.engineeringPort(state, Direction.SOUTH).orElseThrow().direction() != PortDirection.INPUT
                    || provider.engineeringPort(state, Direction.UP).orElseThrow().direction() != PortDirection.INPUT
                    || provider.engineeringPort(state, Direction.DOWN).orElseThrow().direction() != PortDirection.INPUT
                    || provider.engineeringPort(state, Direction.EAST).orElseThrow().direction() != PortDirection.OUTPUT) {
                helper.fail("PID did not expose its complete six-port control contract", pidPos);
                return;
            }
            if (state.getValue(DirectionalSignalBlock.OUTPUT) != 0) {
                helper.fail("PID INHIBIT input did not dominate a high setpoint", pidPos);
                return;
            }
            helper.setBlock(inhibitPos, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(5, () -> {
                if (helper.getBlockState(pidPos).getValue(DirectionalSignalBlock.OUTPUT) <= 0) {
                    helper.fail("PID remained inhibited after the safety input was removed", pidPos);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 90)
    public static void watchdogTimesOutAndHeartbeatRecovers(GameTestHelper helper) {
        BlockPos watchdogPos = new BlockPos(2, 1, 2);
        BlockPos heartbeatPos = watchdogPos.west();
        helper.setBlock(watchdogPos, RedstoneEngineering.WATCHDOG.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));

        helper.runAfterDelay(46, () -> {
            if (helper.getBlockState(watchdogPos).getValue(DirectionalSignalBlock.OUTPUT) != 15
                    || WatchdogBlock.timeoutCount(helper.getLevel(), helper.absolutePos(watchdogPos)) < 1) {
                helper.fail("Watchdog did not assert its timeout after the configured heartbeat age", watchdogPos);
                return;
            }
            helper.setBlock(heartbeatPos, Blocks.REDSTONE_BLOCK.defaultBlockState());
            helper.runAfterDelay(4, () -> {
                if (helper.getBlockState(watchdogPos).getValue(DirectionalSignalBlock.OUTPUT) != 0
                        || WatchdogBlock.transitionCount(helper.getLevel(), helper.absolutePos(watchdogPos)) < 1) {
                    helper.fail("Heartbeat transition did not clear the watchdog timeout", watchdogPos);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void shieldedInstrumentCableReportsCoverageWithoutChangingSolver(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 1, 2);
        BlockPos b = new BlockPos(2, 1, 2);
        BlockPos c = new BlockPos(3, 1, 2);
        helper.setBlock(a, RedstoneEngineering.SHIELDED_INSTRUMENT_CABLE.get().defaultBlockState());
        helper.setBlock(b, RedstoneEngineering.SHIELDED_INSTRUMENT_CABLE.get().defaultBlockState());
        helper.setBlock(c, RedstoneEngineering.SHIELDED_INSTRUMENT_CABLE.get().defaultBlockState());

        helper.runAfterDelay(3, () -> {
            var fullyShielded = InstrumentShieldingAudit.inspect(helper.getLevel(), helper.absolutePos(b));
            if (!fullyShielded.bounded() || fullyShielded.cableNodes() != 3
                    || fullyShielded.coveragePercent() != 100
                    || !"FULLY_SHIELDED".equals(fullyShielded.integrity())) {
                helper.fail("All-shielded instrument segment did not report 100% shielding coverage", b);
                return;
            }
            helper.setBlock(c, RedstoneEngineering.INSTRUMENT_CABLE.get().defaultBlockState());
            helper.runAfterDelay(3, () -> {
                var mixed = InstrumentShieldingAudit.inspect(helper.getLevel(), helper.absolutePos(b));
                if (mixed.cableNodes() != 3 || mixed.unshieldedNodes() != 1
                        || mixed.coveragePercent() >= 100
                        || !"MIXED_SHIELDING".equals(mixed.integrity())) {
                    helper.fail("Mixed instrument segment did not expose its unshielded hop", b);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void servoActuatorPublishesMechanicalPositionNotRedstoneFront(GameTestHelper helper) {
        BlockPos servoPos = new BlockPos(2, 1, 2);
        BlockPos commandPos = servoPos.west();
        BlockState servoState = RedstoneEngineering.SERVO_ACTUATOR.get().defaultBlockState()
                .setValue(ServoActuatorBlock.FACING, Direction.EAST);
        helper.setBlock(commandPos, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(servoPos, servoState);

        helper.runAfterDelay(10, () -> {
            EngineeringPortProvider servo = RedstoneEngineering.SERVO_ACTUATOR.get();
            BlockState state = helper.getBlockState(servoPos);
            var front = servo.engineeringPort(state, Direction.EAST).orElse(null);
            if (front == null || front.domain() != EngineeringDomain.MECHATRONIC_POSITION
                    || front.direction() != PortDirection.OUTPUT
                    || ServoActuatorBlock.position(helper.getLevel(), helper.absolutePos(servoPos)) <= 0) {
                helper.fail("Servo did not publish a moving MECHATRONIC_POSITION FRONT output", servoPos);
                return;
            }
            if (RedstoneEngineering.SERVO_ACTUATOR.get().canConnectRedstone(
                    state, helper.getLevel(), helper.absolutePos(servoPos), Direction.WEST)) {
                helper.fail("Servo mechanical FRONT incorrectly advertised vanilla redstone connectivity", servoPos);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 70)
    public static void servoPositionSensorBridgesMechanicalFeedbackToRedstone(GameTestHelper helper) {
        BlockPos servoPos = new BlockPos(1, 1, 2);
        BlockPos sensorPos = new BlockPos(2, 1, 2);
        BlockPos commandPos = new BlockPos(0, 1, 2);
        helper.setBlock(commandPos, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(servoPos, RedstoneEngineering.SERVO_ACTUATOR.get().defaultBlockState()
                .setValue(ServoActuatorBlock.FACING, Direction.EAST));
        helper.setBlock(sensorPos, RedstoneEngineering.SERVO_POSITION_SENSOR.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));

        helper.runAfterDelay(12, () -> {
            EngineeringPortProvider sensor = RedstoneEngineering.SERVO_POSITION_SENSOR.get();
            BlockState state = helper.getBlockState(sensorPos);
            var back = sensor.engineeringPort(state, Direction.WEST).orElse(null);
            var front = sensor.engineeringPort(state, Direction.EAST).orElse(null);
            if (back == null || back.domain() != EngineeringDomain.MECHATRONIC_POSITION
                    || front == null || front.domain() != EngineeringDomain.REDSTONE
                    || state.getValue(DirectionalSignalBlock.OUTPUT) <= 0) {
                helper.fail("Servo position sensor did not bridge mechanical position into redstone feedback", sensorPos);
                return;
            }
            // NeoForge direction is the queried neighbor direction, so EAST maps to physical WEST/BACK.
            if (RedstoneEngineering.SERVO_POSITION_SENSOR.get().canConnectRedstone(
                    state, helper.getLevel(), helper.absolutePos(sensorPos), Direction.EAST)) {
                helper.fail("Servo position sensor mechanical BACK incorrectly accepted vanilla redstone", sensorPos);
                return;
            }
            // WEST query maps to physical EAST/FRONT and must retain vanilla feedback output connectivity.
            if (!RedstoneEngineering.SERVO_POSITION_SENSOR.get().canConnectRedstone(
                    state, helper.getLevel(), helper.absolutePos(sensorPos), Direction.WEST)) {
                helper.fail("Servo position sensor REDSTONE FRONT stopped advertising output connectivity", sensorPos);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void redundantVoterCountsDisagreementEdgesNotDuration(GameTestHelper helper) {
        BlockPos voterPos = new BlockPos(2, 1, 2);
        helper.setBlock(voterPos, RedstoneEngineering.REDUNDANT_VOTER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));
        helper.setBlock(voterPos.west(), reference(Direction.EAST, 5));
        helper.setBlock(voterPos.north(), reference(Direction.SOUTH, 6));
        helper.setBlock(voterPos.south(), reference(Direction.NORTH, 7));

        helper.runAfterDelay(5, () -> {
            BlockPos world = helper.absolutePos(voterPos);
            if (helper.getBlockState(voterPos).getValue(DirectionalSignalBlock.OUTPUT) != 6
                    || !RedundantVoterBlock.degraded(helper.getLevel(), world)
                    || RedundantVoterBlock.spread(helper.getLevel(), world) != 2
                    || RedundantVoterBlock.disagreementCount(helper.getLevel(), world) != 1) {
                helper.fail("2oo3 voter did not produce median=6 with one disagreement event", voterPos);
                return;
            }
            helper.runAfterDelay(8, () -> {
                if (RedundantVoterBlock.disagreementCount(helper.getLevel(), world) != 1) {
                    helper.fail("Stable disagreement was counted repeatedly instead of as one event edge", voterPos);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 70)
    public static void faultLatchResetHasPriorityAndCountsOneEdge(GameTestHelper helper) {
        BlockPos latchPos = new BlockPos(2, 1, 2);
        BlockPos faultPos = latchPos.west();
        BlockPos resetPos = latchPos.south();
        helper.setBlock(faultPos, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(latchPos, RedstoneEngineering.FAULT_LATCH.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));

        helper.runAfterDelay(4, () -> {
            BlockPos world = helper.absolutePos(latchPos);
            if (!FaultLatchBlock.latched(helper.getLevel(), world)
                    || FaultLatchBlock.tripCount(helper.getLevel(), world) != 1) {
                helper.fail("Fault latch did not capture the first threshold crossing", latchPos);
                return;
            }
            helper.setBlock(resetPos, Blocks.REDSTONE_BLOCK.defaultBlockState());
            helper.runAfterDelay(4, () -> {
                if (FaultLatchBlock.latched(helper.getLevel(), world)
                        || helper.getBlockState(latchPos).getValue(DirectionalSignalBlock.OUTPUT) != 0
                        || FaultLatchBlock.resetCount(helper.getLevel(), world) != 1) {
                    helper.fail("RESET did not dominate a still-active fault with one reset event", latchPos);
                    return;
                }
                helper.runAfterDelay(6, () -> {
                    if (FaultLatchBlock.resetCount(helper.getLevel(), world) != 1
                            || FaultLatchBlock.latched(helper.getLevel(), world)) {
                        helper.fail("Held RESET inflated counters or allowed same-level re-latching", latchPos);
                        return;
                    }
                    helper.setBlock(resetPos, Blocks.AIR.defaultBlockState());
                    helper.runAfterDelay(4, () -> {
                        if (!FaultLatchBlock.latched(helper.getLevel(), world)
                                || FaultLatchBlock.tripCount(helper.getLevel(), world) != 2) {
                            helper.fail("Fault latch did not re-arm after RESET was released", latchPos);
                            return;
                        }
                        helper.succeed();
                    });
                });
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void operationsMonitorIsObserverOnlyAndClassifiesBlockedWork(GameTestHelper helper) {
        BlockPos monitorPos = new BlockPos(2, 1, 2);
        BlockPos queuePos = monitorPos.north();
        helper.setBlock(queuePos, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(monitorPos, RedstoneEngineering.OPERATIONS_MONITOR.get().defaultBlockState());

        helper.runAfterDelay(5, () -> {
            EngineeringPortProvider monitor = RedstoneEngineering.OPERATIONS_MONITOR.get();
            BlockState state = helper.getBlockState(monitorPos);
            if (monitor.engineeringPorts(state).size() != 6
                    || monitor.engineeringPorts(state).stream().anyMatch(port -> port.direction() != PortDirection.INPUT)) {
                helper.fail("Operations Monitor must expose six observer-only input ports", monitorPos);
                return;
            }
            BlockPos world = helper.absolutePos(monitorPos);
            if (OperationsMonitorBlock.queueNow(helper.getLevel(), world) != 15
                    || OperationsMonitorBlock.running(helper.getLevel(), world)
                    || OperationsMonitorBlock.stateOrdinal(helper.getLevel(), world)
                    != OperationsMonitorBlock.SystemState.SAFETY_LIMITED.ordinal()) {
                helper.fail("Stopped machine with queued work was not classified SAFETY_LIMITED", monitorPos);
                return;
            }
            helper.succeed();
        });
    }

    private static BlockState reference(Direction facing, int power) {
        return RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, facing)
                .setValue(RedstoneReferenceSourceBlock.POWER, power);
    }
}
