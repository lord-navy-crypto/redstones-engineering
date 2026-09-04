#!/usr/bin/env python3
"""Static guard for the executable functional-correctness program.

The GameTests themselves prove runtime behavior. This verifier makes sure the
high-value tests stay registered and the common redstone processor keeps the
world-facing 0..15 ownership contract.
"""

from __future__ import annotations

import sys
from pathlib import Path

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(relative: str) -> str:
    path = root / relative
    if not path.is_file():
        errors.append(f"missing: {relative}")
        return ""
    return path.read_text(errors="ignore")


tests = read("src/main/java/dev/redstoneengineering/gametest/RseFunctionalCorrectnessGameTests.java")
registration = read("src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java")
directional = read("src/main/java/dev/redstoneengineering/block/DirectionalSignalBlock.java")
conditioner = read("src/main/java/dev/redstoneengineering/block/SignalConditionerBlock.java")
workflow = read(".github/workflows/build.yml")
matrix = read("docs/FUNCTIONAL_CORRECTNESS_MATRIX.md")

for method in (
    "conditionerChainProducesExpectedWorldOutput",
    "sourceRemovalClearsDownstreamState",
    "conditionerGainSaturatesAtVanillaBoundary",
    "conditionerRejectsSideFeed",
    "conditionerOffsetAndThresholdModesMatchConfiguredSemantics",
    "conditionerDeadbandRetainsAndReleasesOutputDeterministically",
):
    if f"void {method}(GameTestHelper helper)" not in tests:
        errors.append(f"functional GameTest missing: {method}")

if "event.register(RseFunctionalCorrectnessGameTests.class);" not in registration:
    errors.append("functional correctness GameTests are not registered")

for token in (
    "EngineeringSignal.clamp(requestedOutput)",
    "direction == outputSide(state).getOpposite() ? state.getValue(OUTPUT) : 0",
    "side == inputSide(state) || side == outputSide(state)",
):
    if token not in directional:
        errors.append(f"DirectionalSignalBlock lost core directional/boundary contract: {token}")

for token in (
    "case 0 -> SignalMath.gain",
    "case 1 -> SignalMath.offset",
    "case 2 -> Math.min",
    "case 3 -> SignalMath.threshold",
    "case 4 -> Math.abs(input - previousOutput)",
):
    if token not in conditioner:
        errors.append(f"SignalConditioner mode contract missing: {token}")

for token in (
    "A — In-world end-to-end",
    "Signal Conditioner",
    "Redstone → Lapis → Redstone placed-world round trip",
    "unloaded-chunk/no-force-load behavior",
):
    if token not in matrix:
        errors.append(f"functional correctness matrix missing: {token}")

for token in (
    "rse_functional_correctness_verify.py",
    "runGameTestServer",
):
    if token not in workflow:
        errors.append(f"workflow missing functional correctness gate: {token}")

if errors:
    print("RSE functional correctness verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE functional correctness verification: PASS")
print("  six in-world redstone processing correctness tests: registered")
print("  shared 0..15 + FRONT/BACK contract: guarded")
print("  correctness evidence matrix: present")
print("  Minecraft GameTest execution gate: present")
