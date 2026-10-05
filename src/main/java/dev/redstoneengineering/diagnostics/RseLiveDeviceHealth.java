package dev.redstoneengineering.diagnostics;

/** Current observer-only health snapshot for one active monitored RSE device. */
public record RseLiveDeviceHealth(
        String source,
        String blockId,
        RseLiveDiagnostics.Domain domain,
        String dimension,
        String position,
        long lastSeenTick,
        String quality,
        RseDiagnosticSeverity severity,
        String detail
) {
    public RseLiveDeviceHealth {
        source = source == null || source.isBlank() ? "unknown" : source;
        blockId = blockId == null ? "" : blockId;
        domain = domain == null ? RseLiveDiagnostics.Domain.SYSTEM : domain;
        dimension = dimension == null ? "" : dimension;
        position = position == null ? "" : position;
        quality = quality == null || quality.isBlank() ? "UNKNOWN" : quality;
        severity = severity == null ? RseDiagnosticSeverity.INFO : severity;
        detail = detail == null ? "" : detail;
    }
}
