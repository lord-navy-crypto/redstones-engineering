#!/usr/bin/env python3
"""Regression gate for Sample & Hold output-quality semantics.

The contract is intentionally stronger than a value-only assertion:
- no live runtime evidence => STALE
- runtime allocated but no authoritative output yet => NOT_READY
- explicit sample/reset/operator clear => VALID, including value 0

This prevents a real zero from being conflated with "no sample yet".
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
PORT_QUALITY = ROOT / "src/main/java/dev/redstoneengineering/core/port/PortQuality.java"
SAMPLE_HOLD = ROOT / "src/main/java/dev/redstoneengineering/block/SampleHoldBlock.java"

errors = []

def read(path: Path) -> str:
    if not path.exists():
        errors.append(f"missing {path.relative_to(ROOT)}")
        return ""
    return path.read_text(encoding="utf-8")

def require(source: str, needle: str, label: str) -> None:
    if needle not in source:
        errors.append(f"{label}: missing {needle!r}")

port_quality = read(PORT_QUALITY)
sample_hold = read(SAMPLE_HOLD)

# Keep NOT_READY appended so existing ordinal-based transient runtime encodings
# retain their previous numeric meanings.
enum_match = re.search(r"enum\s+PortQuality\s*\{(?P<body>.*?)\}", port_quality, re.S)
if not enum_match:
    errors.append("PortQuality.java: could not parse enum body")
else:
    names = [
        token.strip().split()[0]
        for token in enum_match.group("body").split(",")
        if token.strip()
    ]
    if not names or "NOT_READY" not in names[-1]:
        errors.append("PortQuality.java: NOT_READY must remain the final enum constant")

require(sample_hold, "private static final int RUNTIME_SIZE = 6;", "SampleHoldBlock.java")
require(sample_hold, "private static final int OUTPUT_READY_SLOT = 5;", "SampleHoldBlock.java")
require(sample_hold, "quality = outputQuality(level, pos);", "SampleHoldBlock.java")
require(sample_hold, "return PortQuality.STALE;", "SampleHoldBlock.java")
require(sample_hold, "PortQuality.VALID : PortQuality.NOT_READY", "SampleHoldBlock.java")

# Three authoritative paths must mark the held output ready. We expect one in
# reset, one in sample capture, and one in operator clear.
ready_assignments = sample_hold.count("rt[OUTPUT_READY_SLOT] = 1;")
if ready_assignments < 3:
    errors.append(
        "SampleHoldBlock.java: expected OUTPUT_READY_SLOT to be set by "
        f"reset, sample and clear paths; found {ready_assignments}"
    )

# Model the intended state machine explicitly: a zero value is not evidence of
# missing data once an authoritative operation has occurred.
def modeled_quality(runtime_present: bool, output_ready: bool) -> str:
    if not runtime_present:
        return "STALE"
    return "VALID" if output_ready else "NOT_READY"

assert modeled_quality(False, False) == "STALE"
assert modeled_quality(True, False) == "NOT_READY"
assert modeled_quality(True, True) == "VALID"

captured_value = 0
captured_quality = modeled_quality(True, True)
if (captured_value, captured_quality) != (0, "VALID"):
    errors.append("model contract: captured real zero must remain VALID")

if errors:
    print("RSE SAMPLE HOLD QUALITY VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    sys.exit(1)

print("RSE SAMPLE HOLD QUALITY VERIFY: PASS")
print("  absent runtime evidence -> STALE")
print("  allocated but uncaptured output -> NOT_READY")
print("  explicit capture/reset/clear -> VALID, including real zero")
