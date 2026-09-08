package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.FaultLatchBlock;
import dev.redstoneengineering.block.OperationsMonitorBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Final registered-block closure: #121 Fault Latch and #122 Operations Monitor. */
public final class RseFinalTwoEvidenceClosureGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseFinalTwoEvidenceClosureGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void faultLatchSeparatesMissingZeroAndResetPriority(GameTestHelper helper) {
        BlockPos latchPos = new BlockPos(2, 1, 2);
        BlockPos faultPos = latchPos.west();
        BlockPos resetPos = latchPos.south();
        helper.setBlock(latchPos, RedstoneEngineering.FAULT_LATCH.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));
        BlockPos world = helper.absolutePos(latchPos);

        var missingFault = RedstoneEngineering.FAULT_LATCH.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(latchPos), Direction.WEST).orElseThrow();
        var missingReset = RedstoneEngineering.FAULT_LATCH.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(latchPos), Direction.SOUTH).orElseThrow();
        if (missingFault.quality() != PortQuality.NO_SIGNAL || missingReset.quality() != PortQuality.NO_SIGNAL) {
            helper.fail("Fault latch fabricated VALID zero inputs from missing FAULT/RESET sources", latchPos);
            return;
        }

        helper.setBlock(faultPos, reference(Direction.EAST, 0));
        helper.setBlock(resetPos, reference(Direction.NORTH, 0));
        helper.runAfterDelay(4, () -> {
            var zeroFault = RedstoneEngineering.FAULT_LATCH.get().engineeringSnapshot(
                    helper.getLevel(), world, helper.getBlockState(latchPos), Direction.WEST).orElseThrow();
            var zeroReset = RedstoneEngineering.FAULT_LATCH.get().engineeringSnapshot(
                    helper.getLevel(), world, helper.getBlockState(latchPos), Direction.SOUTH).orElseThrow();
            if (zeroFault.value() != 0.0 || zeroFault.quality() != PortQuality.VALID
                    || zeroReset.value() != 0.0 || zeroReset.quality() != PortQuality.VALID
                    || FaultLatchBlock.latched(helper.getLevel(), world)
                    || FaultLatchBlock.tripCount(helper.getLevel(), world) != 0
                    || FaultLatchBlock.resetCount(helper.getLevel(), world) != 0) {
                helper.fail("Fault latch collapsed configured LOW sources or created a false event", latchPos);
                return;
            }

            helper.setBlock(faultPos, reference(Direction.EAST, 15));
            helper.runAfterDelay(4, () -> {
                if (!FaultLatchBlock.latched(helper.getLevel(), world)
                        || FaultLatchBlock.tripCount(helper.getLevel(), world) != 1) {
                    helper.fail("Valid FAULT source did not latch exactly once", latchPos);
                    return;
                }
                helper.setBlock(resetPos, reference(Direction.NORTH, 15));
                helper.runAfterDelay(4, () -> {
                    if (FaultLatchBlock.latched(helper.getLevel(), world)
                            || helper.getBlockState(latchPos).getValue(DirectionalSignalBlock.OUTPUT) != 0
                            || FaultLatchBlock.resetCount(helper.getLevel(), world) != 1) {
                        helper.fail("Valid RESET source did not dominate the still-active fault", latchPos);
                        return;
                    }
                    helper.succeed();
                });
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void operationsMonitorMissingRunEvidenceCannotCreateKpis(GameTestHelper helper) {
        BlockPos monitorPos = new BlockPos(2, 1, 2);
        BlockPos queuePos = monitorPos.north();
        BlockPos runPos = monitorPos.below();
        helper.setBlock(queuePos, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(monitorPos, RedstoneEngineering.OPERATIONS_MONITOR.get().defaultBlockState());
        BlockPos world = helper.absolutePos(monitorPos);

        helper.runAfterDelay(6, () -> {
            var runSnapshot = RedstoneEngineering.OPERATIONS_MONITOR.get().engineeringSnapshot(
                    helper.getLevel(), world, helper.getBlockState(monitorPos), Direction.DOWN).orElseThrow();
            if (runSnapshot.quality() != PortQuality.NO_SIGNAL
                    || OperationsMonitorBlock.monitoringReady(helper.getLevel(), world)
                    || OperationsMonitorBlock.queueNow(helper.getLevel(), world) != 15
                    || OperationsMonitorBlock.downtimeTicks(helper.getLevel(), world) != 0
                    || OperationsMonitorBlock.blockedFaultTicks(helper.getLevel(), world) != 0
                    || OperationsMonitorBlock.stateOrdinal(helper.getLevel(), world)
                    != OperationsMonitorBlock.SystemState.NOMINAL.ordinal()) {
                helper.fail("Missing RUN evidence fabricated stopped-machine KPI/state evidence", monitorPos);
                return;
            }

            // A zero-power dust source is still a real connected RUN measurement: stopped, not missing.
            helper.setBlock(runPos, Blocks.REDSTONE_WIRE.defaultBlockState());
            helper.runAfterDelay(6, () -> {
                var stoppedSnapshot = RedstoneEngineering.OPERATIONS_MONITOR.get().engineeringSnapshot(
                        helper.getLevel(), world, helper.getBlockState(monitorPos), Direction.DOWN).orElseThrow();
                if (stoppedSnapshot.value() != 0.0 || stoppedSnapshot.quality() != PortQuality.VALID
                        || !OperationsMonitorBlock.monitoringReady(helper.getLevel(), world)
                        || OperationsMonitorBlock.downtimeTicks(helper.getLevel(), world) <= 0
                        || OperationsMonitorBlock.blockedFaultTicks(helper.getLevel(), world) <= 0
                        || OperationsMonitorBlock.stateOrdinal(helper.getLevel(), world)
                        != OperationsMonitorBlock.SystemState.SAFETY_LIMITED.ordinal()) {
                    helper.fail("Explicit stopped-machine zero did not become trustworthy blocked-work evidence", monitorPos);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void operationsMonitorCycleTimingStartsOnSecondTrustworthyPulse(GameTestHelper helper) {
        BlockPos monitorPos = new BlockPos(2, 1, 2);
        BlockPos runPos = monitorPos.below();
        BlockPos cyclePos = monitorPos.above();
        BlockPos queuePos = monitorPos.north();
        helper.setBlock(runPos, Blocks.REDSTONE_WIRE.defaultBlockState());
        helper.setBlock(cyclePos, Blocks.REDSTONE_WIRE.defaultBlockState());
        helper.setBlock(queuePos, reference(Direction.SOUTH, 0));
        helper.setBlock(monitorPos, RedstoneEngineering.OPERATIONS_MONITOR.get().defaultBlockState());
        BlockPos world = helper.absolutePos(monitorPos);

        helper.runAfterDelay(5, () -> {
            if (!OperationsMonitorBlock.monitoringReady(helper.getLevel(), world)
                    || OperationsMonitorBlock.cyclesCurrentWindow(helper.getLevel(), world) != 0
                    || OperationsMonitorBlock.lastCycleTicks(helper.getLevel(), world) != 0) {
                helper.fail("Operations monitor did not establish a clean LOW/ready baseline", monitorPos);
                return;
            }

            helper.setBlock(cyclePos, Blocks.REDSTONE_BLOCK.defaultBlockState());
            helper.runAfterDelay(3, () -> {
                int cycles = OperationsMonitorBlock.cyclesCurrentWindow(helper.getLevel(), world);
                int lastCycle = OperationsMonitorBlock.lastCycleTicks(helper.getLevel(), world);
                var cycleSnapshot = RedstoneEngineering.OPERATIONS_MONITOR.get().engineeringSnapshot(
                        helper.getLevel(), world, helper.getBlockState(monitorPos), Direction.UP).orElseThrow();
                var evidence = OperationsMonitorBlock.inputEvidence(helper.getLevel(), world);
                if (cycles != 1 || lastCycle != 0) {
                    helper.fail("First trustworthy cycle pulse mismatch"
                            + " | cycles=" + cycles
                            + " lastCycle=" + lastCycle
                            + " cycle=" + cycleSnapshot.value() + "/" + cycleSnapshot.quality()
                            + " evidenceCycle=" + evidence.cycle().value() + "/" + evidence.cycle().quality()
                            + " ready=" + evidence.operationalReady(), monitorPos);
                    return;
                }

                helper.setBlock(cyclePos, Blocks.REDSTONE_WIRE.defaultBlockState());
                helper.runAfterDelay(5, () -> {
                    helper.setBlock(cyclePos, Blocks.REDSTONE_BLOCK.defaultBlockState());
                    helper.runAfterDelay(3, () -> {
                        if (OperationsMonitorBlock.cyclesCurrentWindow(helper.getLevel(), world) != 2
                                || OperationsMonitorBlock.lastCycleTicks(helper.getLevel(), world) <= 0) {
                            helper.fail("Second trustworthy cycle pulse did not produce a real pulse-to-pulse interval", monitorPos);
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
