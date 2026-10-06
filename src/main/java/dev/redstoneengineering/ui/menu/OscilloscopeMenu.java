package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.OscilloscopeBlock;
import dev.redstoneengineering.blockentity.OscilloscopeBlockEntity;
import dev.redstoneengineering.instrument.InstrumentNetwork;
import dev.redstoneengineering.ui.EngineeringUiRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;

/** Server-authoritative readback and bounded controls for the two-channel oscilloscope. */
public final class OscilloscopeMenu extends EngineeringDeviceMenu {
    public static final int BUTTON_ARM = 0;
    public static final int BUTTON_TRIGGER_MODE = 1;
    public static final int BUTTON_TRIGGER_CHANNEL = 2;
    public static final int BUTTON_TRIGGER_LEVEL = 3;
    public static final int BUTTON_CURSOR_A = 4;
    public static final int BUTTON_CURSOR_B = 5;
    public static final int BUTTON_CLEAR = 6;
    public static final int BUTTON_SAMPLE_PERIOD = 7;
    public static final int BUTTON_EXPERIMENT_BASELINE = 8;
    public static final int BUTTON_EXPERIMENT_CANDIDATE = 9;
    public static final int BUTTON_EXPERIMENT_CLEAR = 10;
    /** Exact Δt selection encoded as BASE + ticks/sample. */
    public static final int BUTTON_SAMPLE_PERIOD_DIRECT_BASE = 3000;
    public static final int BUTTON_SAMPLE_PERIOD_DIRECT_MAX = 3008;

    private final DataSlot sampleCount = trackedInt();
    private final DataSlot triggerMode = trackedInt();
    private final DataSlot triggerChannel = trackedInt();
    private final DataSlot triggerLevel = trackedInt();
    private final DataSlot captureState = trackedInt();
    private final DataSlot cursorA = trackedInt();
    private final DataSlot cursorB = trackedInt();
    private final DataSlot samplePeriodTicks = trackedInt();
    private final DataSlot sampleRateMilliHz = trackedInt();
    private final DataSlot nyquistMilliHz = trackedInt();

    private final DataSlot experimentStatus = trackedInt();
    private final DataSlot experimentChannel = trackedInt();
    private final DataSlot baselinePresent = trackedInt();
    private final DataSlot candidatePresent = trackedInt();
    private final DataSlot baselineSamplePeriodTicks = trackedInt();
    private final DataSlot candidateSamplePeriodTicks = trackedInt();
    private final DataSlot baselineSampleRateMilliHz = trackedInt();
    private final DataSlot candidateSampleRateMilliHz = trackedInt();
    private final DataSlot baselineNyquistMilliHz = trackedInt();
    private final DataSlot candidateNyquistMilliHz = trackedInt();
    private final DataSlot baselineCoverage = trackedInt();
    private final DataSlot candidateCoverage = trackedInt();
    private final DataSlot baselinePeriodSamples = trackedInt();
    private final DataSlot candidatePeriodSamples = trackedInt();
    private final DataSlot baselineFrequencyMilliHz = trackedInt();
    private final DataSlot candidateFrequencyMilliHz = trackedInt();
    private final DataSlot baselineAliasRisk = trackedInt();
    private final DataSlot candidateAliasRisk = trackedInt();
    private final DataSlot baselineMeanStep100 = trackedInt();
    private final DataSlot candidateMeanStep100 = trackedInt();
    private final DataSlot experimentSamplesDelta = trackedInt();
    private final DataSlot experimentFrequencyDeltaMilliHz = trackedInt();

    private final DataSlot[] current = new DataSlot[2];
    private final DataSlot[] coverage = new DataSlot[2];
    private final DataSlot[] minimum = new DataSlot[2];
    private final DataSlot[] maximum = new DataSlot[2];
    private final DataSlot[] peakToPeak = new DataSlot[2];
    private final DataSlot[] average100 = new DataSlot[2];
    private final DataSlot[] meanStep100 = new DataSlot[2];
    private final DataSlot[] periodTicks = new DataSlot[2];
    private final DataSlot[] periodSamples = new DataSlot[2];
    private final DataSlot[] aliasRisk = new DataSlot[2];
    private final DataSlot[] frequencyMilliHz = new DataSlot[2];
    private final DataSlot[][] display = new DataSlot[2][OscilloscopeBlockEntity.DISPLAY_SAMPLES];

