package dev.redstoneengineering.diagnostics;

/** Immutable diagnostic event retained for the current client session. */
public record RseDiagnosticEntry(
        long sequence,
        long epochMillis,
        RseDiagnosticSeverity severity,
        String source,
        String threadName,
        String message,
        String throwableSummary
) {
}
