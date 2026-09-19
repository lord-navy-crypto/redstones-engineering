package dev.redstoneengineering.gametest;

import dev.redstoneengineering.EngineeringSystemsModule;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.AnalogIndicatorBlock;
import dev.redstoneengineering.block.DirectionalDomainSourceBlock;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.FaultInjectorBlock;
import dev.redstoneengineering.block.LapisPrecisionSourceBlock;
import dev.redstoneengineering.block.LapisToRedstoneQuantizerBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.RedstoneToLapisScalerBlock;
import dev.redstoneengineering.block.SignalAnalyzerBlock;
import dev.redstoneengineering.block.SignalTapBlock;
import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Runtime contracts for the foundational Redstone/Lapis/Quartz/Amethyst engineering domains. */
public final class RseFoundationDomainGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseFoundationDomainGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 90)
    public static void conversionBridgesRetainLastCodeWhenEvidenceDisappears(GameTestHelper helper) {
        BlockPos redstoneSource = new BlockPos(1, 1, 1);
        BlockPos scaler = new BlockPos(2, 1, 1);
        BlockPos lapisSource = new BlockPos(1, 1, 3);
        BlockPos quantizer = new BlockPos(2, 1, 3);

        helper.setBlock(redstoneSource, RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.EAST)
                .setValue(RedstoneReferenceSourceBlock.POWER, 12));
        helper.setBlock(scaler, RedstoneEngineering.REDSTONE_TO_LAPIS_SCALER.get().defaultBlockState()
                .setValue(RedstoneToLapisScalerBlock.FACING, Direction.EAST)
                .setValue(RedstoneToLapisScalerBlock.INPUT_FACING, Direction.WEST));

        helper.setBlock(lapisSource, RedstoneEngineering.LAPIS_PRECISION_SOURCE.get().defaultBlockState()
                .setValue(DirectionalDomainSourceBlock.FACING, Direction.EAST)
                .setValue(LapisPrecisionSourceBlock.VALUE, 80));
        helper.setBlock(quantizer, RedstoneEngineering.LAPIS_TO_REDSTONE_QUANTIZER.get().defaultBlockState()
                .setValue(LapisToRedstoneQuantizerBlock.FACING, Direction.EAST)
                .setValue(LapisToRedstoneQuantizerBlock.INPUT_FACING, Direction.WEST));

        helper.runAfterDelay(6, () -> {
            BlockPos scalerWorld = helper.absolutePos(scaler);
            BlockPos quantizerWorld = helper.absolutePos(quantizer);
            int scaledBefore = RedstoneToLapisScalerBlock.outputValue(helper.getLevel(), scalerWorld);
            int quantizedBefore = helper.getBlockState(quantizer).getValue(LapisToRedstoneQuantizerBlock.POWER);

            if (scaledBefore <= 0
                    || RedstoneToLapisScalerBlock.outputQuality(helper.getLevel(), scalerWorld) != PortQuality.VALID
                    || quantizedBefore <= 0
                    || LapisToRedstoneQuantizerBlock.outputQuality(helper.getLevel(), quantizerWorld) != PortQuality.VALID) {
                helper.fail("Conversion bridges did not establish trustworthy non-zero baselines", scaler);
                return;
            }

            helper.setBlock(redstoneSource, Blocks.AIR.defaultBlockState());
            helper.setBlock(lapisSource, Blocks.AIR.defaultBlockState());

            helper.runAfterDelay(6, () -> {
                int scaledAfter = RedstoneToLapisScalerBlock.outputValue(helper.getLevel(), scalerWorld);
                int quantizedAfter = helper.getBlockState(quantizer).getValue(LapisToRedstoneQuantizerBlock.POWER);
                PortQuality scalerQuality = RedstoneToLapisScalerBlock.outputQuality(helper.getLevel(), scalerWorld);
                PortQuality quantizerQuality = LapisToRedstoneQuantizerBlock.outputQuality(helper.getLevel(), quantizerWorld);

                if (scaledAfter != scaledBefore
                        || quantizedAfter != quantizedBefore
                        || scalerQuality == PortQuality.VALID
                        || quantizerQuality == PortQuality.VALID) {
                    helper.fail("Missing upstream evidence was converted into a new numerical code", scaler);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void signalTapHoldsFaultedEvidenceButDropsOnRealSourceLoss(GameTestHelper helper) {
        BlockPos source = new BlockPos(0, 1, 2);
        BlockPos injector = new BlockPos(1, 1, 2);
        BlockPos arm = new BlockPos(1, 1, 3);
        BlockPos tap = new BlockPos(2, 1, 2);

        helper.setBlock(source, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(injector, EngineeringSystemsModule.FAULT_INJECTOR.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(DirectionalSignalBlock.INPUT_FACING, Direction.WEST)
                .setValue(FaultInjectorBlock.MODE, 0));
        helper.setBlock(tap, RedstoneEngineering.SIGNAL_TAP.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(DirectionalSignalBlock.INPUT_FACING, Direction.WEST));

        helper.runAfterDelay(5, () -> {
            BlockPos tapWorld = helper.absolutePos(tap);
            if (helper.getBlockState(tap).getValue(DirectionalSignalBlock.OUTPUT) != 15
                    || SignalTapBlock.inputQuality(helper.getLevel(), tapWorld, helper.getBlockState(tap)) != PortQuality.VALID
                    || SignalTapBlock.evidenceHoldActive(helper.getLevel(), tapWorld)) {
                helper.fail("Signal Tap did not establish a valid copied baseline", tap);
                return;
            }

            helper.setBlock(arm, Blocks.REDSTONE_BLOCK.defaultBlockState());
            helper.runAfterDelay(4, () -> {
                BlockState faulted = helper.getBlockState(tap);
                if (faulted.getValue(DirectionalSignalBlock.OUTPUT) != 15
                        || SignalTapBlock.inputQuality(helper.getLevel(), tapWorld, faulted) != PortQuality.FAULT
                        || !SignalTapBlock.evidenceHoldActive(helper.getLevel(), tapWorld)
                        || SignalTapBlock.badEvidenceEpisodes(helper.getLevel(), tapWorld) != 1) {
                    helper.fail("Faulted tap evidence was converted into a new numerical zero", tap);
                    return;
                }

                helper.runAfterDelay(4, () -> {
                    if (SignalTapBlock.badEvidenceEpisodes(helper.getLevel(), tapWorld) != 1) {
                        helper.fail("Continuous bad tap evidence inflated the episode counter", tap);
                        return;
                    }

                    helper.setBlock(arm, Blocks.AIR.defaultBlockState());
                    helper.runAfterDelay(4, () -> {
                        BlockState recovered = helper.getBlockState(tap);
                        if (recovered.getValue(DirectionalSignalBlock.OUTPUT) != 15
                                || SignalTapBlock.evidenceHoldActive(helper.getLevel(), tapWorld)
                                || SignalTapBlock.inputQuality(helper.getLevel(), tapWorld, recovered) != PortQuality.VALID) {
                            helper.fail("Signal Tap did not recover from degraded evidence", tap);
                            return;
                        }

                        helper.setBlock(source, Blocks.AIR.defaultBlockState());
                        helper.runAfterDelay(4, () -> {
                            BlockState noSource = helper.getBlockState(tap);
                            if (noSource.getValue(DirectionalSignalBlock.OUTPUT) != 0
                                    || SignalTapBlock.evidenceHoldActive(helper.getLevel(), tapWorld)
                                    || SignalTapBlock.inputQuality(helper.getLevel(), tapWorld, noSource) != PortQuality.NO_SIGNAL) {
                                helper.fail("Real source loss did not de-energize the Signal Tap", tap);
                                return;
                            }
                            helper.succeed();
                        });
                    });
                });
            });
        });
    }


    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void analogIndicatorRetainsFaultedDisplayAndClearsOnNoSource(GameTestHelper helper) {
        BlockPos source = new BlockPos(0, 1, 2);
        BlockPos injector = new BlockPos(1, 1, 2);
        BlockPos arm = new BlockPos(1, 1, 3);
        BlockPos indicator = new BlockPos(2, 1, 2);

        helper.setBlock(source, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(injector, EngineeringSystemsModule.FAULT_INJECTOR.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(DirectionalSignalBlock.INPUT_FACING, Direction.WEST)
                .setValue(FaultInjectorBlock.MODE, 0));
        helper.setBlock(indicator, RedstoneEngineering.ANALOG_INDICATOR.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.EAST));

        helper.runAfterDelay(5, () -> {
            BlockPos world = helper.absolutePos(indicator);
            BlockState baseline = helper.getBlockState(indicator);
            if (baseline.getValue(AnalogIndicatorBlock.LEVEL) != 15
                    || RedstoneEngineering.ANALOG_INDICATOR.get()
                    .inputObservation(helper.getLevel(), world, baseline).quality() != PortQuality.VALID) {
                helper.fail("Analog Indicator did not establish a valid displayed baseline", indicator);
                return;
            }

            helper.setBlock(arm, Blocks.REDSTONE_BLOCK.defaultBlockState());
            helper.runAfterDelay(4, () -> {
                BlockState faulted = helper.getBlockState(indicator);
                if (faulted.getValue(AnalogIndicatorBlock.LEVEL) != 15
                        || RedstoneEngineering.ANALOG_INDICATOR.get()
                        .inputObservation(helper.getLevel(), world, faulted).quality() != PortQuality.FAULT) {
                    helper.fail("Faulted indicator evidence overwrote the last trustworthy display", indicator);
                    return;
                }

                helper.setBlock(arm, Blocks.AIR.defaultBlockState());
                helper.runAfterDelay(4, () -> {
                    helper.setBlock(source, Blocks.AIR.defaultBlockState());
                    helper.runAfterDelay(4, () -> {
                        BlockState noSource = helper.getBlockState(indicator);
                        if (noSource.getValue(AnalogIndicatorBlock.LEVEL) != 0
                                || RedstoneEngineering.ANALOG_INDICATOR.get()
                                .inputObservation(helper.getLevel(), world, noSource).quality() != PortQuality.NO_SIGNAL) {
                            helper.fail("Real source loss did not clear the Analog Indicator", indicator);
                            return;
                        }
                        helper.succeed();
                    });
                });
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 110)
    public static void inlineAnalyzerRetainsFaultedOutputAndClearsOnNoSource(GameTestHelper helper) {
        BlockPos source = new BlockPos(0, 1, 2);
        BlockPos injector = new BlockPos(1, 1, 2);
        BlockPos arm = new BlockPos(1, 1, 3);
        BlockPos analyzer = new BlockPos(2, 1, 2);

        helper.setBlock(source, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(injector, EngineeringSystemsModule.FAULT_INJECTOR.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(DirectionalSignalBlock.INPUT_FACING, Direction.WEST)
                .setValue(FaultInjectorBlock.MODE, 0));
        helper.setBlock(analyzer, RedstoneEngineering.SIGNAL_ANALYZER.get().defaultBlockState()
                .setValue(SignalAnalyzerBlock.FACING, Direction.WEST)
                .setValue(SignalAnalyzerBlock.MODE, SignalAnalyzerBlock.INLINE));

        helper.runAfterDelay(6, () -> {
            BlockPos world = helper.absolutePos(analyzer);
            BlockState baseline = helper.getBlockState(analyzer);
            if (baseline.getValue(SignalAnalyzerBlock.OUTPUT) != 15
                    || SignalAnalyzerBlock.measurementQuality(helper.getLevel(), world, baseline) != PortQuality.VALID) {
                helper.fail("Inline analyzer did not establish a valid pass-through baseline", analyzer);
                return;
            }

            helper.setBlock(arm, Blocks.REDSTONE_BLOCK.defaultBlockState());
            helper.runAfterDelay(5, () -> {
                BlockState faulted = helper.getBlockState(analyzer);
                if (faulted.getValue(SignalAnalyzerBlock.OUTPUT) != 15
                        || SignalAnalyzerBlock.measurementQuality(helper.getLevel(), world, faulted) != PortQuality.FAULT) {
                    helper.fail("Faulted analyzer evidence overwrote the last trustworthy inline output", analyzer);
                    return;
                }

                helper.setBlock(arm, Blocks.AIR.defaultBlockState());
                helper.runAfterDelay(5, () -> {
                    helper.setBlock(source, Blocks.AIR.defaultBlockState());
                    helper.runAfterDelay(5, () -> {
                        BlockState noSource = helper.getBlockState(analyzer);
                        if (noSource.getValue(SignalAnalyzerBlock.OUTPUT) != 0
                                || SignalAnalyzerBlock.measurementQuality(helper.getLevel(), world, noSource) != PortQuality.NO_SIGNAL) {
                            helper.fail("Real source loss did not de-energize the inline analyzer", analyzer);
                            return;
                        }
                        helper.succeed();
                    });
                });
            });
        });
    }

}
