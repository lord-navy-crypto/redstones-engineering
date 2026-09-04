package dev.redstoneengineering.diagnostics;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe, session-local diagnostic buffer for RSE.
 *
 * <p>This is deliberately bounded and observational. It never owns simulation state,
 * topology, sampling cadence, controller state, or persistence.</p>
 */
public final class RseDiagnostics {
    public static final int MAX_ENTRIES = 256;
    private static final int MAX_MESSAGE_CHARS = 1_200;
    private static final int MAX_THROWABLE_CHARS = 8_000;
    private static final int MAX_EXPORT_CHARS = 120_000;
    private static final Object LOCK = new Object();
    private static final ArrayDeque<RseDiagnosticEntry> ENTRIES = new ArrayDeque<>();
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS", Locale.ROOT).withZone(ZoneId.systemDefault());

    private RseDiagnostics() {
    }

    public static void record(RseDiagnosticSeverity severity, String source, String message, Throwable throwable) {
        if (severity == null) severity = RseDiagnosticSeverity.INFO;
        String safeSource = sanitize(source == null || source.isBlank() ? "unknown" : source, 180);
        String safeMessage = sanitize(message == null ? "" : message, MAX_MESSAGE_CHARS);
        String throwableSummary = throwable == null ? "" : sanitize(stackTrace(throwable), MAX_THROWABLE_CHARS);
        RseDiagnosticEntry entry = new RseDiagnosticEntry(
                SEQUENCE.incrementAndGet(),
                System.currentTimeMillis(),
                severity,
                safeSource,
                sanitize(Thread.currentThread().getName(), 120),
                safeMessage,
                throwableSummary
        );

        synchronized (LOCK) {
            while (ENTRIES.size() >= MAX_ENTRIES) {
                ENTRIES.removeFirst();
            }
            ENTRIES.addLast(entry);
        }
    }

    public static List<RseDiagnosticEntry> snapshot() {
        synchronized (LOCK) {
            return List.copyOf(new ArrayList<>(ENTRIES));
        }
    }

    public static int count(RseDiagnosticSeverity severity) {
        synchronized (LOCK) {
            int count = 0;
            for (RseDiagnosticEntry entry : ENTRIES) {
                if (entry.severity() == severity) count++;
            }
            return count;
        }
    }

    public static int size() {
        synchronized (LOCK) {
            return ENTRIES.size();
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            ENTRIES.clear();
        }
    }

    public static String exportReport(String environmentHeader) {
        StringBuilder out = new StringBuilder(Math.min(MAX_EXPORT_CHARS, 32_768));
        out.append("RSE DIAGNOSTICS REPORT\n");
        if (environmentHeader != null && !environmentHeader.isBlank()) {
            out.append(sanitize(environmentHeader, 2_000)).append('\n');
        }
        out.append("Buffer: ").append(size()).append('/').append(MAX_ENTRIES)
                .append(" | WARN: ").append(count(RseDiagnosticSeverity.WARN))
                .append(" | ERROR: ").append(count(RseDiagnosticSeverity.ERROR)).append("\n\n");

        for (RseDiagnosticEntry entry : snapshot()) {
            appendEntry(out, entry);
            if (out.length() >= MAX_EXPORT_CHARS) {
                out.append("\n[report truncated at ").append(MAX_EXPORT_CHARS).append(" characters]\n");
                break;
            }
        }
        return out.substring(0, Math.min(out.length(), MAX_EXPORT_CHARS));
    }

    public static String timeLabel(RseDiagnosticEntry entry) {
        return TIME_FORMAT.format(Instant.ofEpochMilli(entry.epochMillis()));
    }

    private static void appendEntry(StringBuilder out, RseDiagnosticEntry entry) {
        out.append('[').append(timeLabel(entry)).append("] [")
                .append(entry.severity()).append("] [")
                .append(entry.threadName()).append("] ")
                .append(entry.source()).append(" - ")
                .append(entry.message()).append('\n');
        if (!entry.throwableSummary().isBlank()) {
            out.append(entry.throwableSummary()).append('\n');
        }
    }

    private static String stackTrace(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private static String sanitize(String value, int maxChars) {
        String cleaned = value.replace('\r', ' ').replace("\u0000", "");
        String home = System.getProperty("user.home", "");
        if (!home.isBlank()) cleaned = cleaned.replace(home, "~");
        if (cleaned.length() <= maxChars) return cleaned;
        return cleaned.substring(0, Math.max(0, maxChars - 14)) + " …[truncated]";
    }
}
