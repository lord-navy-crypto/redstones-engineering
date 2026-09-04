package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.LogicAnalyzerBlock;
import dev.redstoneengineering.blockentity.LogicAnalyzerBlockEntity;
import dev.redstoneengineering.instrument.InstrumentNetwork;
import dev.redstoneengineering.ui.EngineeringUiRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.level.block.state.BlockState;

/** Server-authoritative capture telemetry and bounded controls for the four-channel logic analyzer. */
public final class LogicAnalyzerMenu extends EngineeringDeviceMenu {
    public static final int BUTTON_ARM = 0;
    public static final int BUTTON_THRESHOLD_DECREASE = 1;
    public static final int BUTTON_THRESHOLD_INCREASE = 2;
    public static final int BUTTON_TRIGGER_CHANNEL = 3;
    public static final int BUTTON_TRIGGER_EDGE = 4;
    public static final int BUTTON_CURSOR_A = 5;
    public static final int BUTTON_CURSOR_B = 6;
    public static final int BUTTON_CLEAR = 7;

    private final DataSlot threshold = trackedInt();
    private final DataSlot sampleCount = trackedInt();
    private final DataSlot triggerChannel = trackedInt();
    private final DataSlot triggerEdge = trackedInt();
    private final DataSlot captureState = trackedInt();
    private final DataSlot cursorA = trackedInt();
    private final DataSlot cursorB = trackedInt();

    private final DataSlot[] coverage = new DataSlot[4];
    private final DataSlot[] duty = new DataSlot[4];
    private final DataSlot[] transitionRate = new DataSlot[4];
    private final DataSlot[] rising = new DataSlot[4];
    private final DataSlot[] falling = new DataSlot[4];
    private final DataSlot[] channelProbeCounts = new DataSlot[4];
    private final DataSlot[][] display = new DataSlot[4][LogicAnalyzerBlockEntity.DISPLAY_SAMPLES];

    private final DataSlot cableNodes = trackedInt();
    private final DataSlot probeNodes = trackedInt();
    private final DataSlot validChannels = trackedInt();
    private final DataSlot activeChannels = trackedInt();
    private final DataSlot duplicateChannels = trackedInt();
    private final DataSlot bounded = trackedInt();

    public LogicAnalyzerMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos());
    }

    public LogicAnalyzerMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(
                EngineeringUiRegistration.LOGIC_ANALYZER.get(),
                containerId,
                inventory,
                pos,
                RedstoneEngineering.LOGIC_ANALYZER.get()
        );
        for (int channel = 0; channel < 4; channel++) {
            coverage[channel] = trackedInt();
            duty[channel] = trackedInt();
            transitionRate[channel] = trackedInt();
            rising[channel] = trackedInt();
            falling[channel] = trackedInt();
            channelProbeCounts[channel] = trackedInt();
            for (int slot = 0; slot < LogicAnalyzerBlockEntity.DISPLAY_SAMPLES; slot++) {
                display[channel][slot] = trackedInt();
            }
        }
        if (!level.isClientSide) refreshAuthoritativeSnapshot();
    }

    @Override
    protected void refreshAuthoritativeSnapshot() {
        BlockState state = level.getBlockState(blockPos);
        if (!(state.getBlock() instanceof LogicAnalyzerBlock)) return;
        if (!(level.getBlockEntity(blockPos) instanceof LogicAnalyzerBlockEntity analyzer)) return;

        threshold.set(state.getValue(LogicAnalyzerBlock.THRESHOLD));
        sampleCount.set(analyzer.sampleCount());
        triggerChannel.set(analyzer.triggerChannel());
        triggerEdge.set(analyzer.triggerEdge());
        captureState.set(analyzer.armed() ? 1 : analyzer.triggered() ? 2 : 0);
        cursorA.set(analyzer.cursorA());
        cursorB.set(analyzer.cursorB());

        for (int channel = 0; channel < 4; channel++) {
            coverage[channel].set(analyzer.coveragePercent(channel));
            duty[channel].set(analyzer.dutyPercent(channel));
            transitionRate[channel].set(analyzer.transitionRatePercent(channel));
            rising[channel].set(analyzer.rising(channel));
            falling[channel].set(analyzer.falling(channel));
            for (int slot = 0; slot < LogicAnalyzerBlockEntity.DISPLAY_SAMPLES; slot++) {
                display[channel][slot].set(analyzer.displayState(channel, slot));
            }
        }

        InstrumentNetwork.ProbeSnapshot network = InstrumentNetwork.scan(level, blockPos);
        cableNodes.set(network.cableNodes());
        probeNodes.set(network.probeNodes());
        validChannels.set(network.validChannels());
        activeChannels.set(network.activeChannels());
        duplicateChannels.set(network.duplicateChannels());
        bounded.set(network.bounded() ? 1 : 0);
        for (int channel = 0; channel < 4; channel++) {
            channelProbeCounts[channel].set(network.counts()[channel]);
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (level.isClientSide) return true;
        if (!stillValid(player)) return false;
        boolean changed = LogicAnalyzerBlock.applyUiAction(level, blockPos, id);
        if (changed) {
            refreshAuthoritativeSnapshot();
            broadcastChanges();
        }
        return changed;
    }

    public int threshold() { return threshold.get(); }
    public int sampleCount() { return sampleCount.get(); }
    public int triggerChannel() { return triggerChannel.get(); }
    public int triggerEdge() { return triggerEdge.get(); }
    public int captureState() { return captureState.get(); }
    public int cursorA() { return cursorA.get(); }
    public int cursorB() { return cursorB.get(); }
    public int coverage(int channel) { return coverage[channel].get(); }
    public int duty(int channel) { return duty[channel].get(); }
    public int transitionRate(int channel) { return transitionRate[channel].get(); }
    public int rising(int channel) { return rising[channel].get(); }
    public int falling(int channel) { return falling[channel].get(); }
    public int displayState(int channel, int slot) { return display[channel][slot].get(); }
    public int probeCount(int channel) { return channelProbeCounts[channel].get(); }
    public int cableNodes() { return cableNodes.get(); }
    public int probeNodes() { return probeNodes.get(); }
    public int validChannels() { return validChannels.get(); }
    public int activeChannels() { return activeChannels.get(); }
    public int duplicateChannels() { return duplicateChannels.get(); }
    public boolean bounded() { return bounded.get() != 0; }
}
