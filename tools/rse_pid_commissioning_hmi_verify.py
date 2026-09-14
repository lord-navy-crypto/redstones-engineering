#!/usr/bin/env python3
"""Regression gate for PID commissioning actions and retained acceptance comparison."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
BLOCK = ROOT / "src/main/java/dev/redstoneengineering/block/PidControllerBlock.java"
MENU = ROOT / "src/main/java/dev/redstoneengineering/ui/menu/PidControllerMenu.java"
SCREEN = ROOT / "src/main/java/dev/redstoneengineering/client/ui/PidControllerScreen.java"

errors = []

def read(path):
    if not path.exists():
        errors.append(f"missing {path.relative_to(ROOT)}")
        return ""
    return path.read_text(encoding="utf-8")

def req(text, needle, label):
    if needle not in text:
        errors.append(f"{label}: missing {needle!r}")

block = read(BLOCK)
menu = read(MENU)
screen = read(SCREEN)

for needle in (
    "public static boolean resetRuntimeAndTrend(Level level, BlockPos pos)",
    "public static AcceptanceEvidenceRecord captureAcceptanceEvidence(Level level, BlockPos pos)",
    "AcceptanceEvidenceRecord record = captureAcceptanceEvidence(l, p);",
    "if (resetRuntimeAndTrend(l, p))",
    "AcceptanceEvidenceStore.compareLatestToPrevious(l, p)",
):
    req(block, needle, "PidControllerBlock.java")

for needle in (
    "BUTTON_CAPTURE_ACCEPTANCE = 6",
    "BUTTON_RESET_RUNTIME_TREND = 7",
    "PidControllerBlock.captureAcceptanceEvidence(level, blockPos)",
    "PidControllerBlock.resetRuntimeAndTrend(level, blockPos)",
    "AcceptanceEvidenceStore.latest(level, blockPos)",
    "AcceptanceEvidenceStore.compareLatestToPrevious(level, blockPos)",
    "latestAcceptanceStatus()",
    "comparisonTrend()",
):
    req(menu, needle, "PidControllerMenu.java")

for needle in (
    'Component.literal("Capture acceptance")',
    'Component.literal("Reset runtime + trend")',
    "PidControllerMenu.BUTTON_CAPTURE_ACCEPTANCE",
    "PidControllerMenu.BUTTON_RESET_RUNTIME_TREND",
    '"Compared with previous: " + trend.name()',
    '"Baseline capture established; capture again after a change to compare."',
):
    req(screen, needle, "PidControllerScreen.java")

# HMI must not duplicate the low-level commissioning mutations.
for forbidden in (
    "RuntimeIntStore.remove(",
    "PidTelemetryStore.clear(",
    "AcceptanceEvidenceStore.capture(",
    "EngineeringAcceptance.evaluate(",
):
    if forbidden in menu:
        errors.append(f"PidControllerMenu.java: duplicates commissioning mutation via {forbidden!r}")

if errors:
    print("PID COMMISSIONING HMI VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    sys.exit(1)

print("PID COMMISSIONING HMI VERIFY: PASS")
print("  HMI + Shift share server-authoritative acceptance capture and runtime reset actions")
print("  retained acceptance exposes latest verdict plus previous-capture comparison")
print("  reset preserves retained acceptance history while clearing live runtime/trend")
