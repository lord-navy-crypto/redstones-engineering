#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
failed = []


def require_file(rel: str) -> Path:
    path = root / rel
    if not path.exists():
        failed.append(f"missing: {rel}")
    return path


readme = require_file("README.md")
license_file = require_file("LICENSE")
gradle_props = require_file("gradle.properties")
workflow = require_file(".github/workflows/build.yml")
gitignore = require_file(".gitignore")
manifest = require_file("ALPHA1_0_3_MANIFEST.txt")

if readme.exists():
    text = readme.read_text(errors="ignore")
    for token in [
        "Redstone Systems Engineering",
        "Alpha 1.0.3",
        "Minecraft",
        "NeoForge",
        "Java",
        "Reference calculations",
        "MIT License",
    ]:
        if token not in text:
            failed.append(f"README missing project metadata: {token}")

if license_file.exists():
    text = license_file.read_text(errors="ignore")
    if not text.startswith("MIT License"):
        failed.append("LICENSE is not an MIT License text")
    if "Copyright (c) 2026 lord-navy-crypto" not in text:
        failed.append("LICENSE copyright line is missing or unexpected")

if gradle_props.exists():
    props = {}
    for line in gradle_props.read_text(errors="ignore").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        props[key.strip()] = value.strip()
    expected = {
        "minecraft_version": "1.21.1",
        "neo_version": "21.1.249",
        "mod_id": "redstoneengineering",
        "mod_name": "Redstone Systems Engineering",
        "mod_license": "MIT",
        "mod_version": "1.0.3-alpha",
        "mod_group_id": "dev.redstoneengineering",
    }
    for key, value in expected.items():
        if props.get(key) != value:
            failed.append(f"gradle.properties {key}={props.get(key)!r}; expected {value!r}")

if workflow.exists():
    text = workflow.read_text(errors="ignore")
    for token in ["actions/checkout@", "actions/setup-java@", "java-version: '21'", "compileJava", "clean build"]:
        if token not in text:
            failed.append(f"workflow missing: {token}")

if gitignore.exists():
    lines = {line.strip() for line in gitignore.read_text(errors="ignore").splitlines()}
    if ".github/workflows/" in lines:
        failed.append(".gitignore must not ignore .github/workflows/")
    if "javac.*.args" not in lines:
        failed.append(".gitignore should ignore javac.*.args")

if manifest.exists():
    text = manifest.read_text(errors="ignore")
    for token in ["Alpha 1.0.3", "1.0.3-alpha", "License: MIT", "Java: 21"]:
        if token not in text:
            failed.append(f"manifest missing: {token}")

# Repository root should not contain local javac argument scratch files.
for path in root.glob("javac.*.args"):
    failed.append(f"temporary compiler scratch file tracked/present: {path.name}")

# Catch an accidentally restored MDK/example description in the project metadata.
template = root / "src/main/templates/META-INF/neoforge.mods.toml"
if template.exists():
    text = template.read_text(errors="ignore")
    if "Example mod description" in text or "change.me" in text:
        failed.append("NeoForge metadata still contains MDK example placeholders")
    for token in ["lord-navy-crypto", "Redstone Systems Engineering", "${mod_license}", "${mod_version}"]:
        if token not in text:
            failed.append(f"NeoForge metadata missing: {token}")

if failed:
    print("RSE repository verification: FAIL")
    for item in failed:
        print(" -", item)
    sys.exit(1)

print("RSE repository verification: PASS")
print(" metadata/version/license/workflow/hygiene checks: PASS")
