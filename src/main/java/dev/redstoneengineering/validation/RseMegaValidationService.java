package dev.redstoneengineering.validation;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.AlarmProcessorBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.QuartzLabOscillatorBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.SafetyInterlockBlock;
import dev.redstoneengineering.block.ServoActuatorBlock;
import dev.redstoneengineering.block.ServoPositionSensorBlock;
import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Automatic 40-DUT / eight-cell factory acceptance orchestrator for Mega Validation Factory v2.1.
 *
 * <p>This class owns lifecycle, phase sequencing, fixture stimuli, structure placement and status
 * panels. Station health semantics live in {@link RseMegaStationEvaluator}; station identity,
 * coordinates and dependencies live in {@link RseMegaValidationTopology}. The validator never
 * writes a DUT output to manufacture a PASS.</p>
 */
public final class RseMegaValidationService {
    private RseMegaValidationService() {}

    public static final int STATION_COUNT = 40;
    public static final int CELL_COUNT = 8;
    private static final int AUTO_INTERVAL_TICKS = 4;
    private static final long PROPAGATION_TIMEOUT_TICKS = RseMegaStationEvaluator.PROPAGATION_TIMEOUT_TICKS;
    private static final long ENDURANCE_REQUIRED_TICKS = 200L;

    private static final BlockPos CONTROL_OFFSET = new BlockPos(35, 0, 0);
    private static final BlockPos NORTH_SPINE_OFFSET = new BlockPos(0, 0, 17);
    private static final BlockPos CROSS_SPINE_OFFSET = new BlockPos(0, 0, 45);
    private static final BlockPos MASTER_RETEST_BUTTON = new BlockPos(58, 1, 14);

    private static final BlockPos MASTER_WAIT_POWER = new BlockPos(50, 2, 14);
    private static final BlockPos MASTER_PASS_POWER = new BlockPos(51, 2, 14);
    private static final BlockPos MASTER_FAIL_POWER = new BlockPos(52, 2, 14);
    private static final BlockPos LOCAL_CELL_WAIT_POWER = new BlockPos(13, 2, 22);
    private static final BlockPos LOCAL_CELL_PASS_POWER = new BlockPos(14, 2, 22);
    private static final BlockPos LOCAL_CELL_FAIL_POWER = new BlockPos(15, 2, 22);

    static {
        if (RseMegaValidationTopology.STATION_COUNT != STATION_COUNT
                || RseMegaValidationTopology.CELL_COUNT != CELL_COUNT) {
            throw new IllegalStateException("Mega validation topology/runtime count mismatch");
        }
    }

    public enum Phase {
        STRUCTURE_PRECHECK,
        STATION_BIST,
        CELL_ACCEPTANCE,
        CHAIN_CONTINUITY,
        NOMINAL_STARTUP,
        TIMING_TEST,
        NOISE_TEST,
        SATURATION_TEST,
        DATA_INTEGRITY_TEST,
        COMM_DEGRADATION,
        PROCESS_LOAD,
        SENSOR_FAULT,
        ACTUATOR_FAULT,
        REDUNDANCY_TEST,
        INTERLOCK_TRIP,
        SAFE_STATE,
        ACK_RESET,
        RECOVERY,
        ENDURANCE_RUN,
        FINAL_ACCEPTANCE
    }

    public static RseValidationFactoryService.Result place(ServerLevel level, BlockPos origin) {
        if (level == null || origin == null) {
            return RseValidationFactoryService.Result.fail("Mega place failed: level/origin missing");
        }
        if (level.getServer().overworld() != level) {
            return RseValidationFactoryService.Result.fail("Mega Validation Factory v2.1 requires the overworld.");
        }
        RseValidationFactoryService.Result preflight = preflight(level);
        if (!preflight.success()) return preflight;
        RseValidationFactoryService.Result placement = placeModules(level, origin);
        if (!placement.success()) return placement;

        RseMegaValidationSavedData.get(level).place(origin, level.getGameTime());
        applyBaseline(level, origin);
        updateAllPanels(level, origin, RseValidationSelfTestService.Verdict.WAIT);
        return RseValidationFactoryService.Result.ok(
                "Placed RSE Mega Validation Factory v2.1: 40 DUT / 8 cells / 20-phase acceptance.",
                "Process route: A -> B -> C -> D -> south turn -> E -> F -> G -> H.",
                "Station verdicts are live and phase-aware; NO_SIGNAL is not a normal PASS.",
                "Developer diagnostics: /rsevalidation mega diagnose | status | station <1..40> | cell <A..H> | report"
        );
    }

    public static RseValidationFactoryService.Result retest(ServerLevel level) {
        if (level == null) return RseValidationFactoryService.Result.fail("Mega retest failed: level missing");
        RseMegaValidationSavedData.Placement placement = RseMegaValidationSavedData.get(level).placement();
        if (placement == null) {
            return RseValidationFactoryService.Result.fail("Mega Validation Factory v2.1 has not been placed.");
        }
        return rebuildForRetest(level, placement.origin(), false)
                ? RseValidationFactoryService.Result.ok(
                        "Mega factory rebuilt; 40-station acceptance restarted at STRUCTURE_PRECHECK.")
                : RseValidationFactoryService.Result.fail("Mega retest rebuild failed.");
    }

    public static RseValidationFactoryService.Result status(ServerLevel level) {
        if (level == null) return RseValidationFactoryService.Result.fail("Mega status failed: level missing");
        RseMegaValidationSavedData data = RseMegaValidationSavedData.get(level);
        RseMegaValidationSavedData.Placement placement = data.placement();
        if (placement == null) {
            return RseValidationFactoryService.Result.fail("Mega Validation Factory v2.1 has not been placed.");
        }

        Phase phase = phaseOf(placement.phase());
        long age = Math.max(0L, level.getGameTime() - placement.phaseStartedTick());
        ArrayList<Component> lines = new ArrayList<>();
        lines.add(Component.literal("RSE Mega Factory v2.1 | phase=" + phase + " | age=" + age
                + "t | stationsEverPassed=" + data.stationsEverPassedCount() + "/40"));
        for (int i = 0; i < CELL_COUNT; i++) {
            String cell = String.valueOf((char) ('A' + i));
            lines.add(Component.literal("Cell " + cell + " -> " + data.cellVerdict(i)
                    + " | " + data.cellDetail(i)));
        }
        lines.add(Component.literal("Current station failures=" + data.stationsCurrentlyFailedCount()
                + " | endurance=" + data.enduranceHealthyTicks() + "/" + ENDURANCE_REQUIRED_TICKS + "t"));
        lines.add(Component.literal("Origin: " + placement.origin().toShortString()));
        return new RseValidationFactoryService.Result(true, lines);
    }

