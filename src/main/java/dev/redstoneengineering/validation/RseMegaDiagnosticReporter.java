package dev.redstoneengineering.validation;

import dev.redstoneengineering.diagnostics.RseDiagnosticSeverity;
import dev.redstoneengineering.diagnostics.RseLiveDiagnostics;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Formats, attributes, logs and exports one live Mega Factory diagnosis. */
public final class RseMegaDiagnosticReporter {
    private static final Logger LOGGER = LogManager.getLogger(RseMegaDiagnosticReporter.class);
    private static final Path DIAGNOSTIC_DIR = Path.of("run/rse-diagnostics");
    private static final Path LATEST_FILE = DIAGNOSTIC_DIR.resolve("mega-latest.txt");
    private static final Path LATEST_TMP = DIAGNOSTIC_DIR.resolve("mega-latest.tmp");
    private static final Path HISTORY_FILE = DIAGNOSTIC_DIR.resolve("mega-history.log");

    public record DiagnosticReport(
            List<Component> chatLines,
            List<String> textLines,
            List<Integer> rootBlockers,
            List<Integer> cascadeStations
    ) {}

    private RseMegaDiagnosticReporter() {}

    /**
     * Stable command boundary for full-factory diagnosis.
     *
     * <p>The service remains authoritative for live evaluation and phase semantics. This wrapper
     * preserves every service line, derives root/cascade attribution from the D01-D40 section,
     * publishes the same observation into the red-cross live hub, and exports it for copy/paste.</p>
     */
    public static RseValidationFactoryService.Result diagnose(ServerLevel level) {
        RseValidationFactoryService.Result base = RseMegaValidationService.diagnose(level);
        if (level == null || !base.success()) return base;

        RseMegaValidationSavedData data = RseMegaValidationSavedData.get(level);
        RseMegaValidationSavedData.Placement placement = data.placement();
        if (placement == null) return base;

        ArrayList<String> original = new ArrayList<>();
        for (Component component : base.lines()) original.add(component.getString());

        LinkedHashMap<Integer, String> stationLines = extractStationLines(original);
        LinkedHashSet<Integer> abnormal = new LinkedHashSet<>();
        for (Map.Entry<Integer, String> entry : stationLines.entrySet()) {
            if (isAbnormalStationLine(entry.getValue())) abnormal.add(entry.getKey());
        }

        ArrayList<Integer> roots = new ArrayList<>();
        ArrayList<Integer> cascades = new ArrayList<>();
        for (Integer station : abnormal) {
            Integer upstream = RseMegaValidationTopology.upstreamStation(station);
            if (upstream != null && abnormal.contains(upstream)) cascades.add(station);
            else roots.add(station);
        }

        String phase = parseHeaderValue(original, "PHASE:", "|");
        String master = parseHeaderValue(original, "MASTER:", "|");
        if (phase.isBlank()) phase = "UNKNOWN";
        if (master.isBlank()) master = "UNKNOWN";
        LinkedHashMap<String, String> cellSummary = extractCellSummary(original);

        ArrayList<String> lines = new ArrayList<>();
        lines.add("MEGA FACTORY FULL DIAGNOSIS");
        lines.add("RUN=" + data.runNumber() + " | PHASE=" + phase + " | MASTER=" + master
                + " | GAME_TICK=" + level.getGameTime());
        lines.add("generated=" + Instant.now());
        lines.add("");
        lines.add("===== ROOT BLOCKERS =====");
        appendPhaseGateBlockers(lines, original);
        if (roots.isEmpty() && lines.get(lines.size() - 1).equals("===== ROOT BLOCKERS =====")) lines.add("NONE");
        for (Integer station : roots) lines.add(stationLines.get(station));

        lines.add("");
        lines.add("===== CASCADE =====");
        if (cascades.isEmpty()) lines.add("NONE");
        for (Integer station : cascades) {
            Integer upstream = RseMegaValidationTopology.upstreamStation(station);
            lines.add("D" + two(station) + " <- D" + two(upstream == null ? 0 : upstream)
                    + " | " + stationLines.get(station));
        }

        appendSection(lines, original, "===== CELL SUMMARY =====", "===== ALL 40 DUT =====");
        appendSection(lines, original, "===== ALL 40 DUT =====", "===== HISTORY =====");
        appendSection(lines, original, "===== HISTORY =====", "===== ACCEPTANCE =====");
        appendSection(lines, original, "===== ACCEPTANCE =====", null);
        lines.add("diagnosticFiles=run/rse-diagnostics/mega-latest.txt, run/rse-diagnostics/mega-history.log, run/rse-diagnostics/rse-live-latest.txt");

        publishParsedStationHealth(level, placement, stationLines);
        RseLiveDiagnostics.publishMegaSnapshot(
                data.runNumber(), phase, master, roots, cascades, cellSummary, abnormal.size());
        emitBackendAndFiles(lines);
        RseLiveDiagnostics.exportLatest("Mega Factory run=" + data.runNumber() + " phase=" + phase + " master=" + master,
                level.getGameTime());

        ArrayList<Component> chat = new ArrayList<>(lines.size());
        for (String line : lines) chat.add(Component.literal(line));
        return new RseValidationFactoryService.Result(true, chat);
    }

