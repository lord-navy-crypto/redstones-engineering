package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.PidControllerBlock;
import dev.redstoneengineering.diagnostics.ClosedLoopCommissioning;
import dev.redstoneengineering.diagnostics.CommissioningSnapshot;
import dev.redstoneengineering.diagnostics.CommissioningStatus;
import dev.redstoneengineering.diagnostics.PidTelemetryHistory;
import dev.redstoneengineering.diagnostics.acceptance.AcceptanceEvidenceStore;
import dev.redstoneengineering.ui.EngineeringUiRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.level.block.state.BlockState;

/** Read-only commissioning telemetry plus bounded server-side tuning actions for the PID controller. */
public final class PidControllerMenu extends EngineeringDeviceMenu {
    public static final int BUTTON_TUNING_PREVIOUS = 0;
    public static final int BUTTON_TUNING_NEXT = 1;

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
    private final DataSlot stepActive = trackedInt();
    private final DataSlot modeTransfers = trackedInt();
    private final DataSlot historyCount = trackedInt();

    private final DataSlot telemetryCount = trackedInt();
    private final DataSlot telemetryTimeSpan = trackedInt();
    private final DataSlot telemetrySaturationHits = trackedInt();
    private final DataSlot telemetryMaxAbsError = trackedInt();
    private final DataSlot telemetryMeanAbsError100 = trackedInt();
    private final DataSlot telemetryRecentAbsError100 = trackedInt();
    private final DataSlot telemetryLatestTimeLow = trackedInt();
    private final DataSlot telemetryLatestTimeHigh = trackedInt();
    private final DataSlot telemetrySaturationMask = trackedInt();
    private final DataSlot[] telemetrySetpoints = new DataSlot[PidTelemetryHistory.DISPLAY_SAMPLES];
    private final DataSlot[] telemetryProcessValues = new DataSlot[PidTelemetryHistory.DISPLAY_SAMPLES];
    private final DataSlot[] telemetryOutputs = new DataSlot[PidTelemetryHistory.DISPLAY_SAMPLES];
    private final DataSlot[] telemetryErrors = new DataSlot[PidTelemetryHistory.DISPLAY_SAMPLES];
    private final DataSlot[] telemetryAges = new DataSlot[PidTelemetryHistory.DISPLAY_SAMPLES];

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
        for (int i = 0; i < PidTelemetryHistory.DISPLAY_SAMPLES; i++) {
            telemetrySetpoints[i] = trackedInt();
            telemetryProcessValues[i] = trackedInt();
            telemetryOutputs[i] = trackedInt();
            telemetryErrors[i] = trackedInt();
            telemetryAges[i] = trackedInt();
        }
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
        stepActive.set(snapshot.stepActive() ? 1 : 0);
        modeTransfers.set(snapshot.modeTransfers());
        historyCount.set(AcceptanceEvidenceStore.history(level, blockPos).size());

        PidTelemetryHistory.Snapshot telemetry = PidTelemetryHistory.snapshot(level, blockPos);
        telemetryCount.set(telemetry.count());
        telemetryTimeSpan.set(telemetry.timeSpanTicks());
        telemetrySaturationHits.set(telemetry.saturationHits());
        telemetryMaxAbsError.set(telemetry.maxAbsError());
        telemetryMeanAbsError100.set(telemetry.meanAbsError100());
        telemetryRecentAbsError100.set(telemetry.recentAbsError100());
        long latest = telemetry.latestGameTime();
        telemetryLatestTimeLow.set((int) latest);
        telemetryLatestTimeHigh.set((int) (latest >>> 32));

        int saturationMask = 0;
        for (int i = 0; i < PidTelemetryHistory.DISPLAY_SAMPLES; i++) {
            telemetrySetpoints[i].set(telemetry.setpoints()[i]);
            telemetryProcessValues[i].set(telemetry.processValues()[i]);
            telemetryOutputs[i].set(telemetry.outputs()[i]);
            telemetryErrors[i].set(telemetry.errors()[i]);
            if (telemetry.saturationOutputs()[i] >= 0) saturationMask |= 1 << i;
            long time = telemetry.sampleTimes()[i];
            telemetryAges[i].set(time < 0 || latest < time ? -1 : durationTicks(latest - time));
        }
        telemetrySaturationMask.set(saturationMask);
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
    public boolean stepActive() { return stepActive.get() != 0; }
    public int modeTransfers() { return modeTransfers.get(); }
    public int historyCount() { return historyCount.get(); }

    public int telemetryCount() { return telemetryCount.get(); }
    public int telemetryTimeSpanTicks() { return telemetryTimeSpan.get(); }
    public int telemetrySaturationHits() { return telemetrySaturationHits.get(); }
    public int telemetryMaxAbsError() { return telemetryMaxAbsError.get(); }
    public int telemetryMeanAbsError100() { return telemetryMeanAbsError100.get(); }
    public int telemetryRecentAbsError100() { return telemetryRecentAbsError100.get(); }
    public int telemetrySetpoint(int slot) { return telemetrySetpoints[slot].get(); }
    public int telemetryProcessValue(int slot) { return telemetryProcessValues[slot].get(); }
    public int telemetryOutput(int slot) { return telemetryOutputs[slot].get(); }
    public int telemetryError(int slot) { return telemetryErrors[slot].get(); }
    public boolean telemetrySaturated(int slot) { return (telemetrySaturationMask.get() & (1 << slot)) != 0; }

    public long telemetryLatestGameTime() {
        return Integer.toUnsignedLong(telemetryLatestTimeLow.get()) | ((long) telemetryLatestTimeHigh.get() << 32);
    }

    public long telemetryGameTime(int slot) {
        int age = telemetryAges[slot].get();
        long latest = telemetryLatestGameTime();
        return age < 0 || latest < 0 ? -1L : latest - age;
    }

    public CommissioningStatus status() {
        CommissioningStatus[] values = CommissioningStatus.values();
        int index = Math.max(0, Math.min(values.length - 1, status.get()));
        return values[index];
    }

    private static int durationTicks(long ticks) {
        if (ticks <= 0) return 0;
        return (int) Math.min(Integer.MAX_VALUE, ticks);
    }
}
