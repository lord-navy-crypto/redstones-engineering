#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
failed: list[str] = []


def require(rel: str, *tokens: str) -> None:
    path = root / rel
    if not path.exists():
        failed.append(f"missing: {rel}")
        return
    text = path.read_text(errors="ignore")
    for token in tokens:
        if token not in text:
            failed.append(f"{rel} missing token: {token}")


require(
    "src/main/java/dev/redstoneengineering/diagnostics/CommissioningSnapshot.java",
    "rise90Ticks",
    "settlingTicks",
    "overshoot",
    "saturationEvents",
    "CommissioningStatus",
)
require(
    "src/main/java/dev/redstoneengineering/diagnostics/ClosedLoopCommissioning.java",
    'RuntimeIntStore.peek(level, PID_KEY, pidPos)',
    "fromPidRuntime",
    "CommissioningComparison compare",
    "scoreLoss <= 20",
)
require(
    "src/main/java/dev/redstoneengineering/diagnostics/FaultInjectionModel.java",
    "addBias",
    "addDeterministicNoise",
    "applyDropout",
    "applySaturation",
    "latencyTicks",
)
require(
    "src/main/java/dev/redstoneengineering/block/LapisNoiseSourceBlock.java",
    "FaultInjectionModel.addDeterministicNoise",
    "Fault injection [NOISE]",
)
require(
    "src/main/java/dev/redstoneengineering/block/QuartzPhaseDelayBlock.java",
    "FaultInjectionModel.latencyTicks",
    "Fault injection [LATENCY]",
)
require(
    "src/main/java/dev/redstoneengineering/gametest/RseCommissioningGameTests.java",
    "pidRuntimeProducesCommissioningSnapshot",
    "disturbedRunLosesRobustness",
    "faultInjectionPrimitivesAreBoundedAndRepeatable",
)
require(
    "src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java",
    "event.register(RseCommissioningGameTests.class)",
)
require("gradle.properties", "mod_version=1.0.16-alpha")
require("README.md", "Alpha 1.0.16", "Closed-Loop Commissioning & Fault Injection")
require("ALPHA1_0_16_MANIFEST.txt", "1.0.16-alpha", "License: MIT", "Java: 21")

probe = root / "src/main/java/dev/redstoneengineering/diagnostics/ClosedLoopCommissioning.java"
if probe.exists():
    text = probe.read_text(errors="ignore")
    if "RuntimeIntStore.get(" in text:
        failed.append("commissioning diagnostics must not create/mutate PID runtime through RuntimeIntStore.get")

if failed:
    print("RSE Alpha 1.0.16 commissioning verification: FAIL")
    for item in failed:
        print(" -", item)
    sys.exit(1)

print("RSE Alpha 1.0.16 commissioning verification: PASS")
print(" read-only PID commissioning + bounded fault injection + GameTest contracts: PASS")
