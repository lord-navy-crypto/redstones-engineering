package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.PidControllerBlock;
import dev.redstoneengineering.diagnostics.ClosedLoopCommissioning;
import dev.redstoneengineering.diagnostics.CommissioningSnapshot;
import dev.redstoneengineering.diagnostics.CommissioningStatus;
import dev.redstoneengineering.diagnostics.CommissioningTrialComparison;
import dev.redstoneengineering.diagnostics.CommissioningTrialRecord;
import dev.redstoneengineering.diagnostics.CommissioningTrialStore;
import dev.redstoneengineering.diagnostics.PidTelemetryStore;
import dev.redstoneengineering.diagnostics.PneumaticClosedLoopWitness;
import dev.redstoneengineering.diagnostics.acceptance.AcceptanceEvidenceRecord;
import dev.redstoneengineering.diagnostics.acceptance.AcceptanceEvidenceStore;
import dev.redstoneengineering.diagnostics.acceptance.AcceptanceEvidenceTrend;
import dev.redstoneengineering.diagnostics.acceptance.EngineeringAcceptanceStatus;
import dev.redstoneengineering.ui.EngineeringUiRegistration;
import dev.redstoneengineering.ui.ldlib.PidControllerLdUi;
import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Commissioning telemetry plus bounded server-side tuning, routing and acceptance actions. */
public final class PidControllerMenu extends EngineeringDeviceMenu {
    public static final int BUTTON_TUNING_PREVIOUS = 0;
    public static final int BUTTON_TUNING_NEXT = 1;
    public static final int BUTTON_INPUT_PREVIOUS = 2;
    public static final int BUTTON_INPUT_NEXT = 3;
    public static final int BUTTON_OUTPUT_PREVIOUS = 4;
    public static final int BUTTON_OUTPUT_NEXT = 5;
    public static final int BUTTON_CAPTURE_ACCEPTANCE = 6;
    public static final int BUTTON_RESET_RUNTIME_TREND = 7;
    public static final int BUTTON_TRIAL_BASELINE = 8;
    public static final int BUTTON_TRIAL_CANDIDATE = 9;
    public static final int BUTTON_TRIAL_CLEAR = 10;
    public static final int TREND_SAMPLES = PidTelemetryStore.MAX_SAMPLES_PER_CONTROLLER;

    private final DataSlot tuning = trackedInt();
    private final DataSlot inputFacing = trackedInt();
    private final DataSlot outputFacing = trackedInt();
    private final DataSlot available = trackedInt();
    private final DataSlot setpoint = trackedInt();
    private final DataSlot processValue = trackedInt();
    private final DataSlot controlOutput = trackedInt();
    private final DataSlot error = trackedInt();
    private final DataSlot rise90 = trackedInt();
    private final DataSlot settling = trackedInt();
    private final DataSlot overshoot = trackedInt();
    private final DataSlot saturationEvents = trackedInt();
    private final DataSlot score = trackedInt();
    private final DataSlot status = trackedInt();
    private final DataSlot controllerScore = trackedInt();
    private final DataSlot controllerStatus = trackedInt();
    private final DataSlot manualMode = trackedInt();
    private final DataSlot inhibited = trackedInt();
    private final DataSlot modeTransfers = trackedInt();
    private final DataSlot kp = trackedInt();
    private final DataSlot kiDiv = trackedInt();
    private final DataSlot kd = trackedInt();
    private final DataSlot derivativeSmoothing = trackedInt();
    private final DataSlot sampleTicks = trackedInt();
    private final DataSlot integralState = trackedInt();
    private final DataSlot derivativeState = trackedInt();
    private final DataSlot bias = trackedInt();
    private final DataSlot pTerm = trackedInt();
    private final DataSlot iTerm = trackedInt();
    private final DataSlot dTerm = trackedInt();
    private final DataSlot unsaturatedOutput = trackedInt();
    private final DataSlot antiWindupHolding = trackedInt();

