package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.ConnectedCableBlock;
import dev.redstoneengineering.block.CopperVoltageSourceBlock;
import dev.redstoneengineering.block.CopperWireBlock;
import dev.redstoneengineering.block.ElectromagnetBlock;
import dev.redstoneengineering.block.IronCoreBlock;
import dev.redstoneengineering.block.MagneticFieldSensorBlock;
import dev.redstoneengineering.block.OpticalEmitterBlock;
import dev.redstoneengineering.block.OpticalFiberBlock;
import dev.redstoneengineering.block.OpticalReceiverBlock;
import dev.redstoneengineering.block.PermanentMagnetBlock;
import dev.redstoneengineering.block.ThermalMassBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.CopperNetworkSupport;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.physics.ThermalPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Design-identity and bug-regression contracts for registered blocks 21-30.
 *
 * <p>These tests intentionally target failure modes that ordinary happy-path
 * acceptance tests miss: observer-created state, terminal transparency,
 * back-driving sinks, source conflicts, valid physical zero, scan coverage and
 * direction-order-dependent physics.</p>
 */
public final class RseThirdTenDesignBugGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseThirdTenDesignBugGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 30)
    public static void copperCableObservationDoesNotCreateRuntimeState(GameTestHelper helper) {
        BlockPos wirePos = new BlockPos(2, 1, 2);
        helper.setBlock(wirePos, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState());
        BlockPos world = helper.absolutePos(wirePos);
        RuntimeIntStore.remove(helper.getLevel(), "copper_cable", world);

        if (RuntimeIntStore.peek(helper.getLevel(), "copper_cable", world) != null) {
            helper.fail("Precondition failed: copper runtime entry already existed", wirePos);
            return;
        }
        if (CopperWireBlock.voltage(helper.getLevel(), world) != 0) {
            helper.fail("Never-solved copper cable did not read as observer-neutral zero", wirePos);
            return;
        }
        if (RuntimeIntStore.peek(helper.getLevel(), "copper_cable", world) != null) {
            helper.fail("Reading CopperWireBlock.voltage created runtime physics state", wirePos);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void opticalFiberDistinguishesNoSourceFromDriverConflict(GameTestHelper helper) {
        BlockPos fiberPos = new BlockPos(2, 1, 2);
        helper.setBlock(fiberPos, RedstoneEngineering.OPTICAL_FIBER.get().defaultBlockState());
        BlockPos fiberWorld = helper.absolutePos(fiberPos);
        DomainNetwork.recomputeOptical(helper.getLevel(), fiberWorld);

        if (OpticalFiberBlock.quality(helper.getLevel(), fiberWorld, helper.getBlockState(fiberPos)) != PortQuality.NO_SIGNAL) {
            helper.fail("Un-driven optical fiber must report NO_SIGNAL", fiberPos);
            return;
        }

        BlockPos left = new BlockPos(1, 1, 2);
        BlockPos right = new BlockPos(3, 1, 2);
        helper.setBlock(left, opticalSource(10, 1));
        helper.setBlock(right, opticalSource(12, 2));
        helper.setBlock(fiberPos, helper.getBlockState(fiberPos)
                .setValue(ConnectedCableBlock.WEST, true)
                .setValue(ConnectedCableBlock.EAST, true));
        DomainNetwork.recomputeOptical(helper.getLevel(), fiberWorld);

        if (OpticalFiberBlock.driverCount(helper.getLevel(), fiberWorld) < 2
                || OpticalFiberBlock.quality(helper.getLevel(), fiberWorld, helper.getBlockState(fiberPos)) != PortQuality.TOPOLOGY_ERROR) {
            helper.fail("Two optical emitters on one passive segment must surface a source conflict", fiberPos);
            return;
        }

        helper.setBlock(right, Blocks.AIR.defaultBlockState());
        helper.runAfterDelay(3, () -> {
            BlockState fiber = helper.getBlockState(fiberPos);
            if (OpticalFiberBlock.quality(helper.getLevel(), fiberWorld, fiber) != PortQuality.VALID
                    || OpticalFiberBlock.driverCount(helper.getLevel(), fiberWorld) != 1
                    || OpticalFiberBlock.intensity(helper.getLevel(), fiberWorld) <= 0) {
                helper.fail("Optical segment did not recover to one valid source after conflict removal", fiberPos);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void opticalReceiverNeverBridgesIndependentFiberSegments(GameTestHelper helper) {
        BlockPos leftEmitter = new BlockPos(0, 1, 2);
        BlockPos leftFiber = new BlockPos(1, 1, 2);
        BlockPos receiver = new BlockPos(2, 1, 2);
        BlockPos rightFiber = new BlockPos(3, 1, 2);
        BlockPos rightEmitter = new BlockPos(4, 1, 2);

        helper.setBlock(leftEmitter, opticalSource(9, 1));
        helper.setBlock(leftFiber, RedstoneEngineering.OPTICAL_FIBER.get().defaultBlockState()
                .setValue(ConnectedCableBlock.WEST, true)
                .setValue(ConnectedCableBlock.EAST, true));
        helper.setBlock(receiver, RedstoneEngineering.OPTICAL_RECEIVER.get().defaultBlockState());
        helper.setBlock(rightFiber, RedstoneEngineering.OPTICAL_FIBER.get().defaultBlockState()
                .setValue(ConnectedCableBlock.WEST, true)
                .setValue(ConnectedCableBlock.EAST, true));
        helper.setBlock(rightEmitter, opticalSource(13, 7));

        DomainNetwork.recomputeOptical(helper.getLevel(), helper.absolutePos(leftFiber));
        DomainNetwork.recomputeOptical(helper.getLevel(), helper.absolutePos(rightFiber));

        helper.runAfterDelay(3, () -> {
            BlockPos leftWorld = helper.absolutePos(leftFiber);
            BlockPos rightWorld = helper.absolutePos(rightFiber);
            BlockPos receiverWorld = helper.absolutePos(receiver);
            if (!OpticalFiberBlock.valid(helper.getLevel(), leftWorld)
                    || OpticalFiberBlock.channel(helper.getLevel(), leftWorld) != 1
                    || !OpticalFiberBlock.valid(helper.getLevel(), rightWorld)
                    || OpticalFiberBlock.channel(helper.getLevel(), rightWorld) != 7) {
                helper.fail("Receiver terminal bridged two optical components into one network", receiver);
                return;
            }
            if (OpticalReceiverBlock.inputCount(helper.getLevel(), receiverWorld) != 2
                    || OpticalReceiverBlock.quality(helper.getLevel(), receiverWorld) != PortQuality.TOPOLOGY_ERROR) {
                helper.fail("Multi-fed optical receiver did not expose terminal input conflict", receiver);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void copperCableDistinguishesNoSourceSingleSourceAndConflict(GameTestHelper helper) {
        BlockPos wirePos = new BlockPos(2, 1, 2);
        BlockPos world = helper.absolutePos(wirePos);
        helper.setBlock(wirePos, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState());
        DomainNetwork.recomputeCopper(helper.getLevel(), world);
        if (CopperWireBlock.quality(helper.getLevel(), world, helper.getBlockState(wirePos)) != PortQuality.NO_SIGNAL) {
            helper.fail("Floating copper cable must report NO_SIGNAL", wirePos);
            return;
        }

        BlockPos left = new BlockPos(1, 1, 2);
        helper.setBlock(left, copperSource(12));
        helper.setBlock(wirePos, helper.getBlockState(wirePos).setValue(ConnectedCableBlock.WEST, true));
        DomainNetwork.recomputeCopper(helper.getLevel(), world);
        if (CopperWireBlock.quality(helper.getLevel(), world, helper.getBlockState(wirePos)) != PortQuality.VALID
                || CopperWireBlock.driverCount(helper.getLevel(), world) != 1) {
            helper.fail("Single Copper source did not produce one valid cable owner", wirePos);
            return;
        }

        BlockPos right = new BlockPos(3, 1, 2);
        helper.setBlock(right, copperSource(9));
        helper.setBlock(wirePos, helper.getBlockState(wirePos).setValue(ConnectedCableBlock.EAST, true));
        DomainNetwork.recomputeCopper(helper.getLevel(), world);
        if (CopperWireBlock.driverCount(helper.getLevel(), world) < 2
                || CopperWireBlock.quality(helper.getLevel(), world, helper.getBlockState(wirePos)) != PortQuality.TOPOLOGY_ERROR) {
            helper.fail("Competing Copper voltage sources did not become a topology/source conflict", wirePos);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 70)
    public static void poweredLoadCannotBackDriveElectromagnet(GameTestHelper helper) {
        BlockPos source = new BlockPos(0, 1, 2);
        BlockPos wire = new BlockPos(1, 1, 2);
        BlockPos load = new BlockPos(2, 1, 2);
        BlockPos magnet = new BlockPos(3, 1, 2);

        helper.setBlock(source, copperSource(12));
        helper.setBlock(wire, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState()
                .setValue(ConnectedCableBlock.WEST, true)
                .setValue(ConnectedCableBlock.EAST, true));
        helper.setBlock(load, RedstoneEngineering.COPPER_RESISTIVE_LOAD.get().defaultBlockState());
        helper.setBlock(magnet, RedstoneEngineering.ELECTROMAGNET.get().defaultBlockState());
        DomainNetwork.recomputeCopper(helper.getLevel(), helper.absolutePos(wire));

        helper.runAfterDelay(5, () -> {
            CopperNetworkSupport.TerminalInput loadInput = RedstoneEngineering.COPPER_RESISTIVE_LOAD.get()
                    .input(helper.getLevel(), helper.absolutePos(load));
            if (loadInput.quality() != PortQuality.VALID || loadInput.voltage() <= 0) {
                helper.fail("Precondition failed: Copper load was not energized", load);
                return;
            }
            if (helper.getBlockState(magnet).getValue(ElectromagnetBlock.FIELD) != 0) {
                helper.fail("INPUT-only Copper load illegally back-drove the adjacent electromagnet", magnet);
                return;
            }

            BlockPos legitimateSource = new BlockPos(4, 1, 2);
            helper.setBlock(legitimateSource, copperSource(10));
            helper.getLevel().scheduleTick(helper.absolutePos(magnet), RedstoneEngineering.ELECTROMAGNET.get(), 1);
            helper.runAfterDelay(3, () -> {
                if (helper.getBlockState(magnet).getValue(ElectromagnetBlock.FIELD) <= 0) {
                    helper.fail("Electromagnet rejected a legitimate adjacent Copper OUTPUT feed", magnet);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void completeZeroMagneticFieldIsValidMeasurement(GameTestHelper helper) {
        BlockPos sensor = new BlockPos(2, 1, 2);
        helper.setBlock(sensor, RedstoneEngineering.MAGNETIC_FIELD_SENSOR.get().defaultBlockState());
        helper.runAfterDelay(3, () -> {
            BlockPos world = helper.absolutePos(sensor);
            BlockState state = helper.getBlockState(sensor);
            MagneticFieldSensorBlock.Observation observation = MagneticFieldSensorBlock.observation(helper.getLevel(), world, state);
            var snapshot = RedstoneEngineering.MAGNETIC_FIELD_SENSOR.get()
                    .engineeringSnapshot(helper.getLevel(), world, state, Direction.NORTH).orElse(null);
            if (!observation.complete() || observation.field() != 0
                    || snapshot == null || snapshot.quality() != PortQuality.VALID || Math.round(snapshot.value()) != 0) {
                helper.fail("Complete magnetic scan of empty space must be VALID zero, not NO_SIGNAL", sensor);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void ironCoreMagnetizesFromRealExternalField(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 2);
        BlockPos core = new BlockPos(2, 1, 2);
        helper.setBlock(source, RedstoneEngineering.PERMANENT_MAGNET.get().defaultBlockState()
                .setValue(PermanentMagnetBlock.STRENGTH, 15));
        helper.setBlock(core, RedstoneEngineering.IRON_CORE.get().defaultBlockState());
        helper.runAfterDelay(4, () -> {
            BlockState state = helper.getBlockState(core);
            if (IronCoreBlock.appliedField(helper.getLevel(), helper.absolutePos(core)) < 8
                    || !state.getValue(IronCoreBlock.MAGNETIZED)) {
                helper.fail("Soft iron core did not acquire remanence under a strong external magnetic field", core);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 30)
    public static void thermalEnvironmentResolutionIsRotationOrderIndependent(GameTestHelper helper) {
        BlockPos center = new BlockPos(2, 1, 2);
        BlockPos north = center.north();
        BlockPos south = center.south();
        helper.setBlock(center, RedstoneEngineering.THERMAL_MASS.get().defaultBlockState());
        helper.setBlock(north, Blocks.LAVA.defaultBlockState());
        helper.setBlock(south, Blocks.BLUE_ICE.defaultBlockState());
        int first = ThermalPhysics.environmentTarget(helper.getLevel(), helper.absolutePos(center));

        helper.setBlock(north, Blocks.BLUE_ICE.defaultBlockState());
        helper.setBlock(south, Blocks.LAVA.defaultBlockState());
        int second = ThermalPhysics.environmentTarget(helper.getLevel(), helper.absolutePos(center));
        ThermalMassBlock.ThermalState thermal = ThermalMassBlock.thermalState(
                helper.getLevel(), helper.absolutePos(center), helper.getBlockState(center));

        if (first != second || first != 50 || thermal.environment() != 50) {
            helper.fail("Hot/cold thermal target changed when identical boundary conditions were rotated", center);
            return;
        }
        helper.succeed();
    }

    private static BlockState opticalSource(int intensity, int channel) {
        return RedstoneEngineering.OPTICAL_EMITTER.get().defaultBlockState()
                .setValue(OpticalEmitterBlock.INTENSITY, intensity)
                .setValue(OpticalEmitterBlock.CHANNEL, channel);
    }

    private static BlockState copperSource(int voltage) {
        return RedstoneEngineering.COPPER_VOLTAGE_SOURCE.get().defaultBlockState()
                .setValue(CopperVoltageSourceBlock.VOLTAGE, voltage);
    }
}
