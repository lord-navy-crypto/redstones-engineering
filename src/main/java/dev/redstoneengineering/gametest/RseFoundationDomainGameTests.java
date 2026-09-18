package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalDomainSourceBlock;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.LapisPrecisionSourceBlock;
import dev.redstoneengineering.block.LapisToRedstoneQuantizerBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.RedstoneToLapisScalerBlock;
import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
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
}
