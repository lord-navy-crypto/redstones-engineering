package dev.redstoneengineering.validation;

import net.minecraft.core.BlockPos;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Authoritative identity/placement/dependency contract for Mega Validation Factory v2.1.
 *
 * <p>The Python structure generator carries a matching contract and the contract tests fail
 * closed when the two surfaces drift.</p>
 */
public final class RseMegaValidationTopology {
    public static final int STATION_COUNT = 40;
    public static final int CELL_COUNT = 8;

    public record Module(String id, String cell, BlockPos offset, BlockPos size) {}

    public record Station(
            int number,
            String cell,
            String moduleId,
            String blockId,
            BlockPos dut,
            String name,
            Integer upstreamStation
    ) {}

    private static final List<Module> MODULES = List.of(
            new Module("mega_control_hall", "", new BlockPos(35, 0, 0), new BlockPos(63, 7, 17)),
            new Module("mega_north_spine", "", new BlockPos(0, 0, 17), new BlockPos(133, 7, 3)),
            new Module("mega_cross_spine", "", new BlockPos(0, 0, 45), new BlockPos(133, 7, 3)),
            new Module("mega_cell_a_analog", "A", new BlockPos(0, 0, 20), new BlockPos(31, 7, 25)),
            new Module("mega_cell_b_timing", "B", new BlockPos(34, 0, 20), new BlockPos(31, 7, 25)),
            new Module("mega_cell_c_precision", "C", new BlockPos(68, 0, 20), new BlockPos(31, 7, 25)),
            new Module("mega_cell_d_data", "D", new BlockPos(102, 0, 20), new BlockPos(31, 7, 25)),
            new Module("mega_cell_e_comms", "E", new BlockPos(102, 0, 48), new BlockPos(31, 7, 25)),
            new Module("mega_cell_f_optical", "F", new BlockPos(68, 0, 48), new BlockPos(31, 7, 25)),
            new Module("mega_cell_g_pneumatic", "G", new BlockPos(34, 0, 48), new BlockPos(31, 7, 25)),
            new Module("mega_cell_h_control", "H", new BlockPos(0, 0, 48), new BlockPos(31, 7, 25))
    );

