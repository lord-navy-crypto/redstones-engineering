package dev.redstoneengineering.signal;

/**
 * Pure bounded electromagnet model: finite inductive field response plus thermal derating.
 */
public final class ElectromagnetLogic {
    public static final int MIN_FIELD = 0;
    public static final int MAX_FIELD = 15;
    public static final int MIN_RESPONSE_RATE = 1;
    public static final int MAX_RESPONSE_RATE = 15;
    public static final int DEFAULT_RISE_RATE = 2;
    public static final int DEFAULT_FALL_RATE = 3;
    public static final int MIN_COOLING_RATE = 1;
    public static final int MAX_COOLING_RATE = 40;
    public static final int DEFAULT_COOLING_RATE = 20;
    public static final int MIN_THERMAL_LOAD = 0;
    public static final int MAX_THERMAL_LOAD = 1000;
    public static final int WARM_STATE_THRESHOLD = 400;
    public static final int WARM_DERATE_THRESHOLD = 700;
    public static final int HOT_DERATE_THRESHOLD = 850;
    public static final int WARM_FIELD_CAP = 10;
    public static final int HOT_FIELD_CAP = 6;
    public static final int ENERGIZED_COOLING_DIVISOR = 5;

    private ElectromagnetLogic() {}

    public static int boundedField(int field) {
        return Math.max(MIN_FIELD, Math.min(MAX_FIELD, field));
    }

    public static int boundedResponseRate(int rate) {
        return Math.max(MIN_RESPONSE_RATE, Math.min(MAX_RESPONSE_RATE, rate));
    }

    public static int boundedCoolingRate(int rate) {
        return Math.max(MIN_COOLING_RATE, Math.min(MAX_COOLING_RATE, rate));
    }

    public static int boundedThermalLoad(int thermalLoad) {
        return Math.max(MIN_THERMAL_LOAD, Math.min(MAX_THERMAL_LOAD, thermalLoad));
    }

    public static boolean isThermallyDerated(int thermalLoad) {
        return boundedThermalLoad(thermalLoad) >= WARM_DERATE_THRESHOLD;
    }

    /** Energization rises more slowly than field collapse to make inductive response visible. */
    public static int stepField(int actualField, int targetField) {
        return stepFieldRate(actualField, targetField, DEFAULT_RISE_RATE, DEFAULT_FALL_RATE);
    }

    public static int stepFieldRate(int actualField, int targetField, int riseRate, int fallRate) {
        int actual = boundedField(actualField);
        int target = boundedField(targetField);
        int rise = boundedResponseRate(riseRate);
        int fall = boundedResponseRate(fallRate);
        if (target > actual) return Math.min(target, actual + rise);
        if (target < actual) return Math.max(target, actual - fall);
        return actual;
    }

    /**
     * Thermal load is a gameplay I²-like proxy in 0..1000. Stronger excitation heats
     * disproportionately; de-energized coils cool substantially faster.
     */
    public static int nextThermal(int thermalLoad, int excitation) {
        return nextThermal(thermalLoad, excitation, DEFAULT_COOLING_RATE);
    }

    /**
     * Cooling rate is an engineering heat-rejection parameter in thermal units/tick.
     * The historical model used 20 when de-energized and 4 while energized; a cooling
     * rate of 20 therefore reproduces the Alpha 1.0.21 baseline.
     */
    public static int nextThermal(int thermalLoad, int excitation, int coolingRate) {
        int thermal = boundedThermalLoad(thermalLoad);
        int field = boundedField(excitation);
        int cooling = boundedCoolingRate(coolingRate);
        if (field <= 0) return Math.max(0, thermal - cooling);
        int heating = Math.max(1, (field * field + 7) / 8);
        int energizedCooling = Math.max(1, cooling / ENERGIZED_COOLING_DIVISOR);
        return boundedThermalLoad(thermal + heating - energizedCooling);
    }

    /** Thermal protection limits achievable field before the coil reaches destructive temperature. */
    public static int deratedTarget(int commandedField, int thermalLoad) {
        int target = boundedField(commandedField);
        int thermal = boundedThermalLoad(thermalLoad);
        if (thermal >= HOT_DERATE_THRESHOLD) return Math.min(target, HOT_FIELD_CAP);
        if (thermal >= WARM_DERATE_THRESHOLD) return Math.min(target, WARM_FIELD_CAP);
        return target;
    }

    public static String thermalState(int thermalLoad) {
        int thermal = boundedThermalLoad(thermalLoad);
        if (thermal >= HOT_DERATE_THRESHOLD) return "HOT / DERATED";
        if (thermal >= WARM_DERATE_THRESHOLD) return "WARM / DERATED";
        if (thermal >= WARM_STATE_THRESHOLD) return "WARM";
        return "NORMAL";
    }
}
