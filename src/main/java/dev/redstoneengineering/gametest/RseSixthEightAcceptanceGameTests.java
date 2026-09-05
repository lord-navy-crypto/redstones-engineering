package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.AmethystFrequencyFilterBlock;
import dev.redstoneengineering.block.AmethystResonanceDustBlock;
import dev.redstoneengineering.block.AmethystResonatorBlock;
import dev.redstoneengineering.block.AmethystSpectrumAnalyzerBlock;
import dev.redstoneengineering.block.AmethystTunedResonatorBlock;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.MechanicalExciterBlock;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.physics.VibrationNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Sixth block-by-block acceptance campaign: wave propagation and frequency engineering. */
public final class RseSixthEightAcceptanceGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseSixthEightAcceptanceGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void amethystResonatorExcitesConfiguredFrequency(GameTestHelper helper) {
        BlockPos resonatorPos = new BlockPos(1, 1, 2);
        BlockPos dustPos = new BlockPos(2, 1, 2);
        BlockState resonator = RedstoneEngineering.AMETHYST_RESONATOR.get().defaultBlockState()
                .setValue(AmethystResonatorBlock.FREQUENCY, 7)
                .setValue(AmethystResonatorBlock.AMPLITUDE, 12);
        helper.setBlock(resonatorPos, resonator);
        helper.setBlock(dustPos, RedstoneEngineering.AMETHYST_RESONANCE_DUST.get().defaultBlockState());
        BlockPos resonatorWorld = helper.absolutePos(resonatorPos);
        RuntimeIntStore.get(helper.getLevel(), "amethyst_resonator", resonatorWorld, 1)[0] = 1;
        DomainNetwork.recomputeAmethyst(helper.getLevel(), resonatorWorld);

