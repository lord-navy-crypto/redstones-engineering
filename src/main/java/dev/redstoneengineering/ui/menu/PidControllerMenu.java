package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.PidControllerBlock;
import dev.redstoneengineering.diagnostics.ClosedLoopCommissioning;
import dev.redstoneengineering.diagnostics.CommissioningSnapshot;
import dev.redstoneengineering.diagnostics.CommissioningStatus;
import dev.redstoneengineering.diagnostics.PidTelemetryStore;
import dev.redstoneengineering.diagnostics.acceptance.AcceptanceEvidenceStore;
import dev.redstoneengineering.ui.EngineeringUiRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Read-only commissioning telemetry plus bounded server-side tuning actions for the PID controller. */
public final class PidControllerMenu extends EngineeringDeviceMenu {
    public static final int BUTTON_TUNING_PREVIOUS = 0;
    public static final int BUTTON_TUNING_NEXT = 1;
    public static final int TREND_SAMPLES = PidTelemetryStore.MAX_SAMPLES_PER_CONTROLLER;

    private final DataSlot tuning = trackedInt();
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
    private final DataSlot manualMode = trackedInt();
    private final DataSlot inhibited = trackedInt();
    private final DataSlot modeTransfers = trackedInt();
    private final DataSlot historyCount = trackedInt();
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
    }

    @Override
    protected void refreshAuthoritativeSnapshot() {
        BlockState state = level.getBlockState(blockPos);
        if (!(state.getBlock() instanceof PidControllerBlock)) return;
        tuning.set(state.getValue(PidControllerBlock.TUNING));

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
        manualMode.set(snapshot.manualMode() ? 1 : 0);
        inhibited.set(snapshot.inhibited() ? 1 : 0);
        modeTransfers.set(snapshot.modeTransfers());
        historyCount.set(AcceptanceEvidenceStore.history(level, blockPos).size());

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
        boolean changed = PidControllerBlock.applyTuningAction(level, blockPos, id);
        if (changed) {
            refreshAuthoritativeSnapshot();
            broadcastChanges();
        }
        return changed;
    }

    public int tuning() { return tuning.get(); }
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
    public boolean manualMode() { return manualMode.get() != 0; }
    public boolean inhibited() { return inhibited.get() != 0; }
    public int modeTransfers() { return modeTransfers.get(); }
    public int historyCount() { return historyCount.get(); }
    public int trendCount() { return trendCount.get(); }

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

    public CommissioningStatus status() {
        CommissioningStatus[] values = CommissioningStatus.values();
        int index = Math.max(0, Math.min(values.length - 1, status.get()));
        return values[index];
    }
}
