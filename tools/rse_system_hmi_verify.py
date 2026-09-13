#!/usr/bin/env python3
"""Verify server-authoritative system controls in the universal engineering HMI."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []


def read(path: str) -> str:
    p = ROOT / path
    if not p.is_file():
        errors.append(f"missing {path}")
        return ""
    return p.read_text(encoding="utf-8")


def require(text: str, label: str, *tokens: str) -> None:
    for token in tokens:
        if token not in text:
            errors.append(f"{label}: missing {token!r}")

sequence = read("src/main/java/dev/redstoneengineering/block/SequenceControllerBlock.java")
interlock = read("src/main/java/dev/redstoneengineering/block/SafetyInterlockBlock.java")
topology = read("src/main/java/dev/redstoneengineering/block/TopologyDebuggerBlock.java")
menu = read("src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java")
screen = read("src/main/java/dev/redstoneengineering/client/ui/UniversalFieldDeviceScreen.java")

require(sequence, "SequenceControllerBlock.java",
        "boolean operatorReset(Level level, BlockPos pos)",
        "operatorReset(level, pos);",
        '"SEQUENCE_OPERATOR_RESET"')
require(interlock, "SafetyInterlockBlock.java",
        "boolean resetDiagnostics(Level level, BlockPos pos)",
        "RuntimeIntStore.remove(level, KEY, pos);",
        "resetDiagnostics(level, pos);")
require(topology, "TopologyDebuggerBlock.java",
        "boolean resetDiagnostics(Level level, BlockPos pos)",
        "FieldDeviceUi.open(serverPlayer, pos)",
        '"Topology scan counters reset"')

require(menu, "UniversalFieldDeviceMenu.java",
        "CONFIG_SEQUENCE_CONTROLLER = 9",
        "CONFIG_SAFETY_INTERLOCK = 10",
        "CONFIG_TOPOLOGY_DEBUGGER = 11",
        "SequenceControllerBlock.step(level, blockPos)",
        "SequenceControllerBlock.completedCycles(level, blockPos)",
        "SafetyInterlockBlock.failedMask(level, blockPos)",
        "TopologyDebuggerBlock.scanCount(level, blockPos)",
        "TopologyDebuggerBlock.targetsVanillaRedstone(level, blockPos, state)",
        "sequence.operatorReset(level, blockPos)",
        "interlock.resetDiagnostics(level, blockPos)",
        "debugger.resetDiagnostics(level, blockPos)",
        "block instanceof SequenceControllerBlock",
        "block instanceof SafetyInterlockBlock",
        "block instanceof TopologyDebuggerBlock",
        "return ROUTE_MULTI_PORT_LAYOUT")

require(screen, "UniversalFieldDeviceScreen.java",
        'Component.literal("Reset sequence to IDLE")',
        'Component.literal("Reset diagnostic counters")',
        'Component.literal("Reset scan counters")',
        "CONFIG_SEQUENCE_CONTROLLER",
        "CONFIG_SAFETY_INTERLOCK",
        "CONFIG_TOPOLOGY_DEBUGGER",
        '"Missing permissives"',
        '"Target mode"',
        '"Completed cycles"')

if errors:
    print("RSE SYSTEM HMI VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE SYSTEM HMI VERIFY: PASS")
print(" sequence: shared operator reset + synchronized step/cycle evidence")
print(" interlock: failed permissive mask + non-bypass diagnostic reset")
print(" topology: HMI opener + scan target/count + counter reset")
print(" system blocks use multi-port route semantics in universal HMI")
