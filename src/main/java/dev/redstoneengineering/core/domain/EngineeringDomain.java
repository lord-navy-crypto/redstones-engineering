package dev.redstoneengineering.core.domain;

/** Stable engineering-domain identifiers used by RSE ports and diagnostics. */
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
    MECHANICAL_VIBRATION("MECHANICAL_VIBRATION"),
    PNEUMATIC("PNEUMATIC"),
    GENERIC("GENERIC");

    private final String id;

    EngineeringDomain(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
