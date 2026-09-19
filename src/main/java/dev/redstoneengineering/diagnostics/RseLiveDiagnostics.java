package dev.redstoneengineering.diagnostics;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Observer-only structured telemetry hub for RSE.
 *
 * <p>The registry is deliberately bounded. Producers explicitly publish state; this class never
 * scans worlds, loads chunks, or mutates simulation/controller state.</p>
 */
public final class RseLiveDiagnostics {
    public static final int MAX_EVENTS = 512;
    public static final int MAX_ACTIVE_DEVICES = 512;
    public static final long DEVICE_STALE_TICKS = 200L;
    private static final long COALESCE_TICKS = 20L;
    private static final long COALESCE_MILLIS = 1_500L;
    private static final int MAX_TEXT = 1_600;
    private static final Path DIAGNOSTIC_DIR = Path.of("run/rse-diagnostics");
    private static final Path LATEST_FILE = DIAGNOSTIC_DIR.resolve("rse-live-latest.txt");
    private static final Path LATEST_TMP = DIAGNOSTIC_DIR.resolve("rse-live-latest.tmp");
    private static final Logger LOGGER = LogManager.getLogger(RseLiveDiagnostics.class);
    private static final Object LOCK = new Object();
    private static final ArrayDeque<RseLiveDiagnosticEvent> EVENTS = new ArrayDeque<>();
    private static final LinkedHashMap<String, RseLiveDeviceHealth> DEVICES = new LinkedHashMap<>();
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static MegaSnapshot megaSnapshot;
    private static ValidationRunSnapshot validationRunSnapshot;

    public enum Domain {
        ANALOG,
        DIGITAL,
        OPTICAL,
        PNEUMATIC,
        CONTROL,
        ROBOTICS,
        OPERATIONS,
        VALIDATION,
        SYSTEM
    }

    public record Summary(
            int activeDevices,
            Map<String, Integer> qualityCounts,
            Map<Domain, Integer> domainCounts,
            Map<Domain, Integer> unhealthyByDomain,
            int warnEvents,
            int errorEvents,
            RseLiveDiagnosticEvent latestAbnormal
    ) {}

    public record MegaSnapshot(
            int runNumber,
            String phase,
            String master,
            List<Integer> rootBlockers,
            List<Integer> cascades,
            Map<String, String> cellSummary,
            int abnormalStations,
            long epochMillis
    ) {
        public MegaSnapshot {
            phase = safe(phase, "UNKNOWN");
            master = safe(master, "UNKNOWN");
            rootBlockers = List.copyOf(rootBlockers == null ? List.of() : rootBlockers);
            cascades = List.copyOf(cascades == null ? List.of() : cascades);
            cellSummary = Collections.unmodifiableMap(new LinkedHashMap<>(cellSummary == null ? Map.of() : cellSummary));
        }
    }

    /** Latest copy/paste-ready result from the integrated manual commissioning bench. */
    public record ValidationRunSnapshot(
            String runId,
            String command,
            String overall,
            List<String> feedbackLines,
            List<String> logLines,
            long gameTick,
            long epochMillis
    ) {
        public ValidationRunSnapshot {
            runId = safe(runId, "UNSET");
            command = safe(command, "unknown");
            overall = safe(overall, "WAIT");
            feedbackLines = List.copyOf(feedbackLines == null ? List.of() : feedbackLines);
            logLines = List.copyOf(logLines == null ? List.of() : logLines);
        }
    }

    private RseLiveDiagnostics() {}

    public static void recordEvent(RseLiveDiagnosticEvent event) {
        if (event == null) return;
        synchronized (LOCK) {
            if (shouldCoalesce(event)) return;
            while (EVENTS.size() >= MAX_EVENTS) EVENTS.removeFirst();
            EVENTS.addLast(event);
        }
    }

