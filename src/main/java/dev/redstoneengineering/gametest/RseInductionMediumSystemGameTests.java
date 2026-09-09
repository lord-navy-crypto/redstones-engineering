package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.CopperWireBlock;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.block.InductionCoilBlock;
import dev.redstoneengineering.block.PermanentMagnetBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.CopperObservationSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** System-level induction lifecycle: static valid-zero, flux transient, and valid-zero recovery. */
public final class RseInductionMediumSystemGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseInductionMediumSystemGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 90)
    public static void inductionOutputRemainsAValidZeroCopperSourceBetweenFluxTransients(GameTestHelper helper) {
        BlockPos magnet = new BlockPos(1, 1, 2);
        BlockPos coil = new BlockPos(2, 1, 2);
        BlockPos wire = new BlockPos(3, 1, 2);

        helper.setBlock(magnet, RedstoneEngineering.PERMANENT_MAGNET.get().defaultBlockState()
                .setValue(PermanentMagnetBlock.STRENGTH, 1));
        helper.setBlock(coil, RedstoneEngineering.INDUCTION_COIL.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                .setValue(InductionCoilBlock.TURNS, 4));
        helper.setBlock(wire, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState());

        BlockPos coilWorld = helper.absolutePos(coil);
        BlockPos wireWorld = helper.absolutePos(wire);

        helper.runAfterDelay(5, () -> {
            PortQuality coilQuality = InductionCoilBlock.outputQuality(helper.getLevel(), coilWorld);
            CopperObservationSupport.Observation observed = CopperObservationSupport.observe(
                    helper.getLevel(), coilWorld, wireWorld);
            PortQuality wireQuality = CopperWireBlock.quality(
                    helper.getLevel(), wireWorld, helper.getBlockState(wire));

            if (InductionCoilBlock.outputVoltage(helper.getLevel(), coilWorld) != 0
                    || coilQuality != PortQuality.VALID
                    || observed.voltage() != 0
                    || observed.quality() != PortQuality.VALID
                    || CopperWireBlock.voltage(helper.getLevel(), wireWorld) != 0
                    || CopperWireBlock.driverCount(helper.getLevel(), wireWorld) != 1
                    || wireQuality != PortQuality.VALID) {
                helper.fail("Static complete-field induction output collapsed valid 0 V into NO_SIGNAL", coil);
                return;
            }

            helper.setBlock(magnet, helper.getBlockState(magnet).setValue(PermanentMagnetBlock.STRENGTH, 15));
            helper.getLevel().scheduleTick(coilWorld, RedstoneEngineering.INDUCTION_COIL.get(), 1);
            helper.runAfterDelay(1, () -> {
                int transientVoltage = InductionCoilBlock.outputVoltage(helper.getLevel(), coilWorld);
                if (transientVoltage <= 0
                        || InductionCoilBlock.outputQuality(helper.getLevel(), coilWorld) != PortQuality.VALID
                        || CopperWireBlock.driverCount(helper.getLevel(), wireWorld) != 1
                        || CopperWireBlock.quality(helper.getLevel(), wireWorld, helper.getBlockState(wire)) != PortQuality.VALID
                        || CopperWireBlock.voltage(helper.getLevel(), wireWorld) <= 0) {
                    helper.fail("Flux change did not produce one valid transient Copper source", coil);
                    return;
                }

                helper.runAfterDelay(5, () -> {
                    CopperObservationSupport.Observation settled = CopperObservationSupport.observe(
                            helper.getLevel(), coilWorld, wireWorld);
                    if (InductionCoilBlock.outputVoltage(helper.getLevel(), coilWorld) != 0
                            || InductionCoilBlock.outputQuality(helper.getLevel(), coilWorld) != PortQuality.VALID
                            || settled.voltage() != 0
                            || settled.quality() != PortQuality.VALID
                            || CopperWireBlock.voltage(helper.getLevel(), wireWorld) != 0
                            || CopperWireBlock.driverCount(helper.getLevel(), wireWorld) != 1
                            || CopperWireBlock.quality(helper.getLevel(), wireWorld, helper.getBlockState(wire)) != PortQuality.VALID) {
                        helper.fail("Induction output failed to recover from transient to a valid sourced 0 V state", coil);
                        return;
                    }
                    helper.succeed();
                });
            });
        });
    }
}
