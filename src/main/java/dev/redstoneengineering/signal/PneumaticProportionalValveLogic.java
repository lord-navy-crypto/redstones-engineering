package dev.redstoneengineering.signal;

/** Pure finite-rate spool motion for the pneumatic proportional valve. */
public final class PneumaticProportionalValveLogic {
    private PneumaticProportionalValveLogic() {}

    public static int responseRate(int responseMode) {
        return switch (Math.max(0, Math.min(2, responseMode))) {
            case 0 -> 1;
            case 1 -> 2;
            default -> 4;
        };
    }

    public static int stepOpening(int actualOpening, int commandedOpening, int responseMode) {
        int actual = Math.max(0, Math.min(15, actualOpening));
        int command = Math.max(0, Math.min(15, commandedOpening));
        int rate = responseRate(responseMode);
        if (command > actual) return Math.min(command, actual + rate);
        if (command < actual) return Math.max(command, actual - rate);
        return actual;
    }

    public static int trackingError(int actualOpening, int commandedOpening) {
        return Math.max(0, Math.min(15, commandedOpening))
                - Math.max(0, Math.min(15, actualOpening));
    }

    public static String modeName(int responseMode) {
        return switch (Math.max(0, Math.min(2, responseMode))) {
            case 0 -> "SOFT";
            case 1 -> "NORMAL";
            default -> "FAST";
        };
    }
}
