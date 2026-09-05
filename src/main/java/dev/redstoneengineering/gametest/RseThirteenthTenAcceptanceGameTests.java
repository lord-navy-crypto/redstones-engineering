package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.CopperCableJunctionBlock;
import dev.redstoneengineering.block.CopperCapacitorBlock;
import dev.redstoneengineering.block.CopperCircuitMeterBlock;
import dev.redstoneengineering.block.CopperFuseBlock;
import dev.redstoneengineering.block.CopperResistiveLoadBlock;
import dev.redstoneengineering.block.CopperSeriesResistorBlock;
import dev.redstoneengineering.block.CopperWireBlock;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.block.ThermalMassBlock;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.physics.DomainNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Thirteenth block-by-block campaign: ten copper/electrothermal devices.
 *
 * <p>The emphasis is lifecycle correctness. Removing a conductor, source, junction, or processor
 * must not leave an energized ghost island, while measurement and electrothermal endpoints remain
 * explicit non-redstone engineering-domain boundaries.</p>
 */
public final class RseThirteenthTenAcceptanceGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseThirteenthTenAcceptanceGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void copperWirePublishesConnectedBusPortsAndClearsSplitIsland(GameTestHelper helper) {
        BlockPos source = new BlockPos(0, 1, 2);
        BlockPos wireA = new BlockPos(1, 1, 2);
        BlockPos wireB = new BlockPos(2, 1, 2);
        BlockPos load = new BlockPos(3, 1, 2);

        helper.setBlock(source, RedstoneEngineering.COPPER_VOLTAGE_SOURCE.get().defaultBlockState());
        helper.setBlock(wireA, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState());
        helper.setBlock(wireB, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState());
        helper.setBlock(load, RedstoneEngineering.COPPER_RESISTIVE_LOAD.get().defaultBlockState());

