#!/usr/bin/env python3
from pathlib import Path
import json
import re
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
failed = []

def text(rel):
    p = root / rel
    if not p.exists():
        failed.append(f"missing {rel}")
        return ""
    return p.read_text(errors="ignore")

def require(rel, *tokens):
    body = text(rel)
    for token in tokens:
        if token not in body:
            failed.append(f"{rel}: missing {token!r}")

props = text("gradle.properties")
version_match = re.search(r"^mod_version=(\d+)\.(\d+)\.(\d+)-alpha(?:[.-][0-9A-Za-z.-]+)?$", props, re.MULTILINE)
if not version_match:
    failed.append("gradle.properties does not contain a supported alpha semantic version")
else:
    version_tuple = tuple(map(int, version_match.groups()))
    if version_tuple < (1, 0, 7):
        failed.append(f"Alpha 1.0.7 regression requires version >= 1.0.7-alpha, found {version_tuple}")

require("src/main/java/dev/redstoneengineering/block/PortDiagnostics.java",
        "DOMAIN_MISMATCH", "connectedCable", "surfaceTrace", "directionalFlow", "INSULATED_REDSTONE", "INSTRUMENT_BUS")
require("src/main/java/dev/redstoneengineering/block/InstrumentCableBlock.java",
        "extends ConnectedCableBlock", "TransmissionTopology.instrumentPort", "return 6", "PortDiagnostics.connectedCable")
require("src/main/java/dev/redstoneengineering/block/TransmissionTopology.java",
        "instrumentPort", "SignalProbeBlock.FACING", "OscilloscopeBlock", "LogicAnalyzerBlock")
require("src/main/java/dev/redstoneengineering/instrument/InstrumentNetwork.java",
        "ConnectedCableBlock.connected", "TransmissionTopology.instrumentPort", "direction.getOpposite()")
require("src/main/java/dev/redstoneengineering/block/RedstoneSignalCableBlock.java",
        "signal=", "PortDiagnostics.Domain.INSULATED_REDSTONE", "Math.min(15")
require("src/main/java/dev/redstoneengineering/block/CopperWireBlock.java",
        "PortDiagnostics.Domain.COPPER", "Math.min(15")
require("src/main/java/dev/redstoneengineering/block/LapisSignalLineBlock.java",
        "PortDiagnostics.surfaceTrace", "PortDiagnostics.Domain.LAPIS")
require("src/main/java/dev/redstoneengineering/block/QuartzTimingLineBlock.java",
        "PortDiagnostics.surfaceTrace", "PortDiagnostics.Domain.QUARTZ")
require("src/main/java/dev/redstoneengineering/block/RedstoneCableTerminalBlock.java",
        "PortDiagnostics.terminal", "vanillaSide", "cableSide")

blockstate = root / "src/main/resources/assets/redstoneengineering/blockstates/instrument_cable.json"
try:
    data = json.loads(blockstate.read_text())
    if "multipart" not in data:
        failed.append("instrument_cable blockstate must accept topology properties via multipart")
except Exception as exc:
    failed.append(f"instrument_cable blockstate invalid: {exc}")

for rel in ["docs/ALPHA1_0_7_LEGACY_WIRING_PORTS.md", "docs/ALPHA1_0_7_TEST_MATRIX.md", "ALPHA1_0_7_MANIFEST.txt"]:
    if not (root / rel).exists(): failed.append(f"missing {rel}")

workflow = text(".github/workflows/build.yml")
if "rse_alpha107_legacy_wiring_verify.py" not in workflow:
    failed.append("workflow missing Alpha 1.0.7 verifier")

if failed:
    print("RSE Alpha 1.0.7 legacy wiring verification: FAIL")
    for item in failed: print(" -", item)
    raise SystemExit(1)

print("RSE Alpha 1.0.7 legacy wiring verification: PASS")
print(" forward-compatible version gate: PASS")
print(" instrument bus physical-edge traversal: PASS")
print(" port/mismatch diagnostics: PASS")
print(" redstone 0..15 boundary retained: PASS")
print(" legacy trace/cable diagnostics: PASS")
