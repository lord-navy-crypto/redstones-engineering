#!/usr/bin/env python3
from pathlib import Path
import json
import sys

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/redstoneengineering"
errors: list[str] = []


def read_json(rel: str):
    path = ASSETS / rel
    if not path.is_file():
        errors.append(f"missing {rel}")
        return {}
    try:
        return json.loads(path.read_text())
    except json.JSONDecodeError as exc:
        errors.append(f"{rel}: invalid JSON: {exc}")
        return {}


models = [f"models/block/live_output_segment_{i}.json" for i in range(1, 5)]
for rel in models:
    model = read_json(rel)
    elements = model.get("elements", []) if isinstance(model, dict) else []
    if len(elements) != 1:
        errors.append(f"{rel}: expected one visible overlay element")
    if "redstoneengineering:block/" not in json.dumps(model):
        errors.append(f"{rel}: expected RSE-owned texture")

thresholds = {
    1: set(range(1, 16)),
    2: set(range(5, 16)),
    3: set(range(9, 16)),
    4: set(range(13, 16)),
}

for block_id in ("sample_hold", "pwm_controller", "calibration_module"):
    state = read_json(f"blockstates/{block_id}.json")
    parts = state.get("multipart", []) if isinstance(state, dict) else []
    for segment, expected in thresholds.items():
        model_name = f"redstoneengineering:block/live_output_segment_{segment}"
        matches = [p for p in parts if p.get("apply", {}).get("model") == model_name]
        if len(matches) != 1:
            errors.append(f"{block_id}: expected exactly one {model_name} rule")
            continue
        raw = matches[0].get("when", {}).get("output", "")
        try:
            actual = {int(v) for v in raw.split("|") if v}
        except ValueError:
            actual = set()
        if actual != expected:
            errors.append(f"{block_id}: segment {segment} output threshold mismatch: {sorted(actual)}")

if errors:
    print("RSE live output bars verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE live output bars verification: PASS")
print(" sample hold: 4-step held-output bar from authoritative output BlockState")
print(" calibration: 4-step calibrated-output bar from authoritative output BlockState")
print(" PWM: LOW=dark / HIGH=all four segments via authoritative output BlockState")
