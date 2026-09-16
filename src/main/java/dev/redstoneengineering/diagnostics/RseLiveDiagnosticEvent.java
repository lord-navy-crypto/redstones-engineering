package dev.redstoneengineering.diagnostics;

/** Immutable structured event retained by the observer-only RSE live diagnostics hub. */
public record RseLiveDiagnosticEvent(
        long sequence,
        long epochMillis,
        long gameTick,
        RseDiagnosticSeverity severity,
        RseLiveDiagnostics.Domain domain,
        String source,
        String eventType,
        String message,
        String dimension,
        String position,
        String oldState,
        String newState,
        String reasonCode,
        String upstream
) {
    public RseLiveDiagnosticEvent {
        severity = severity == null ? RseDiagnosticSeverity.INFO : severity;
        domain = domain == null ? RseLiveDiagnostics.Domain.SYSTEM : domain;
        source = safe(source, "unknown");
        eventType = safe(eventType, "EVENT");
        message = safe(message, "");
        dimension = safe(dimension, "");
        position = safe(position, "");
        oldState = safe(oldState, "");
        newState = safe(newState, "");
        reasonCode = safe(reasonCode, "");
        upstream = safe(upstream, "");
    }

    private static String safe(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
