package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.PidControllerBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.SampleHoldBlock;
import dev.redstoneengineering.block.ServoActuatorBlock;
import dev.redstoneengineering.block.ServoPositionSensorBlock;
import dev.redstoneengineering.block.SignalConditionerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * System-Level Closure Phase 2: sampled-data closed-loop control.
 *
 * <p>Dynamic plant measurement crosses a Sample & Hold boundary before it re-enters
 * PID process value. The long sensor/conditioner return remains live while PID PV
 * changes only on an explicit sample edge. This bounds the feedback topology without
 * weakening the production blocks or inventing client-side control state.</p>
 */
public final class RseSystemLevelClosurePhase2ProbeGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseSystemLevelClosurePhase2ProbeGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 120)
    public static void sampledPidLoopBoundsLongDynamicFeedback(GameTestHelper helper) {
        BlockPos setpoint = new BlockPos(3, 1, 4);
        BlockPos pid = new BlockPos(3, 1, 3);
        BlockPos servo = new BlockPos(3, 1, 2);
        BlockPos sensor = new BlockPos(3, 1, 1);
        BlockPos[] feedback = {
                new BlockPos(3, 1, 0),
                new BlockPos(2, 1, 0),
                new BlockPos(1, 1, 0),
                new BlockPos(0, 1, 0),
                new BlockPos(0, 1, 1),
                new BlockPos(0, 1, 2),
                new BlockPos(0, 1, 3)
        };
        BlockPos conditioner = new BlockPos(1, 1, 3);
        BlockPos sampleHold = new BlockPos(2, 1, 3);
        BlockPos trigger = new BlockPos(2, 1, 2);

        for (BlockPos wire : feedback) {
            helper.setBlock(wire.below(), Blocks.STONE.defaultBlockState());
            helper.setBlock(wire, Blocks.REDSTONE_WIRE.defaultBlockState());
        }

        helper.setBlock(setpoint, reference(Direction.NORTH, 8));
        helper.setBlock(pid, RedstoneEngineering.PID_CONTROLLER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.NORTH)
                .setValue(PidControllerBlock.TUNING, 0));
        helper.setBlock(servo, RedstoneEngineering.SERVO_ACTUATOR.get().defaultBlockState()
                .setValue(ServoActuatorBlock.FACING, Direction.NORTH)
                .setValue(ServoActuatorBlock.SLEW, 1));
        helper.setBlock(sensor, RedstoneEngineering.SERVO_POSITION_SENSOR.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.NORTH));
        helper.setBlock(conditioner, RedstoneEngineering.SIGNAL_CONDITIONER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(SignalConditionerBlock.MODE, 0)
                .setValue(SignalConditionerBlock.PARAM, 1));
        helper.setBlock(sampleHold, RedstoneEngineering.SAMPLE_HOLD.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(SampleHoldBlock.TRIGGER_MODE, 0));
        helper.setBlock(trigger, reference(Direction.SOUTH, 0));

        helper.runAfterDelay(24, () -> {
            BlockPos holdWorld = helper.absolutePos(sampleHold);
            if (SampleHoldBlock.captureCount(helper.getLevel(), holdWorld) != 0) {
                helper.fail("Sample boundary captured without an explicit trigger edge", sampleHold);
                return;
            }
            if (helper.getBlockState(sampleHold).getValue(DirectionalSignalBlock.OUTPUT) != 0) {
                helper.fail("Sample boundary did not retain its initial held value before the first sample", sampleHold);
                return;
            }
            if (ServoActuatorBlock.braking(helper.getLevel(), helper.absolutePos(servo))) {
                helper.fail("Isolated trigger fixture leaked into the servo BRAKE/control topology", servo);
                return;
            }

            int finalDust = helper.getBlockState(feedback[feedback.length - 1]).getValue(RedStoneWireBlock.POWER);
            int conditionerIn = SignalConditionerBlock.inspectInput(
                    helper.getLevel(), helper.absolutePos(conditioner), helper.getBlockState(conditioner));
            int conditionerOut = helper.getBlockState(conditioner).getValue(DirectionalSignalBlock.OUTPUT);
            if (finalDust <= 0 || conditionerIn != finalDust || conditionerOut != conditionerIn) {
                helper.fail("Live plant feedback did not reach the sampling boundary before capture"
                        + " | dust=" + finalDust + " condIn=" + conditionerIn + " condOut=" + conditionerOut,
                        conditioner);
                return;
            }

            helper.setBlock(trigger, reference(Direction.SOUTH, 15));
            helper.runAfterDelay(4, () -> {
                int firstCount = SampleHoldBlock.captureCount(helper.getLevel(), holdWorld);
                int firstHeld = helper.getBlockState(sampleHold).getValue(DirectionalSignalBlock.OUTPUT);
                if (firstCount != 1 || firstHeld <= 0) {
                    helper.fail("First rising edge did not create exactly one positive feedback sample"
                            + " | captures=" + firstCount + " held=" + firstHeld, sampleHold);
                    return;
                }

                helper.setBlock(trigger, reference(Direction.SOUTH, 0));
                helper.setBlock(setpoint, reference(Direction.NORTH, 15));
                helper.runAfterDelay(24, () -> {
                    int countWhileHolding = SampleHoldBlock.captureCount(helper.getLevel(), holdWorld);
                    int heldWhilePlantMoves = helper.getBlockState(sampleHold).getValue(DirectionalSignalBlock.OUTPUT);
                    int liveConditioned = helper.getBlockState(conditioner).getValue(DirectionalSignalBlock.OUTPUT);
                    int liveSensor = helper.getBlockState(sensor).getValue(DirectionalSignalBlock.OUTPUT);

                    if (countWhileHolding != 1 || heldWhilePlantMoves != firstHeld) {
                        helper.fail("Dynamic plant updates crossed the sampling boundary without a trigger"
                                + " | captures=" + countWhileHolding
                                + " held=" + heldWhilePlantMoves + " expectedHeld=" + firstHeld,
                                sampleHold);
                        return;
                    }
                    if (liveConditioned == firstHeld) {
                        helper.fail("Setpoint step did not create distinguishable live feedback for temporal-isolation proof"
                                + " | sensor=" + liveSensor + " conditioned=" + liveConditioned
                                + " held=" + firstHeld,
                                conditioner);
                        return;
                    }
                    if (ServoActuatorBlock.braking(helper.getLevel(), helper.absolutePos(servo))) {
                        helper.fail("Trigger lane contaminated servo control while the loop was holding", servo);
                        return;
                    }

                    helper.setBlock(trigger, reference(Direction.SOUTH, 15));
                    helper.runAfterDelay(4, () -> {
                        int secondCount = SampleHoldBlock.captureCount(helper.getLevel(), holdWorld);
                        int secondHeld = helper.getBlockState(sampleHold).getValue(DirectionalSignalBlock.OUTPUT);
                        int age = SampleHoldBlock.sampleAgeTicks(helper.getLevel(), holdWorld);
                        if (secondCount != 2 || secondHeld == firstHeld || age < 0 || age > 5) {
                            helper.fail("Second explicit sample did not refresh PID PV exactly once"
                                    + " | captures=" + secondCount
                                    + " firstHeld=" + firstHeld + " secondHeld=" + secondHeld
                                    + " sampleAge=" + age,
                                    sampleHold);
                            return;
                        }

                        int pidOut = helper.getBlockState(pid).getValue(DirectionalSignalBlock.OUTPUT);
                        int position = ServoActuatorBlock.position(helper.getLevel(), helper.absolutePos(servo));
                        if (pidOut < 0 || pidOut > 15 || position < 0 || position > 15) {
                            helper.fail("Sampled closed loop escaped engineering bounds"
                                    + " | PID_OUT=" + pidOut + " servo=" + position,
                                    pid);
                            return;
                        }
                        helper.succeed();
                    });
                });
            });
        });
    }

    private static BlockState reference(Direction facing, int power) {
        return RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, facing)
                .setValue(RedstoneReferenceSourceBlock.POWER, power);
    }
}
