package dev.redstoneengineering.signal;

/** Pure finite-rate spool motion for the pneumatic proportional valve. */
public final class PneumaticProportionalValveLogic {
    public static final int MIN_OPENING = 0;
    public static final int MAX_OPENING = 15;
    public static final int MIN_RESPONSE_RATE = 1;
    public static final int MAX_RESPONSE_RATE = 15;

    private PneumaticProportionalValveLogic() {}

    public static int boundedOpening(int opening) {
        return Math.max(MIN_OPENING, Math.min(MAX_OPENING, opening));
    }

    public static int boundedResponseRate(int rate) {
        return Math.max(MIN_RESPONSE_RATE, Math.min(MAX_RESPONSE_RATE, rate));
    }

    public static int responseRate(int responseMode) {
        return switch (Math.max(0, Math.min(2, responseMode))) {
            case 0 -> 1;
            case 1 -> 2;
            default -> 4;
        };
    }

    public static int stepOpening(int actualOpening, int commandedOpening, int responseMode) {
        return stepOpeningRate(actualOpening, commandedOpening, responseRate(responseMode));
    }

    public static int stepOpeningRate(int actualOpening, int commandedOpening, int responseRate) {
        int actual = boundedOpening(actualOpening);
        int command = boundedOpening(commandedOpening);
        int rate = boundedResponseRate(responseRate);
        if (command > actual) return Math.min(command, actual + rate);
        if (command < actual) return Math.max(command, actual - rate);
        return actual;
    }

    public static int trackingError(int actualOpening, int commandedOpening) {
        return boundedOpening(commandedOpening) - boundedOpening(actualOpening);
    }

    public static String modeName(int responseMode) {
        return switch (Math.max(0, Math.min(2, responseMode))) {
            case 0 -> "SOFT";
            case 1 -> "NORMAL";
            default -> "FAST";
        };
    }
}
