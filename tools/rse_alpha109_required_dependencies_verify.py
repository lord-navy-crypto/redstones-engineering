#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
failed = []


def text(rel: str) -> str:
    p = root / rel
    if not p.exists():
        failed.append(f"missing {rel}")
        return ""
    return p.read_text(errors="ignore")

props = text("gradle.properties")
for token in [
    "mod_version=1.0.9-alpha",
    "jei_version=19.27.0.336",
    "jade_version=15.10.6",
    "geckolib_version=4.9.2",
    "cloth_config_version=15.0.140",
    "fusion_version=1.3.14",
    "fusion_maven_version=1.3.14-neoforge-mc1.21.1",
]:
    if token not in props:
        failed.append(f"gradle.properties missing {token}")

build = text("build.gradle")
for token in [
    'implementation "mezz.jei:jei-${minecraft_version}-neoforge:${jei_version}"',
    'implementation "maven.modrinth:nvQzSEkH:${jade_modrinth_version}"',
    'implementation "software.bernie.geckolib:geckolib-neoforge-${minecraft_version}:${geckolib_version}"',
    'implementation "me.shedaniel.cloth:cloth-config-neoforge:${cloth_config_version}"',
    'implementation "maven.modrinth:fusion-connected-textures:${fusion_maven_version}"',
    'url = "https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/"',
    'url = "https://maven.shedaniel.me/"',
]:
    if token not in build:
        failed.append(f"build.gradle missing required platform token: {token}")

metadata = text("src/main/templates/META-INF/neoforge.mods.toml")
required = {
    "jei": "CLIENT",
    "jade": "BOTH",
    "geckolib": "BOTH",
    "cloth_config": "CLIENT",
    "fusion": "CLIENT",
}
for mod_id, side in required.items():
    pattern = rf'\[\[dependencies\.\$\{{mod_id\}}\]\][\s\S]*?modId="{re.escape(mod_id)}"[\s\S]*?type="required"[\s\S]*?side="{side}"'
    if not re.search(pattern, metadata):
        failed.append(f"NeoForge metadata does not require {mod_id} on {side}")

for forbidden in [
    'localRuntime "mezz.jei:',
    'compileOnly "mezz.jei:',
    'localRuntime "maven.modrinth:nvQzSEkH:',
    'compileOnly "maven.modrinth:nvQzSEkH:',
]:
    if forbidden in build:
        failed.append(f"legacy optional dependency form still present: {forbidden}")

integration = text("src/main/java/dev/redstoneengineering/integration/IntegrationStatus.java")
for token in [
    "GECKOLIB_MOD_ID",
    "CLOTH_CONFIG_MOD_ID",
    "FUSION_MOD_ID",
    "requiredPlatform",
    "MISSING",
]:
    if token not in integration:
        failed.append(f"IntegrationStatus missing {token}")

policy = text("docs/DEPENDENCY_POLICY.md")
policy_lower = policy.lower()
if "required platform" not in policy_lower:
    failed.append("dependency policy missing required platform")
for token in ["JEI", "Jade", "GeckoLib", "Cloth Config", "Fusion"]:
    if token not in policy:
        failed.append(f"dependency policy missing {token}")

manifest = text("ALPHA1_0_9_MANIFEST.txt")
for token in ["1.0.9-alpha", "Java: 21", "License: MIT", "JEI", "Jade", "GeckoLib", "Cloth Config", "Fusion"]:
    if token not in manifest:
        failed.append(f"Alpha 1.0.9 manifest missing {token}")

workflow = text(".github/workflows/build.yml")
if "rse_alpha109_required_dependencies_verify.py" not in workflow:
    failed.append("workflow missing Alpha 1.0.9 verifier")

if failed:
    print("RSE Alpha 1.0.9 required dependency verification: FAIL")
    for item in failed:
        print(" -", item)
    sys.exit(1)

print("RSE Alpha 1.0.9 required dependency verification: PASS")
print(" JEI required client platform: PASS")
print(" Jade required client/server diagnostics platform: PASS")
print(" GeckoLib required animation platform: PASS")
print(" Cloth Config required client configuration platform: PASS")
print(" Fusion required client rendering platform: PASS")