        helper.runAfterDelay(2, () -> {
            BlockPos dustWorld = helper.absolutePos(dustPos);
            if (!AmethystResonanceDustBlock.active(helper.getLevel(), dustWorld)
                    || AmethystResonanceDustBlock.frequency(helper.getLevel(), dustWorld) != 7
                    || AmethystResonanceDustBlock.amplitude(helper.getLevel(), dustWorld) <= 0) {
                helper.fail("Configured resonator did not excite adjacent dust at f=7", dustPos);
                return;
            }
            EngineeringPortProvider provider = RedstoneEngineering.AMETHYST_RESONATOR.get();
            var east = provider.engineeringPort(helper.getBlockState(resonatorPos), Direction.EAST).orElse(null);
            if (east == null || east.domain() != EngineeringDomain.AMETHYST || east.direction() != PortDirection.OUTPUT) {
                helper.fail("Amethyst resonator did not expose its AMETHYST output contract", resonatorPos);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void amethystResonanceDustPropagatesWithoutGain(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 1, 2);
        BlockPos b = new BlockPos(2, 1, 2);
        BlockPos c = new BlockPos(3, 1, 2);
        helper.setBlock(a, RedstoneEngineering.AMETHYST_RESONANCE_DUST.get().defaultBlockState());
        helper.setBlock(b, RedstoneEngineering.AMETHYST_RESONANCE_DUST.get().defaultBlockState());
        helper.setBlock(c, RedstoneEngineering.AMETHYST_RESONANCE_DUST.get().defaultBlockState());

        // GameTestHelper#setBlock does not model a player's placement context, so
        // make the intended physical A <-> B <-> C trace explicit. The solver is
        // deliberately topology-aware and must not invent an edge from adjacency.
        helper.setBlock(a, helper.getBlockState(a).setValue(AmethystResonanceDustBlock.EAST, true));
        helper.setBlock(b, helper.getBlockState(b)
                .setValue(AmethystResonanceDustBlock.WEST, true)
                .setValue(AmethystResonanceDustBlock.EAST, true));
        helper.setBlock(c, helper.getBlockState(c).setValue(AmethystResonanceDustBlock.WEST, true));

        DomainNetwork.driveAmethyst(helper.getLevel(), helper.absolutePos(a), true, 5, 11);

        helper.runAfterDelay(2, () -> {
            var first = DomainNetwork.sampleAmethyst(helper.getLevel(), helper.absolutePos(a));
            var last = DomainNetwork.sampleAmethyst(helper.getLevel(), helper.absolutePos(c));
            if (!first.active() || !last.active() || last.frequency() != 5
                    || last.amplitude() <= 0 || last.amplitude() > first.amplitude()) {
                helper.fail("Amethyst resonance dust failed propagation/no-gain contract", c);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 70)
    public static void amethystFrequencyFilterPassesTargetAndRejectsOffBand(GameTestHelper helper) {
        BlockPos input = new BlockPos(1, 1, 2);
        BlockPos filter = new BlockPos(2, 1, 2);
        BlockPos output = new BlockPos(3, 1, 2);
        helper.setBlock(input, RedstoneEngineering.AMETHYST_RESONANCE_DUST.get().defaultBlockState());
        helper.setBlock(filter, RedstoneEngineering.AMETHYST_FREQUENCY_FILTER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                .setValue(AmethystFrequencyFilterBlock.TARGET, 6));
        helper.setBlock(output, RedstoneEngineering.AMETHYST_RESONANCE_DUST.get().defaultBlockState());
        DomainNetwork.driveAmethyst(helper.getLevel(), helper.absolutePos(input), true, 6, 10);

        helper.runAfterDelay(5, () -> {
            var passed = DomainNetwork.sampleAmethyst(helper.getLevel(), helper.absolutePos(output));
            if (!passed.active() || passed.frequency() != 6 || passed.amplitude() != 9) {
                helper.fail("Frequency filter did not pass target f=6 with one-amplitude insertion loss", output);
                return;
            }
            DomainNetwork.driveAmethyst(helper.getLevel(), helper.absolutePos(input), true, 7, 10);
            helper.runAfterDelay(5, () -> {
                var rejected = DomainNetwork.sampleAmethyst(helper.getLevel(), helper.absolutePos(output));
                if (rejected.active() || rejected.amplitude() != 0) {
                    helper.fail("Frequency filter failed to reject off-band f=7", output);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 70)
    public static void amethystTunedResonatorAmplifiesOnResonance(GameTestHelper helper) {
        BlockPos input = new BlockPos(1, 1, 2);
        BlockPos resonator = new BlockPos(2, 1, 2);
        BlockPos output = new BlockPos(3, 1, 2);
        helper.setBlock(input, RedstoneEngineering.AMETHYST_RESONANCE_DUST.get().defaultBlockState());
        helper.setBlock(resonator, RedstoneEngineering.AMETHYST_TUNED_RESONATOR.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                .setValue(AmethystTunedResonatorBlock.NATURAL, 8)
                .setValue(AmethystTunedResonatorBlock.Q_INDEX, 3));
        helper.setBlock(output, RedstoneEngineering.AMETHYST_RESONANCE_DUST.get().defaultBlockState());
        DomainNetwork.driveAmethyst(helper.getLevel(), helper.absolutePos(input), true, 8, 6);

        helper.runAfterDelay(5, () -> {
            var sample = DomainNetwork.sampleAmethyst(helper.getLevel(), helper.absolutePos(output));
            if (!sample.active() || sample.frequency() != 8 || sample.amplitude() <= 6) {
                helper.fail("Tuned resonator did not produce resonant gain at f0=8", output);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 70)
    public static void amethystSpectrumAnalyzerObservesWithoutDriving(GameTestHelper helper) {
        BlockPos dust = new BlockPos(1, 1, 2);
        BlockPos analyzer = new BlockPos(3, 1, 2);
        helper.setBlock(dust, RedstoneEngineering.AMETHYST_RESONANCE_DUST.get().defaultBlockState());
        helper.setBlock(analyzer, RedstoneEngineering.AMETHYST_SPECTRUM_ANALYZER.get().defaultBlockState());
        AmethystResonanceDustBlock.setResonance(helper.getLevel(), helper.absolutePos(dust), 9, 11);

        helper.runAfterDelay(12, () -> {
            AmethystSpectrumAnalyzerBlock.Spectrum spectrum = AmethystSpectrumAnalyzerBlock.spectrum(
                    helper.getLevel(), helper.absolutePos(analyzer));
            if (spectrum.dominantFrequency() != 9 || spectrum.energy() < 11 || spectrum.samples() < 1) {
                helper.fail("Spectrum analyzer did not publish a server-side f=9 measurement snapshot", analyzer);
                return;
            }
            if (!AmethystResonanceDustBlock.active(helper.getLevel(), helper.absolutePos(dust))
                    || AmethystResonanceDustBlock.frequency(helper.getLevel(), helper.absolutePos(dust)) != 9
                    || AmethystResonanceDustBlock.amplitude(helper.getLevel(), helper.absolutePos(dust)) != 11) {
                helper.fail("Spectrum analyzer mutated the measured amethyst network", dust);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 70)
    public static void mechanicalExciterUsesDownDriveAndPublishesWave(GameTestHelper helper) {
        BlockPos exciter = new BlockPos(2, 1, 2);
        BlockPos conduit = new BlockPos(3, 1, 2);
        BlockPos sidePower = new BlockPos(1, 1, 2);
        BlockPos downPower = new BlockPos(2, 0, 2);
        helper.setBlock(conduit, RedstoneEngineering.SLIME_VIBRATION_CONDUIT.get().defaultBlockState());
        helper.setBlock(exciter, RedstoneEngineering.MECHANICAL_EXCITER.get().defaultBlockState()
                .setValue(MechanicalExciterBlock.FREQUENCY, 6));
        helper.setBlock(sidePower, Blocks.REDSTONE_BLOCK.defaultBlockState());

        helper.runAfterDelay(2, () -> {
            if (VibrationNetwork.sample(helper.getLevel(), helper.absolutePos(conduit)).valid()) {
                helper.fail("Side redstone incorrectly drove a DOWN-input mechanical exciter", exciter);
                return;
            }
            helper.setBlock(downPower, Blocks.REDSTONE_BLOCK.defaultBlockState());
            helper.runAfterDelay(2, () -> {
                VibrationNetwork.Wave wave = VibrationNetwork.sample(helper.getLevel(), helper.absolutePos(conduit));
                if (!wave.valid() || wave.amplitude() <= 0 || wave.frequency() != 6) {
                    helper.fail("DOWN-powered exciter did not publish f=6 into adjacent conduit", conduit);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void slimeVibrationConduitExpiresTransientPacket(GameTestHelper helper) {
        BlockPos conduit = new BlockPos(2, 1, 2);
        helper.setBlock(conduit, RedstoneEngineering.SLIME_VIBRATION_CONDUIT.get().defaultBlockState());
        BlockPos world = helper.absolutePos(conduit);
        InformationRuntime.write(helper.getLevel(), "mech_wave", world, 15, 4, true, 100);
        helper.getLevel().scheduleTick(world, RedstoneEngineering.SLIME_VIBRATION_CONDUIT.get(), 4);

        helper.runAfterDelay(4, () -> {
            if (!VibrationNetwork.sample(helper.getLevel(), world).valid()) {
                helper.fail("Slime vibration packet disappeared before its bounded decay sequence", conduit);
                return;
            }
            helper.runAfterDelay(32, () -> {
                VibrationNetwork.Wave expired = VibrationNetwork.sample(helper.getLevel(), world);
                if (expired.valid() || expired.amplitude() != 0) {
                    helper.fail("Slime vibration conduit retained a ghost packet after bounded decay", conduit);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 70)
    public static void mechanicalVibrationReceiverAcceptsBackAndRejectsWrongSide(GameTestHelper helper) {
        BlockPos receiver = new BlockPos(2, 1, 2);
        BlockPos westSource = new BlockPos(1, 1, 2);
        BlockPos eastSource = new BlockPos(3, 1, 2);
        helper.setBlock(receiver, RedstoneEngineering.MECHANICAL_VIBRATION_RECEIVER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));

        VibrationNetwork.propagate(helper.getLevel(), helper.absolutePos(westSource), 10, 5, Direction.EAST);
        helper.runAfterDelay(2, () -> {
            if (helper.getBlockState(receiver).getValue(DirectionalSignalBlock.OUTPUT) <= 0) {
                helper.fail("Mechanical receiver rejected a packet arriving on its WEST/BACK input", receiver);
                return;
            }
            InformationRuntime.clear(helper.getLevel(), "mech_wave", helper.absolutePos(receiver));
            helper.setBlock(receiver, helper.getBlockState(receiver).setValue(DirectionalSignalBlock.OUTPUT, 0));
            VibrationNetwork.propagate(helper.getLevel(), helper.absolutePos(eastSource), 10, 5, Direction.WEST);
            helper.runAfterDelay(2, () -> {
                if (helper.getBlockState(receiver).getValue(DirectionalSignalBlock.OUTPUT) != 0
                        || VibrationNetwork.sample(helper.getLevel(), helper.absolutePos(receiver)).valid()) {
                    helper.fail("Mechanical receiver accepted a packet arriving on its FRONT/wrong side", receiver);
                    return;
                }
                helper.succeed();
            });
        });
    }
}