    private final DataSlot cableNodes = trackedInt();
    private final DataSlot probeNodes = trackedInt();
    private final DataSlot validChannels = trackedInt();
    private final DataSlot activeChannels = trackedInt();
    private final DataSlot duplicateChannels = trackedInt();
    private final DataSlot bounded = trackedInt();
    private final DataSlot shieldedCableNodes = trackedInt();
    private final DataSlot unshieldedCableNodes = trackedInt();
    private final DataSlot shieldingCoverage = trackedInt();
    private final DataSlot exposedCableNodes = trackedInt();
    private final DataSlot shieldedExposedNodes = trackedInt();
    private final DataSlot unshieldedExposedNodes = trackedInt();
    private final DataSlot interferenceExposure = trackedInt();
    private final DataSlot interferenceConfidence = trackedInt();
    private final DataSlot[] channelProbeCounts = new DataSlot[2];

    public OscilloscopeMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos());
    }

    public OscilloscopeMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(EngineeringUiRegistration.OSCILLOSCOPE.get(), containerId, inventory, pos,
                RedstoneEngineering.OSCILLOSCOPE.get());
        for (int channel = 0; channel < 2; channel++) {
            current[channel] = trackedInt(); coverage[channel] = trackedInt(); minimum[channel] = trackedInt();
            maximum[channel] = trackedInt(); peakToPeak[channel] = trackedInt(); average100[channel] = trackedInt();
            meanStep100[channel] = trackedInt(); periodTicks[channel] = trackedInt();
            periodSamples[channel] = trackedInt(); aliasRisk[channel] = trackedInt(); frequencyMilliHz[channel] = trackedInt();
            channelProbeCounts[channel] = trackedInt();
            for (int slot = 0; slot < OscilloscopeBlockEntity.DISPLAY_SAMPLES; slot++) display[channel][slot] = trackedInt();
        }
        if (!level.isClientSide) refreshAuthoritativeSnapshot();
    }

    @Override
    protected void refreshAuthoritativeSnapshot() {
        if (!(level.getBlockEntity(blockPos) instanceof OscilloscopeBlockEntity scope)) return;
        sampleCount.set(scope.sampleCount()); triggerMode.set(scope.triggerMode()); triggerChannel.set(scope.triggerChannel());
        triggerLevel.set(scope.triggerLevel()); captureState.set(scope.armed() ? 1 : scope.triggered() ? 2 : 0);
        cursorA.set(scope.cursorA()); cursorB.set(scope.cursorB());
        samplePeriodTicks.set(scope.samplePeriodTicks());
        sampleRateMilliHz.set(scope.sampleRateMilliHz());
        nyquistMilliHz.set(scope.nyquistMilliHz());

        experimentStatus.set(scope.samplingExperimentStatus().ordinal());
        experimentSamplesDelta.set(scope.samplingExperimentSamplesPerCycleDelta());
        experimentFrequencyDeltaMilliHz.set(scope.samplingExperimentObservedFrequencyDeltaMilliHz());

        var baseline = scope.experimentBaseline();
        baselinePresent.set(baseline.isPresent() ? 1 : 0);
        if (baseline.isPresent()) {
            var record = baseline.get();
            experimentChannel.set(record.channel());
            baselineSamplePeriodTicks.set(record.samplePeriodTicks());
            baselineSampleRateMilliHz.set(record.sampleRateMilliHz());
            baselineNyquistMilliHz.set(record.nyquistMilliHz());
            baselineCoverage.set(record.coveragePercent());
            baselinePeriodSamples.set(record.periodSamples());
            baselineFrequencyMilliHz.set(record.observedFrequencyMilliHz());
            baselineAliasRisk.set(record.aliasRiskCode());
            baselineMeanStep100.set(record.meanStep100());
        } else {
            experimentChannel.set(scope.triggerChannel());
            baselineSamplePeriodTicks.set(0);
            baselineSampleRateMilliHz.set(0);
            baselineNyquistMilliHz.set(0);
            baselineCoverage.set(0);
            baselinePeriodSamples.set(-1);
            baselineFrequencyMilliHz.set(-1);
            baselineAliasRisk.set(0);
            baselineMeanStep100.set(-1);
        }

        var candidate = scope.experimentCandidate();
        candidatePresent.set(candidate.isPresent() ? 1 : 0);
        if (candidate.isPresent()) {
            var record = candidate.get();
            candidateSamplePeriodTicks.set(record.samplePeriodTicks());
            candidateSampleRateMilliHz.set(record.sampleRateMilliHz());
            candidateNyquistMilliHz.set(record.nyquistMilliHz());
            candidateCoverage.set(record.coveragePercent());
            candidatePeriodSamples.set(record.periodSamples());
            candidateFrequencyMilliHz.set(record.observedFrequencyMilliHz());
            candidateAliasRisk.set(record.aliasRiskCode());
            candidateMeanStep100.set(record.meanStep100());
        } else {
            candidateSamplePeriodTicks.set(0);
            candidateSampleRateMilliHz.set(0);
            candidateNyquistMilliHz.set(0);
            candidateCoverage.set(0);
            candidatePeriodSamples.set(-1);
            candidateFrequencyMilliHz.set(-1);
            candidateAliasRisk.set(0);
            candidateMeanStep100.set(-1);
        }
        for (int channel = 0; channel < 2; channel++) {
            current[channel].set(scope.current(channel)); coverage[channel].set(scope.coveragePercent(channel));
            minimum[channel].set(scope.minimum(channel)); maximum[channel].set(scope.maximum(channel));
            peakToPeak[channel].set(scope.peakToPeak(channel)); average100[channel].set(scope.average100(channel));
            meanStep100[channel].set(scope.meanStep100(channel)); periodTicks[channel].set(scope.estimatedPeriodTicks(channel));
            periodSamples[channel].set(scope.estimatedPeriodSamples(channel)); aliasRisk[channel].set(scope.aliasRiskCode(channel));
            frequencyMilliHz[channel].set(scope.estimatedFrequencyMilliHz(channel));
            for (int slot = 0; slot < OscilloscopeBlockEntity.DISPLAY_SAMPLES; slot++) display[channel][slot].set(scope.displaySample(channel, slot));
        }

        InstrumentNetwork.ProbeSnapshot network = InstrumentNetwork.scan(level, blockPos);
        cableNodes.set(network.cableNodes()); probeNodes.set(network.probeNodes()); validChannels.set(network.validChannels());
        activeChannels.set(network.activeChannels()); duplicateChannels.set(network.duplicateChannels()); bounded.set(network.bounded() ? 1 : 0);
        shieldedCableNodes.set(network.shieldedCableNodes()); unshieldedCableNodes.set(network.unshieldedCableNodes());
        shieldingCoverage.set(network.shieldingCoveragePercent()); exposedCableNodes.set(network.exposedCableNodes());
        shieldedExposedNodes.set(network.shieldedExposedNodes()); unshieldedExposedNodes.set(network.unshieldedExposedNodes());
        interferenceExposure.set(network.interferenceExposurePercent()); interferenceConfidence.set(network.interferenceConfidencePercent());
        for (int channel = 0; channel < 2; channel++) channelProbeCounts[channel].set(network.counts()[channel]);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (level.isClientSide) return true;
        if (!stillValid(player)) return false;
        boolean changed;
        if (id >= BUTTON_SAMPLE_PERIOD_DIRECT_BASE && id <= BUTTON_SAMPLE_PERIOD_DIRECT_MAX
                && level.getBlockEntity(blockPos) instanceof OscilloscopeBlockEntity scope) {
            changed = scope.setSamplePeriodTicks(id - BUTTON_SAMPLE_PERIOD_DIRECT_BASE);
        } else {
            changed = OscilloscopeBlock.applyUiAction(level, blockPos, id);
        }
        if (changed) { refreshAuthoritativeSnapshot(); broadcastChanges(); }
        return changed;
    }

    public int sampleCount() { return sampleCount.get(); }
    public int triggerMode() { return triggerMode.get(); }
    public int triggerChannel() { return triggerChannel.get(); }
    public int triggerLevel() { return triggerLevel.get(); }
    public int captureState() { return captureState.get(); }
    public int cursorA() { return cursorA.get(); }
    public int cursorB() { return cursorB.get(); }
    public int samplePeriodTicks() { return samplePeriodTicks.get(); }
    public int sampleRateMilliHz() { return sampleRateMilliHz.get(); }
    public int nyquistMilliHz() { return nyquistMilliHz.get(); }

    public int experimentStatus() { return experimentStatus.get(); }
    public int experimentChannel() { return experimentChannel.get(); }
    public boolean baselinePresent() { return baselinePresent.get() != 0; }
    public boolean candidatePresent() { return candidatePresent.get() != 0; }
    public int baselineSamplePeriodTicks() { return baselineSamplePeriodTicks.get(); }
    public int candidateSamplePeriodTicks() { return candidateSamplePeriodTicks.get(); }
    public int baselineSampleRateMilliHz() { return baselineSampleRateMilliHz.get(); }
    public int candidateSampleRateMilliHz() { return candidateSampleRateMilliHz.get(); }
    public int baselineNyquistMilliHz() { return baselineNyquistMilliHz.get(); }
    public int candidateNyquistMilliHz() { return candidateNyquistMilliHz.get(); }
    public int baselineCoverage() { return baselineCoverage.get(); }
    public int candidateCoverage() { return candidateCoverage.get(); }
    public int baselinePeriodSamples() { return baselinePeriodSamples.get(); }
    public int candidatePeriodSamples() { return candidatePeriodSamples.get(); }
    public int baselineFrequencyMilliHz() { return baselineFrequencyMilliHz.get(); }
    public int candidateFrequencyMilliHz() { return candidateFrequencyMilliHz.get(); }
    public int baselineAliasRisk() { return baselineAliasRisk.get(); }
    public int candidateAliasRisk() { return candidateAliasRisk.get(); }
    public int baselineMeanStep100() { return baselineMeanStep100.get(); }
    public int candidateMeanStep100() { return candidateMeanStep100.get(); }
    public int experimentSamplesDelta() { return experimentSamplesDelta.get(); }
    public int experimentFrequencyDeltaMilliHz() { return experimentFrequencyDeltaMilliHz.get(); }
    public int current(int channel) { return current[channel].get(); }
    public int coverage(int channel) { return coverage[channel].get(); }
    public int minimum(int channel) { return minimum[channel].get(); }
    public int maximum(int channel) { return maximum[channel].get(); }
    public int peakToPeak(int channel) { return peakToPeak[channel].get(); }
    public int average100(int channel) { return average100[channel].get(); }
    public int meanStep100(int channel) { return meanStep100[channel].get(); }
    public int periodTicks(int channel) { return periodTicks[channel].get(); }
    public int periodSamples(int channel) { return periodSamples[channel].get(); }
    public int aliasRisk(int channel) { return aliasRisk[channel].get(); }
    public int frequencyMilliHz(int channel) { return frequencyMilliHz[channel].get(); }
    public static int samplePeriodOptionCount() { return OscilloscopeBlockEntity.SAMPLE_PERIOD_OPTIONS.length; }
    public static int samplePeriodOptionTicks(int index) {
        int bounded = Math.max(0, Math.min(OscilloscopeBlockEntity.SAMPLE_PERIOD_OPTIONS.length - 1, index));
        return OscilloscopeBlockEntity.SAMPLE_PERIOD_OPTIONS[bounded];
    }
    public static double nominalTicksPerSecond() { return OscilloscopeBlockEntity.NOMINAL_TICKS_PER_SECOND; }
    public int displaySample(int channel, int slot) { return display[channel][slot].get(); }
    public int cableNodes() { return cableNodes.get(); }
    public int probeNodes() { return probeNodes.get(); }
    public int validChannels() { return validChannels.get(); }
    public int activeChannels() { return activeChannels.get(); }
    public int duplicateChannels() { return duplicateChannels.get(); }
    public boolean bounded() { return bounded.get() != 0; }
    public int shieldedCableNodes() { return shieldedCableNodes.get(); }
    public int unshieldedCableNodes() { return unshieldedCableNodes.get(); }
    public int shieldingCoverage() { return shieldingCoverage.get(); }
    public int exposedCableNodes() { return exposedCableNodes.get(); }
    public int shieldedExposedNodes() { return shieldedExposedNodes.get(); }
    public int unshieldedExposedNodes() { return unshieldedExposedNodes.get(); }
    public int interferenceExposure() { return interferenceExposure.get(); }
    public int interferenceConfidence() { return interferenceConfidence.get(); }
    public int probeCount(int channel) { return channelProbeCounts[channel].get(); }
}
