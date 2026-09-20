package dev.redstoneengineering.core.port;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Small engineering-unit registry used for port compatibility and safe conversion.
 *
 * <p>Recognized physical units are compared by canonical unit family rather than by
 * raw label. Prefix variants therefore remain compatible (for example V/mV and
 * Pa/kPa) while physically unrelated recognized units do not. Legacy/custom labels
 * remain compatibility-wildcards until their owning blocks are explicitly migrated,
 * preserving the existing RSE network contracts during the retrofit.</p>
 */
public final class EngineeringUnits {
    private record UnitDefinition(String canonical, double toCanonical) {}

    private static final Map<String, UnitDefinition> DEFINITIONS = new HashMap<>();

    static {
        // SI decimal-prefix examples used by the current electrical / pneumatic retrofit.
        register("V", "V", 1.0);
        register("mV", "V", 1.0e-3);
        register("kV", "V", 1.0e3);

        register("Pa", "Pa", 1.0);
        register("kPa", "Pa", 1.0e3);
        register("MPa", "Pa", 1.0e6);
    }

    private EngineeringUnits() {}

    private static void register(String symbol, String canonical, double toCanonical) {
        DEFINITIONS.put(symbol, new UnitDefinition(canonical, toCanonical));
    }

    private static String normalize(String unit) {
        if (unit == null || unit.isBlank()) return "unitless";
        return unit.trim().replace('μ', 'µ');
    }

    /**
     * Returns true when the two labels can safely share one physical-unit family.
     * Unknown/custom labels intentionally remain permissive for backward compatibility.
     */
    public static boolean compatible(String left, String right) {
        String a = normalize(left);
        String b = normalize(right);
        if (a.equals(b)) return true;

        UnitDefinition leftDefinition = DEFINITIONS.get(a);
        UnitDefinition rightDefinition = DEFINITIONS.get(b);
        if (leftDefinition == null || rightDefinition == null) {
            return true; // legacy/custom descriptors remain compatibility-wildcard until migrated
        }
        return leftDefinition.canonical().equals(rightDefinition.canonical());
    }

    /**
     * Multiplicative factor that converts a numeric value in {@code from} into {@code to}.
     * Empty means the conversion is not defined by this registry.
     */
    public static OptionalDouble conversionFactor(String from, String to) {
        String a = normalize(from);
        String b = normalize(to);
        if (a.equals(b)) return OptionalDouble.of(1.0);

        UnitDefinition source = DEFINITIONS.get(a);
        UnitDefinition target = DEFINITIONS.get(b);
        if (source == null || target == null || !source.canonical().equals(target.canonical())) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(source.toCanonical() / target.toCanonical());
    }
}
