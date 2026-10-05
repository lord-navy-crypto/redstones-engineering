from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest

from tools import rse_validation_factory as factory
from tools import rse_validation_plant as plant

ROOT = Path(__file__).resolve().parents[1]

EXPECTED_MODULES = (
    "control_room",
    "service_spine",
    "cell_a_acquisition",
    "cell_b_conditioning",
    "cell_c_instrumentation",
    "cell_d_control",
    "cell_e_safety",
    "cell_f_process",
)
CELL_MODULES = tuple(name for name in EXPECTED_MODULES if name.startswith("cell_"))

EXPECTED_CELL_SIGNS = {
    "cell_a_acquisition": ("CELL A", "ACQUISITION", "SOURCE + PROBE", "EXPECT 6 VALID"),
    "cell_b_conditioning": ("CELL B", "CONDITIONING", "GAIN x2 + FILTER", "SATURATION TEST"),
    "cell_c_instrumentation": ("CELL C", "INSTRUMENT", "INLINE ANALYZER", "READBACK CHECK"),
    "cell_d_control": ("CELL D", "CONTROL", "PWM BRANCH", "ACTIVITY CHECK"),
    "cell_e_safety": ("CELL E", "SAFETY + FAULT", "INTERLOCK + ALARM", "TRIP RECOVER"),
    "cell_f_process": ("CELL F", "PROCESS", "SERVO + SENSOR", "FEEDBACK CHECK"),
}


