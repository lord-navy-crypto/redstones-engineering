#!/usr/bin/env python3
"""Static closure gate for the audited 122-block core plus systems extensions."""
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
BLOCK = ROOT / "src/main/java/dev/redstoneengineering/block"
GT = ROOT / "src/main/java/dev/redstoneengineering/gametest/RseTotalAuditClosureGameTests.java"
REG = ROOT / "src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java"
WORKFLOW = ROOT / ".github/workflows/build.yml"
TOTAL_AUDIT = ROOT / "tools/rse_122_block_total_audit.py"
SYSTEMS_VERIFY = ROOT / "tools/rse_engineering_systems_verify.py"

errors: list[str] = []

def read(path: Path) -> str:
    if not path.exists():
        errors.append(f"missing {path.relative_to(ROOT)}")
        return ""
    return path.read_text(encoding="utf-8")

def require(source: str, needle: str, label: str) -> None:
    if needle not in source:
        errors.append(f"{label}: missing {needle!r}")

def require_all(source: str, needles: tuple[str, ...], label: str) -> None:
    for needle in needles:
        require(source, needle, label)

signal = read(BLOCK / "SignalAnalyzerBlock.java")
scope = read(BLOCK / "OscilloscopeBlock.java")
logic = read(BLOCK / "LogicAnalyzerBlock.java")
iron = read(BLOCK / "IronCoreBlock.java")
magnet = read(BLOCK / "PermanentMagnetBlock.java")
field = read(BLOCK / "MagneticFieldSensorBlock.java")
gradient = read(BLOCK / "MagneticGradientMeterBlock.java")
directional = read(BLOCK / "DirectionalSignalBlock.java")
precision = read(BLOCK / "PrecisionFilterBlock.java")
pwm = read(BLOCK / "PwmControllerBlock.java")
sample_hold = read(BLOCK / "SampleHoldBlock.java")
gt = read(GT)
reg = read(REG)
workflow = read(WORKFLOW)
total_audit = read(TOTAL_AUDIT)

require_all(signal, (
    "implements EngineeringPortProvider",
    "EngineeringDomain.REDSTONE",
    '"TAP APERTURE"',
    '"INLINE OUT"',
    "RuntimeIntStore.remove(level, KEY, pos)",
    "canConnectRedstone(",
), "SignalAnalyzerBlock.java")

for name, source in (("OscilloscopeBlock.java", scope), ("LogicAnalyzerBlock.java", logic)):
    require_all(source, (
        "implements EntityBlock, EngineeringPortProvider",
        "EngineeringDomain.INSTRUMENT_BUS",
        "PortKind.BUS",
        "PortDirection.INPUT",
        "engineeringSnapshot(",
        "canConnectRedstone(",
    ), name)

require_all(iron, (
    "implements EngineeringPortProvider",
    "EngineeringDomain.IRON_MAGNETIC",
    '"MAGNETIC COUPLING "',
    "PortDirection.BIDIRECTIONAL",
), "IronCoreBlock.java")
require_all(magnet, (
    "implements EngineeringPortProvider",
    "EngineeringDomain.IRON_MAGNETIC",
    '"MAGNETIC FIELD "',
    "PortDirection.OUTPUT",
), "PermanentMagnetBlock.java")
require_all(field, (
    "implements EngineeringPortProvider",
    "EngineeringDomain.IRON_MAGNETIC",
    '"MAGNETIC APERTURE "',
    "PortKind.MEASUREMENT",
), "MagneticFieldSensorBlock.java")
require_all(gradient, (
    "implements EngineeringPortProvider",
    "EngineeringDomain.IRON_MAGNETIC",
    '"GRADIENT APERTURE "',
    "PortKind.MEASUREMENT",
), "MagneticGradientMeterBlock.java")

# Precision Filter intentionally inherits the common two-face REDSTONE contract.
require_all(directional, (
    "implements EngineeringPortProvider",
    "EngineeringDomain.REDSTONE",
    "engineeringSnapshot(",
    "canConnectRedstone(",
), "DirectionalSignalBlock.java")
require(precision, "class PrecisionFilterBlock extends DirectionalSignalBlock", "PrecisionFilterBlock.java")

# PWM and Sample-and-Hold inherit the base electrical behavior but expose additional physical
# control faces. Those faces must be present in the same engineering descriptor/snapshot layer
# used by Jade and the Field Device Inspector.
require_all(pwm, (
    "class PwmControllerBlock extends DirectionalSignalBlock",
    '"COMMAND IN"',
    '"PWM OUT"',
    '"INHIBIT"',
    "PortKind.SAFETY",
    "engineeringSnapshot(",
), "PwmControllerBlock.java")
require_all(sample_hold, (
    "class SampleHoldBlock extends DirectionalSignalBlock",
    '"VALUE IN"',
    '"HELD OUT"',
    '"TRIGGER"',
    '"RESET"',
    "PortKind.TRIGGER",
    "PortKind.RESET",
    "engineeringSnapshot(",
), "SampleHoldBlock.java")

count = len(re.findall(r"@GameTest\s*\(", gt))
if count != 4:
    errors.append(f"RseTotalAuditClosureGameTests.java: expected exactly 4 @GameTest methods, found {count}")
require(reg, "event.register(RseTotalAuditClosureGameTests.class);", "RseGameTestRegistration.java")
require(workflow, "tools/rse_122_block_total_audit.py", "build.yml")
require(workflow, "tools/rse_122_block_closure_verify.py", "build.yml")
require(workflow, "tools/rse_neighbor_update_audit.py", "build.yml")
require(workflow, 'Too many chained neighbor updates', "build.yml")
require(total_audit, "EXPECTED_REGISTERED = 122", "rse_122_block_total_audit.py")
require(total_audit, '"pid_controller"', "rse_122_block_total_audit.py")

# The systems verifier is intentionally chained from this already-required CI gate. This keeps
# the historical 122-block total audit intact while making the aggregate 122 + 3 systems closure
# a hard failure if registration, resources, ports, cleanup, or GameTests regress.
if not SYSTEMS_VERIFY.exists():
    errors.append("missing tools/rse_engineering_systems_verify.py")
else:
    proc = subprocess.run(
        [sys.executable, str(SYSTEMS_VERIFY)],
        cwd=ROOT,
        text=True,
        capture_output=True,
        check=False,
    )
    if proc.stdout:
        print(proc.stdout, end="")
    if proc.stderr:
        print(proc.stderr, end="", file=sys.stderr)
    if proc.returncode != 0:
        errors.append(f"engineering systems verifier failed with exit code {proc.returncode}")

if errors:
    print("RSE 125-BLOCK AGGREGATE CLOSURE VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    sys.exit(1)

print("RSE 125-BLOCK AGGREGATE CLOSURE VERIFY: PASS")
print("  historical deep-audit core: 122")
print("  systems extension: 3")
print("  closure targets: signal analyzer, scope, logic analyzer, magnetic free-space quartet")
print("  static-evidence closure: precision filter plus truthful PWM/sample-hold control faces")
print("  systems closure: sequence controller, safety interlock, fault injector")
print("  neighbor-update storm runtime gate: present")
print("  legacy closure GameTests: 4")