    public static void recordEvent(
            long gameTick,
            RseDiagnosticSeverity severity,
            Domain domain,
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
        recordEvent(new RseLiveDiagnosticEvent(
                SEQUENCE.incrementAndGet(),
                System.currentTimeMillis(),
                gameTick,
                severity,
                domain,
                trim(source),
                trim(eventType),
                trim(message),
                trim(dimension),
                trim(position),
                trim(oldState),
                trim(newState),
                trim(reasonCode),
                trim(upstream)
        ));
    }

    public static void recordLogEvent(
            RseDiagnosticSeverity severity,
            String source,
            String message,
            Throwable throwable
    ) {
        String reason = throwable == null ? "LOG" : "LOG_THROWABLE_" + throwable.getClass().getSimpleName();
        recordEvent(-1L, severity, Domain.SYSTEM, source, "LOG_EVENT", message,
                "", "", "", "", reason, "");
    }

    public static void refreshDevice(RseLiveDeviceHealth health) {
        if (health == null) return;
        synchronized (LOCK) {
            RseLiveDeviceHealth previous = DEVICES.put(health.source(), health);
            while (DEVICES.size() > MAX_ACTIVE_DEVICES) {
                String oldest = DEVICES.keySet().iterator().next();
                DEVICES.remove(oldest);
            }
            if (previous != null
                    && (!previous.quality().equals(health.quality()) || previous.severity() != health.severity())) {
                recordEvent(health.lastSeenTick(), health.severity(), health.domain(), health.source(),
                        "HEALTH_CHANGE", health.detail(), health.dimension(), health.position(),
                        previous.quality(), health.quality(), "DEVICE_HEALTH_CHANGE", "");
            }
        }
    }

    public static void refreshDevice(
            String source,
            String blockId,
            Domain domain,
            String dimension,
            String position,
            long tick,
            String quality,
            RseDiagnosticSeverity severity,
            String detail
    ) {
        refreshDevice(new RseLiveDeviceHealth(source, blockId, domain, dimension, position,
                tick, quality, severity, detail));
    }

    public static List<RseLiveDiagnosticEvent> snapshotEvents() {
        synchronized (LOCK) {
            return List.copyOf(new ArrayList<>(EVENTS));
        }
    }

    public static List<RseLiveDeviceHealth> snapshotDevices(long currentTick) {
        synchronized (LOCK) {
            pruneStaleLocked(currentTick);
            return List.copyOf(new ArrayList<>(DEVICES.values()));
        }
    }

    public static void pruneStale(long currentTick) {
        synchronized (LOCK) {
            pruneStaleLocked(currentTick);
        }
    }

    private static void pruneStaleLocked(long currentTick) {
        if (currentTick < 0L) return;
        DEVICES.entrySet().removeIf(entry -> {
            long seen = entry.getValue().lastSeenTick();
            return seen >= 0L && currentTick - seen > DEVICE_STALE_TICKS;
        });
    }

    public static Summary summary(long currentTick) {
        List<RseLiveDeviceHealth> devices = snapshotDevices(currentTick);
        LinkedHashMap<String, Integer> qualities = new LinkedHashMap<>();
        EnumMap<Domain, Integer> domains = new EnumMap<>(Domain.class);
        EnumMap<Domain, Integer> unhealthy = new EnumMap<>(Domain.class);
        for (RseLiveDeviceHealth device : devices) {
            qualities.merge(device.quality(), 1, Integer::sum);
            domains.merge(device.domain(), 1, Integer::sum);
            if (device.severity() != RseDiagnosticSeverity.INFO
                    || !"VALID".equalsIgnoreCase(device.quality())) {
                unhealthy.merge(device.domain(), 1, Integer::sum);
            }
        }

        int warn = 0;
        int error = 0;
        RseLiveDiagnosticEvent latestAbnormal = null;
        for (RseLiveDiagnosticEvent event : snapshotEvents()) {
            if (event.severity() == RseDiagnosticSeverity.WARN) warn++;
            if (event.severity() == RseDiagnosticSeverity.ERROR) error++;
            if (event.severity() != RseDiagnosticSeverity.INFO) latestAbnormal = event;
        }
        return new Summary(
                devices.size(),
                Collections.unmodifiableMap(qualities),
                Collections.unmodifiableMap(domains),
                Collections.unmodifiableMap(unhealthy),
                warn,
                error,
                latestAbnormal
        );
    }

