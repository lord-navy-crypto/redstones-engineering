#!/usr/bin/env python3
"""Inventory synchronous neighbor-update risks across RSE Java sources.

This is diagnostic by design: the runtime GameTest log gate is authoritative for actual
neighbor-update storms. This scanner makes the source-side suspects explicit so a future
regression cannot hide in a large block catalog.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA_ROOT = ROOT / "src/main/java/dev/redstoneengineering"

records = []
for path in sorted(JAVA_ROOT.rglob("*.java")):
    source = path.read_text(encoding="utf-8")
    update_neighbors = source.count("updateNeighborsAt(")
    neighbor_changed = "neighborChanged(" in source
    set_block = "setBlock(" in source
    recompute = "recompute" in source
    if update_neighbors or (neighbor_changed and (set_block or recompute)):
        records.append((path.relative_to(ROOT), update_neighbors, neighbor_changed, set_block, recompute))

print("RSE NEIGHBOR-UPDATE SOURCE AUDIT")
print(f"  candidate Java sources: {len(records)}")
for path, update_count, neighbor_changed, set_block, recompute in records:
    flags = []
    if update_count:
        flags.append(f"updateNeighborsAt={update_count}")
    if neighbor_changed:
        flags.append("neighborChanged")
    if set_block:
        flags.append("setBlock")
    if recompute:
        flags.append("recompute")
    print(f"  - {path}: {', '.join(flags)}")

# Guard the two shared redstone notification helpers because changes here multiply across
# many blocks. Runtime storm detection remains the hard behavioral gate.
directional = (JAVA_ROOT / "block/DirectionalSignalBlock.java").read_text(encoding="utf-8")
endpoint = (JAVA_ROOT / "block/DirectionalRedstoneEndpointBlock.java").read_text(encoding="utf-8")
if directional.count("updateNeighborsAt(") > 4:
    raise SystemExit("FAIL: DirectionalSignalBlock accumulated excessive explicit neighbor notifications")
if endpoint.count("updateNeighborsAt(") > 2:
    raise SystemExit("FAIL: DirectionalRedstoneEndpointBlock accumulated excessive explicit neighbor notifications")

print("  PASS: source-side neighbor-update inventory generated; runtime GameTest log gate is authoritative")