        helper.runAfterDelay(5, () -> {
            CopperWireBlock block = RedstoneEngineering.COPPER_WIRE.get();
            BlockState state = helper.getBlockState(wireA);
            if (!hasPort(block, state, Direction.WEST, EngineeringDomain.COPPER, PortKind.BUS, PortDirection.BIDIRECTIONAL)
                    || !hasPort(block, state, Direction.EAST, EngineeringDomain.COPPER, PortKind.BUS, PortDirection.BIDIRECTIONAL)) {
                helper.fail("Copper wire did not expose connected WEST/EAST COPPER bus ports", wireA);
                return;
            }
            if (block.engineeringPort(state, Direction.NORTH).isPresent()
                    || block.engineeringPort(state, Direction.UP).isPresent()) {
                helper.fail("Copper wire exposed an engineering port on an unconnected face", wireA);
                return;
            }
            if (CopperWireBlock.voltage(helper.getLevel(), helper.absolutePos(wireB)) <= 0) {
                helper.fail("Copper wire island was not energized before split test", wireB);
                return;
            }

            helper.setBlock(wireA, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(5, () -> {
                if (CopperWireBlock.voltage(helper.getLevel(), helper.absolutePos(wireB)) != 0
                        || helper.getBlockState(load).getValue(CopperResistiveLoadBlock.VOLTAGE) != 0) {
                    helper.fail("Removing copper wire left a ghost-powered disconnected island", wireB);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void copperJunctionRemovalClearsEveryFormerBranch(GameTestHelper helper) {
        BlockPos source = new BlockPos(0, 1, 2);
        BlockPos feeder = new BlockPos(1, 1, 2);
        BlockPos junction = new BlockPos(2, 1, 2);
        BlockPos eastBranch = new BlockPos(3, 1, 2);
        BlockPos northBranch = new BlockPos(2, 1, 1);

        helper.setBlock(source, RedstoneEngineering.COPPER_VOLTAGE_SOURCE.get().defaultBlockState());
        helper.setBlock(feeder, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState());
        helper.setBlock(junction, RedstoneEngineering.COPPER_CABLE_JUNCTION.get().defaultBlockState());
        helper.setBlock(eastBranch, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState());
        helper.setBlock(northBranch, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState());

        helper.runAfterDelay(5, () -> {
            if (CopperCableJunctionBlock.voltage(helper.getLevel(), helper.absolutePos(junction)) <= 0
                    || CopperWireBlock.voltage(helper.getLevel(), helper.absolutePos(eastBranch)) <= 0
                    || CopperWireBlock.voltage(helper.getLevel(), helper.absolutePos(northBranch)) <= 0) {
                helper.fail("Copper junction did not energize both explicit branches before removal", junction);
                return;
            }
            helper.setBlock(junction, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(5, () -> {
                if (CopperWireBlock.voltage(helper.getLevel(), helper.absolutePos(eastBranch)) != 0
                        || CopperWireBlock.voltage(helper.getLevel(), helper.absolutePos(northBranch)) != 0) {
                    helper.fail("Removing copper junction left one or more former branches energized", junction);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void voltageSourceRemovalClearsPoweredCableAndLoad(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 2);
        BlockPos wire = new BlockPos(2, 1, 2);
        BlockPos load = new BlockPos(3, 1, 2);

        helper.setBlock(source, RedstoneEngineering.COPPER_VOLTAGE_SOURCE.get().defaultBlockState());
        helper.setBlock(wire, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState());
        helper.setBlock(load, RedstoneEngineering.COPPER_RESISTIVE_LOAD.get().defaultBlockState());

        helper.runAfterDelay(5, () -> {
            if (CopperWireBlock.voltage(helper.getLevel(), helper.absolutePos(wire)) <= 0) {
                helper.fail("Copper source did not energize its cable before removal", wire);
                return;
            }
            helper.setBlock(source, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(5, () -> {
                if (CopperWireBlock.voltage(helper.getLevel(), helper.absolutePos(wire)) != 0
                        || helper.getBlockState(load).getValue(CopperResistiveLoadBlock.VOLTAGE) != 0) {
                    helper.fail("Removing the only copper source left stale voltage in its old network", wire);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void resistiveLoadIsSixFaceInputButNeverTransparentConductor(GameTestHelper helper) {
        BlockPos source = new BlockPos(0, 1, 2);
        BlockPos feeder = new BlockPos(1, 1, 2);
        BlockPos load = new BlockPos(2, 1, 2);
        BlockPos behindLoad = new BlockPos(3, 1, 2);

        helper.setBlock(source, RedstoneEngineering.COPPER_VOLTAGE_SOURCE.get().defaultBlockState());
        helper.setBlock(feeder, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState());
        helper.setBlock(load, RedstoneEngineering.COPPER_RESISTIVE_LOAD.get().defaultBlockState());
        helper.setBlock(behindLoad, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState());

        helper.runAfterDelay(6, () -> {
            CopperResistiveLoadBlock block = RedstoneEngineering.COPPER_RESISTIVE_LOAD.get();
            BlockState state = helper.getBlockState(load);
            for (Direction side : Direction.values()) {
                if (!hasPort(block, state, side, EngineeringDomain.COPPER, PortKind.ELECTRICAL, PortDirection.INPUT)) {
                    helper.fail("Copper load must expose a terminal COPPER input on every face", load);
                    return;
                }
            }
            if (state.getValue(CopperResistiveLoadBlock.VOLTAGE) <= 0) {
                helper.fail("Copper load was not energized from its feeder", load);
                return;
            }
            if (CopperWireBlock.voltage(helper.getLevel(), helper.absolutePos(behindLoad)) != 0) {
                helper.fail("Terminal resistive load incorrectly became a transparent conductor", behindLoad);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void seriesResistorRemovalReleasesProtectedOutputDriver(GameTestHelper helper) {
        BlockPos source = new BlockPos(0, 1, 2);
        BlockPos inputWire = new BlockPos(1, 1, 2);
        BlockPos resistor = new BlockPos(2, 1, 2);
        BlockPos outputWire = new BlockPos(3, 1, 2);
        BlockPos load = new BlockPos(4, 1, 2);

        helper.setBlock(source, RedstoneEngineering.COPPER_VOLTAGE_SOURCE.get().defaultBlockState());
        helper.setBlock(inputWire, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState());
        helper.setBlock(resistor, RedstoneEngineering.COPPER_SERIES_RESISTOR.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                .setValue(CopperSeriesResistorBlock.RESISTANCE, 4));
        helper.setBlock(outputWire, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState());
        helper.setBlock(load, RedstoneEngineering.COPPER_RESISTIVE_LOAD.get().defaultBlockState());

        helper.runAfterDelay(14, () -> {
            if (CopperSeriesResistorBlock.outputVoltage(helper.getLevel(), helper.absolutePos(resistor)) <= 0) {
                helper.fail("Series resistor output was not energized before removal", resistor);
                return;
            }
            helper.setBlock(resistor, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(5, () -> {
                if (CopperWireBlock.voltage(helper.getLevel(), helper.absolutePos(outputWire)) != 0
                        || helper.getBlockState(load).getValue(CopperResistiveLoadBlock.VOLTAGE) != 0) {
                    helper.fail("Removing series resistor left its former output driver alive", outputWire);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void capacitorRemovalClearsStoredOutputIsland(GameTestHelper helper) {
        BlockPos source = new BlockPos(0, 1, 2);
        BlockPos inputWire = new BlockPos(1, 1, 2);
        BlockPos capacitor = new BlockPos(2, 1, 2);
        BlockPos outputWire = new BlockPos(3, 1, 2);
        BlockPos load = new BlockPos(4, 1, 2);

        helper.setBlock(source, RedstoneEngineering.COPPER_VOLTAGE_SOURCE.get().defaultBlockState());
        helper.setBlock(inputWire, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState());
        helper.setBlock(capacitor, RedstoneEngineering.COPPER_CAPACITOR.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                .setValue(CopperCapacitorBlock.C_INDEX, 0));
        helper.setBlock(outputWire, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState());
        helper.setBlock(load, RedstoneEngineering.COPPER_RESISTIVE_LOAD.get().defaultBlockState());

        helper.runAfterDelay(22, () -> {
            if (CopperCapacitorBlock.outputVoltage(helper.getLevel(), helper.absolutePos(capacitor)) <= 0) {
                helper.fail("Copper capacitor did not charge before removal", capacitor);
                return;
            }
            helper.setBlock(capacitor, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(5, () -> {
                if (CopperWireBlock.voltage(helper.getLevel(), helper.absolutePos(outputWire)) != 0
                        || helper.getBlockState(load).getValue(CopperResistiveLoadBlock.VOLTAGE) != 0) {
                    helper.fail("Removing charged capacitor left a ghost-powered output island", outputWire);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void fuseRemovalClearsProtectedOutputIsland(GameTestHelper helper) {
        BlockPos source = new BlockPos(0, 1, 2);
        BlockPos inputWire = new BlockPos(1, 1, 2);
        BlockPos fuse = new BlockPos(2, 1, 2);
        BlockPos outputWire = new BlockPos(3, 1, 2);
        BlockPos load = new BlockPos(4, 1, 2);

        helper.setBlock(source, RedstoneEngineering.COPPER_VOLTAGE_SOURCE.get().defaultBlockState());
        helper.setBlock(inputWire, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState());
        helper.setBlock(fuse, RedstoneEngineering.COPPER_FUSE.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                .setValue(CopperFuseBlock.RATING, 15));
        helper.setBlock(outputWire, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState());
        helper.setBlock(load, RedstoneEngineering.COPPER_RESISTIVE_LOAD.get().defaultBlockState()
                .setValue(CopperResistiveLoadBlock.RESISTANCE, 15));

        helper.runAfterDelay(12, () -> {
            if (CopperFuseBlock.outputVoltage(helper.getLevel(), helper.absolutePos(fuse)) <= 0
                    || helper.getBlockState(fuse).getValue(CopperFuseBlock.TRIPPED)) {
                helper.fail("High-rated fuse was not conducting before removal", fuse);
                return;
            }
            helper.setBlock(fuse, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(5, () -> {
                if (CopperWireBlock.voltage(helper.getLevel(), helper.absolutePos(outputWire)) != 0
                        || helper.getBlockState(load).getValue(CopperResistiveLoadBlock.VOLTAGE) != 0) {
                    helper.fail("Removing fuse left its protected island energized", outputWire);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void circuitMeterObservesFacingCopperWithoutBridgingThrough(GameTestHelper helper) {
        BlockPos source = new BlockPos(0, 1, 2);
        BlockPos observedWire = new BlockPos(1, 1, 2);
        BlockPos meter = new BlockPos(2, 1, 2);
        BlockPos isolatedWire = new BlockPos(3, 1, 2);

        helper.setBlock(source, RedstoneEngineering.COPPER_VOLTAGE_SOURCE.get().defaultBlockState());
        helper.setBlock(observedWire, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState());
        helper.setBlock(meter, RedstoneEngineering.COPPER_CIRCUIT_METER.get().defaultBlockState()
                .setValue(CopperCircuitMeterBlock.FACING, Direction.WEST));
        helper.setBlock(isolatedWire, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState());

        helper.runAfterDelay(12, () -> {
            CopperCircuitMeterBlock block = RedstoneEngineering.COPPER_CIRCUIT_METER.get();
            BlockState state = helper.getBlockState(meter);
            if (!hasPort(block, state, Direction.WEST, EngineeringDomain.COPPER, PortKind.MEASUREMENT, PortDirection.INPUT)
                    || block.engineeringPorts(state).size() != 1) {
                helper.fail("Copper meter must expose exactly one facing observer-only measurement port", meter);
                return;
            }
            if (CopperCircuitMeterBlock.sampledVoltage(helper.getLevel(), helper.absolutePos(meter), state) <= 0) {
                helper.fail("Copper meter did not observe the energized facing wire", meter);
                return;
            }
            if (CopperWireBlock.voltage(helper.getLevel(), helper.absolutePos(isolatedWire)) != 0) {
                helper.fail("Copper meter incorrectly bridged power through its body", isolatedWire);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void thermalHeaterExposesSixCopperConverterInputs(GameTestHelper helper) {
        BlockPos source = new BlockPos(0, 1, 2);
        BlockPos wire = new BlockPos(1, 1, 2);
        BlockPos heater = new BlockPos(2, 1, 2);

        helper.setBlock(source, RedstoneEngineering.COPPER_VOLTAGE_SOURCE.get().defaultBlockState());
        helper.setBlock(wire, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState());
        helper.setBlock(heater, RedstoneEngineering.THERMAL_HEATER.get().defaultBlockState());

        helper.runAfterDelay(12, () -> {
            EngineeringPortProvider provider = RedstoneEngineering.THERMAL_HEATER.get();
            BlockState state = helper.getBlockState(heater);
            for (Direction side : Direction.values()) {
                if (!hasPort(provider, state, side, EngineeringDomain.COPPER, PortKind.CONVERTER, PortDirection.INPUT)) {
                    helper.fail("Thermal heater must expose COPPER converter input on all six physical faces", heater);
                    return;
                }
            }
            if (state.getValue(dev.redstoneengineering.block.ThermalHeaterBlock.TEMPERATURE) <= 20) {
                helper.fail("Copper-powered thermal heater did not convert electrical power into heat", heater);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 30)
    public static void thermalMassPublishesSixFaceThermalBodyState(GameTestHelper helper) {
        BlockPos mass = new BlockPos(2, 1, 2);
        helper.setBlock(mass, RedstoneEngineering.THERMAL_MASS.get().defaultBlockState()
                .setValue(ThermalMassBlock.TEMPERATURE, 80)
                .setValue(ThermalMassBlock.HEAT_CAPACITY, 4));

        EngineeringPortProvider provider = RedstoneEngineering.THERMAL_MASS.get();
        BlockState state = helper.getBlockState(mass);
        for (Direction side : Direction.values()) {
            if (!hasPort(provider, state, side, EngineeringDomain.THERMAL, PortKind.BUS, PortDirection.BIDIRECTIONAL)) {
                helper.fail("Thermal mass must publish a bidirectional THERMAL body port on every face", mass);
                return;
            }
            var snapshot = provider.engineeringSnapshot(helper.getLevel(), helper.absolutePos(mass), state, side).orElse(null);
            if (snapshot == null || Math.round(snapshot.value()) != 80) {
                helper.fail("Thermal mass port snapshot did not publish its physical T-index", mass);
                return;
            }
        }
        helper.succeed();
    }

    private static boolean hasPort(
            EngineeringPortProvider provider,
            BlockState state,
            Direction side,
            EngineeringDomain domain,
            PortKind kind,
            PortDirection direction
    ) {
        var port = provider.engineeringPort(state, side).orElse(null);
        return port != null
                && port.domain() == domain
                && port.kind() == kind
                && port.direction() == direction;
    }
}
