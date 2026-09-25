package dev.redstoneengineering.signal;

/** Pure gain/headroom contract for the Redstone Signal Amplifier. */
public final class SignalAmplifierLogic {
    public static final int MIN_SIGNAL = 0;
    public static final int MAX_SIGNAL = 15;

    public static final int MIN_GAIN = 1;
    public static final int MAX_GAIN = 8;

    public static final int MIN_LEGACY_GAIN_MODE = 0;
    public static final int MAX_LEGACY_GAIN_MODE = 3;
    public static final int DEFAULT_LEGACY_GAIN_MODE = 1;
    public static final int LEGACY_GAIN_X1 = 1;
    public static final int LEGACY_GAIN_X2 = 2;
    public static final int LEGACY_GAIN_X3 = 3;
    public static final int LEGACY_GAIN_X4 = 4;

    private SignalAmplifierLogic() {}

    public static int boundedSignal(int value) {
        return Math.max(MIN_SIGNAL, Math.min(MAX_SIGNAL, value));
    }

    public static int boundedGain(int gain) {
        return Math.max(MIN_GAIN, Math.min(MAX_GAIN, gain));
    }

    public static int boundedLegacyGainMode(int mode) {
        return Math.max(MIN_LEGACY_GAIN_MODE, Math.min(MAX_LEGACY_GAIN_MODE, mode));
    }

    public static int gainForLegacyMode(int mode) {
        return switch (boundedLegacyGainMode(mode)) {
            case 0 -> LEGACY_GAIN_X1;
            case 1 -> LEGACY_GAIN_X2;
            case 2 -> LEGACY_GAIN_X3;
            default -> LEGACY_GAIN_X4;
        };
    }

    public static int rawOutput(int input, int gain) {
        return boundedSignal(input) * boundedGain(gain);
    }

    public static int output(int input, int gain) {
        return Math.min(MAX_SIGNAL, rawOutput(input, gain));
    }

    public static boolean clipping(int input, int gain) {
        return rawOutput(input, gain) > MAX_SIGNAL;
    }
}
