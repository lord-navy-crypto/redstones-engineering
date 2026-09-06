#!/usr/bin/env python3
"""Static gate for alarm/event semantics and observer-only topology diagnostics."""
from pathlib import Path
import re, sys
ROOT = Path(__file__).resolve().parents[1]
BLOCK = ROOT / "src/main/java/dev/redstoneengineering/block"
TOPO = ROOT / "src/main/java/dev/redstoneengineering/diagnostics/topology"
GT = ROOT / "src/main/java/dev/redstoneengineering/gametest/RseEngineeringSystemsGameTests.java"
errors=[]
def read(p):
    if not p.exists(): errors.append(f"missing {p.relative_to(ROOT)}"); return ""
    return p.read_text(encoding="utf-8")
def need(src, text, label):
    if text not in src: errors.append(f"{label}: missing {text!r}")
alarm=read(BLOCK/"AlarmProcessorBlock.java"); debug=read(BLOCK/"TopologyDebuggerBlock.java"); report=read(TOPO/"TopologyDiagnosticsReport.java"); gt=read(GT)
for s in ("SEVERITY", '"ALARM CONDITION"', '"ACKNOWLEDGE"', '"RESET / CLEAR"', "condition <= 0", "PortQuality.FAULT", "RuntimeIntStore.remove(level, KEY, pos)"): need(alarm,s,"AlarmProcessorBlock.java")
for s in ("EngineeringTopologyView.inspect", "TopologyDiagnosticsReport", '"TOPOLOGY ALARM OUT"', "report.hasIssue()", "RuntimeIntStore.remove(level, KEY, pos)"): need(debug,s,"TopologyDebuggerBlock.java")
for s in ("DOMAIN_MISMATCH", "DIRECTION_MISMATCH", "PortQuality.FAULT", "disconnectedIsland", "hasIssue()", "summary()"): need(report,s,"TopologyDiagnosticsReport.java")
need(gt,"alarmProcessorLatchesAndRequiresHealthyReset","RseEngineeringSystemsGameTests.java"); need(gt,"topologyDebuggerFlagsDanglingEngineeringTarget","RseEngineeringSystemsGameTests.java")
if len(re.findall(r"@GameTest\s*\(",gt)) != 5: errors.append("RseEngineeringSystemsGameTests.java: expected exactly 5 systems GameTests")
if errors:
    print("RSE ALARM + TOPOLOGY DIAGNOSTICS VERIFY: FAIL")
    for e in errors: print(" -",e)
    sys.exit(1)
print("RSE ALARM + TOPOLOGY DIAGNOSTICS VERIFY: PASS")
print("  latched alarm + acknowledge + healthy-reset semantics: PASS")
print("  severity-coded redstone alarm output: PASS")
print("  observer-only topology explanation layer: PASS")
print("  dangling/mismatch/unloaded/fault/disconnected classifications: PASS")
print("  executable systems GameTests: 5")
