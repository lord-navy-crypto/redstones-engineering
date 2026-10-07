package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.SignalAnalyzerBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.diagnostics.SignalCalibrationTrialComparison;
import dev.redstoneengineering.diagnostics.SignalCalibrationTrialStore;
import dev.redstoneengineering.ui.EngineeringUiRegistration;
import dev.redstoneengineering.ui.ldlib.SignalAnalyzerLdUi;
import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;

/** Server-synchronized measurement-quality snapshot and bounded controls for Signal Analyzer. */
public final class SignalAnalyzerMenu extends EngineeringDeviceMenu {
    public static final int BUTTON_MODE_TOGGLE = 0;
    public static final int BUTTON_CALIBRATION_DECREASE = 1;
    public static final int BUTTON_CALIBRATION_INCREASE = 2;
    public static final int BUTTON_RESET_HISTORY = 3;
    public static final int BUTTON_ROTATE_LEFT = 4;
    public static final int BUTTON_ROTATE_RIGHT = 5;
    public static final int BUTTON_REFERENCE_DECREASE = 6;
    public static final int BUTTON_REFERENCE_INCREASE = 7;
    public static final int BUTTON_TRIAL_BASELINE = 8;
    public static final int BUTTON_TRIAL_CANDIDATE = 9;
    public static final int BUTTON_TRIAL_CLEAR = 10;
    public static final int BUTTON_CALIBRATION_DIRECT_BASE = 18000;
    public static final int BUTTON_CALIBRATION_DIRECT_MAX = 18004;
    public static final int BUTTON_REFERENCE_DIRECT_BASE = 18100;
    public static final int BUTTON_REFERENCE_DIRECT_MAX = 18115;

    private final DataSlot mode = trackedInt();
    private final DataSlot calibrationOffset = trackedInt();
    private final DataSlot reference = trackedInt();
    private final DataSlot facing = trackedInt();
    private final DataSlot raw = trackedInt();
    private final DataSlot calibrated = trackedInt();
    private final DataSlot output = trackedInt();
    private final DataSlot lifeMin = trackedInt();
    private final DataSlot lifeMax = trackedInt();
    private final DataSlot changes = trackedInt();
    private final DataSlot rising = trackedInt();
    private final DataSlot falling = trackedInt();
    private final DataSlot lastDelta = trackedInt();
    private final DataSlot maxDelta = trackedInt();
    private final DataSlot windowCount = trackedInt();
    private final DataSlot validWindowCount = trackedInt();
    private final DataSlot measurementQuality = trackedInt();
    private final DataSlot average100 = trackedInt();
    private final DataSlot peakToPeak = trackedInt();
    private final DataSlot meanStep100 = trackedInt();
    private final DataSlot stableAge = trackedInt();
    private final DataSlot sampleAge = trackedInt();
    private final DataSlot totalSamples = trackedInt();
    private final DataSlot modeSwitches = trackedInt();
    private final DataSlot calibrationSwitches = trackedInt();
    private final DataSlot referenceSwitches = trackedInt();

    private final DataSlot trialBaselineSequence = trackedInt();
    private final DataSlot trialCandidateSequence = trackedInt();
    private final DataSlot trialTrend = trackedInt();
    private final DataSlot trialErrorDelta100 = trackedInt();
    private final DataSlot trialClippingDelta = trackedInt();
    private final DataSlot trialSpanDelta = trackedInt();
    private final DataSlot trialMeanStepDelta100 = trackedInt();
    private final DataSlot trialCalibrationDelta = trackedInt();

    private final DataSlot[] samples = new DataSlot[SignalAnalyzerBlock.DISPLAY_SAMPLES];

