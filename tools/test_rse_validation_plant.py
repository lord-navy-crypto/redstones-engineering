from __future__ import annotations

from pathlib import Path
import tempfile
import unittest

from tools import rse_validation_factory as factory
from tools import rse_validation_plant as plant

ROOT = Path(__file__).resolve().parents[1]

EXPECTED_MODULES = (
    "control_room",
    "cell_a_acquisition",
    "cell_b_conditioning",
    "cell_c_instrumentation",
    "cell_d_control",
    "cell_e_safety",
    "cell_f_process",
)


class ValidationPlantTests(unittest.TestCase):
    def test_modular_plant_defines_seven_structures(self) -> None:
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
            for module_id in EXPECTED_MODULES
            if module_id != "control_room"
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
