package dev.redstoneengineering.signal;

/** Pure parameter/decay contract for the Honey vibration damper. */
public final class HoneyVibrationDamperLogic {
    public static final int MIN_ATTENUATION = 1;
    public static final int MAX_ATTENUATION = 15;
    public static final int DEFAULT_ATTENUATION = 4;
    public static final int PACKET_TTL_TICKS = 4;
    public static final int INITIAL_ENVELOPE_QUALITY = 80;
    public static final int QUALITY_DECAY_PER_STEP = 20;

    private HoneyVibrationDamperLogic() {}

    public static int boundedAttenuation(int attenuation) {
        return Math.max(MIN_ATTENUATION, Math.min(MAX_ATTENUATION, attenuation));
    }

    public static int attenuatedAmplitude(int amplitude, int attenuation) {
        return Math.max(0, amplitude - boundedAttenuation(attenuation));
    }

    public static int degradedQuality(int quality) {
        return Math.max(0, quality - QUALITY_DECAY_PER_STEP);
    }
}
