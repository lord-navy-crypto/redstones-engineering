#!/usr/bin/env python3
"""Executable fixtures for RSE's read-only local dependency preflight."""
from pathlib import Path
from tempfile import TemporaryDirectory
import json
import unittest
import zipfile

import rse_install_preflight as checker


def jar(folder: Path, name: str, mod_id: str, version: str, loader: str = "neoforge") -> Path:
    path = folder / name
    with zipfile.ZipFile(path, "w") as file:
        if loader == "neoforge":
            file.writestr("META-INF/neoforge.mods.toml",
                          f'modLoader="javafml"\n[[mods]]\nmodId="{mod_id}"\n'
                          f'version="{version}"\n[[dependencies.{mod_id}]]\nmodId="minecraft"\n')
        elif loader == "forge":
            file.writestr("META-INF/mods.toml",
                          f'[[mods]]\nmodId="{mod_id}"\nversion="{version}"\n')
        else:
            file.writestr("fabric.mod.json", '{"id":"' + mod_id + '"}')
    return path


def valid_fixture(folder: Path) -> Path:
    rse = jar(folder, "redstoneengineering-1.0.20-alpha.jar", "redstoneengineering", "1.0.20-alpha")
    jar(folder, "jade-neoforge.jar", "jade", "15.10.6")
    jar(folder, "geckolib-neoforge.jar", "geckolib", "4.9.2")
    jar(folder, "ldlib2-neoforge.jar", "ldlib2", "2.2.26")
    return rse


class TestInstallPreflight(unittest.TestCase):
    def test_compatible(self):
        with TemporaryDirectory() as tmp:
            base = Path(tmp)
            rse = valid_fixture(base)
            manifest = base / "BUILD_INFO.json"
            manifest.write_text(json.dumps({"jar_sha256": checker.digest(rse)}))
            result = checker.check(base, manifest)
            self.assertTrue(result["ready"], result)
            self.assertTrue(result["mods"]["redstoneengineering"]["sha256_matches_test_build"])

    def test_duplicate_dependency(self):
        with TemporaryDirectory() as tmp:
            base = Path(tmp)
            valid_fixture(base)
            jar(base, "jade-duplicate.jar", "jade", "15.10.6")
            r = checker.check(base)
            self.assertFalse(r["ready"])
            self.assertTrue(any("重复安装" in e for e in r["errors"]))

    def test_fabric_not_neoforge(self):
        with TemporaryDirectory() as tmp:
            base = Path(tmp)
            valid_fixture(base)
            (base / "ldlib2-neoforge.jar").unlink()
            jar(base, "ldlib2-fabric.jar", "ldlib2", "2.2.26", "fabric")
            r = checker.check(base)
            self.assertFalse(r["ready"])
            self.assertTrue(any("Fabric" in e for e in r["warnings"]))

    def test_sources_not_usable(self):
        with TemporaryDirectory() as tmp:
            base = Path(tmp)
            valid_fixture(base)
            (base / "geckolib-neoforge.jar").unlink()
            jar(base, "geckolib-sources.jar", "geckolib", "4.9.2")
            r = checker.check(base)
            self.assertFalse(r["ready"])
            self.assertTrue(any("源码" in e for e in r["warnings"]))

    def test_older_version_fails(self):
        with TemporaryDirectory() as tmp:
            base = Path(tmp)
            valid_fixture(base)
            (base / "ldlib2-neoforge.jar").unlink()
            jar(base, "ldlib2-old.jar", "ldlib2", "2.2.25")
            self.assertFalse(checker.check(base)["ready"])

    def test_bad_sha_fails(self):
        with TemporaryDirectory() as tmp:
            base = Path(tmp)
            valid_fixture(base)
            manifest = base / "BUILD_INFO.json"
            manifest.write_text(json.dumps({"jar_sha256": "0" * 64}))
            self.assertTrue(any("SHA-256" in e for e in checker.check(base, manifest)["errors"]))

    def test_corrupted_unrelated_jar_warns_not_fake_mod(self):
        with TemporaryDirectory() as tmp:
            base = Path(tmp)
            valid_fixture(base)
            (base / "other-broken.jar").write_bytes(b"garbage")
            r = checker.check(base)
            self.assertTrue(r["ready"])
            self.assertTrue(any("无法读取" in e for e in r["warnings"]))

    def test_official_ldlib2_all_filename_is_valid_when_metadata_matches(self):
        # The official LDLib2 Modrinth primary download uses the -all suffix.
        # File provenance and NeoForge metadata matter; suffix alone must not reject.
        with TemporaryDirectory() as tmp:
            base = Path(tmp)
            valid_fixture(base)
            (base / "ldlib2-neoforge.jar").unlink()
            jar(base, "ldlib2-neoforge-1.21.1-2.2.26-all.jar", "ldlib2", "2.2.26")
            self.assertTrue(checker.check(base)["ready"])

    def test_manifest_invalid_json_fails_closed(self):
        with TemporaryDirectory() as tmp:
            base = Path(tmp)
            valid_fixture(base)
            manifest = base / "BUILD_INFO.json"
            manifest.write_text("{invalid")
            r = checker.check(base, manifest)
            self.assertFalse(r["ready"])
            self.assertEqual(r["integrity"], "failed")

    def test_manifest_missing_digest_fails_closed(self):
        with TemporaryDirectory() as tmp:
            base = Path(tmp)
            valid_fixture(base)
            manifest = base / "BUILD_INFO.json"
            manifest.write_text('{"jar_name":"redstoneengineering-1.0.20-alpha.jar"}')
            r = checker.check(base, manifest)
            self.assertFalse(r["ready"])
            self.assertEqual(r["integrity"], "failed")

    def test_manifest_rejects_mismatched_jar_filename(self):
        with TemporaryDirectory() as tmp:
            base = Path(tmp)
            rse = valid_fixture(base)
            manifest = base / "BUILD_INFO.json"
            manifest.write_text(json.dumps({
                "jar_sha256": checker.digest(rse), "jar_name": "other.jar"
            }))
            r = checker.check(base, manifest)
            self.assertFalse(r["ready"])
            self.assertEqual(r["integrity"], "failed")

    def test_version_parse(self):
        self.assertEqual(checker.parse_version("1.0.20-alpha"), (1, 0, 20))
        self.assertEqual(checker.parse_version("15.10.6+neoforge"), (15, 10, 6))
        self.assertIsNone(checker.parse_version("unknown"))


if __name__ == "__main__":
    unittest.main()
