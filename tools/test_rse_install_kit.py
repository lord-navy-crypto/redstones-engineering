#!/usr/bin/env python3
"""Offline regression tests for install-kit provenance and bundled checksum."""
import hashlib
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
import zipfile

import rse_install_kit as kit

HEAD = "1" * 40
CHECKOUT = "2" * 40

class TestInstallKit(unittest.TestCase):
    def prepare(self, base: Path) -> tuple[Path, Path, Path]:
        jar = base / "redstoneengineering-1.0.20-alpha.jar"
        with zipfile.ZipFile(jar, "w") as z:
            z.writestr("META-INF/neoforge.mods.toml",
                       'modLoader="javafml"\n[[mods]]\nmodId="redstoneengineering"\nversion="1.0.20-alpha"\n')
        preflight = base / "rse_install_preflight.py"
        preflight.write_text("# preflight fixture\n")
        guide = base / "README_zh-CN.md"
        guide.write_text("# 安装说明\n")
        return jar, preflight, guide

    def test_kit_checksums_and_distinct_commit_provenance(self):
        with TemporaryDirectory() as tmp:
            base = Path(tmp)
            jar, preflight, guide = self.prepare(base)
            zip_path = kit.build_kit(jar, base/"dist", HEAD, CHECKOUT, preflight, guide)
            with zipfile.ZipFile(zip_path) as z:
                names = set(z.namelist())
                self.assertEqual(len(names), 7)
                data = json.loads(z.read("BUILD_INFO.json"))
                self.assertEqual(data["source_head_sha"], HEAD)
                self.assertEqual(data["build_checkout_sha"], CHECKOUT)
                self.assertEqual(hashlib.sha256(z.read(f"RSE_JAR/{jar.name}")).hexdigest(), data["jar_sha256"])
                self.assertIn(data["jar_sha256"], z.read("SHA256SUMS.txt").decode())
                self.assertEqual(z.testzip(), None)

    def test_reject_wrong_version_or_loader(self):
        with TemporaryDirectory() as tmp:
            base = Path(tmp)
            jar, preflight, guide = self.prepare(base)
            jar.rename(base / "mismatched.jar")
            with self.assertRaises(ValueError):
                kit.build_kit(base/"mismatched.jar", base/"dist", HEAD, CHECKOUT, preflight, guide)
            with self.assertRaises(ValueError):
                kit.build_kit(base/"mismatched.jar", base/"dist", "short", CHECKOUT, preflight, guide)

    def test_deterministic_output(self):
        with TemporaryDirectory() as tmp:
            base = Path(tmp)
            jar, preflight, guide = self.prepare(base)
            first = kit.build_kit(jar, base/"one", HEAD, CHECKOUT, preflight, guide)
            second = kit.build_kit(jar, base/"two", HEAD, CHECKOUT, preflight, guide)
            self.assertEqual(first.read_bytes(), second.read_bytes())

if __name__ == "__main__":
    unittest.main()
