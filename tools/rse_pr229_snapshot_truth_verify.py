#!/usr/bin/env python3
"""PR #229 shared LDLib2 HMI snapshot-truth regression gate.

The 130-block entry matrix is a *source* coverage statement, not a license
to show default zero, fabricated alarm/permit state or false evidence.
This gate targets the two shared HMI surfaces used across block families.
"""
from pathlib import Path
import sys

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors = []

def read(rel):
    p = ROOT / rel
    if not p.is_file():
        errors.append("Missing file: " + rel)
        return ""
    return p.read_text(errors="replace")

field = read("src/main/java/dev/redstoneengineering/ui/ldlib/EnhancedFieldDeviceLdUi.java")
universal = read("src/main/java/dev/redstoneengineering/ui/ldlib/UniversalFieldDeviceLdUi.java")
menu = read("src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java")

def requires(src, title, tokens):
    for t in tokens:
        if t not in src:
            errors.append(f"{title}: missing contract {t!r}")

# Field/Enhanced: may safely show static role, topology *capabilities* and
# bounds before sync. Actual source counts, masks, configuration and authority
# may only be shown after the server DataSlots are synchronized.
requires(field, "EnhancedFieldDevice", (
    '!m.snapshotReady() ? "NOT READY • awaiting server snapshot"',
    '!m.snapshotReady() ? "NOT READY • awaiting server metric"',
    'hasAuxiliaryMetric(m.kind()) ? Integer.toString(m.driverCount())',
    '"NOT REPORTED • no auxiliary metric"',
    'm.snapshotReady() ? m.portCount() + " / " + m.connectionCount()',
    '"NOT READY • no server topology"',
    '"NOT READY • no connection mask"',
    '"NOT READY • awaiting server authority"',
    '"NOT READY • awaiting server route"',
    'm.snapshotReady()\n                            ? controlValue(m)',
    '"NOT READY • awaiting synchronized setpoint"',
    'm.snapshotReady() ? Integer.toString(controlValue(m)) : ""',
    'if (!m.snapshotReady()) return;',
))
if 'm.topologyValid() ? "SERVER SYNCHRONIZED" : "FAIL-CLOSED"' in field and \
        '"NOT READY • awaiting server authority"' not in field:
    errors.append("FieldDevice claims authority before synchronizing the first snapshot")

# Universal: the same named gating applies to small Overview model previews
# and the six-variable Pioneer content, not just the Configure text box.
requires(universal, "UniversalFieldDevice", (
    '"NOT READY • awaiting server process snapshot"',
    '"NOT READY • awaiting server measurement"',
    '"NOT READY • awaiting server process evidence"',
    '"NOT READY • awaiting server measurement evidence"',
    '"NOT READY • awaiting synchronized setpoint"',
    '"NOT READY • awaiting server state"',
    '"NOT READY • awaiting server detail"',
    '"A=? / B=? / C=? • NOT EVALUATED"',
    '!menu.snapshotReady() || menu.configPrimary() < 0',
    '(!menu.snapshotReady() || menu.configPrimary() < 0)',
    'menu.snapshotReady() ? primaryDisplay(menu) : ""',
    'menu.snapshotReady() ? secondaryDisplay(menu) : ""',
))
# PR229 operator-state truth: a default DataSlot 0 must not claim PWM
# NORMAL, an already-acknowledged alarm, or synchronized evidence before the
# first server packet. A receiver's gain is a configuration setting, not a
# metrology sample (which may legitimately be missing).
requires(universal, "Universal operator-state/readback truth", (
    '() -> !menu.snapshotReady() ? "NOT READY • awaiting server inversion state"',
    'if (!menu.snapshotReady()) return "NOT READY • awaiting server action state";',
    '"NOT READY • awaiting first authoritative port snapshot"',
    'MolecularCloudReceiverBlock.gainFor(menu.configPrimary())',
    'import dev.redstoneengineering.block.MolecularCloudReceiverBlock;',
))
if 'return Integer.toString(menu.pioneerProcessQuinary());' in universal:
    errors.append("Molecular receiver configured gain still depends on process measurement slot")
if 'new Label().setText("SYNCHRONIZED SNAPSHOT")' in universal:
    errors.append("Unsynchronized Universal Evidence page still claims synchronized snapshot")

# Both genuinely absent records and never synchronized initial value must
# remain NOT_READY, not be substituted with a physically meaningful NO_SIGNAL.
for name in ("pioneerProcessEvidenceQuality", "pioneerEvidenceQuality"):
    start = menu.find(f"public PortQuality {name}()")
    end = menu.find("\n    }", start)
    if start < 0 or end < 0:
        errors.append(f"Missing menu getter {name}")
        continue
    body = menu[start:end]
    if 'return ordinal < 0 || ordinal >= all.length ? PortQuality.NOT_READY : all[ordinal];' not in body:
        errors.append(f"{name}: unknown evidence falsely reported as NO_SIGNAL")

for title, screen in (("EnhancedFieldDevice", field), ("UniversalFieldDevice", universal)):
    for forbidden in ("RedstoneCableNetwork.", "DomainNetwork.", "PneumaticNetwork.",
                      "RuntimeIntStore.", "InformationRuntime."):
        if forbidden in screen:
            errors.append(f"{title} HMI must not run physical solver: {forbidden}")

if errors:
    print("RSE PR229 SHARED SNAPSHOT TRUTH: FAIL")
    for item in errors:
        print(" - " + item)
    raise SystemExit(1)
print("RSE PR229 SHARED SNAPSHOT TRUTH: PASS")
print(" FieldDevice pre-sync value/count/route/authority: GUARDED")
print(" Universal Overview/Pioneer six-slot/health/evidence: GUARDED")
print(" Unknown evidence does not masquerade as NO_SIGNAL: GUARDED")
print(" Shared LDLib2 HMI owns no server physics: PASS")
