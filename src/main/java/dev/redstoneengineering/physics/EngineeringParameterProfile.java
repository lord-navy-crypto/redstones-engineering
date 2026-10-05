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
    public static final double NOMINAL_TICKS_PER_SECOND = 20.0;

    public static final int LAPIS_FILTER_ALPHA_STEPS = 8;
    public static final int LAPIS_FILTER_DEFAULT_INDEX = 2;
    public static final int LAPIS_FILTER_SAMPLE_PERIOD_TICKS = 2;
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

    /**
     * Whether the selected first-order filter coefficient is an explicit bypass.
     *
     * <p>alpha=1 gives y[n]=x[n], so no finite time constant or cutoff frequency
     * should be presented to the player.</p>
     */
    public static boolean lapisFilterBypass(int index) {
        return lapisFilterAlpha(index) >= 1.0;
    }

    /**
     * Exact exponential-equivalent time constant for the discrete update
     * y[n] = y[n-1] + alpha * (x[n] - y[n-1]).
     *
     * <p>For 0&lt;alpha&lt;1, alpha = 1-exp(-dt/tau), hence
     * tau = -dt / ln(1-alpha). Returned units are Minecraft ticks.</p>
     */
    public static double lapisFilterTimeConstantTicks(int index) {
        double alpha = lapisFilterAlpha(index);
        if (alpha >= 1.0) return 0.0;
        return -LAPIS_FILTER_SAMPLE_PERIOD_TICKS / Math.log1p(-alpha);
    }

    /**
     * Nominal -3 dB cutoff frequency implied by the exponential-equivalent
     * time constant, assuming the normal 20 game-tick/s cadence.
     */
    public static double lapisFilterCutoffHzNominal(int index) {
        double tauTicks = lapisFilterTimeConstantTicks(index);
        if (tauTicks <= 0.0) return Double.POSITIVE_INFINITY;
        return NOMINAL_TICKS_PER_SECOND / (2.0 * Math.PI * tauTicks);
    }
}
