from __future__ import annotations

from collections import Counter
import json
from pathlib import Path
import re
import tempfile
import unittest

from tools import rse_validation_factory as factory
from tools import rse_mega_factory as mega

ROOT = Path(__file__).resolve().parents[1]
JAVA_EVALUATOR = ROOT / "src/main/java/dev/redstoneengineering/validation/RseMegaStationEvaluator.java"
JAVA_TOPOLOGY = ROOT / "src/main/java/dev/redstoneengineering/validation/RseMegaValidationTopology.java"

EXPECTED_CELLS = tuple("ABCDEFGH")
EXPECTED_MODULES = (
    "mega_control_hall",
    "mega_north_spine",
    "mega_cross_spine",
    "mega_cell_a_analog",
    "mega_cell_b_timing",
    "mega_cell_c_precision",
    "mega_cell_d_data",
    "mega_cell_e_comms",
    "mega_cell_f_optical",
    "mega_cell_g_pneumatic",
    "mega_cell_h_control",
)
EXPECTED_DUTS = (
    "redstone_reference_source", "signal_probe", "signal_conditioner", "precision_filter", "signal_analyzer",
    "quartz_lab_oscillator", "quartz_clock_divider", "edge_detector", "pulse_shaper", "pwm_controller",
    "lapis_noise_source", "lapis_low_pass_filter", "lapis_precision_meter", "quartz_triggered_lapis_sampler", "lapis_to_redstone_quantizer",
    "redstone_byte_encoder", "eight_bit_data_bus", "serializer", "serial_data_line", "deserializer",
    "differential_driver", "differential_data_pair", "digital_regenerator", "differential_receiver", "watchdog",
    "optical_emitter", "optical_fiber", "optical_splitter", "optical_channel_filter", "optical_receiver",
    "air_compressor", "air_reservoir", "pressure_regulator", "pneumatic_proportional_valve", "pneumatic_cylinder",
    "pid_controller", "servo_actuator", "servo_position_sensor", "safety_interlock", "alarm_processor",
)
EXPECTED_PHASES = (
    "STRUCTURE_PRECHECK", "STATION_BIST", "CELL_ACCEPTANCE", "CHAIN_CONTINUITY", "NOMINAL_STARTUP",
    "TIMING_TEST", "NOISE_TEST", "SATURATION_TEST", "DATA_INTEGRITY_TEST", "COMM_DEGRADATION",
    "PROCESS_LOAD", "SENSOR_FAULT", "ACTUATOR_FAULT", "REDUNDANCY_TEST", "INTERLOCK_TRIP",
    "SAFE_STATE", "ACK_RESET", "RECOVERY", "ENDURANCE_RUN", "FINAL_ACCEPTANCE",
)


