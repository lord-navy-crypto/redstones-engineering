package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.MolecularCloudReceiverBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.RedundantVoterBlock;
import dev.redstoneengineering.block.ServoActuatorBlock;
import dev.redstoneengineering.block.ServoPositionSensorBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.instrument.InstrumentShieldingAudit;
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Twelfth 10-block design/bug campaign: tail runtime, coverage, fail-safe and quorum semantics. */
public final class RseTwelfthTenDesignBugGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseTwelfthTenDesignBugGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void soulInjectorSeparatesMissingCommandAndKnownEmptyOutput(GameTestHelper helper) {
        BlockPos injector = new BlockPos(2, 1, 2);
        BlockPos command = injector.above();
        BlockPos reservoir = injector.east();
        helper.setBlock(injector, RedstoneEngineering.SOUL_FLUX_INJECTOR.get().defaultBlockState());
        BlockPos world = helper.absolutePos(injector);

        var missing = RedstoneEngineering.SOUL_FLUX_INJECTOR.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(injector), Direction.UP).orElseThrow();
        if (missing.quality() != PortQuality.NO_SIGNAL) {
            helper.fail("Soul Flux injector treated an empty command face as a real zero command", injector);
            return;
        }

        helper.setBlock(command, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(reservoir, RedstoneEngineering.SOUL_SAND_RESERVOIR.get().defaultBlockState());
        var driven = RedstoneEngineering.SOUL_FLUX_INJECTOR.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(injector), Direction.UP).orElseThrow();
        var emptyStorage = RedstoneEngineering.SOUL_FLUX_INJECTOR.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(injector), Direction.EAST).orElseThrow();
        if (driven.value() != 15.0 || driven.quality() != PortQuality.VALID
                || emptyStorage.value() != 0.0 || emptyStorage.quality() != PortQuality.VALID) {
            helper.fail("Soul Flux injector lost command evidence or mislabeled known-empty reservoir output", injector);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void soulMeterSeparatesNoNodeTransientAbsenceAndStoredZero(GameTestHelper helper) {
        BlockPos meter = new BlockPos(2, 1, 2);
        BlockPos source = meter.west();
        helper.setBlock(meter, RedstoneEngineering.SOUL_FLUX_METER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));
        BlockPos world = helper.absolutePos(meter);

        var noNode = RedstoneEngineering.SOUL_FLUX_METER.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(meter), Direction.WEST).orElseThrow();
        if (noNode.quality() != PortQuality.NO_SIGNAL) {
            helper.fail("Soul Flux meter fabricated a zero measurement without a Soul-Flux node", meter);
            return;
        }

        helper.setBlock(source, RedstoneEngineering.SOUL_SAND_RESERVOIR.get().defaultBlockState());
        var storedZero = RedstoneEngineering.SOUL_FLUX_METER.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(meter), Direction.WEST).orElseThrow();
        var zeroReadout = RedstoneEngineering.SOUL_FLUX_METER.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(meter), Direction.EAST).orElseThrow();
        if (storedZero.value() != 0.0 || storedZero.quality() != PortQuality.VALID
                || zeroReadout.value() != 0.0 || zeroReadout.quality() != PortQuality.VALID) {
            helper.fail("Soul Flux meter collapsed known-empty storage into missing data", meter);
            return;
        }

        helper.setBlock(source, RedstoneEngineering.SOUL_SOIL_CONDUIT.get().defaultBlockState());
        var emptyConduit = RedstoneEngineering.SOUL_FLUX_METER.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(meter), Direction.WEST).orElseThrow();
        if (emptyConduit.quality() != PortQuality.NO_SIGNAL) {
            helper.fail("Soul Flux meter treated an empty transient conduit as retained valid zero", meter);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void molecularInspectionIsNeutralAndCoverageQualityIsExplicit(GameTestHelper helper) {
        BlockPos receiver = new BlockPos(2, 1, 2);
        helper.setBlock(receiver, RedstoneEngineering.MOLECULAR_CLOUD_RECEIVER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));
        BlockPos world = helper.absolutePos(receiver);
        RuntimeIntStore.remove(helper.getLevel(), "molecular_sensor", world);

        int filtered = MolecularCloudReceiverBlock.filtered(helper.getLevel(), world);
        var live = MolecularCloudReceiverBlock.sample(helper.getLevel(), world, helper.getBlockState(receiver));
        var input = RedstoneEngineering.MOLECULAR_CLOUD_RECEIVER.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(receiver), Direction.UP).orElseThrow();
        var output = RedstoneEngineering.MOLECULAR_CLOUD_RECEIVER.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(receiver), Direction.EAST).orElseThrow();
        PortQuality expectedInput = live.complete() ? PortQuality.VALID : PortQuality.STALE;
        if (filtered != 0 || RuntimeIntStore.peek(helper.getLevel(), "molecular_sensor", world) != null
                || input.quality() != expectedInput || output.quality() != PortQuality.STALE) {
            helper.fail("Molecular receiver inspection allocated history or hid aperture coverage/first-sample state", receiver);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void phononConduitClearsDecayedPacketWithoutGhostEnvelope(GameTestHelper helper) {
        BlockPos conduit = new BlockPos(2, 1, 2);
        helper.setBlock(conduit, RedstoneEngineering.PHONON_CONDUIT.get().defaultBlockState());
        BlockPos world = helper.absolutePos(conduit);
        InformationRuntime.write(helper.getLevel(), "thermal_pulse", world, 2, 0, true, 100);
        helper.getLevel().scheduleTick(world, helper.getBlockState(conduit).getBlock(), 1);

        helper.runAfterDelay(3, () -> {
            InformationRuntime.Snapshot packet = InformationRuntime.snapshot(helper.getLevel(), "thermal_pulse", world);
            var port = RedstoneEngineering.PHONON_CONDUIT.get().engineeringSnapshot(
                    helper.getLevel(), world, helper.getBlockState(conduit), Direction.NORTH).orElseThrow();
            if (packet.ageTicks() >= 0 || port.quality() != PortQuality.NO_SIGNAL) {
                helper.fail("Phonon conduit retained zero/invalid transient runtime after pulse decay", conduit);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void thermalEncoderSeparatesDrivenZeroFromTransientEmission(GameTestHelper helper) {
        BlockPos source = new BlockPos(2, 0, 2);
        BlockPos encoder = new BlockPos(2, 1, 2);
        helper.setBlock(source, Blocks.REDSTONE_WIRE.defaultBlockState());
        helper.setBlock(encoder, RedstoneEngineering.THERMAL_PULSE_ENCODER.get().defaultBlockState());
        BlockPos world = helper.absolutePos(encoder);

        var zeroDrive = RedstoneEngineering.THERMAL_PULSE_ENCODER.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(encoder), Direction.DOWN).orElseThrow();
        var darkOutput = RedstoneEngineering.THERMAL_PULSE_ENCODER.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(encoder), Direction.EAST).orElseThrow();
        if (zeroDrive.value() != 0.0 || zeroDrive.quality() != PortQuality.VALID
                || darkOutput.quality() != PortQuality.NO_SIGNAL) {
            helper.fail("Thermal encoder collapsed a configured LOW or fabricated a zero-amplitude pulse", encoder);
            return;
        }

        helper.setBlock(encoder, Blocks.AIR.defaultBlockState());
        helper.setBlock(source, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(encoder, RedstoneEngineering.THERMAL_PULSE_ENCODER.get().defaultBlockState());
        var emitted = RedstoneEngineering.THERMAL_PULSE_ENCODER.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(encoder), Direction.EAST).orElseThrow();
        if (emitted.value() != 15.0 || emitted.quality() != PortQuality.VALID) {
            helper.fail("Thermal encoder failed to expose the actual emitted transient packet", encoder);
            return;
        }

        helper.runAfterDelay(3, () -> {
            InformationRuntime.Snapshot packet = InformationRuntime.snapshot(helper.getLevel(), "thermal_encoder", world);
            var expired = RedstoneEngineering.THERMAL_PULSE_ENCODER.get().engineeringSnapshot(
                    helper.getLevel(), world, helper.getBlockState(encoder), Direction.EAST).orElseThrow();
            if (packet.ageTicks() >= 0 || expired.quality() != PortQuality.NO_SIGNAL) {
                helper.fail("Thermal encoder presented a held electrical level as a permanent phonon packet", encoder);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void thermalReceiverClearsFinalZeroPacket(GameTestHelper helper) {
        BlockPos receiver = new BlockPos(2, 1, 2);
        helper.setBlock(receiver, RedstoneEngineering.THERMAL_PULSE_RECEIVER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));
        BlockPos world = helper.absolutePos(receiver);
        InformationRuntime.write(helper.getLevel(), "thermal_pulse", world, 1, 0, true, 100);
        helper.getLevel().scheduleTick(world, helper.getBlockState(receiver).getBlock(), 1);

        helper.runAfterDelay(3, () -> {
            InformationRuntime.Snapshot packet = InformationRuntime.snapshot(helper.getLevel(), "thermal_pulse", world);
            var output = RedstoneEngineering.THERMAL_PULSE_RECEIVER.get().engineeringSnapshot(
                    helper.getLevel(), world, helper.getBlockState(receiver), Direction.EAST).orElseThrow();
            if (packet.ageTicks() >= 0 || output.value() != 0.0 || output.quality() != PortQuality.NO_SIGNAL
                    || helper.getBlockState(receiver).getValue(DirectionalSignalBlock.OUTPUT) != 0) {
                helper.fail("Thermal pulse receiver left a ghost zero packet or stale redstone output", receiver);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void shieldedCableAuditTracksPhysicalMixWithoutChangingIdentity(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 1, 2);
        BlockPos b = new BlockPos(2, 1, 2);
        BlockPos c = new BlockPos(3, 1, 2);
        helper.setBlock(a, RedstoneEngineering.SHIELDED_INSTRUMENT_CABLE.get().defaultBlockState());
        helper.setBlock(b, RedstoneEngineering.SHIELDED_INSTRUMENT_CABLE.get().defaultBlockState());
        helper.setBlock(c, RedstoneEngineering.SHIELDED_INSTRUMENT_CABLE.get().defaultBlockState());

        helper.runAfterDelay(3, () -> {
            var first = InstrumentShieldingAudit.inspect(helper.getLevel(), helper.absolutePos(b));
            var second = InstrumentShieldingAudit.inspect(helper.getLevel(), helper.absolutePos(b));
            if (!first.equals(second) || !first.bounded() || first.cableNodes() != 3
                    || first.shieldedNodes() != 3 || !"FULLY_SHIELDED".equals(first.integrity())) {
                helper.fail("Shielding audit mutated topology or lost the all-shielded physical segment", b);
                return;
            }
            helper.setBlock(c, RedstoneEngineering.INSTRUMENT_CABLE.get().defaultBlockState());
            helper.runAfterDelay(3, () -> {
                var mixed = InstrumentShieldingAudit.inspect(helper.getLevel(), helper.absolutePos(b));
                if (mixed.cableNodes() != 3 || mixed.shieldedNodes() != 2 || mixed.unshieldedNodes() != 1
                        || !"MIXED_SHIELDING".equals(mixed.integrity())) {
                    helper.fail("Shielding audit did not project the actual mixed cable composition", b);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 70)
    public static void servoCommandLossBrakesInsteadOfBecomingZeroPositionCommand(GameTestHelper helper) {
        BlockPos command = new BlockPos(1, 1, 2);
        BlockPos servo = new BlockPos(2, 1, 2);
        helper.setBlock(command, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(servo, RedstoneEngineering.SERVO_ACTUATOR.get().defaultBlockState()
                .setValue(ServoActuatorBlock.FACING, Direction.EAST));

        helper.runAfterDelay(10, () -> {
            BlockPos world = helper.absolutePos(servo);
            int beforeLoss = ServoActuatorBlock.position(helper.getLevel(), world);
            if (beforeLoss <= 0) {
                helper.fail("Servo never moved before command-loss regression scene", servo);
                return;
            }
            helper.setBlock(command, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(4, () -> {
                int held = ServoActuatorBlock.position(helper.getLevel(), world);
                var missing = RedstoneEngineering.SERVO_ACTUATOR.get().engineeringSnapshot(
                        helper.getLevel(), world, helper.getBlockState(servo), Direction.WEST).orElseThrow();
                if (held != beforeLoss || ServoActuatorBlock.velocity(helper.getLevel(), world) != 0
                        || !ServoActuatorBlock.braking(helper.getLevel(), world)
                        || missing.quality() != PortQuality.NO_SIGNAL) {
                    helper.fail("Servo interpreted command loss as a position-zero command instead of fail-safe hold", servo);
                    return;
                }

                helper.setBlock(command, reference(Direction.EAST, 0));
                helper.runAfterDelay(5, () -> {
                    var zeroCommand = RedstoneEngineering.SERVO_ACTUATOR.get().engineeringSnapshot(
                            helper.getLevel(), world, helper.getBlockState(servo), Direction.WEST).orElseThrow();
                    if (zeroCommand.value() != 0.0 || zeroCommand.quality() != PortQuality.VALID
                            || ServoActuatorBlock.position(helper.getLevel(), world) >= held) {
                        helper.fail("Servo collapsed a real zero-position command into command absence", servo);
                        return;
                    }
                    helper.succeed();
                });
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void servoPositionSensorRejectsMisalignedMechanicalSource(GameTestHelper helper) {
        BlockPos servo = new BlockPos(1, 1, 2);
        BlockPos sensor = new BlockPos(2, 1, 2);
        helper.setBlock(servo, RedstoneEngineering.SERVO_ACTUATOR.get().defaultBlockState()
                .setValue(ServoActuatorBlock.FACING, Direction.NORTH));
        helper.setBlock(sensor, RedstoneEngineering.SERVO_POSITION_SENSOR.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));
        BlockPos sensorWorld = helper.absolutePos(sensor);

        var misalignedInput = RedstoneEngineering.SERVO_POSITION_SENSOR.get().engineeringSnapshot(
                helper.getLevel(), sensorWorld, helper.getBlockState(sensor), Direction.WEST).orElseThrow();
        var misalignedOutput = RedstoneEngineering.SERVO_POSITION_SENSOR.get().engineeringSnapshot(
                helper.getLevel(), sensorWorld, helper.getBlockState(sensor), Direction.EAST).orElseThrow();
        if (misalignedInput.quality() != PortQuality.TOPOLOGY_ERROR
                || misalignedOutput.quality() != PortQuality.TOPOLOGY_ERROR) {
            helper.fail("Servo position sensor accepted an adjacent servo whose mechanical FRONT points elsewhere", sensor);
            return;
        }

        helper.setBlock(servo, RedstoneEngineering.SERVO_ACTUATOR.get().defaultBlockState()
                .setValue(ServoActuatorBlock.FACING, Direction.EAST));
        if (ServoPositionSensorBlock.sourceQuality(
                helper.getLevel(), sensorWorld, helper.getBlockState(sensor)) != PortQuality.VALID) {
            helper.fail("Servo position sensor rejected a correctly aligned mechanical FRONT-to-BACK source", sensor);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 70)
    public static void redundantVoterRequiresRealTwoOfThreeSourceQuorum(GameTestHelper helper) {
        BlockPos voter = new BlockPos(2, 1, 2);
        BlockPos a = voter.west();
        BlockPos b = voter.north();
        BlockPos c = voter.south();
        helper.setBlock(a, reference(Direction.EAST, 5));
        helper.setBlock(b, reference(Direction.SOUTH, 5));
        helper.setBlock(voter, RedstoneEngineering.REDUNDANT_VOTER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));

        helper.runAfterDelay(4, () -> {
            BlockPos world = helper.absolutePos(voter);
            var degradedOutput = RedstoneEngineering.REDUNDANT_VOTER.get().engineeringSnapshot(
                    helper.getLevel(), world, helper.getBlockState(voter), Direction.EAST).orElseThrow();
            var missingC = RedstoneEngineering.REDUNDANT_VOTER.get().engineeringSnapshot(
                    helper.getLevel(), world, helper.getBlockState(voter), Direction.SOUTH).orElseThrow();
            if (helper.getBlockState(voter).getValue(DirectionalSignalBlock.OUTPUT) != 5
                    || degradedOutput.quality() != PortQuality.FAULT
                    || missingC.quality() != PortQuality.NO_SIGNAL) {
                helper.fail("2oo3 voter did not distinguish two-channel degraded quorum from a fabricated third zero", voter);
                return;
            }

            helper.setBlock(b, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(3, () -> {
                var noQuorum = RedstoneEngineering.REDUNDANT_VOTER.get().engineeringSnapshot(
                        helper.getLevel(), world, helper.getBlockState(voter), Direction.EAST).orElseThrow();
                if (helper.getBlockState(voter).getValue(DirectionalSignalBlock.OUTPUT) != 0
                        || noQuorum.quality() != PortQuality.NO_SIGNAL) {
                    helper.fail("2oo3 voter produced a voted value with fewer than two real sources", voter);
                    return;
                }

                helper.setBlock(b, reference(Direction.SOUTH, 5));
                helper.setBlock(c, reference(Direction.NORTH, 5));
                helper.runAfterDelay(3, () -> {
                    var healthy = RedstoneEngineering.REDUNDANT_VOTER.get().engineeringSnapshot(
                            helper.getLevel(), world, helper.getBlockState(voter), Direction.EAST).orElseThrow();
                    if (helper.getBlockState(voter).getValue(DirectionalSignalBlock.OUTPUT) != 5
                            || healthy.quality() != PortQuality.VALID
                            || RedundantVoterBlock.degraded(helper.getLevel(), world)) {
                        helper.fail("2oo3 voter failed to recover VALID quality with three agreeing real sources", voter);
                        return;
                    }
                    helper.succeed();
                });
            });
        });
    }

    private static BlockState reference(Direction front, int power) {
        return RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, front)
                .setValue(RedstoneReferenceSourceBlock.POWER, power);
    }
}
