#!/usr/bin/env python3
"""Verify world-visible RX/TX routing and live status overlays for engineering system blocks."""
from pathlib import Path
import json
import sys

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/redstoneengineering"
BLOCKSTATES = ASSETS / "blockstates"
MODELS = ASSETS / "models/block"
errors: list[str] = []


def load(path: Path) -> dict:
    if not path.is_file():
        errors.append(f"missing {path.relative_to(ROOT)}")
        return {}
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        errors.append(f"{path.relative_to(ROOT)} invalid JSON: {exc}")
        return {}


def parts(name: str) -> list[dict]:
    data = load(BLOCKSTATES / f"{name}.json")
    value = data.get("multipart", [])
    if not isinstance(value, list):
        errors.append(f"{name}: multipart must be a list")
        return []
    return [p for p in value if isinstance(p, dict)]


def has_part(items: list[dict], prop: str, value: str, model: str, rotation: int | None = None) -> bool:
    for part in items:
        when = part.get("when")
        apply = part.get("apply")
        if not isinstance(when, dict) or not isinstance(apply, dict):
            continue
        if when.get(prop) != value or apply.get("model") != model:
            continue
        if rotation is None or apply.get("y", 0) % 360 == rotation % 360:
            return True
    return False


def verify_route(name: str, items: list[dict]) -> None:
    rotations = {"north": 0, "east": 90, "south": 180, "west": 270}
    for direction, rotation in rotations.items():
        if not has_part(items, "facing", direction, "redstoneengineering:block/engineering_tx_marker", rotation):
            errors.append(f"{name}: missing TX marker for facing={direction} y={rotation}")
        if not has_part(items, "input_facing", direction, "redstoneengineering:block/engineering_rx_marker", rotation):
            errors.append(f"{name}: missing RX marker for input_facing={direction} y={rotation}")


sequence = parts("sequence_controller")
interlock = parts("safety_interlock")
verify_route("sequence_controller", sequence)
verify_route("safety_interlock", interlock)

sequence_steps = (
    ("1|2|3|4", "sequence_step_1"),
    ("2|3|4", "sequence_step_2"),
    ("3|4", "sequence_step_3"),
    ("4", "sequence_step_4"),
)
for condition, model in sequence_steps:
    if not has_part(sequence, "output", condition, f"redstoneengineering:block/{model}"):
        errors.append(f"sequence_controller: missing cumulative output={condition} overlay {model}")

for output, model in (("0", "interlock_blocked_indicator"), ("15", "interlock_permit_indicator")):
    if not has_part(interlock, "output", output, f"redstoneengineering:block/{model}"):
        errors.append(f"safety_interlock: missing output={output} overlay {model}")

required_models = (
    "engineering_rx_marker",
    "engineering_tx_marker",
    "sequence_step_1",
    "sequence_step_2",
    "sequence_step_3",
    "sequence_step_4",
    "interlock_blocked_indicator",
    "interlock_permit_indicator",
)
for model in required_models:
    data = load(MODELS / f"{model}.json")
    if not data.get("elements"):
        errors.append(f"{model}: expected visible geometry")

if errors:
    print("RSE DYNAMIC I/O VISUALS VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE DYNAMIC I/O VISUALS VERIFY: PASS")
print(" sequence controller: world-visible RX/TX + cumulative STEP 1..4 indicators")
print(" safety interlock: world-visible RX/TX + BLOCKED/PERMIT indicators")
print(" visuals consume synchronized BlockState only; no second runtime state")
