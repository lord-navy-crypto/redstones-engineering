package dev.redstoneengineering.signal;

/** Pure range/normalization contract for the Lapis Precision Range Sensor. */
public final class LapisPrecisionRangeSensorLogic {
    public static final int MIN_RANGE_BLOCKS = 1;
    public static final int MAX_RANGE_BLOCKS = 128;

    public static final int MIN_LEGACY_RANGE_INDEX = 0;
    public static final int MAX_LEGACY_RANGE_INDEX = 3;
    public static final int DEFAULT_LEGACY_RANGE_INDEX = 1;

    public static final int LEGACY_RANGE_SHORT = 8;
    public static final int LEGACY_RANGE_MEDIUM = 16;
    public static final int LEGACY_RANGE_LONG = 32;
    public static final int LEGACY_RANGE_EXTENDED = 64;

    public static final int MIN_NORMALIZED_OUTPUT = 0;
    public static final int MAX_NORMALIZED_OUTPUT = 100;

    private LapisPrecisionRangeSensorLogic() {}

    public static int boundedRange(int range) {
        return Math.max(MIN_RANGE_BLOCKS, Math.min(MAX_RANGE_BLOCKS, range));
    }

    public static int boundedLegacyRangeIndex(int index) {
        return Math.max(MIN_LEGACY_RANGE_INDEX, Math.min(MAX_LEGACY_RANGE_INDEX, index));
    }

    public static int rangeForLegacyIndex(int index) {
        return switch (boundedLegacyRangeIndex(index)) {
            case 0 -> LEGACY_RANGE_SHORT;
            case 1 -> LEGACY_RANGE_MEDIUM;
            case 2 -> LEGACY_RANGE_LONG;
            default -> LEGACY_RANGE_EXTENDED;
        };
    }

    public static int normalizedDistance(int distance, int maxRange) {
        int range = boundedRange(maxRange);
        int boundedDistance = Math.max(0, Math.min(range, distance));
        return Math.max(MIN_NORMALIZED_OUTPUT, Math.min(MAX_NORMALIZED_OUTPUT,
                Math.round(boundedDistance * (MAX_NORMALIZED_OUTPUT / (float) range))));
    }
}
