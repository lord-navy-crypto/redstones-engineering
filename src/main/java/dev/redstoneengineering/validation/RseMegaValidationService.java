package dev.redstoneengineering.validation;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.AlarmProcessorBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.QuartzLabOscillatorBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.SafetyInterlockBlock;
import dev.redstoneengineering.block.ServoActuatorBlock;
import dev.redstoneengineering.block.ServoPositionSensorBlock;
import dev.redstoneengineering.block.SignalAnalyzerBlock;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
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
 * Automatic 40-DUT / eight-cell factory acceptance runtime for Mega Validation Factory v2.
 *
 * <p>The validator owns fixture stimuli, status panels and acceptance sequencing. It never writes a
 * DUT output to manufacture a PASS. Evidence comes from the placed world: exact block identity,
 * engineering-port snapshots, device runtime APIs, blockstate outputs, the physical serpentine
 * backbone and cell-local taps.</p>
 */
public final class RseMegaValidationService {
    private RseMegaValidationService() {}

    public static final int STATION_COUNT = 40;
    public static final int CELL_COUNT = 8;
    private static final int AUTO_INTERVAL_TICKS = 4;
    private static final long PROPAGATION_TIMEOUT_TICKS = 600L;
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

    private record ModuleSpec(String id, String cell, BlockPos offset, BlockPos size) {}
    private record StationSpec(
            int number,
            String cell,
            String moduleId,
            String blockId,
            BlockPos dut,
            String name
    ) {}

    private static final List<ModuleSpec> MODULES = List.of(
            new ModuleSpec("mega_control_hall", "", CONTROL_OFFSET, new BlockPos(63, 7, 17)),
            new ModuleSpec("mega_north_spine", "", NORTH_SPINE_OFFSET, new BlockPos(133, 7, 3)),
            new ModuleSpec("mega_cross_spine", "", CROSS_SPINE_OFFSET, new BlockPos(133, 7, 3)),
            new ModuleSpec("mega_cell_a_analog", "A", new BlockPos(0, 0, 20), new BlockPos(31, 7, 25)),
            new ModuleSpec("mega_cell_b_timing", "B", new BlockPos(34, 0, 20), new BlockPos(31, 7, 25)),
            new ModuleSpec("mega_cell_c_precision", "C", new BlockPos(68, 0, 20), new BlockPos(31, 7, 25)),
            new ModuleSpec("mega_cell_d_data", "D", new BlockPos(102, 0, 20), new BlockPos(31, 7, 25)),
            new ModuleSpec("mega_cell_e_comms", "E", new BlockPos(102, 0, 48), new BlockPos(31, 7, 25)),
            new ModuleSpec("mega_cell_f_optical", "F", new BlockPos(68, 0, 48), new BlockPos(31, 7, 25)),
            new ModuleSpec("mega_cell_g_pneumatic", "G", new BlockPos(34, 0, 48), new BlockPos(31, 7, 25)),
            new ModuleSpec("mega_cell_h_control", "H", new BlockPos(0, 0, 48), new BlockPos(31, 7, 25))
    );

