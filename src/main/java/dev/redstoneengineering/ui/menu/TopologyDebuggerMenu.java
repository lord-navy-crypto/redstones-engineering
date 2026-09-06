package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.EngineeringSystemsModule;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.TopologyDebuggerBlock;
import dev.redstoneengineering.diagnostics.redstone.VanillaRedstoneBehaviorReport;
import dev.redstoneengineering.diagnostics.redstone.VanillaRedstoneDiagnosticsReport;
import dev.redstoneengineering.diagnostics.redstone.VanillaRedstoneRuntimeReport;
import dev.redstoneengineering.diagnostics.redstone.VanillaRedstoneRuntimeTelemetry;
import dev.redstoneengineering.diagnostics.redstone.VanillaRedstoneTargetHistory;
import dev.redstoneengineering.diagnostics.redstone.VanillaRedstoneTargetSnapshot;
import dev.redstoneengineering.diagnostics.redstone.VanillaRedstoneTimingReport;
import dev.redstoneengineering.ui.EngineeringUiRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.level.block.state.BlockState;

/** Server-authoritative read-only engineering overlay for a vanilla-redstone target behind the debugger. */
public final class TopologyDebuggerMenu extends EngineeringDeviceMenu {
    private final DataSlot targetKind = trackedInt();
    private final DataSlot signalValue = trackedInt();
    private final DataSlot activeState = trackedInt();
    private final DataSlot configuredDelay = trackedInt();
    private final DataSlot targetMode = trackedInt();
    private final DataSlot targetFacing = trackedInt();
    private final DataSlot locked = trackedInt();

    private final DataSlot nodeCount = trackedInt();
    private final DataSlot dustCount = trackedInt();
    private final DataSlot poweredDustCount = trackedInt();
    private final DataSlot timingComponentCount = trackedInt();
    private final DataSlot maxDustPower = trackedInt();
    private final DataSlot fanoutProxy = trackedInt();
    private final DataSlot qcCandidates = trackedInt();
    private final DataSlot unloadedBoundaries = trackedInt();
    private final DataSlot traversalCapped = trackedInt();

    private final DataSlot runtimeEvents = trackedInt();
    private final DataSlot runtimeTransitions = trackedInt();
    private final DataSlot runtimeSources = trackedInt();
    private final DataSlot runtimeHotspotEvents = trackedInt();
    private final DataSlot timingObservations = trackedInt();
    private final DataSlot activeTicks = trackedInt();
    private final DataSlot observedSpan = trackedInt();
    private final DataSlot minInterTransition = trackedInt();
    private final DataSlot maxInterTransition = trackedInt();

    private final DataSlot completePulses = trackedInt();
    private final DataSlot minPulseWidth = trackedInt();
    private final DataSlot maxPulseWidth = trackedInt();
    private final DataSlot narrowPulses = trackedInt();
    private final DataSlot observerReturns = trackedInt();
    private final DataSlot orderScore = trackedInt();
    private final DataSlot orderConfidence = trackedInt();

    private final DataSlot timelineCount = trackedInt();
    private final DataSlot timelineSpan = trackedInt();
    private final DataSlot timelineTransitions = trackedInt();
    private final DataSlot timelineLatestLow = trackedInt();
    private final DataSlot timelineLatestHigh = trackedInt();
    private final DataSlot timelineTransitionMask = trackedInt();
    private final DataSlot[] timelineValues = new DataSlot[VanillaRedstoneTargetHistory.DISPLAY_SAMPLES];
    private final DataSlot[] timelineAges = new DataSlot[VanillaRedstoneTargetHistory.DISPLAY_SAMPLES];

