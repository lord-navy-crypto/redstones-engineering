package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.AnalogIndicatorBlock;
import dev.redstoneengineering.block.ConnectedCableBlock;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.EngineeringLightSensorBlock;
import dev.redstoneengineering.block.LapisToRedstoneQuantizerBlock;
import dev.redstoneengineering.block.RedstoneCableTerminalBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.RedstoneToLapisScalerBlock;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.PortDirection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-world regression tests for the RSE engineering-port and cable topology contract. */
public final class RseTopologyGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseTopologyGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void redstoneCableConnectsToRedstoneJunction(GameTestHelper helper) {
        BlockPos cableFirst = new BlockPos(1, 1, 1);
        BlockPos junctionSecond = new BlockPos(2, 1, 1);
        BlockPos junctionFirst = new BlockPos(1, 1, 3);
        BlockPos cableSecond = new BlockPos(2, 1, 3);

        // Order A: cable exists first, then junction arrives.
        helper.setBlock(cableFirst, RedstoneEngineering.REDSTONE_SIGNAL_CABLE.get().defaultBlockState());
        helper.setBlock(junctionSecond, RedstoneEngineering.REDSTONE_CABLE_JUNCTION.get().defaultBlockState());

        // Order B: junction exists first, then cable arrives.
        helper.setBlock(junctionFirst, RedstoneEngineering.REDSTONE_CABLE_JUNCTION.get().defaultBlockState());
        helper.setBlock(cableSecond, RedstoneEngineering.REDSTONE_SIGNAL_CABLE.get().defaultBlockState());

