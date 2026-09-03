package dev.redstoneengineering.core.port;

/** Runtime quality/state classification for engineering observations. */
public enum PortQuality {
    VALID,
    NO_SIGNAL,
    SATURATED,
    STALE,
    FAULT,
    DOMAIN_MISMATCH,
    TOPOLOGY_ERROR
}
