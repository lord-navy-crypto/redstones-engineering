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


def parse_properties(path: Path) -> dict[str, str]:
    props: dict[str, str] = {}
    if not path.exists():
        return props
    for raw in path.read_text(errors="ignore").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        props[key.strip()] = value.strip()
    return props


readme = require_file("README.md")
license_file = require_file("LICENSE")
gradle_props = require_file("gradle.properties")
workflow = require_file(".github/workflows/build.yml")
gitignore = require_file(".gitignore")
require_file("CONTRIBUTING.md")
require_file("CHANGELOG.md")

props = parse_properties(gradle_props)
expected_stable = {
    "minecraft_version": "1.21.1",
    "neo_version": "21.1.249",
    "mod_id": "redstoneengineering",
    "mod_name": "Redstone Systems Engineering",
    "mod_license": "MPL-2.0",
    "mod_group_id": "dev.redstoneengineering",
}
for key, value in expected_stable.items():
    if props.get(key) != value:
        failed.append(f"gradle.properties {key}={props.get(key)!r}; expected {value!r}")

version = props.get("mod_version", "")
if not re.fullmatch(r"\d+\.\d+\.\d+-alpha(?:[.-][0-9A-Za-z.-]+)?", version):
    failed.append(f"unexpected alpha mod_version format: {version!r}")

if readme.exists():
    text = readme.read_text(errors="ignore")
    for token in [
        "Redstone Systems Engineering",
        "Minecraft",
        "NeoForge",
        "Java",
        "Reference calculations",
        "MPL-2.0",
    ]:
        if token not in text:
            failed.append(f"README missing project metadata: {token}")
    if version and version not in text:
        failed.append(f"README does not mention current artifact version {version}")

# The current version must have a matching milestone manifest. Historical manifests
# may stay in the repository, but only the current one is part of the sync contract.
if version:
    core = version.split("-", 1)[0]
    manifest_rel = "ALPHA" + core.replace(".", "_") + "_MANIFEST.txt"
    manifest = require_file(manifest_rel)
    if manifest.exists():
        text = manifest.read_text(errors="ignore")
        for token in [version, "License: MPL-2.0", "Java: 21"]:
            if token not in text:
                failed.append(f"{manifest_rel} missing: {token}")

if license_file.exists():
    text = license_file.read_text(errors="ignore")
    if "Mozilla Public License" not in text or "2.0" not in text:
        failed.append("LICENSE does not identify the Mozilla Public License 2.0")
    if "mozilla.org/MPL/2.0/" not in text and "www.mozilla.org/MPL/2.0/" not in text:
        failed.append("LICENSE does not provide the MPL 2.0 license location")

if workflow.exists():
    text = workflow.read_text(errors="ignore")
    for token in [
        "actions/checkout@",
        "actions/setup-java@",
        "java-version: '21'",
        "compileJava",
        "clean build",
        "SHA256SUMS.txt",
        "actions/upload-artifact@",
    ]:
        if token not in text:
            failed.append(f"workflow missing: {token}")

if gitignore.exists():
    lines = {line.strip() for line in gitignore.read_text(errors="ignore").splitlines()}
    if ".github/workflows/" in lines:
        failed.append(".gitignore must not ignore .github/workflows/")
    if "javac.*.args" not in lines:
        failed.append(".gitignore should ignore javac.*.args")

for path in root.glob("javac.*.args"):
    failed.append(f"temporary compiler scratch file tracked/present: {path.name}")

# Catch accidentally restored MDK/example metadata.
template = root / "src/main/templates/META-INF/neoforge.mods.toml"
if template.exists():
    text = template.read_text(errors="ignore")
    if "Example mod description" in text or "change.me" in text:
        failed.append("NeoForge metadata still contains MDK example placeholders")
    for token in [
        "lord-navy-crypto",
        "Redstone Systems Engineering",
        "${mod_license}",
        "${mod_version}",
    ]:
        if token not in text:
            failed.append(f"NeoForge metadata missing: {token}")

# No duplicate Finder/overlay-style root artifacts such as `build 2.gradle`.
for path in root.iterdir():
    if path.is_file() and re.search(r" 2(?:\.|$)", path.name):
        failed.append(f"duplicate-looking root artifact: {path.name}")

if failed:
    print("RSE repository verification: FAIL")
    for item in failed:
        print(" -", item)
    sys.exit(1)

print("RSE repository verification: PASS")
print(f" metadata/version/license/workflow/hygiene checks: PASS ({version})")
