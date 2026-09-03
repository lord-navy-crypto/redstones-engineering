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

props = text("gradle.properties")
match = re.search(r"^mod_version=(\d+)\.(\d+)\.(\d+)-alpha(?:[.-][0-9A-Za-z.-]+)?$", props, re.MULTILINE)
if not match:
    failed.append("gradle.properties missing alpha semantic version")
    current = (0, 0, 0)
else:
    current = tuple(map(int, match.groups()))
    if current < (1, 0, 8):
        failed.append(f"Alpha 1.0.8 verifier requires version >= 1.0.8-alpha, found {current}")

for token in ["jei_version=19.27.0.336", "jade_version=15.10.6"]:
    if token not in props:
        failed.append(f"gradle.properties missing Alpha 1.0.8 dependency foundation token: {token}")

build = text("build.gradle")
for token in [
    'url = "https://maven.blamejared.com"',
    'url = "https://api.modrinth.com/maven"',
    'gameTestServer',
]:
    if token not in build:
        failed.append(f"build.gradle missing dependency foundation token: {token}")

integration = text("src/main/java/dev/redstoneengineering/integration/IntegrationStatus.java")
for token in ["JEI_MOD_ID", "JADE_MOD_ID", "ModList.get().isLoaded"]:
    if token not in integration:
        failed.append(f"IntegrationStatus missing historical integration token: {token}")

# Alpha 1.0.8 itself required optional JEI/Jade semantics. From Alpha 1.0.9 onward
# dependency policy intentionally changes, so the historical gate must not block
# the newer architecture.
if current == (1, 0, 8):
    for token in [
        'compileOnly "mezz.jei:jei-${minecraft_version}-common-api:${jei_version}"',
        'localRuntime "mezz.jei:jei-${minecraft_version}-neoforge:${jei_version}"',
        'compileOnly "maven.modrinth:nvQzSEkH:${jade_modrinth_version}"',
        'localRuntime "maven.modrinth:nvQzSEkH:${jade_modrinth_version}"',
    ]:
        if token not in build:
            failed.append(f"Alpha 1.0.8 optional integration token missing: {token}")

workflow = text(".github/workflows/build.yml")
if "rse_alpha108_dependency_verify.py" not in workflow:
    failed.append("workflow missing Alpha 1.0.8 verifier")

if failed:
    print("RSE Alpha 1.0.8 dependency foundation verification: FAIL")
    for item in failed:
        print(" -", item)
    sys.exit(1)

print("RSE Alpha 1.0.8 dependency foundation regression: PASS")
print(" JEI/Jade repository and detection foundation retained: PASS")
if current >= (1, 0, 9):
    print(" optional-policy assertions delegated to newer milestone: PASS")
