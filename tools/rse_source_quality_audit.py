#!/usr/bin/env python3
"""Repository-wide source/resource quality audit for RSE.

This catches structural mistakes that feature-specific verifiers can miss:
package/path mismatches, malformed or empty resources, local model references that
point nowhere, duplicate case-insensitive resource names, trailing whitespace,
high-cardinality BlockState ranges, deprecated event registration APIs, and
Gradle DSL forms that are already scheduled for removal.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []
java_root = root / "src/main/java"
resources_root = root / "src/main/resources"
assets_root = resources_root / "assets/redstoneengineering"


# 1) Java structural hygiene.
for path in java_root.rglob("*.java"):
    body = path.read_text(errors="ignore")
    if not body.strip():
        errors.append(f"empty Java source: {path.relative_to(root)}")
        continue

    relative = path.relative_to(java_root)
    expected_package = ".".join(relative.parts[:-1])
    match = re.search(r"^\s*package\s+([A-Za-z0-9_.]+)\s*;", body, re.MULTILINE)
    if not match:
        errors.append(f"missing package declaration: {path.relative_to(root)}")
    elif match.group(1) != expected_package:
        errors.append(
            f"package/path mismatch: {path.relative_to(root)} declares {match.group(1)!r}, expected {expected_package!r}"
        )

    # Cheap but useful syntax smoke check. Real correctness is still compileJava's job.
    if body.count("{") != body.count("}"):
        errors.append(f"brace imbalance: {path.relative_to(root)}")

    for line_no, line in enumerate(body.splitlines(), 1):
        if line.rstrip() != line:
            errors.append(f"trailing whitespace: {path.relative_to(root)}:{line_no}")
            break

    # NeoForge 21.1 supports direct IEventBus listener registration and the annotation
    # event subscriber path is deprecated in the pinned API. Keep repository-owned event
    # registration warning-free rather than suppressing removal warnings.
    if "@EventBusSubscriber" in body:
        errors.append(f"deprecated EventBusSubscriber annotation: {path.relative_to(root)}")
    if "@SubscribeEvent" in body:
        errors.append(f"deprecated SubscribeEvent annotation: {path.relative_to(root)}")

    for prop in re.finditer(
        r'IntegerProperty\.create\([^,]+,\s*(-?\d+)\s*,\s*(-?\d+)\s*\)',
        body,
    ):
        lo, hi = map(int, prop.groups())
        if hi < lo:
            errors.append(f"invalid IntegerProperty range in {path.name}: {lo}..{hi}")
        elif hi - lo + 1 > 256:
            errors.append(f"high-cardinality IntegerProperty in {path.name}: {lo}..{hi}")


# 2) JSON parse/non-empty checks and case-insensitive collision detection.
json_paths = list(resources_root.rglob("*.json"))
seen_casefold: dict[str, Path] = {}
parsed: dict[Path, object] = {}
for path in json_paths:
    rel = path.relative_to(resources_root)
    key = str(rel).casefold()
    if key in seen_casefold and seen_casefold[key] != path:
        errors.append(
            f"case-insensitive resource collision: {seen_casefold[key].relative_to(root)} vs {path.relative_to(root)}"
        )
    else:
        seen_casefold[key] = path

    if path.stat().st_size == 0:
        errors.append(f"empty JSON resource: {path.relative_to(root)}")
        continue
    try:
        parsed[path] = json.loads(path.read_text())
    except Exception as exc:
        errors.append(f"bad JSON: {path.relative_to(root)}: {exc}")


# 3) Local model references from blockstates/models must resolve.
reference_pattern = re.compile(r'"(?:model|parent)"\s*:\s*"redstoneengineering:(block|item)/([a-z0-9_./-]+)"')
for path, data in parsed.items():
    if "assets/redstoneengineering" not in str(path).replace("\\", "/"):
        continue
    raw = json.dumps(data, separators=(",", ":"))
    for kind, name in reference_pattern.findall(raw):
        target = assets_root / "models" / kind / f"{name}.json"
        if not target.exists():
            errors.append(
                f"unresolved local model reference in {path.relative_to(root)}: redstoneengineering:{kind}/{name}"
            )


# 4) Blockstate files should have a corresponding item model for player-placeable blocks.
# Historical/special resources can opt out by having no same-name block model; this keeps
# the rule conservative instead of assuming every data file is a placeable block.
blockstates_dir = assets_root / "blockstates"
block_models_dir = assets_root / "models/block"
item_models_dir = assets_root / "models/item"
if blockstates_dir.exists():
    for state in blockstates_dir.glob("*.json"):
        stem = state.stem
        if (block_models_dir / f"{stem}.json").exists() and not (item_models_dir / f"{stem}.json").exists():
            errors.append(f"block has model/blockstate but no item model: {stem}")


# 5) Repository root and build-script hygiene.
for path in root.iterdir():
    if not path.is_file():
        continue
    if path.name.startswith("javac.") and path.name.endswith(".args"):
        errors.append(f"temporary compiler args file at repository root: {path.name}")
    if re.search(r"\s+copy(?:\s|\.|$)", path.name, re.IGNORECASE):
        errors.append(f"copy-like root artifact: {path.name}")
    if re.search(r"\s+\d+(?:\.|$)", path.name):
        errors.append(f"duplicate-looking numbered root artifact: {path.name}")

build_gradle = root / "build.gradle"
if build_gradle.exists():
    gradle_body = build_gradle.read_text(errors="ignore")
    # Gradle 8 warns that Groovy space-assignment syntax such as `url "..."`
    # is deprecated and will be removed in Gradle 10. Require property assignment.
    if re.search(r"(?m)^\s*url\s+['\"]", gradle_body):
        errors.append("deprecated Gradle Groovy space assignment for Maven repository url")


if errors:
    print("RSE source quality audit: FAIL")
    for item in errors:
        print(" -", item)
    raise SystemExit(1)

print("RSE source quality audit: PASS")
print(f"  Java sources checked: {len(list(java_root.rglob('*.java')))}")
print(f"  JSON resources checked: {len(json_paths)}")
print("  package/path + braces + whitespace: PASS")
print("  deprecated annotation event registration: PASS")
print("  JSON parse + case-collision checks: PASS")
print("  local model reference integrity: PASS")
print("  block/item model pairing: PASS")
print("  BlockState cardinality + root hygiene: PASS")
print("  Gradle DSL assignment hygiene: PASS")