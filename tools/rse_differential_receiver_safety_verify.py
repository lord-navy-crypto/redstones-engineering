#!/usr/bin/env python3
"""Differential receiver fail-closed watchdog and zero/invalid evidence gate.

Checks source semantics and runtime GameTest registration; does not execute GameTest.
"""
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors = []
def read(rel):
    p = root / rel
    if not p.is_file():
        errors.append(f"missing {rel}")
        return ""
    return p.read_text(encoding="utf-8")
base = "src/main/java/dev/redstoneengineering/"
rx = read(base+"block/DifferentialReceiverBlock.java")
helper = read(base+"block/DigitalEndpointEvidenceSupport.java")
tests = read(base+"gametest/RseDifferentialReceiverSafetyGameTests.java")
registry = read(base+"gametest/RseGameTestRegistration.java")
workflow = read(".github/workflows/build.yml")
def require(condition, message):
    if not condition: errors.append(message)
for token in (
    "DigitalEndpointEvidenceSupport.outputQuality(",
    "DigitalEndpointEvidenceSupport.valueWhenValid(",
    "quality == PortQuality.VALID ? state.getValue(OUTPUT) : 0",
    "PortQuality.STALE",
    "DifferentialNetwork.quality(level, input)",
    "super.tick(state, level, pos, random);",
    "level.scheduleTick(pos, this, 16);",
):
    require(token in rx, f"RX must guard authoritative bit/quality or watchdog: {token}")
for token in ("if (output.ageTicks() < 0 || !output.valid())", "return upstream;",
              "envelope.value() & mask : 0;"):
    require(token in helper, f"pure shared byte/bit semantics lost: {token}")
for token in ("void differentialReceiverWithdrawsOrphanedHigh(",
              "void byteAndBitQualityNeverPromoteInvalidEnvelope(",
              "helper.runAfterDelay(18",
              "PortQuality.TOPOLOGY_ERROR",
              "validZero"):
    require(token in tests, f"runtime witness missing: {token}")
require(tests.count("@GameTest(") == 2, "must have two receiver safety witnesses")
require("event.register(RseDifferentialReceiverSafetyGameTests.class);" in registry,
        "receiver tests are not registered")
require("tools/rse_differential_receiver_safety_verify.py" in workflow,
        "CI must execute differential receiver contract")
if errors:
    print("RSE DIFFERENTIAL RECEIVER SAFETY: FAIL")
    for e in errors: print(" -", e)
    raise SystemExit(1)
print("RSE DIFFERENTIAL RECEIVER SAFETY: PASS")
print(" FAIL-CLOSED port readout, valid-zero semantics, recurring source watchdog: SOURCE CHECKED")
print(" Two in-world GameTests registered (runtime not executed)")
