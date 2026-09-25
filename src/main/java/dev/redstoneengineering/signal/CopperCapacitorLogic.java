package dev.redstoneengineering.signal;

/**
 * Pure macroscopic storage model for the copper capacitor.
 *
 * A valid source drives charge toward its voltage using the selected capacitance time constant.
 * When the source is absent, downstream resistance controls discharge; an open circuit retains
 * charge longest and decays only through modeled leakage.
 */
public final class CopperCapacitorLogic {
    public static final int MIN_CAPACITANCE_INDEX = 0;
    public static final int MAX_CAPACITANCE_INDEX = 3;
    public static final int DEFAULT_CAPACITANCE_INDEX = 1;
    public static final int MIN_BASE_TAU = 1;
    public static final int MAX_BASE_TAU = 64;
    public static final int MIN_LEAKAGE_FACTOR = 2;
    public static final int MAX_LEAKAGE_FACTOR = 16;
    public static final int DEFAULT_LEAKAGE_FACTOR = 8;

    private CopperCapacitorLogic() {}

    public static int boundedCapacitanceIndex(int index) {
        return Math.max(MIN_CAPACITANCE_INDEX, Math.min(MAX_CAPACITANCE_INDEX, index));
    }

    public static int boundedBaseTau(int baseTau) {
        return Math.max(MIN_BASE_TAU, Math.min(MAX_BASE_TAU, baseTau));
    }

    public static int boundedLeakageFactor(int leakageFactor) {
        return Math.max(MIN_LEAKAGE_FACTOR, Math.min(MAX_LEAKAGE_FACTOR, leakageFactor));
    }

    public static int chargeTau(int capacitanceIndex) {
        return switch (boundedCapacitanceIndex(capacitanceIndex)) {
            case 0 -> 2;
            case 1 -> 4;
            case 2 -> 8;
            default -> 16;
        };
    }

    public static int dischargeTau(int capacitanceIndex, double loadResistance) {
        return dischargeTauBase(chargeTau(capacitanceIndex), loadResistance);
    }

    public static int dischargeTauBase(int baseTau, double loadResistance) {
        return dischargeTauBase(baseTau, loadResistance, DEFAULT_LEAKAGE_FACTOR);
    }

    public static int dischargeTauBase(int baseTau, double loadResistance, int leakageFactor) {
        int base = boundedBaseTau(baseTau);
        int leakage = boundedLeakageFactor(leakageFactor);
        if (Double.isInfinite(loadResistance)) return base * leakage;
        double boundedLoad = Math.max(0.25, Math.min(32.0, loadResistance));
        int loadMultiplier = Math.max(1, (int) Math.round(boundedLoad / 4.0));
        return Math.max(base, base * loadMultiplier);
    }

    public static int stepChargeBaseTau(
            int chargePercent, int inputVoltage, boolean inputValid,
            int baseTau, double loadResistance
    ) {
        return stepChargeBaseTau(
                chargePercent, inputVoltage, inputValid, baseTau, loadResistance,
                DEFAULT_LEAKAGE_FACTOR);
    }

    public static int stepChargeBaseTau(
            int chargePercent, int inputVoltage, boolean inputValid,
            int baseTau, double loadResistance, int leakageFactor
    ) {
        int charge = Math.max(0, Math.min(100, chargePercent));
        int target = inputValid
                ? (int) Math.round(Math.max(0, Math.min(15, inputVoltage)) / 15.0 * 100.0)
                : 0;
        int tau = inputValid ? boundedBaseTau(baseTau)
                : dischargeTauBase(baseTau, loadResistance, leakageFactor);
        int delta = target - charge;
        if (delta == 0) return charge;
        int step = Math.max(1, Math.abs(delta) / Math.max(1, tau));
        return Math.max(0, Math.min(100, charge + Integer.signum(delta) * step));
    }

    public static int stepCharge(
            int chargePercent,
            int inputVoltage,
            boolean inputValid,
            int capacitanceIndex,
            double loadResistance
    ) {
        int charge = Math.max(0, Math.min(100, chargePercent));
        int target = inputValid
                ? (int) Math.round(Math.max(0, Math.min(15, inputVoltage)) / 15.0 * 100.0)
                : 0;
        int tau = inputValid
                ? chargeTau(capacitanceIndex)
                : dischargeTau(capacitanceIndex, loadResistance);
        int delta = target - charge;
        if (delta == 0) return charge;
        int step = Math.max(1, Math.abs(delta) / Math.max(1, tau));
        return Math.max(0, Math.min(100, charge + Integer.signum(delta) * step));
    }
}
