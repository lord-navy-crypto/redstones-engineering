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
amethyst = read("src/main/java/dev/redstoneengineering/client/ui/AmethystSystemScreen.java")
reliability = read("src/main/java/dev/redstoneengineering/client/ui/ReliabilitySystemScreen.java")
radio = read("src/main/java/dev/redstoneengineering/client/ui/RadioLinkScreen.java")
analyzer = read("src/main/java/dev/redstoneengineering/client/ui/SignalAnalyzerScreen.java")
buffer = read("src/main/java/dev/redstoneengineering/client/ui/IndustrialBufferScreen.java")
logic = read("src/main/java/dev/redstoneengineering/client/ui/LogicAnalyzerScreen.java")
copper = read("src/main/java/dev/redstoneengineering/client/ui/CopperCircuitMeterScreen.java")
ops = read("src/main/java/dev/redstoneengineering/client/ui/OperationsMonitorScreen.java")
workcell = read("src/main/java/dev/redstoneengineering/client/ui/WorkcellControllerScreen.java")
universal = read("src/main/java/dev/redstoneengineering/client/ui/UniversalFieldDeviceScreen.java")
enhanced = read("src/main/java/dev/redstoneengineering/client/ui/EnhancedFieldDeviceScreen.java")
processor = read("src/main/java/dev/redstoneengineering/client/ui/SignalProcessorScreen.java")
client_registration = read("src/main/java/dev/redstoneengineering/client/ui/EngineeringUiClientRegistration.java")
field_menu = read("src/main/java/dev/redstoneengineering/ui/menu/FieldDeviceMenu.java")

