#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
failed = []


def text(rel: str) -> str:
    path = root / rel
    if not path.exists():
        failed.append(f"missing {rel}")
        return ""
    return path.read_text(errors="ignore")


def require(rel: str, *tokens: str) -> None:
    body = text(rel)
    for token in tokens:
        if token not in body:
            failed.append(f"{rel}: missing {token!r}")


props = text("gradle.properties")
version_match = re.search(r"^mod_version=(\d+)\.(\d+)\.(\d+)-alpha(?:[.-][0-9A-Za-z.-]+)?$", props, re.MULTILINE)
if not version_match:
    failed.append("gradle.properties missing alpha semantic version")
else:
    current = tuple(map(int, version_match.groups()))
    if current < (1, 0, 8):
        failed.append(f"Alpha 1.0.8 verifier requires version >= 1.0.8-alpha, found {current}")

for token in [
    "jei_version=19.27.0.336",
    "jade_modrinth_version=eYz2YBGT",
]:
    if token not in props:
        failed.append(f"gradle.properties missing pinned optional integration: {token}")

build = text("build.gradle")
required_build_tokens = [
    'url = "https://maven.blamejared.com"',
    'includeGroup "mezz.jei"',
    'url = "https://api.modrinth.com/maven"',
    'includeGroup "maven.modrinth"',
    'compileOnly "mezz.jei:jei-${minecraft_version}-common-api:${jei_version}"',
    'compileOnly "mezz.jei:jei-${minecraft_version}-neoforge-api:${jei_version}"',
    'localRuntime "mezz.jei:jei-${minecraft_version}-neoforge:${jei_version}"',
    'compileOnly "maven.modrinth:nvQzSEkH:${jade_modrinth_version}"',
    'localRuntime "maven.modrinth:nvQzSEkH:${jade_modrinth_version}"',
    'runtimeClasspath.extendsFrom localRuntime',
    'gameTestServer',
]
for token in required_build_tokens:
    if token not in build:
        failed.append(f"build.gradle missing dependency foundation token: {token}")

# Optional integrations must never become implementation/api dependencies in RSE core.
for pattern, label in [
    (r'(?m)^\s*implementation\s+["\']mezz\.jei:', "JEI implementation dependency"),
    (r'(?m)^\s*api\s+["\']mezz\.jei:', "JEI api dependency"),
    (r'(?m)^\s*implementation\s+["\']maven\.modrinth:nvQzSEkH:', "Jade implementation dependency"),
    (r'(?m)^\s*api\s+["\']maven\.modrinth:nvQzSEkH:', "Jade api dependency"),
]:
    if re.search(pattern, build):
        failed.append(f"optional integration incorrectly promoted to required/public dependency: {label}")

# Catch common shadow/shading patterns unless a future milestone explicitly revises policy.
for forbidden in ["com.github.johnrengelman.shadow", "shadowJar", "jarJar(", "include("]:
    if forbidden in build:
        failed.append(f"build.gradle contains possible dependency bundling/shading token: {forbidden}")

require(
    "src/main/java/dev/redstoneengineering/integration/IntegrationStatus.java",
    "JEI_MOD_ID",
    "JADE_MOD_ID",
    'ModList.get().isLoaded',
    "AVAILABLE",
    "ABSENT",
)
require(
    "docs/DEPENDENCY_POLICY.md",
    "NeoForge alone",
    "compileOnly",
    "localRuntime",
    "GeckoLib",
    "Ponder",
    "Fusion",
)
require(
    "ALPHA1_0_8_MANIFEST.txt",
    "1.0.8-alpha",
    "Java: 21",
    "License: MIT",
    "JEI 19.27.0.336",
    "Jade 15.10.6+neoforge",
)

# RSE's generated NeoForge metadata must not mark JEI or Jade as required.
metadata = text("src/main/templates/META-INF/neoforge.mods.toml")
for optional_mod in ["jei", "jade"]:
    # There may be prose references in the future; only reject explicit dependency modId entries.
    if re.search(rf'(?m)^\s*modId\s*=\s*"{optional_mod}"\s*$', metadata):
        failed.append(f"NeoForge metadata makes optional integration explicit dependency: {optional_mod}")

workflow = text(".github/workflows/build.yml")
if "rse_alpha108_dependency_verify.py" not in workflow:
    failed.append("workflow missing Alpha 1.0.8 dependency verifier")

if failed:
    print("RSE Alpha 1.0.8 dependency foundation verification: FAIL")
    for item in failed:
        print(" -", item)
    sys.exit(1)

print("RSE Alpha 1.0.8 dependency foundation verification: PASS")
print(" NeoForge-only core launch policy: PASS")
print(" JEI optional compile/runtime integration: PASS")
print(" Jade optional compile/runtime integration: PASS")
print(" no shading / no required optional-mod metadata: PASS")
print(" native GameTest foundation retained: PASS")
