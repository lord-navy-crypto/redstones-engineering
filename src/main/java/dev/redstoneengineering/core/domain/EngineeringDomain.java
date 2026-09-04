package dev.redstoneengineering.core.domain;

/**
 * First-class engineering domains carried by RSE ports and diagnostics.
 *
 * <p>The enum names stay stable for source compatibility while {@link #label()}
 * exposes the more explicit player-facing engineering medium.</p>
 */
public enum EngineeringDomain {
    REDSTONE("INSULATED_REDSTONE"),
    LAPIS("LAPIS_PRECISION"),
    QUARTZ("QUARTZ_TIMING"),
    AMETHYST("AMETHYST_RESONANCE"),
    OPTICAL("OPTICAL"),
    COPPER("COPPER"),
    IRON_MAGNETIC("IRON_MAGNETIC"),
    THERMAL("THERMAL"),
    INSTRUMENT_BUS("INSTRUMENT_BUS"),
    DATA_BUS_8("DATA_BUS_8"),
    SERIAL_DATA("SERIAL_DATA"),
    DIFFERENTIAL_DATA("DIFFERENTIAL_DATA"),
    RADIO_DATA("RADIO_DATA"),
    PNEUMATIC("PNEUMATIC"),
    GENERIC("GENERIC");

    private final String label;

    EngineeringDomain(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