    private static final List<Station> STATIONS = List.of(
            new Station(1, "A", "mega_cell_a_analog", "redstoneengineering:redstone_reference_source", new BlockPos(3, 1, 12), "REF SOURCE", null),
            new Station(2, "A", "mega_cell_a_analog", "redstoneengineering:signal_probe", new BlockPos(9, 1, 12), "SIGNAL PROBE", 1),
            new Station(3, "A", "mega_cell_a_analog", "redstoneengineering:signal_conditioner", new BlockPos(15, 1, 12), "CONDITIONER", 2),
            new Station(4, "A", "mega_cell_a_analog", "redstoneengineering:precision_filter", new BlockPos(21, 1, 12), "PREC FILTER", 3),
            new Station(5, "A", "mega_cell_a_analog", "redstoneengineering:signal_analyzer", new BlockPos(27, 1, 12), "ANALYZER", 4),

            new Station(6, "B", "mega_cell_b_timing", "redstoneengineering:quartz_lab_oscillator", new BlockPos(3, 1, 12), "QUARTZ OSC", null),
            new Station(7, "B", "mega_cell_b_timing", "redstoneengineering:quartz_clock_divider", new BlockPos(9, 1, 12), "CLOCK DIV", 6),
            new Station(8, "B", "mega_cell_b_timing", "redstoneengineering:edge_detector", new BlockPos(15, 1, 12), "EDGE DETECT", 7),
            new Station(9, "B", "mega_cell_b_timing", "redstoneengineering:pulse_shaper", new BlockPos(21, 1, 12), "PULSE SHAPE", 8),
            new Station(10, "B", "mega_cell_b_timing", "redstoneengineering:pwm_controller", new BlockPos(27, 1, 12), "PWM CTRL", 9),

            new Station(11, "C", "mega_cell_c_precision", "redstoneengineering:lapis_noise_source", new BlockPos(3, 1, 12), "NOISE SOURCE", null),
            new Station(12, "C", "mega_cell_c_precision", "redstoneengineering:lapis_low_pass_filter", new BlockPos(9, 1, 12), "LAPIS LPF", 11),
            new Station(13, "C", "mega_cell_c_precision", "redstoneengineering:lapis_precision_meter", new BlockPos(15, 1, 12), "PREC METER", 12),
            new Station(14, "C", "mega_cell_c_precision", "redstoneengineering:quartz_triggered_lapis_sampler", new BlockPos(21, 1, 12), "QZ SAMPLER", 13),
            new Station(15, "C", "mega_cell_c_precision", "redstoneengineering:lapis_to_redstone_quantizer", new BlockPos(27, 1, 12), "QUANTIZER", 14),

            new Station(16, "D", "mega_cell_d_data", "redstoneengineering:redstone_byte_encoder", new BlockPos(3, 1, 12), "BYTE ENC", null),
            new Station(17, "D", "mega_cell_d_data", "redstoneengineering:eight_bit_data_bus", new BlockPos(9, 1, 12), "8BIT BUS", 16),
            new Station(18, "D", "mega_cell_d_data", "redstoneengineering:serializer", new BlockPos(15, 1, 12), "SERIALIZER", 17),
            new Station(19, "D", "mega_cell_d_data", "redstoneengineering:serial_data_line", new BlockPos(21, 1, 12), "SERIAL LINE", 18),
            new Station(20, "D", "mega_cell_d_data", "redstoneengineering:deserializer", new BlockPos(27, 1, 12), "DESERIALIZER", 19),

            new Station(21, "E", "mega_cell_e_comms", "redstoneengineering:differential_driver", new BlockPos(3, 1, 12), "DIFF DRIVER", null),
            new Station(22, "E", "mega_cell_e_comms", "redstoneengineering:differential_data_pair", new BlockPos(9, 1, 12), "DIFF PAIR", 21),
            new Station(23, "E", "mega_cell_e_comms", "redstoneengineering:digital_regenerator", new BlockPos(15, 1, 12), "REGENERATOR", 22),
            new Station(24, "E", "mega_cell_e_comms", "redstoneengineering:differential_receiver", new BlockPos(21, 1, 12), "DIFF RX", 23),
            new Station(25, "E", "mega_cell_e_comms", "redstoneengineering:watchdog", new BlockPos(27, 1, 12), "WATCHDOG", 24),

            new Station(26, "F", "mega_cell_f_optical", "redstoneengineering:optical_emitter", new BlockPos(3, 1, 12), "OPT EMITTER", null),
            new Station(27, "F", "mega_cell_f_optical", "redstoneengineering:optical_fiber", new BlockPos(9, 1, 12), "OPT FIBER", 26),
            new Station(28, "F", "mega_cell_f_optical", "redstoneengineering:optical_splitter", new BlockPos(15, 1, 12), "OPT SPLITTER", 27),
            new Station(29, "F", "mega_cell_f_optical", "redstoneengineering:optical_channel_filter", new BlockPos(21, 1, 12), "OPT FILTER", 28),
            new Station(30, "F", "mega_cell_f_optical", "redstoneengineering:optical_receiver", new BlockPos(27, 1, 12), "OPT RX", 29),

            new Station(31, "G", "mega_cell_g_pneumatic", "redstoneengineering:air_compressor", new BlockPos(3, 1, 12), "COMPRESSOR", null),
            new Station(32, "G", "mega_cell_g_pneumatic", "redstoneengineering:air_reservoir", new BlockPos(9, 1, 12), "RESERVOIR", 31),
            new Station(33, "G", "mega_cell_g_pneumatic", "redstoneengineering:pressure_regulator", new BlockPos(15, 1, 12), "REGULATOR", 32),
            new Station(34, "G", "mega_cell_g_pneumatic", "redstoneengineering:pneumatic_proportional_valve", new BlockPos(21, 1, 12), "PROP VALVE", 33),
            new Station(35, "G", "mega_cell_g_pneumatic", "redstoneengineering:pneumatic_cylinder", new BlockPos(27, 1, 12), "CYLINDER", 34),

            new Station(36, "H", "mega_cell_h_control", "redstoneengineering:pid_controller", new BlockPos(3, 1, 12), "PID CTRL", null),
            new Station(37, "H", "mega_cell_h_control", "redstoneengineering:servo_actuator", new BlockPos(9, 1, 12), "SERVO", 36),
            new Station(38, "H", "mega_cell_h_control", "redstoneengineering:servo_position_sensor", new BlockPos(15, 1, 12), "POS SENSOR", 37),
            new Station(39, "H", "mega_cell_h_control", "redstoneengineering:safety_interlock", new BlockPos(21, 1, 12), "INTERLOCK", 38),
            new Station(40, "H", "mega_cell_h_control", "redstoneengineering:alarm_processor", new BlockPos(27, 1, 12), "ALARM", 39)
    );

    private static final Map<String, Module> MODULE_BY_ID;
    private static final Map<String, Module> CELL_MODULES;
    private static final Map<Integer, Station> STATION_BY_NUMBER;

    static {
        LinkedHashMap<String, Module> modules = new LinkedHashMap<>();
        LinkedHashMap<String, Module> cells = new LinkedHashMap<>();
        for (Module module : MODULES) {
            modules.put(module.id(), module);
            if (!module.cell().isBlank()) cells.put(module.cell(), module);
        }
        MODULE_BY_ID = Map.copyOf(modules);
        CELL_MODULES = Map.copyOf(cells);

        LinkedHashMap<Integer, Station> stations = new LinkedHashMap<>();
        for (Station station : STATIONS) stations.put(station.number(), station);
        STATION_BY_NUMBER = Map.copyOf(stations);
    }

    private RseMegaValidationTopology() {}

    public static List<Module> modules() { return MODULES; }
    public static List<Station> stations() { return STATIONS; }
    public static Map<String, Module> cellModules() { return CELL_MODULES; }
    public static Module module(String id) { return MODULE_BY_ID.get(id); }
    public static Station station(int number) { return STATION_BY_NUMBER.get(number); }

    public static BlockPos stationWorldPos(BlockPos plantOrigin, Station station) {
        Module module = MODULE_BY_ID.get(station.moduleId());
        if (module == null) throw new IllegalArgumentException("unknown station module " + station.moduleId());
        return plantOrigin.offset(module.offset()).offset(station.dut());
    }

    public static Integer upstreamStation(int stationNumber) {
        Station station = STATION_BY_NUMBER.get(stationNumber);
        return station == null ? null : station.upstreamStation();
    }
}