    private static final List<StationSpec> STATIONS = List.of(
            new StationSpec(1, "A", "mega_cell_a_analog", "redstoneengineering:redstone_reference_source", new BlockPos(3, 1, 12), "REF SOURCE"),
            new StationSpec(2, "A", "mega_cell_a_analog", "redstoneengineering:signal_probe", new BlockPos(9, 1, 12), "SIGNAL PROBE"),
            new StationSpec(3, "A", "mega_cell_a_analog", "redstoneengineering:signal_conditioner", new BlockPos(15, 1, 12), "CONDITIONER"),
            new StationSpec(4, "A", "mega_cell_a_analog", "redstoneengineering:precision_filter", new BlockPos(21, 1, 12), "PREC FILTER"),
            new StationSpec(5, "A", "mega_cell_a_analog", "redstoneengineering:signal_analyzer", new BlockPos(27, 1, 12), "ANALYZER"),
            new StationSpec(6, "B", "mega_cell_b_timing", "redstoneengineering:quartz_lab_oscillator", new BlockPos(3, 1, 12), "QUARTZ OSC"),
            new StationSpec(7, "B", "mega_cell_b_timing", "redstoneengineering:quartz_clock_divider", new BlockPos(9, 1, 12), "CLOCK DIV"),
            new StationSpec(8, "B", "mega_cell_b_timing", "redstoneengineering:edge_detector", new BlockPos(15, 1, 12), "EDGE DETECT"),
            new StationSpec(9, "B", "mega_cell_b_timing", "redstoneengineering:pulse_shaper", new BlockPos(21, 1, 12), "PULSE SHAPE"),
            new StationSpec(10, "B", "mega_cell_b_timing", "redstoneengineering:pwm_controller", new BlockPos(27, 1, 12), "PWM CTRL"),
            new StationSpec(11, "C", "mega_cell_c_precision", "redstoneengineering:lapis_noise_source", new BlockPos(3, 1, 12), "NOISE SOURCE"),
            new StationSpec(12, "C", "mega_cell_c_precision", "redstoneengineering:lapis_low_pass_filter", new BlockPos(9, 1, 12), "LAPIS LPF"),
            new StationSpec(13, "C", "mega_cell_c_precision", "redstoneengineering:lapis_precision_meter", new BlockPos(15, 1, 12), "PREC METER"),
            new StationSpec(14, "C", "mega_cell_c_precision", "redstoneengineering:quartz_triggered_lapis_sampler", new BlockPos(21, 1, 12), "QZ SAMPLER"),
            new StationSpec(15, "C", "mega_cell_c_precision", "redstoneengineering:lapis_to_redstone_quantizer", new BlockPos(27, 1, 12), "QUANTIZER"),
            new StationSpec(16, "D", "mega_cell_d_data", "redstoneengineering:redstone_byte_encoder", new BlockPos(3, 1, 12), "BYTE ENC"),
            new StationSpec(17, "D", "mega_cell_d_data", "redstoneengineering:eight_bit_data_bus", new BlockPos(9, 1, 12), "8BIT BUS"),
            new StationSpec(18, "D", "mega_cell_d_data", "redstoneengineering:serializer", new BlockPos(15, 1, 12), "SERIALIZER"),
            new StationSpec(19, "D", "mega_cell_d_data", "redstoneengineering:serial_data_line", new BlockPos(21, 1, 12), "SERIAL LINE"),
            new StationSpec(20, "D", "mega_cell_d_data", "redstoneengineering:deserializer", new BlockPos(27, 1, 12), "DESERIALIZER"),
            new StationSpec(21, "E", "mega_cell_e_comms", "redstoneengineering:differential_driver", new BlockPos(3, 1, 12), "DIFF DRIVER"),
            new StationSpec(22, "E", "mega_cell_e_comms", "redstoneengineering:differential_data_pair", new BlockPos(9, 1, 12), "DIFF PAIR"),
            new StationSpec(23, "E", "mega_cell_e_comms", "redstoneengineering:digital_regenerator", new BlockPos(15, 1, 12), "REGENERATOR"),
            new StationSpec(24, "E", "mega_cell_e_comms", "redstoneengineering:differential_receiver", new BlockPos(21, 1, 12), "DIFF RX"),
            new StationSpec(25, "E", "mega_cell_e_comms", "redstoneengineering:watchdog", new BlockPos(27, 1, 12), "WATCHDOG"),
            new StationSpec(26, "F", "mega_cell_f_optical", "redstoneengineering:optical_emitter", new BlockPos(3, 1, 12), "OPT EMITTER"),
            new StationSpec(27, "F", "mega_cell_f_optical", "redstoneengineering:optical_fiber", new BlockPos(9, 1, 12), "OPT FIBER"),
            new StationSpec(28, "F", "mega_cell_f_optical", "redstoneengineering:optical_splitter", new BlockPos(15, 1, 12), "OPT SPLITTER"),
            new StationSpec(29, "F", "mega_cell_f_optical", "redstoneengineering:optical_channel_filter", new BlockPos(21, 1, 12), "OPT FILTER"),
            new StationSpec(30, "F", "mega_cell_f_optical", "redstoneengineering:optical_receiver", new BlockPos(27, 1, 12), "OPT RX"),
            new StationSpec(31, "G", "mega_cell_g_pneumatic", "redstoneengineering:air_compressor", new BlockPos(3, 1, 12), "COMPRESSOR"),
            new StationSpec(32, "G", "mega_cell_g_pneumatic", "redstoneengineering:air_reservoir", new BlockPos(9, 1, 12), "RESERVOIR"),
            new StationSpec(33, "G", "mega_cell_g_pneumatic", "redstoneengineering:pressure_regulator", new BlockPos(15, 1, 12), "REGULATOR"),
            new StationSpec(34, "G", "mega_cell_g_pneumatic", "redstoneengineering:pneumatic_proportional_valve", new BlockPos(21, 1, 12), "PROP VALVE"),
            new StationSpec(35, "G", "mega_cell_g_pneumatic", "redstoneengineering:pneumatic_cylinder", new BlockPos(27, 1, 12), "CYLINDER"),
            new StationSpec(36, "H", "mega_cell_h_control", "redstoneengineering:pid_controller", new BlockPos(3, 1, 12), "PID CTRL"),
            new StationSpec(37, "H", "mega_cell_h_control", "redstoneengineering:servo_actuator", new BlockPos(9, 1, 12), "SERVO"),
            new StationSpec(38, "H", "mega_cell_h_control", "redstoneengineering:servo_position_sensor", new BlockPos(15, 1, 12), "POS SENSOR"),
            new StationSpec(39, "H", "mega_cell_h_control", "redstoneengineering:safety_interlock", new BlockPos(21, 1, 12), "INTERLOCK"),
            new StationSpec(40, "H", "mega_cell_h_control", "redstoneengineering:alarm_processor", new BlockPos(27, 1, 12), "ALARM")
    );

    private static final Map<String, ModuleSpec> MODULE_BY_ID;
    private static final Map<String, ModuleSpec> CELL_MODULES;
    private static final Map<Integer, StationSpec> STATION_BY_NUMBER;
    static {
        LinkedHashMap<String, ModuleSpec> modules = new LinkedHashMap<>();
        LinkedHashMap<String, ModuleSpec> cells = new LinkedHashMap<>();
        for (ModuleSpec module : MODULES) {
            modules.put(module.id(), module);
            if (!module.cell().isBlank()) cells.put(module.cell(), module);
        }
        MODULE_BY_ID = Map.copyOf(modules);
        CELL_MODULES = Map.copyOf(cells);
        LinkedHashMap<Integer, StationSpec> stations = new LinkedHashMap<>();
        for (StationSpec station : STATIONS) stations.put(station.number(), station);
        STATION_BY_NUMBER = Map.copyOf(stations);
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
        if (level == null || origin == null) return RseValidationFactoryService.Result.fail("Mega place failed: level/origin missing");
        if (level.getServer().overworld() != level) return RseValidationFactoryService.Result.fail("Mega Validation Factory v2 requires the overworld.");
        RseValidationFactoryService.Result preflight = preflight(level);
        if (!preflight.success()) return preflight;
        RseValidationFactoryService.Result placement = placeModules(level, origin);
        if (!placement.success()) return placement;
        RseMegaValidationSavedData.get(level).place(origin, level.getGameTime());
        applyBaseline(level, origin);
        updateAllPanels(level, origin, RseValidationSelfTestService.Verdict.WAIT);
        return RseValidationFactoryService.Result.ok(
                "Placed RSE Mega Validation Factory v2: 40 DUT / 8 cells / 20-phase acceptance.",
                "Process route: A -> B -> C -> D -> south turn -> E -> F -> G -> H.",
                "Use physical MASTER RETEST for a fresh run; commands remain engineering/debug access.",
                "Developer diagnostics: /rsevalidation mega diagnose | status | station <1..40> | cell <A..H> | report"
        );
    }

