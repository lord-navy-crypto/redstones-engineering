package dev.redstoneengineering.diagnostics.topology;

/** Read-only classification of what an engineering port sees on one physical face. */
public enum TopologyLinkStatus {
    CONNECTED,
    OPEN,
    ISOLATED,
    DOMAIN_MISMATCH,
    DIRECTION_MISMATCH,
    UNLOADED
}
