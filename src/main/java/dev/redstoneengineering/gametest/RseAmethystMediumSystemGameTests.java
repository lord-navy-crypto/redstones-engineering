package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.AmethystFrequencyFilterBlock;
import dev.redstoneengineering.block.AmethystResonanceDustBlock;
import dev.redstoneengineering.block.AmethystResonatorBlock;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** System-level Amethyst fault propagation: real frequency conflict, processor clearing, and recovery. */
public final class RseAmethystMediumSystemGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseAmethystMediumSystemGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 90)
    public static void upstreamFrequencyConflictRemainsVisibleAcrossFilterAndRecovers(GameTestHelper helper) {
        BlockPos sourceNorth = new BlockPos(2, 1, 1);
        BlockPos sourceSouth = new BlockPos(2, 1, 3);
        BlockPos input = new BlockPos(2, 1, 2);
        BlockPos filter = new BlockPos(3, 1, 2);
        BlockPos output = new BlockPos(4, 1, 2);

        helper.setBlock(sourceNorth, RedstoneEngineering.AMETHYST_RESONATOR.get().defaultBlockState()
                .setValue(AmethystResonatorBlock.FREQUENCY, 6)
                .setValue(AmethystResonatorBlock.AMPLITUDE, 12));
        helper.setBlock(sourceSouth, RedstoneEngineering.AMETHYST_RESONATOR.get().defaultBlockState()
                .setValue(AmethystResonatorBlock.FREQUENCY, 8)
                .setValue(AmethystResonatorBlock.AMPLITUDE, 12));
        helper.setBlock(input, RedstoneEngineering.AMETHYST_RESONANCE_DUST.get().defaultBlockState()
                .setValue(AmethystResonanceDustBlock.NORTH, true)
                .setValue(AmethystResonanceDustBlock.SOUTH, true)
                .setValue(AmethystResonanceDustBlock.EAST, true));
        helper.setBlock(filter, RedstoneEngineering.AMETHYST_FREQUENCY_FILTER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                .setValue(AmethystFrequencyFilterBlock.TARGET, 6));
        helper.setBlock(output, RedstoneEngineering.AMETHYST_RESONANCE_DUST.get().defaultBlockState()
                .setValue(AmethystResonanceDustBlock.WEST, true));

        BlockPos northWorld = helper.absolutePos(sourceNorth);
        BlockPos southWorld = helper.absolutePos(sourceSouth);
        BlockPos inputWorld = helper.absolutePos(input);
        BlockPos filterWorld = helper.absolutePos(filter);
        BlockPos outputWorld = helper.absolutePos(output);
        RuntimeIntStore.get(helper.getLevel(), "amethyst_resonator", northWorld, 1)[0] = 1;
        RuntimeIntStore.get(helper.getLevel(), "amethyst_resonator", southWorld, 1)[0] = 1;
        DomainNetwork.recomputeAmethyst(helper.getLevel(), inputWorld);

        helper.runAfterDelay(6, () -> {
            AmethystFrequencyFilterBlock.FilterEvidence faultEvidence = AmethystFrequencyFilterBlock.evidence(
                    helper.getLevel(), filterWorld, helper.getBlockState(filter));
            PortQuality outputQuality = RedstoneEngineering.AMETHYST_FREQUENCY_FILTER.get()
                    .engineeringSnapshot(helper.getLevel(), filterWorld, helper.getBlockState(filter), Direction.EAST)
                    .orElseThrow().quality();

            if (AmethystResonanceDustBlock.status(helper.getLevel(), inputWorld)
                    != AmethystResonanceDustBlock.ResonanceStatus.FREQUENCY_CONFLICT
                    || faultEvidence.inputQuality() != PortQuality.TOPOLOGY_ERROR
                    || faultEvidence.matched()
                    || faultEvidence.expectedOutputAmplitude() != 0
                    || outputQuality != PortQuality.TOPOLOGY_ERROR
                    || AmethystResonanceDustBlock.status(helper.getLevel(), outputWorld)
                    != AmethystResonanceDustBlock.ResonanceStatus.IDLE
                    || AmethystResonanceDustBlock.amplitude(helper.getLevel(), outputWorld) != 0) {
                helper.fail("Amethyst filter collapsed upstream FREQUENCY_CONFLICT or leaked a ghost carrier", filter);
                return;
            }

            helper.setBlock(sourceSouth, Blocks.AIR.defaultBlockState());
            DomainNetwork.recomputeAmethyst(helper.getLevel(), inputWorld);
            helper.runAfterDelay(6, () -> {
                AmethystFrequencyFilterBlock.FilterEvidence recovered = AmethystFrequencyFilterBlock.evidence(
                        helper.getLevel(), filterWorld, helper.getBlockState(filter));
                PortQuality recoveredQuality = RedstoneEngineering.AMETHYST_FREQUENCY_FILTER.get()
                        .engineeringSnapshot(helper.getLevel(), filterWorld, helper.getBlockState(filter), Direction.EAST)
                        .orElseThrow().quality();
                DomainNetwork.AmethystSample recoveredOutput = DomainNetwork.sampleAmethyst(helper.getLevel(), outputWorld);

                if (AmethystResonanceDustBlock.status(helper.getLevel(), inputWorld)
                        != AmethystResonanceDustBlock.ResonanceStatus.ACTIVE
                        || AmethystResonanceDustBlock.frequency(helper.getLevel(), inputWorld) != 6
                        || recovered.inputQuality() != PortQuality.VALID
                        || !recovered.matched()
                        || !recoveredOutput.active()
                        || recoveredOutput.frequency() != 6
                        || recoveredOutput.amplitude() <= 0
                        || recoveredQuality != PortQuality.VALID) {
                    helper.fail("Amethyst filter did not recover cleanly after conflicting source removal", filter);
                    return;
                }
                helper.succeed();
            });
        });
    }
}
