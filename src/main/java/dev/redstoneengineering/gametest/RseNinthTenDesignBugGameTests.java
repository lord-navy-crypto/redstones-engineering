package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.AirReservoirBlock;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.PressureRegulatorBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.WatchdogBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.DifferentialNetwork;
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.PneumaticNetwork;
import dev.redstoneengineering.physics.PneumaticObservationSupport;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.physics.SerialNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Ninth 10-block design/bug campaign: regeneration, control safety and pneumatic state quality. */
public final class RseNinthTenDesignBugGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseNinthTenDesignBugGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void digitalRegeneratorPreservesSerialConflictQuality(GameTestHelper helper) {
        BlockPos west = new BlockPos(1, 1, 2);
        BlockPos line = new BlockPos(2, 1, 2);
        BlockPos east = new BlockPos(3, 1, 2);
        BlockPos regenerator = new BlockPos(2, 1, 3);
        helper.setBlock(line, RedstoneEngineering.SERIAL_DATA_LINE.get().defaultBlockState());
        helper.setBlock(west, RedstoneEngineering.SERIALIZER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(east, RedstoneEngineering.SERIALIZER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.WEST));
        helper.setBlock(regenerator, RedstoneEngineering.DIGITAL_REGENERATOR.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.SOUTH));
        InformationRuntime.write(helper.getLevel(), "serial", helper.absolutePos(west), 11, 8, true, 100);
        InformationRuntime.write(helper.getLevel(), "serial", helper.absolutePos(east), 23, 8, true, 100);
        SerialNetwork.recompute(helper.getLevel(), helper.absolutePos(line));

        var input = RedstoneEngineering.DIGITAL_REGENERATOR.get().engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(regenerator), helper.getBlockState(regenerator), Direction.NORTH).orElseThrow();
        var output = RedstoneEngineering.DIGITAL_REGENERATOR.get().engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(regenerator), helper.getBlockState(regenerator), Direction.SOUTH).orElseThrow();
        if (input.quality() != PortQuality.TOPOLOGY_ERROR || output.quality() != PortQuality.TOPOLOGY_ERROR) {
            helper.fail("Digital regenerator collapsed serial contention into ordinary invalid data", regenerator);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void differentialDriverSeparatesEmptyInputFromDrivenZero(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 2);
        BlockPos driver = new BlockPos(2, 1, 2);
        BlockPos pair = new BlockPos(3, 1, 2);
        helper.setBlock(driver, RedstoneEngineering.DIFFERENTIAL_DRIVER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(pair, RedstoneEngineering.DIFFERENTIAL_DATA_PAIR.get().defaultBlockState());
        BlockPos pairWorld = helper.absolutePos(pair);

        helper.runAfterDelay(3, () -> {
            var empty = RedstoneEngineering.DIFFERENTIAL_DRIVER.get().engineeringSnapshot(
                    helper.getLevel(), helper.absolutePos(driver), helper.getBlockState(driver), Direction.WEST).orElseThrow();
            if (empty.quality() != PortQuality.NO_SIGNAL
                    || DifferentialNetwork.quality(helper.getLevel(), pairWorld) != PortQuality.NO_SIGNAL) {
                helper.fail("Empty differential-driver redstone input became a legitimate LOW driver", driver);
                return;
            }
            helper.setBlock(source, reference(Direction.EAST, 0));
            helper.runAfterDelay(3, () -> {
                if (DifferentialNetwork.quality(helper.getLevel(), pairWorld) != PortQuality.VALID
                        || (InformationRuntime.snapshot(helper.getLevel(), "diff", pairWorld).value() & 1) != 0
                        || DifferentialNetwork.driverCount(helper.getLevel(), pairWorld) != 1) {
                    helper.fail("Configured redstone zero did not become one valid differential LOW driver", pair);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void differentialReceiverPreservesConflictQuality(GameTestHelper helper) {
        BlockPos westSource = new BlockPos(0, 1, 2);
        BlockPos westDriver = new BlockPos(1, 1, 2);
        BlockPos pair = new BlockPos(2, 1, 2);
        BlockPos eastDriver = new BlockPos(3, 1, 2);
        BlockPos eastSource = new BlockPos(4, 1, 2);
        BlockPos receiver = new BlockPos(2, 1, 3);
        helper.setBlock(westSource, reference(Direction.EAST, 0));
        helper.setBlock(eastSource, reference(Direction.WEST, 15));
        helper.setBlock(pair, RedstoneEngineering.DIFFERENTIAL_DATA_PAIR.get().defaultBlockState());
        helper.setBlock(westDriver, RedstoneEngineering.DIFFERENTIAL_DRIVER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(eastDriver, RedstoneEngineering.DIFFERENTIAL_DRIVER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.WEST));
        helper.setBlock(receiver, RedstoneEngineering.DIFFERENTIAL_RECEIVER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.SOUTH));

        helper.runAfterDelay(4, () -> {
            var input = RedstoneEngineering.DIFFERENTIAL_RECEIVER.get().engineeringSnapshot(
                    helper.getLevel(), helper.absolutePos(receiver), helper.getBlockState(receiver), Direction.NORTH).orElseThrow();
            var output = RedstoneEngineering.DIFFERENTIAL_RECEIVER.get().engineeringSnapshot(
                    helper.getLevel(), helper.absolutePos(receiver), helper.getBlockState(receiver), Direction.SOUTH).orElseThrow();
            if (DifferentialNetwork.quality(helper.getLevel(), helper.absolutePos(pair)) != PortQuality.TOPOLOGY_ERROR
                    || input.quality() != PortQuality.TOPOLOGY_ERROR
                    || output.quality() != PortQuality.TOPOLOGY_ERROR
                    || helper.getBlockState(receiver).getValue(DirectionalSignalBlock.OUTPUT) != 0) {
                helper.fail("Differential receiver hid a two-driver conflict at its redstone boundary", receiver);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void sculkInterfaceSeparatesIdleDrivenZeroFromEmptyInput(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 2);
        BlockPos iface = new BlockPos(2, 1, 2);
        helper.setBlock(iface, RedstoneEngineering.SCULK_VIBRATION_INTERFACE.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));
        BlockPos world = helper.absolutePos(iface);

        var empty = RedstoneEngineering.SCULK_VIBRATION_INTERFACE.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(iface), Direction.WEST).orElseThrow();
        if (empty.quality() != PortQuality.NO_SIGNAL) {
            helper.fail("Idle Sculk interface fabricated an input source from air", iface);
            return;
        }
        helper.setBlock(source, reference(Direction.EAST, 0));
        helper.runAfterDelay(2, () -> {
            var input = RedstoneEngineering.SCULK_VIBRATION_INTERFACE.get().engineeringSnapshot(
                    helper.getLevel(), world, helper.getBlockState(iface), Direction.WEST).orElseThrow();
            var output = RedstoneEngineering.SCULK_VIBRATION_INTERFACE.get().engineeringSnapshot(
                    helper.getLevel(), world, helper.getBlockState(iface), Direction.EAST).orElseThrow();
            if (input.value() != 0.0 || input.quality() != PortQuality.VALID
                    || output.value() != 0.0 || output.quality() != PortQuality.VALID) {
                helper.fail("Sculk interface collapsed a configured idle-zero source into NO_SIGNAL", iface);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void pidRequiresRealProcessEvidenceInAuto(GameTestHelper helper) {
        BlockPos setpoint = new BlockPos(1, 1, 2);
        BlockPos pid = new BlockPos(2, 1, 2);
        BlockPos process = new BlockPos(2, 1, 1);
        helper.setBlock(setpoint, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(pid, RedstoneEngineering.PID_CONTROLLER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));

        helper.runAfterDelay(4, () -> {
            var pv = RedstoneEngineering.PID_CONTROLLER.get().engineeringSnapshot(
                    helper.getLevel(), helper.absolutePos(pid), helper.getBlockState(pid), Direction.NORTH).orElseThrow();
            var output = RedstoneEngineering.PID_CONTROLLER.get().engineeringSnapshot(
                    helper.getLevel(), helper.absolutePos(pid), helper.getBlockState(pid), Direction.EAST).orElseThrow();
            if (helper.getBlockState(pid).getValue(DirectionalSignalBlock.OUTPUT) != 0
                    || pv.quality() != PortQuality.NO_SIGNAL || output.quality() != PortQuality.NO_SIGNAL) {
                helper.fail("PID AUTO treated missing process feedback as a valid process value of zero", pid);
                return;
            }
            helper.setBlock(process, reference(Direction.SOUTH, 0));
            helper.runAfterDelay(5, () -> {
                var validOutput = RedstoneEngineering.PID_CONTROLLER.get().engineeringSnapshot(
                        helper.getLevel(), helper.absolutePos(pid), helper.getBlockState(pid), Direction.EAST).orElseThrow();
                if (helper.getBlockState(pid).getValue(DirectionalSignalBlock.OUTPUT) <= 0
                        || validOutput.quality() != PortQuality.VALID) {
                    helper.fail("PID AUTO did not resume after receiving a real valid-zero process value", pid);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 90)
    public static void watchdogUsesObservedHeartbeatEdgeNotMissingInput(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 2);
        BlockPos watchdog = new BlockPos(2, 1, 2);
        helper.setBlock(watchdog, RedstoneEngineering.WATCHDOG.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));
        BlockPos world = helper.absolutePos(watchdog);
        var missing = RedstoneEngineering.WATCHDOG.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(watchdog), Direction.WEST).orElseThrow();
        if (missing.quality() != PortQuality.NO_SIGNAL) {
            helper.fail("Watchdog advertised a missing heartbeat source as VALID", watchdog);
            return;
        }
        helper.setBlock(source, reference(Direction.EAST, 0));
        helper.runAfterDelay(46, () -> {
            if (helper.getBlockState(watchdog).getValue(DirectionalSignalBlock.OUTPUT) != 15
                    || WatchdogBlock.transitionCount(helper.getLevel(), world) != 0) {
                helper.fail("Constant valid-zero heartbeat either prevented timeout or fabricated transitions", watchdog);
                return;
            }
            helper.setBlock(source, reference(Direction.EAST, 15));
            helper.runAfterDelay(4, () -> {
                if (helper.getBlockState(watchdog).getValue(DirectionalSignalBlock.OUTPUT) != 0
                        || WatchdogBlock.transitionCount(helper.getLevel(), world) != 1) {
                    helper.fail("Observed LOW-to-HIGH heartbeat edge did not reset watchdog exactly once", watchdog);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void compressorSeparatesEmptyCommandFromValidZeroPressure(GameTestHelper helper) {
        BlockPos command = new BlockPos(2, 0, 2);
        BlockPos compressor = new BlockPos(2, 1, 2);
        BlockPos outlet = new BlockPos(2, 2, 2);
        helper.setBlock(compressor, RedstoneEngineering.AIR_COMPRESSOR.get().defaultBlockState());
        helper.setBlock(outlet, RedstoneEngineering.PNEUMATIC_PIPE.get().defaultBlockState());

        helper.runAfterDelay(2, () -> {
            var emptyCommand = RedstoneEngineering.AIR_COMPRESSOR.get().engineeringSnapshot(
                    helper.getLevel(), helper.absolutePos(compressor), helper.getBlockState(compressor), Direction.DOWN).orElseThrow();
            var zeroPressure = RedstoneEngineering.AIR_COMPRESSOR.get().engineeringSnapshot(
                    helper.getLevel(), helper.absolutePos(compressor), helper.getBlockState(compressor), Direction.UP).orElseThrow();
            if (emptyCommand.quality() != PortQuality.NO_SIGNAL
                    || zeroPressure.value() != 0.0 || zeroPressure.quality() != PortQuality.VALID) {
                helper.fail("Compressor collapsed empty command and solved zero-pressure outlet", compressor);
                return;
            }
            helper.setBlock(command, Blocks.REDSTONE_WIRE.defaultBlockState());
            helper.runAfterDelay(2, () -> {
                var drivenZero = RedstoneEngineering.AIR_COMPRESSOR.get().engineeringSnapshot(
                        helper.getLevel(), helper.absolutePos(compressor), helper.getBlockState(compressor), Direction.DOWN).orElseThrow();
                if (drivenZero.value() != 0.0 || drivenZero.quality() != PortQuality.VALID) {
                    helper.fail("Configured redstone zero did not remain a valid compressor command", compressor);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void pneumaticPipeInspectionIsNeutralThenZeroIsValid(GameTestHelper helper) {
        BlockPos pipe = new BlockPos(2, 1, 2);
        helper.setBlock(pipe, RedstoneEngineering.PNEUMATIC_PIPE.get().defaultBlockState());
        BlockPos world = helper.absolutePos(pipe);
        InformationRuntime.clear(helper.getLevel(), "pneumatic", world);
        int before = RuntimeIntStore.entryCount(helper.getLevel());
        var unsolved = RedstoneEngineering.PNEUMATIC_PIPE.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(pipe), Direction.NORTH).orElseThrow();
        if (unsolved.quality() != PortQuality.STALE || RuntimeIntStore.entryCount(helper.getLevel()) != before) {
            helper.fail("Inspecting an unsolved pneumatic pipe fabricated runtime or validity", pipe);
            return;
        }
        PneumaticNetwork.recompute(helper.getLevel(), world);
        var solved = RedstoneEngineering.PNEUMATIC_PIPE.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(pipe), Direction.NORTH).orElseThrow();
        if (solved.value() != 0.0 || solved.quality() != PortQuality.VALID) {
            helper.fail("Solved zero-pressure pneumatic pipe was mislabeled NO_SIGNAL", pipe);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void airReservoirStoredPressureReadIsObserverNeutral(GameTestHelper helper) {
        BlockPos reservoir = new BlockPos(2, 1, 2);
        helper.setBlock(reservoir, RedstoneEngineering.AIR_RESERVOIR.get().defaultBlockState());
        BlockPos world = helper.absolutePos(reservoir);
        InformationRuntime.clear(helper.getLevel(), "air_reservoir", world);
        InformationRuntime.clear(helper.getLevel(), "pneumatic", world);
        int before = RuntimeIntStore.entryCount(helper.getLevel());
        int stored = AirReservoirBlock.storedPressure(helper.getLevel(), world);
        PneumaticObservationSupport.Observation observation = PneumaticObservationSupport.observe(helper.getLevel(), world);
        if (stored != 0 || observation.quality() != PortQuality.STALE
                || RuntimeIntStore.entryCount(helper.getLevel()) != before
                || RuntimeIntStore.peek(helper.getLevel(), "info:air_reservoir", world) != null
                || RuntimeIntStore.peek(helper.getLevel(), "info:pneumatic", world) != null) {
            helper.fail("Reservoir inspection recreated cleared transient pressure state", reservoir);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void pressureRegulatorSetpointDoesNotFabricatePressure(GameTestHelper helper) {
        BlockPos regulator = new BlockPos(2, 1, 2);
        helper.setBlock(regulator, RedstoneEngineering.PRESSURE_REGULATOR.get().defaultBlockState()
                .setValue(PressureRegulatorBlock.SETPOINT, 2));
        helper.runAfterDelay(2, () -> {
            var snapshot = RedstoneEngineering.PRESSURE_REGULATOR.get().engineeringSnapshot(
                    helper.getLevel(), helper.absolutePos(regulator), helper.getBlockState(regulator), Direction.EAST).orElseThrow();
            if (PressureRegulatorBlock.setpointPressure(helper.getBlockState(regulator)) != 50
                    || snapshot.value() != 0.0 || snapshot.quality() != PortQuality.VALID) {
                helper.fail("Regulator setpoint fabricated pressure or mislabeled solved zero as NO_SIGNAL", regulator);
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
