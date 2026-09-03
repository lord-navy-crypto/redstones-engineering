package dev.redstoneengineering.signal;

public final class EngineeringSignal {
    public static final int MIN = 0;
    public static final int MAX = 15;

    private final int strength;

    private EngineeringSignal(int strength) {
        this.strength = clamp(strength);
    }

    public static EngineeringSignal of(int strength) {
        return new EngineeringSignal(strength);
    }

    public int strength() {
        return strength;
    }

    public double normalized() {
        return strength / 15.0;
    }

    public static int clamp(int value) {
        return Math.max(MIN, Math.min(MAX, value));
    }
}