for token in (
    "Pioneer / Showcase blocks",
    "Lapis Low-Pass Filter — model transparency pioneer",
    "Oscilloscope — experiment and sampling pioneer",
    "PID Controller — control and acceptance-evidence pioneer",
    "MODEL — expose the real implemented transfer/model/constraint",
    "Do not invent knobs, formulas, history, uncertainty, experiments or hidden physics",
    "Wave 1:",
    "Wave 2:",
    "Wave 3:",
    "Wave 4:",
    "Wave 5:",
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


wave3 = {
    "AmethystSystemScreen.java": (amethyst, (
        "PIONEER PATTERN • RESONANCE MODEL",
        "resonanceEquation()",
        "A_out = (f_in = f_target) ? max(0, A_in - 1) : 0",
        "BW = 5 - Q",
        "Frequency values are deliberate model indices, not fabricated Hz",
    )),
    "ReliabilitySystemScreen.java": (reliability, (
        "PIONEER PATTERN • RELIABILITY / SAFE STATE",
        "reliabilityEquation()",
        "heartbeat seen ∧ age ≥ timeout",
        "spread ≤ tolerance",
        "fault ≥ threshold",
    )),
    "RadioLinkScreen.java": (radio, (
        "PIONEER PATTERN • RADIO LINK BUDGET",
        "radioEquation()",
        "M_decode = Q_link - Q_min",
        "availability = 100 · validSamples / samples",
        'variableRole(g,"EVIDENCE","path"',
    )),
    "SignalAnalyzerScreen.java": (analyzer, (
        "PIONEER PATTERN • METROLOGY / CALIBRATION",
        "x_cal = clamp(x_raw + b_cal, 0, 15)",
        'variableRole(graphics,"ADJUSTABLE","b_cal"',
        "Calibration changes only the displayed engineering reading",
        "client never samples the world",
    )),
    "IndustrialBufferScreen.java": (buffer, (
        "PIONEER PATTERN • OPERATIONS / WIP MODEL",
        "WIP% = 100·used/capacity",
        "clamp(round(15·used/capacity),1,15)",
        'evidenceRow(graphics,"Workcell roles"',
        "LOT identity",
    )),
}

for name, (text, tokens) in wave3.items():
    for token in tokens:
        if token not in text:
            errors.append(f"{name} missing Wave-3 pioneer-rollout token {token!r}")
    if "dev.redstoneengineering.physics" in text:
        errors.append(f"{name} imports physics directly; client screen must remain presentation-only")


wave4 = {
    "LogicAnalyzerScreen.java": (logic, (
        "PIONEER PATTERN • DIGITAL TIMING MODEL",
        "D_ch[n] = (x_ch[n] ≥ T) ? HIGH : LOW",
        '"Δt_sample"',
        '"Δt_cursor"',
        "server capture engine",
    )),
    "CopperCircuitMeterScreen.java": (copper, (
        "PIONEER PATTERN • ELECTRICAL MEASUREMENT MODEL",
        "I = V / R_eq ; P = V · I",
        '"commissioning"',
        "observer-only",
        "server computes V, R_eq, I and P",
    )),
    "OperationsMonitorScreen.java": (ops, (
        "PIONEER PATTERN • PLANT STATE / KPI AUTHORITY",
        "QUEUE = max(valid horizontal QUEUE/WIP sources)",
        '"queue pressure"',
        '"state"',
        "KPIs stay WITHHELD",
    )),
    "WorkcellControllerScreen.java": (workcell, (
        "PIONEER PATTERN • WORKCELL ADMISSION GATE",
        "PERMIT ⇔ valid capacity evidence ∧ no fault ∧ output space ∧ resource capacity",
        '"admission"',
        '"reason"',
        "Operations Binding Tool",
    )),
    "UniversalFieldDeviceScreen.java": (universal, (
        "universalContract(kind)",
        "Universal HMI rule:",
        "reset statistics does not disarm",
        "diagnostics reset never bypasses",
        "no hidden universal physics",
    )),
}

for name, (text, tokens) in wave4.items():
    for token in tokens:
        if token not in text:
            errors.append(f"{name} missing Wave-4 pioneer-rollout token {token!r}")
    if "dev.redstoneengineering.physics" in text:
        errors.append(f"{name} imports physics directly; client screen must remain presentation-only")


wave5 = {
    "EnhancedFieldDeviceScreen.java": (enhanced, (
        "PIONEER PATTERN • SHARED FIELD DEVICE",
        "pioneerContract()",
        "sharedPioneerExplanation()",
        "TOPOLOGY: connected faces = physical graph edges; medium identity is preserved",
        "OBSERVE: physical/process state → synchronized evidence; network drive = NONE",
        "STATE: safety/process state is server-authoritative; invalid evidence fails closed",
        '"AUTHORITY", "policy"',
    )),
    "SignalProcessorScreen.java": (processor, (
        "PIONEER PATTERN • SIGNAL PROCESSOR MODEL",
        "processorEquation()",
        "y[n+1] = y[n] + clamp(x[n]-y[n], -r, +r)",
        "e[n] = edge_mode(x[n-1], x[n]); e[n] ⇒ y=15 for 2 ticks",
        "rising edge(x) ⇒ y=15 for W ticks; otherwise y=0",
        "Observer readback never initializes or retriggers runtime state",
    )),
}

for name, (text, tokens) in wave5.items():
    for token in tokens:
        if token not in text:
            errors.append(f"{name} missing Wave-5 pioneer-rollout token {token!r}")
    if "dev.redstoneengineering.physics" in text:
        errors.append(f"{name} imports physics directly; client screen must remain presentation-only")

registration_token = "event.register(EngineeringUiRegistration.FIELD_DEVICE.get(), EnhancedFieldDeviceScreen::new);"
if registration_token not in client_registration:
    errors.append("FIELD_DEVICE is no longer registered to the enhanced shared Pioneer inspector")

kind_count = field_menu.count("public static final int KIND_")
if kind_count < 79:
    errors.append(f"expected FieldDeviceMenu taxonomy to retain at least 79 device kinds, found {kind_count}")

formula_migrated = 0
screens_dir = root / "src/main/java/dev/redstoneengineering/client/ui"
if screens_dir.is_dir():
    for path in screens_dir.glob("*Screen.java"):
        text = path.read_text(errors="ignore")
        if "extends EngineeringScreen<" in text and "formulaCard(" in text:
            formula_migrated += 1

if formula_migrated < 20:
    errors.append(f"expected at least 20 formula-first EngineeringScreen families after Wave 5, found {formula_migrated}")

if errors:
    print("RSE PIONEER SHOWCASE ROLLOUT VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE PIONEER SHOWCASE ROLLOUT VERIFY: PASS")
print(" showcase pioneer references: low-pass / oscilloscope / PID PASS")
print(" Wave 2 communication / pneumatic / optical / magnetic PASS")
print(" Wave 3 amethyst / reliability / radio / metrology / operations PASS")
print(" Wave 4 timing / copper / plant-state / workcell / universal-contract PASS")
print(" Wave 5 shared field-device inspector / signal processors PASS")
print(f" FieldDeviceMenu device-kind taxonomy: {kind_count}")
print(f" formula-first EngineeringScreen families: {formula_migrated}")
print(" no client-side second physics solver in Waves 2-5: PASS")