    public static RseValidationFactoryService.Result station(ServerLevel level, int number) {
        if (level == null) return RseValidationFactoryService.Result.fail("Mega station failed: level missing");
        RseMegaValidationTopology.Station spec = RseMegaValidationTopology.station(number);
        if (spec == null) return RseValidationFactoryService.Result.fail("Mega station must be 1..40.");

        RseMegaValidationSavedData data = RseMegaValidationSavedData.get(level);
        RseMegaValidationSavedData.Placement placement = data.placement();
        if (placement == null) {
            return RseValidationFactoryService.Result.fail("Mega Validation Factory v2.1 has not been placed.");
        }

        Phase phase = phaseOf(placement.phase());
        long age = Math.max(0L, level.getGameTime() - placement.phaseStartedTick());
        RseMegaStationEvaluator.StationEvaluation live = RseMegaStationEvaluator.evaluate(
                level, placement.origin(), spec, phase, age);
        ArrayList<Component> lines = new ArrayList<>();
        lines.add(Component.literal(String.format(Locale.ROOT, "D%02d %s | Cell %s",
                spec.number(), spec.name(), spec.cell())));
        lines.add(Component.literal("Live -> " + live.scenarioVerdict()
                + " | health=" + live.health()
                + " | quality=" + live.quality()
                + " | reason=" + live.reasonCode()));
        lines.add(Component.literal("Evidence -> " + live.detail()
                + " | pos=" + live.worldPos().toShortString()));
        lines.add(Component.literal("Stored -> " + data.stationVerdict(number)
                + " | " + data.stationDetail(number)));
        lines.add(Component.literal("History -> everPassed=" + data.stationEverPassed(number)
                + " everFailed=" + data.stationEverFailed(number)));
        if (data.stationEverFailed(number)) {
            lines.add(Component.literal("First failure -> " + data.firstFailurePhase(number)
                    + " | " + data.firstFailureDetail(number)));
        }
        return new RseValidationFactoryService.Result(true, lines);
    }

    public static RseValidationFactoryService.Result cell(ServerLevel level, String requestedCell) {
        if (level == null) return RseValidationFactoryService.Result.fail("Mega cell failed: level missing");
        String cell = requestedCell == null ? "" : requestedCell.trim().toUpperCase(Locale.ROOT);
        if (!RseMegaValidationTopology.cellModules().containsKey(cell)) {
            return RseValidationFactoryService.Result.fail("Mega cell must be A..H.");
        }

        RseMegaValidationSavedData data = RseMegaValidationSavedData.get(level);
        RseMegaValidationSavedData.Placement placement = data.placement();
        if (placement == null) {
            return RseValidationFactoryService.Result.fail("Mega Validation Factory v2.1 has not been placed.");
        }

        Phase phase = phaseOf(placement.phase());
        long age = Math.max(0L, level.getGameTime() - placement.phaseStartedTick());
        LinkedHashMap<Integer, RseMegaStationEvaluator.StationEvaluation> liveStations = evaluateStations(
                level, placement.origin(), phase, age);
        LinkedHashMap<String, RseValidationSelfTestService.Evaluation> liveCells = evaluateCells(
                level, placement.origin(), phase, age, liveStations);

        ArrayList<Component> lines = new ArrayList<>();
        RseValidationSelfTestService.Evaluation cellEvaluation = liveCells.get(cell);
        lines.add(Component.literal("Mega Cell " + cell + " LIVE -> "
                + cellEvaluation.verdict() + " | " + cellEvaluation.detail()));
        for (RseMegaValidationTopology.Station spec : RseMegaValidationTopology.stations()) {
            if (!spec.cell().equals(cell)) continue;
            RseMegaStationEvaluator.StationEvaluation evaluation = liveStations.get(spec.number());
            lines.add(Component.literal(String.format(Locale.ROOT,
                    "D%02d %s -> %s | health=%s | q=%s | %s",
                    spec.number(), spec.name(), evaluation.scenarioVerdict(), evaluation.health(),
                    evaluation.quality(), evaluation.detail())));
        }
        return new RseValidationFactoryService.Result(true, lines);
    }

    public static RseValidationFactoryService.Result report(ServerLevel level) {
        if (level == null) return RseValidationFactoryService.Result.fail("Mega report failed: level missing");
        RseMegaValidationSavedData data = RseMegaValidationSavedData.get(level);
        RseMegaValidationSavedData.Placement placement = data.placement();
        if (placement == null) {
            return RseValidationFactoryService.Result.fail("Mega Validation Factory v2.1 has not been placed.");
        }

        ArrayList<Component> lines = new ArrayList<>();
        Phase phase = phaseOf(placement.phase());
        lines.add(Component.literal("MEGA FACTORY ACCEPTANCE REPORT | current phase=" + phase));
        lines.add(Component.literal("Stations passed at least once: " + data.stationsEverPassedCount()
                + "/40 | unresolved failures=" + data.stationsCurrentlyFailedCount()));
        int completed = Long.bitCount(data.completedPhases());
        lines.add(Component.literal("Completed phases: " + completed + "/20 | endurance healthy="
                + data.enduranceHealthyTicks() + "/" + ENDURANCE_REQUIRED_TICKS + "t"));
        for (int station = 1; station <= STATION_COUNT; station++) {
            if (!data.stationEverFailed(station)) continue;
            RseMegaValidationTopology.Station spec = RseMegaValidationTopology.station(station);
            lines.add(Component.literal(String.format(Locale.ROOT,
                    "D%02d %s first failure: %s | %s",
                    station, spec.name(), data.firstFailurePhase(station), data.firstFailureDetail(station))));
        }
        return new RseValidationFactoryService.Result(true, lines);
    }

