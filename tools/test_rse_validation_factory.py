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
