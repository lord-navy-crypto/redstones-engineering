package dev.redstoneengineering.gametest;

import dev.redstoneengineering.EngineeringSystemsModule;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.CalibrationModuleBlock;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.FaultInjectorBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Evidence-role acceptance tests for the calibration module. */
public final class RseCalibrationEvidenceGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseCalibrationEvidenceGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void calibrationHoldsOutputAcrossFaultedObservedEvidence(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 2);
        BlockPos injector = new BlockPos(2, 1, 2);
        BlockPos arm = new BlockPos(2, 1, 3);
        BlockPos module = new BlockPos(3, 1, 2);
        BlockPos reference = new BlockPos(3, 1, 1);

        helper.setBlock(source, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(injector, EngineeringSystemsModule.FAULT_INJECTOR.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(DirectionalSignalBlock.INPUT_FACING, Direction.WEST)
                .setValue(FaultInjectorBlock.MODE, 0));
        helper.setBlock(reference, referenceSource(Direction.SOUTH, 15));
        helper.setBlock(module, RedstoneEngineering.CALIBRATION_MODULE.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(DirectionalSignalBlock.INPUT_FACING, Direction.WEST)
                .setValue(CalibrationModuleBlock.PROFILE, 0));

        helper.runAfterDelay(5, () -> {
            BlockPos world = helper.absolutePos(module);
            BlockState baseline = helper.getBlockState(module);
            int baselineSamples = CalibrationModuleBlock.measurement(helper.getLevel(), world).sampleCount();
            if (baseline.getValue(DirectionalSignalBlock.OUTPUT) != 15
                    || baselineSamples <= 0
                    || CalibrationModuleBlock.observedQuality(helper.getLevel(), world, baseline) != PortQuality.VALID
                    || CalibrationModuleBlock.referenceQuality(helper.getLevel(), world, baseline) != PortQuality.VALID
                    || CalibrationModuleBlock.outputQuality(helper.getLevel(), world, baseline) != PortQuality.VALID) {
                helper.fail("Calibration module did not establish a valid traceable baseline", module);
                return;
            }

            helper.setBlock(arm, Blocks.REDSTONE_BLOCK.defaultBlockState());
            helper.runAfterDelay(5, () -> {
                BlockState faulted = helper.getBlockState(module);
                int faultSamples = CalibrationModuleBlock.measurement(helper.getLevel(), world).sampleCount();
                if (faulted.getValue(DirectionalSignalBlock.OUTPUT) != 15
                        || CalibrationModuleBlock.observedQuality(helper.getLevel(), world, faulted) != PortQuality.FAULT
                        || CalibrationModuleBlock.outputQuality(helper.getLevel(), world, faulted) != PortQuality.FAULT
                        || faultSamples != baselineSamples) {
                    helper.fail("FAULT OBSERVED evidence became numerical zero or contaminated traceable calibration history", module);
                    return;
                }

                helper.setBlock(arm, Blocks.AIR.defaultBlockState());
                helper.runAfterDelay(4, () -> {
                    BlockState recovered = helper.getBlockState(module);
                    if (recovered.getValue(DirectionalSignalBlock.OUTPUT) != 15
                            || CalibrationModuleBlock.observedQuality(helper.getLevel(), world, recovered) != PortQuality.VALID
                            || CalibrationModuleBlock.outputQuality(helper.getLevel(), world, recovered) != PortQuality.VALID) {
                        helper.fail("Calibration output evidence did not recover after OBSERVED source recovery", module);
                        return;
                    }
                    helper.succeed();
                });
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void missingReferenceDegradesEvidenceWithoutBecomingControlInput(GameTestHelper helper) {
        BlockPos observed = new BlockPos(1, 1, 2);
        BlockPos module = new BlockPos(2, 1, 2);
        BlockPos reference = new BlockPos(2, 1, 1);

        helper.setBlock(observed, referenceSource(Direction.EAST, 7));
        helper.setBlock(reference, referenceSource(Direction.SOUTH, 7));
        helper.setBlock(module, RedstoneEngineering.CALIBRATION_MODULE.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(DirectionalSignalBlock.INPUT_FACING, Direction.WEST)
                .setValue(CalibrationModuleBlock.PROFILE, 0));

        helper.runAfterDelay(5, () -> {
            BlockPos world = helper.absolutePos(module);
            int baselineSamples = CalibrationModuleBlock.measurement(helper.getLevel(), world).sampleCount();
            if (baselineSamples <= 0 || helper.getBlockState(module).getValue(DirectionalSignalBlock.OUTPUT) != 7) {
                helper.fail("Calibration module did not establish baseline before REFERENCE-loss test", module);
                return;
            }

            helper.setBlock(reference, Blocks.AIR.defaultBlockState());
            helper.setBlock(observed, referenceSource(Direction.EAST, 12));
            helper.getLevel().scheduleTick(world, RedstoneEngineering.CALIBRATION_MODULE.get(), 1);

            helper.runAfterDelay(4, () -> {
                BlockState degraded = helper.getBlockState(module);
                int degradedSamples = CalibrationModuleBlock.measurement(helper.getLevel(), world).sampleCount();
                if (degraded.getValue(DirectionalSignalBlock.OUTPUT) != 12
                        || CalibrationModuleBlock.observedQuality(helper.getLevel(), world, degraded) != PortQuality.VALID
                        || CalibrationModuleBlock.referenceQuality(helper.getLevel(), world, degraded) != PortQuality.NO_SIGNAL
                        || CalibrationModuleBlock.outputQuality(helper.getLevel(), world, degraded) != PortQuality.NO_SIGNAL
                        || degradedSamples != baselineSamples) {
                    helper.fail("Missing REFERENCE incorrectly controlled output or fabricated traceable samples", module);
                    return;
                }

                helper.setBlock(reference, referenceSource(Direction.SOUTH, 12));
                helper.getLevel().scheduleTick(world, RedstoneEngineering.CALIBRATION_MODULE.get(), 1);
                helper.runAfterDelay(4, () -> {
                    BlockState recovered = helper.getBlockState(module);
                    if (recovered.getValue(DirectionalSignalBlock.OUTPUT) != 12
                            || CalibrationModuleBlock.referenceQuality(helper.getLevel(), world, recovered) != PortQuality.VALID
                            || CalibrationModuleBlock.outputQuality(helper.getLevel(), world, recovered) != PortQuality.VALID
                            || CalibrationModuleBlock.measurement(helper.getLevel(), world).sampleCount() <= baselineSamples) {
                        helper.fail("Calibration traceability did not resume after REFERENCE recovery", module);
                        return;
                    }
                    helper.succeed();
                });
            });
        });
    }

    private static BlockState referenceSource(Direction facing, int power) {
        return RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, facing)
                .setValue(RedstoneReferenceSourceBlock.POWER, power);
    }
}