    public static RseValidationFactoryService.Result diagnose(ServerLevel level) {
        if (level == null) return RseValidationFactoryService.Result.fail("Mega diagnose failed: level missing");
        RseMegaValidationSavedData data = RseMegaValidationSavedData.get(level);
        RseMegaValidationSavedData.Placement placement = data.placement();
        if (placement == null) {
            return RseValidationFactoryService.Result.fail("Mega Validation Factory v2.1 has not been placed.");
        }

        Phase phase = phaseOf(placement.phase());
        long age = Math.max(0L, level.getGameTime() - placement.phaseStartedTick());
        LinkedHashMap<Integer, RseMegaStationEvaluator.StationEvaluation> stations = evaluateStations(
                level, placement.origin(), phase, age);
        LinkedHashMap<String, RseValidationSelfTestService.Evaluation> cells = evaluateCells(
                level, placement.origin(), phase, age, stations);
        RseValidationSelfTestService.Evaluation gate = phaseGate(
                level, placement.origin(), phase, age, stations, cells, data);

        boolean failed = gate.verdict() == RseValidationSelfTestService.Verdict.FAIL
                || stations.values().stream().anyMatch(e -> e.scenarioVerdict() == RseValidationSelfTestService.Verdict.FAIL)
                || cells.values().stream().anyMatch(e -> e.verdict() == RseValidationSelfTestService.Verdict.FAIL);
        boolean waiting = gate.verdict() == RseValidationSelfTestService.Verdict.WAIT
                || stations.values().stream().anyMatch(e -> e.scenarioVerdict() == RseValidationSelfTestService.Verdict.WAIT)
                || cells.values().stream().anyMatch(e -> e.verdict() == RseValidationSelfTestService.Verdict.WAIT);
        RseValidationSelfTestService.Verdict master = failed
                ? RseValidationSelfTestService.Verdict.FAIL
                : waiting ? RseValidationSelfTestService.Verdict.WAIT : RseValidationSelfTestService.Verdict.PASS;

        ArrayList<Component> lines = new ArrayList<>();
        lines.add(Component.literal("MEGA FACTORY FULL DIAGNOSIS"));
        lines.add(Component.literal("PHASE: " + phase + " | AGE: " + age + "t | MASTER: " + master));
        lines.add(Component.literal("===== CURRENT BLOCKERS ====="));
        int blockers = 0;
        for (RseValidationSelfTestService.Verdict wanted : List.of(
                RseValidationSelfTestService.Verdict.FAIL,
                RseValidationSelfTestService.Verdict.WAIT)) {
            if (gate.verdict() == wanted) {
                lines.add(Component.literal("PHASE GATE " + wanted + " | " + gate.detail()));
                blockers++;
            }
            for (RseMegaValidationTopology.Station spec : RseMegaValidationTopology.stations()) {
                RseMegaStationEvaluator.StationEvaluation evaluation = stations.get(spec.number());
                if (evaluation == null || evaluation.scenarioVerdict() != wanted) continue;
                lines.add(Component.literal(String.format(Locale.ROOT,
                        "D%02d %s %s | health=%s q=%s reason=%s | %s",
                        spec.number(), spec.name(), wanted, evaluation.health(), evaluation.quality(),
                        evaluation.reasonCode(), evaluation.detail())));
                blockers++;
            }
            for (char c = 'A'; c <= 'H'; c++) {
                String cell = String.valueOf(c);
                RseValidationSelfTestService.Evaluation evaluation = cells.get(cell);
                if (evaluation == null || evaluation.verdict() != wanted) continue;
                lines.add(Component.literal("CELL " + cell + " " + wanted + " | " + evaluation.detail()));
                blockers++;
            }
        }
        if (blockers == 0) lines.add(Component.literal("NONE"));

        lines.add(Component.literal("===== CELL SUMMARY ====="));
        for (char c = 'A'; c <= 'H'; c++) {
            String cell = String.valueOf(c);
            RseValidationSelfTestService.Evaluation evaluation = cells.get(cell);
            if (evaluation == null) {
                lines.add(Component.literal("CELL " + cell + " UNKNOWN | no live evaluation"));
            } else {
                lines.add(Component.literal("CELL " + cell + " " + evaluation.verdict()
                        + " | " + evaluation.detail()));
            }
        }

        lines.add(Component.literal("===== ALL 40 DUT ====="));
        for (RseMegaValidationTopology.Station spec : RseMegaValidationTopology.stations()) {
            RseMegaStationEvaluator.StationEvaluation evaluation = stations.get(spec.number());
            if (evaluation == null) continue;
            lines.add(Component.literal(String.format(Locale.ROOT,
                    "D%02d %s %s | health=%s q=%s reason=%s | %s",
                    spec.number(), spec.name(), evaluation.scenarioVerdict(), evaluation.health(),
                    evaluation.quality(), evaluation.reasonCode(), evaluation.detail())));
        }

        lines.add(Component.literal("===== HISTORY ====="));
        int historicalFailures = 0;
        for (int station = 1; station <= STATION_COUNT; station++) {
            if (!data.stationEverFailed(station)) continue;
            RseMegaValidationTopology.Station spec = RseMegaValidationTopology.station(station);
            lines.add(Component.literal(String.format(Locale.ROOT,
                    "D%02d %s first failure: %s | %s",
                    station, spec.name(), data.firstFailurePhase(station), data.firstFailureDetail(station))));
            historicalFailures++;
        }
        if (historicalFailures == 0) lines.add(Component.literal("No recorded station failures."));

        long liveFail = stations.values().stream()
                .filter(e -> e.scenarioVerdict() == RseValidationSelfTestService.Verdict.FAIL).count();
        long liveWait = stations.values().stream()
                .filter(e -> e.scenarioVerdict() == RseValidationSelfTestService.Verdict.WAIT).count();
        long liveHealthy = stations.values().stream()
                .filter(e -> e.health() == RseMegaStationEvaluator.Health.HEALTHY).count();
        lines.add(Component.literal("===== ACCEPTANCE ====="));
        lines.add(Component.literal("Completed phases: " + Long.bitCount(data.completedPhases()) + "/20"
                + " | stationsEverPassed=" + data.stationsEverPassedCount() + "/40"
                + " | liveHealthy=" + liveHealthy + "/40"
                + " | storedCurrentFailures=" + data.stationsCurrentlyFailedCount()));
        lines.add(Component.literal("CURRENT BLOCKERS=" + blockers
                + " | liveStationFail=" + liveFail
                + " | liveStationWait=" + liveWait
                + " | endurance=" + data.enduranceHealthyTicks() + "/" + ENDURANCE_REQUIRED_TICKS + "t"));
        lines.add(Component.literal("Origin: " + placement.origin().toShortString()));
        return new RseValidationFactoryService.Result(true, lines);
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        ServerLevel level = server.overworld();
        if (level.getGameTime() % AUTO_INTERVAL_TICKS != 0L) return;
        RseMegaValidationSavedData data = RseMegaValidationSavedData.get(level);
        RseMegaValidationSavedData.Placement placement = data.placement();
        if (placement == null) return;
        BlockPos origin = placement.origin();
        if (!level.hasChunkAt(origin.offset(CONTROL_OFFSET))) return;

        boolean pressed = masterRetestPressed(level, origin);
        if (pressed && !placement.retestPressed()) {
            if (!rebuildForRetest(level, origin, true)) {
                updateMasterPanel(level, origin, RseValidationSelfTestService.Verdict.FAIL);
            }
            return;
        }
        if (!pressed && placement.retestPressed()) {
            placement = data.setRetestPressed(false);
            if (placement == null) return;
        }
        tickAcceptance(level, data, placement);
    }

