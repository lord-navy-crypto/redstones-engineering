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
                    any(block_id.startswith("redstoneengineering:") for _position, block_id in placements),
                    f"preset {name} contains no RSE block",
                )

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
