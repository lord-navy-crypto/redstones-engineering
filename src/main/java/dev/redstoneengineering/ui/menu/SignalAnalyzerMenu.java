package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.SignalAnalyzerBlock;
import dev.redstoneengineering.ui.EngineeringUiRegistration;
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

    private final DataSlot mode = trackedInt();
    private final DataSlot calibrationOffset = trackedInt();
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
    private final DataSlot average100 = trackedInt();
    private final DataSlot peakToPeak = trackedInt();
    private final DataSlot meanStep100 = trackedInt();
    private final DataSlot stableAge = trackedInt();
    private final DataSlot sampleAge = trackedInt();
    private final DataSlot totalSamples = trackedInt();
    private final DataSlot modeSwitches = trackedInt();
    private final DataSlot calibrationSwitches = trackedInt();
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
    }

    @Override
    protected void refreshAuthoritativeSnapshot() {
        SignalAnalyzerBlock.UiSnapshot snapshot = SignalAnalyzerBlock.uiSnapshot(level, blockPos);
        mode.set(snapshot.mode());
        calibrationOffset.set(snapshot.calibrationOffset());
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
        average100.set(snapshot.average100());
        peakToPeak.set(snapshot.peakToPeak());
        meanStep100.set(snapshot.meanStep100());
        stableAge.set(snapshot.stableAgeTicks());
        sampleAge.set(snapshot.sampleAgeTicks());
        totalSamples.set(snapshot.totalSamples());
        modeSwitches.set(snapshot.modeSwitches());
        calibrationSwitches.set(snapshot.calibrationSwitches());
        for (int i = 0; i < samples.length; i++) samples[i].set(snapshot.samples()[i]);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (level.isClientSide) return true;
        if (!stillValid(player)) return false;
        boolean changed = SignalAnalyzerBlock.applyUiAction(level, blockPos, id);
        if (changed) {
            refreshAuthoritativeSnapshot();
            broadcastChanges();
        }
        return changed;
    }

    public int mode() { return mode.get(); }
    public int calibrationOffset() { return calibrationOffset.get(); }
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
    public int average100() { return average100.get(); }
    public int peakToPeak() { return peakToPeak.get(); }
    public int meanStep100() { return meanStep100.get(); }
    public int stableAgeTicks() { return stableAge.get(); }
    public int sampleAgeTicks() { return sampleAge.get(); }
    public int totalSamples() { return totalSamples.get(); }
    public int modeSwitches() { return modeSwitches.get(); }
    public int calibrationSwitches() { return calibrationSwitches.get(); }
    public int sample(int slot) { return samples[slot].get(); }
}