    private static void tickAcceptance(
            ServerLevel level,
            RseMegaValidationSavedData data,
            RseMegaValidationSavedData.Placement placement
    ) {
        Phase phase = phaseOf(placement.phase());
        long phaseAge = Math.max(0L, level.getGameTime() - placement.phaseStartedTick());
        applyPhaseStimulus(level, placement.origin(), phase);

        if (phaseAge < settleTicks(phase)) {
            updateAllPanels(level, placement.origin(), RseValidationSelfTestService.Verdict.WAIT);
            return;
        }

        LinkedHashMap<Integer, RseMegaStationEvaluator.StationEvaluation> stations = evaluateStations(
                level, placement.origin(), phase, phaseAge);
        LinkedHashMap<String, RseValidationSelfTestService.Evaluation> cells = evaluateCells(
                level, placement.origin(), phase, phaseAge, stations);

        for (Map.Entry<Integer, RseMegaStationEvaluator.StationEvaluation> entry : stations.entrySet()) {
            RseMegaStationEvaluator.StationEvaluation value = entry.getValue();
            data.recordStation(entry.getKey(), value.scenarioVerdict(),
                    "health=" + value.health() + " q=" + value.quality()
                            + " reason=" + value.reasonCode() + " | " + value.detail(), phase.name());
        }
        for (Map.Entry<String, RseValidationSelfTestService.Evaluation> entry : cells.entrySet()) {
            data.recordCell(entry.getKey().charAt(0) - 'A', entry.getValue().verdict(), entry.getValue().detail());
        }
        updateStationPanels(level, placement.origin(), stations);
        updateCellPanels(level, placement.origin(), cells);

        RseValidationSelfTestService.Evaluation gate = phaseGate(
                level, placement.origin(), phase, phaseAge, stations, cells, data);
        boolean failed = gate.verdict() == RseValidationSelfTestService.Verdict.FAIL
                || stations.values().stream().anyMatch(e -> e.scenarioVerdict() == RseValidationSelfTestService.Verdict.FAIL)
                || cells.values().stream().anyMatch(e -> e.verdict() == RseValidationSelfTestService.Verdict.FAIL);
        boolean waiting = gate.verdict() == RseValidationSelfTestService.Verdict.WAIT
                || stations.values().stream().anyMatch(e -> e.scenarioVerdict() == RseValidationSelfTestService.Verdict.WAIT)
                || cells.values().stream().anyMatch(e -> e.verdict() == RseValidationSelfTestService.Verdict.WAIT);

        if (failed) {
            updateMasterPanel(level, placement.origin(), RseValidationSelfTestService.Verdict.FAIL);
            return;
        }
        if (waiting) {
            updateMasterPanel(level, placement.origin(), RseValidationSelfTestService.Verdict.WAIT);
            return;
        }

        if (phase == Phase.ENDURANCE_RUN) {
            data.beginEndurance(level.getGameTime());
            data.addHealthyEndurance(AUTO_INTERVAL_TICKS);
            if (data.enduranceHealthyTicks() < ENDURANCE_REQUIRED_TICKS) {
                updateMasterPanel(level, placement.origin(), RseValidationSelfTestService.Verdict.WAIT);
                return;
            }
        }

        data.markPhaseComplete(phase.ordinal());
        if (phase == Phase.FINAL_ACCEPTANCE) {
            updateMasterPanel(level, placement.origin(), RseValidationSelfTestService.Verdict.PASS);
            return;
        }
        updateMasterPanel(level, placement.origin(), RseValidationSelfTestService.Verdict.WAIT);
        data.advancePhase(level.getGameTime());
    }

    private static LinkedHashMap<Integer, RseMegaStationEvaluator.StationEvaluation> evaluateStations(
            ServerLevel level,
            BlockPos origin,
            Phase phase,
            long phaseAge
    ) {
        LinkedHashMap<Integer, RseMegaStationEvaluator.StationEvaluation> result = new LinkedHashMap<>();
        for (RseMegaValidationTopology.Station spec : RseMegaValidationTopology.stations()) {
            result.put(spec.number(), RseMegaStationEvaluator.evaluate(level, origin, spec, phase, phaseAge));
        }
        return result;
    }

