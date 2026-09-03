package dev.redstoneengineering.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Shared alpha.8 sensor model.
 *
 * Sensors deliberately expose engineering imperfections instead of returning a
 * mathematically perfect value.  The four profiles trade sample rate,
 * resolution, noise and latency while keeping runtime values out of BlockState.
 */
public final class SensorModel {
    private SensorModel() {}

    public static int samplePeriod(int profile) {
        return switch (EngineeringMath.clamp(profile, 0, 3)) {
            case 0 -> 2;   // FAST
            case 1 -> 4;   // BALANCED
            case 2 -> 8;   // PRECISION
            default -> 6;  // RUGGED
        };
    }

    public static int noiseAmplitude(int profile) {
        return switch (EngineeringMath.clamp(profile, 0, 3)) {
            case 0 -> 3;
            case 1 -> 2;
            case 2 -> 1;
            default -> 1;
        };
    }

    public static int resolutionStep(int profile) {
        return switch (EngineeringMath.clamp(profile, 0, 3)) {
            case 0 -> 2;
            case 1 -> 1;
            case 2 -> 1;
            default -> 5;
        };
    }

    public static int latencySamples(int profile) {
        return profile == 0 ? 0 : 1;
    }

    public static String profileName(int profile) {
        return switch (EngineeringMath.clamp(profile, 0, 3)) {
            case 0 -> "FAST";
            case 1 -> "BALANCED";
            case 2 -> "PRECISION";
            default -> "RUGGED";
        };
    }

    /** Apply bounded deterministic noise and quantization to a normalized 0..100 quantity. */
    public static int condition(ServerLevel level, BlockPos pos, int normalized, int profile) {
        int p = EngineeringMath.clamp(profile, 0, 3);
        int amplitude = noiseAmplitude(p);
        long seed = level.getGameTime() * 0x9E3779B97F4A7C15L
                ^ pos.asLong() * 0xC2B2AE3D27D4EB4FL
                ^ (long) p * 0x165667B19E3779F9L;
        seed ^= (seed >>> 33);
        seed *= 0xff51afd7ed558ccdL;
        seed ^= (seed >>> 33);
        int noise = amplitude == 0 ? 0 : (int) Math.floorMod(seed, amplitude * 2L + 1L) - amplitude;
        int noisy = EngineeringMath.clamp(normalized + noise, 0, 100);
        int step = Math.max(1, resolutionStep(p));
        int quantized = Math.round(noisy / (float) step) * step;
        return EngineeringMath.clamp(quantized, 0, 100);
    }
}
