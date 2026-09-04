package dev.redstoneengineering.diagnostics.acceptance;

/** Stable diagnostic issue code plus human-readable detail for acceptance traceability. */
public record EngineeringAcceptanceIssue(String code, String detail) {
    public EngineeringAcceptanceIssue {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("issue code is required");
        detail = detail == null ? "" : detail;
    }
}
