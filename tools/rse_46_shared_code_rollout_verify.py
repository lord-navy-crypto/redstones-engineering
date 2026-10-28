#!/usr/bin/env python3
"""Source-level 46-candidate shared-implementation matrix and contract gate.

A shared helper is not evidence of physics equivalence or runtime acceptance.
Do not merge registry IDs, media, topology, state or world-save contracts.
"""
from pathlib import Path
import csv
import json
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
out = root / ".rse-audit"
data = json.loads((out / "rse-130-block-slimming-inventory.json").read_text(encoding="utf-8"))
blocks = data["blocks"]
groups = data["candidate_groups"]
candidates = [item for item in blocks if item["review_group"] != "none"]
errors = []
if len(blocks) != 130 or len(candidates) != 46 or len(groups) != 15:
    errors.append(f"registry/groups drift: {len(blocks)} blocks, {len(candidates)} candidates, {len(groups)} groups")

src = root / "src/main/java/dev/redstoneengineering/block"
reads = {}
for filename in ("OpticalSeriesSupport", "PneumaticValveSupport", "ExciterDriveSupport"):
    path = src / (filename + ".java")
    reads[filename] = path.read_text(encoding="utf-8") if path.is_file() else ""
    if not reads[filename]:
        errors.append(f"shared helper missing: {filename}")
forbidden_in_helper = (
    "setBlock(", "setValue(", "RuntimeIntStore.get(", "getBlockEntity(",
)
for file, source in reads.items():
    for token in forbidden_in_helper:
        if token in source:
            errors.append(f"unexpected world mutation or state injection {file}: {token}")
if "DomainNetwork.driveOptical(level, pos.relative(output), pos, 0, 0, false)" not in reads["OpticalSeriesSupport"]:
    errors.append("optical old-route invalidation lost")
if "PneumaticNetwork.recomputeAround(server, pos)" not in reads["PneumaticValveSupport"]:
    errors.append("pneumatic topology recompute lost")
if "PortQuality.NO_SIGNAL" not in reads["ExciterDriveSupport"]:
    errors.append("exciter zero-amplitude quality semantics lost")

families = {
    "optical_series": ("OpticalSeriesSupport", {
        "optical_attenuator": ("OpticalAttenuatorBlock", "EngineeringMath.opticalAfterLoss("),
        "optical_channel_filter": ("OpticalChannelFilterBlock", "input.channel() == target"),
    }),
    "pneumatic_valves": ("PneumaticValveSupport", {
        "pneumatic_valve": ("PneumaticValveBlock", "PortDirection.BIDIRECTIONAL"),
        "pneumatic_check_valve": ("PneumaticCheckValveBlock", "PortDirection.INPUT"),
        "pneumatic_proportional_valve": ("PneumaticProportionalValveBlock", "MechatronicsVisualBlockEntity.push("),
        "pneumatic_relief_valve": ("PneumaticReliefValveBlock", "recordVent("),
        "pressure_regulator": ("PressureRegulatorBlock", "PneumaticNetwork.recompute(server, pos)"),
    }),
    "vibration_excitation": ("ExciterDriveSupport", {
        "mechanical_exciter": ("MechanicalExciterBlock", "VibrationNetwork.propagate("),
        "hydroacoustic_exciter": ("HydroacousticExciterBlock", "HydroacousticNetwork.propagate("),
    }),
}
wired = {}
for family, (helper, mapping) in families.items():
    for block_id, (klass, distinct) in mapping.items():
        file = src / (klass + ".java")
        if not file.is_file():
            errors.append(f"{block_id}: class missing")
            continue
        source = file.read_text(encoding="utf-8")
        if helper + "." not in source:
            errors.append(f"{block_id}: no reuse of {helper}")
        if distinct not in source:
            errors.append(f"{block_id}: distinct physics/operation missing: {distinct}")
        if "engineeringSnapshot(" not in source:
            errors.append(f"{block_id}: formal port evidence missing")
        wired[block_id] = helper
if len(wired) != 9:
    errors.append(f"expected 9 first-wave helper users, got {len(wired)}")
if not set(wired).issubset({r["id"] for r in candidates}):
    errors.append("an implemented refactor is not a registered 46-candidate")

def recommendation(family):
    return {
        "quartz_clock": "Compare clock transitions and jitter metrics; retain lab-specific drift data.",
        "filtering": "Extract only identical pure transfer equations; keep domain and precision distinct.",
        "range_measurement": "Compare scan geometry/edge cases before sharing scanner logic.",
        "sampling_hold": "Review edge detection and stale-sample handling for safe reuse.",
        "amethyst_frequency": "Share frequency/quality calculations; never collapse source/filter/resonator.",
        "metering": "Reuse measurement-quality presentation; do not unify sensor calibration laws.",
        "optical_transport": "Share scoped optical quality helpers; preserve fiber vs line-of-sight.",
        "medium_junctions": "Reuse declared-face formatting, never auto-bridge domains.",
        "failure_diagnostics": "Share event record snapshots but preserve fail-safe triggers.",
        "digital_transport": "Reuse encode/decode bounds; keep driver, receiver and cable separate.",
        "instrument_cables": "Share topology scan and preserve shielding exposure semantics.",
        "optical_sensor_conversion": "Only share common observation; preserve quantization and signal medium.",
    }.get(family, "Candidate reviewed; no proved functional equivalence. Retain block identity.")

rows = []
for r in candidates:
    family = r["review_group"]
    is_done = r["id"] in wired
    rows.append({
        "registry_id": r["id"], "source_class": r["class"], "family": family,
        "public_block_id": "PRESERVE", "physics_transfer": "PRESERVE",
        "port_domain_and_direction": "PRESERVE", "old_world_save": "PRESERVE",
        "review": "SHARED_HELPER_WIRED_SOURCE_CHECKED" if is_done else "NOT_REFACTORED",
        "shared_helper": wired.get(r["id"], ""),
        "next_action": ("Run runtime GameTest, old-save and multiplayer checks."
                        if is_done else recommendation(family)),
        "runtime_acceptance": "NOT_TESTED",
        "behavioral_equivalence": "NOT_PROVEN",
        "save_migration": "NOT_REQUIRED_NO_ID_CHANGE",
    })
rows.sort(key=lambda x: (x["family"], x["registry_id"]))
with (out / "rse-46-shared-code-rollout.csv").open("w", newline="", encoding="utf-8") as f:
    writer = csv.DictWriter(f, fieldnames=list(rows[0]))
    writer.writeheader()
    writer.writerows(rows)
summary = {
    "review_candidates": len(rows),
    "groups": len(groups),
    "first_wave_wired": len(wired),
    "queued": len(rows)-len(wired),
    "registry_changed": False,
    "runtime_acceptance": False,
    "errors": errors,
}
(out / "rse-46-shared-code-rollout.json").write_text(json.dumps(
    {"summary": summary, "blocks": rows}, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")
if errors:
    print("RSE 46 SHARED CODE ROLLOUT: FAIL")
    for err in errors:
        print(" -", err)
    sys.exit(1)
print("RSE 46 SHARED CODE ROLLOUT: PASS")
print(f" {len(rows)} candidates / {len(groups)} families; {len(wired)} shared-helper users, {len(rows)-len(wired)} queued")
print(" no deleted IDs, no physics law merges; runtime equivalence not yet verified")
