#!/usr/bin/env python3
"""CI evidence bundle for full 130-block audit. No code mutation.

Contains complete local Java and project-owned data/resource definitions so
reviewers can inspect code bodies rather than just registry statistics.
No secrets, .git content, machine paths, external dependencies or runtime data.
"""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import json
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
audit = root / ".rse-audit"
registry = audit / "rse-130-block-hmi-coverage.json"
if not registry.exists():
    sys.exit("rse-130-block-hmi-coverage.py must run first")
records = json.loads(registry.read_text(encoding="utf-8"))["blocks"]
if len(records) != 130 or len({row["id"] for row in records}) != 130:
    sys.exit("Registry is not exactly 130 unique blocks")
paths = []
for base in ("src/main/java/dev/redstoneengineering",
             "src/main/resources/assets/redstoneengineering",
             "src/main/resources/data/redstoneengineering"):
    directory = root / base
    if directory.is_dir():
        for path in directory.rglob("*"):
            if path.is_file() and path.suffix.lower() in (".java", ".json", ".mcmeta"):
                paths.append(path)
expected = {root / "src/main/java/dev/redstoneengineering/block" / (row["class"] + ".java") for row in records}
missing = sorted(p.name for p in expected if p not in paths)
if missing:
    sys.exit("Missing block sources: " + ", ".join(missing))
target = audit / "rse-130-full-source-review.zip"
with ZipFile(target, "w", ZIP_DEFLATED, compresslevel=8) as zipped:
    for path in sorted(set(paths)):
        zipped.write(path, path.relative_to(root).as_posix())
print(f"130/130 complete block Java sources and {len(paths)} supporting owned source/resource files archived")
print("Static audit evidence; does NOT prove in-game behavior, performance or save compatibility")
