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
     * Continuous-time RC-equivalent pole frequency from tau, at nominal 20 TPS.
     *
     * <p>This is provenance for the exponential mapping alpha=1-exp(-dt/tau);
     * it is not necessarily the sampled filter's actual -3 dB frequency when
     * the pole approaches Nyquist.</p>
     */
    public static double lapisFilterCutoffHzNominal(int index) {
        double tauTicks = lapisFilterTimeConstantTicks(index);
        if (tauTicks <= 0.0) return Double.POSITIVE_INFINITY;
        return NOMINAL_TICKS_PER_SECOND / (2.0 * Math.PI * tauTicks);
    }

    /**
     * Exact -3 dB frequency of the implemented one-pole discrete filter.
     *
     * <p>For H(z)=alpha/(1-(1-alpha)z^-1), solve |H(e^jw)|^2=1/2.
     * Some fast settings never fall by 3 dB before Nyquist; those return NaN
     * rather than reporting a physically unreachable cutoff.</p>
     */
    public static double lapisFilterDiscreteCutoffHzNominal(int index) {
        double alpha = lapisFilterAlpha(index);
        if (alpha >= 1.0) return Double.NaN;
        double pole = 1.0 - alpha;
        double cosOmega = (1.0 + pole * pole - 2.0 * alpha * alpha) / (2.0 * pole);
        if (cosOmega < -1.0 || cosOmega > 1.0) return Double.NaN;
        double omega = Math.acos(cosOmega);
        double sampleRateHz = NOMINAL_TICKS_PER_SECOND / LAPIS_FILTER_SAMPLE_PERIOD_TICKS;
        return omega * sampleRateHz / (2.0 * Math.PI);
    }

    public static double lapisFilterNyquistHzNominal() {
        return NOMINAL_TICKS_PER_SECOND / (2.0 * LAPIS_FILTER_SAMPLE_PERIOD_TICKS);
    }
}
