package dev.redstoneengineering.diagnostics.events;

import net.minecraft.core.BlockPos;

/** Immutable system-event evidence captured from authoritative server transitions. */
public record SystemEventRecord(
        long tick,
        SystemEventKind kind,
        BlockPos source,
        int severity,
        String code,
        String detail
) {
    public SystemEventRecord {
        severity = Math.max(0, Math.min(3, severity));
        code = clean(code, 48);
        detail = clean(detail, 192);
    }

    public boolean abnormal() {
        return kind.abnormal() || severity >= 2;
    }

    public String compact() {
        return "T=" + tick + " " + kind + " @" + source.toShortString()
                + " S" + severity + " " + code + " | " + detail;
    }

    private static String clean(String value, int max) {
        if (value == null || value.isBlank()) return "-";
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }
}
