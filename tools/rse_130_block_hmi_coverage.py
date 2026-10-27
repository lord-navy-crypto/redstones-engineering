#!/usr/bin/env python3
"""Whole-registry PR #229 HMI entry audit (122 core + 8 engineering systems).

Distinguishes source-declared interaction from actual runtime acceptance. Runs
in CI without a Minecraft client and publishes per-block evidence in .rse-audit.
"""
from collections import Counter
from pathlib import Path
import csv
import json
import re
import sys

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
BLOCKS = ROOT / "src/main/java/dev/redstoneengineering/block"
OUT = ROOT / ".rse-audit"
OUT.mkdir(parents=True, exist_ok=True)
sources = (
    ("core", ROOT / "src/main/java/dev/redstoneengineering/RedstoneEngineering.java"),
    ("systems", ROOT / "src/main/java/dev/redstoneengineering/EngineeringSystemsModule.java"),
)
EXPECT = {"core": 122, "systems": 8}
# A compass is intentionally a passive N/E/S/W world datum and provides
# right-click coordinate feedback without mutable parameters. This is not a
# missing HMI for a configurable machine.
EXCEPTIONS = {"engineering_compass": "passive world datum: read-only coordinate feedback"}

def read(path):
    return path.read_text(encoding="utf-8", errors="replace") if path.exists() else ""

def registered():
    records = []
    errors = []
    for family, path in sources:
        src = read(path)
        pairs = re.findall(
            r'\bregisterBlock\s*\(\s*"([a-z0-9_]+)"\s*,\s*([A-Za-z0-9_]+)::new',
            src,
        )
        for block_id, cls in pairs:
            records.append((family, block_id, cls))
        if len(pairs) != EXPECT[family]:
            errors.append(f"{family}: expected {EXPECT[family]} registrations, saw {len(pairs)}")
    if len({r[1] for r in records}) != 130:
        errors.append("duplicate IDs or missing block in 130-item combined registry")
    return records, errors

def first_interaction(cls):
    seen = set()
    for depth in range(12):
        if cls in seen:
            break
        seen.add(cls)
        source = read(BLOCKS / f"{cls}.java")
        if not source:
            return "", "MISSING_SOURCE", cls, depth
        # Find the nearest overriding action along the real block inheritance chain.
        signature = re.search(r'\buseWithoutItem\s*\(', source)
        if signature:
            i = source.find("{", signature.end())
            if i < 0:
                return "", "INVALID_METHOD", cls, depth
            balance = 1
            j = i + 1
            while j < len(source) and balance:
                if source[j] == "{":
                    balance += 1
                elif source[j] == "}":
                    balance -= 1
                j += 1
            return source[i:j], "DIRECT" if depth == 0 else "INHERITED", cls, depth
        base = re.search(r'\bclass\s+' + re.escape(cls)
                         + r'\b[^\{]*?\bextends\s+([A-Za-z0-9_]+)', source, re.S)
        if not base:
            break
        cls = base.group(1)
    return "", "NO_INTERACTION", cls, -1

def classify(interaction):
    if "FieldDeviceUi.openUniversal(" in interaction:
        return "UNIVERSAL_LDLIB2"
    if "FieldDeviceUi.open(" in interaction:
        return "SHARED_OR_DEDICATED_LDLIB2"
    if re.search(r'\bopenMenu\s*\(', interaction):
        return "DEDICATED_LDLIB2"
    return "NO_GUI"

records, errors = registered()
rows = []
for family, block_id, cls in records:
    body, declaration, owner, depth = first_interaction(cls)
    ui = classify(body)
    if block_id in EXCEPTIONS:
        status = "INTENTIONAL_NON_GUI"
        if not body or ui != "NO_GUI":
            errors.append(f"{block_id}: exemption no longer matches behavior")
    elif ui == "NO_GUI":
        status = "MISSING_HMI_ENTRY"
        errors.append(f"{block_id} ({cls}): {declaration} from {owner}, no LDLib2 opener")
    else:
        status = "DECLARED_HMI_ENTRY"
    rows.append({
        "registry": family,
        "id": block_id,
        "class": cls,
        "interaction_owner": owner,
        "interaction_source": declaration,
        "entry_kind": ui,
        "status": status,
        "client_visual_verified": False,
        "gametest_verified_by_this_audit": False,
    })

rows.sort(key=lambda item: (item["registry"], item["id"]))
summary = {
    "registered_total": len(rows),
    "core": sum(x["registry"] == "core" for x in rows),
    "systems": sum(x["registry"] == "systems" for x in rows),
    "entry_kinds": dict(Counter(x["entry_kind"] for x in rows)),
    "statuses": dict(Counter(x["status"] for x in rows)),
    "errors": errors,
    "semantics": "source-level HMI-entry audit, NOT a game client, GameTest or screenshot verification",
}
(OUT / "rse-130-block-hmi-coverage.json").write_text(
    json.dumps({"summary": summary, "blocks": rows}, ensure_ascii=False, indent=2) + "\n",
    encoding="utf-8",
)
with (OUT / "rse-130-block-hmi-coverage.csv").open("w", newline="", encoding="utf-8") as fd:
    writer = csv.DictWriter(fd, fieldnames=list(rows[0]) if rows else ["id"])
    writer.writeheader()
    writer.writerows(rows)
md = [
    "# PR #229 — 130-block LDLib2 entry matrix",
    "",
    "Static source inventory only. This does **not** certify successful in-game opening,",
    "editable-value synchronization, responsiveness, multiplayer behavior or GameTests.",
    "",
    f"Registered: {len(rows)} (core={summary['core']}, systems={summary['systems']})",
    f"Entry kind breakdown: {summary['entry_kinds']}",
    f"Coverage status: {summary['statuses']}",
    "",
    "| ID | Java class | Entry | Method owner | Status |",
    "| --- | --- | --- | --- | --- |",
]
for r in rows:
    md.append(f"| {r['id']} | {r['class']} | {r['entry_kind']} | {r['interaction_owner']} | {r['status']} |")
(OUT / "rse-130-block-hmi-coverage.md").write_text("\n".join(md) + "\n", encoding="utf-8")

if errors:
    print("RSE 130 BLOCK HMI COVERAGE: FAIL")
    for error in errors:
        print(" - " + error)
    raise SystemExit(1)
print("RSE 130 BLOCK HMI COVERAGE: PASS")
print(f" core={summary['core']} systems={summary['systems']} registry total={len(rows)}")
print(f" entry statuses={summary['statuses']}")
print(" three machine-readable/audit artifacts: JSON, CSV, Markdown")
print(" source entry ≠ actual runtime or visual acceptance")