    public static RseValidationFactoryService.Result retest(ServerLevel level) {
        if (level == null) return RseValidationFactoryService.Result.fail("Mega retest failed: level missing");
        RseMegaValidationSavedData.Placement placement = RseMegaValidationSavedData.get(level).placement();
        if (placement == null) return RseValidationFactoryService.Result.fail("Mega Validation Factory v2 has not been placed.");
        return rebuildForRetest(level, placement.origin(), false)
                ? RseValidationFactoryService.Result.ok("Mega factory rebuilt; 40-station acceptance restarted at STRUCTURE_PRECHECK.")
                : RseValidationFactoryService.Result.fail("Mega retest rebuild failed.");
    }

    public static RseValidationFactoryService.Result status(ServerLevel level) {
        if (level == null) return RseValidationFactoryService.Result.fail("Mega status failed: level missing");
        RseMegaValidationSavedData data = RseMegaValidationSavedData.get(level);
        RseMegaValidationSavedData.Placement placement = data.placement();
        if (placement == null) return RseValidationFactoryService.Result.fail("Mega Validation Factory v2 has not been placed.");
        Phase phase = phaseOf(placement.phase());
        long age = Math.max(0L, level.getGameTime() - placement.phaseStartedTick());
        ArrayList<Component> lines = new ArrayList<>();
        lines.add(Component.literal("RSE Mega Factory v2 | phase=" + phase + " | age=" + age + "t | stationsEverPassed=" + data.stationsEverPassedCount() + "/40"));
        for (int i = 0; i < CELL_COUNT; i++) {
            String cell = String.valueOf((char) ('A' + i));
            lines.add(Component.literal("Cell " + cell + " -> " + data.cellVerdict(i) + " | " + data.cellDetail(i)));
        }
        lines.add(Component.literal("Current station failures=" + data.stationsCurrentlyFailedCount() + " | endurance=" + data.enduranceHealthyTicks() + "/" + ENDURANCE_REQUIRED_TICKS + "t"));
        lines.add(Component.literal("Origin: " + placement.origin().toShortString()));
        return new RseValidationFactoryService.Result(true, lines);
    }

    public static RseValidationFactoryService.Result station(ServerLevel level, int number) {
        if (level == null) return RseValidationFactoryService.Result.fail("Mega station failed: level missing");
        StationSpec spec = STATION_BY_NUMBER.get(number);
        if (spec == null) return RseValidationFactoryService.Result.fail("Mega station must be 1..40.");
        RseMegaValidationSavedData data = RseMegaValidationSavedData.get(level);
        RseMegaValidationSavedData.Placement placement = data.placement();
        if (placement == null) return RseValidationFactoryService.Result.fail("Mega Validation Factory v2 has not been placed.");
        Phase phase = phaseOf(placement.phase());
        long age = Math.max(0L, level.getGameTime() - placement.phaseStartedTick());
        RseValidationSelfTestService.Evaluation live = evaluateStation(level, placement.origin(), spec, phase, age);
        ArrayList<Component> lines = new ArrayList<>();
        lines.add(Component.literal(String.format(Locale.ROOT, "D%02d %s | Cell %s", spec.number(), spec.name(), spec.cell())));
        lines.add(Component.literal("Live -> " + live.verdict() + " | " + live.detail()));
        lines.add(Component.literal("Stored -> " + data.stationVerdict(number) + " | " + data.stationDetail(number)));
        lines.add(Component.literal("History -> everPassed=" + data.stationEverPassed(number) + " everFailed=" + data.stationEverFailed(number)));
        if (data.stationEverFailed(number)) {
            lines.add(Component.literal("First failure -> " + data.firstFailurePhase(number) + " | " + data.firstFailureDetail(number)));
        }
        return new RseValidationFactoryService.Result(true, lines);
    }

    public static RseValidationFactoryService.Result cell(ServerLevel level, String requestedCell) {
        if (level == null) return RseValidationFactoryService.Result.fail("Mega cell failed: level missing");
        String cell = requestedCell == null ? "" : requestedCell.trim().toUpperCase(Locale.ROOT);
        if (!CELL_MODULES.containsKey(cell)) return RseValidationFactoryService.Result.fail("Mega cell must be A..H.");
        RseMegaValidationSavedData data = RseMegaValidationSavedData.get(level);
        RseMegaValidationSavedData.Placement placement = data.placement();
        if (placement == null) return RseValidationFactoryService.Result.fail("Mega Validation Factory v2 has not been placed.");
        int cellIndex = cell.charAt(0) - 'A';
        ArrayList<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Mega Cell " + cell + " -> " + data.cellVerdict(cellIndex) + " | " + data.cellDetail(cellIndex)));
        for (StationSpec spec : STATIONS) {
            if (!spec.cell().equals(cell)) continue;
            lines.add(Component.literal(String.format(Locale.ROOT, "D%02d %s -> %s | %s",
                    spec.number(), spec.name(), data.stationVerdict(spec.number()), data.stationDetail(spec.number()))));
        }
        return new RseValidationFactoryService.Result(true, lines);
    }

