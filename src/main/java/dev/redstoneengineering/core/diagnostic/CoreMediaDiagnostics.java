package dev.redstoneengineering.core.diagnostic;

/**
 * Deterministic diagnostics for the core Redstone/Lapis representation boundary.
 *
 * <p>This class does not simulate a second signal model. It only describes the
 * information consequences of the existing 0..15 Redstone and 0..100 Lapis
 * representations, using the same round-to-nearest transfer already used by
 * the converter blocks.</p>
 */
public final class CoreMediaDiagnostics {
    private CoreMediaDiagnostics() {}

    public static int lapisFromRedstone(int redstone) {
        return Math.round(clamp(redstone, 0, 15) * 100.0f / 15.0f);
    }

    public static int redstoneFromLapis(int lapis) {
        return Math.round(clamp(lapis, 0, 100) * 15.0f / 100.0f);
    }

    public static int lapisReconstructedFromRedstone(int redstone) {
        return lapisFromRedstone(redstone);
    }

    /** Absolute Lapis-domain error introduced by quantizing a 0..100 value to 0..15. */
    public static int quantizationError(int lapis) {
        int clamped = clamp(lapis, 0, 100);
        return Math.abs(clamped - lapisReconstructedFromRedstone(redstoneFromLapis(clamped)));
    }

    /** True when a valid Lapis value contains detail not exactly representable in Redstone 0..15. */
    public static boolean hasSubRedstoneDetail(int lapis) {
        return quantizationError(lapis) != 0;
    }

    /**
     * Redstone -> Lapis scaling can increase numeric resolution but never source information.
     * Returns the spacing between the neighboring Redstone-derived Lapis codes around a level.
     */
    public static int sourceCodeSpacing(int redstone) {
        int r = clamp(redstone, 0, 15);
        if (r == 15) return lapisFromRedstone(15) - lapisFromRedstone(14);
        return lapisFromRedstone(r + 1) - lapisFromRedstone(r);
    }

    public static String lapisInformationClass(int lapis) {
        return hasSubRedstoneDetail(lapis) ? "SUB-REDSTONE DETAIL PRESENT" : "REDSTONE-EXACT CODE";
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