    private static LinkedHashMap<String, RseValidationSelfTestService.Evaluation> evaluateCells(
            ServerLevel level,
            BlockPos origin,
            Phase phase,
            long phaseAge,
            Map<Integer, RseMegaStationEvaluator.StationEvaluation> stations
    ) {
        LinkedHashMap<String, RseValidationSelfTestService.Evaluation> result = new LinkedHashMap<>();
        for (char c = 'A'; c <= 'H'; c++) {
            String cell = String.valueOf(c);
            boolean failed = false;
            boolean waiting = false;
            int passed = 0;
            for (RseMegaValidationTopology.Station spec : RseMegaValidationTopology.stations()) {
                if (!spec.cell().equals(cell)) continue;
                RseMegaStationEvaluator.StationEvaluation evaluation = stations.get(spec.number());
                failed |= evaluation.scenarioVerdict() == RseValidationSelfTestService.Verdict.FAIL;
                waiting |= evaluation.scenarioVerdict() == RseValidationSelfTestService.Verdict.WAIT;
                if (evaluation.scenarioVerdict() == RseValidationSelfTestService.Verdict.PASS) passed++;
            }
            if (failed) {
                result.put(cell, fail("station failure inside cell; passed=" + passed + "/5"));
                continue;
            }
            if (waiting) {
                result.put(cell, waitFor("station evidence pending; passed=" + passed + "/5"));
                continue;
            }
            if (phase == Phase.STRUCTURE_PRECHECK || phase == Phase.STATION_BIST) {
                result.put(cell, pass("5/5 phase-aware station evidence"));
                continue;
            }
            RseValidationSelfTestService.Evaluation tap = evaluateCellTap(level, origin, cell, phaseAge);
            if (tap.verdict() != RseValidationSelfTestService.Verdict.PASS) {
                result.put(cell, tap);
                continue;
            }
            result.put(cell, pass("5/5 stations + live plant-backbone tap"));
        }
        return result;
    }

    private static RseValidationSelfTestService.Evaluation evaluateCellTap(
            ServerLevel level,
            BlockPos origin,
            String cell,
            long phaseAge
    ) {
        RseMegaValidationTopology.Module module = RseMegaValidationTopology.cellModules().get(cell);
        if (module == null) return fail("cell module missing");
        BlockPos tap = origin.offset(module.offset()).offset(29, 1, 2);
        BlockState state = level.getBlockState(tap);
        int output = outputOrMinusOne(state);
        if (output > 0) return pass("backbone tap=" + output);
        if (phaseAge < PROPAGATION_TIMEOUT_TICKS) return waitFor("backbone tap propagating=" + output);
        return fail("BACKBONE TAP STUCK=" + output + " after " + phaseAge + "t");
    }

    private static RseValidationSelfTestService.Evaluation phaseGate(
            ServerLevel level,
            BlockPos origin,
            Phase phase,
            long phaseAge,
            Map<Integer, RseMegaStationEvaluator.StationEvaluation> stations,
            Map<String, RseValidationSelfTestService.Evaluation> cells,
            RseMegaValidationSavedData data
    ) {
        return switch (phase) {
            case STRUCTURE_PRECHECK -> allBlocksPresent(level, origin);
            case STATION_BIST -> aggregateStations(stations, "40-station BIST");
            case CELL_ACCEPTANCE -> aggregateCells(cells, "8-cell acceptance");
            case CHAIN_CONTINUITY -> evaluateBackbone(level, origin, phaseAge, -1);
            case NOMINAL_STARTUP -> aggregateCellsAndBackbone(level, origin, phaseAge, cells, "nominal startup");
            case TIMING_TEST -> timingGate(level, origin, phaseAge);
            case NOISE_TEST -> cellGate(cells, "C", "precision/noise cell");
            case SATURATION_TEST -> evaluateBackbone(level, origin, phaseAge, 15);
            case DATA_INTEGRITY_TEST -> cellGate(cells, "D", "digital data chain");
            case COMM_DEGRADATION -> cellGate(cells, "E", "communications degradation");
            case PROCESS_LOAD -> cellGate(cells, "G", "pneumatic process load");
            case SENSOR_FAULT -> stationGate(stations, 38, "sensor fault observation");
            case ACTUATOR_FAULT -> stationGate(stations, 37, "actuator safe-state observation");
            case REDUNDANCY_TEST -> aggregateCells(cells, "independent A-H verdict agreement");
            case INTERLOCK_TRIP -> stationGate(stations, 39, "interlock trip observation");
            case SAFE_STATE -> stationGate(stations, 37, "servo safe state");
            case ACK_RESET -> alarmResetGate(level, origin);
            case RECOVERY -> aggregateCellsAndBackbone(level, origin, phaseAge, cells, "recovery");
            case ENDURANCE_RUN -> aggregateCellsAndBackbone(level, origin, phaseAge, cells, "endurance healthy window");
            case FINAL_ACCEPTANCE -> finalAcceptanceGate(level, origin, phaseAge, stations, cells, data);
        };
    }

    private static RseValidationSelfTestService.Evaluation stationGate(
            Map<Integer, RseMegaStationEvaluator.StationEvaluation> stations,
            int station,
            String label
    ) {
        RseMegaStationEvaluator.StationEvaluation value = stations.get(station);
        if (value == null) return fail(label + " missing D" + station);
        if (value.scenarioVerdict() == RseValidationSelfTestService.Verdict.PASS) {
            return pass(label + " | health=" + value.health() + " reason=" + value.reasonCode()
                    + " | " + value.detail());
        }
        return new RseValidationSelfTestService.Evaluation(
                value.scenarioVerdict(),
                label + " | health=" + value.health() + " reason=" + value.reasonCode()
                        + " | " + value.detail());
    }

    private static RseValidationSelfTestService.Evaluation allBlocksPresent(ServerLevel level, BlockPos origin) {
        for (RseMegaValidationTopology.Station spec : RseMegaValidationTopology.stations()) {
            BlockPos pos = RseMegaValidationTopology.stationWorldPos(origin, spec);
            String actual = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
            if (!spec.blockId().equals(actual)) {
                return fail(String.format(Locale.ROOT,
                        "D%02d identity=%s expected=%s", spec.number(), actual, spec.blockId()));
            }
        }
        return pass("40/40 exact DUT identities present");
    }

