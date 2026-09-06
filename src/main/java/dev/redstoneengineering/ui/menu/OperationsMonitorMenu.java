package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.OperationsMonitorBlock;
import dev.redstoneengineering.diagnostics.IndustrialOperationsAssessment;
import dev.redstoneengineering.diagnostics.OperationsDashboardSnapshot;
import dev.redstoneengineering.diagnostics.OperationsEventWindow;
import dev.redstoneengineering.diagnostics.events.SystemEventKind;
import dev.redstoneengineering.diagnostics.events.SystemEventRecord;
import dev.redstoneengineering.ui.EngineeringUiRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Dedicated read-only synchronized operations console for the existing Operations Monitor block. */
public final class OperationsMonitorMenu extends EngineeringDeviceMenu {
    public static final int EVENT_SLOTS = OperationsEventWindow.MAX_VISIBLE_EVENTS;
    private static final int MAX_SYNC_AGE_TICKS = 32767;

    private final DataSlot queue = trackedInt();
    private final DataSlot throughput = trackedInt();
    private final DataSlot downtime = trackedInt();
    private final DataSlot state = trackedInt();
    private final DataSlot queuePressure = trackedInt();
    private final DataSlot dominantConstraint = trackedInt();

    private final DataSlot retainedEvents = trackedInt();
    private final DataSlot recentEvents = trackedInt();
    private final DataSlot recentAbnormalEvents = trackedInt();
    private final DataSlot timelineCount = trackedInt();
    private final DataSlot firstOutSlot = trackedInt();
    private final DataSlot firstOutKind = trackedInt();
    private final DataSlot firstOutSeverity = trackedInt();
    private final DataSlot firstOutAge = trackedInt();

    private final DataSlot[] eventKinds = trackedInts(EVENT_SLOTS);
    private final DataSlot[] eventSeverities = trackedInts(EVENT_SLOTS);
    private final DataSlot[] eventAges = trackedInts(EVENT_SLOTS);

    public OperationsMonitorMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos());
    }

    public OperationsMonitorMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(
                EngineeringUiRegistration.OPERATIONS_MONITOR.get(),
                containerId,
                inventory,
                pos,
                RedstoneEngineering.OPERATIONS_MONITOR.get()
        );
        if (!level.isClientSide) refreshAuthoritativeSnapshot();
    }

    @Override
    protected void refreshAuthoritativeSnapshot() {
        BlockState blockState = level.getBlockState(blockPos);
        if (!(blockState.getBlock() instanceof OperationsMonitorBlock)) return;

        OperationsDashboardSnapshot dashboard = OperationsDashboardSnapshot.inspect(level, blockPos);
        IndustrialOperationsAssessment.Snapshot operations = dashboard.operations();
        OperationsEventWindow window = OperationsEventWindow.inspect(level, dashboard);

        queue.set(operations.queueNow());
        throughput.set(operations.throughputCyclesPerMinute());
        downtime.set(operations.downtimeTicks());
        state.set(operations.state().ordinal());
        queuePressure.set(operations.queuePressurePercent());
        dominantConstraint.set(operations.dominantConstraint().ordinal());

        retainedEvents.set(dashboard.retainedEvents());
        recentEvents.set(dashboard.recentEvents());
        recentAbnormalEvents.set(dashboard.recentAbnormalEvents());

        List<SystemEventRecord> events = window.events();
        int count = Math.min(EVENT_SLOTS, events.size());
        int pad = EVENT_SLOTS - count;
        timelineCount.set(count);
        for (int slot = 0; slot < EVENT_SLOTS; slot++) {
            if (slot < pad) {
                eventKinds[slot].set(-1);
                eventSeverities[slot].set(-1);
                eventAges[slot].set(-1);
                continue;
            }
            SystemEventRecord event = events.get(slot - pad);
            eventKinds[slot].set(event.kind().ordinal());
            eventSeverities[slot].set(event.severity());
            eventAges[slot].set(ageTicks(event.tick()));
        }

        int visibleFirstOut = window.firstOutIndex();
        firstOutSlot.set(visibleFirstOut < 0 ? -1 : pad + visibleFirstOut);
        if (window.firstOut().isPresent()) {
            SystemEventRecord first = window.firstOut().get().firstOut();
            firstOutKind.set(first.kind().ordinal());
            firstOutSeverity.set(first.severity());
            firstOutAge.set(ageTicks(first.tick()));
        } else {
            firstOutKind.set(-1);
            firstOutSeverity.set(-1);
            firstOutAge.set(-1);
        }
    }

    private int ageTicks(long eventTick) {
        long age = Math.max(0L, level.getGameTime() - eventTick);
        return (int) Math.min(MAX_SYNC_AGE_TICKS, age);
    }

    public int queue() { return queue.get(); }
    public int throughput() { return throughput.get(); }
    public int downtimeTicks() { return downtime.get(); }
    public int queuePressurePercent() { return queuePressure.get(); }
    public int retainedEvents() { return retainedEvents.get(); }
    public int recentEvents() { return recentEvents.get(); }
    public int recentAbnormalEvents() { return recentAbnormalEvents.get(); }
    public int timelineCount() { return timelineCount.get(); }
    public int firstOutSlot() { return firstOutSlot.get(); }
    public int firstOutKindOrdinal() { return firstOutKind.get(); }
    public int firstOutSeverity() { return firstOutSeverity.get(); }
    public int firstOutAgeTicks() { return firstOutAge.get(); }

    public OperationsMonitorBlock.SystemState state() {
        OperationsMonitorBlock.SystemState[] values = OperationsMonitorBlock.SystemState.values();
        return values[clampIndex(state.get(), values.length)];
    }

    public IndustrialOperationsAssessment.Constraint dominantConstraint() {
        IndustrialOperationsAssessment.Constraint[] values = IndustrialOperationsAssessment.Constraint.values();
        return values[clampIndex(dominantConstraint.get(), values.length)];
    }

    public int eventKindOrdinal(int slot) {
        return validSlot(slot) ? eventKinds[slot].get() : -1;
    }

    public int eventSeverity(int slot) {
        return validSlot(slot) ? eventSeverities[slot].get() : -1;
    }

    public int eventAgeTicks(int slot) {
        return validSlot(slot) ? eventAges[slot].get() : -1;
    }

    public boolean eventAbnormal(int slot) {
        int kind = eventKindOrdinal(slot);
        if (kind < 0) return false;
        SystemEventKind[] values = SystemEventKind.values();
        int index = clampIndex(kind, values.length);
        return values[index].abnormal() || eventSeverity(slot) >= 2;
    }

    private static boolean validSlot(int slot) {
        return slot >= 0 && slot < EVENT_SLOTS;
    }

    private static int clampIndex(int value, int length) {
        return Math.max(0, Math.min(Math.max(0, length - 1), value));
    }
}
