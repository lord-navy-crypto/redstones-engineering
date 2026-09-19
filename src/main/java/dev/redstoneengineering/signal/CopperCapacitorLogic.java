package dev.redstoneengineering.signal;

/**
 * Pure macroscopic storage model for the copper capacitor.
 *
 * A valid source drives charge toward its voltage using the selected capacitance time constant.
 * When the source is absent, downstream resistance controls discharge; an open circuit retains
 * charge longest and decays only through modeled leakage.
 */
public final class CopperCapacitorLogic {
    private CopperCapacitorLogic() {}

    public static int chargeTau(int capacitanceIndex) {
        return switch (Math.max(0, Math.min(3, capacitanceIndex))) {
            case 0 -> 2;
            case 1 -> 4;
            case 2 -> 8;
            default -> 16;
        };
    }

    public static int dischargeTau(int capacitanceIndex, double loadResistance) {
        int base = chargeTau(capacitanceIndex);
        if (Double.isInfinite(loadResistance)) return base * 8;
        double boundedLoad = Math.max(0.25, Math.min(32.0, loadResistance));
        int loadMultiplier = Math.max(1, (int) Math.round(boundedLoad / 4.0));
        return Math.max(base, base * loadMultiplier);
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
