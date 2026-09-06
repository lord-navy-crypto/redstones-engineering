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
 * not claim to count Minecraft's internal redstone solver evaluations and never mutates the level.</p>
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

        telemetry.events.addLast(new NeighborNotificationSample(
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
        int radius = Math.max(1, Math.min(MAX_RADIUS_BLOCKS, requestedRadius));
        long windowTicks = Math.max(1L, Math.min(MAX_WINDOW_TICKS, requestedWindowTicks));
        long minimumTick = level.getGameTime() - windowTicks;
        LevelTelemetry telemetry = telemetry(level);

        int events = 0;
        int notifiedSides = 0;
        int forced = 0;
        int transitions = 0;
        Set<BlockPos> uniqueSources = new HashSet<>();
        Map<BlockPos, Integer> sourceCounts = new HashMap<>();

        for (NeighborNotificationSample sample : telemetry.events) {
            if (sample.gameTime() < minimumTick) continue;
            if (anchor.distManhattan(sample.source()) > radius) continue;
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
                anchor.immutable(), radius, windowTicks, events, notifiedSides, forced, transitions,
                uniqueSources.size(), hotspotCount, hotspot);
    }

    public static int retainedEventCount(ServerLevel level) {
        return telemetry(level).events.size();
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
        private final ArrayDeque<NeighborNotificationSample> events = new ArrayDeque<>();
        private final LinkedHashMap<BlockPos, String> previousStates = new LinkedHashMap<>(128, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<BlockPos, String> eldest) {
                return size() > MAX_STATE_POSITIONS;
            }
        };
    }

    public record NeighborNotificationSample(
            long gameTime,
            BlockPos source,
            String sourceKind,
            int notifiedSideCount,
            boolean forceRedstoneUpdate,
            boolean observedStateTransition,
            String stateSignature
    ) {}
}
