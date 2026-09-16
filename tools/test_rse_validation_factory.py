from __future__ import annotations

import tempfile
from pathlib import Path
import unittest
import zipfile

from tools import rse_validation_factory as factory


class ValidationFactoryToolTests(unittest.TestCase):
    def test_structure_bytes_are_deterministic_and_compound_root(self) -> None:
        placements = [
            ((0, 0, 0), "minecraft:stone"),
            ((1, 0, 0), "redstoneengineering:industrial_buffer"),
            ((2, 0, 0), "minecraft:stone"),
        ]
        first = factory._structure_bytes((3, 2, 1), placements)
        second = factory._structure_bytes((3, 2, 1), placements)
        self.assertEqual(first, second)
        self.assertEqual(first[0], factory.TAG_COMPOUND)
        self.assertIn(b"minecraft:stone", first)
        self.assertIn(b"redstoneengineering:industrial_buffer", first)

    def test_structure_palette_serializes_block_state_properties(self) -> None:
        placements = [
            ((0, 0, 0), ("redstoneengineering:redstone_reference_source", {"facing": "east", "power": "7"})),
        ]
        payload = factory._structure_bytes((1, 1, 1), placements)
        self.assertIn(b"Properties", payload)
        self.assertIn(b"facing", payload)
        self.assertIn(b"east", payload)
        self.assertIn(b"power", payload)
        self.assertIn(b"7", payload)

    def test_all_declared_structures_have_unique_block_positions(self) -> None:
        for name, builder in factory.STRUCTURES.items():
            with self.subTest(name=name):
                _size, placements = builder()
                positions = [position for position, _block_id in placements]
                self.assertEqual(len(positions), len(set(positions)), f"duplicate block coordinate in {name}")

    def test_operations_monitor_station_contains_real_operations_monitor(self) -> None:
        _size, placements = factory.STRUCTURES["operations_monitor"]()
        block_ids = {block_id for _position, block_id in placements}
        self.assertIn("redstoneengineering:operations_monitor", block_ids)

    def test_basic_and_signal_preset_catalog_is_large_and_ordered(self) -> None:
        expected = {
            "01_basic/redstone_input_output",
            "01_basic/signal_probe",
            "01_basic/signal_analyzer",
            "01_basic/oscilloscope",
            "01_basic/signal_conditioner",
            "01_basic/calibration_module",
            "01_basic/directional_io",
            "01_basic/instrument_chain",
            "02_signal/precision_filter",
            "02_signal/sample_hold",
            "02_signal/edge_detector",
            "02_signal/pulse_shaper",
            "02_signal/pwm_control",
            "02_signal/noise_vs_filter",
            "02_signal/quantizer_scaler",
            "02_signal/multi_stage_signal_chain",
        }
        self.assertGreaterEqual(len(factory.PRESETS), 16)
        self.assertTrue(expected.issubset(factory.PRESETS.keys()))

    def test_all_presets_have_unique_positions_and_real_rse_content(self) -> None:
        for name, preset in factory.PRESETS.items():
            with self.subTest(name=name):
                size, placements = preset.builder()
                self.assertTrue(all(component > 0 for component in size))
                positions = [position for position, _block_id in placements]
                self.assertEqual(len(positions), len(set(positions)), f"duplicate block coordinate in {name}")
                self.assertTrue(
                    any(factory._block_name(block_id).startswith("redstoneengineering:") for _position, block_id in placements),
                    f"preset {name} contains no RSE block",
                )

    def test_selftest_catalog_covers_basic_and_signal_series(self) -> None:
        expected = {
            "01_basic/reference_source",
            "01_basic/signal_probe",
            "01_basic/signal_analyzer_tap",
            "01_basic/signal_analyzer_inline",
            "01_basic/analog_indicator",
            "01_basic/signal_conditioner_gain",
            "01_basic/directional_io",
            "01_basic/instrument_bus",
            "02_signal/conditioner_saturation",
            "02_signal/precision_filter",
            "02_signal/sample_hold",
            "02_signal/edge_detector",
            "02_signal/pulse_shaper",
            "02_signal/pwm_control",
            "02_signal/noise_vs_filter",
            "02_signal/quantizer_scaler",
        }
        self.assertGreaterEqual(len(factory.SELFTESTS), len(expected))
        self.assertTrue(expected.issubset(factory.SELFTESTS))

    def test_each_selftest_has_unique_positions_and_wait_pass_fail_panel(self) -> None:
        for name, selftest in factory.SELFTESTS.items():
            with self.subTest(name=name):
                size, placements = selftest.builder()
                self.assertTrue(all(component > 0 for component in size))
                positions = [position for position, _block in placements]
                self.assertEqual(len(positions), len(set(positions)), f"duplicate block coordinate in {name}")
                by_position = {position: factory._block_name(block) for position, block in placements}
                panel = selftest.panel
                self.assertEqual(by_position.get(panel.wait_lamp), "minecraft:redstone_lamp")
                self.assertEqual(by_position.get(panel.pass_lamp), "minecraft:redstone_lamp")
                self.assertEqual(by_position.get(panel.fail_lamp), "minecraft:redstone_lamp")
                self.assertEqual(by_position.get(panel.wait_label), "minecraft:yellow_concrete")
                self.assertEqual(by_position.get(panel.pass_label), "minecraft:lime_concrete")
                self.assertEqual(by_position.get(panel.fail_label), "minecraft:red_concrete")
                self.assertEqual(by_position.get(panel.wait_power), "minecraft:redstone_block")
                self.assertNotIn(panel.pass_power, by_position)
                self.assertNotIn(panel.fail_power, by_position)
                self.assertTrue(
                    any(factory._block_name(block).startswith("redstoneengineering:") for _position, block in placements),
                    f"selftest {name} contains no RSE DUT",
                )

    def test_selftest_dynamic_benches_use_real_domains_and_observers(self) -> None:
        def block_at(test_id: str, position: tuple[int, int, int]):
            _size, placements = factory.SELFTESTS[test_id].builder()
            return dict(placements)[position]

        precision = block_at("02_signal/precision_filter", (4, 1, 4))
        precision_id, precision_props = factory._normalize_block_spec(precision)
        self.assertEqual(precision_id, "redstoneengineering:precision_filter")
        self.assertEqual(dict(precision_props)["input_facing"], "west")
        self.assertEqual(factory._block_name(block_at("02_signal/precision_filter", (3, 1, 4))),
                         "redstoneengineering:redstone_reference_source")

        hold = block_at("02_signal/sample_hold", (4, 1, 4))
        hold_id, hold_props = factory._normalize_block_spec(hold)
        self.assertEqual(hold_id, "redstoneengineering:sample_hold")
        self.assertEqual(dict(hold_props)["trigger_mode"], "0")
        self.assertEqual(factory._block_name(block_at("02_signal/sample_hold", (3, 1, 4))),
                         "redstoneengineering:redstone_reference_source")

        self.assertEqual(factory._block_name(block_at("02_signal/pulse_shaper", (5, 1, 4))),
                         "redstoneengineering:signal_analyzer")
        self.assertEqual(factory._block_name(block_at("02_signal/pwm_control", (5, 1, 4))),
                         "redstoneengineering:signal_analyzer")

        scaler_id, scaler_props = factory._normalize_block_spec(block_at("02_signal/quantizer_scaler", (3, 1, 4)))
        quantizer_id, quantizer_props = factory._normalize_block_spec(block_at("02_signal/quantizer_scaler", (5, 1, 4)))
        self.assertEqual(scaler_id, "redstoneengineering:redstone_to_lapis_scaler")
        self.assertEqual(quantizer_id, "redstoneengineering:lapis_to_redstone_quantizer")
        self.assertEqual(dict(scaler_props)["input_facing"], "west")
        self.assertEqual(dict(quantizer_props)["facing"], "east")

    def test_package_presets_contains_only_generated_preset_assets_and_index(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            structure_dir = root / "structures"
            index_path = root / "preset-index.txt"
            output = root / "RSE-Preset-Pack.zip"
            self.assertEqual(factory.generate_presets(structure_dir=structure_dir, index_path=index_path), 0)
            self.assertEqual(factory.package_presets(structure_dir=structure_dir, index_path=index_path, output=output), 0)
            with zipfile.ZipFile(output) as archive:
                names = set(archive.namelist())
            self.assertIn("preset-index.txt", names)
            self.assertIn("01_basic/signal_probe.nbt", names)
            self.assertIn("02_signal/multi_stage_signal_chain.nbt", names)
            self.assertEqual(len([name for name in names if name.endswith(".nbt")]), len(factory.PRESETS))

    def test_legacy_cli_aliases_map_to_current_commands(self) -> None:
        self.assertEqual(factory._legacy_cli(["--generate-structures"]), ["generate"])
        self.assertEqual(
            factory._legacy_cli(["--package-world", "/tmp/RSE Validation Factory"]),
            ["package", "--world", "/tmp/RSE Validation Factory"],
        )
        self.assertEqual(factory._legacy_cli(["generate"]), ["generate"])

    def test_package_world_uses_stable_root_and_excludes_transient_files(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            world = root / "SourceWorld"
            world.mkdir()
            (world / "level.dat").write_bytes(b"real-world-sentinel")
            (world / "session.lock").write_bytes(b"transient")
            (world / ".DS_Store").write_bytes(b"transient")
            (world / "region").mkdir()
            (world / "region" / "r.0.0.mca").write_bytes(b"region")
            (world / "playerdata").mkdir()
            (world / "playerdata" / "player.dat").write_bytes(b"player")
            (world / "logs").mkdir()
            (world / "logs" / "latest.log").write_text("noise", encoding="utf-8")
            (world / "crash-reports").mkdir()
            (world / "crash-reports" / "crash.txt").write_text("noise", encoding="utf-8")
            output = root / "factory.zip"

            self.assertEqual(factory.package_world(world, output), 0)
            with zipfile.ZipFile(output) as archive:
                names = archive.namelist()

            self.assertIn("RSE Validation Factory/level.dat", names)
            self.assertIn("RSE Validation Factory/region/r.0.0.mca", names)
            self.assertIn("RSE Validation Factory/playerdata/player.dat", names)
            self.assertNotIn("RSE Validation Factory/session.lock", names)
            self.assertNotIn("RSE Validation Factory/.DS_Store", names)
            self.assertFalse(any(name.startswith("RSE Validation Factory/logs/") for name in names))
            self.assertFalse(any(name.startswith("RSE Validation Factory/crash-reports/") for name in names))

    def test_package_world_refuses_non_world_directory(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            world = root / "NotAWorld"
            world.mkdir()
            output = root / "factory.zip"
            self.assertEqual(factory.package_world(world, output), 2)
            self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
