#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors = []

def read(rel):
    path = root / rel
    if not path.is_file():
        errors.append(f"missing {rel}")
        return ""
    return path.read_text(errors="ignore")

doc = read("docs/PIONEER_SHOWCASE_STANDARD.md")
lowpass = read("src/main/java/dev/redstoneengineering/client/ui/LapisLowPassScreen.java")
scope = read("src/main/java/dev/redstoneengineering/client/ui/OscilloscopeScreen.java")
pid = read("src/main/java/dev/redstoneengineering/client/ui/PidControllerScreen.java")
digital = read("src/main/java/dev/redstoneengineering/client/ui/DigitalCommunicationScreen.java")
pneumatic = read("src/main/java/dev/redstoneengineering/client/ui/PneumaticSystemScreen.java")
optical = read("src/main/java/dev/redstoneengineering/client/ui/OpticalSystemScreen.java")
magnetic = read("src/main/java/dev/redstoneengineering/client/ui/MagneticSystemScreen.java")

for token in (
    "Pioneer / Showcase blocks",
    "Lapis Low-Pass Filter — model transparency pioneer",
    "Oscilloscope — experiment and sampling pioneer",
    "PID Controller — control and acceptance-evidence pioneer",
    "MODEL — expose the real implemented transfer/model/constraint",
    "Do not invent knobs, formulas, history, uncertainty, experiments or hidden physics",
    "Wave 1:",
    "Wave 2:",
):
    if token not in doc:
        errors.append(f"Pioneer standard missing {token!r}")

for token in (
    "y[n] = y[n-1] + α",
    "LIVE SUBSTITUTION",
    "VARIABLE ROLES",
):
    if token not in lowpass:
        errors.append(f"Low-pass pioneer missing {token!r}")

for token in (
    'EXPERIMENT("Experiment")',
    "SAMPLING MODEL",
    "FROZEN EVIDENCE COMPARISON",
    "PASS",
    "MARGINAL",
    "FAIL",
):
    if token not in scope:
        errors.append(f"Oscilloscope pioneer missing {token!r}")

for token in (
    "Capture acceptance",
    "historyCount()",
    "AcceptanceEvidenceTrend",
):
    if token not in pid:
        errors.append(f"PID pioneer missing acceptance-evidence token {token!r}")

wave2 = {
    "DigitalCommunicationScreen.java": (digital, (
        "PIONEER PATTERN • COMMUNICATION MODEL",
        "communicationEquation()",
        "U = min(100%, 100 · T_frame / Δt_arrival)",
        "Q_bus = max(35, 100 - loadingPenalty - contentionPenalty)",
        'variableRole(g, "MEASURED", "Q_link"',
    )),
    "PneumaticSystemScreen.java": (pneumatic, (
        "PIONEER PATTERN • PNEUMATIC MODEL",
        "pneumaticEquation()",
        "ΔP_path = ΔP_line + ΔP_restriction",
        "H_charge = max(0, P_line - P_stored)",
        "ΔP_local = max(0, P_in - P_out)",
    )),
    "OpticalSystemScreen.java": (optical, (
        "PIONEER PATTERN • OPTICAL MODEL",
        "opticalEquation()",
        "I_out = max(0, I_in - L)",
        "I_A = floor(I_in/2)",
        "L_obs = I_TX - I_RX",
    )),
    "MagneticSystemScreen.java": (magnetic, (
        "PIONEER PATTERN • MAGNETIC MODEL",
        "magneticEquation()",
        "V_ind = clamp(N · |B[n] - B[n-1]|, 0, 15)",
        "Σ S_i / max(1,r_i²)",
        'variableRole(g,"EVIDENCE"',
    )),
}

for name, (text, tokens) in wave2.items():
    for token in tokens:
        if token not in text:
            errors.append(f"{name} missing Wave-2 pioneer-rollout token {token!r}")
    if "dev.redstoneengineering.physics" in text:
        errors.append(f"{name} imports physics directly; client screen must remain presentation-only")

formula_migrated = 0
screens_dir = root / "src/main/java/dev/redstoneengineering/client/ui"
if screens_dir.is_dir():
    for path in screens_dir.glob("*Screen.java"):
        text = path.read_text(errors="ignore")
        if "extends EngineeringScreen<" in text and "formulaCard(" in text:
            formula_migrated += 1

if formula_migrated < 8:
    errors.append(f"expected at least 8 formula-first EngineeringScreen families after Wave 2, found {formula_migrated}")

if errors:
    print("RSE PIONEER SHOWCASE ROLLOUT VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE PIONEER SHOWCASE ROLLOUT VERIFY: PASS")
print(" showcase pioneer references: low-pass / oscilloscope / PID PASS")
print(" Wave 2 communication / pneumatic / optical / magnetic PASS")
print(f" formula-first EngineeringScreen families: {formula_migrated}")
print(" no client-side second physics solver in Wave 2: PASS")
