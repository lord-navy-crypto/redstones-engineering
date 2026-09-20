#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []

EXPECTED_VERSION = "1.0.21-alpha"
EXPECTED_MINECRAFT = "1.21.1"
EXPECTED_NEOFORGE = "21.1.249"
EXPECTED_JAVA = "21"
EXPECTED_LICENSE = "MPL-2.0"

def read(rel: str) -> str:
    p = root / rel
    if not p.is_file():
        errors.append(f"missing release file: {rel}")
        return ""
    return p.read_text(errors="ignore")

gradle = read("gradle.properties")
manifest = read("ALPHA1_0_21_MANIFEST.txt")
guide = read("docs/ALPHA1_0_21_TESTING_GUIDE.md")
mods = read("src/main/templates/META-INF/neoforge.mods.toml")
workflow = read(".github/workflows/build.yml")
readme = read("README.md")
install_guide = read("INSTALL_WITH_ZSH.txt")

for key, expected in (
    ("mod_version", EXPECTED_VERSION),
    ("minecraft_version", EXPECTED_MINECRAFT),
    ("neo_version", EXPECTED_NEOFORGE),
    ("mod_license", EXPECTED_LICENSE),
):
    if not re.search(rf"(?m)^{re.escape(key)}={re.escape(expected)}$", gradle):
        errors.append(f"gradle.properties does not pin {key}={expected}")

for token in (
    "Redstone Systems Engineering — Alpha 1.0.21",
    f"Artifact: {EXPECTED_VERSION}",
    f"Minecraft: {EXPECTED_MINECRAFT}",
    f"NeoForge: {EXPECTED_NEOFORGE}",
    f"Java: {EXPECTED_JAVA}",
    f"License: {EXPECTED_LICENSE}",
    "Integrated Validation, Actuator Deepening & Signal Processing Laboratory",
    "docs/ALPHA1_0_21_TESTING_GUIDE.md",
):
    if token not in manifest:
        errors.append(f"manifest missing: {token}")

for token in (
    "RSE Alpha 1.0.21 Testing Guide",
    EXPECTED_VERSION,
    EXPECTED_MINECRAFT,
    EXPECTED_NEOFORGE,
    "Back up any world you care about before testing",
    "Release-candidate gate",
    "runGameTestServer",
    "latest.log",
):
    if token not in guide:
        errors.append(f"testing guide missing: {token}")

for dependency in ("jei", "jade", "geckolib", "cloth_config", "fusion"):
    if f'modId="{dependency}"' not in mods:
        errors.append(f"required dependency missing from NeoForge metadata: {dependency}")

for token in (
    "tools/test_rse_integrated_demo.py",
    "tools/test_rse_signal_processing_lab.py",
    "tools/rse_pid_actuator_dynamics_verify.py",
    "tools/rse_ninth_ten_control_pneumatic_verify.py",
    "tools/rse_alpha1021_release_verify.py",
    "compileJava",
    "clean build",
    "sha256sum *.jar > SHA256SUMS.txt",
):
    if token not in workflow:
        errors.append(f"workflow missing release gate: {token}")

if EXPECTED_VERSION not in readme or "Alpha 1.0.21" not in readme:
    errors.append("README does not identify Alpha 1.0.21 / 1.0.21-alpha")

for token in (
    "1.0.21-alpha",
    "release/1.0.21-alpha-rc1",
    "JEI 19.27.0.336",
    "Jade 15.10.6",
    "GeckoLib 4.9.2",
    "Cloth Config 15.0.140",
    "Fusion 1.3.14",
    "runGameTestServer",
    "runClient",
):
    if token not in install_guide:
        errors.append(f"INSTALL_WITH_ZSH.txt missing current release instruction: {token}")

if errors:
    print("RSE Alpha 1.0.21 release verification: FAIL")
    for e in errors:
        print(" -", e)
    raise SystemExit(1)

print("RSE Alpha 1.0.21 release verification: PASS")
print(f" artifact: {EXPECTED_VERSION}")
print(f" Minecraft / NeoForge / Java: {EXPECTED_MINECRAFT} / {EXPECTED_NEOFORGE} / {EXPECTED_JAVA}")
print(" integrated demo / actuator / pneumatic / signal-lab release gates: PASS")
print(" required dependency metadata: PASS")
print(" checksum/build packaging gates: PASS")
