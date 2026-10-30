package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.AmethystFrequencyFilterBlock;
import dev.redstoneengineering.block.AmethystFrequencySupport;
import dev.redstoneengineering.block.AmethystResonanceDustBlock;
import dev.redstoneengineering.block.AmethystResonatorBlock;
import dev.redstoneengineering.block.AmethystTunedResonatorBlock;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Runtime witnesses; CI source checks are not substitutes for Minecraft execution. */
public final class RseAmethystFrequencyIntegrityGameTests {
    private static final String TEMPLATE = "empty5x4x5";
    private RseAmethystFrequencyIntegrityGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void filterCannotPresentRetainedAmplitudeFromConflictedInput(GameTestHelper helper) {
        BlockPos input = new BlockPos(1, 1, 2);
        BlockPos filter = new BlockPos(2, 1, 2);
        BlockPos output = new BlockPos(3, 1, 2);
        helper.setBlock(input, RedstoneEngineering.AMETHYST_RESONANCE_DUST.get().defaultBlockState());
        helper.setBlock(output, RedstoneEngineering.AMETHYST_RESONANCE_DUST.get().defaultBlockState());
        helper.setBlock(filter, RedstoneEngineering.AMETHYST_FREQUENCY_FILTER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                .setValue(AmethystFrequencyFilterBlock.TARGET, 6));
        AmethystResonanceDustBlock.setResonance(helper.getLevel(), helper.absolutePos(input), 0, 0,
                AmethystResonanceDustBlock.ResonanceStatus.FREQUENCY_CONFLICT);
        AmethystResonanceDustBlock.setResonance(helper.getLevel(), helper.absolutePos(output), 6, 13,
                AmethystResonanceDustBlock.ResonanceStatus.ACTIVE);
        var snapshot = RedstoneEngineering.AMETHYST_FREQUENCY_FILTER.get().engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(filter), helper.getBlockState(filter), Direction.EAST).orElseThrow();
        if (snapshot.quality() != PortQuality.TOPOLOGY_ERROR || snapshot.value() != 0.0) {
            helper.fail("Filter presented an obsolete output amplitude under frequency conflict", filter);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void tunedResonatorCannotPresentRetainedAmplitudeFromStaleInput(GameTestHelper helper) {
        BlockPos input = new BlockPos(1, 1, 2);
        BlockPos tuned = new BlockPos(2, 1, 2);
        BlockPos output = new BlockPos(3, 1, 2);
        helper.setBlock(input, RedstoneEngineering.AMETHYST_RESONANCE_DUST.get().defaultBlockState());
        helper.setBlock(output, RedstoneEngineering.AMETHYST_RESONANCE_DUST.get().defaultBlockState());
        helper.setBlock(tuned, RedstoneEngineering.AMETHYST_TUNED_RESONATOR.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                .setValue(AmethystTunedResonatorBlock.NATURAL, 6));
        AmethystResonanceDustBlock.setResonance(helper.getLevel(), helper.absolutePos(input), 0, 0,
                AmethystResonanceDustBlock.ResonanceStatus.STALE);
        AmethystResonanceDustBlock.setResonance(helper.getLevel(), helper.absolutePos(output), 6, 14,
                AmethystResonanceDustBlock.ResonanceStatus.ACTIVE);
        var snapshot = RedstoneEngineering.AMETHYST_TUNED_RESONATOR.get().engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(tuned), helper.getBlockState(tuned), Direction.EAST).orElseThrow();
        if (snapshot.quality() != PortQuality.STALE || snapshot.value() != 0.0) {
            helper.fail("Tuned resonator presented an obsolete output amplitude under STALE input", tuned);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void sourcePortDistinguishesConfiguredAmplitudeFromAnActualPulse(GameTestHelper helper) {
        BlockPos local = new BlockPos(2, 1, 2);
        var source = RedstoneEngineering.AMETHYST_RESONATOR.get();
        helper.setBlock(local, source.defaultBlockState()
                .setValue(AmethystResonatorBlock.AMPLITUDE, 12));
        BlockPos world = helper.absolutePos(local);
        var idle = source.engineeringSnapshot(helper.getLevel(), world,
                helper.getBlockState(local), Direction.EAST).orElseThrow();
        RuntimeIntStore.get(helper.getLevel(), "amethyst_resonator", world, 1)[0] = 1;
        var pulsing = source.engineeringSnapshot(helper.getLevel(), world,
                helper.getBlockState(local), Direction.EAST).orElseThrow();
        if (idle.quality() != PortQuality.NO_SIGNAL || idle.value() != 0.0
                || pulsing.quality() != PortQuality.VALID || pulsing.value() != 12.0
                || AmethystFrequencySupport.readableAmplitude(PortQuality.TOPOLOGY_ERROR, 12) != 0) {
            helper.fail("Configured resonator amplitude was misreported as a live carrier", local);
            return;
        }
        helper.succeed();
    }
}
