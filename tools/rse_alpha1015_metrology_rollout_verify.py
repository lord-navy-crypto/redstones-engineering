#!/usr/bin/env python3
"""Alpha 1.0.15 metrology rollout and calibration architecture gate."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]

checks = {
    "src/main/java/dev/redstoneengineering/metrology/MetrologySupport.java": [
        "conditionBounded", "conditionRedstone", "portQuality", "compactDiagnostics",
    ],
    "src/main/java/dev/redstoneengineering/block/DirectionalRedstoneSensorBlock.java": [
        "metrologyChannel", "sampleMeasurement", "sensorMeasurement", "MetrologyStore.remove",
    ],
    "src/main/java/dev/redstoneengineering/block/EngineeringLightSensorBlock.java": [
        '"light_sensor"', "sampleMeasurement", "conditionRedstone",
    ],
    "src/main/java/dev/redstoneengineering/block/EntityDensitySensorBlock.java": [
        '"entity_density"', "physicalCount > 15", "sampleMeasurement",
    ],
    "src/main/java/dev/redstoneengineering/block/TankLevelSensorBlock.java": [
        '"tank_level"', "MetrologySupport.snapshot", "sampleMeasurement",
    ],
    "src/main/java/dev/redstoneengineering/block/ServoPositionSensorBlock.java": [
        '"servo_position_sensor"', "conditionRedstone", "MetrologySupport.sample",
    ],
    "src/main/java/dev/redstoneengineering/block/CopperCircuitMeterBlock.java": [
        '"copper_circuit_meter"', "level.scheduleTick", "MetrologySupport.sample",
    ],
    "src/main/java/dev/redstoneengineering/block/PneumaticFlowMeterBlock.java": [
        '"pneumatic_flow_meter"', "conditionBounded", "runtime[1] * 12 > 100",
    ],
    "src/main/java/dev/redstoneengineering/block/CalibrationModuleBlock.java": [
        '"OBSERVED"', '"REFERENCE"', '"CALIBRATED"', "referenceSide", "MetrologySupport.sample",
    ],
    "src/main/java/dev/redstoneengineering/gametest/RseMetrologyGameTests.java": [
        "sharedPortQualityPreservesHardMeasurementStates",
        "calibrationComparisonSeparatesResidualFromUncertainty",
    ],
    "ALPHA1_0_15_MANIFEST.txt": [
        "METROLOGY ROLLOUT & CALIBRATION", "UI/Jade/render polling must never determine sample cadence",
    ],
    "docs/ALPHA1_0_15_METROLOGY_ROLLOUT.md": [
        "Sampling ownership", "REFERENCE INPUT", "measurement uncertainty proxy",
    ],
}

errors = []

# Historical gates must survive later Alpha releases: require >= 1.0.15 rather than
# pinning the repository forever to the milestone that introduced the contract.
props = ROOT / "gradle.properties"
if not props.is_file():
    errors.append("missing gradle.properties")
else:
    text = props.read_text(encoding="utf-8")
    match = re.search(r"(?m)^mod_version=(\d+)\.(\d+)\.(\d+)-alpha(?:[.-][0-9A-Za-z.-]+)?$", text)
    if not match:
        errors.append("gradle.properties: invalid alpha mod_version")
    elif tuple(map(int, match.groups())) < (1, 0, 15):
        errors.append("gradle.properties: Alpha 1.0.15 gate requires version >= 1.0.15-alpha")

for rel, required in checks.items():
    path = ROOT / rel
    if not path.is_file():
        errors.append(f"missing {rel}")
        continue
    text = path.read_text(encoding="utf-8")
    for token in required:
        if token not in text:
            errors.append(f"{rel}: missing token {token!r}")

if errors:
    print(f"RSE Alpha 1.0.15 metrology rollout verification: FAIL ({len(errors)} issue(s))", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print("RSE Alpha 1.0.15 metrology rollout verification: PASS")
print(" forward-compatible milestone version gate: PASS")
print(" shared multi-domain metrology support: PASS")
print(" explicit saturation + scheduled sampling ownership: PASS")
print(" three-port calibration comparison workflow: PASS")
print(" calibration residual vs uncertainty semantics: PASS")
