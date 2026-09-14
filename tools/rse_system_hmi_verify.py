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


def forbid(text: str, label: str, *tokens: str) -> None:
    for token in tokens:
        if token in text:
            errors.append(f"{label}: forbidden {token!r}")

sequence = read("src/main/java/dev/redstoneengineering/block/SequenceControllerBlock.java")
interlock = read("src/main/java/dev/redstoneengineering/block/SafetyInterlockBlock.java")
topology = read("src/main/java/dev/redstoneengineering/block/TopologyDebuggerBlock.java")
fault = read("src/main/java/dev/redstoneengineering/block/FaultInjectorBlock.java")
menu = read("src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java")
screen = read("src/main/java/dev/redstoneengineering/client/ui/UniversalFieldDeviceScreen.java")

require(sequence, "SequenceControllerBlock.java",
        "boolean operatorReset(Level level, BlockPos pos)",
        "operatorReset(level, pos);",
        '"SEQUENCE_OPERATOR_RESET"')
require(interlock, "SafetyInterlockBlock.java",
        "boolean resetDiagnostics(Level level, BlockPos pos)",
        "RuntimeIntStore.remove(level, KEY, pos);",
        "resetDiagnostics(level, pos);",
        "return runtime == null || runtime.length < RUNTIME_SIZE ? -1 : runtime[0];")
require(topology, "TopologyDebuggerBlock.java",
        "boolean resetDiagnostics(Level level, BlockPos pos)",
        "FieldDeviceUi.open(serverPlayer, pos)",
        '"Topology scan counters reset"')
require(fault, "FaultInjectorBlock.java",
        "boolean resetDiagnostics(Level level, BlockPos pos)",
        "runtime[1] = 0;",
        "runtime[2] = 0;",
        "resetDiagnostics(level, pos);",
        '"Fault injector statistics reset"')
fault_reset = fault[fault.find("public boolean resetDiagnostics"):fault.find("@Override", fault.find("public boolean resetDiagnostics"))]
forbid(fault_reset, "FaultInjectorBlock.resetDiagnostics",
       "RuntimeIntStore.remove", "runtime[0] =", "runtime[3] =", "runtime[4] =")

require(menu, "UniversalFieldDeviceMenu.java",
        "CONFIG_SEQUENCE_CONTROLLER = 9",
        "CONFIG_SAFETY_INTERLOCK = 10",
        "CONFIG_TOPOLOGY_DEBUGGER = 11",
        "SequenceControllerBlock.step(level, blockPos)",
        "SequenceControllerBlock.completedCycles(level, blockPos)",
        "SafetyInterlockBlock.failedMask(level, blockPos)",
        "TopologyDebuggerBlock.scanCount(level, blockPos)",
        "TopologyDebuggerBlock.targetsVanillaRedstone(level, blockPos, state)",
        "AlarmProcessorBlock.latched(level, blockPos)",
        "AlarmProcessorBlock.unacknowledged(level, blockPos)",
        "FaultInjectorBlock.active(level, blockPos) ? 1 : 0",
        "faultInjector.resetDiagnostics(level, blockPos)",
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
        'Component.literal("Reset fault statistics")',
        "CONFIG_SEQUENCE_CONTROLLER",
        "CONFIG_SAFETY_INTERLOCK",
        "CONFIG_TOPOLOGY_DEBUGGER",
        "CONFIG_FAULT_INJECTOR",
        '"ALARM • ACTIVE / UNACK"',
        '"ALARM • ACTIVE / ACK"',
        "action.active = kind != UniversalFieldDeviceMenu.CONFIG_ALARM || menu.configSecondary() == 2",
        '"INTERLOCK • REACQUIRING"',
        '"NOT EVALUATED"',
        '"FAULT INJECTOR • ARMED"',
        '"FAULT INJECTOR • SAFE"',
        '"ARMED / INJECTION ACTIVE"',
        '"SAFE / PASS-THROUGH"',
        '"LIVE ONLY • NO RETAINED HISTORY"',
        '"Current evidence"',
        '"SYNCHRONIZED SNAPSHOT"',
        '"Retained chronology belongs in analyzers, monitors, or the Diagnostic Tablet."',
        '"Missing permissives"',
        '"Target mode"',
        '"Completed cycles"')
forbid(screen, "UniversalFieldDeviceScreen.history",
       '"Universal HMI intentionally stores no client-local history."',
       '"This prevents opening a UI from creating measurement evidence or changing simulation state."')

if errors:
    print("RSE SYSTEM HMI VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE SYSTEM HMI VERIFY: PASS")
print(" alarm: CLEAR / ACTIVE-UNACK / ACTIVE-ACK operator state")
print(" fault injector: SAFE/ARMED live state + statistics-only reset preserving active/last I/O")
print(" sequence: shared operator reset + synchronized step/cycle evidence")
print(" interlock: explicit unevaluated state + failed permissive mask + non-bypass reset")
print(" topology: HMI opener + scan target/count + counter reset")
print(" universal history: compact live-only state; retained chronology delegated to evidence tools")
print(" system blocks use multi-port route semantics in universal HMI")
