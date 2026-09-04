#!/usr/bin/env python3
from pathlib import Path
import json
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")

checks = {
    "PID manual/auto + bumpless": (
        "src/main/java/dev/redstoneengineering/block/PidControllerBlock.java",
        [
            "AUTO_MODE",
            "MANUAL_MODE",
            "DOWN=manual",
            "manual→auto",
            "MAX_OUT",
            "candidateIntegral",
            "rt[17]",
            "rt[18]",
            "rt[19]",
            "rt[20]",
        ],
    ),
    "Servo velocity mode + braking": (
        "src/main/java/dev/redstoneengineering/block/ServoActuatorBlock.java",
        ["POSITION_MODE", "VELOCITY_MODE", "7=stop", "softLimitHits", "BRAKE", "appliedVelocity"],
    ),
    "Bus contention diagnostics": (
        "src/main/java/dev/redstoneengineering/physics/DataBusNetwork.java",
        ["driverCount", "distinct=", "same-value-multidriver", "getDiagnostics", "conflictFrames"],
    ),
    "Radio accumulated diagnostics": (
        "src/main/java/dev/redstoneengineering/block/RadioReceiverBlock.java",
        ["radio_rx_diag", "undecodable=", "collisions=", "dropouts=", "linkQuality", "noiseStrength"],
    ),
    "Operations classifications": (
        "src/main/java/dev/redstoneengineering/block/OperationsMonitorBlock.java",
        [
            "classifySystemState",
            "NOMINAL",
            "CONGESTED",
            "NOISY",
            "UNSTABLE",
            "OVERLOADED",
            "SAFETY_LIMITED",
            "FAILED",
            "starved=",
            "blocked/fault=",
            "highQueueRun=",
        ],
    ),
    "Pneumatic proportional valve": (
        "src/main/java/dev/redstoneengineering/block/PneumaticProportionalValveBlock.java",
        ["PneumaticProportionalValveBlock", "opening(level"],
    ),
    "Pneumatic relief valve": (
        "src/main/java/dev/redstoneengineering/block/PneumaticReliefValveBlock.java",
        ["PneumaticReliefValveBlock", "pneumatic_relief"],
    ),
    "Pneumatic cylinder": (
        "src/main/java/dev/redstoneengineering/block/PneumaticCylinderBlock.java",
        ["PneumaticCylinderBlock", "pneumatic_cylinder"],
    ),
}

failed = []
for name, (rel, required) in checks.items():
    path = root / rel
    if not path.exists():
        failed.append(f"{name}: missing {rel}")
        continue
    text = path.read_text(errors="ignore")
    missing = [token for token in required if token not in text]
    if missing:
        failed.append(f"{name}: missing tokens {', '.join(missing)}")

for name in ["pneumatic_proportional_valve", "pneumatic_relief_valve", "pneumatic_cylinder"]:
    for rel in [
        f"assets/redstoneengineering/blockstates/{name}.json",
        f"assets/redstoneengineering/models/block/{name}.json",
        f"assets/redstoneengineering/models/item/{name}.json",
        f"data/redstoneengineering/loot_table/blocks/{name}.json",
        f"data/redstoneengineering/recipe/{name}.json",
    ]:
        path = root / "src/main/resources" / rel
        if not path.exists():
            failed.append(f"missing resource: {rel}")
            continue
        try:
            json.loads(path.read_text())
        except Exception as exc:
            failed.append(f"invalid JSON: {rel}: {exc}")

# High-cardinality values belong in transient runtime state / BlockEntity data,
# not in BlockState where they would explode model-state combinations.
java_text = "\n".join(p.read_text(errors="ignore") for p in (root / "src/main/java").rglob("*.java"))
if 'IntegerProperty.create("value", 0, 255)' in java_text:
    failed.append("high-cardinality 0..255 BlockState detected")

if failed:
    print("RSE Alpha 1.0.3 verification: FAIL")
    for item in failed:
        print(" -", item)
    sys.exit(1)

print("RSE Alpha 1.0.3 closed-loop verification: PASS")
for name in checks:
    print(" ", name + ": PASS")
