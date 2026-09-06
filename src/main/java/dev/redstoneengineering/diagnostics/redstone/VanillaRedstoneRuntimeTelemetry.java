package dev.redstoneengineering.diagnostics.redstone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Server-authoritative, read-only telemetry for vanilla-redstone NeighborNotifyEvent observations.
 *
 * <p>This deliberately measures the public NeoForge physics-notification event surface. It does
 * not claim to count Minecraft's internal redstone solver evaluations and never mutates the level.
 * Phase 3 additionally records a monotonically increasing listener-observation sequence. That
 * sequence is evidence of the order in which RSE observed events, not causal Minecraft update
 * order, scheduler priority, or sub-tick time.</p>
 */
public final class VanillaRedstoneRuntimeTelemetry {
    public static final int MAX_EVENTS_PER_LEVEL = 4096;
    public static final int MAX_STATE_POSITIONS = 1024;
    public static final int DEFAULT_RADIUS_BLOCKS = 16;
    public static final int MAX_RADIUS_BLOCKS = 64;
    public static final long DEFAULT_WINDOW_TICKS = 100L;
    public static final long MAX_WINDOW_TICKS = 1200L;

    private static final Map<ServerLevel, LevelTelemetry> LEVELS = new WeakHashMap<>();

    private VanillaRedstoneRuntimeTelemetry() {}

    /** Registered on NeoForge.EVENT_BUS. NeighborNotifyEvent is a server physics-update event. */
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockState state = event.getState();
        if (!VanillaRedstoneEngineeringProfile.isVanillaRedstoneTarget(state)) return;

        BlockPos pos = event.getPos().immutable();
        String signature = stateSignature(state);
        LevelTelemetry telemetry = telemetry(level);
        String previous = telemetry.previousStates.put(pos, signature);
        boolean transition = previous != null && !previous.equals(signature);
        long sequence = ++telemetry.nextObservationSequence;