    public static void publishMegaSnapshot(
            int runNumber,
            String phase,
            String master,
            List<Integer> rootBlockers,
            List<Integer> cascades,
            Map<String, String> cellSummary,
            int abnormalStations
    ) {
        MegaSnapshot next = new MegaSnapshot(runNumber, phase, master, rootBlockers, cascades,
                cellSummary, abnormalStations, System.currentTimeMillis());
        MegaSnapshot previous;
        synchronized (LOCK) {
            previous = megaSnapshot;
            megaSnapshot = next;
        }
        String previousState = previous == null ? "" : previous.phase() + "/" + previous.master();
        String newState = next.phase() + "/" + next.master();
        if (previous == null || !previousState.equals(newState)
                || !previous.rootBlockers().equals(next.rootBlockers())
                || previous.abnormalStations() != next.abnormalStations()) {
            RseDiagnosticSeverity severity = "FAIL".equalsIgnoreCase(master)
                    ? RseDiagnosticSeverity.ERROR
                    : "WAIT".equalsIgnoreCase(master) ? RseDiagnosticSeverity.WARN : RseDiagnosticSeverity.INFO;
            recordEvent(-1L, severity, Domain.VALIDATION, "mega-factory", "MEGA_STATE_CHANGE",
                    "run=" + runNumber + " phase=" + phase + " master=" + master
                            + " root=" + rootBlockers + " cascade=" + cascades
                            + " abnormalStations=" + abnormalStations,
                    "", "", previousState, newState, "MEGA_STATE", "");
        }
    }

    public static MegaSnapshot latestMegaSnapshot() {
        synchronized (LOCK) {
            return megaSnapshot;
        }
    }

    public static void publishValidationRun(
            String runId,
            String command,
            String overall,
            List<String> feedbackLines,
            List<String> logLines,
            long gameTick
    ) {
        ValidationRunSnapshot next = new ValidationRunSnapshot(
                runId, command, overall, feedbackLines, logLines, gameTick, System.currentTimeMillis());
        synchronized (LOCK) {
            validationRunSnapshot = next;
        }
    }

    public static ValidationRunSnapshot latestValidationRun() {
        synchronized (LOCK) {
            return validationRunSnapshot;
        }
    }