    public static DiagnosticReport buildAndExport(
            ServerLevel level,
            RseMegaValidationSavedData data,
            RseMegaValidationSavedData.Placement placement,
            RseMegaValidationService.Phase phase,
            long age,
            Map<Integer, RseMegaStationEvaluator.StationEvaluation> stations,
            Map<String, RseValidationSelfTestService.Evaluation> cells,
            RseValidationSelfTestService.Evaluation gate,
            RseValidationSelfTestService.Verdict master,
            long enduranceRequiredTicks
    ) {
        LinkedHashSet<Integer> abnormal = new LinkedHashSet<>();
        for (RseMegaValidationTopology.Station spec : RseMegaValidationTopology.stations()) {
            RseMegaStationEvaluator.StationEvaluation evaluation = stations.get(spec.number());
            if (evaluation == null || evaluation.scenarioVerdict() == RseValidationSelfTestService.Verdict.PASS) continue;
            abnormal.add(spec.number());
        }

        ArrayList<Integer> roots = new ArrayList<>();
        ArrayList<Integer> cascades = new ArrayList<>();
        for (Integer station : abnormal) {
            Integer upstream = RseMegaValidationTopology.upstreamStation(station);
            if (upstream != null && abnormal.contains(upstream)) cascades.add(station);
            else roots.add(station);
        }

        LinkedHashMap<String, String> cellSummary = new LinkedHashMap<>();
        for (char c = 'A'; c <= 'H'; c++) {
            String cell = String.valueOf(c);
            RseValidationSelfTestService.Evaluation evaluation = cells.get(cell);
            cellSummary.put(cell, evaluation == null ? "UNKNOWN" : evaluation.verdict() + " | " + evaluation.detail());
        }

        ArrayList<String> lines = new ArrayList<>();
        lines.add("MEGA FACTORY FULL DIAGNOSIS");
        lines.add("RUN=" + data.runNumber() + " | PHASE=" + phase + " | AGE=" + age + "t | MASTER=" + master);
        lines.add("generated=" + Instant.now());
        lines.add("");
        lines.add("===== ROOT BLOCKERS =====");
        if (gate.verdict() != RseValidationSelfTestService.Verdict.PASS) {
            lines.add("PHASE GATE " + gate.verdict() + " | " + gate.detail());
        }
        if (roots.isEmpty() && gate.verdict() == RseValidationSelfTestService.Verdict.PASS) lines.add("NONE");
        for (Integer station : roots) lines.add(stationLine(station, stations.get(station)));

        lines.add("");
        lines.add("===== CASCADE =====");
        if (cascades.isEmpty()) lines.add("NONE");
        for (Integer station : cascades) {
            Integer upstream = RseMegaValidationTopology.upstreamStation(station);
            lines.add("D" + two(station) + " <- D" + two(upstream == null ? 0 : upstream)
                    + " | " + stationLine(station, stations.get(station)));
        }

        lines.add("");
        lines.add("===== CELL SUMMARY =====");
        for (Map.Entry<String, String> entry : cellSummary.entrySet()) lines.add("CELL " + entry.getKey() + " " + entry.getValue());

        lines.add("");
        lines.add("===== ALL 40 DUT =====");
        long liveHealthy = 0L;
        long liveFail = 0L;
        long liveWait = 0L;
        for (RseMegaValidationTopology.Station spec : RseMegaValidationTopology.stations()) {
            RseMegaStationEvaluator.StationEvaluation evaluation = stations.get(spec.number());
            if (evaluation == null) continue;
            if (evaluation.health() == RseMegaStationEvaluator.Health.HEALTHY) liveHealthy++;
            if (evaluation.scenarioVerdict() == RseValidationSelfTestService.Verdict.FAIL) liveFail++;
            if (evaluation.scenarioVerdict() == RseValidationSelfTestService.Verdict.WAIT) liveWait++;
            lines.add(stationLine(spec.number(), evaluation));

            RseDiagnosticSeverity severity = switch (evaluation.scenarioVerdict()) {
                case PASS -> RseDiagnosticSeverity.INFO;
                case WAIT -> RseDiagnosticSeverity.WARN;
                case FAIL -> RseDiagnosticSeverity.ERROR;
            };
            RseLiveDiagnostics.refreshDevice(
                    "D" + two(spec.number()) + "/" + spec.name(),
                    spec.blockId(),
                    RseLiveDiagnostics.Domain.VALIDATION,
                    level.dimension().location().toString(),
                    evaluation.worldPos().toShortString(),
                    level.getGameTime(),
                    evaluation.quality() == null ? "UNKNOWN" : evaluation.quality().name(),
                    severity,
                    evaluation.reasonCode() + " | " + evaluation.detail()
            );
        }

        lines.add("");
        lines.add("===== HISTORY =====");
        int historyCount = 0;
        for (int station = 1; station <= RseMegaValidationTopology.STATION_COUNT; station++) {
            if (!data.stationEverFailed(station)) continue;
            RseMegaValidationTopology.Station spec = RseMegaValidationTopology.station(station);
            lines.add(String.format(Locale.ROOT,
                    "D%02d %s first failure: %s | %s",
                    station, spec.name(), data.firstFailurePhase(station), data.firstFailureDetail(station)));
            historyCount++;
        }
        if (historyCount == 0) lines.add("No recorded station failures.");

        lines.add("");
        lines.add("===== ACCEPTANCE =====");
        lines.add("completedPhases=" + Long.bitCount(data.completedPhases()) + "/20"
                + " stationsEverPassed=" + data.stationsEverPassedCount() + "/40"
                + " liveHealthy=" + liveHealthy + "/40"
                + " liveFail=" + liveFail
                + " liveWait=" + liveWait);
        lines.add("currentRoots=" + roots.size()
                + " cascades=" + cascades.size()
                + " storedCurrentFailures=" + data.stationsCurrentlyFailedCount()
                + " endurance=" + data.enduranceHealthyTicks() + "/" + enduranceRequiredTicks + "t");
        lines.add("origin=" + placement.origin().toShortString());

        RseLiveDiagnostics.publishMegaSnapshot(
                data.runNumber(), phase.name(), master.name(), roots, cascades, cellSummary, abnormal.size());
        emitBackendAndFiles(lines);

        ArrayList<Component> chat = new ArrayList<>(lines.size());
        for (String line : lines) chat.add(Component.literal(line));
        return new DiagnosticReport(List.copyOf(chat), List.copyOf(lines), List.copyOf(roots), List.copyOf(cascades));
    }

