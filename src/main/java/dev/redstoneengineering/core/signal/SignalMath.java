package dev.redstoneengineering.core.signal;

import dev.redstoneengineering.signal.EngineeringSignal;

public final class SignalMath {
    private SignalMath() {}

    public static int clamp(int value) {
        return EngineeringSignal.clamp(value);
    }

    public static int gain(int input, double gain) {
        return clamp((int) Math.round(input * gain));
    }

    public static int offset(int input, int offset) {
        return clamp(input + offset);
    }

    public static int threshold(int input, int threshold) {
        return input >= clamp(threshold) ? clamp(input) : 0;
    }

    public static int mapRange(int input, int inMin, int inMax, int outMin, int outMax) {
        if (inMax <= inMin) {
            return clamp(outMin);
        }

        double t = (input - inMin) / (double) (inMax - inMin);
        t = Math.max(0.0, Math.min(1.0, t));

        return clamp((int) Math.round(outMin + t * (outMax - outMin)));
    }

    public static int approach(int current, int target, int step) {
        step = Math.max(1, step);

        if (current < target) {
            return Math.min(target, current + step);
        }

        if (current > target) {
            return Math.max(target, current - step);
        }

        return current;
    }
}