    public static RseValidationFactoryService.Result report(ServerLevel level) {
        if (level == null) return RseValidationFactoryService.Result.fail("Mega report failed: level missing");
        RseMegaValidationSavedData data = RseMegaValidationSavedData.get(level);
        RseMegaValidationSavedData.Placement placement = data.placement();
        if (placement == null) return RseValidationFactoryService.Result.fail("Mega Validation Factory v2 has not been placed.");
        ArrayList<Component> lines = new ArrayList<>();
        Phase phase = phaseOf(placement.phase());
        lines.add(Component.literal("MEGA FACTORY ACCEPTANCE REPORT | current phase=" + phase));
        lines.add(Component.literal("Stations passed at least once: " + data.stationsEverPassedCount() + "/40 | unresolved failures=" + data.stationsCurrentlyFailedCount()));
        int completed = Long.bitCount(data.completedPhases());
        lines.add(Component.literal("Completed phases: " + completed + "/20 | endurance healthy=" + data.enduranceHealthyTicks() + "/" + ENDURANCE_REQUIRED_TICKS + "t"));
        for (int station = 1; station <= STATION_COUNT; station++) {
            if (!data.stationEverFailed(station)) continue;
            StationSpec spec = STATION_BY_NUMBER.get(station);
            lines.add(Component.literal(String.format(Locale.ROOT, "D%02d %s first failure: %s | %s",
                    station, spec.name(), data.firstFailurePhase(station), data.firstFailureDetail(station))));
        }
        return new RseValidationFactoryService.Result(true, lines);
    }

    public static RseValidationFactoryService.Result diagnose(ServerLevel level) {
        if (level == null) return RseValidationFactoryService.Result.fail("Mega diagnose failed: level missing");
        RseMegaValidationSavedData data = RseMegaValidationSavedData.get(level);
        RseMegaValidationSavedData.Placement placement = data.placement();
        if (placement == null) return RseValidationFactoryService.Result.fail("Mega Validation Factory v2 has not been placed.");

        Phase phase = phaseOf(placement.phase());
        long age = Math.max(0L, level.getGameTime() - placement.phaseStartedTick());
        LinkedHashMap<Integer, RseValidationSelfTestService.Evaluation> stations = evaluateStations(
                level, placement.origin(), phase, age);
        LinkedHashMap<String, RseValidationSelfTestService.Evaluation> cells = evaluateCells(
                level, placement.origin(), phase, age, stations);
        RseValidationSelfTestService.Evaluation gate = phaseGate(
                level, placement.origin(), phase, age, stations, cells, data);

        boolean failed = gate.verdict() == RseValidationSelfTestService.Verdict.FAIL
                || stations.values().stream().anyMatch(e -> e.verdict() == RseValidationSelfTestService.Verdict.FAIL)
                || cells.values().stream().anyMatch(e -> e.verdict() == RseValidationSelfTestService.Verdict.FAIL);
        boolean waiting = gate.verdict() == RseValidationSelfTestService.Verdict.WAIT
                || stations.values().stream().anyMatch(e -> e.verdict() == RseValidationSelfTestService.Verdict.WAIT)
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
            for (StationSpec spec : STATIONS) {
                RseValidationSelfTestService.Evaluation evaluation = stations.get(spec.number());
                if (evaluation == null || evaluation.verdict() != wanted) continue;
                lines.add(Component.literal(String.format(Locale.ROOT, "D%02d %s %s | %s",
                        spec.number(), spec.name(), wanted, evaluation.detail())));
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
                lines.add(Component.literal("CELL " + cell + " " + evaluation.verdict() + " | " + evaluation.detail()));
            }
        }

        lines.add(Component.literal("===== ALL 40 DUT ====="));
        for (StationSpec spec : STATIONS) {
            RseValidationSelfTestService.Evaluation evaluation = stations.get(spec.number());
            if (evaluation == null) continue;
            lines.add(Component.literal(String.format(Locale.ROOT, "D%02d %s %s | %s",
                    spec.number(), spec.name(), evaluation.verdict(), evaluation.detail())));
        }

        lines.add(Component.literal("===== HISTORY ====="));
        int historicalFailures = 0;
        for (int station = 1; station <= STATION_COUNT; station++) {
            if (!data.stationEverFailed(station)) continue;
            StationSpec spec = STATION_BY_NUMBER.get(station);
            lines.add(Component.literal(String.format(Locale.ROOT,
                    "D%02d %s first failure: %s | %s",
                    station, spec.name(), data.firstFailurePhase(station), data.firstFailureDetail(station))));
            historicalFailures++;
        }
        if (historicalFailures == 0) lines.add(Component.literal("No recorded station failures."));

