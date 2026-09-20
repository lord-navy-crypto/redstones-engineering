package dev.redstoneengineering.core.port;

import dev.redstoneengineering.core.domain.EngineeringDomain;

import java.util.Objects;

/**
 * Physical or information quantity carried by an engineering port.
 *
 * <p>The transport medium ({@link EngineeringDomain}) and the carried quantity are
 * deliberately separate. Two ports may share a medium while still being semantically
 * incompatible if they represent different engineering quantities. {@link #UNSPECIFIED}
 * is a compatibility wildcard for legacy or intentionally generic ports.</p>
 */
public enum EngineeringQuantity {
    UNSPECIFIED("unspecified"),
    SIGNAL_LEVEL("signal-level"),
    TIMING("timing"),
    WAVEFORM("waveform"),
    OPTICAL_INTENSITY("optical-intensity"),
    VOLTAGE("voltage"),
    MAGNETIC_FIELD("magnetic-field"),
    TEMPERATURE("temperature"),
    INSTRUMENT_DATA("instrument-data"),
    DIGITAL_DATA("digital-data"),
    POSITION("position"),
    PRESSURE("pressure"),
    SOUL_FLUX("soul-flux");

    private final String label;

    EngineeringQuantity(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Legacy/generic ports remain connectable while explicit quantities must agree. */
    public boolean compatibleWith(EngineeringQuantity other) {
        Objects.requireNonNull(other, "other");
        return this == UNSPECIFIED || other == UNSPECIFIED || this == other;
    }

    /**
     * Backward-compatible default for existing seven-argument {@link EngineeringPort}
     * declarations. Existing blocks therefore gain quantity semantics without requiring
     * a repository-wide constructor rewrite in the same migration.
     */
    public static EngineeringQuantity defaultFor(EngineeringDomain domain, PortKind kind) {
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(kind, "kind");
        return switch (domain) {
            case REDSTONE, LAPIS -> SIGNAL_LEVEL;
            case QUARTZ -> TIMING;
            case AMETHYST, MECHANICAL_VIBRATION, HYDROACOUSTIC -> WAVEFORM;
            case OPTICAL -> OPTICAL_INTENSITY;
            case COPPER -> VOLTAGE;
            case IRON_MAGNETIC -> MAGNETIC_FIELD;
            case THERMAL, PHONON_THERMAL -> TEMPERATURE;
            case INSTRUMENT_BUS -> INSTRUMENT_DATA;
            case DATA_BUS_8, SERIAL_DATA, DIFFERENTIAL_DATA, RADIO_DATA -> DIGITAL_DATA;
            case MECHATRONIC_POSITION -> POSITION;
            case PNEUMATIC -> PRESSURE;
            case SOUL_FLUX -> SOUL_FLUX;
            case GENERIC -> UNSPECIFIED;
        };
    }
}
