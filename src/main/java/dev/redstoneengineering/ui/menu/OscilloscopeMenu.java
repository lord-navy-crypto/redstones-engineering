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

    private final DataSlot sampleCount = trackedInt();
    private final DataSlot triggerMode = trackedInt();
    private final DataSlot triggerChannel = trackedInt();
    private final DataSlot triggerLevel = trackedInt();
    private final DataSlot captureState = trackedInt(); // 0 HOLD, 1 ARMED, 2 TRIGGERED
    private final DataSlot cursorA = trackedInt();
    private final DataSlot cursorB = trackedInt();

    private final DataSlot[] current = new DataSlot[2];
    private final DataSlot[] coverage = new DataSlot[2];
    private final DataSlot[] minimum = new DataSlot[2];
    private final DataSlot[] maximum = new DataSlot[2];
    private final DataSlot[] peakToPeak = new DataSlot[2];
    private final DataSlot[] average100 = new DataSlot[2];
    private final DataSlot[] meanStep100 = new DataSlot[2];
    private final DataSlot[] periodTicks = new DataSlot[2];
    private final DataSlot[][] display = new DataSlot[2][OscilloscopeBlockEntity.DISPLAY_SAMPLES];

    private final DataSlot cableNodes = trackedInt();
    private final DataSlot probeNodes = trackedInt();
    private final DataSlot validChannels = trackedInt();
    private final DataSlot activeChannels = trackedInt();
    private final DataSlot duplicateChannels = trackedInt();
    private final DataSlot bounded = trackedInt();
    private final DataSlot[] channelProbeCounts = new DataSlot[2];

    public OscilloscopeMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos());
    }

    public OscilloscopeMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(
                EngineeringUiRegistration.OSCILLOSCOPE.get(),
                containerId,
                inventory,
                pos,
                RedstoneEngineering.OSCILLOSCOPE.get()
        );
        for (int channel = 0; channel < 2; channel++) {
            current[channel] = trackedInt();
            coverage[channel] = trackedInt();
            minimum[channel] = trackedInt();
            maximum[channel] = trackedInt();
            peakToPeak[channel] = trackedInt();
            average100[channel] = trackedInt();
            meanStep100[channel] = trackedInt();
            periodTicks[channel] = trackedInt();
            channelProbeCounts[channel] = trackedInt();
            for (int slot = 0; slot < OscilloscopeBlockEntity.DISPLAY_SAMPLES; slot++) {
                display[channel][slot] = trackedInt();
            }
        }
        if (!level.isClientSide) refreshAuthoritativeSnapshot();
    }

    @Override
    protected void refreshAuthoritativeSnapshot() {
        if (!(level.getBlockEntity(blockPos) instanceof OscilloscopeBlockEntity scope)) return;

        sampleCount.set(scope.sampleCount());
        triggerMode.set(scope.triggerMode());
        triggerChannel.set(scope.triggerChannel());
        triggerLevel.set(scope.triggerLevel());
        captureState.set(scope.armed() ? 1 : scope.triggered() ? 2 : 0);
        cursorA.set(scope.cursorA());
        cursorB.set(scope.cursorB());

        for (int channel = 0; channel < 2; channel++) {
            current[channel].set(scope.current(channel));
            coverage[channel].set(scope.coveragePercent(channel));
            minimum[channel].set(scope.minimum(channel));
            maximum[channel].set(scope.maximum(channel));
            peakToPeak[channel].set(scope.peakToPeak(channel));
            average100[channel].set(scope.average100(channel));
            meanStep100[channel].set(scope.meanStep100(channel));
            periodTicks[channel].set(scope.estimatedPeriodTicks(channel));
            for (int slot = 0; slot < OscilloscopeBlockEntity.DISPLAY_SAMPLES; slot++) {
                display[channel][slot].set(scope.displaySample(channel, slot));
            }
        }

        InstrumentNetwork.ProbeSnapshot network = InstrumentNetwork.scan(level, blockPos);
        cableNodes.set(network.cableNodes());
        probeNodes.set(network.probeNodes());
        validChannels.set(network.validChannels());
        activeChannels.set(network.activeChannels());
        duplicateChannels.set(network.duplicateChannels());
        bounded.set(network.bounded() ? 1 : 0);
        for (int channel = 0; channel < 2; channel++) channelProbeCounts[channel].set(network.counts()[channel]);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (level.isClientSide) return true;
        if (!stillValid(player)) return false;
        boolean changed = OscilloscopeBlock.applyUiAction(level, blockPos, id);
        if (changed) {
            refreshAuthoritativeSnapshot();
            broadcastChanges();
        }
        return changed;
    }

    public int sampleCount() { return sampleCount.get(); }
    public int triggerMode() { return triggerMode.get(); }
    public int triggerChannel() { return triggerChannel.get(); }
    public int triggerLevel() { return triggerLevel.get(); }
    public int captureState() { return captureState.get(); }
    public int cursorA() { return cursorA.get(); }
    public int cursorB() { return cursorB.get(); }
    public int current(int channel) { return current[channel].get(); }
    public int coverage(int channel) { return coverage[channel].get(); }
    public int minimum(int channel) { return minimum[channel].get(); }
    public int maximum(int channel) { return maximum[channel].get(); }
    public int peakToPeak(int channel) { return peakToPeak[channel].get(); }
    public int average100(int channel) { return average100[channel].get(); }
    public int meanStep100(int channel) { return meanStep100[channel].get(); }
    public int periodTicks(int channel) { return periodTicks[channel].get(); }
    public int displaySample(int channel, int slot) { return display[channel][slot].get(); }
    public int cableNodes() { return cableNodes.get(); }
    public int probeNodes() { return probeNodes.get(); }
    public int validChannels() { return validChannels.get(); }
    public int activeChannels() { return activeChannels.get(); }
    public int duplicateChannels() { return duplicateChannels.get(); }
    public boolean bounded() { return bounded.get() != 0; }
    public int probeCount(int channel) { return channelProbeCounts[channel].get(); }
}
