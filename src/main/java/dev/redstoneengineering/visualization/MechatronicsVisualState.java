package dev.redstoneengineering.visualization;

/**
 * Immutable renderer-facing projection of simulation state.
 *
 * This type contains no mutation hooks and deliberately lives downstream of
 * physics. GeckoLib/renderer code may consume it, but it cannot drive the
 * simulation back in the opposite direction.
 */
public record MechatronicsVisualState(
        double position01,
        double velocitySigned,
        boolean braked,
        double opening01,
        double pressure01
) {
    public MechatronicsVisualState {
        position01 = clamp01(position01);
        velocitySigned = Math.max(-1.0, Math.min(1.0, velocitySigned));
        opening01 = clamp01(opening01);
        pressure01 = clamp01(pressure01);
    }

    public static MechatronicsVisualState servo(int position, int velocity, boolean brake, int maxVelocity) {
        double v = maxVelocity <= 0 ? 0.0 : velocity / (double) maxVelocity;
        return new MechatronicsVisualState(position / 15.0, v, brake, 0.0, 0.0);
    }

    public static MechatronicsVisualState cylinder(int position, int velocity, int pressure) {
        return new MechatronicsVisualState(position / 15.0, velocity, false, 0.0, pressure / 100.0);
    }

    public static MechatronicsVisualState valve(int opening, int pressure) {
        return new MechatronicsVisualState(0.0, 0.0, false, opening / 15.0, pressure / 100.0);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
