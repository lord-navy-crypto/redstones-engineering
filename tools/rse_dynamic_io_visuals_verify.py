#!/usr/bin/env python3
"""Verify world-visible routing and synchronized status/config overlays for engineering system blocks."""
from pathlib import Path
import json

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


def verify_cumulative(items: list[dict], name: str, prop: str, conditions: tuple[tuple[str, str], ...]) -> None:
    for condition, model in conditions:
        if not has_part(items, prop, condition, f"redstoneengineering:block/{model}"):
            errors.append(f"{name}: missing {prop}={condition} overlay {model}")


sequence = parts("sequence_controller")
interlock = parts("safety_interlock")
alarm = parts("alarm_processor")
topology = parts("topology_debugger")
fault = parts("fault_injector")
sample = parts("sample_hold")
pwm = parts("pwm_controller")
calibration = parts("calibration_module")

for name, items in (
    ("sequence_controller", sequence),
    ("safety_interlock", interlock),
    ("alarm_processor", alarm),
    ("fault_injector", fault),
    ("sample_hold", sample),
    ("pwm_controller", pwm),
    ("calibration_module", calibration),
):
    verify_route(name, items)

verify_cumulative(sequence, "sequence_controller", "output", (
    ("1|2|3|4", "sequence_step_1"),
    ("2|3|4", "sequence_step_2"),
    ("3|4", "sequence_step_3"),
    ("4", "sequence_step_4"),
))

for output, model in (("0", "interlock_blocked_indicator"), ("15", "interlock_permit_indicator")):
    if not has_part(interlock, "output", output, f"redstoneengineering:block/{model}"):
        errors.append(f"safety_interlock: missing output={output} overlay {model}")

verify_cumulative(alarm, "alarm_processor", "output", (
    ("0", "alarm_clear_indicator"),
    ("5|10|15", "alarm_severity_1"),
    ("10|15", "alarm_severity_2"),
    ("15", "alarm_severity_3"),
))

for mode in range(4):
    model = f"fault_mode_{mode}"
    if not has_part(fault, "mode", str(mode), f"redstoneengineering:block/{model}"):
        errors.append(f"fault_injector: missing mode={mode} overlay {model}")
fault_model = load(MODELS / "fault_injector.json")
fault_textures = fault_model.get("textures", {}) if isinstance(fault_model, dict) else {}
if fault_textures.get("arm") != "redstoneengineering:block/redstone_reference_source":
    errors.append("fault_injector: integrated ARM marker must use the RSE redstone reference texture")
if len(fault_model.get("elements", [])) < 3:
    errors.append("fault_injector: expected base geometry plus integrated ARM cross geometry")

verify_cumulative(sample, "sample_hold", "trigger_mode", (
    ("0|1|2", "config_segment_1"),
    ("1|2", "config_segment_2"),
    ("2", "config_segment_3"),
))
sample_model = load(MODELS / "sample_hold.json")
if sample_model.get("textures", {}).get("control") != "redstoneengineering:block/redstone_reference_source":
    errors.append("sample_hold: TRIGGER/RESET markers must use RSE control texture")
if len(sample_model.get("elements", [])) < 4:
    errors.append("sample_hold: expected base + left TRIGGER + right RESET cross geometry")

verify_cumulative(pwm, "pwm_controller", "period_mode", (
    ("0|1|2|3", "config_segment_1"),
    ("1|2|3", "config_segment_2"),
    ("2|3", "config_segment_3"),
    ("3", "config_segment_4"),
))
if not has_part(pwm, "invert", "true", "redstoneengineering:block/config_invert_indicator"):
    errors.append("pwm_controller: missing invert=true world indicator")
pwm_model = load(MODELS / "pwm_controller.json")
if pwm_model.get("textures", {}).get("control") != "redstoneengineering:block/redstone_reference_source":
    errors.append("pwm_controller: INHIBIT marker must use RSE control texture")
if len(pwm_model.get("elements", [])) < 2:
    errors.append("pwm_controller: expected base + integrated INHIBIT geometry")

verify_cumulative(calibration, "calibration_module", "profile", (
    ("0|1|2|3|4", "config_segment_1"),
    ("1|2|3|4", "config_segment_2"),
    ("2|3|4", "config_segment_3"),
    ("3|4", "config_segment_4"),
    ("4", "config_segment_5"),
))
calibration_model = load(MODELS / "calibration_module.json")
if calibration_model.get("textures", {}).get("reference") != "redstoneengineering:block/redstone_reference_source":
    errors.append("calibration_module: REFERENCE marker must use RSE reference texture")
if len(calibration_model.get("elements", [])) < 3:
    errors.append("calibration_module: expected base + integrated REFERENCE geometry")

# Topology debugger scans the face opposite its alarm-output facing.
tx_rotations = {"north": 0, "east": 90, "south": 180, "west": 270}
scan_rotations = {"north": 180, "east": 270, "south": 0, "west": 90}
for direction, rotation in tx_rotations.items():
    if not has_part(topology, "facing", direction, "redstoneengineering:block/engineering_tx_marker", rotation):
        errors.append(f"topology_debugger: missing alarm TX marker for facing={direction} y={rotation}")
for direction, rotation in scan_rotations.items():
    if not has_part(topology, "facing", direction, "redstoneengineering:block/engineering_scan_marker", rotation):
        errors.append(f"topology_debugger: scan marker must oppose facing={direction}; expected y={rotation}")
for output, model in (("0", "topology_nominal_indicator"), ("15", "topology_issue_indicator")):
    if not has_part(topology, "output", output, f"redstoneengineering:block/{model}"):
        errors.append(f"topology_debugger: missing output={output} overlay {model}")

required_models = (
    "engineering_rx_marker",
    "engineering_tx_marker",
    "engineering_scan_marker",
    "sequence_step_1", "sequence_step_2", "sequence_step_3", "sequence_step_4",
    "interlock_blocked_indicator", "interlock_permit_indicator",
    "alarm_clear_indicator", "alarm_severity_1", "alarm_severity_2", "alarm_severity_3",
    "fault_mode_0", "fault_mode_1", "fault_mode_2", "fault_mode_3",
    "config_segment_1", "config_segment_2", "config_segment_3", "config_segment_4", "config_segment_5",
    "config_invert_indicator",
    "topology_nominal_indicator", "topology_issue_indicator",
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
print(" alarm processor: world-visible RX/TX + CLEAR/severity 1..3 indicators")
print(" fault injector: world-visible RX/TX + integrated ARM + configured mode 0..3 indicators")
print(" sample & hold: RX/TX + integrated TRIGGER/RESET + trigger-mode segments")
print(" PWM controller: RX/TX + integrated INHIBIT + period segments + invert marker")
print(" calibration module: RX/TX + integrated REFERENCE + profile segments")
print(" topology debugger: world-visible alarm TX + opposite SCAN target + NOMINAL/ISSUE indicators")
print(" visuals consume synchronized BlockState only; no second runtime state")
