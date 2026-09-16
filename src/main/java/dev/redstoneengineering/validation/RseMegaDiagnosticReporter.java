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
        if (roots.isEmpty() && gate.verdict() == RseValidationSelfTestService.Verdict.PASS) {
            lines.add("NONE");
        }
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
        for (Map.Entry<String, String> entry : cellSummary.entrySet()) {
            lines.add("CELL " + entry.getKey() + " " + entry.getValue());
        }

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