    private final DataSlot plantDetected = trackedInt();
    private final DataSlot plantReady = trackedInt();
    private final DataSlot plantPosition = trackedInt();
    private final DataSlot plantTarget = trackedInt();
    private final DataSlot plantPressure = trackedInt();
    private final DataSlot plantSupply = trackedInt();
    private final DataSlot plantObservedLoss = trackedInt();
    private final DataSlot plantLineLoss = trackedInt();
    private final DataSlot plantRestrictionLoss = trackedInt();
    private final DataSlot plantStallTicks = trackedInt();
    private final DataSlot plantSamples = trackedInt();
    private final DataSlot plantPenalty = trackedInt();
    private final DataSlot plantStatus = trackedInt();
    private final DataSlot plantDiagnosis = trackedInt();

    private final DataSlot historyCount = trackedInt();
    private final DataSlot latestSequence = trackedInt();
    private final DataSlot latestAcceptanceStatus = trackedInt();
    private final DataSlot latestAcceptanceScore = trackedInt();
    private final DataSlot comparisonTrend = trackedInt();
    private final DataSlot scoreDelta = trackedInt();
    private final DataSlot topologyIssueDelta = trackedInt();

    private final DataSlot trialBaselineSequence = trackedInt();
    private final DataSlot trialCandidateSequence = trackedInt();
    private final DataSlot trialTrend = trackedInt();
    private final DataSlot trialRobust = trackedInt();
    private final DataSlot trialScoreDelta = trackedInt();
    private final DataSlot trialSettlingDelta = trackedInt();
    private final DataSlot trialOvershootDelta = trackedInt();
    private final DataSlot trialSaturationDelta = trackedInt();
    private final DataSlot trialTopologyIssueDelta = trackedInt();

    private final DataSlot trendCount = trackedInt();
    private final DataSlot[] trend = trackedInts(TREND_SAMPLES);

