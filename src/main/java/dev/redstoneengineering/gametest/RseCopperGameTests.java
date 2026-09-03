package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.CopperFuseBlock;
import dev.redstoneengineering.block.CopperResistiveLoadBlock;
import dev.redstoneengineering.block.CopperSeriesResistorBlock;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.physics.DomainNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Runtime regression tests for the Alpha 1.0.13 copper engineering topology. */
public final class RseCopperGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseCopperGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void axialCopperProcessorsExposeBackAndFrontPorts(GameTestHelper helper) {
        BlockPos marker = new BlockPos(2, 1, 2);
        assertAxialPorts(
                helper,
                RedstoneEngineering.COPPER_SERIES_RESISTOR.get(),
                RedstoneEngineering.COPPER_SERIES_RESISTOR.get().defaultBlockState()
                        .setValue(DirectionalDomainBlock.FACING, Direction.EAST),
                marker,
                "series resistor"
        );
        assertAxialPorts(
                helper,
                RedstoneEngineering.COPPER_CAPACITOR.get(),
                RedstoneEngineering.COPPER_CAPACITOR.get().defaultBlockState()
                        .setValue(DirectionalDomainBlock.FACING, Direction.SOUTH),
                marker,
                "capacitor"
        );
        assertAxialPorts(
                helper,
                RedstoneEngineering.COPPER_FUSE.get(),
                RedstoneEngineering.COPPER_FUSE.get().defaultBlockState()
                        .setValue(DirectionalDomainBlock.FACING, Direction.WEST),
                marker,
                "fuse"
        );
        helper.succeed();
    }

    private static void assertAxialPorts(
            GameTestHelper helper,
            EngineeringPortProvider provider,
            BlockState state,
            BlockPos marker,
            String name
    ) {
        Direction front = state.getValue(DirectionalDomainBlock.FACING);
        Direction back = front.getOpposite();
        var input = provider.engineeringPort(state, back).orElse(null);
        var output = provider.engineeringPort(state, front).orElse(null);
        if (input == null
                || input.domain() != EngineeringDomain.COPPER
                || input.kind() != PortKind.ELECTRICAL
                || input.direction() != PortDirection.INPUT) {
            helper.fail("Copper " + name + " must expose COPPER ELECTRICAL INPUT on BACK", marker);
        }
        if (output == null
                || output.domain() != EngineeringDomain.COPPER
                || output.kind() != PortKind.ELECTRICAL
                || output.direction() != PortDirection.OUTPUT) {
            helper.fail("Copper " + name + " must expose COPPER ELECTRICAL OUTPUT on FRONT", marker);
        }
        for (Direction side : Direction.values()) {
            if (side != back && side != front && provider.engineeringPort(state, side).isPresent()) {
                helper.fail("Copper " + name + " incorrectly exposes a SIDE port", marker);
            }
        }
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void seriesResistorPropagatesAttenuatedVoltage(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(0, 1, 2);
        BlockPos inputWirePos = new BlockPos(1, 1, 2);
        BlockPos resistorPos = new BlockPos(2, 1, 2);
        BlockPos outputWirePos = new BlockPos(3, 1, 2);
        BlockPos loadPos = new BlockPos(4, 1, 2);

        helper.setBlock(sourcePos, RedstoneEngineering.COPPER_VOLTAGE_SOURCE.get().defaultBlockState());
        helper.setBlock(inputWirePos, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState());
        helper.setBlock(
                resistorPos,
                RedstoneEngineering.COPPER_SERIES_RESISTOR.get().defaultBlockState()
                        .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                        .setValue(CopperSeriesResistorBlock.RESISTANCE, 4)
        );
        helper.setBlock(outputWirePos, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState());
        helper.setBlock(
                loadPos,
                RedstoneEngineering.COPPER_RESISTIVE_LOAD.get().defaultBlockState()
                        .setValue(CopperResistiveLoadBlock.RESISTANCE, 4)
        );

        helper.runAfterDelay(14, () -> {
            int input = DomainNetwork.sampleCopperVoltage(helper.getLevel(), helper.absolutePos(inputWirePos));
            int output = DomainNetwork.sampleCopperVoltage(helper.getLevel(), helper.absolutePos(outputWirePos));
            int load = helper.getBlockState(loadPos).getValue(CopperResistiveLoadBlock.VOLTAGE);
            if (input <= 0) {
                helper.fail("Copper source did not energize the resistor input segment", inputWirePos);
                return;
            }
            if (output <= 0 || output >= input) {
                helper.fail("Series resistor output must be nonzero and attenuated below input", outputWirePos);
                return;
            }
            if (load <= 0 || load > output) {
                helper.fail("Copper load did not receive the protected output segment", loadPos);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void fuseTripsAndCutsProtectedOutput(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(0, 1, 2);
        BlockPos inputWirePos = new BlockPos(1, 1, 2);
        BlockPos fusePos = new BlockPos(2, 1, 2);
        BlockPos outputWirePos = new BlockPos(3, 1, 2);
        BlockPos loadPos = new BlockPos(4, 1, 2);

        helper.setBlock(sourcePos, RedstoneEngineering.COPPER_VOLTAGE_SOURCE.get().defaultBlockState());
        helper.setBlock(inputWirePos, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState());
        helper.setBlock(
                fusePos,
                RedstoneEngineering.COPPER_FUSE.get().defaultBlockState()
                        .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                        .setValue(CopperFuseBlock.RATING, 1)
        );
        helper.setBlock(outputWirePos, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState());
        helper.setBlock(
                loadPos,
                RedstoneEngineering.COPPER_RESISTIVE_LOAD.get().defaultBlockState()
                        .setValue(CopperResistiveLoadBlock.RESISTANCE, 4)
        );

        helper.runAfterDelay(14, () -> {
            BlockState fuse = helper.getBlockState(fusePos);
            int output = DomainNetwork.sampleCopperVoltage(helper.getLevel(), helper.absolutePos(outputWirePos));
            if (!fuse.getValue(CopperFuseBlock.TRIPPED)) {
                helper.fail("Low-rated copper fuse did not trip under load", fusePos);
                return;
            }
            if (output != 0 || CopperFuseBlock.outputVoltage(helper.getLevel(), helper.absolutePos(fusePos)) != 0) {
                helper.fail("Tripped copper fuse did not cut the protected output", outputWirePos);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void seriesResistorRejectsSideFeed(GameTestHelper helper) {
        BlockPos resistorPos = new BlockPos(2, 1, 2);
        BlockPos sideSourcePos = new BlockPos(2, 1, 1);
        BlockPos outputWirePos = new BlockPos(3, 1, 2);
        BlockPos loadPos = new BlockPos(4, 1, 2);

        helper.setBlock(sideSourcePos, RedstoneEngineering.COPPER_VOLTAGE_SOURCE.get().defaultBlockState());
        helper.setBlock(
                resistorPos,
                RedstoneEngineering.COPPER_SERIES_RESISTOR.get().defaultBlockState()
                        .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
        );
        helper.setBlock(outputWirePos, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState());
        helper.setBlock(loadPos, RedstoneEngineering.COPPER_RESISTIVE_LOAD.get().defaultBlockState());

        helper.runAfterDelay(12, () -> {
            int output = DomainNetwork.sampleCopperVoltage(helper.getLevel(), helper.absolutePos(outputWirePos));
            if (output != 0 || CopperSeriesResistorBlock.outputVoltage(helper.getLevel(), helper.absolutePos(resistorPos)) != 0) {
                helper.fail("Axial copper processor incorrectly accepted a SIDE feed", resistorPos);
                return;
            }
            helper.succeed();
        });
    }
}