        telemetry.events.addLast(new NeighborNotificationSample(
                sequence,
                level.getGameTime(),
                pos,
                BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
                event.getNotifiedSides().size(),
                event.getForceRedstoneUpdate(),
                transition,
                signature
        ));
        while (telemetry.events.size() > MAX_EVENTS_PER_LEVEL) telemetry.events.removeFirst();
    }

    public static VanillaRedstoneRuntimeReport inspect(ServerLevel level, BlockPos anchor) {
        return inspect(level, anchor, DEFAULT_RADIUS_BLOCKS, DEFAULT_WINDOW_TICKS);
    }

    public static VanillaRedstoneRuntimeReport inspect(
            ServerLevel level,
            BlockPos anchor,
            int requestedRadius,
            long requestedWindowTicks
    ) {
        QueryBounds bounds = bounds(level, requestedRadius, requestedWindowTicks);
        LevelTelemetry telemetry = telemetry(level);

        int events = 0;
        int notifiedSides = 0;
        int forced = 0;
        int transitions = 0;
        Set<BlockPos> uniqueSources = new HashSet<>();
        Map<BlockPos, Integer> sourceCounts = new HashMap<>();

        for (NeighborNotificationSample sample : telemetry.events) {
            if (!included(sample, anchor, bounds)) continue;
            events++;
            notifiedSides += sample.notifiedSideCount();
            if (sample.forceRedstoneUpdate()) forced++;
            if (sample.observedStateTransition()) transitions++;
            uniqueSources.add(sample.source());
            sourceCounts.merge(sample.source(), 1, Integer::sum);
        }

        BlockPos hotspot = anchor.immutable();
        int hotspotCount = 0;
        for (Map.Entry<BlockPos, Integer> entry : sourceCounts.entrySet()) {
            if (entry.getValue() > hotspotCount) {
                hotspot = entry.getKey().immutable();
                hotspotCount = entry.getValue();
            }
        }

        return new VanillaRedstoneRuntimeReport(
                anchor.immutable(), bounds.radius(), bounds.windowTicks(), events, notifiedSides, forced, transitions,
                uniqueSources.size(), hotspotCount, hotspot);
    }

    public static VanillaRedstoneTimingReport inspectTiming(ServerLevel level, BlockPos anchor) {
        return inspectTiming(level, anchor, DEFAULT_RADIUS_BLOCKS, DEFAULT_WINDOW_TICKS);
    }

    /**
     * Computes bounded tick-spacing and listener-observation-order evidence from retained samples.
     * Same-tick sequence values preserve listener invocation order only; they are not sub-tick time.
     */
    public static VanillaRedstoneTimingReport inspectTiming(
            ServerLevel level,
            BlockPos anchor,
            int requestedRadius,
            long requestedWindowTicks
    ) {
        QueryBounds bounds = bounds(level, requestedRadius, requestedWindowTicks);
        LevelTelemetry telemetry = telemetry(level);

        int observations = 0;
        int transitions = 0;
        int sameTickOrderedPairs = 0;
        int sameTickDistinctSourcePairs = 0;
        long firstTick = Long.MAX_VALUE;
        long lastTick = Long.MIN_VALUE;
        long firstSequence = Long.MAX_VALUE;
        long lastSequence = Long.MIN_VALUE;
        long minInterObservation = Long.MAX_VALUE;
        long maxInterObservation = Long.MIN_VALUE;
        long minInterTransition = Long.MAX_VALUE;
        long maxInterTransition = Long.MIN_VALUE;
        NeighborNotificationSample previousIncluded = null;
        Set<Long> activeTicks = new HashSet<>();
        Map<BlockPos, Long> previousTransitionTick = new HashMap<>();

        for (NeighborNotificationSample sample : telemetry.events) {
            if (!included(sample, anchor, bounds)) continue;
            observations++;
            activeTicks.add(sample.gameTime());
            firstTick = Math.min(firstTick, sample.gameTime());
            lastTick = Math.max(lastTick, sample.gameTime());
            firstSequence = Math.min(firstSequence, sample.observationSequence());
            lastSequence = Math.max(lastSequence, sample.observationSequence());

            if (previousIncluded != null) {
                long delta = Math.max(0L, sample.gameTime() - previousIncluded.gameTime());
                minInterObservation = Math.min(minInterObservation, delta);
                maxInterObservation = Math.max(maxInterObservation, delta);
                if (delta == 0L) {
                    sameTickOrderedPairs++;
                    if (!sample.source().equals(previousIncluded.source())) sameTickDistinctSourcePairs++;
                }
            }

            if (sample.observedStateTransition()) {
                transitions++;
                Long priorTransition = previousTransitionTick.put(sample.source(), sample.gameTime());
                if (priorTransition != null) {
                    long delta = Math.max(0L, sample.gameTime() - priorTransition);
                    minInterTransition = Math.min(minInterTransition, delta);
                    maxInterTransition = Math.max(maxInterTransition, delta);
                }
            }
            previousIncluded = sample;
        }

        if (observations == 0) {
            return VanillaRedstoneTimingReport.empty(anchor, bounds.radius(), bounds.windowTicks());
        }
        long observedSpanTicks = Math.max(1L, lastTick - firstTick + 1L);
        return new VanillaRedstoneTimingReport(
                anchor.immutable(), bounds.radius(), bounds.windowTicks(), observations, transitions, activeTicks.size(),
                firstTick, lastTick, observedSpanTicks,
                minInterObservation == Long.MAX_VALUE ? -1L : minInterObservation,
                maxInterObservation == Long.MIN_VALUE ? -1L : maxInterObservation,
                minInterTransition == Long.MAX_VALUE ? -1L : minInterTransition,
                maxInterTransition == Long.MIN_VALUE ? -1L : maxInterTransition,
                sameTickOrderedPairs, sameTickDistinctSourcePairs, firstSequence, lastSequence);
    }

    public static int retainedEventCount(ServerLevel level) {
        return telemetry(level).events.size();
    }

    /** Clears only RSE observer telemetry; it never changes any world or redstone state. */
    public static synchronized void clear(ServerLevel level) {
        LEVELS.remove(level);
    }

    /** Region-scoped cache reset used by repeatable diagnostics/tests without disturbing other areas. */
    public static synchronized void clearRegion(ServerLevel level, BlockPos anchor, int requestedRadius) {
        int radius = Math.max(1, Math.min(MAX_RADIUS_BLOCKS, requestedRadius));
        LevelTelemetry telemetry = telemetry(level);
        telemetry.events.removeIf(sample -> anchor.distManhattan(sample.source()) <= radius);
        telemetry.previousStates.entrySet().removeIf(entry -> anchor.distManhattan(entry.getKey()) <= radius);
    }

    private static QueryBounds bounds(ServerLevel level, int requestedRadius, long requestedWindowTicks) {
        int radius = Math.max(1, Math.min(MAX_RADIUS_BLOCKS, requestedRadius));
        long windowTicks = Math.max(1L, Math.min(MAX_WINDOW_TICKS, requestedWindowTicks));
        return new QueryBounds(radius, windowTicks, level.getGameTime() - windowTicks);
    }

    private static boolean included(NeighborNotificationSample sample, BlockPos anchor, QueryBounds bounds) {
        return sample.gameTime() >= bounds.minimumTick()
                && anchor.distManhattan(sample.source()) <= bounds.radius();
    }

    private static synchronized LevelTelemetry telemetry(ServerLevel level) {
        return LEVELS.computeIfAbsent(level, ignored -> new LevelTelemetry());
    }

    private static String stateSignature(BlockState state) {
        StringBuilder signature = new StringBuilder(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        if (state.hasProperty(RedStoneWireBlock.POWER)) {
            signature.append("|power=").append(state.getValue(RedStoneWireBlock.POWER));
        }
        if (state.hasProperty(BlockStateProperties.POWERED)) {
            signature.append("|powered=").append(state.getValue(BlockStateProperties.POWERED));
        }
        if (state.hasProperty(BlockStateProperties.LIT)) {
            signature.append("|lit=").append(state.getValue(BlockStateProperties.LIT));
        }
        if (state.hasProperty(BlockStateProperties.EXTENDED)) {
            signature.append("|extended=").append(state.getValue(BlockStateProperties.EXTENDED));
        }
        return signature.toString();
    }

    private static final class LevelTelemetry {
        private long nextObservationSequence;
        private final ArrayDeque<NeighborNotificationSample> events = new ArrayDeque<>();
        private final LinkedHashMap<BlockPos, String> previousStates = new LinkedHashMap<>(128, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<BlockPos, String> eldest) {
                return size() > MAX_STATE_POSITIONS;
            }
        };
    }

    private record QueryBounds(int radius, long windowTicks, long minimumTick) {}

    public record NeighborNotificationSample(
            long observationSequence,
            long gameTime,
            BlockPos source,
            String sourceKind,
            int notifiedSideCount,
            boolean forceRedstoneUpdate,
            boolean observedStateTransition,
            String stateSignature
    ) {}
}