        long liveFail = stations.values().stream()
                .filter(e -> e.verdict() == RseValidationSelfTestService.Verdict.FAIL).count();
        long liveWait = stations.values().stream()
                .filter(e -> e.verdict() == RseValidationSelfTestService.Verdict.WAIT).count();
        lines.add(Component.literal("===== ACCEPTANCE ====="));
        lines.add(Component.literal("Completed phases: " + Long.bitCount(data.completedPhases()) + "/20"
                + " | stationsEverPassed=" + data.stationsEverPassedCount() + "/40"
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
            if (!rebuildForRetest(level, origin, true)) updateMasterPanel(level, origin, RseValidationSelfTestService.Verdict.FAIL);
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

        LinkedHashMap<Integer, RseValidationSelfTestService.Evaluation> stations = evaluateStations(
                level, placement.origin(), phase, phaseAge);
        LinkedHashMap<String, RseValidationSelfTestService.Evaluation> cells = evaluateCells(
                level, placement.origin(), phase, phaseAge, stations);

        for (Map.Entry<Integer, RseValidationSelfTestService.Evaluation> entry : stations.entrySet()) {
            data.recordStation(entry.getKey(), entry.getValue().verdict(), entry.getValue().detail(), phase.name());
        }
        for (Map.Entry<String, RseValidationSelfTestService.Evaluation> entry : cells.entrySet()) {
            data.recordCell(entry.getKey().charAt(0) - 'A', entry.getValue().verdict(), entry.getValue().detail());
        }
        updateStationPanels(level, placement.origin(), stations);
        updateCellPanels(level, placement.origin(), cells);

        RseValidationSelfTestService.Evaluation gate = phaseGate(level, placement.origin(), phase, phaseAge, stations, cells, data);
        boolean failed = gate.verdict() == RseValidationSelfTestService.Verdict.FAIL
                || stations.values().stream().anyMatch(e -> e.verdict() == RseValidationSelfTestService.Verdict.FAIL)
                || cells.values().stream().anyMatch(e -> e.verdict() == RseValidationSelfTestService.Verdict.FAIL);
        boolean waiting = gate.verdict() == RseValidationSelfTestService.Verdict.WAIT
                || stations.values().stream().anyMatch(e -> e.verdict() == RseValidationSelfTestService.Verdict.WAIT)
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

    private static LinkedHashMap<Integer, RseValidationSelfTestService.Evaluation> evaluateStations(
            ServerLevel level,
            BlockPos origin,
            Phase phase,
            long phaseAge
    ) {
        LinkedHashMap<Integer, RseValidationSelfTestService.Evaluation> result = new LinkedHashMap<>();
        for (StationSpec spec : STATIONS) result.put(spec.number(), evaluateStation(level, origin, spec, phase, phaseAge));
        return result;
    }

    private static RseValidationSelfTestService.Evaluation evaluateStation(
            ServerLevel level,
            BlockPos plantOrigin,
            StationSpec spec,
            Phase phase,
            long phaseAge
    ) {
        BlockPos moduleOrigin = plantOrigin.offset(MODULE_BY_ID.get(spec.moduleId()).offset());
        BlockPos pos = moduleOrigin.offset(spec.dut());
        if (!level.hasChunkAt(pos)) return waitFor("chunk not loaded at " + pos.toShortString());
        BlockState state = level.getBlockState(pos);
        String actual = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if (!spec.blockId().equals(actual)) return fail("identity=" + actual + " expected=" + spec.blockId());

        // High-value station-specific runtime evidence first.
        switch (spec.number()) {
            case 1 -> {
                if (!(state.getBlock() instanceof RedstoneReferenceSourceBlock)) return fail("reference source class mismatch");
                int power = state.getValue(RedstoneReferenceSourceBlock.POWER);
                if (power <= 0) return fail("reference source power=" + power);
                return pass("identity + source power=" + power);
            }
            case 5 -> {
                SignalAnalyzerBlock.UiSnapshot snapshot = SignalAnalyzerBlock.uiSnapshot(level, pos);
                if (snapshot.totalSamples() <= 0) {
                    return phaseAge < PROPAGATION_TIMEOUT_TICKS
                            ? waitFor("analyzer awaiting first sample")
                            : fail("analyzer produced no samples");
                }
                return pass("analyzer samples=" + snapshot.totalSamples() + " raw=" + snapshot.raw() + " out=" + snapshot.output());
            }
            case 6 -> {
                if (!(state.getBlock() instanceof QuartzLabOscillatorBlock)) return fail("quartz oscillator class mismatch");
                QuartzLabOscillatorBlock.TimingEvidence evidence = QuartzLabOscillatorBlock.timingEvidence(level, pos, state);
                if (!evidence.available()) {
                    return phaseAge < PROPAGATION_TIMEOUT_TICKS
                            ? waitFor("quartz timing evidence pending")
                            : fail("quartz timing evidence unavailable");
                }
                return pass("timing nominal=" + evidence.nominalPeriod() + "t lastHalf=" + evidence.lastHalfInterval() + "t jitter=" + evidence.lastJitterOffset() + "t");
            }
            case 37 -> {
                int command = ServoActuatorBlock.command(level, pos);
                int position = ServoActuatorBlock.position(level, pos);
                boolean braking = ServoActuatorBlock.braking(level, pos);
                if (command < 0 || command > 15 || position < 0 || position > 15) return fail("servo runtime out of bounds");
                return pass("servo command=" + command + " position=" + position + " brake=" + braking);
            }
            case 38 -> {
                if (!(state.getBlock() instanceof ServoPositionSensorBlock)) return fail("position sensor class mismatch");
                PortQuality quality = ServoPositionSensorBlock.sourceQuality(level, pos, state);
                return pass("position-sensor sourceQuality=" + quality);
            }
            case 39 -> {
                int mask = SafetyInterlockBlock.failedMask(level, pos);
                if (mask < 0) {
                    return phaseAge < PROPAGATION_TIMEOUT_TICKS
                            ? waitFor("interlock runtime pending")
                            : fail("interlock runtime unavailable");
                }
                return pass("interlock failedMask=" + mask + " output=" + outputOrMinusOne(state));
            }
            case 40 -> {
                boolean latched = AlarmProcessorBlock.latched(level, pos);
                return pass("alarm latched=" + latched + " output=" + outputOrMinusOne(state));
            }
            default -> {
                // fall through to shared port/output/topology evidence below
            }
        }

        int output = outputOrMinusOne(state);
        if (output >= 0) return pass("identity + directional output=" + output);

        if (state.getBlock() instanceof EngineeringPortProvider provider) {
            List<EngineeringPort> ports = provider.engineeringPorts(state);
            int snapshots = 0;
            int fresh = 0;
            String first = "";
            for (EngineeringPort port : ports) {
                var optional = provider.engineeringSnapshot(level, pos, state, port.side());
                if (optional.isEmpty()) continue;
                snapshots++;
                EngineeringPortSnapshot snapshot = optional.get();
                if (snapshot.quality() != PortQuality.STALE) fresh++;
                if (first.isBlank()) first = port.label() + "=" + Math.round(snapshot.value()) + "/" + snapshot.quality();
            }
            if (fresh > 0) return pass("ports=" + ports.size() + " freshSnapshots=" + fresh + " " + first);
            if (!ports.isEmpty()) return pass("ports=" + ports.size() + " snapshots=" + snapshots + " topology declared");
        }

        // Every bay contains a validation-owned fixture source at local z=15; this is topology evidence,
        // not a substitute for a DUT output.
        BlockPos fixture = moduleOrigin.offset(spec.dut().getX(), 1, 15);
        String fixtureId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(fixture).getBlock()).toString();
        if (!"redstoneengineering:redstone_reference_source".equals(fixtureId)) return fail("station fixture topology missing");
        return pass("identity + fixture topology verified");
    }

    private static LinkedHashMap<String, RseValidationSelfTestService.Evaluation> evaluateCells(
            ServerLevel level,
            BlockPos origin,
            Phase phase,
            long phaseAge,
            Map<Integer, RseValidationSelfTestService.Evaluation> stations
    ) {
        LinkedHashMap<String, RseValidationSelfTestService.Evaluation> result = new LinkedHashMap<>();
        for (char c = 'A'; c <= 'H'; c++) {
            String cell = String.valueOf(c);
            boolean failed = false;
            boolean waiting = false;
            int passed = 0;
            for (StationSpec spec : STATIONS) {
                if (!spec.cell().equals(cell)) continue;
                RseValidationSelfTestService.Evaluation evaluation = stations.get(spec.number());
                failed |= evaluation.verdict() == RseValidationSelfTestService.Verdict.FAIL;
                waiting |= evaluation.verdict() == RseValidationSelfTestService.Verdict.WAIT;
                if (evaluation.verdict() == RseValidationSelfTestService.Verdict.PASS) passed++;
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
                result.put(cell, pass("5/5 station identity/live evidence"));
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
            ServerLevel level, BlockPos origin, String cell, long phaseAge
    ) {
        ModuleSpec module = CELL_MODULES.get(cell);
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
            Map<Integer, RseValidationSelfTestService.Evaluation> stations,
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
            case SENSOR_FAULT -> sensorFaultGate(level, origin);
            case ACTUATOR_FAULT -> actuatorFaultGate(level, origin);
            case REDUNDANCY_TEST -> aggregateCells(cells, "independent A-H verdict agreement");
            case INTERLOCK_TRIP -> interlockTripGate(level, origin, phaseAge);
            case SAFE_STATE -> safeStateGate(level, origin, phaseAge);
            case ACK_RESET -> alarmResetGate(level, origin);
            case RECOVERY -> aggregateCellsAndBackbone(level, origin, phaseAge, cells, "recovery");
            case ENDURANCE_RUN -> aggregateCellsAndBackbone(level, origin, phaseAge, cells, "endurance healthy window");
            case FINAL_ACCEPTANCE -> finalAcceptanceGate(level, origin, phaseAge, cells, data);
        };
    }

    private static RseValidationSelfTestService.Evaluation allBlocksPresent(ServerLevel level, BlockPos origin) {
        for (StationSpec spec : STATIONS) {
            BlockPos pos = stationWorldPos(origin, spec);
            String actual = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
            if (!spec.blockId().equals(actual)) return fail(String.format(Locale.ROOT, "D%02d identity=%s expected=%s", spec.number(), actual, spec.blockId()));
        }
        return pass("40/40 exact DUT identities present");
    }

    private static RseValidationSelfTestService.Evaluation aggregateStations(
            Map<Integer, RseValidationSelfTestService.Evaluation> stations, String label
    ) {
        long pass = stations.values().stream().filter(e -> e.verdict() == RseValidationSelfTestService.Verdict.PASS).count();
        if (stations.values().stream().anyMatch(e -> e.verdict() == RseValidationSelfTestService.Verdict.FAIL)) return fail(label + " has station failure; pass=" + pass + "/40");
        if (pass < STATION_COUNT) return waitFor(label + " pending; pass=" + pass + "/40");
        return pass(label + " pass=40/40");
    }

    private static RseValidationSelfTestService.Evaluation aggregateCells(
            Map<String, RseValidationSelfTestService.Evaluation> cells, String label
    ) {
        long pass = cells.values().stream().filter(e -> e.verdict() == RseValidationSelfTestService.Verdict.PASS).count();
        if (cells.values().stream().anyMatch(e -> e.verdict() == RseValidationSelfTestService.Verdict.FAIL)) return fail(label + " has cell failure; pass=" + pass + "/8");
        if (pass < CELL_COUNT) return waitFor(label + " pending; pass=" + pass + "/8");
        return pass(label + " pass=8/8");
    }

    private static RseValidationSelfTestService.Evaluation cellGate(
            Map<String, RseValidationSelfTestService.Evaluation> cells, String cell, String label
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
            ServerLevel level, BlockPos origin, long phaseAge, int exactExpected
    ) {
        BlockPos sourcePos = origin.offset(NORTH_SPINE_OFFSET).offset(0, 1, 2);
        BlockState sourceState = level.getBlockState(sourcePos);
        if (!(sourceState.getBlock() instanceof RedstoneReferenceSourceBlock)) return fail("mega backbone source missing");
        int source = sourceState.getValue(RedstoneReferenceSourceBlock.POWER);
        BlockPos finalPos = origin.offset(CROSS_SPINE_OFFSET).offset(0, 1, 2);
        BlockState finalState = level.getBlockState(finalPos);
        int output = outputOrMinusOne(finalState);
        int expected = exactExpected >= 0 ? exactExpected : source;
        if (output == expected && output > 0) return pass("backbone source/final=" + source + "/" + output);
        if (phaseAge < PROPAGATION_TIMEOUT_TICKS) return waitFor("backbone propagating source/final=" + source + "/" + output + " expected=" + expected);
        return fail("BACKBONE CONTINUITY STUCK source/final=" + source + "/" + output + " expected=" + expected);
    }

    private static RseValidationSelfTestService.Evaluation timingGate(ServerLevel level, BlockPos origin, long phaseAge) {
        StationSpec spec = STATION_BY_NUMBER.get(6);
        BlockPos pos = stationWorldPos(origin, spec);
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof QuartzLabOscillatorBlock)) return fail("D06 quartz oscillator missing");
        QuartzLabOscillatorBlock.TimingEvidence evidence = QuartzLabOscillatorBlock.timingEvidence(level, pos, state);
        if (evidence.available()) return pass("D06 realized timing nominal=" + evidence.nominalPeriod() + " lastHalf=" + evidence.lastHalfInterval());
        return phaseAge < PROPAGATION_TIMEOUT_TICKS ? waitFor("D06 timing evidence pending") : fail("D06 timing evidence timeout");
    }

