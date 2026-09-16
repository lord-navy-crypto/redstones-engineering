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

    def _sign_lines(self, block: factory.BlockSpec) -> tuple[str, ...]:
        nbt = factory._block_nbt(block)
        self.assertIsNotNone(nbt)
        return tuple(json.loads(message).get("text", "") for message in nbt["front_text"]["messages"])

    def _signs(self, placements: list[factory.Placement]) -> list[tuple[str, ...]]:
        return [self._sign_lines(block) for _pos, block in placements if self._block_id(block).endswith("_sign")]

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
            # One original corner lamp is deliberately consumed by the live plant-backbone tap.
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
        registry = (ROOT / "src/main/java/dev/redstoneengineering/RedstoneEngineering.java").read_text(encoding="utf-8")
        for dut in EXPECTED_DUTS:
            # Registrations use both compact one-line and formatted multi-line registerBlock calls.
            self.assertRegex(registry, rf'BLOCKS\.registerBlock\(\s*"{re.escape(dut)}"', dut)

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
        self.assertIn("STATION_COUNT = 40", service)
        self.assertIn("CELL_COUNT = 8", service)
        for number, dut in enumerate(EXPECTED_DUTS, 1):
            self.assertIn(f'new StationSpec({number},', service)
            self.assertIn(f'"redstoneengineering:{dut}"', service)
        for cell in EXPECTED_CELLS:
            self.assertIn(f'"{cell}"', service)

    def test_command_surface_and_server_tick_wire_mega_runtime(self) -> None:
        module = (ROOT / "src/main/java/dev/redstoneengineering/validation/RseValidationFactoryModule.java").read_text(encoding="utf-8")
        self.assertIn('Commands.literal("mega")', module)
        for literal in ("place", "status", "station", "cell", "report", "retest"):
            self.assertIn(f'Commands.literal("{literal}")', module)
        self.assertIn("RseMegaValidationService.tick(", module)
        self.assertIn("RseMegaValidationService.place(", module)
        self.assertIn("RseMegaValidationService.station(", module)
        self.assertIn("RseMegaValidationService.cell(", module)
        self.assertIn("RseMegaValidationService.report(", module)

    def test_existing_v1_and_nineteen_selftests_remain_present(self) -> None:
        self.assertEqual(len(factory.SELFTESTS), 19)
        module = (ROOT / "src/main/java/dev/redstoneengineering/validation/RseValidationFactoryModule.java").read_text(encoding="utf-8")
        self.assertIn('Commands.literal("plant")', module)
        self.assertIn('Commands.literal("selftest")', module)


if __name__ == "__main__":
    unittest.main()
