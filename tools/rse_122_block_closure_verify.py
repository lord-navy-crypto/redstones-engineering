#!/usr/bin/env python3
"""Static closure gate for findings from the repository-wide 122-block audit."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
BLOCK = ROOT / "src/main/java/dev/redstoneengineering/block"
GT = ROOT / "src/main/java/dev/redstoneengineering/gametest/RseTotalAuditClosureGameTests.java"
REG = ROOT / "src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java"
WORKFLOW = ROOT / ".github/workflows/build.yml"
TOTAL_AUDIT = ROOT / "tools/rse_122_block_total_audit.py"

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

# These three had runtime acceptance coverage but were the only blocks not named by a static
# verifier in the first matrix. Naming and probing their stable architecture here closes that gap.
require_all(precision, ("class PrecisionFilterBlock", "EngineeringPortProvider"), "PrecisionFilterBlock.java")
require_all(pwm, ("class PwmControllerBlock", "EngineeringPortProvider"), "PwmControllerBlock.java")
require_all(sample_hold, ("class SampleHoldBlock", "EngineeringPortProvider"), "SampleHoldBlock.java")

count = len(re.findall(r"@GameTest\s*\(", gt))
if count != 4:
    errors.append(f"RseTotalAuditClosureGameTests.java: expected exactly 4 @GameTest methods, found {count}")
require(reg, "event.register(RseTotalAuditClosureGameTests.class);", "RseGameTestRegistration.java")
require(workflow, "tools/rse_122_block_total_audit.py", "build.yml")
require(workflow, "tools/rse_122_block_closure_verify.py", "build.yml")
require(total_audit, "EXPECTED_REGISTERED = 122", "rse_122_block_total_audit.py")
require(total_audit, '"pid_controller"', "rse_122_block_total_audit.py")

if errors:
    print("RSE 122-BLOCK CLOSURE VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    sys.exit(1)

print("RSE 122-BLOCK CLOSURE VERIFY: PASS")
print("  closure targets: signal analyzer, scope, logic analyzer, magnetic free-space quartet")
print("  static-evidence closure: precision filter, PWM controller, sample-and-hold")
print("  closure GameTests: 4")