class ValidationPlantTests(unittest.TestCase):
    def _block_id(self, block: factory.BlockSpec) -> str:
        return factory._normalize_block_spec(block)[0]

    def _block_properties(self, block: factory.BlockSpec) -> dict[str, str]:
        return dict(factory._normalize_block_spec(block)[1])

    def _sign_lines(self, block: factory.BlockSpec) -> tuple[str, ...]:
        nbt = factory._block_nbt(block)
        self.assertIsNotNone(nbt, "sign block is missing block-entity NBT")
        front = nbt["front_text"]
        return tuple(json.loads(message).get("text", "") for message in front["messages"])

    def _all_sign_lines(self, placements: list[factory.Placement]) -> list[tuple[str, ...]]:
        result: list[tuple[str, ...]] = []
        for _pos, block in placements:
            if self._block_id(block).endswith("_sign"):
                result.append(self._sign_lines(block))
        return result

    def _assert_panel_colors(self, by_position: dict[tuple[int, int, int], factory.BlockSpec], panel: plant.StatusPanel) -> None:
        expected = (
            (panel.wait_lamp, "minecraft:yellow_concrete"),
            (panel.pass_lamp, "minecraft:lime_concrete"),
            (panel.fail_lamp, "minecraft:red_concrete"),
        )
        for lamp_pos, expected_base in expected:
            x, y, z = lamp_pos
            self.assertEqual(y, 2, "status lamps are expected at y=2")
            base = by_position.get((x, y - 1, z))
            self.assertIsNotNone(base, f"missing color base below status lamp at {lamp_pos}")
            self.assertEqual(self._block_id(base), expected_base)

    def test_modular_plant_defines_eight_structures(self) -> None:
        self.assertEqual(tuple(plant.PLANT_STRUCTURE_ORDER), EXPECTED_MODULES)
        self.assertEqual(set(plant.PLANT_STRUCTURES), set(EXPECTED_MODULES))
        for module_id in EXPECTED_MODULES:
            with self.subTest(module_id=module_id):
                size, placements = plant.PLANT_STRUCTURES[module_id]()
                self.assertGreater(size[0], 0)
                self.assertGreater(size[2], 0)
                self.assertTrue(placements)

    def test_module_offsets_do_not_overlap(self) -> None:
        boxes: list[tuple[str, int, int, int, int]] = []
        for module_id in EXPECTED_MODULES:
            ox, _oy, oz = plant.PLANT_MODULE_OFFSETS[module_id]
            size, _placements = plant.PLANT_STRUCTURES[module_id]()
            boxes.append((module_id, ox, oz, ox + size[0] - 1, oz + size[2] - 1))
        for i, left in enumerate(boxes):
            for right in boxes[i + 1:]:
                separated = left[3] < right[1] or right[3] < left[1] or left[4] < right[2] or right[4] < left[2]
                self.assertTrue(separated, f"plant modules overlap: {left[0]} / {right[0]}")

    def test_service_spine_closes_control_room_gap(self) -> None:
        self.assertEqual(plant.PLANT_MODULE_OFFSETS["service_spine"], (0, 0, 13))
        size, _placements = plant.PLANT_STRUCTURES["service_spine"]()
        self.assertEqual(size, (57, 5, 3))
        control_offset = plant.PLANT_MODULE_OFFSETS["control_room"]
        control_size, _ = plant.PLANT_STRUCTURES["control_room"]()
        self.assertEqual(control_offset[2] + control_size[2], 13)
        self.assertEqual(plant.PLANT_MODULE_OFFSETS["cell_a_acquisition"][2], 16)

    def test_all_modules_clear_their_full_template_volume(self) -> None:
        for module_id in EXPECTED_MODULES:
            with self.subTest(module_id=module_id):
                size, placements = plant.PLANT_STRUCTURES[module_id]()
                self.assertEqual(len(placements), size[0] * size[1] * size[2])

    def test_each_cell_has_real_high_frame_and_corner_columns(self) -> None:
        for module_id in CELL_MODULES:
            with self.subTest(module_id=module_id):
                size, placements = plant.PLANT_STRUCTURES[module_id]()
                by_position = dict(placements)
                width, height, depth = size
                self.assertEqual(height, 5)
                for x, z in ((0, 0), (width - 1, 0), (0, depth - 1), (width - 1, depth - 1)):
                    for y in range(1, height):
                        self.assertNotEqual(self._block_id(by_position[(x, y, z)]), "minecraft:air")
                roof_frame = [
                    block for (_x, y, _z), block in placements
                    if y == height - 1 and self._block_id(block) != "minecraft:air"
                ]
                self.assertGreaterEqual(len(roof_frame), 32)

    def test_status_lamps_have_unambiguous_color_bases(self) -> None:
        for module_id in CELL_MODULES:
            with self.subTest(module_id=module_id):
                _size, placements = plant.PLANT_STRUCTURES[module_id]()
                self._assert_panel_colors(dict(placements), plant.LOCAL_STATUS_PANEL)

        _size, placements = plant.PLANT_STRUCTURES["control_room"]()
        control = dict(placements)
        self._assert_panel_colors(control, plant.MASTER_STATUS_PANEL)
        for panel in plant.CELL_STATUS_PANELS.values():
            self._assert_panel_colors(control, panel)

    def test_every_cell_has_a_readable_test_identity_sign(self) -> None:
        for module_id, expected in EXPECTED_CELL_SIGNS.items():
            with self.subTest(module_id=module_id):
                _size, placements = plant.PLANT_STRUCTURES[module_id]()
                self.assertIn(expected, self._all_sign_lines(placements))

    def test_control_room_has_legend_and_diagnostic_reporting_signs(self) -> None:
        _size, placements = plant.PLANT_STRUCTURES["control_room"]()
        signs = self._all_sign_lines(placements)
        self.assertIn(("MASTER STATUS", "WAIT = YELLOW", "PASS = GREEN", "FAIL = RED"), signs)
        self.assertIn(("DIAGNOSTICS", "RUN plant status", "REPORT PHASE", "CELL + DETAIL"), signs)

    def test_structure_writer_serializes_sign_block_entity_nbt(self) -> None:
        sign = plant._sign(("CELL A", "ACQUISITION", "SOURCE + PROBE", "EXPECT 6 VALID"), rotation=8)
        payload = factory._structure_bytes((1, 2, 1), [((0, 1, 0), sign)])
        self.assertIn(b"front_text", payload)
        self.assertIn(b"messages", payload)
        self.assertIn(b'\"text\":\"CELL A\"', payload)
        self.assertIn(b"is_waxed", payload)

    def test_control_room_has_physical_master_retest_and_cell_panels(self) -> None:
        _size, placements = plant.PLANT_STRUCTURES["control_room"]()
        by_position = dict(placements)
        button = by_position.get(plant.MASTER_RETEST_BUTTON)
        self.assertIsNotNone(button)
        block_id, properties = factory._normalize_block_spec(button)
        self.assertEqual(block_id, "minecraft:stone_button")
        self.assertEqual(dict(properties).get("powered"), "false")
        self.assertEqual(set(plant.CELL_STATUS_PANELS), {"A", "B", "C", "D", "E", "F"})
        for panel in plant.CELL_STATUS_PANELS.values():
            self.assertIn(panel.wait_power, by_position)
            self.assertIn(panel.pass_power, by_position)
            self.assertIn(panel.fail_power, by_position)

    def test_cell_d_pwm_inhibit_face_is_physically_isolated(self) -> None:
        _size, placements = plant.PLANT_STRUCTURES["cell_d_control"]()
        by_position = dict(placements)
        pwm = [(pos, block) for pos, block in placements if self._block_id(block) == "redstoneengineering:pwm_controller"]
        self.assertEqual(len(pwm), 1)
        (x, y, z), pwm_block = pwm[0]
        facing = self._block_properties(pwm_block)["facing"]
        inhibit_direction = {"north": "west", "west": "south", "south": "east", "east": "north"}[facing]
        dx, dz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[inhibit_direction]
        inhibit_pos = (x + dx, y, z + dz)
        self.assertEqual(
            self._block_id(by_position[inhibit_pos]),
            "minecraft:air",
            f"PWM inhibit face {inhibit_direction} is accidentally driven at {inhibit_pos}",
        )

    def test_cell_f_has_explicit_zero_brake_test_source_on_servo_brake_port(self) -> None:
        _size, placements = plant.PLANT_STRUCTURES["cell_f_process"]()
        by_position = dict(placements)
        servos = [(pos, block) for pos, block in placements if self._block_id(block) == "redstoneengineering:servo_actuator"]
        self.assertEqual(len(servos), 1)
        (x, y, z), servo = servos[0]
        facing = self._block_properties(servo)["facing"]
        brake_direction = {"north": "east", "east": "south", "south": "west", "west": "north"}[facing]
        dx, dz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[brake_direction]
        brake_pos = (x + dx, y, z + dz)
        brake_source = by_position[brake_pos]
        self.assertEqual(self._block_id(brake_source), "redstoneengineering:redstone_reference_source")
        properties = self._block_properties(brake_source)
        self.assertEqual(properties.get("power"), "0")
        self.assertEqual(properties.get("facing"), "south")

    def test_process_runtime_waits_for_command_propagation_and_checks_expected_braking(self) -> None:
        service = (ROOT / "src/main/java/dev/redstoneengineering/validation/RseValidationPlantService.java").read_text(encoding="utf-8")
        self.assertIn('waitFor("servo command propagating=', service)
        self.assertIn("PROCESS_PROPAGATION_TIMEOUT_TICKS", service)
        self.assertIn("boolean expectedBrake = tripExpected;", service)
        self.assertIn("braking != expectedBrake", service)
        self.assertIn("f.offset(14, 1, 5)", service)
        self.assertIn("COMMAND STUCK", service)
        self.assertIn("BRAKE STUCK", service)
        self.assertIn("POSITION STUCK", service)
        self.assertIn("FEEDBACK STUCK", service)

    def test_factory_generator_emits_modular_plant_assets(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            structure_dir = Path(directory) / "plant"
            index_path = Path(directory) / "plant-index.txt"
            self.assertEqual(factory.generate_plant(structure_dir, index_path), 0)
            emitted = sorted(path.stem for path in structure_dir.glob("*.nbt"))
            self.assertEqual(emitted, sorted(EXPECTED_MODULES))
            index = index_path.read_text(encoding="utf-8")
            for module_id in EXPECTED_MODULES:
                self.assertIn(module_id, index)

    def test_java_command_surface_and_server_tick_own_plant_runtime(self) -> None:
        module = (ROOT / "src/main/java/dev/redstoneengineering/validation/RseValidationFactoryModule.java").read_text(encoding="utf-8")
        service = (ROOT / "src/main/java/dev/redstoneengineering/validation/RseValidationPlantService.java").read_text(encoding="utf-8")
        saved = (ROOT / "src/main/java/dev/redstoneengineering/validation/RseValidationPlantSavedData.java").read_text(encoding="utf-8")
        self.assertIn('Commands.literal("plant")', module)
        self.assertIn('Commands.literal("place")', module)
        self.assertIn('Commands.literal("status")', module)
        self.assertIn('Commands.literal("retest")', module)
        self.assertIn("RseValidationPlantService.tick(", module)
        self.assertIn("RseValidationPlantService.place(", module)
        self.assertIn("RseValidationPlantService.status(", module)
        self.assertIn("RseValidationPlantService.retest(", module)
        self.assertIn('new ModuleSpec("service_spine", "", new BlockPos(0, 0, 13), new BlockPos(57, 5, 3))', service)
        self.assertIn("extends SavedData", saved)
        self.assertIn("retestPressed", saved)
        self.assertIn("phase", saved)
        self.assertIn("tick(MinecraftServer", service)

    def test_plant_service_defines_full_acceptance_sequence_and_real_evidence(self) -> None:
        service = (ROOT / "src/main/java/dev/redstoneengineering/validation/RseValidationPlantService.java").read_text(encoding="utf-8")
        for phase in (
            "PRECHECK", "BIST", "STARTUP", "NOMINAL", "DISTURBANCE", "SATURATION",
            "SENSOR_FAULT", "INTERLOCK_TRIP", "RECOVERY", "FINAL_RUN", "ACCEPTANCE",
        ):
            self.assertIn(phase, service)
        for evidence in (
            "engineeringSnapshot", "SignalAnalyzerBlock.uiSnapshot", "SafetyInterlockBlock.failedMask",
            "AlarmProcessorBlock.latched", "BlockStateProperties.LIT",
        ):
            self.assertIn(evidence, service)
        self.assertNotIn("setValue(DirectionalSignalBlock.OUTPUT", service)

    def test_cells_cover_acquisition_conditioning_instrumentation_control_safety_process(self) -> None:
        builders = {
            module_id: dict(plant.PLANT_STRUCTURES[module_id]()[1])
            for module_id in CELL_MODULES
        }
        block_ids = {
            module_id: {factory._normalize_block_spec(block)[0] for block in placements.values()}
            for module_id, placements in builders.items()
        }
        self.assertTrue({"redstoneengineering:redstone_reference_source", "redstoneengineering:signal_probe"} <= block_ids["cell_a_acquisition"])
        self.assertTrue({"redstoneengineering:signal_conditioner", "redstoneengineering:precision_filter"} <= block_ids["cell_b_conditioning"])
        self.assertTrue({"redstoneengineering:signal_analyzer", "redstoneengineering:analog_indicator"} <= block_ids["cell_c_instrumentation"])
        self.assertTrue({"redstoneengineering:pwm_controller", "redstoneengineering:signal_analyzer"} <= block_ids["cell_d_control"])
        self.assertTrue({"redstoneengineering:safety_interlock", "redstoneengineering:alarm_processor", "redstoneengineering:fault_injector"} <= block_ids["cell_e_safety"])
        self.assertTrue({"redstoneengineering:servo_actuator", "redstoneengineering:servo_position_sensor"} <= block_ids["cell_f_process"])

    def test_existing_nineteen_selftests_are_not_repurposed(self) -> None:
        self.assertEqual(len(factory.SELFTESTS), 19)
        self.assertNotIn("04_subsystems", "\n".join(factory.SELFTESTS))


if __name__ == "__main__":
    unittest.main()
