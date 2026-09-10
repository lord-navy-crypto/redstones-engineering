package dev.redstoneengineering.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Low-bandwidth industrial-style radio abstraction.
 * Payload and link quality are separate. Same-channel collisions invalidate the frame;
 * adjacent-channel aggressors, distance, obstacles and deterministic fading reduce quality.
 */
public final class RadioKernel {
    private RadioKernel() {}
    public static final int RANGE = 32;
    public static final int MIN_DECODE_QUALITY = 20;

    public record Reception(
            int value,
            int quality,
            int drivers,
            boolean valid,
            boolean collision,
            int interference,
            int obstacles,
            int latencyTicks,
            boolean coverageComplete
    ) {}

    private record Tx(int channel, int payload) {}
    private record ObstacleSample(int hits, boolean complete) {}
    private static final Map<Level, Map<Long, Tx>> TX = new WeakHashMap<>();

    /** Register an active transmitter. Payload zero is a real frame value, not transmitter absence. */
    public static synchronized void updateTransmitter(Level level, BlockPos pos, int channel, int payload) {
        Map<Long, Tx> transmitters = TX.computeIfAbsent(level, l -> new HashMap<>());
        transmitters.put(pos.asLong(), new Tx(channel, Math.max(0, Math.min(15, payload))));
    }

    public static synchronized void removeTransmitter(Level level, BlockPos pos) {
        Map<Long, Tx> transmitters = TX.get(level);
        if (transmitters != null) transmitters.remove(pos.asLong());
    }

    /**
     * Sample the line of sight without converting unavailable chunks into clear air.
     * A partial obstacle scan is unknown evidence and therefore cannot support a decoded frame.
     */
    private static ObstacleSample obstacleSamples(Level level, BlockPos a, BlockPos b) {
        double dx = b.getX() - a.getX();
        double dy = b.getY() - a.getY();
        double dz = b.getZ() - a.getZ();
        int steps = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz)));
        int hits = 0;
        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            BlockPos p = BlockPos.containing(
                    a.getX() + 0.5 + dx * t,
                    a.getY() + 0.5 + dy * t,
                    a.getZ() + 0.5 + dz * t
            );
            if (!level.hasChunkAt(p)) return new ObstacleSample(hits, false);
            if (!level.getBlockState(p).isAir()) hits++;
        }
        return new ObstacleSample(hits, true);
    }

    private static int deterministicFade(Level level, BlockPos tx, BlockPos rx) {
        long h = tx.asLong() * 31L + rx.asLong() * 17L + (level.getGameTime() / 20L);
        return (int) Math.floorMod(h, 7L);
    }

    /** Observer-neutral radio reception calculation. Reading diagnostics must not mutate network state. */
    public static synchronized Reception receivePacket(Level level, BlockPos rx, int channel) {
        Map<Long, Tx> transmitters = TX.get(level);
        if (transmitters == null) {
            return new Reception(0, 0, 0, false, false, 0, 0, 0, true);
        }

        int drivers = 0;
        int value = 0;
        int bestQuality = 0;
        int bestObstacles = 0;
        int adjacent = 0;
        int bestLatency = 0;
        boolean coverageComplete = true;

        for (var entry : transmitters.entrySet()) {
            Tx tx = entry.getValue();
            BlockPos transmitterPos = BlockPos.of(entry.getKey());
            long dx = transmitterPos.getX() - rx.getX();
            long dy = transmitterPos.getY() - rx.getY();
            long dz = transmitterPos.getZ() - rx.getZ();
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance > RANGE) continue;

            boolean adjacentChannel = Math.abs(tx.channel - channel) == 1;
            boolean sameChannel = tx.channel == channel;
            if (!adjacentChannel && !sameChannel) continue;

            // Registry presence is not proof that the transmitter's current world state is available.
            // Treat a relevant registered Tx in an unavailable chunk as incomplete coverage rather than
            // trusting stale payload/channel evidence left behind by chunk unload.
            if (!level.hasChunkAt(transmitterPos)) {
                coverageComplete = false;
                continue;
            }

            if (adjacentChannel) {
                adjacent++;
                continue;
            }

            ObstacleSample sample = obstacleSamples(level, transmitterPos, rx);
            if (!sample.complete()) {
                coverageComplete = false;
                continue;
            }

            drivers++;
            value = tx.payload;
            int obstacles = sample.hits();
            int distanceLoss = (int) Math.round(55.0 * distance / RANGE);
            int obstacleLoss = Math.min(25, obstacles * 2);
            int fade = deterministicFade(level, transmitterPos, rx);
            int quality = Math.max(0, 100 - distanceLoss - obstacleLoss - fade);
            if (quality > bestQuality) {
                bestQuality = quality;
                bestObstacles = obstacles;
                bestLatency = 2 + (int) Math.ceil(distance / 12.0);
            }
        }

        int interferencePenalty = Math.min(30, adjacent * 8);
        int quality = Math.max(0, bestQuality - interferencePenalty);
        boolean collision = drivers > 1;
        boolean valid = coverageComplete && drivers == 1 && quality >= MIN_DECODE_QUALITY;
        return new Reception(
                valid ? value : 0,
                coverageComplete && !collision ? quality : 0,
                drivers,
                valid,
                collision,
                adjacent,
                bestObstacles,
                bestLatency,
                coverageComplete
        );
    }

    /** Authoritative receiver ticks record topology/driver diagnostics explicitly. */
    public static void recordReception(Level level, int channel, Reception reception) {
        NetworkKernel.recordDriverState(level, "radio:" + channel, reception.drivers());
    }

    public static int receive(Level level, BlockPos rx, int channel) {
        return receivePacket(level, rx, channel).value();
    }
}