class MegaValidationFactoryTests(unittest.TestCase):
    def _block_id(self, block: factory.BlockSpec) -> str:
        return factory._normalize_block_spec(block)[0]

    def _props(self, block: factory.BlockSpec) -> dict[str, str]:
        return dict(factory._normalize_block_spec(block)[1])

    def _sign_lines(self, block: factory.BlockSpec) -> tuple[str, ...]:
        nbt = factory._block_nbt(block)
        self.assertIsNotNone(nbt)
        return tuple(json.loads(message).get("text", "") for message in nbt["front_text"]["messages"])

    def _signs(self, placements: list[factory.Placement]) -> list[tuple[str, ...]]:
        return [self._sign_lines(block) for _pos, block in placements if self._block_id(block).endswith("_sign")]

    def _cell_by_pos(self, cell: str) -> dict[tuple[int, int, int], factory.BlockSpec]:
        module_id = mega.CELL_DEFINITIONS[cell][0]
        _size, placements = mega.MEGA_STRUCTURES[module_id]()
        return dict(placements)

    def _station(self, number: int):
        return next(station for station in mega.MEGA_STATIONS if station.number == number)

    def test_exactly_forty_unique_stations_grouped_five_per_cell(self) -> None:
        self.assertEqual(len(mega.MEGA_STATIONS), 40)
        self.assertEqual([station.number for station in mega.MEGA_STATIONS], list(range(1, 41)))
        self.assertEqual([station.block_id.removeprefix("redstoneengineering:") for station in mega.MEGA_STATIONS], list(EXPECTED_DUTS))
        counts = Counter(station.cell for station in mega.MEGA_STATIONS)
        self.assertEqual(counts, Counter({cell: 5 for cell in EXPECTED_CELLS}))
        self.assertEqual(set(mega.MEGA_CELL_STATIONS), set(EXPECTED_CELLS))
        for cell in EXPECTED_CELLS:
            self.assertEqual(len(mega.MEGA_CELL_STATIONS[cell]), 5)

    def test_eleven_non_overlapping_modules_form_giant_footprint(self) -> None:
        self.assertEqual(tuple(mega.MEGA_STRUCTURE_ORDER), EXPECTED_MODULES)
        self.assertEqual(set(mega.MEGA_STRUCTURES), set(EXPECTED_MODULES))
        boxes = []
        max_x = max_z = 0
        for module_id in EXPECTED_MODULES:
            ox, _oy, oz = mega.MEGA_MODULE_OFFSETS[module_id]
            size = mega.MEGA_MODULE_SIZES[module_id]
            self.assertEqual(size, mega.MEGA_STRUCTURES[module_id]()[0])
            x2 = ox + size[0] - 1
            z2 = oz + size[2] - 1
            max_x = max(max_x, x2)
            max_z = max(max_z, z2)
            boxes.append((module_id, ox, oz, x2, z2))
        self.assertGreaterEqual(max_x + 1, 130)
        self.assertGreaterEqual(max_z + 1, 70)
        for i, left in enumerate(boxes):
            for right in boxes[i + 1:]:
                separated = left[3] < right[1] or right[3] < left[1] or left[4] < right[2] or right[4] < left[2]
                self.assertTrue(separated, f"mega modules overlap: {left[0]} / {right[0]}")

    def test_every_module_clears_full_template_volume(self) -> None:
        for module_id in EXPECTED_MODULES:
            size, placements = mega.MEGA_STRUCTURES[module_id]()
            self.assertEqual(len(placements), size[0] * size[1] * size[2], module_id)

    def test_every_station_has_dut_identity_sign_and_three_color_panel(self) -> None:
        for station in mega.MEGA_STATIONS:
            size, placements = mega.MEGA_STRUCTURES[station.module_id]()
            by_pos = dict(placements)
            self.assertEqual(self._block_id(by_pos[station.dut_pos]), station.block_id)
            signs = self._signs(placements)
            expected_id = f"D{station.number:02d}"
            self.assertTrue(any(lines[0] == expected_id and station.short_name.upper() in lines[1].upper() for lines in signs), expected_id)
            panel = station.panel
            for lamp, base_id in (
                (panel.wait_lamp, "minecraft:yellow_concrete"),
                (panel.pass_lamp, "minecraft:lime_concrete"),
                (panel.fail_lamp, "minecraft:red_concrete"),
            ):
                x, y, z = lamp
                self.assertEqual(self._block_id(by_pos[(x, y - 1, z)]), base_id)
                self.assertEqual(self._block_id(by_pos[lamp]), "minecraft:redstone_lamp")
            self.assertTrue(all(0 <= coordinate < bound for coordinate, bound in zip(station.dut_pos, size)))

    def test_station_coordinates_are_unique_inside_each_module(self) -> None:
        seen: set[tuple[str, tuple[int, int, int]]] = set()
        for station in mega.MEGA_STATIONS:
            key = (station.module_id, station.dut_pos)
            self.assertNotIn(key, seen)
            seen.add(key)

    def test_control_hall_contains_forty_station_lamps_eight_cell_panels_and_master_retest(self) -> None:
        _size, placements = mega.MEGA_STRUCTURES["mega_control_hall"]()
        by_pos = dict(placements)
        self.assertEqual(len(mega.CONTROL_STATION_PANELS), 40)
        self.assertEqual(set(mega.CONTROL_CELL_PANELS), set(EXPECTED_CELLS))
        self.assertEqual(self._block_id(by_pos[mega.MEGA_MASTER_RETEST_BUTTON]), "minecraft:stone_button")
        for panel in mega.CONTROL_STATION_PANELS.values():
            self.assertIn(panel.wait_lamp, by_pos)
            self.assertIn(panel.pass_lamp, by_pos)
            self.assertIn(panel.fail_lamp, by_pos)
        for panel in mega.CONTROL_CELL_PANELS.values():
            self.assertIn(panel.wait_lamp, by_pos)
            self.assertIn(panel.pass_lamp, by_pos)
            self.assertIn(panel.fail_lamp, by_pos)

    def test_cells_have_industrial_frames_lighting_and_maintenance_lanes(self) -> None:
        for module_id in [name for name in EXPECTED_MODULES if "mega_cell_" in name]:
            size, placements = mega.MEGA_STRUCTURES[module_id]()
            by_pos = dict(placements)
            width, height, depth = size
            self.assertEqual(height, 7)
            for x, z in ((0, 0), (width - 1, 0), (0, depth - 1), (width - 1, depth - 1)):
                for y in range(1, height):
                    self.assertNotEqual(self._block_id(by_pos[(x, y, z)]), "minecraft:air")
            self.assertGreaterEqual(sum(self._block_id(block) == "minecraft:sea_lantern" for _pos, block in placements), 5)
            self.assertGreaterEqual(sum(self._block_id(block) == "minecraft:light_gray_concrete" for _pos, block in placements), 40)

    def test_generator_emits_all_mega_assets(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            structure_dir = Path(directory) / "mega"
            index_path = Path(directory) / "mega-index.txt"
            self.assertEqual(factory.generate_mega(structure_dir, index_path), 0)
            emitted = sorted(path.stem for path in structure_dir.glob("*.nbt"))
            self.assertEqual(emitted, sorted(EXPECTED_MODULES))
            index = index_path.read_text(encoding="utf-8")
            self.assertIn("40 DUT", index)
            for number in range(1, 41):
                self.assertIn(f"D{number:02d}", index)

    def test_all_selected_dut_ids_are_registered_blocks(self) -> None:
        registry = "\n".join((
            (ROOT / "src/main/java/dev/redstoneengineering/RedstoneEngineering.java").read_text(encoding="utf-8"),
            (ROOT / "src/main/java/dev/redstoneengineering/EngineeringSystemsModule.java").read_text(encoding="utf-8"),
        ))
        for dut in EXPECTED_DUTS:
            self.assertRegex(registry, rf'BLOCKS\.registerBlock\(\s*"{re.escape(dut)}"', dut)

    def test_analog_cell_has_real_main_chain_and_noninvasive_probe_tap(self) -> None:
        by_pos = self._cell_by_pos("A")
        probe_props = self._props(by_pos[self._station(2).dut_pos])
        self.assertEqual(probe_props.get("facing"), "south")
        for station in (3, 4):
            props = self._props(by_pos[self._station(station).dut_pos])
            self.assertEqual((props.get("facing"), props.get("input_facing")), ("east", "west"), station)
        analyzer = self._props(by_pos[self._station(5).dut_pos])
        self.assertEqual((analyzer.get("facing"), analyzer.get("mode")), ("west", "1"))
        for pos in ((4, 1, 12), (8, 1, 12), (8, 1, 13), (9, 1, 13), (14, 1, 13), (14, 1, 12),
                    (16, 1, 12), (20, 1, 12), (22, 1, 12), (26, 1, 12), (28, 1, 12)):
            self.assertEqual(self._block_id(by_pos[pos]), "redstoneengineering:redstone_signal_cable", pos)
        self.assertEqual(self._block_id(by_pos[(9, 1, 13)]), "redstoneengineering:redstone_signal_cable")

    def test_timing_cell_has_real_quartz_lane_and_redstone_waveform_lane(self) -> None:
        by_pos = self._cell_by_pos("B")
        osc = self._props(by_pos[self._station(6).dut_pos])
        divider = self._props(by_pos[self._station(7).dut_pos])
        self.assertEqual(osc.get("facing"), "east")
        self.assertEqual((divider.get("facing"), divider.get("input_facing")), ("east", "west"))
        for x in tuple(range(4, 9)) + tuple(range(10, 15)):
            self.assertEqual(self._block_id(by_pos[(x, 1, 12)]), "redstoneengineering:quartz_timing_line", x)
        self.assertEqual(self._block_id(by_pos[(14, 1, 13)]), "redstoneengineering:redstone_reference_source")
        for station in (8, 9, 10):
            props = self._props(by_pos[self._station(station).dut_pos])
            self.assertEqual((props.get("facing"), props.get("input_facing")), ("east", "west"), station)
        for x in tuple(range(16, 21)) + tuple(range(22, 27)):
            self.assertEqual(self._block_id(by_pos[(x, 1, 12)]), "redstoneengineering:redstone_signal_cable", x)
        self.assertEqual(self._block_id(by_pos[(27, 1, 11)]), "minecraft:air", "PWM inhibit face must remain isolated")

    def test_precision_cell_has_lapis_chain_meter_tap_and_real_quartz_trigger(self) -> None:
        by_pos = self._cell_by_pos("C")
        source = self._props(by_pos[self._station(11).dut_pos])
        lpf = self._props(by_pos[self._station(12).dut_pos])
        meter = self._props(by_pos[self._station(13).dut_pos])
        sampler = self._props(by_pos[self._station(14).dut_pos])
        quantizer = self._props(by_pos[self._station(15).dut_pos])
        self.assertEqual(source.get("facing"), "east")
        self.assertEqual((lpf.get("facing"), lpf.get("input_facing")), ("east", "west"))
        self.assertEqual(meter.get("facing"), "south")
        self.assertEqual((sampler.get("facing"), sampler.get("input_facing")), ("east", "west"))
        self.assertEqual((quantizer.get("facing"), quantizer.get("input_facing")), ("east", "west"))
        for pos in ((4, 1, 12), (8, 1, 12), (10, 1, 12), (14, 1, 12), (14, 1, 13), (15, 1, 13),
                    (20, 1, 13), (20, 1, 12), (22, 1, 12), (26, 1, 12)):
            self.assertEqual(self._block_id(by_pos[pos]), "redstoneengineering:lapis_signal_line", pos)
        self.assertEqual(self._block_id(by_pos[(21, 1, 10)]), "redstoneengineering:quartz_lab_oscillator")
        self.assertEqual(self._props(by_pos[(21, 1, 10)]).get("facing"), "south")
        self.assertEqual(self._block_id(by_pos[(21, 1, 11)]), "redstoneengineering:quartz_timing_line")

    def test_digital_cell_uses_real_bus_and_serial_media(self) -> None:
        by_pos = self._cell_by_pos("D")
        for station, facing, input_facing in ((16, "east", "west"), (18, "east", "west"), (20, "east", "west")):
            props = self._props(by_pos[self._station(station).dut_pos])
            self.assertEqual(props.get("facing"), facing)
            self.assertEqual(props.get("input_facing"), input_facing)
        for pos in ((4, 1, 12), (8, 1, 12), (10, 1, 12), (14, 1, 12), (28, 1, 12)):
            self.assertEqual(self._block_id(by_pos[pos]), "redstoneengineering:eight_bit_data_bus", pos)
        for pos in ((16, 1, 12), (20, 1, 12), (22, 1, 12), (26, 1, 12)):
            self.assertEqual(self._block_id(by_pos[pos]), "redstoneengineering:serial_data_line", pos)

    def test_robust_comms_cell_has_real_differential_serial_and_heartbeat_paths(self) -> None:
        by_pos = self._cell_by_pos("E")
        for station in (21, 23, 24, 25):
            props = self._props(by_pos[self._station(station).dut_pos])
            self.assertEqual(props.get("facing"), "east", station)
            self.assertEqual(props.get("input_facing"), "west", station)
        self.assertEqual(self._block_id(by_pos[(4, 1, 12)]), "redstoneengineering:differential_data_pair")
        self.assertEqual(self._block_id(by_pos[(20, 1, 12)]), "redstoneengineering:differential_data_pair")
        self.assertEqual(self._block_id(by_pos[(14, 1, 12)]), "redstoneengineering:serial_data_line")
        self.assertEqual(self._block_id(by_pos[(16, 1, 12)]), "redstoneengineering:serial_data_line")
        for x in range(22, 27):
            self.assertEqual(self._block_id(by_pos[(x, 1, 12)]), "redstoneengineering:redstone_signal_cable", x)
        self.assertTrue(any(self._block_id(block) == "redstoneengineering:serializer" for block in by_pos.values()))
        self.assertTrue(any(self._block_id(block) == "redstoneengineering:deserializer" for block in by_pos.values()))

    def test_optical_cell_is_one_real_carrier_path_with_splitter_branch(self) -> None:
        by_pos = self._cell_by_pos("F")
        for pos in ((4, 1, 12), (8, 1, 12), (10, 1, 12), (14, 1, 12),
                    (16, 1, 12), (20, 1, 12), (22, 1, 12), (26, 1, 12), (15, 1, 11)):
            self.assertEqual(self._block_id(by_pos[pos]), "redstoneengineering:optical_fiber", pos)
        splitter = self._props(by_pos[self._station(28).dut_pos])
        optical_filter = self._props(by_pos[self._station(29).dut_pos])
        self.assertEqual((splitter.get("facing"), splitter.get("input_facing")), ("east", "west"))
        self.assertEqual((optical_filter.get("facing"), optical_filter.get("input_facing")), ("east", "west"))
        self.assertEqual(optical_filter.get("target"), "0")
        self.assertEqual(self._block_id(by_pos[(15, 1, 10)]), "redstoneengineering:optical_receiver")

    def test_pneumatic_cell_has_commanded_compressor_and_continuous_pressure_path(self) -> None:
        by_pos = self._cell_by_pos("G")
        self.assertEqual(self._block_id(by_pos[(3, 0, 12)]), "minecraft:redstone_block")
        self.assertEqual(self._block_id(by_pos[(3, 2, 12)]), "redstoneengineering:pneumatic_pipe")
        self.assertEqual(self._block_id(by_pos[(9, 2, 12)]), "redstoneengineering:pneumatic_pipe")
        for x in tuple(range(10, 15)) + tuple(range(16, 21)) + tuple(range(22, 27)):
            self.assertEqual(self._block_id(by_pos[(x, 1, 12)]), "redstoneengineering:pneumatic_pipe", x)
        for station in (33, 34, 35):
            props = self._props(by_pos[self._station(station).dut_pos])
            self.assertEqual((props.get("facing"), props.get("input_facing")), ("east", "west"), station)
        self.assertEqual(self._block_id(by_pos[(21, 2, 12)]), "minecraft:redstone_block")

    def test_h_cell_closes_pid_servo_sensor_mechanical_loop(self) -> None:
        by_pos = self._cell_by_pos("H")
        servo = self._station(37)
        sensor = self._station(38)
        self.assertEqual(sum(abs(a - b) for a, b in zip(servo.dut_pos, sensor.dut_pos)), 1)
        servo_props = self._props(by_pos[servo.dut_pos])
        sensor_props = self._props(by_pos[sensor.dut_pos])
        self.assertEqual(servo_props.get("facing"), "east")
        self.assertEqual(sensor_props.get("input_facing"), "west")
        self.assertEqual(sensor_props.get("facing"), "north")
        pid_props = self._props(by_pos[self._station(36).dut_pos])
        self.assertEqual((pid_props.get("facing"), pid_props.get("input_facing")), ("east", "west"))
        self.assertEqual(self._block_id(by_pos[(2, 1, 12)]), "redstoneengineering:redstone_reference_source")
        for x in range(4, 9):
            self.assertEqual(self._block_id(by_pos[(x, 1, 12)]), "redstoneengineering:redstone_signal_cable", x)
        self.assertEqual(self._block_id(by_pos[(10, 1, 11)]), "redstoneengineering:redstone_signal_cable")
        self.assertEqual(self._block_id(by_pos[(3, 1, 11)]), "redstoneengineering:redstone_signal_cable")
        for x in range(3, 11):
            self.assertEqual(self._block_id(by_pos[(x, 1, 10)]), "redstoneengineering:redstone_signal_cable", x)
        for x in range(4, 9):
            self.assertNotEqual(self._block_id(by_pos[(x, 1, 11)]), "redstoneengineering:redstone_signal_cable", "feedback must not short into command bus")

    def test_h_cell_interlock_trip_physically_drives_brake_and_alarm(self) -> None:
        by_pos = self._cell_by_pos("H")
        interlock = self._station(39)
        alarm = self._station(40)
        interlock_props = self._props(by_pos[interlock.dut_pos])
        alarm_props = self._props(by_pos[alarm.dut_pos])
        self.assertEqual((interlock_props.get("facing"), interlock_props.get("input_facing")), ("west", "east"))
        self.assertEqual((alarm_props.get("facing"), alarm_props.get("input_facing")), ("west", "east"))
        expected_sources = {
            (22, 1, 12): ("west", "15"),
            (21, 1, 13): ("north", "15"),
            (21, 1, 11): ("south", "15"),
            (27, 1, 13): ("north", "0"),
            (27, 1, 11): ("south", "0"),
        }
        for pos, (facing, power) in expected_sources.items():
            self.assertEqual(self._block_id(by_pos[pos]), "redstoneengineering:redstone_reference_source", pos)
            props = self._props(by_pos[pos])
            self.assertEqual((props.get("facing"), props.get("power")), (facing, power), pos)
        self.assertEqual(self._block_id(by_pos[(20, 1, 12)]), "minecraft:stone")
        self.assertEqual(self._block_id(by_pos[(20, 1, 13)]), "minecraft:redstone_wall_torch")
        self.assertEqual(self._block_id(by_pos[(19, 1, 13)]), "redstoneengineering:redstone_cable_terminal")
        self.assertNotEqual(self._props(by_pos[(19, 1, 13)]).get("output_mode"), "true")
        self.assertEqual(self._block_id(by_pos[(9, 1, 13)]), "redstoneengineering:redstone_cable_terminal")
        self.assertEqual(self._props(by_pos[(9, 1, 13)]).get("output_mode"), "true")
        self.assertEqual(self._block_id(by_pos[(28, 1, 12)]), "redstoneengineering:redstone_cable_terminal")
        self.assertEqual(self._props(by_pos[(28, 1, 12)]).get("output_mode"), "true")
        self.assertGreaterEqual(sum(self._block_id(block) == "redstoneengineering:redstone_signal_cable" for block in by_pos.values()), 20)

    def test_old_unrelated_z15_station_fixture_strip_is_removed(self) -> None:
        for cell in EXPECTED_CELLS:
            by_pos = self._cell_by_pos(cell)
            for station in mega.MEGA_CELL_STATIONS[cell]:
                pos = (station.sign_pos[0], 1, 15)
                self.assertNotEqual(self._block_id(by_pos[pos]), "redstoneengineering:redstone_reference_source", (cell, station.number, pos))

    def test_java_runtime_defines_twenty_phase_hierarchy_and_history(self) -> None:
        service = (ROOT / "src/main/java/dev/redstoneengineering/validation/RseMegaValidationService.java").read_text(encoding="utf-8")
        saved = (ROOT / "src/main/java/dev/redstoneengineering/validation/RseMegaValidationSavedData.java").read_text(encoding="utf-8")
        for phase in EXPECTED_PHASES:
            self.assertIn(phase, service)
        for field in ("stationVerdict", "stationDetail", "stationEverPassed", "stationEverFailed", "firstFailurePhase", "cellVerdict", "completedPhases", "enduranceHealthyTicks"):
            self.assertIn(field, saved)
        self.assertIn("recordStation", saved)
        self.assertIn("recordCell", saved)
        self.assertIn("markPhaseComplete", saved)

    def test_java_runtime_contains_exactly_forty_station_specs_and_eight_cells(self) -> None:
        service = (ROOT / "src/main/java/dev/redstoneengineering/validation/RseMegaValidationService.java").read_text(encoding="utf-8")
        topology = JAVA_TOPOLOGY.read_text(encoding="utf-8")
        self.assertIn("STATION_COUNT = 40", service)
        self.assertIn("CELL_COUNT = 8", service)
        self.assertIn("STATION_COUNT = 40", topology)
        self.assertIn("CELL_COUNT = 8", topology)
        for number, dut in enumerate(EXPECTED_DUTS, 1):
            self.assertIn(f'new Station({number},', topology)
            self.assertIn(f'"redstoneengineering:{dut}"', topology)
        for cell in EXPECTED_CELLS:
            self.assertIn(f'"{cell}"', topology)

    def test_java_topology_declares_station_dependencies(self) -> None:
        topology = JAVA_TOPOLOGY.read_text(encoding="utf-8")
        self.assertIn("upstreamStation", topology)
        for start in (1, 6, 11, 16, 21, 26, 31, 36):
            self.assertRegex(topology, rf'new Station\({start},.*null\)')
        for station in (2, 3, 4, 5, 7, 8, 9, 10, 12, 13, 14, 15, 17, 18, 19, 20, 22, 23, 24, 25, 27, 28, 29, 30, 32, 33, 34, 35, 37, 38, 39, 40):
            self.assertRegex(topology, rf'new Station\({station},.*\d+\)')

    def test_command_surface_and_server_tick_wire_mega_runtime(self) -> None:
        module = (ROOT / "src/main/java/dev/redstoneengineering/validation/RseValidationFactoryModule.java").read_text(encoding="utf-8")
        self.assertIn('Commands.literal("mega")', module)
        for literal in ("place", "status", "station", "cell", "report", "retest", "diagnose"):
            self.assertIn(f'Commands.literal("{literal}")', module)
        self.assertIn("RseMegaValidationService.tick(", module)
        self.assertIn("RseMegaValidationService.place(", module)
        self.assertIn("RseMegaValidationService.station(", module)
        self.assertIn("RseMegaValidationService.cell(", module)
        self.assertIn("RseMegaValidationService.report(", module)
        self.assertIn("RseMegaValidationService.diagnose(", module)

    def test_mega_diagnose_is_full_factory_live_dump_with_problem_first_sections(self) -> None:
        service = (ROOT / "src/main/java/dev/redstoneengineering/validation/RseMegaValidationService.java").read_text(encoding="utf-8")
        self.assertIn("public static RseValidationFactoryService.Result diagnose(ServerLevel level)", service)
        self.assertIn("evaluateStations(", service)
        self.assertIn("evaluateCells(", service)
        for heading in (
            "MEGA FACTORY FULL DIAGNOSIS",
            "CURRENT BLOCKERS",
            "CELL SUMMARY",
            "ALL 40 DUT",
            "HISTORY",
            "ACCEPTANCE",
        ):
            self.assertIn(heading, service)
        self.assertIn("stationsCurrentlyFailedCount()", service)
        self.assertIn("stationsEverPassedCount()", service)
        self.assertIn("completedPhases()", service)
        self.assertIn("enduranceHealthyTicks()", service)
        self.assertIn("firstFailurePhase(", service)
        self.assertIn("firstFailureDetail(", service)

    def test_normal_quality_policy_rejects_false_green_states(self) -> None:
        self.assertTrue(JAVA_EVALUATOR.exists(), "phase-aware mega station evaluator must exist")
        source = JAVA_EVALUATOR.read_text(encoding="utf-8")
        for token in (
            "case NO_SIGNAL",
            "case FAULT",
            "case DOMAIN_MISMATCH",
            "case TOPOLOGY_ERROR",
            "case STALE",
        ):
            self.assertIn(token, source)
        self.assertNotIn("quality() != PortQuality.STALE", source)
        service = (ROOT / "src/main/java/dev/redstoneengineering/validation/RseMegaValidationService.java").read_text(encoding="utf-8")
        self.assertNotIn("quality() != PortQuality.STALE", service)

    def test_h_cell_normal_state_cannot_false_pass(self) -> None:
        self.assertTrue(JAVA_EVALUATOR.exists(), "H-cell health rules belong in mega station evaluator")
        source = JAVA_EVALUATOR.read_text(encoding="utf-8")
        for reason in (
            "UNINTENDED_BRAKE",
            "POSITION_FEEDBACK_NO_SIGNAL",
            "INTERLOCK_FAILED_MASK",
            "UNEXPECTED_ALARM_LATCH",
        ):
            self.assertIn(reason, source)
        self.assertIn("ServoActuatorBlock.braking", source)
        self.assertIn("ServoPositionSensorBlock.sourceQuality", source)
        self.assertIn("SafetyInterlockBlock.failedMask", source)
        self.assertIn("AlarmProcessorBlock.latched", source)

    def test_existing_v1_and_nineteen_selftests_remain_present(self) -> None:
        self.assertEqual(len(factory.SELFTESTS), 19)
        module = (ROOT / "src/main/java/dev/redstoneengineering/validation/RseValidationFactoryModule.java").read_text(encoding="utf-8")
        self.assertIn('Commands.literal("plant")', module)
        self.assertIn('Commands.literal("selftest")', module)


if __name__ == "__main__":
    unittest.main()
