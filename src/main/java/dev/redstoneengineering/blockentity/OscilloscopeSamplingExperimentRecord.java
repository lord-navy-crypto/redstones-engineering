package dev.redstoneengineering.blockentity;

import net.minecraft.nbt.CompoundTag;

import java.util.Optional;

/**
 * Immutable explicitly-captured oscilloscope sampling evidence.
 *
 * <p>This record freezes one server-owned capture state for later comparison. It never advances
 * sampling, changes trigger state, or infers unobserved source bandwidth.</p>
 */
public record OscilloscopeSamplingExperimentRecord(
        long gameTick,
        int channel,
        int samplePeriodTicks,
        int sampleRateMilliHz,
        int nyquistMilliHz,
        int sampleCount,
        int coveragePercent,
        int periodSamples,
        int periodTicks,
        int observedFrequencyMilliHz,
        int aliasRiskCode,
        int meanStep100
) {
    public OscilloscopeSamplingExperimentRecord {
        if (gameTick < 0) throw new IllegalArgumentException("gameTick must be >= 0");
        if (channel < 0 || channel > 1) throw new IllegalArgumentException("channel must be 0 or 1");
        if (samplePeriodTicks < 1) throw new IllegalArgumentException("samplePeriodTicks must be >= 1");
        if (sampleRateMilliHz < 1) throw new IllegalArgumentException("sampleRateMilliHz must be >= 1");
        if (nyquistMilliHz < 1) throw new IllegalArgumentException("nyquistMilliHz must be >= 1");
        if (sampleCount < 0 || sampleCount > 32) throw new IllegalArgumentException("sampleCount must be 0..32");
        if (coveragePercent < 0 || coveragePercent > 100) throw new IllegalArgumentException("coveragePercent must be 0..100");
        if (aliasRiskCode < 0 || aliasRiskCode > 3) throw new IllegalArgumentException("aliasRiskCode must be 0..3");
    }

    public static OscilloscopeSamplingExperimentRecord capture(
            OscilloscopeBlockEntity scope,
            int channel,
            long gameTick
    ) {
        return new OscilloscopeSamplingExperimentRecord(
                gameTick,
                channel,
                scope.samplePeriodTicks(),
                scope.sampleRateMilliHz(),
                scope.nyquistMilliHz(),
                scope.sampleCount(),
                scope.coveragePercent(channel),
                scope.estimatedPeriodSamples(channel),
                scope.estimatedPeriodTicks(channel),
                scope.estimatedFrequencyMilliHz(channel),
                scope.aliasRiskCode(channel),
                scope.meanStep100(channel)
        );
    }

    public boolean comparisonReady() {
        return coveragePercent >= 70 && periodSamples > 0 && aliasRiskCode > 0;
    }

    public void save(CompoundTag tag, String prefix) {
        tag.putBoolean(prefix + "Present", true);
        tag.putLong(prefix + "Tick", gameTick);
        tag.putInt(prefix + "Channel", channel);
        tag.putInt(prefix + "SamplePeriodTicks", samplePeriodTicks);
        tag.putInt(prefix + "SampleRateMilliHz", sampleRateMilliHz);
        tag.putInt(prefix + "NyquistMilliHz", nyquistMilliHz);
        tag.putInt(prefix + "SampleCount", sampleCount);
        tag.putInt(prefix + "CoveragePercent", coveragePercent);
        tag.putInt(prefix + "PeriodSamples", periodSamples);
        tag.putInt(prefix + "PeriodTicks", periodTicks);
        tag.putInt(prefix + "ObservedFrequencyMilliHz", observedFrequencyMilliHz);
        tag.putInt(prefix + "AliasRiskCode", aliasRiskCode);
        tag.putInt(prefix + "MeanStep100", meanStep100);
    }

    public static Optional<OscilloscopeSamplingExperimentRecord> load(CompoundTag tag, String prefix) {
        if (!tag.getBoolean(prefix + "Present")) return Optional.empty();
        return Optional.of(new OscilloscopeSamplingExperimentRecord(
                Math.max(0L, tag.getLong(prefix + "Tick")),
                Math.max(0, Math.min(1, tag.getInt(prefix + "Channel"))),
                Math.max(1, tag.getInt(prefix + "SamplePeriodTicks")),
                Math.max(1, tag.getInt(prefix + "SampleRateMilliHz")),
                Math.max(1, tag.getInt(prefix + "NyquistMilliHz")),
                Math.max(0, Math.min(32, tag.getInt(prefix + "SampleCount"))),
                Math.max(0, Math.min(100, tag.getInt(prefix + "CoveragePercent"))),
                tag.getInt(prefix + "PeriodSamples"),
                tag.getInt(prefix + "PeriodTicks"),
                tag.getInt(prefix + "ObservedFrequencyMilliHz"),
                Math.max(0, Math.min(3, tag.getInt(prefix + "AliasRiskCode"))),
                tag.getInt(prefix + "MeanStep100")
        ));
    }
}
