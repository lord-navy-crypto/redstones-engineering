package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.CopperCapacitorBlock;
import dev.redstoneengineering.block.CopperFuseBlock;
import dev.redstoneengineering.block.CopperResistiveLoadBlock;
import dev.redstoneengineering.block.CopperSeriesResistorBlock;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.CircuitPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Behavioral contracts for the seven-block Pioneer Wave 15 Copper rollout. */
public final class RsePioneerWave15GameTests {
    private static final String TEMPLATE = "empty5x4x5";
    private static final BlockPos MARKER = new BlockPos(2, 1, 2);

    private RsePioneerWave15GameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void copperDcModelSweepRemainsBounded(GameTestHelper helper) {
        for (int voltage = 0; voltage <= 15; voltage++) {
            for (int resistance = 1; resistance <= 15; resistance++) {
                double current = CircuitPhysics.current(voltage, resistance);
                double power = CircuitPhysics.power(voltage, resistance);
                if (current < 0.0 || power < 0.0 || Math.abs(power - voltage * current) > 1.0e-9) {
                    helper.fail("Copper load I=V/R and P=VI must remain finite, non-negative and self-consistent", MARKER);
                    return;
                }
                for (int load = 1; load <= 15; load++) {
                    int output = CircuitPhysics.divider(voltage, resistance, load);
                    if (output < 0 || output > voltage || output > 15) {
                        helper.fail("Copper divider output must remain bounded by input and the 0..15 Copper scale", MARKER);
                        return;
                    }
                }
            }
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void capacitorProfilesExposeImplementedTau(GameTestHelper helper) {
        int[] expected = {2, 4, 8, 16};
        for (int index = 0; index < expected.length; index++) {
            if (CopperCapacitorBlock.tauTicks(index) != expected[index]) {
                helper.fail("Copper capacitor C-index must expose the implemented 2/4/8/16 tick tau profile", MARKER);
                return;
            }
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void seriesResistorRetainsSolverEvidenceFromAuthoritativeTick(GameTestHelper helper) {
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
        helper.setBlock(load, RedstoneEngineering.COPPER_RESISTIVE_LOAD.get().defaultBlockState()
                .setValue(CopperResistiveLoadBlock.RESISTANCE, 4));

        helper.runAfterDelay(14, () -> {
            BlockPos absolute = helper.absolutePos(resistor);
            if (CopperSeriesResistorBlock.outputQuality(helper.getLevel(), absolute) != PortQuality.VALID
                    || CopperSeriesResistorBlock.loadResistanceMilli(helper.getLevel(), absolute) <= 0
                    || CopperSeriesResistorBlock.currentMilli(helper.getLevel(), absolute) <= 0) {
                helper.fail("Series-resistor Pioneer evidence must come from the authoritative server evaluation", resistor);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void fuseRetainsProtectionEvidenceFromAuthoritativeTick(GameTestHelper helper) {
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
                .setValue(CopperResistiveLoadBlock.RESISTANCE, 4));

        helper.runAfterDelay(14, () -> {
            BlockPos absolute = helper.absolutePos(fuse);
            if (helper.getBlockState(fuse).getValue(CopperFuseBlock.TRIPPED)
                    || CopperFuseBlock.outputQuality(helper.getLevel(), absolute, helper.getBlockState(fuse)) != PortQuality.VALID
                    || CopperFuseBlock.loadResistanceMilli(helper.getLevel(), absolute) <= 0
                    || CopperFuseBlock.currentMilli(helper.getLevel(), absolute) <= 0) {
                helper.fail("Fuse Pioneer evidence must retain the last complete protection evaluation without a UI rescan", fuse);
                return;
            }
            helper.succeed();
        });
    }
}