    public PidControllerMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos());
    }

    public PidControllerMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(
                EngineeringUiRegistration.PID_CONTROLLER.get(),
                containerId,
                inventory,
                pos,
                RedstoneEngineering.PID_CONTROLLER.get()
        );
        if (!level.isClientSide) refreshAuthoritativeSnapshot();
        if ((Object) this instanceof IModularUIHolderMenu holder) {
            holder.setModularUI(PidControllerLdUi.create(this, inventory.player));
        }
    }

    @Override
    protected void refreshAuthoritativeSnapshot() {
        BlockState state = level.getBlockState(blockPos);
        if (!(state.getBlock() instanceof PidControllerBlock)) return;
        tuning.set(state.getValue(PidControllerBlock.TUNING));
        inputFacing.set(DirectionalSignalBlock.seriesInputSide(state).ordinal());
        outputFacing.set(DirectionalSignalBlock.seriesOutputSide(state).ordinal());

        CommissioningSnapshot controller = ClosedLoopCommissioning.inspectController(level, blockPos);
        CommissioningSnapshot snapshot = ClosedLoopCommissioning.inspectPid(level, blockPos);
        available.set(snapshot.available() ? 1 : 0);
        setpoint.set(snapshot.setpoint());
        processValue.set(snapshot.processValue());
        controlOutput.set(snapshot.controlOutput());
        error.set(snapshot.error());
        rise90.set(snapshot.rise90Ticks());
        settling.set(snapshot.settlingTicks());
        overshoot.set(snapshot.overshoot());
        saturationEvents.set(snapshot.saturationEvents());
        score.set(snapshot.score());
        status.set(snapshot.status().ordinal());
        controllerScore.set(controller.score());
        controllerStatus.set(controller.status().ordinal());
        manualMode.set(snapshot.manualMode() ? 1 : 0);
        inhibited.set(snapshot.inhibited() ? 1 : 0);
        modeTransfers.set(snapshot.modeTransfers());

        PidControllerBlock.TuningModel model = PidControllerBlock.tuningModel(tuning.get());
        kp.set(model.kp());
        kiDiv.set(model.kiDiv());
        kd.set(model.kd());
        derivativeSmoothing.set(model.derivativeSmoothing());
        sampleTicks.set(model.sampleTicks());
        PidControllerBlock.RuntimeTerms terms = PidControllerBlock.runtimeTerms(level, blockPos, tuning.get(), snapshot.error());
        integralState.set(terms.integralState());
        derivativeState.set(terms.derivativeState());
        bias.set(terms.bias());
        pTerm.set(terms.pTerm());
        iTerm.set(terms.iTerm());
        dTerm.set(terms.dTerm());
        unsaturatedOutput.set(terms.unsaturatedOutput());
        antiWindupHolding.set(terms.antiWindupHolding() ? 1 : 0);

        PneumaticClosedLoopWitness.Snapshot plant = ClosedLoopCommissioning.inspectPneumaticPlant(level, blockPos);
        plantDetected.set(plant.detected() ? 1 : 0);
        plantReady.set(plant.ready() ? 1 : 0);
        plantPosition.set(plant.position());
        plantTarget.set(plant.target());
        plantPressure.set(plant.actuatorPressure());
        plantSupply.set(plant.supplyPressure());
        plantObservedLoss.set(plant.observedLoss());
        plantLineLoss.set(plant.lineLoss());
        plantRestrictionLoss.set(plant.restrictionLoss());
        plantStallTicks.set(plant.stallTicks());
        plantSamples.set(plant.samples());
        plantPenalty.set(plant.penalty());
        plantStatus.set(plant.plantStatus().ordinal());
        plantDiagnosis.set(plant.diagnosis().ordinal());

        List<AcceptanceEvidenceRecord> evidence = AcceptanceEvidenceStore.history(level, blockPos);
        historyCount.set(evidence.size());
        latestSequence.set(0);
        latestAcceptanceStatus.set(EngineeringAcceptanceStatus.NOT_READY.ordinal());
        latestAcceptanceScore.set(0);
        comparisonTrend.set(-1);
        scoreDelta.set(0);
        topologyIssueDelta.set(0);
        AcceptanceEvidenceStore.latest(level, blockPos).ifPresent(record -> {
            latestSequence.set((int) Math.min(Integer.MAX_VALUE, record.sequence()));
            latestAcceptanceStatus.set(record.acceptance().status().ordinal());
            latestAcceptanceScore.set(record.acceptance().commissioningScore());
        });
        AcceptanceEvidenceStore.compareLatestToPrevious(level, blockPos).ifPresent(comparison -> {
            comparisonTrend.set(comparison.trend().ordinal());
            scoreDelta.set(comparison.scoreDelta());
            topologyIssueDelta.set(comparison.topologyIssueDelta());
        });

        trialBaselineSequence.set(0);
        trialCandidateSequence.set(0);
        trialTrend.set(-1);
        trialRobust.set(0);
        trialScoreDelta.set(0);
        trialSettlingDelta.set(0);
        trialOvershootDelta.set(0);
        trialSaturationDelta.set(0);
        trialTopologyIssueDelta.set(0);

        CommissioningTrialStore.baseline(level, blockPos).ifPresent(record ->
                trialBaselineSequence.set((int) Math.min(Integer.MAX_VALUE, record.sequence())));
        CommissioningTrialStore.candidate(level, blockPos).ifPresent(record ->
                trialCandidateSequence.set((int) Math.min(Integer.MAX_VALUE, record.sequence())));
        CommissioningTrialStore.comparison(level, blockPos).ifPresent(comparison -> {
            trialTrend.set(comparison.trend().ordinal());
            trialRobust.set(comparison.robust() ? 1 : 0);
            trialScoreDelta.set(comparison.scoreDelta());
            trialSettlingDelta.set(comparison.settlingDeltaTicks());
            trialOvershootDelta.set(comparison.overshootDelta());
            trialSaturationDelta.set(comparison.saturationDelta());
            trialTopologyIssueDelta.set(comparison.topologyIssueDelta());
        });

        List<Integer> samples = PidTelemetryStore.snapshot(level, blockPos);
        int count = Math.min(TREND_SAMPLES, samples.size());
        int pad = TREND_SAMPLES - count;
        trendCount.set(count);
        for (int slot = 0; slot < TREND_SAMPLES; slot++) {
            trend[slot].set(slot < pad ? -1 : samples.get(slot - pad));
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (level.isClientSide) return true;
        if (!stillValid(player)) return false;

        boolean changed;
        if (id == BUTTON_CAPTURE_ACCEPTANCE) {
            changed = PidControllerBlock.captureAcceptanceEvidence(level, blockPos) != null;
        } else if (id == BUTTON_RESET_RUNTIME_TREND) {
            changed = PidControllerBlock.resetRuntimeAndTrend(level, blockPos);
        } else if (id == BUTTON_TRIAL_BASELINE) {
            changed = PidControllerBlock.captureCommissioningTrialBaseline(level, blockPos) != null;
        } else if (id == BUTTON_TRIAL_CANDIDATE) {
            changed = PidControllerBlock.captureCommissioningTrialCandidate(level, blockPos) != null;
        } else if (id == BUTTON_TRIAL_CLEAR) {
            changed = PidControllerBlock.clearCommissioningTrial(level, blockPos);
        } else if (id == BUTTON_INPUT_PREVIOUS || id == BUTTON_INPUT_NEXT) {
            changed = DirectionalSignalBlock.rotateSeriesInput(level, blockPos, id == BUTTON_INPUT_NEXT);
        } else if (id == BUTTON_OUTPUT_PREVIOUS || id == BUTTON_OUTPUT_NEXT) {
            changed = DirectionalSignalBlock.rotateSeriesOutput(level, blockPos, id == BUTTON_OUTPUT_NEXT);
        } else {
            changed = PidControllerBlock.applyTuningAction(level, blockPos, id);
        }
        if (changed) {
            refreshAuthoritativeSnapshot();
            broadcastChanges();
        }
        return changed;
    }

    /** LDLib2 operator-intent facade; all actions reuse the existing server-authoritative menu path. */
    public boolean cycleTuningForward() { return clickMenuButton(playerInventory.player, BUTTON_TUNING_NEXT); }
    public boolean cycleInputForward() { return clickMenuButton(playerInventory.player, BUTTON_INPUT_NEXT); }
    public boolean cycleOutputForward() { return clickMenuButton(playerInventory.player, BUTTON_OUTPUT_NEXT); }
    public boolean captureAcceptance() { return clickMenuButton(playerInventory.player, BUTTON_CAPTURE_ACCEPTANCE); }
    public boolean resetRuntimeTrend() { return clickMenuButton(playerInventory.player, BUTTON_RESET_RUNTIME_TREND); }
    public boolean captureTrialBaseline() { return clickMenuButton(playerInventory.player, BUTTON_TRIAL_BASELINE); }
    public boolean captureTrialCandidate() { return clickMenuButton(playerInventory.player, BUTTON_TRIAL_CANDIDATE); }
    public boolean clearTrial() { return clickMenuButton(playerInventory.player, BUTTON_TRIAL_CLEAR); }

    public int tuning() { return tuning.get(); }
    public Direction inputFacing() { return direction(inputFacing.get()); }
    public Direction outputFacing() { return direction(outputFacing.get()); }
    public boolean available() { return available.get() != 0; }
    public int setpoint() { return setpoint.get(); }
    public int processValue() { return processValue.get(); }
    public int controlOutput() { return controlOutput.get(); }
    public int error() { return error.get(); }
    public int rise90Ticks() { return rise90.get(); }
    public int settlingTicks() { return settling.get(); }
    public int overshoot() { return overshoot.get(); }
    public int saturationEvents() { return saturationEvents.get(); }
    public int score() { return score.get(); }
    public int controllerScore() { return controllerScore.get(); }
    public CommissioningStatus controllerStatus() { return status(controllerStatus.get()); }
    public boolean manualMode() { return manualMode.get() != 0; }
    public boolean inhibited() { return inhibited.get() != 0; }
    public int modeTransfers() { return modeTransfers.get(); }
    public int kp() { return kp.get(); }
    public int kiDiv() { return kiDiv.get(); }
    public int kd() { return kd.get(); }
    public int derivativeSmoothing() { return derivativeSmoothing.get(); }
    public int sampleTicks() { return sampleTicks.get(); }
    public int integralState() { return integralState.get(); }
    public int derivativeState() { return derivativeState.get(); }
    public int bias() { return bias.get(); }
    public int pTerm() { return pTerm.get(); }
    public int iTerm() { return iTerm.get(); }
    public int dTerm() { return dTerm.get(); }
    public int unsaturatedOutput() { return unsaturatedOutput.get(); }
    public boolean antiWindupHolding() { return antiWindupHolding.get() != 0; }

    public boolean plantDetected() { return plantDetected.get() != 0; }
    public boolean plantReady() { return plantReady.get() != 0; }
    public int plantPosition() { return plantPosition.get(); }
    public int plantTarget() { return plantTarget.get(); }
    public int plantPressure() { return plantPressure.get(); }
    public int plantSupply() { return plantSupply.get(); }
    public int plantObservedLoss() { return plantObservedLoss.get(); }
    public int plantLineLoss() { return plantLineLoss.get(); }
    public int plantRestrictionLoss() { return plantRestrictionLoss.get(); }
    public int plantStallTicks() { return plantStallTicks.get(); }
    public int plantSamples() { return plantSamples.get(); }
    public int plantPenalty() { return plantPenalty.get(); }
    public CommissioningStatus plantStatus() { return status(plantStatus.get()); }
    public PneumaticClosedLoopWitness.Diagnosis plantDiagnosis() {
        PneumaticClosedLoopWitness.Diagnosis[] values = PneumaticClosedLoopWitness.Diagnosis.values();
        int index = Math.max(0, Math.min(values.length - 1, plantDiagnosis.get()));
        return values[index];
    }

    public int historyCount() { return historyCount.get(); }
    public int latestSequence() { return latestSequence.get(); }
    public int latestAcceptanceScore() { return latestAcceptanceScore.get(); }
    public int scoreDelta() { return scoreDelta.get(); }
    public int topologyIssueDelta() { return topologyIssueDelta.get(); }

    public int trialBaselineSequence() { return trialBaselineSequence.get(); }
    public int trialCandidateSequence() { return trialCandidateSequence.get(); }
    public boolean trialRobust() { return trialRobust.get() != 0; }
    public int trialScoreDelta() { return trialScoreDelta.get(); }
    public int trialSettlingDelta() { return trialSettlingDelta.get(); }
    public int trialOvershootDelta() { return trialOvershootDelta.get(); }
    public int trialSaturationDelta() { return trialSaturationDelta.get(); }
    public int trialTopologyIssueDelta() { return trialTopologyIssueDelta.get(); }
    public AcceptanceEvidenceTrend trialTrend() {
        AcceptanceEvidenceTrend[] values = AcceptanceEvidenceTrend.values();
        int index = trialTrend.get();
        return index >= 0 && index < values.length ? values[index] : null;
    }

    public int trendCount() { return trendCount.get(); }

    public EngineeringAcceptanceStatus latestAcceptanceStatus() {
        EngineeringAcceptanceStatus[] values = EngineeringAcceptanceStatus.values();
        int index = Math.max(0, Math.min(values.length - 1, latestAcceptanceStatus.get()));
        return values[index];
    }

    public AcceptanceEvidenceTrend comparisonTrend() {
        AcceptanceEvidenceTrend[] values = AcceptanceEvidenceTrend.values();
        int index = comparisonTrend.get();
        return index >= 0 && index < values.length ? values[index] : null;
    }

    public int trendSetpoint(int slot) {
        return PidTelemetryStore.setpoint(trendPacked(slot));
    }

    public int trendProcessValue(int slot) {
        return PidTelemetryStore.processValue(trendPacked(slot));
    }

    public int trendControlOutput(int slot) {
        return PidTelemetryStore.controlOutput(trendPacked(slot));
    }

    private int trendPacked(int slot) {
        return slot >= 0 && slot < TREND_SAMPLES ? trend[slot].get() : -1;
    }

    public CommissioningStatus status() { return status(status.get()); }

    private static CommissioningStatus status(int ordinal) {
        CommissioningStatus[] values = CommissioningStatus.values();
        int index = Math.max(0, Math.min(values.length - 1, ordinal));
        return values[index];
    }

    private static Direction direction(int ordinal) {
        Direction[] values = Direction.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : Direction.NORTH;
    }
}