    public TopologyDebuggerMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos());
    }

    public TopologyDebuggerMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(EngineeringUiRegistration.TOPOLOGY_DEBUGGER.get(), containerId, inventory, pos,
                EngineeringSystemsModule.TOPOLOGY_DEBUGGER.get());
        for (int i = 0; i < VanillaRedstoneTargetHistory.DISPLAY_SAMPLES; i++) {
            timelineValues[i] = trackedInt();
            timelineAges[i] = trackedInt();
        }
        if (!level.isClientSide) refreshAuthoritativeSnapshot();
    }

    @Override
    protected void refreshAuthoritativeSnapshot() {
        if (!(level instanceof ServerLevel server)) return;
        BlockState debugger = level.getBlockState(blockPos);
        if (!(debugger.getBlock() instanceof TopologyDebuggerBlock)) return;
        BlockPos target = blockPos.relative(debugger.getValue(DirectionalSignalBlock.FACING).getOpposite());

        VanillaRedstoneTargetSnapshot targetSnapshot = VanillaRedstoneTargetSnapshot.inspect(level, target);
        targetKind.set(targetSnapshot.kind());
        signalValue.set(targetSnapshot.signalValue());
        activeState.set(targetSnapshot.activeState());
        configuredDelay.set(targetSnapshot.configuredDelayGameTicks());
        targetMode.set(targetSnapshot.mode());
        targetFacing.set(targetSnapshot.facing());
        locked.set(targetSnapshot.locked());

        VanillaRedstoneDiagnosticsReport structural = TopologyDebuggerBlock.inspectVanillaTarget(level, blockPos, debugger);
        nodeCount.set(structural.nodeCount());
        dustCount.set(structural.dustCount());
        poweredDustCount.set(structural.poweredDustCount());
        timingComponentCount.set(structural.timingComponentCount());
        maxDustPower.set(structural.maxDustPower());
        fanoutProxy.set(structural.maxAdjacentRelevantDegree());
        qcCandidates.set(structural.possibleQcDependencyCount());
        unloadedBoundaries.set(structural.unloadedBoundaryCount());
        traversalCapped.set(structural.traversalCapped() ? 1 : 0);

        VanillaRedstoneRuntimeReport runtime = VanillaRedstoneRuntimeTelemetry.inspect(server, target);
        runtimeEvents.set(runtime.neighborNotificationEvents());
        runtimeTransitions.set(runtime.observedStateTransitions());
        runtimeSources.set(runtime.uniqueSourcePositions());
        runtimeHotspotEvents.set(runtime.hotspotEventCount());

        VanillaRedstoneTimingReport timing = VanillaRedstoneRuntimeTelemetry.inspectTiming(server, target);
        timingObservations.set(timing.observationCount());
        activeTicks.set(timing.activeTickCount());
        observedSpan.set(durationTicks(timing.observedSpanTicks()));
        minInterTransition.set(durationOrMissing(timing.minInterTransitionTicks()));
        maxInterTransition.set(durationOrMissing(timing.maxInterTransitionTicks()));

        VanillaRedstoneBehaviorReport behavior = VanillaRedstoneRuntimeTelemetry.inspectBehavior(server, target);
        completePulses.set(behavior.completeObservedPulses());
        minPulseWidth.set(durationOrMissing(behavior.minObservedPulseWidthTicks()));
        maxPulseWidth.set(durationOrMissing(behavior.maxObservedPulseWidthTicks()));
        narrowPulses.set(behavior.narrowObservedPulses());
        observerReturns.set(behavior.observerFeedbackCandidates());
        orderScore.set(behavior.orderSensitivityEvidenceScore());
        orderConfidence.set(confidenceCode(behavior.orderSensitivityConfidence()));

        VanillaRedstoneTargetHistory.Snapshot history = VanillaRedstoneTargetHistory.inspect(
                server, target, VanillaRedstoneRuntimeTelemetry.DEFAULT_WINDOW_TICKS);
        timelineCount.set(history.count());
        timelineSpan.set(history.timeSpanTicks());
        timelineTransitions.set(history.transitionCount());
        long latest = history.latestGameTime();
        timelineLatestLow.set((int) latest);
        timelineLatestHigh.set((int) (latest >>> 32));
        int transitionMask = 0;
        for (int i = 0; i < VanillaRedstoneTargetHistory.DISPLAY_SAMPLES; i++) {
            timelineValues[i].set(history.values()[i]);
            long time = history.sampleTimes()[i];
            timelineAges[i].set(time < 0 || latest < time ? -1 : durationTicks(latest - time));
            if (history.transitions()[i]) transitionMask |= 1 << i;
        }
        timelineTransitionMask.set(transitionMask);
    }

    public int targetKind() { return targetKind.get(); }
    public int signalValue() { return signalValue.get(); }
    public int activeState() { return activeState.get(); }
    public int configuredDelayGameTicks() { return configuredDelay.get(); }
    public int targetMode() { return targetMode.get(); }
    public int targetFacing() { return targetFacing.get(); }
    public int locked() { return locked.get(); }
    public int nodeCount() { return nodeCount.get(); }
    public int dustCount() { return dustCount.get(); }
    public int poweredDustCount() { return poweredDustCount.get(); }
    public int timingComponentCount() { return timingComponentCount.get(); }
    public int maxDustPower() { return maxDustPower.get(); }
    public int fanoutProxy() { return fanoutProxy.get(); }
    public int qcCandidates() { return qcCandidates.get(); }
    public int unloadedBoundaries() { return unloadedBoundaries.get(); }
    public boolean traversalCapped() { return traversalCapped.get() != 0; }
    public int runtimeEvents() { return runtimeEvents.get(); }
    public int runtimeTransitions() { return runtimeTransitions.get(); }
    public int runtimeSources() { return runtimeSources.get(); }
    public int runtimeHotspotEvents() { return runtimeHotspotEvents.get(); }
    public int timingObservations() { return timingObservations.get(); }
    public int activeTicks() { return activeTicks.get(); }
    public int observedSpanTicks() { return observedSpan.get(); }
    public int minInterTransitionTicks() { return minInterTransition.get(); }
    public int maxInterTransitionTicks() { return maxInterTransition.get(); }
    public int completePulses() { return completePulses.get(); }
    public int minPulseWidthTicks() { return minPulseWidth.get(); }
    public int maxPulseWidthTicks() { return maxPulseWidth.get(); }
    public int narrowPulses() { return narrowPulses.get(); }
    public int observerReturns() { return observerReturns.get(); }
    public int orderScore() { return orderScore.get(); }
    public int orderConfidence() { return orderConfidence.get(); }
    public int timelineCount() { return timelineCount.get(); }
    public int timelineSpanTicks() { return timelineSpan.get(); }
    public int timelineTransitions() { return timelineTransitions.get(); }
    public int timelineValue(int slot) { return timelineValues[slot].get(); }
    public boolean timelineTransition(int slot) { return (timelineTransitionMask.get() & (1 << slot)) != 0; }

    public long timelineLatestGameTime() {
        return Integer.toUnsignedLong(timelineLatestLow.get()) | ((long) timelineLatestHigh.get() << 32);
    }

    public long timelineGameTime(int slot) {
        int age = timelineAges[slot].get();
        long latest = timelineLatestGameTime();
        return age < 0 || latest < 0 ? -1L : latest - age;
    }

    private static int confidenceCode(String confidence) {
        return switch (confidence) {
            case "LOW" -> 1;
            case "MEDIUM" -> 2;
            case "HIGH" -> 3;
            default -> 0;
        };
    }

    private static int durationOrMissing(long ticks) {
        return ticks < 0 ? -1 : durationTicks(ticks);
    }

    private static int durationTicks(long ticks) {
        if (ticks <= 0) return 0;
        return (int) Math.min(Integer.MAX_VALUE, ticks);
    }
}