    private static RseValidationSelfTestService.Evaluation aggregateStations(
            Map<Integer, RseMegaStationEvaluator.StationEvaluation> stations,
            String label
    ) {
        long passed = stations.values().stream()
                .filter(e -> e.scenarioVerdict() == RseValidationSelfTestService.Verdict.PASS).count();
        if (stations.values().stream().anyMatch(e -> e.scenarioVerdict() == RseValidationSelfTestService.Verdict.FAIL)) {
            return fail(label + " has station failure; pass=" + passed + "/40");
        }
        if (passed < STATION_COUNT) return waitFor(label + " pending; pass=" + passed + "/40");
        return pass(label + " pass=40/40");
    }

    private static RseValidationSelfTestService.Evaluation aggregateCells(
            Map<String, RseValidationSelfTestService.Evaluation> cells,
            String label
    ) {
        long passed = cells.values().stream()
                .filter(e -> e.verdict() == RseValidationSelfTestService.Verdict.PASS).count();
        if (cells.values().stream().anyMatch(e -> e.verdict() == RseValidationSelfTestService.Verdict.FAIL)) {
            return fail(label + " has cell failure; pass=" + passed + "/8");
        }
        if (passed < CELL_COUNT) return waitFor(label + " pending; pass=" + passed + "/8");
        return pass(label + " pass=8/8");
    }

    private static RseValidationSelfTestService.Evaluation cellGate(
            Map<String, RseValidationSelfTestService.Evaluation> cells,
            String cell,
            String label
    ) {
        RseValidationSelfTestService.Evaluation value = cells.get(cell);
        if (value == null) return fail(label + " missing cell " + cell);
        return value.verdict() == RseValidationSelfTestService.Verdict.PASS
                ? pass(label + " | " + value.detail())
                : new RseValidationSelfTestService.Evaluation(value.verdict(), label + " | " + value.detail());
    }

    private static RseValidationSelfTestService.Evaluation aggregateCellsAndBackbone(
            ServerLevel level,
            BlockPos origin,
            long phaseAge,
            Map<String, RseValidationSelfTestService.Evaluation> cells,
            String label
    ) {
        RseValidationSelfTestService.Evaluation cell = aggregateCells(cells, label);
        if (cell.verdict() != RseValidationSelfTestService.Verdict.PASS) return cell;
        RseValidationSelfTestService.Evaluation backbone = evaluateBackbone(level, origin, phaseAge, -1);
        if (backbone.verdict() != RseValidationSelfTestService.Verdict.PASS) return backbone;
        return pass(label + " | 8/8 cells + end-to-end backbone");
    }

    private static RseValidationSelfTestService.Evaluation evaluateBackbone(
            ServerLevel level,
            BlockPos origin,
            long phaseAge,
            int exactExpected
    ) {
        BlockPos sourcePos = origin.offset(NORTH_SPINE_OFFSET).offset(0, 1, 2);
        BlockState sourceState = level.getBlockState(sourcePos);
        if (!(sourceState.getBlock() instanceof RedstoneReferenceSourceBlock)) {
            return fail("mega backbone source missing");
        }
        int source = sourceState.getValue(RedstoneReferenceSourceBlock.POWER);
        BlockPos finalPos = origin.offset(CROSS_SPINE_OFFSET).offset(0, 1, 2);
        BlockState finalState = level.getBlockState(finalPos);
        int output = outputOrMinusOne(finalState);
        int expected = exactExpected >= 0 ? exactExpected : source;
        if (output == expected && output > 0) return pass("backbone source/final=" + source + "/" + output);
        if (phaseAge < PROPAGATION_TIMEOUT_TICKS) {
            return waitFor("backbone propagating source/final=" + source + "/" + output
                    + " expected=" + expected);
        }
        return fail("BACKBONE CONTINUITY STUCK source/final=" + source + "/" + output
                + " expected=" + expected);
    }