    public static String exportReport(String environmentHeader, long currentTick) {
        Summary summary = summary(currentTick);
        StringBuilder out = new StringBuilder(48_000);
        out.append("RSE LIVE DIAGNOSTICS REPORT\n");
        out.append("generated=").append(Instant.now()).append('\n');
        if (environmentHeader != null && !environmentHeader.isBlank()) {
            out.append(trim(environmentHeader)).append('\n');
        }
        out.append("activeDevices=").append(summary.activeDevices())
                .append(" warnEvents=").append(summary.warnEvents())
                .append(" errorEvents=").append(summary.errorEvents()).append('\n');
        out.append("qualityCounts=").append(summary.qualityCounts()).append('\n');
        out.append("domainCounts=").append(summary.domainCounts()).append('\n');
        out.append("unhealthyByDomain=").append(summary.unhealthyByDomain()).append("\n\n");

        ValidationRunSnapshot validation = latestValidationRun();
        out.append("===== INTEGRATED DEMO FEEDBACK =====\n");
        if (validation == null) {
            out.append("no integrated demo run published in this session\n");
        } else {
            out.append("runId=").append(validation.runId())
                    .append(" command=").append(validation.command())
                    .append(" overall=").append(validation.overall())
                    .append(" tick=").append(validation.gameTick()).append('\n');
            for (String line : validation.feedbackLines()) out.append(line).append('\n');
            out.append("\n===== INTEGRATED DEMO RUN LOG =====\n");
            for (String line : validation.logLines()) out.append(line).append('\n');
        }
        out.append('\n');

        MegaSnapshot mega = latestMegaSnapshot();
        out.append("===== MEGA FACTORY =====\n");
        if (mega == null) {
            out.append("no Mega telemetry published in this session\n");
        } else {
            out.append("run=").append(mega.runNumber())
                    .append(" phase=").append(mega.phase())
                    .append(" master=").append(mega.master())
                    .append(" abnormalStations=").append(mega.abnormalStations()).append('\n');
            out.append("rootBlockers=").append(mega.rootBlockers()).append('\n');
            out.append("cascades=").append(mega.cascades()).append('\n');
            out.append("cells=").append(mega.cellSummary()).append('\n');
        }

        out.append("\n===== ACTIVE DEVICES =====\n");
        for (RseLiveDeviceHealth device : snapshotDevices(currentTick)) {
            out.append("[RSE-LIVE] device=").append(device.source())
                    .append(" domain=").append(device.domain())
                    .append(" quality=").append(device.quality())
                    .append(" severity=").append(device.severity())
                    .append(" pos=").append(device.position())
                    .append(" detail=").append(oneLine(device.detail())).append('\n');
        }

        out.append("\n===== RECENT EVENTS =====\n");
        for (RseLiveDiagnosticEvent event : snapshotEvents()) {
            out.append("[RSE-LIVE]")
                    .append(" seq=").append(event.sequence())
                    .append(" tick=").append(event.gameTick())
                    .append(" domain=").append(event.domain())
                    .append(" source=").append(event.source())
                    .append(" event=").append(event.eventType())
                    .append(" severity=").append(event.severity())
                    .append(" old=").append(oneLine(event.oldState()))
                    .append(" new=").append(oneLine(event.newState()))
                    .append(" reason=").append(oneLine(event.reasonCode()))
                    .append(" upstream=").append(oneLine(event.upstream()))
                    .append(" message=").append(oneLine(event.message()))
                    .append('\n');
        }
        return out.toString();
    }

    public static Path exportLatest(String environmentHeader, long currentTick) {
        try {
            Files.createDirectories(DIAGNOSTIC_DIR);
            Files.writeString(LATEST_TMP, exportReport(environmentHeader, currentTick), StandardCharsets.UTF_8);
            try {
                Files.move(LATEST_TMP, LATEST_FILE,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(LATEST_TMP, LATEST_FILE, StandardCopyOption.REPLACE_EXISTING);
            }
            return LATEST_FILE;
        } catch (IOException exception) {
            LOGGER.warn("[RSE-LIVE] Failed to export {}: {}", LATEST_FILE, exception.toString());
            return null;
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            EVENTS.clear();
            DEVICES.clear();
            megaSnapshot = null;
            validationRunSnapshot = null;
        }
    }

    private static boolean shouldCoalesce(RseLiveDiagnosticEvent next) {
        RseLiveDiagnosticEvent previous = EVENTS.peekLast();
        if (previous == null) return false;
        if (!coalesceKey(previous).equals(coalesceKey(next))) return false;
        if (next.gameTick() >= 0L && previous.gameTick() >= 0L) {
            return next.gameTick() - previous.gameTick() <= COALESCE_TICKS;
        }
        return next.epochMillis() - previous.epochMillis() <= COALESCE_MILLIS;
    }

    private static String coalesceKey(RseLiveDiagnosticEvent event) {
        return event.source() + '|' + event.eventType() + '|' + event.newState() + '|' + event.reasonCode();
    }

    private static String trim(String value) {
        String safe = value == null ? "" : value.replace('\r', ' ').replace("\u0000", "");
        if (safe.length() <= MAX_TEXT) return safe;
        return safe.substring(0, MAX_TEXT - 14) + " …[truncated]";
    }

    private static String oneLine(String value) {
        return trim(value).replace('\n', ' ').replace('\t', ' ').trim();
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
