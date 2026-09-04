#!/usr/bin/env python3
"""Alpha 1.0.20 release-readiness checks that are safe to run in CI.

This verifier intentionally focuses on release packaging/documentation invariants rather
than gameplay semantics, which remain covered by the historical verifiers and GameTests.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []

EXPECTED_VERSION = "1.0.20-alpha"
EXPECTED_MINECRAFT = "1.21.1"
EXPECTED_NEOFORGE = "21.1.249"
EXPECTED_JAVA = "21"


def read(relative: str) -> str:
    path = root / relative
    if not path.is_file():
        errors.append(f"missing release file: {relative}")
        return ""
    return path.read_text(errors="ignore")


gradle_properties = read("gradle.properties")
manifest = read("ALPHA1_0_20_MANIFEST.txt")
mods_template = read("src/main/templates/META-INF/neoforge.mods.toml")
build_gradle = read("build.gradle")
workflow = read(".github/workflows/build.yml")
lang_en_us = read("src/main/resources/assets/red<>stoneengineering/lang/en_us.json")

for key, expected in (
    ("mod_version", EXPECTED_VERSION),
    ("minecraft_version", EXPECTED_MINECRAFT),
    ("neo_version", EXPECTED_NEOFORGE),
):
    if not re.search(rf"(?m)^{re.escape(key)}={re.escape(expected)}$", gradle_properties):
        errors.append(f"gradle.properties does not pin {key}={expected}")

if f"Artifact: {EXPECTED_VERSION}" not in manifest:
    errors.append(f"manifest artifact is not {EXPECTED_VERSION}")
if f"Minecraft: {EXPECTED_MINECRAFT}" not in manifest:
    errors.append(f"manifest Minecraft baseline is not {EXPECTED_MINECRAFT}")
if f"NeoForge: {EXPECTED_VERSION}" in manifest:
    errors.append("manifest NeoForge field accidentally contains the RSE artifact version")
if f"NeoForge: {EXPECTED_NEOFORTH}" not in manifest:
    errors.append(f"manifest NeoForge baseline is not {EXPECTED_NEOFORGE}")
if f"Java: {EXPECTED_JAVA}" not in manifest:
    errors.append(f"manifest Java baseline is not {EXPECTED_JAVA}")

if 'version="${mod_version}"' not in mods_template:
    errors.append("NeoForge metadata must derive the mod version from ${mod_version}")

for dependency in ("jei", "jade", "geckolib", "cloth_config", "fusion"):
    if f'modId="{dependency}"' not in mods_template:
        errors.append(f"required dependency missing from NeoForge metadata: {dependency}")

match = re.search(r"(?m)^Public alpha testing guide:\s*(\S+)\s*$", manifest)
if not match:
    errors.append("manifest does not declare the public alpha testing guide")
    testing_guide = ""
else:
    guide_path = match.group(1)
    guide_file = root / guide_path
    if not guide_file.is_file():
        errors.append(f"manifest testing-guide path does not exist: {guide_path}")
        testing_guide = ""
    else:
        testing_guide = guide_file.read_text(errors="ignore")

for required in (
    "RSE Alpha 1.0.20 Testing Guide",
    EXPECTED_VERSION,
    EXPECTED_MINECRAFT,
    EXPECTED_NEOFORGE,
    "Back up any world you care about before testing",
    "latest.log",
    "Release-candidate gate",
):
    if testing_guide and required not in testing_guide:
        errors.append(f"testing guide missing release-critical text: {required}")

if re.search(r"(?m)^\s*url\s+['\"]", build_gradle):
    errors.append("build.gradle still uses deprecated Groovy space assignment for repository url")

jade_source_dir = root / "src/main/java/dev/redstoneengineering/integration/jade"
jade_uid_pattern = re.compile(
    r'private\s+static\s+final\s+ResourceLocation\s+UID\s*=\s*ResourceLocation\.parse\(\s*"redstoneengineering:([a-z0-9_./-]+)"\s*\)'
)
jade_provider_ids: set[str] = set()
if not jade_source_dir.is_dir():
    errors.append("missing RSE Jade integration source directory")
else:
    for source in jade_source_dir.glob("*.java"):
        for uid_match in jade_uid_pattern.finditer(source.read_text(errors="ignore")):
            jade_provider_ids.add(uid_match.group(1))

if not jade_provider_ids:
    errors.append("release verifier found no RSE Jade provider UID declarations")

try:
    lang_entries = json.loads(lang_en_us) if lang_en_us else {}
except json.JSONDecodeError as exc:
    errors.append(f"en_us.json is not valid JSON: {exc}")
    lang_entries = {}

if not isinstance(lang_entries, dict):
    errors.append("en_us.json must contain a JSON object")
    lang_entries = {}

for provider_id in sorted(jade_provider_ids):
    translation_key = f"config.jade.plugin_redstoneengineering.{provider_id}"
    value = lang_entries.get(translation_key)
    if not isinstance(value, str) or not value.strip():
        errors.append(f"missing Jade config translation: {translation_key}")

for required_workflow_text in (
    "runGameTestServer",
    "sha256sum *.jar > SHA256SUMS.txt",
    "if-no-files-found: error",
):
    if required_workflow_text not in workflow:
        errors.append(f"release workflow missing gate: {required_workflow_text}")

artifact_action = re.search(r"(?m)^\s*uses:\s*actions/upload-artifact@v(\d+)\s*$", workflow)
if not artifact_action:
    errors.append("release workflow is missing the actions/upload-artifact gate")
elif int(artifact_action.group(1)) < 6:
    errors.append(
        f"release workflow uses actions/upload-artifact@v{artifact_action.group(1)}; Alpha 1.0.20 requires Node 24-capable v6 or newer"
    )

if errors:
    print("RSE Alpha 1.0.20 release verification: FAIL")
    for error in errors:
        print(" -", error)
    raise System:last(1)

print("RSE Alpha 1.0.20 release verification: PASS")
print(f"  artifact version: {EXPECTED_VERSION}")
print(f"  Minecraft / NeoForge / Java: {EXPECTED_MINECRAFT} / {EXPECTED_NEOFORGE} / {EXPECTED_JAVA}")
print("  manifest -> testing guide link: PASS")
print("  required dependency metadata: PASS")
print("  Gradle publishing syntax: PASS")
print(f"  Jade provider config translations: {len(jade_provider_ids)} PASS")
print(f"  artifact upload action: v{artifact_action.group(1)}")
print("  GameTest + checksum + artifact gates: PASS")