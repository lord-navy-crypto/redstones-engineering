package dev.redstoneengineering.metrology;

/** Coarse engineering-health classification for a measurement channel. */
public enum MeasurementQuality {
    GOOD,
    DEGRADED,
    SATURATED,
    STALE,
    INVALID
}
