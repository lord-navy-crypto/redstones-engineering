#!/usr/bin/env python3
"""Inventory synchronous neighbor-update risks across RSE Java sources.

The runtime GameTest log gate is authoritative for actual neighbor-update storms. This scanner
makes source-side suspects explicit and locks the shared no-op suppression rules that prevent
solver recomputation from recursively waking the same network forever.
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

# Guard shared redstone notification helpers because changes here multiply across many blocks.
directional = (JAVA_ROOT / "block/DirectionalSignalBlock.java").read_text(encoding="utf-8")
endpoint = (JAVA_ROOT / "block/DirectionalRedstoneEndpointBlock.java").read_text(encoding="utf-8")
if directional.count("updateNeighborsAt(") > 4:
    raise SystemExit("FAIL: DirectionalSignalBlock accumulated excessive explicit neighbor notifications")
if endpoint.count("updateNeighborsAt(") > 2:
    raise SystemExit("FAIL: DirectionalRedstoneEndpointBlock accumulated excessive explicit neighbor notifications")

# Pneumatic blocks commonly call recompute() from neighborChanged(). Therefore the solver must
# suppress vanilla neighbor notifications on an identical pressure/quality result; otherwise
# recompute -> updateNeighborsAt -> neighborChanged -> recompute is a synchronous feedback loop.
pneumatic = (JAVA_ROOT / "physics/PneumaticNetwork.java").read_text(encoding="utf-8")
for token in (
    "int oldPressure = InformationRuntime.value(level, \"pneumatic\", pos);",
    "int oldQuality = InformationRuntime.quality(level, \"pneumatic\", pos);",
    "boolean oldValid = InformationRuntime.valid(level, \"pneumatic\", pos);",
    "boolean effectiveChanged = oldPressure != pressure || oldQuality != quality || !oldValid;",
    "if (effectiveChanged) {",
    "level.updateNeighborsAt(pos, block);",
):
    if token not in pneumatic:
        raise SystemExit(f"FAIL: PneumaticNetwork lost no-op neighbor suppression token: {token}")

print("  pneumatic no-op solver notification suppression: PASS")
print("  PASS: source-side neighbor-update inventory generated; runtime GameTest log gate is authoritative")