        helper.runAfterDelay(2, () -> {
            assertConnectedPair(helper, cableFirst, Direction.EAST, junctionSecond, Direction.WEST, "cable-first");
            assertConnectedPair(helper, cableSecond, Direction.WEST, junctionFirst, Direction.EAST, "junction-first");
            helper.succeed();
        });
    }

    private static void assertConnectedPair(
            GameTestHelper helper,
            BlockPos cablePos,
            Direction cableSide,
            BlockPos junctionPos,
            Direction junctionSide,
            String order
    ) {
        BlockState cable = helper.getBlockState(cablePos);
        BlockState junction = helper.getBlockState(junctionPos);
        if (!ConnectedCableBlock.connected(cable, cableSide)) {
            helper.fail("Insulated redstone cable did not connect to its junction (" + order + ")", cablePos);
        }
        if (!ConnectedCableBlock.connected(junction, junctionSide)) {
            helper.fail("Redstone junction did not connect to insulated cable (" + order + ")", junctionPos);
        }
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void redstoneCableRejectsCopperJunction(GameTestHelper helper) {
        BlockPos cablePos = new BlockPos(1, 1, 2);
        BlockPos copperPos = new BlockPos(2, 1, 2);

        helper.setBlock(cablePos, RedstoneEngineering.REDSTONE_SIGNAL_CABLE.get().defaultBlockState());
        helper.setBlock(copperPos, RedstoneEngineering.COPPER_CABLE_JUNCTION.get().defaultBlockState());

        helper.runAfterDelay(2, () -> {
            BlockState cable = helper.getBlockState(cablePos);
            BlockState copper = helper.getBlockState(copperPos);
            if (ConnectedCableBlock.connected(cable, Direction.EAST)) {
                helper.fail("Redstone cable incorrectly connected to the COPPER domain", cablePos);
                return;
            }
            if (ConnectedCableBlock.connected(copper, Direction.WEST)) {
                helper.fail("Copper junction incorrectly connected to the REDSTONE domain", copperPos);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void terminalDirectionFollowsMode(GameTestHelper helper) {
        RedstoneCableTerminalBlock block = RedstoneEngineering.REDSTONE_CABLE_TERMINAL.get();
        EngineeringPortProvider provider = block;
        BlockState inputMode = block.defaultBlockState()
                .setValue(RedstoneCableTerminalBlock.FACING, Direction.EAST)
                .setValue(RedstoneCableTerminalBlock.OUTPUT_MODE, false);

        var vanillaIn = provider.engineeringPort(inputMode, Direction.EAST).orElse(null);
        var cableOut = provider.engineeringPort(inputMode, Direction.WEST).orElse(null);
        if (vanillaIn == null || vanillaIn.direction() != PortDirection.INPUT) {
            helper.fail("Terminal input mode must expose VANILLA INPUT on FACING", new BlockPos(1, 1, 1));
            return;
        }
        if (cableOut == null || cableOut.direction() != PortDirection.OUTPUT) {
            helper.fail("Terminal input mode must expose CABLE OUTPUT opposite FACING", new BlockPos(1, 1, 1));
            return;
        }

        BlockState outputMode = inputMode.setValue(RedstoneCableTerminalBlock.OUTPUT_MODE, true);
        var vanillaOut = provider.engineeringPort(outputMode, Direction.EAST).orElse(null);
        var cableIn = provider.engineeringPort(outputMode, Direction.WEST).orElse(null);
        if (vanillaOut == null || vanillaOut.direction() != PortDirection.OUTPUT) {
            helper.fail("Terminal output mode must expose VANILLA OUTPUT on FACING", new BlockPos(1, 1, 1));
            return;
        }
        if (cableIn == null || cableIn.direction() != PortDirection.INPUT) {
            helper.fail("Terminal output mode must expose CABLE INPUT opposite FACING", new BlockPos(1, 1, 1));
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void explicitConvertersBridgeDomains(GameTestHelper helper) {
        RedstoneToLapisScalerBlock scaler = RedstoneEngineering.REDSTONE_TO_LAPIS_SCALER.get();
        BlockState scalerState = scaler.defaultBlockState().setValue(RedstoneToLapisScalerBlock.FACING, Direction.EAST);
        EngineeringPortProvider scalerProvider = scaler;

        var scalerInput = scalerProvider.engineeringPort(scalerState, Direction.WEST).orElse(null);
        var scalerOutput = scalerProvider.engineeringPort(scalerState, Direction.EAST).orElse(null);
        if (scalerInput == null || scalerInput.domain() != EngineeringDomain.REDSTONE || scalerInput.direction() != PortDirection.INPUT) {
            helper.fail("Redstone→Lapis scaler must expose REDSTONE INPUT on BACK", new BlockPos(1, 1, 1));
            return;
        }
        if (scalerOutput == null || scalerOutput.domain() != EngineeringDomain.LAPIS || scalerOutput.direction() != PortDirection.OUTPUT) {
            helper.fail("Redstone→Lapis scaler must expose LAPIS OUTPUT on FRONT", new BlockPos(1, 1, 1));
            return;
        }

        LapisToRedstoneQuantizerBlock quantizer = RedstoneEngineering.LAPIS_TO_REDSTONE_QUANTIZER.get();
        BlockState quantizerState = quantizer.defaultBlockState().setValue(LapisToRedstoneQuantizerBlock.FACING, Direction.EAST);
        EngineeringPortProvider quantizerProvider = quantizer;
        var quantizerInput = quantizerProvider.engineeringPort(quantizerState, Direction.WEST).orElse(null);
        var quantizerOutput = quantizerProvider.engineeringPort(quantizerState, Direction.EAST).orElse(null);
        if (quantizerInput == null || quantizerInput.domain() != EngineeringDomain.LAPIS || quantizerInput.direction() != PortDirection.INPUT) {
            helper.fail("Lapis→Redstone quantizer must expose LAPIS INPUT on BACK", new BlockPos(1, 1, 1));
            return;
        }
        if (quantizerOutput == null || quantizerOutput.domain() != EngineeringDomain.REDSTONE || quantizerOutput.direction() != PortDirection.OUTPUT) {
            helper.fail("Lapis→Redstone quantizer must expose REDSTONE OUTPUT on FRONT", new BlockPos(1, 1, 1));
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void directionalRedstoneEndpointsExposeOnlyPhysicalPorts(GameTestHelper helper) {
        BlockPos probePos = new BlockPos(1, 1, 1);

        RedstoneReferenceSourceBlock source = RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get();
        BlockState sourceState = source.defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.EAST)
                .setValue(RedstoneReferenceSourceBlock.POWER, 11);
        EngineeringPortProvider sourceProvider = source;
        var sourceOut = sourceProvider.engineeringPort(sourceState, Direction.EAST).orElse(null);
        if (sourceOut == null || sourceOut.direction() != PortDirection.OUTPUT) {
            helper.fail("Reference source must expose one physical FRONT output", probePos);
            return;
        }
        if (sourceProvider.engineeringPort(sourceState, Direction.WEST).isPresent()) {
            helper.fail("Reference source BACK must not be an engineering output", probePos);
            return;
        }
        // Physical FRONT=EAST is queried by vanilla redstone with direction WEST.
        if (!source.canConnectRedstone(sourceState, helper.getLevel(), helper.absolutePos(probePos), Direction.WEST)
                || source.canConnectRedstone(sourceState, helper.getLevel(), helper.absolutePos(probePos), Direction.EAST)) {
            helper.fail("Reference source redstone connectivity does not match FRONT-only query semantics", probePos);
            return;
        }

        EngineeringLightSensorBlock sensor = RedstoneEngineering.ENGINEERING_LIGHT_SENSOR.get();
        BlockState sensorState = sensor.defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.SOUTH)
                .setValue(EngineeringLightSensorBlock.POWER, 7);
        EngineeringPortProvider sensorProvider = sensor;
        var sensorOut = sensorProvider.engineeringPort(sensorState, Direction.SOUTH).orElse(null);
        if (sensorOut == null || sensorOut.direction() != PortDirection.OUTPUT) {
            helper.fail("Directional sensor must expose FRONT output", probePos);
            return;
        }
        if (sensorProvider.engineeringPort(sensorState, Direction.NORTH).isPresent()) {
            helper.fail("Directional sensor BACK must not expose an output", probePos);
            return;
        }

        AnalogIndicatorBlock indicator = RedstoneEngineering.ANALOG_INDICATOR.get();
        BlockState indicatorState = indicator.defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.EAST);
        EngineeringPortProvider indicatorProvider = indicator;
        var indicatorInput = indicatorProvider.engineeringPort(indicatorState, Direction.WEST).orElse(null);
        if (indicatorInput == null || indicatorInput.direction() != PortDirection.INPUT) {
            helper.fail("Analog indicator must expose BACK input", probePos);
            return;
        }
        if (indicatorProvider.engineeringPort(indicatorState, Direction.EAST).isPresent()) {
            helper.fail("Analog indicator FRONT display face must not be an electrical input", probePos);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void analogIndicatorReadsBackOnly(GameTestHelper helper) {
        BlockPos indicatorPos = new BlockPos(2, 1, 2);
        BlockPos backPos = indicatorPos.west();
        BlockPos sidePos = indicatorPos.north();
        BlockState indicator = RedstoneEngineering.ANALOG_INDICATOR.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.EAST);

        helper.setBlock(backPos, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(indicatorPos, indicator);

        helper.runAfterDelay(2, () -> {
            BlockState powered = helper.getBlockState(indicatorPos);
            if (powered.getValue(AnalogIndicatorBlock.LEVEL) != 15) {
                helper.fail("Analog indicator did not read full-strength BACK input", indicatorPos);
                return;
            }

            helper.setBlock(backPos, Blocks.AIR.defaultBlockState());
            helper.setBlock(sidePos, Blocks.REDSTONE_BLOCK.defaultBlockState());

            helper.runAfterDelay(2, () -> {
                BlockState sidePowered = helper.getBlockState(indicatorPos);
                if (sidePowered.getValue(AnalogIndicatorBlock.LEVEL) != 0) {
                    helper.fail("Analog indicator incorrectly accepted a SIDE input", indicatorPos);
                    return;
                }
                helper.succeed();
            });
        });
    }
}