    public SignalAnalyzerMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos());
    }

    public SignalAnalyzerMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(
                EngineeringUiRegistration.SIGNAL_ANALYZER.get(),
                containerId,
                inventory,
                pos,
                RedstoneEngineering.SIGNAL_ANALYZER.get()
        );
        for (int i = 0; i < samples.length; i++) samples[i] = trackedInt();
        if (!level.isClientSide) refreshAuthoritativeSnapshot();
        if ((Object) this instanceof IModularUIHolderMenu holder) {
            holder.setModularUI(SignalAnalyzerLdUi.create(this, inventory.player));
        }
    }

    @Override
    protected void refreshAuthoritativeSnapshot() {
        SignalAnalyzerBlock.UiSnapshot snapshot = SignalAnalyzerBlock.uiSnapshot(level, blockPos);
        mode.set(snapshot.mode());
        calibrationOffset.set(snapshot.calibrationOffset());
        reference.set(snapshot.reference());
        facing.set(snapshot.facingOrdinal());
        raw.set(snapshot.raw());
        calibrated.set(snapshot.calibrated());
        output.set(snapshot.output());
        lifeMin.set(snapshot.lifeMin());
        lifeMax.set(snapshot.lifeMax());
        changes.set(snapshot.changes());
        rising.set(snapshot.rising());
        falling.set(snapshot.falling());
        lastDelta.set(snapshot.lastDelta());
        maxDelta.set(snapshot.maxDelta());
        windowCount.set(snapshot.windowCount());
        validWindowCount.set(snapshot.validWindowCount());
        measurementQuality.set(snapshot.measurementQuality().ordinal());
        average100.set(snapshot.average100());
        peakToPeak.set(snapshot.peakToPeak());
        meanStep100.set(snapshot.meanStep100());
        stableAge.set(snapshot.stableAgeTicks());
        sampleAge.set(snapshot.sampleAgeTicks());
        totalSamples.set(snapshot.totalSamples());
        modeSwitches.set(snapshot.modeSwitches());
        calibrationSwitches.set(snapshot.calibrationSwitches());
        referenceSwitches.set(snapshot.referenceSwitches());

        trialBaselineSequence.set(0);
        trialCandidateSequence.set(0);
        trialTrend.set(-1);
        trialErrorDelta100.set(0);
        trialClippingDelta.set(0);
        trialSpanDelta.set(0);
        trialMeanStepDelta100.set(0);
        trialCalibrationDelta.set(0);

        SignalCalibrationTrialStore.baseline(level, blockPos).ifPresent(record ->
                trialBaselineSequence.set((int) Math.min(Integer.MAX_VALUE, record.sequence())));
        SignalCalibrationTrialStore.candidate(level, blockPos).ifPresent(record ->
                trialCandidateSequence.set((int) Math.min(Integer.MAX_VALUE, record.sequence())));
        SignalCalibrationTrialStore.comparison(level, blockPos).ifPresent(comparison -> {
            trialTrend.set(comparison.trend().ordinal());
            trialErrorDelta100.set(comparison.errorDelta100());
            trialClippingDelta.set(comparison.clippingDelta());
            trialSpanDelta.set(comparison.spanDelta());
            trialMeanStepDelta100.set(comparison.meanStepDelta100());
            trialCalibrationDelta.set(comparison.calibrationOffsetDelta());
        });

        for (int i = 0; i < samples.length; i++) samples[i].set(snapshot.samples()[i]);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (level.isClientSide) return true;
        if (!stillValid(player)) return false;
        boolean changed;
        if (id >= BUTTON_CALIBRATION_DIRECT_BASE && id <= BUTTON_CALIBRATION_DIRECT_MAX) {
            changed = SignalAnalyzerBlock.setCalibrationOffset(level, blockPos, (id - BUTTON_CALIBRATION_DIRECT_BASE) - 2);
        } else if (id >= BUTTON_REFERENCE_DIRECT_BASE && id <= BUTTON_REFERENCE_DIRECT_MAX) {
            changed = SignalAnalyzerBlock.setReference(level, blockPos, id - BUTTON_REFERENCE_DIRECT_BASE);
        } else if (id == BUTTON_TRIAL_BASELINE) {
            changed = SignalAnalyzerBlock.captureCalibrationBaseline(level, blockPos) != null;
        } else if (id == BUTTON_TRIAL_CANDIDATE) {
            changed = SignalAnalyzerBlock.captureCalibrationCandidate(level, blockPos) != null;
        } else if (id == BUTTON_TRIAL_CLEAR) {
            changed = SignalAnalyzerBlock.clearCalibrationTrial(level, blockPos);
        } else if (id == BUTTON_ROTATE_LEFT || id == BUTTON_ROTATE_RIGHT) {
            changed = SignalAnalyzerBlock.rotateMeasurementAxis(level, blockPos, id == BUTTON_ROTATE_RIGHT);
        } else {
            changed = SignalAnalyzerBlock.applyUiAction(level, blockPos, id);
        }
        if (changed) {
            refreshAuthoritativeSnapshot();
            broadcastChanges();
        }
        return changed;
    }

    /** LDLib2 HMI intent facade; mutations reuse the validated server menu path. */
    public boolean toggleMode() {
        return clickMenuButton(playerInventory.player, BUTTON_MODE_TOGGLE);
    }

    public boolean resetStatistics() {
        return clickMenuButton(playerInventory.player, BUTTON_RESET_HISTORY);
    }

    public boolean setCalibrationFromUi(int value) {
        if (value < -2 || value > 2) return false;
        return clickMenuButton(playerInventory.player, BUTTON_CALIBRATION_DIRECT_BASE + value + 2);
    }

    public boolean setReferenceFromUi(int value) {
        if (value < 0 || value > 15) return false;
        return clickMenuButton(playerInventory.player, BUTTON_REFERENCE_DIRECT_BASE + value);
    }

    public boolean captureTrialBaseline() {
        return clickMenuButton(playerInventory.player, BUTTON_TRIAL_BASELINE);
    }

    public boolean captureTrialCandidate() {
        return clickMenuButton(playerInventory.player, BUTTON_TRIAL_CANDIDATE);
    }

    public boolean clearTrial() {
        return clickMenuButton(playerInventory.player, BUTTON_TRIAL_CLEAR);
    }

    public int mode() { return mode.get(); }
    public int calibrationOffset() { return calibrationOffset.get(); }
    public int reference() { return reference.get(); }
    public int facingOrdinal() { return facing.get(); }
    public int raw() { return raw.get(); }
    public int calibrated() { return calibrated.get(); }
    public int output() { return output.get(); }
    public int lifeMin() { return lifeMin.get(); }
    public int lifeMax() { return lifeMax.get(); }
    public int changes() { return changes.get(); }
    public int rising() { return rising.get(); }
    public int falling() { return falling.get(); }
    public int lastDelta() { return lastDelta.get(); }
    public int maxDelta() { return maxDelta.get(); }
    public int windowCount() { return windowCount.get(); }
    public int validWindowCount() { return validWindowCount.get(); }
    public int coveragePercent() {
        int count = windowCount();
        return count <= 0 ? 0 : Math.max(0, Math.min(100, (validWindowCount() * 100) / count));
    }
    public PortQuality measurementQuality() {
        PortQuality[] values = PortQuality.values();
        int ordinal = measurementQuality.get();
        return ordinal < 0 || ordinal >= values.length ? PortQuality.NO_SIGNAL : values[ordinal];
    }
    public int average100() { return average100.get(); }
    public int peakToPeak() { return peakToPeak.get(); }
    public int meanStep100() { return meanStep100.get(); }
    public int stableAgeTicks() { return stableAge.get(); }
    public int sampleAgeTicks() { return sampleAge.get(); }
    public int totalSamples() { return totalSamples.get(); }
    public int modeSwitches() { return modeSwitches.get(); }
    public int calibrationSwitches() { return calibrationSwitches.get(); }
    public int referenceSwitches() { return referenceSwitches.get(); }

    public int trialBaselineSequence() { return trialBaselineSequence.get(); }
    public int trialCandidateSequence() { return trialCandidateSequence.get(); }
    public int trialErrorDelta100() { return trialErrorDelta100.get(); }
    public int trialClippingDelta() { return trialClippingDelta.get(); }
    public int trialSpanDelta() { return trialSpanDelta.get(); }
    public int trialMeanStepDelta100() { return trialMeanStepDelta100.get(); }
    public int trialCalibrationDelta() { return trialCalibrationDelta.get(); }
    public SignalCalibrationTrialComparison.Trend trialTrend() {
        SignalCalibrationTrialComparison.Trend[] values = SignalCalibrationTrialComparison.Trend.values();
        int ordinal = trialTrend.get();
        return ordinal < 0 || ordinal >= values.length ? null : values[ordinal];
    }

    public int sample(int slot) { return samples[slot].get(); }
}
