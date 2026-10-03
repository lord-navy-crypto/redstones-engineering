package dev.redstoneengineering.physics;

/**
 * Centralized default engineering parameter profile for bounded RSE physics.
 *
 * <p>This class is intentionally server-authoritative and dependency-free. It
 * gives experiments a single provenance point for model defaults while device
 * state still owns the selected setting. World-facing vanilla redstone remains
 * 0..15.</p>
 */
public final class EngineeringParameterProfile {
    public static final String PROFILE_ID = "rse-default-v1";

    public static final int LAPIS_FILTER_ALPHA_STEPS = 8;
    public static final int LAPIS_FILTER_DEFAULT_INDEX = 2;
    private static final double[] LAPIS_FILTER_ALPHA = {
            0.05, 0.10, 0.20, 0.35, 0.50, 0.65, 0.80, 1.00
    };

    public static final int QUARTZ_PHASE_DELAY_MIN_TICKS = 1;
    public static final int QUARTZ_PHASE_DELAY_MAX_TICKS = 16;
    public static final int QUARTZ_PHASE_DELAY_DEFAULT_TICKS = 2;

    private EngineeringParameterProfile() {}

    public static double lapisFilterAlpha(int index) {
        int bounded = EngineeringMath.clamp(index, 0, LAPIS_FILTER_ALPHA.length - 1);
        return LAPIS_FILTER_ALPHA[bounded];
    }
}
