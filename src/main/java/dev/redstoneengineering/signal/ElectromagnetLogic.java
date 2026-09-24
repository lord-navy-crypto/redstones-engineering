package dev.redstoneengineering.signal;

/**
 * Pure bounded electromagnet model: finite inductive field response plus thermal derating.
 */
public final class ElectromagnetLogic {
    private ElectromagnetLogic() {}

    /** Energization rises more slowly than field collapse to make inductive response visible. */
    public static int stepField(int actualField, int targetField) {
        return stepFieldRate(actualField, targetField, 2, 3);
    }

    public static int stepFieldRate(int actualField, int targetField, int riseRate, int fallRate) {
        int actual = Math.max(0, Math.min(15, actualField));
        int target = Math.max(0, Math.min(15, targetField));
        int rise = Math.max(1, Math.min(15, riseRate));
        int fall = Math.max(1, Math.min(15, fallRate));
        if (target > actual) return Math.min(target, actual + rise);
        if (target < actual) return Math.max(target, actual - fall);
        return actual;
    }

    /**
     * Thermal load is a gameplay I²-like proxy in 0..1000. Stronger excitation heats
     * disproportionately; de-energized coils cool substantially faster.
     */
    public static int nextThermal(int thermalLoad, int excitation) {
        return nextThermal(thermalLoad, excitation, 20);
    }

    /**
     * Cooling rate is an engineering heat-rejection parameter in thermal units/tick.
     * The historical model used 20 when de-energized and 4 while energized; a cooling
     * rate of 20 therefore reproduces the Alpha 1.0.21 baseline.
     */
    public static int nextThermal(int thermalLoad, int excitation, int coolingRate) {
        int thermal = Math.max(0, Math.min(1000, thermalLoad));
        int field = Math.max(0, Math.min(15, excitation));
        int cooling = Math.max(1, Math.min(40, coolingRate));
        if (field <= 0) return Math.max(0, thermal - cooling);
        int heating = Math.max(1, (field * field + 7) / 8);
        int energizedCooling = Math.max(1, cooling / 5);
        return Math.max(0, Math.min(1000, thermal + heating - energizedCooling));
    }

    /** Thermal protection limits achievable field before the coil reaches destructive temperature. */
    public static int deratedTarget(int commandedField, int thermalLoad) {
        int target = Math.max(0, Math.min(15, commandedField));
        int thermal = Math.max(0, Math.min(1000, thermalLoad));
        if (thermal >= 850) return Math.min(target, 6);
        if (thermal >= 700) return Math.min(target, 10);
        return target;
    }

    public static String thermalState(int thermalLoad) {
        int thermal = Math.max(0, Math.min(1000, thermalLoad));
        if (thermal >= 850) return "HOT / DERATED";
        if (thermal >= 700) return "WARM / DERATED";
        if (thermal >= 400) return "WARM";
        return "NORMAL";
    }
}