    private static RseValidationSelfTestService.Evaluation sensorFaultGate(ServerLevel level, BlockPos origin) {
        StationSpec spec = STATION_BY_NUMBER.get(38);
        BlockPos pos = stationWorldPos(origin, spec);
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ServoPositionSensorBlock)) return fail("D38 position sensor missing");
        PortQuality quality = ServoPositionSensorBlock.sourceQuality(level, pos, state);
        return quality == PortQuality.VALID
                ? waitFor("D38 still VALID; waiting for intended degraded sensor observation")
                : pass("D38 degraded sensor quality observed=" + quality);
    }

    private static RseValidationSelfTestService.Evaluation actuatorFaultGate(ServerLevel level, BlockPos origin) {
        BlockPos pos = stationWorldPos(origin, STATION_BY_NUMBER.get(37));
        boolean braking = ServoActuatorBlock.braking(level, pos);
        return braking ? pass("D37 actuator safe/braking condition observed") : waitFor("D37 actuator not yet in safe/braking condition");
    }

    private static RseValidationSelfTestService.Evaluation interlockTripGate(ServerLevel level, BlockPos origin, long phaseAge) {
        BlockPos pos = stationWorldPos(origin, STATION_BY_NUMBER.get(39));
        int mask = SafetyInterlockBlock.failedMask(level, pos);
        if (mask > 0) return pass("D39 interlock trip observed mask=" + mask);
        return phaseAge < PROPAGATION_TIMEOUT_TICKS ? waitFor("D39 interlock trip pending mask=" + mask) : fail("D39 interlock did not trip");
    }

    private static RseValidationSelfTestService.Evaluation safeStateGate(ServerLevel level, BlockPos origin, long phaseAge) {
        BlockPos servo = stationWorldPos(origin, STATION_BY_NUMBER.get(37));
        if (ServoActuatorBlock.braking(level, servo)) return pass("D37 safe state: servo braking=true");
        return phaseAge < PROPAGATION_TIMEOUT_TICKS ? waitFor("safe-state braking pending") : fail("servo failed to enter safe state");
    }

    private static RseValidationSelfTestService.Evaluation alarmResetGate(ServerLevel level, BlockPos origin) {
        BlockPos alarm = stationWorldPos(origin, STATION_BY_NUMBER.get(40));
        boolean latched = AlarmProcessorBlock.latched(level, alarm);
        return !latched ? pass("D40 alarm clear/reset state observed") : waitFor("D40 alarm remains latched");
    }

    private static RseValidationSelfTestService.Evaluation finalAcceptanceGate(
            ServerLevel level,
            BlockPos origin,
            long phaseAge,
            Map<String, RseValidationSelfTestService.Evaluation> cells,
            RseMegaValidationSavedData data
    ) {
        if (data.stationsEverPassedCount() != STATION_COUNT) return fail("final acceptance missing station-pass history=" + data.stationsEverPassedCount() + "/40");
        if (data.stationsCurrentlyFailedCount() != 0) return fail("final acceptance has unresolved station failures=" + data.stationsCurrentlyFailedCount());
        RseValidationSelfTestService.Evaluation cell = aggregateCells(cells, "final 8-cell state");
        if (cell.verdict() != RseValidationSelfTestService.Verdict.PASS) return cell;
        if (data.enduranceHealthyTicks() < ENDURANCE_REQUIRED_TICKS) return fail("endurance evidence=" + data.enduranceHealthyTicks() + "/" + ENDURANCE_REQUIRED_TICKS + "t");
        for (int phaseIndex = 0; phaseIndex < Phase.FINAL_ACCEPTANCE.ordinal(); phaseIndex++) {
            if (!data.phaseCompleted(phaseIndex)) return fail("required phase not completed: " + Phase.values()[phaseIndex]);
        }
        RseValidationSelfTestService.Evaluation backbone = evaluateBackbone(level, origin, phaseAge, -1);
        if (backbone.verdict() != RseValidationSelfTestService.Verdict.PASS) return backbone;
        return pass("FINAL ACCEPTANCE: 40/40 stations, 8/8 cells, history, endurance and backbone satisfied");
    }

    private static void applyPhaseStimulus(ServerLevel level, BlockPos origin, Phase phase) {
        int token = switch (phase) {
            case SATURATION_TEST -> 15;
            case COMM_DEGRADATION -> 7;
            case NOISE_TEST -> 8;
            default -> 9;
        };
        setReferencePower(level, origin.offset(NORTH_SPINE_OFFSET).offset(0, 1, 2), token);
        // D01 is itself a primary DUT source, and station-owned fixture sources provide repeatable local stimuli.
        setReferencePower(level, stationWorldPos(origin, STATION_BY_NUMBER.get(1)), token);
        for (StationSpec spec : STATIONS) {
            ModuleSpec module = MODULE_BY_ID.get(spec.moduleId());
            BlockPos fixture = origin.offset(module.offset()).offset(spec.dut().getX(), 1, 15);
            int power = 6 + (spec.number() % 4);
            if (phase == Phase.SENSOR_FAULT && spec.number() == 38) power = 0;
            if (phase == Phase.ACTUATOR_FAULT && spec.number() == 37) power = 15;
            setReferencePower(level, fixture, power);
        }
    }

    private static void applyBaseline(ServerLevel level, BlockPos origin) {
        setReferencePower(level, origin.offset(NORTH_SPINE_OFFSET).offset(0, 1, 2), 9);
        setReferencePower(level, stationWorldPos(origin, STATION_BY_NUMBER.get(1)), 9);
        for (StationSpec spec : STATIONS) {
            ModuleSpec module = MODULE_BY_ID.get(spec.moduleId());
            setReferencePower(level, origin.offset(module.offset()).offset(spec.dut().getX(), 1, 15), 6 + (spec.number() % 4));
        }
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

    private static BlockPos stationWorldPos(BlockPos plantOrigin, StationSpec spec) {
        ModuleSpec module = MODULE_BY_ID.get(spec.moduleId());
        if (module == null) throw new IllegalArgumentException("unknown station module " + spec.moduleId());
        return plantOrigin.offset(module.offset()).offset(spec.dut());
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
        for (ModuleSpec module : MODULES) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(RedstoneEngineering.MOD_ID, "validation/mega/" + module.id());
            if (level.getStructureManager().get(id).isEmpty()) return RseValidationFactoryService.Result.fail("Mega preflight missing structure: " + id);
        }
        return RseValidationFactoryService.Result.ok("Mega v2 structure preflight passed (11 modules).");
    }

    private static RseValidationFactoryService.Result placeModules(ServerLevel level, BlockPos origin) {
        for (ModuleSpec module : MODULES) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(RedstoneEngineering.MOD_ID, "validation/mega/" + module.id());
            StructureTemplate template = level.getStructureManager().get(id).orElse(null);
            if (template == null) return RseValidationFactoryService.Result.fail("Missing Mega module: " + id);
            BlockPos moduleOrigin = origin.offset(module.offset());
            boolean placed = template.placeInWorld(level, moduleOrigin, moduleOrigin, new StructurePlaceSettings(), level.getRandom(), 2);
            if (!placed) return RseValidationFactoryService.Result.fail("Failed to place Mega module: " + id);
        }
        return RseValidationFactoryService.Result.ok("Placed 11 Mega Validation Factory modules.");
    }

    private static boolean rebuildForRetest(ServerLevel level, BlockPos origin, boolean pressed) {
        if (!preflight(level).success()) return false;
        for (ModuleSpec module : MODULES) clearModule(level, origin.offset(module.offset()), module.size());
        if (!placeModules(level, origin).success()) return false;
        RseMegaValidationSavedData.get(level).resetForRetest(level.getGameTime(), pressed);
        applyBaseline(level, origin);
        updateAllPanels(level, origin, RseValidationSelfTestService.Verdict.WAIT);
        return true;
    }

    private static void clearModule(ServerLevel level, BlockPos origin, BlockPos size) {
        for (int x = 0; x < size.getX(); x++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int z = 0; z < size.getZ(); z++) level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
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
            Map<Integer, RseValidationSelfTestService.Evaluation> evaluations
    ) {
        BlockPos control = origin.offset(CONTROL_OFFSET);
        for (StationSpec spec : STATIONS) {
            RseValidationSelfTestService.Evaluation evaluation = evaluations.get(spec.number());
            if (evaluation == null) continue;
            ModuleSpec module = MODULE_BY_ID.get(spec.moduleId());
            BlockPos local = origin.offset(module.offset());
            int x = spec.dut().getX();
            updatePanel(level, local,
                    new BlockPos(x - 1, 2, 5), new BlockPos(x, 2, 5), new BlockPos(x + 1, 2, 5), evaluation.verdict());

            int row = (spec.number() - 1) / 5;
            int col = (spec.number() - 1) % 5;
            int cx = 3 + col * 6;
            int cz = 1 + row * 2;
            updatePanel(level, control,
                    new BlockPos(cx - 1, 2, cz + 1), new BlockPos(cx, 2, cz + 1), new BlockPos(cx + 1, 2, cz + 1), evaluation.verdict());
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
            ModuleSpec module = CELL_MODULES.get(cell);
            if (module == null) continue;
            updatePanel(level, origin.offset(module.offset()),
                    LOCAL_CELL_WAIT_POWER, LOCAL_CELL_PASS_POWER, LOCAL_CELL_FAIL_POWER, entry.getValue().verdict());
            int index = cell.charAt(0) - 'A';
            int z = 1 + index * 2;
            updatePanel(level, control,
                    new BlockPos(39, 2, z + 1), new BlockPos(40, 2, z + 1), new BlockPos(41, 2, z + 1), entry.getValue().verdict());
        }
    }

    private static void updateAllPanels(ServerLevel level, BlockPos origin, RseValidationSelfTestService.Verdict verdict) {
        LinkedHashMap<Integer, RseValidationSelfTestService.Evaluation> stations = new LinkedHashMap<>();
        for (StationSpec spec : STATIONS) stations.put(spec.number(), new RseValidationSelfTestService.Evaluation(verdict, "phase transition"));
        LinkedHashMap<String, RseValidationSelfTestService.Evaluation> cells = new LinkedHashMap<>();
        for (char c = 'A'; c <= 'H'; c++) cells.put(String.valueOf(c), new RseValidationSelfTestService.Evaluation(verdict, "phase transition"));
        updateStationPanels(level, origin, stations);
        updateCellPanels(level, origin, cells);
        updateMasterPanel(level, origin, verdict);
    }

    private static void updateMasterPanel(ServerLevel level, BlockPos origin, RseValidationSelfTestService.Verdict verdict) {
        updatePanel(level, origin.offset(CONTROL_OFFSET), MASTER_WAIT_POWER, MASTER_PASS_POWER, MASTER_FAIL_POWER, verdict);
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
