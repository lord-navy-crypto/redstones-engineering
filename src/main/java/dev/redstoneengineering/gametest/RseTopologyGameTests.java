package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.ConnectedCableBlock;
import dev.redstoneengineering.block.LapisToRedstoneQuantizerBlock;
import dev.redstoneengineering.block.RedstoneCableTerminalBlock;
import dev.redstoneengineering.block.RedstoneToLapisScalerBlock;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.PortDirection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-world regression tests for the RSE engineering-port and cable topology contract. */
public final class RseTopologyGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseTopologyGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void redstoneCableConnectsToRedstoneJunction(GameTestHelper helper) {
        BlockPos cablePos = new BlockPos(1, 1, 2);
        BlockPos junctionPos = new BlockPos(2, 1, 2);

        helper.setBlock(cablePos, RedstoneEngineering.REDSTONE_SIGNAL_CABLE.get().defaultBlockState());
        helper.setBlock(junctionPos, RedstoneEngineering.REDSTONE_CABLE_JUNCTION.get().defaultBlockState());

        helper.runAfterDelay(2, () -> {
            BlockState cable = helper.getBlockState(cablePos);
            BlockState junction = helper.getBlockState(junctionPos);
            if (!ConnectedCableBlock.connected(cable, Direction.EAST)) {
                helper.fail("Insulated redstone cable did not connect east to its junction", cablePos);
                return;
            }
            if (!ConnectedCableBlock.connected(junction, Direction.WEST)) {
                helper.fail("Redstone junction did not connect west to insulated cable", junctionPos);
                return;
            }
            helper.succeed();
        });
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
}
