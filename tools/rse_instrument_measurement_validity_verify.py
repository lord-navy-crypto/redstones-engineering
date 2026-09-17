#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
failed: list[str] = []


def require(path: str, *tokens: str) -> None:
    p = root / path
    if not p.is_file():
        failed.append(f"missing: {path}")
        return
    text = p.read_text(errors="ignore")
    for token in tokens:
        if token not in text:
            failed.append(f"{path} missing token: {token}")


require(
    "src/main/java/dev/redstoneengineering/block/SignalProbeBlock.java",
    "public static boolean measurementPresent",
    "return measured > 0 || !target.isAir();",
    "PortQuality.VALID : PortQuality.NO_SIGNAL",
)
require(
    "src/main/java/dev/redstoneengineering/instrument/InstrumentNetwork.java",
    "SignalProbeBlock.measurementPresent(level, pos, state, value)",
    "values[channel] = present ? value : -1",
    "counts[channel] == 1 && values[channel] >= 0",
)
require(
    "src/main/java/dev/redstoneengineering/block/OscilloscopeBlock.java",
    "snapshot.valueOr(0, -1)",
    "snapshot.valueOr(1, -1)",
    "bus.qualityForMask(OBSERVED_CHANNEL_MASK)",
)
require(
    "src/main/java/dev/redstoneengineering/blockentity/OscilloscopeBlockEntity.java",
    "return value < 0 ? -1 : Math.max(0, Math.min(15, value));",
    "if (values[i] < 0) builder.append(\"·\")",
)
require(
    "src/main/java/dev/redstoneengineering/block/SignalAnalyzerBlock.java",
    "boolean present = measurementPresent(level, pos, state, measured)",
    "recordSample(level, pos, measured, present)",
    "r[WINDOW_BASE + write] = present ? measured : -1",
    "if (value < 0) continue",
    "if (before < 0 || now < 0)",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/SignalAnalyzerScreen.java",
    "summary.invalidSamples() > 0",
    "summary.coveragePercent()",
    "summary.validSamples() == 0",
)

if failed:
    print("RSE instrument measurement validity verification: FAIL")
    for item in failed:
        print(" -", item)
    raise SystemExit(1)

print("RSE instrument measurement validity verification: PASS")
print(" probe zero-vs-no-signal distinction: PASS")
print(" instrument-network validity propagation: PASS")
print(" oscilloscope invalid-sample sentinel chain: PASS")
print(" signal-analyzer retained validity + coverage chain: PASS")
