package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.LapisNoiseSourceBlock;
import dev.redstoneengineering.block.MolecularCloudReceiverBlock;
import dev.redstoneengineering.block.QuartzLabOscillatorBlock;
import dev.redstoneengineering.block.QuartzPhaseDelayBlock;
import dev.redstoneengineering.block.QuartzTimingLineBlock;
import dev.redstoneengineering.block.QuartzTriggeredLapisSamplerBlock;
import dev.redstoneengineering.block.SoulFluxInjectorBlock;
import dev.redstoneengineering.block.SoulFluxMeterBlock;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.EngineeringParameterProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Behavioral contracts for the seven-block Pioneer Wave 16 active-source/timing rollout. */
public final class RsePioneerWave16GameTests {
    private static final String TEMPLATE = "empty5x4x5";
    private static final BlockPos MARKER = new BlockPos(2, 1, 2);

    private RsePioneerWave16GameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void zeroNoiseSourceProducesValidZeroEvidence(GameTestHelper helper) {
        helper.setBlock(MARKER, RedstoneEngineering.LAPIS_NOISE_SOURCE.get().defaultBlockState()
                .setValue(LapisNoiseSourceBlock.BASELINE, 0)
                .setValue(LapisNoiseSourceBlock.NOISE, 0));

        helper.runAfterDelay(6, () -> {
            BlockPos absolute = helper.absolutePos(MARKER);
            var state = helper.getBlockState(MARKER);
            int value = LapisNoiseSourceBlock.currentValue(helper.getLevel(), absolute, state);
            var snapshot = RedstoneEngineering.LAPIS_NOISE_SOURCE.get()
                    .engineeringSnapshot(helper.getLevel(), absolute, state,
                            state.getValue(dev.redstoneengineering.block.DirectionalDomainBlock.FACING))
                    .orElse(null);
            if (!LapisNoiseSourceBlock.sampleInitialized(helper.getLevel(), absolute)
                    || value != 0
                    || snapshot == null
                    || snapshot.quality() != PortQuality.VALID
                    || snapshot.value() != 0.0) {
                helper.fail("Wave-16 Lapis noise source must keep generated numeric zero distinct from missing evidence", MARKER);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void quartzOscillatorRetainsRealizedIntervalEvidence(GameTestHelper helper) {
        helper.setBlock(MARKER, RedstoneEngineering.QUARTZ_LAB_OSCILLATOR.get().defaultBlockState()
                .setValue(QuartzLabOscillatorBlock.PERIOD_INDEX, 2)
                .setValue(QuartzLabOscillatorBlock.JITTER, 0));

        helper.runAfterDelay(8, () -> {
            BlockPos absolute = helper.absolutePos(MARKER);
            var state = helper.getBlockState(MARKER);
            QuartzLabOscillatorBlock.TimingEvidence evidence =
                    QuartzLabOscillatorBlock.timingEvidence(helper.getLevel(), absolute, state);
            if (!evidence.available()
                    || evidence.nominalPeriod() != 8
                    || evidence.lastHalfInterval() != 4
                    || evidence.lastJitterOffset() != 0) {
                helper.fail("Quartz oscillator must retain one real server scheduling interval as Pioneer evidence", MARKER);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void wave16ParameterProfilesRemainBounded(GameTestHelper helper) {
        int[] periods = {2, 4, 8, 16, 32};
        for (int i = 0; i < periods.length; i++) {
            if (QuartzTimingLineBlock.periodTicks(i) != periods[i]) {
                helper.fail("Quartz oscillator period profile drifted from implemented 2/4/8/16/32 tick model", MARKER);
                return;
            }
        }
        if (EngineeringParameterProfile.QUARTZ_PHASE_DELAY_MIN_TICKS != 1
                || EngineeringParameterProfile.QUARTZ_PHASE_DELAY_MAX_TICKS != 16
                || EngineeringParameterProfile.QUARTZ_PHASE_DELAY_DEFAULT_TICKS != 2) {
            helper.fail("Quartz phase-delay Pioneer range/default must match the authoritative parameter profile", MARKER);
            return;
        }
        int[] gains = {6, 9, 12, 16};
        for (int i = 0; i < gains.length; i++) {
            if (MolecularCloudReceiverBlock.gainFor(i) != gains[i]) {
                helper.fail("Molecular sensitivity gain profile drifted from implemented model", MARKER);
                return;
            }
        }
        if (Math.abs(MolecularCloudReceiverBlock.apertureRadiusBlocks() - 8.0) > 1.0e-9) {
            helper.fail("Molecular Pioneer aperture must expose the implemented radius-8 world query", MARKER);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void wave16PortRolesRemainSpecialized(GameTestHelper helper) {
        var samplerState = RedstoneEngineering.QUARTZ_TRIGGERED_LAPIS_SAMPLER.get().defaultBlockState();
        var samplerPorts = RedstoneEngineering.QUARTZ_TRIGGERED_LAPIS_SAMPLER.get().engineeringPorts(samplerState);
        boolean lapisIn = samplerPorts.stream().anyMatch(p ->
                p.domain() == EngineeringDomain.LAPIS && p.direction() == PortDirection.INPUT);
        boolean quartzTrigger = samplerPorts.stream().anyMatch(p ->
                p.domain() == EngineeringDomain.QUARTZ && p.direction() == PortDirection.INPUT);
        boolean lapisOut = samplerPorts.stream().anyMatch(p ->
                p.domain() == EngineeringDomain.LAPIS && p.direction() == PortDirection.OUTPUT);
        if (!lapisIn || !quartzTrigger || !lapisOut || samplerPorts.size() != 3) {
            helper.fail("Quartz-triggered sampler must retain distinct Lapis input / Quartz trigger / Lapis output roles", MARKER);
            return;
        }

        var injectorPorts = RedstoneEngineering.SOUL_FLUX_INJECTOR.get().engineeringPorts(
                RedstoneEngineering.SOUL_FLUX_INJECTOR.get().defaultBlockState());
        long redstoneInputs = injectorPorts.stream().filter(p ->
                p.domain() == EngineeringDomain.REDSTONE && p.direction() == PortDirection.INPUT).count();
        long soulOutputs = injectorPorts.stream().filter(p ->
                p.domain() == EngineeringDomain.SOUL_FLUX && p.direction() == PortDirection.OUTPUT).count();
        if (injectorPorts.size() != 6 || redstoneInputs != 1 || soulOutputs != 5) {
            helper.fail("Soul injector must retain one UP redstone command and five Soul-Flux outputs", MARKER);
            return;
        }

        var meterPorts = RedstoneEngineering.SOUL_FLUX_METER.get().engineeringPorts(
                RedstoneEngineering.SOUL_FLUX_METER.get().defaultBlockState());
        if (meterPorts.size() != 2
                || meterPorts.stream().noneMatch(p -> p.domain() == EngineeringDomain.SOUL_FLUX && p.direction() == PortDirection.INPUT)
                || meterPorts.stream().noneMatch(p -> p.domain() == EngineeringDomain.REDSTONE && p.direction() == PortDirection.OUTPUT)) {
            helper.fail("Soul meter must remain a Soul measurement → redstone readout converter", MARKER);
            return;
        }

        helper.succeed();
    }
}