    private static LinkedHashMap<Integer, String> extractStationLines(List<String> lines) {
        LinkedHashMap<Integer, String> stations = new LinkedHashMap<>();
        boolean inStations = false;
        for (String line : lines) {
            if ("===== ALL 40 DUT =====".equals(line)) {
                inStations = true;
                continue;
            }
            if (inStations && line.startsWith("=====")) break;
            if (!inStations || line.length() < 3 || line.charAt(0) != 'D') continue;
            try {
                int station = Integer.parseInt(line.substring(1, 3));
                if (station >= 1 && station <= RseMegaValidationTopology.STATION_COUNT) stations.put(station, line);
            } catch (NumberFormatException ignored) {
                // Non-station diagnostic text is preserved elsewhere.
            }
        }
        return stations;
    }

    private static boolean isAbnormalStationLine(String line) {
        return line != null && (line.contains(" FAIL |") || line.contains(" WAIT |"));
    }

    private static LinkedHashMap<String, String> extractCellSummary(List<String> lines) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        boolean inCells = false;
        for (String line : lines) {
            if ("===== CELL SUMMARY =====".equals(line)) {
                inCells = true;
                continue;
            }
            if (inCells && line.startsWith("=====")) break;
            if (!inCells || !line.startsWith("CELL ") || line.length() < 7) continue;
            result.put(line.substring(5, 6), line.substring(7));
        }
        return result;
    }

    private static void appendPhaseGateBlockers(List<String> target, List<String> original) {
        boolean inBlockers = false;
        for (String line : original) {
            if ("===== CURRENT BLOCKERS =====".equals(line)) {
                inBlockers = true;
                continue;
            }
            if (inBlockers && line.startsWith("=====")) break;
            if (!inBlockers || line.isBlank() || line.startsWith("D") || line.startsWith("CELL ")) continue;
            if (!"NONE".equals(line)) target.add(line);
        }
    }

    private static void appendSection(List<String> target, List<String> source, String heading, String nextHeading) {
        int start = source.indexOf(heading);
        if (start < 0) return;
        target.add("");
        target.add(heading);
        for (int i = start + 1; i < source.size(); i++) {
            String line = source.get(i);
            if (nextHeading != null && nextHeading.equals(line)) break;
            target.add(line);
        }
    }

    private static String parseHeaderValue(List<String> lines, String key, String delimiter) {
        for (String line : lines) {
            int index = line.indexOf(key);
            if (index < 0) continue;
            String value = line.substring(index + key.length()).trim();
            int end = value.indexOf(delimiter);
            if (end >= 0) value = value.substring(0, end).trim();
            return value;
        }
        return "";
    }

    private static void publishParsedStationHealth(
            ServerLevel level,
            RseMegaValidationSavedData.Placement placement,
            Map<Integer, String> stationLines
    ) {
        for (Map.Entry<Integer, String> entry : stationLines.entrySet()) {
            int station = entry.getKey();
            String line = entry.getValue();
            RseMegaValidationTopology.Station spec = RseMegaValidationTopology.station(station);
            if (spec == null) continue;
            RseDiagnosticSeverity severity = line.contains(" FAIL |") ? RseDiagnosticSeverity.ERROR
                    : line.contains(" WAIT |") ? RseDiagnosticSeverity.WARN : RseDiagnosticSeverity.INFO;
            String quality = inferQuality(line);
            RseLiveDiagnostics.refreshDevice(
                    "D" + two(station) + "/" + spec.name(),
                    spec.blockId(),
                    RseLiveDiagnostics.Domain.VALIDATION,
                    level.dimension().location().toString(),
                    RseMegaValidationTopology.stationWorldPos(placement.origin(), spec).toShortString(),
                    level.getGameTime(),
                    quality,
                    severity,
                    line
            );
        }
    }

    private static String inferQuality(String line) {
        for (String quality : List.of("TOPOLOGY_ERROR", "DOMAIN_MISMATCH", "NO_SIGNAL", "SATURATED", "STALE", "FAULT", "VALID")) {
            if (line.contains(quality)) return quality;
        }
        return line.contains(" PASS |") ? "VALID" : "UNKNOWN";
    }

    private static String stationLine(int station, RseMegaStationEvaluator.StationEvaluation evaluation) {
        RseMegaValidationTopology.Station spec = RseMegaValidationTopology.station(station);
        if (spec == null) return "D" + two(station) + " UNKNOWN";
        if (evaluation == null) return "D" + two(station) + " " + spec.name() + " UNKNOWN | no live evaluation";
        return String.format(Locale.ROOT,
                "D%02d %s %s | health=%s q=%s reason=%s | %s | pos=%s",
                station,
                spec.name(),
                evaluation.scenarioVerdict(),
                evaluation.health(),
                evaluation.quality(),
                evaluation.reasonCode(),
                evaluation.detail(),
                evaluation.worldPos().toShortString());
    }

    private static void emitBackendAndFiles(List<String> lines) {
        for (String line : lines) LOGGER.info("[RSE-MEGA-DIAG] {}", line);
        String report = String.join(System.lineSeparator(), lines) + System.lineSeparator();
        try {
            Files.createDirectories(DIAGNOSTIC_DIR);
            Files.writeString(LATEST_TMP, report, StandardCharsets.UTF_8);
            try {
                Files.move(LATEST_TMP, LATEST_FILE,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(LATEST_TMP, LATEST_FILE, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(HISTORY_FILE,
                    "\n===== DIAGNOSIS " + Instant.now() + " =====\n" + report,
                    StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException exception) {
            LOGGER.warn("[RSE-MEGA-DIAG] diagnostic export failed: {}", exception.toString());
        }
    }

    private static String two(int value) {
        return String.format(Locale.ROOT, "%02d", value);
    }
}
