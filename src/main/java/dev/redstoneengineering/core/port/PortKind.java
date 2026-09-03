package dev.redstoneengineering.core.port;

/** Semantic purpose of an engineering port, independent of signal direction. */
public enum PortKind {
    REDSTONE_ANALOG,
    REDSTONE_BINARY,
    ELECTRICAL,
    SENSOR,
    CONTROL,
    TRIGGER,
    ENABLE,
    RESET,
    TAP,
    BUS,
    MEASUREMENT,
    CONVERTER,
    ACTUATOR,
    FEEDBACK,
    AUXILIARY,
    SAFETY
}