    private static RseValidationSelfTestService.Evaluation timingGate(
            ServerLevel level,
            BlockPos origin,
            long phaseAge
    ) {
        RseMegaValidationTopology.Station spec = RseMegaValidationTopology.station(6);
        BlockPos pos = RseMegaValidationTopology.stationWorldPos(origin, spec);
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof QuartzLabOscillatorBlock)) return fail("D06 quartz oscillator missing");
        QuartzLabOscillatorBlock.TimingEvidence evidence = QuartzLabOscillatorBlock.timingEvidence(level, pos, state);
        if (evidence.available()) {
            return pass("D06 realized timing nominal=" + evidence.nominalPeriod()
                    + " lastHalf=" + evidence.lastHalfInterval());
        }
        return phaseAge < PROPAGATION_TIMEOUT_TICKS
                ? waitFor("D06 timing evidence pending")
                : fail("D06 timing evidence timeout");
    }

    private static RseValidationSelfTestService.Evaluation alarmResetGate(ServerLevel level, BlockPos origin) {
        BlockPos alarm = RseMegaValidationTopology.stationWorldPos(origin, RseMegaValidationTopology.station(40));
        boolean latched = AlarmProcessorBlock.latched(level, alarm);
        return !latched ? pass("D40 alarm clear/reset state observed") : waitFor("D40 alarm remains latched");
    }

    private static RseValidationSelfTestService.Evaluation finalAcceptanceGate(
            ServerLevel level,
            BlockPos origin,
            long phaseAge,
            Map<Integer, RseMegaStationEvaluator.StationEvaluation> stations,
            Map<String, RseValidationSelfTestService.Evaluation> cells,
            RseMegaValidationSavedData data
    ) {
        if (data.stationsEverPassedCount() != STATION_COUNT) {
            return fail("final acceptance missing station-pass history=" + data.stationsEverPassedCount() + "/40");
        }
        if (data.stationsCurrentlyFailedCount() != 0) {
            return fail("final acceptance has unresolved station failures=" + data.stationsCurrentlyFailedCount());
        }
        long liveHealthy = stations.values().stream()
                .filter(e -> e.health() == RseMegaStationEvaluator.Health.HEALTHY).count();
        if (liveHealthy != STATION_COUNT) {
            return fail("final acceptance live healthy stations=" + liveHealthy + "/40");
        }
        RseValidationSelfTestService.Evaluation cell = aggregateCells(cells, "final 8-cell state");
        if (cell.verdict() != RseValidationSelfTestService.Verdict.PASS) return cell;
        if (data.enduranceHealthyTicks() < ENDURANCE_REQUIRED_TICKS) {
            return fail("endurance evidence=" + data.enduranceHealthyTicks() + "/" + ENDURANCE_REQUIRED_TICKS + "t");
        }
        for (int phase = 0; phase < Phase.FINAL_ACCEPTANCE.ordinal(); phase++) {
            if (!data.phaseCompleted(phase)) return fail("required phase not completed: " + Phase.values()[phase]);
        }
        RseValidationSelfTestService.Evaluation backbone = evaluateBackbone(level, origin, phaseAge, -1);
        if (backbone.verdict() != RseValidationSelfTestService.Verdict.PASS) return backbone;
        return pass("FINAL ACCEPTANCE: 40/40 live healthy, 8/8 cells, history, endurance and backbone satisfied");
    }

    private static void applyPhaseStimulus(ServerLevel level, BlockPos origin, Phase phase) {
        int token = switch (phase) {
            case SATURATION_TEST -> 15;
            case COMM_DEGRADATION -> 7;
            case NOISE_TEST -> 8;
            default -> 9;
        };
        setReferencePower(level, origin.offset(NORTH_SPINE_OFFSET).offset(0, 1, 2), token);
        setReferencePower(level,
                RseMegaValidationTopology.stationWorldPos(origin, RseMegaValidationTopology.station(1)), token);
        for (RseMegaValidationTopology.Station spec : RseMegaValidationTopology.stations()) {
            BlockPos fixture = fixturePos(origin, spec);
            int power = 6 + (spec.number() % 4);
            if (phase == Phase.SENSOR_FAULT && spec.number() == 38) power = 0;
            if (phase == Phase.ACTUATOR_FAULT && spec.number() == 37) power = 15;
            setReferencePower(level, fixture, power);
        }
    }

    private static void applyBaseline(ServerLevel level, BlockPos origin) {
        setReferencePower(level, origin.offset(NORTH_SPINE_OFFSET).offset(0, 1, 2), 9);
        setReferencePower(level,
                RseMegaValidationTopology.stationWorldPos(origin, RseMegaValidationTopology.station(1)), 9);
        for (RseMegaValidationTopology.Station spec : RseMegaValidationTopology.stations()) {
            setReferencePower(level, fixturePos(origin, spec), 6 + (spec.number() % 4));
        }
    }

    private static BlockPos fixturePos(BlockPos origin, RseMegaValidationTopology.Station spec) {
        RseMegaValidationTopology.Module module = RseMegaValidationTopology.module(spec.moduleId());
        return origin.offset(module.offset()).offset(spec.dut().getX(), 1, 15);
    }

    private static int settleTicks(Phase phase) {
        return switch (phase) {
            case STRUCTURE_PRECHECK -> 8;
            case STATION_BIST -> 80;
            case CELL_ACCEPTANCE, CHAIN_CONTINUITY -> 120;
            case ENDURANCE_RUN -> 0;
            default -> 80;
        };
    }

    private static Phase phaseOf(int index) {
        Phase[] phases = Phase.values();
        return phases[Math.max(0, Math.min(phases.length - 1, index))];
    }

    private static int outputOrMinusOne(BlockState state) {
        return state.hasProperty(DirectionalSignalBlock.OUTPUT) ? state.getValue(DirectionalSignalBlock.OUTPUT) : -1;
    }

    private static boolean setReferencePower(ServerLevel level, BlockPos pos, int power) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof RedstoneReferenceSourceBlock source)
                || !state.hasProperty(RedstoneReferenceSourceBlock.POWER)) return false;
        int bounded = Math.max(0, Math.min(15, power));
        if (state.getValue(RedstoneReferenceSourceBlock.POWER) == bounded) return true;
        BlockState next = state.setValue(RedstoneReferenceSourceBlock.POWER, bounded);
        level.setBlock(pos, next, 3);
        level.updateNeighborsAt(pos, source);
        level.updateNeighborsAt(pos.relative(next.getValue(RedstoneReferenceSourceBlock.FACING)), source);
        return true;
    }

    private static RseValidationFactoryService.Result preflight(ServerLevel level) {
        for (RseMegaValidationTopology.Module module : RseMegaValidationTopology.modules()) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                    RedstoneEngineering.MOD_ID, "validation/mega/" + module.id());
            if (level.getStructureManager().get(id).isEmpty()) {
                return RseValidationFactoryService.Result.fail("Mega preflight missing structure: " + id);
            }
        }
        return RseValidationFactoryService.Result.ok("Mega v2.1 structure preflight passed (11 modules).");
    }

    private static RseValidationFactoryService.Result placeModules(ServerLevel level, BlockPos origin) {
        for (RseMegaValidationTopology.Module module : RseMegaValidationTopology.modules()) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                    RedstoneEngineering.MOD_ID, "validation/mega/" + module.id());
            StructureTemplate template = level.getStructureManager().get(id).orElse(null);
            if (template == null) return RseValidationFactoryService.Result.fail("Missing Mega module: " + id);
            BlockPos moduleOrigin = origin.offset(module.offset());
            boolean placed = template.placeInWorld(
                    level, moduleOrigin, moduleOrigin, new StructurePlaceSettings(), level.getRandom(), 2);
            if (!placed) return RseValidationFactoryService.Result.fail("Failed to place Mega module: " + id);
        }
        return RseValidationFactoryService.Result.ok("Placed 11 Mega Validation Factory modules.");
    }

    private static boolean rebuildForRetest(ServerLevel level, BlockPos origin, boolean pressed) {
        if (!preflight(level).success()) return false;
        for (RseMegaValidationTopology.Module module : RseMegaValidationTopology.modules()) {
            clearModule(level, origin.offset(module.offset()), module.size());
        }
        if (!placeModules(level, origin).success()) return false;
        RseMegaValidationSavedData.get(level).resetForRetest(level.getGameTime(), pressed);
        applyBaseline(level, origin);
        updateAllPanels(level, origin, RseValidationSelfTestService.Verdict.WAIT);
        return true;
    }

    private static void clearModule(ServerLevel level, BlockPos origin, BlockPos size) {
        for (int x = 0; x < size.getX(); x++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int z = 0; z < size.getZ(); z++) {
                    level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static boolean masterRetestPressed(ServerLevel level, BlockPos origin) {
        BlockState state = level.getBlockState(origin.offset(CONTROL_OFFSET).offset(MASTER_RETEST_BUTTON));
        return state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED);
    }

    private static void updateStationPanels(
            ServerLevel level,
            BlockPos origin,
            Map<Integer, RseMegaStationEvaluator.StationEvaluation> evaluations
    ) {
        BlockPos control = origin.offset(CONTROL_OFFSET);
        for (RseMegaValidationTopology.Station spec : RseMegaValidationTopology.stations()) {
            RseMegaStationEvaluator.StationEvaluation evaluation = evaluations.get(spec.number());
            if (evaluation == null) continue;
            RseMegaValidationTopology.Module module = RseMegaValidationTopology.module(spec.moduleId());
            BlockPos local = origin.offset(module.offset());
            int x = spec.dut().getX();
            updatePanel(level, local,
                    new BlockPos(x - 1, 2, 5), new BlockPos(x, 2, 5), new BlockPos(x + 1, 2, 5),
                    evaluation.scenarioVerdict());

            int row = (spec.number() - 1) / 5;
            int col = (spec.number() - 1) % 5;
            int cx = 3 + col * 6;
            int cz = 1 + row * 2;
            updatePanel(level, control,
                    new BlockPos(cx - 1, 2, cz + 1), new BlockPos(cx, 2, cz + 1), new BlockPos(cx + 1, 2, cz + 1),
                    evaluation.scenarioVerdict());
        }
    }

    private static void updateCellPanels(
            ServerLevel level,
            BlockPos origin,
            Map<String, RseValidationSelfTestService.Evaluation> evaluations
    ) {
        BlockPos control = origin.offset(CONTROL_OFFSET);
        for (Map.Entry<String, RseValidationSelfTestService.Evaluation> entry : evaluations.entrySet()) {
            String cell = entry.getKey();
            RseMegaValidationTopology.Module module = RseMegaValidationTopology.cellModules().get(cell);
            if (module == null) continue;
            updatePanel(level, origin.offset(module.offset()),
                    LOCAL_CELL_WAIT_POWER, LOCAL_CELL_PASS_POWER, LOCAL_CELL_FAIL_POWER, entry.getValue().verdict());
            int index = cell.charAt(0) - 'A';
            int z = 1 + index * 2;
            updatePanel(level, control,
                    new BlockPos(39, 2, z + 1), new BlockPos(40, 2, z + 1), new BlockPos(41, 2, z + 1),
                    entry.getValue().verdict());
        }
    }

    private static void updateAllPanels(
            ServerLevel level,
            BlockPos origin,
            RseValidationSelfTestService.Verdict verdict
    ) {
        LinkedHashMap<Integer, RseMegaStationEvaluator.StationEvaluation> stations = new LinkedHashMap<>();
        for (RseMegaValidationTopology.Station spec : RseMegaValidationTopology.stations()) {
            BlockPos pos = RseMegaValidationTopology.stationWorldPos(origin, spec);
            RseMegaStationEvaluator.Health health = verdict == RseValidationSelfTestService.Verdict.FAIL
                    ? RseMegaStationEvaluator.Health.FAILED
                    : verdict == RseValidationSelfTestService.Verdict.PASS
                        ? RseMegaStationEvaluator.Health.HEALTHY
                        : RseMegaStationEvaluator.Health.PENDING;
            stations.put(spec.number(), new RseMegaStationEvaluator.StationEvaluation(
                    spec.number(), spec.cell(), health, verdict, "PHASE_TRANSITION",
                    verdict == RseValidationSelfTestService.Verdict.PASS ? PortQuality.VALID : PortQuality.STALE,
                    "phase transition", pos));
        }
        LinkedHashMap<String, RseValidationSelfTestService.Evaluation> cells = new LinkedHashMap<>();
        for (char c = 'A'; c <= 'H'; c++) {
            cells.put(String.valueOf(c), new RseValidationSelfTestService.Evaluation(verdict, "phase transition"));
        }
        updateStationPanels(level, origin, stations);
        updateCellPanels(level, origin, cells);
        updateMasterPanel(level, origin, verdict);
    }

    private static void updateMasterPanel(
            ServerLevel level,
            BlockPos origin,
            RseValidationSelfTestService.Verdict verdict
    ) {
        updatePanel(level, origin.offset(CONTROL_OFFSET),
                MASTER_WAIT_POWER, MASTER_PASS_POWER, MASTER_FAIL_POWER, verdict);
    }

    private static void updatePanel(
            ServerLevel level,
            BlockPos origin,
            BlockPos waitPower,
            BlockPos passPower,
            BlockPos failPower,
            RseValidationSelfTestService.Verdict verdict
    ) {
        setPanelPower(level, origin.offset(waitPower), verdict == RseValidationSelfTestService.Verdict.WAIT);
        setPanelPower(level, origin.offset(passPower), verdict == RseValidationSelfTestService.Verdict.PASS);
        setPanelPower(level, origin.offset(failPower), verdict == RseValidationSelfTestService.Verdict.FAIL);
    }

    private static void setPanelPower(ServerLevel level, BlockPos pos, boolean powered) {
        BlockState desired = powered ? Blocks.REDSTONE_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
        if (level.getBlockState(pos).is(desired.getBlock())) return;
        level.setBlock(pos, desired, 3);
        level.updateNeighborsAt(pos, desired.getBlock());
    }

    private static RseValidationSelfTestService.Evaluation pass(String detail) {
        return new RseValidationSelfTestService.Evaluation(RseValidationSelfTestService.Verdict.PASS, detail);
    }

    private static RseValidationSelfTestService.Evaluation waitFor(String detail) {
        return new RseValidationSelfTestService.Evaluation(RseValidationSelfTestService.Verdict.WAIT, detail);
    }

    private static RseValidationSelfTestService.Evaluation fail(String detail) {
        return new RseValidationSelfTestService.Evaluation(RseValidationSelfTestService.Verdict.FAIL, detail);
    }
}
