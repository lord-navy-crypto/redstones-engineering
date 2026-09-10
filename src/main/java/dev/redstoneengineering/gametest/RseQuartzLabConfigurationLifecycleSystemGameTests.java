package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.QuartzLabOscillatorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Lifecycle regression for realized Quartz Lab timing evidence across configuration epochs. */
public final class RseQuartzLabConfigurationLifecycleSystemGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseQuartzLabConfigurationLifecycleSystemGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void configurationChangeInvalidatesOldRealizedTimingEvidenceUntilNextSample(GameTestHelper helper) {
        BlockPos oscillatorPos = new BlockPos(2, 1, 2);
        helper.setBlock(oscillatorPos, RedstoneEngineering.QUARTZ_LAB_OSCILLATOR.get().defaultBlockState()
                .setValue(QuartzLabOscillatorBlock.PERIOD_INDEX, 2)
                .setValue(QuartzLabOscillatorBlock.JITTER, 0));
        BlockPos world = helper.absolutePos(oscillatorPos);

        helper.runAfterDelay(3, () -> {
            BlockState beforeState = helper.getBlockState(oscillatorPos);
            QuartzLabOscillatorBlock.TimingEvidence before = QuartzLabOscillatorBlock.timingEvidence(
                    helper.getLevel(), world, beforeState);
            if (!before.available() || before.nominalPeriod() != 8 || before.lastHalfInterval() != 4) {
                helper.fail("Quartz Lab precondition did not establish deterministic realized timing evidence", oscillatorPos);
                return;
            }

            BlockState reconfigured = beforeState.setValue(QuartzLabOscillatorBlock.PERIOD_INDEX, 3);
            helper.getLevel().setBlock(world, reconfigured, Block.UPDATE_CLIENTS);
            helper.getLevel().scheduleTick(world, RedstoneEngineering.QUARTZ_LAB_OSCILLATOR.get(), 1);

            QuartzLabOscillatorBlock.TimingEvidence afterConfig = QuartzLabOscillatorBlock.timingEvidence(
                    helper.getLevel(), world, reconfigured);
            if (afterConfig.available()) {
                helper.fail("Quartz Lab configuration change relabeled old realized timing evidence as current | old nominal=8 new nominal="
                        + afterConfig.nominalPeriod() + " old half=" + afterConfig.lastHalfInterval(), oscillatorPos);
                return;
            }

            helper.runAfterDelay(2, () -> {
                BlockState recoveredState = helper.getBlockState(oscillatorPos);
                QuartzLabOscillatorBlock.TimingEvidence recovered = QuartzLabOscillatorBlock.timingEvidence(
                        helper.getLevel(), world, recoveredState);
                if (!recovered.available() || recovered.nominalPeriod() != 16 || recovered.lastHalfInterval() != 8) {
                    helper.fail("Quartz Lab did not publish fresh realized timing evidence for the new configuration", oscillatorPos);
                    return;
                }
                helper.succeed();
            });
        });
    }
}
