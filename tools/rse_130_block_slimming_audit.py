#!/usr/bin/env python3
"""Non-destructive all-registry implementation inventory and overlap review.
Similarity is not equivalent behavior, and this is NOT runtime acceptance.
"""
from collections import Counter
from pathlib import Path
import csv
import json
import re
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
out = root / ".rse-audit"
out.mkdir(exist_ok=True)
reg = out / "rse-130-block-hmi-coverage.json"
if not reg.is_file():
    sys.exit("Run rse_130_block_hmi_coverage.py first")
blocks = json.loads(reg.read_text(encoding="utf-8"))["blocks"]
errors = []
if len(blocks) != 130 or len({b["id"] for b in blocks}) != 130:
    errors.append("expected exactly 130 unique registered IDs")

# Code-reuse candidates, NOT block deletion or save-format migration proposals.
reviews = {
    "quartz_clock": ("quartz_oscillator", "quartz_lab_oscillator"),
    "filtering": ("precision_filter", "signal_conditioner", "lapis_low_pass_filter"),
    "range_measurement": ("range_sensor", "lapis_precision_range_sensor"),
    "sampling_hold": ("sample_hold", "quartz_triggered_lapis_sampler"),
    "amethyst_frequency": ("amethyst_resonator", "amethyst_tuned_resonator", "amethyst_frequency_filter"),
    "optical_series": ("optical_attenuator", "optical_channel_filter"),
    "vibration_excitation": ("mechanical_exciter", "hydroacoustic_exciter"),
    "metering": ("signal_analyzer", "lapis_precision_meter", "copper_circuit_meter"),
    "optical_transport": ("optical_emitter", "optical_receiver", "free_space_optical_transmitter", "free_space_optical_receiver"),
    "pneumatic_valves": ("pneumatic_valve", "pneumatic_proportional_valve", "pneumatic_check_valve", "pneumatic_relief_valve", "pressure_regulator"),
    "medium_junctions": ("redstone_cable_junction", "copper_cable_junction", "optical_fiber_junction"),
    "failure_diagnostics": ("watchdog", "fault_latch", "alarm_processor", "safety_interlock", "topology_debugger"),
    "digital_transport": ("differential_data_pair", "differential_driver", "differential_receiver", "serial_data_line", "serializer", "deserializer"),
    "instrument_cables": ("instrument_cable", "shielded_instrument_cable"),
    "optical_sensor_conversion": ("optical_power_meter", "lapis_optical_transducer"),
}
membership = {}
for group, ids in reviews.items():
    for id_ in ids:
        if id_ in membership:
            errors.append("double-assigned group: " + id_)
        membership[id_] = group
if set(membership) - {b["id"] for b in blocks}:
    errors.append("review candidate not currently registered")

rows = []
for b in blocks:
    path = root / "src/main/java/dev/redstoneengineering/block" / (b["class"] + ".java")
    if not path.is_file():
        errors.append("missing source: " + b["class"])
        continue
    src = path.read_text(encoding="utf-8", errors="replace")
    parent = re.search(r"\bclass\s+" + re.escape(b["class"]) +
                       r"\b[^{]*?\bextends\s+(\w+)", src, re.S)
    props = sorted(set(re.findall(
        r"\b(?:IntegerProperty|BooleanProperty|DirectionProperty)\s+([A-Z][A-Z0-9_]*)\s*=", src)))
    domains = sorted(set(re.findall(r"\bEngineeringDomain\.([A-Z0-9_]+)", src)))
    rows.append({
        "registry": b["registry"], "id": b["id"], "class": b["class"],
        "parent": parent.group(1) if parent else "UNKNOWN",
        "source_lines": src.count("\n") + 1,
        "source_bytes": len(src.encode("utf-8")),
        "state_props": ";".join(props),
        "port_domain_constants": ";".join(domains),
        "tick_override": bool(re.search(r"\bvoid\s+tick\s*\(", src)),
        "on_remove_override": bool(re.search(r"\bvoid\s+onRemove\s*\(", src)),
        "port_snapshot": "engineeringSnapshot(" in src,
        "review_group": membership.get(b["id"], "none"),
        "conclusion": "SHARED_INTERNALS_CANDIDATE_NOT_PROVEN_DUPLICATE"
                      if b["id"] in membership else "RETAIN_PENDING_BEHAVIORAL_REVIEW",
        "functional_equivalence_verified": False,
        "world_save_migration_verified": False,
        "runtime_verified": False,
    })
if len(rows) != 130:
    errors.append(f"found {len(rows)} source implementations instead of 130")
rows.sort(key=lambda x: (x["registry"], x["id"]))
with (out / "rse-130-block-slimming-inventory.csv").open("w", newline="", encoding="utf-8") as fp:
    writer = csv.DictWriter(fp, fieldnames=list(rows[0]))
    writer.writeheader()
    writer.writerows(rows)
summary = {
    "count": len(rows),
    "core": sum(r["registry"] == "core" for r in rows),
    "systems": sum(r["registry"] == "systems" for r in rows),
    "source_lines": sum(r["source_lines"] for r in rows),
    "source_bytes": sum(r["source_bytes"] for r in rows),
    "tick_overrides": sum(r["tick_override"] for r in rows),
    "remove_overrides": sum(r["on_remove_override"] for r in rows),
    "snapshot_handlers": sum(r["port_snapshot"] for r in rows),
    "review_groups": len(reviews),
    "grouped_candidates": len(membership),
    "errors": errors,
    "scope": "static source metrics only; no proven duplicate behavior or deletion safety",
}
(out / "rse-130-block-slimming-inventory.json").write_text(
    json.dumps({"summary": summary, "candidate_groups": reviews, "blocks": rows},
               ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
md = [
    "# RSE 130-block implementation footprint",
    "",
    "Static audit only. Similar implementation does NOT justify deleting blocks.",
    "",
    f"Count: {len(rows)} (core {summary['core']}, systems {summary['systems']}).",
    f"Java lines in registered block classes: {summary['source_lines']}.",
    f"Tick overrides {summary['tick_overrides']}; onRemove overrides {summary['remove_overrides']}; port snapshots {summary['snapshot_handlers']}.",
    "",
    "Candidate groups for shared implementations (retain individual IDs, recipes,",
    "world saves, port directions, timing, and media unless behavior is separately tested):",
    "",
]
for group, ids in reviews.items():
    md.append("- " + group + ": " + ", ".join(ids))
md += ["", "| ID | Source class | Lines | Parent | Declared domains | Review family |",
       "| --- | --- | ---: | --- | --- | --- |"]
for r in rows:
    md.append("| " + " | ".join([r["id"], r["class"], str(r["source_lines"]),
                                 r["parent"], r["port_domain_constants"] or "—",
                                 r["review_group"]]) + " |")
(out / "rse-130-block-slimming-inventory.md").write_text("\n".join(md) + "\n", encoding="utf-8")
if errors:
    print("RSE 130 BLOCK SLIMMING AUDIT: FAIL")
    for e in errors:
        print(" -", e)
    sys.exit(1)
print("RSE 130 BLOCK SLIMMING AUDIT: PASS")
print(f" 130 registry implementations, {summary['source_lines']} Java lines")
print(f" {len(reviews)} non-destructive shared-code review groups")
print(" No blocks, saves or physics changed; similarity is not equivalence")
