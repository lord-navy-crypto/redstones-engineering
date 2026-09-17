package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.CopperFuseBlock;
import dev.redstoneengineering.block.CopperSeriesResistorBlock;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.physics.DomainNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Runtime regression coverage for unloaded/open-circuit Copper behavior.
 *
 * <p>An open circuit has no downstream current. Therefore a series resistor must
 * not manufacture a voltage drop without load current, and a fuse must not trip
 * solely because a voltage source is present upstream.</p>
 */
public final class RseCopperOpenCircuitGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseCopperOpenCircuitGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void seriesResistorOpenCircuitHasNoVoltageDrop(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(0, 1, 2);
        BlockPos inputWirePos = new BlockPos(1, 1, 2);
        BlockPos resistorPos = new BlockPos(2, 1, 2);
        BlockPos outputWirePos = new BlockPos(3, 1, 2);

        helper.setBlock(sourcePos, RedstoneEngineering.COPPER_VOLTAGE_SOURCE.get().defaultBlockState());
        helper.setBlock(inputWirePos, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState());
        helper.setBlock(
                resistorPos,
                RedstoneEngineering.COPPER_SERIES_RESISTOR.get().defaultBlockState()
                        .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                        .setValue(CopperSeriesResistorBlock.RESISTANCE, 4)
        );
        helper.setBlock(outputWirePos, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState());

        helper.runAfterDelay(14, () -> {
            int input = DomainNetwork.sampleCopperVoltage(helper.getLevel(), helper.absolutePos(inputWirePos));
            int output = DomainNetwork.sampleCopperVoltage(helper.getLevel(), helper.absolutePos(outputWirePos));
            if (input <= 0) {
                helper.fail("Copper source did not energize the open-circuit resistor input", inputWirePos);
                return;
            }
            if (output != input) {
                helper.fail("Open-circuit series resistor created a voltage drop without load current", resistorPos);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void unloadedFuseDoesNotTrip(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(0, 1, 2);
        BlockPos inputWirePos = new BlockPos(1, 1, 2);
        BlockPos fusePos = new BlockPos(2, 1, 2);
        BlockPos outputWirePos = new BlockPos(3, 1, 2);

        helper.setBlock(sourcePos, RedstoneEngineering.COPPER_VOLTAGE_SOURCE.get().defaultBlockState());
        helper.setBlock(inputWirePos, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState());
        helper.setBlock(
                fusePos,
                RedstoneEngineering.COPPER_FUSE.get().defaultBlockState()
                        .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                        .setValue(CopperFuseBlock.RATING, 1)
        );
        helper.setBlock(outputWirePos, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState());

        helper.runAfterDelay(14, () -> {
            BlockState fuse = helper.getBlockState(fusePos);
            int output = DomainNetwork.sampleCopperVoltage(helper.getLevel(), helper.absolutePos(outputWirePos));
            if (fuse.getValue(CopperFuseBlock.TRIPPED)) {
                helper.fail("Unloaded fuse tripped even though an open circuit carries no current", fusePos);
                return;
            }
            if (output <= 0 || CopperFuseBlock.outputVoltage(helper.getLevel(), helper.absolutePos(fusePos)) <= 0) {
                helper.fail("Healthy unloaded fuse failed to preserve the upstream voltage", outputWirePos);
                return;
            }
            helper.succeed();
        });
    }
}
