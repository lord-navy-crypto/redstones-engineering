package dev.redstoneengineering.physics;

public final class EngineeringMath {
    private EngineeringMath() {}

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static int approach(int current, int target, int maxStep) {
        if (current == target) return current;
        int delta = target - current;
        int step = Math.min(Math.abs(delta), Math.max(1, maxStep));
        return current + Integer.signum(delta) * step;
    }

    public static int ema100(int previous, int input, int alphaIndex) {
        double alpha = switch (alphaIndex) {
            case 0 -> 0.10;
            case 1 -> 0.25;
            case 2 -> 0.50;
            default -> 0.75;
        };
        return clamp((int)Math.round(previous + alpha * (input - previous)), 0, 100);
    }

    public static int opticalAfterLoss(int intensity, int loss) {
        return clamp(intensity - Math.max(0, loss), 0, 15);
    }
}
