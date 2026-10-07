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
match = re.search(r"^mod_version=(\d+)\.(\d+)\.(\d+)-alpha(?:[.-][0-9A-Za-z.-]+)?$", props, re.MULTILINE)
if not match:
    failed.append("gradle.properties missing alpha semantic version")
    current = (0, 0, 0)
else:
    current = tuple(map(int, match.groups()))
    if current < (1, 0, 9):
        failed.append(f"Alpha 1.0.9 dependency regression requires version >= 1.0.9-alpha, found {current}")

# Alpha 1.0.9 itself historically required five libraries. Current releases are
# allowed to revise that policy when the implementation no longer uses them.
manifest = text("ALPHA1_0_9_MANIFEST.txt")
for token in ["1.0.9-alpha", "JEI", "Jade", "GeckoLib", "Cloth Config", "Fusion"]:
    if token not in manifest:
        failed.append(f"historical Alpha 1.0.9 manifest missing {token}")

if current > (1, 0, 9):
    for token in ["jade_version=15.10.6", "geckolib_version=4.9.2", "ldlib2_version=2.2.26"]:
        if token not in props:
            failed.append(f"current runtime dependency property missing: {token}")

    for obsolete in ["jei_version=", "cloth_config_version=", "fusion_version=", "fusion_maven_version="]:
        if obsolete in props:
            failed.append(f"unused current dependency property still present: {obsolete}")

    build = text("build.gradle")
    for token in [
        'implementation "maven.modrinth:nvQzSEkH:${jade_modrinth_version}"',
        'implementation "software.bernie.geckolib:geckolib-neoforge-${minecraft_version}:${geckolib_version}"',
        'url = "https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/"',
        'implementation "com.lowdragmc.ldlib2:ldlib2-neoforge-${minecraft_version}:${ldlib2_version}:all"',
        'url = "https://maven.firstdark.dev/snapshots"',
    ]:
        if token not in build:
            failed.append(f"build.gradle missing current required platform token: {token}")

    for obsolete in [
        'mezz.jei:',
        'me.shedaniel.cloth:',
        'fusion-connected-textures',
        'https://maven.blamejared.com',
        'https://maven.shedaniel.me/',
    ]:
        if obsolete in build:
            failed.append(f"unused dependency/repository still present in build.gradle: {obsolete}")

    metadata = text("src/main/templates/META-INF/neoforge.mods.toml")
    required = {"jade": "BOTH", "geckolib": "BOTH", "ldlib2": "BOTH"}
    for mod_id, side in required.items():
        pattern = rf'\[\[dependencies\.\$\{{mod_id\}}\]\][\s\S]*?modId="{re.escape(mod_id)}"[\s\S]*?type="required"[\s\S]*?side="{side}"'
        if not re.search(pattern, metadata):
            failed.append(f"NeoForge metadata does not require {mod_id} on {side}")
    for obsolete in ['modId="jei"', 'modId="cloth_config"', 'modId="fusion"']:
        if obsolete in metadata:
            failed.append(f"unused hard dependency still present in metadata: {obsolete}")

    integration = text("src/main/java/dev/redstoneengineering/integration/IntegrationStatus.java")
    for token in ["JADE_MOD_ID", "GECKOLIB_MOD_ID", "LDLIB2_MOD_ID", "requiredPlatform"]:
        if token not in integration:
            failed.append(f"IntegrationStatus missing current dependency token: {token}")
    for obsolete in ["JEI_MOD_ID", "CLOTH_CONFIG_MOD_ID", "FUSION_MOD_ID"]:
        if obsolete in integration:
            failed.append(f"IntegrationStatus still reports unused platform member: {obsolete}")

    policy = text("docs/DEPENDENCY_POLICY.md")
    for token in ["Jade", "GeckoLib", "LDLib2", "JEI", "Cloth Config", "Fusion", "Not hard runtime dependencies"]:
        if token not in policy:
            failed.append(f"dependency policy missing evidence-based dependency statement: {token}")

workflow = text(".github/workflows/build.yml")
if "rse_alpha109_required_dependencies_verify.py" not in workflow:
    failed.append("workflow missing Alpha 1.0.9 dependency verifier")

if failed:
    print("RSE Alpha 1.0.9 dependency verification: FAIL")
    for item in failed:
        print(" -", item)
    sys.exit(1)

print("RSE Alpha 1.0.9 dependency history/current-policy verification: PASS")
print(" historical five-library decision preserved as history: PASS")
if current > (1, 0, 9):
    print(" current hard runtime platform is evidence-based Jade + GeckoLib + LDLib2: PASS")
    print(" unused JEI / Cloth Config / Fusion prerequisites removed: PASS")
