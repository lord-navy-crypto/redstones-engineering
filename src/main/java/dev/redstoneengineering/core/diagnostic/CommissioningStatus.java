package dev.redstoneengineering.core.diagnostic;

/**
 * Shared server-authoritative commissioning outcome used by engineering HMIs.
 *
 * <p>The status is deliberately coarse: devices should expose the evidence that
 * produced the result rather than hiding physical behavior behind a score.</p>
 */
public enum CommissioningStatus {
    NOT_READY,
    PASS,
    MARGINAL,
    FAIL;

    public int code() {
        return ordinal();
    }

    public static CommissioningStatus fromCode(int code) {
        CommissioningStatus[] values = values();
        return code < 0 || code >= values.length ? NOT_READY : values[code];
    }
}
