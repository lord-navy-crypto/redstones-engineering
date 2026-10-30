package dev.redstoneengineering.block;

import dev.redstoneengineering.core.signal.SignalMath;
import dev.redstoneengineering.physics.EngineeringMath;

/**
 * Pure transfer predictions shared by the 0..15 slew filter and the 0..100
 * first-order low-pass. Distinct units and laws remain explicit.
 * Predictions assume constant, valid input; no unsolicited world sampling.
 */
final class FilterResponseSupport {
    private FilterResponseSupport() {}

    static int slewStep(int previous, int input, int rate) {
        return SignalMath.approach(previous, input, rate);
    }

    static int slewTicksToTarget(int current, int target, int rate) {
        if (rate <= 0) throw new IllegalArgumentException("rate must be > 0");
        int residual = Math.abs(target - current);
        return (residual + rate - 1) / rate;
    }

    static int lapisLowPassStep(int previous, int input, double alpha) {
        if (!(alpha > 0.0 && alpha <= 1.0)) {
            throw new IllegalArgumentException("0 < alpha <= 1 required");
        }
        return EngineeringMath.clamp(
                (int) Math.round(previous + alpha * (input - previous)), 0, 100);
    }

    static int predictedNextLapis(int current, int input, int alphaIndex) {
        return lapisLowPassStep(current, input, LapisLowPassFilterBlock.alpha(alphaIndex));
    }

    /** Geometric 90% response, not a guaranteed discrete rounded settle time. */
    static int nominalNinetyPercentSamples(double alpha) {
        if (!(alpha > 0.0 && alpha <= 1.0)) {
            throw new IllegalArgumentException("0 < alpha <= 1 required");
        }
        return alpha >= 1.0 ? 1 : (int) Math.ceil(Math.log(0.1) / Math.log1p(-alpha));
    }
}
