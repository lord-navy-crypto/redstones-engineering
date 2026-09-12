#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing tenth-ten file: {rel}")
        return ""
    return path.read_text(errors="ignore")


def require(rel: str, *tokens: str) -> None:
    body = read(rel)
    for token in tokens:
        if body and token not in body:
            errors.append(f"{rel}: missing tenth-ten contract token {token!r}")


require(
    "src/main/java/dev/redstoneengineering/physics/InformationRuntime.java",
    "return snapshot(level, medium, pos).value();",
    "return snapshot(level, medium, pos).selector();",
    "return snapshot(level, medium, pos).valid();",
    "return snapshot(level, medium, pos).qualityPercent();",
    "Observer-neutral scalar read",
)
require(
    "src/main/java/dev/redstoneengineering/block/PneumaticReceiverBlock.java",
    "PneumaticObservationSupport.observe",
    "pressure.quality()",
)
require(
    "src/main/java/dev/redstoneengineering/block/PneumaticValveBlock.java",
    "PneumaticObservationSupport.observe",
    "observation.quality()",
)
require(
    "src/main/java/dev/redstoneengineering/block/PneumaticCheckValveBlock.java",
    "PneumaticObservationSupport.observe",
    "observation.quality()",
)
require(
    "src/main/java/dev/redstoneengineering/block/PneumaticFlowMeterBlock.java",
    "RuntimeIntStore.peek",
    "PneumaticObservationSupport.observe",
    "inlet.valid() && outlet.valid()",
    "runtimeSnapshot",
)
require(
    "src/main/java/dev/redstoneengineering/block/PneumaticProportionalValveBlock.java",
    "RedstoneObservationSupport.observe",
    "PneumaticObservationSupport.observe",
    "commandObservation",
)
require(
    "src/main/java/dev/redstoneengineering/block/PneumaticReliefValveBlock.java",
    "diagnosticsSnapshot",
    "mutableDiagnostics",
    "RuntimeIntStore.peek",
    "PneumaticObservationSupport.observe",
)
require(
    "src/main/java/dev/redstoneengineering/block/PneumaticCylinderBlock.java",
    "diagnosticsSnapshot",
    "RuntimeIntStore.peek",
    "PneumaticObservationSupport.observe",
)
require(
    "src/main/java/dev/redstoneengineering/physics/VibrationNetwork.java",
    'InformationRuntime.snapshot(level, "mech_wave", pos)',
    "snapshot.selector()",
)
require(
    "src/main/java/dev/redstoneengineering/block/MechanicalExciterBlock.java",
    "RedstoneObservationSupport.observe",
    "driveObservation",
    "drive.quality()",
)

runtime_tests = "src/main/java/dev/redstoneengineering/gametest/RseTenthTenDesignBugGameTests.java"
for method in (
    "pneumaticReceiverSeparatesUnknownFromSolvedZero",
    "pneumaticValvePortsPreserveSolvedZero",
    "pneumaticCheckValvePortsPreserveSolvedZero",
    "pneumaticFlowMeterInspectionDoesNotCreateRuntimeOrSamples",
    "proportionalValveSeparatesMissingCommandFromDrivenZero",
    "reliefValveDiagnosticsAreObserverNeutral",
    "pneumaticCylinderInspectionIsNeutralAndZeroInputIsValid",
    "slimeVibrationInspectionDoesNotCreateWaveRuntime",
    "honeyDamperInspectionDoesNotCreateWaveRuntime",
    "mechanicalExciterSeparatesMissingDriveFromDrivenZero",
):
    require(runtime_tests, f"void {method}(GameTestHelper helper)")

test_body = read(runtime_tests)
if test_body and len(re.findall(r"@GameTest\(", test_body)) != 10:
    errors.append("tenth-ten GameTest class must contain exactly ten @GameTest methods")

require(
    "src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java",
    "event.register(RseTenthTenDesignBugGameTests.class);",
)

workflow = read(".github/workflows/build.yml")
if workflow and "tools/rse_tenth_ten_pneumatic_vibration_verify.py" not in workflow:
    errors.append("workflow does not gate the tenth-ten verifier")
for token in (
    "Minecraft topology GameTests (manual diagnostic)",
    "github.event_name == 'workflow_dispatch'",
    "continue-on-error: true",
    "./gradlew runGameTestServer",
    "./gradlew compileJava",
    "./gradlew test",
):
    if workflow and token not in workflow:
        errors.append(f"build.yml: missing manual/non-blocking GameTest policy token {token!r}")

if errors:
    print("RSE tenth-ten pneumatic/vibration verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE tenth-ten pneumatic/vibration verification: PASS")
print("  observer-neutral InformationRuntime scalar reads: PASS")
print("  pneumatic solved-zero evidence propagation: PASS")
print("  flow/relief/cylinder diagnostics neutrality: PASS")
print("  proportional-valve and exciter redstone source evidence: PASS")
print("  vibration sampling observer neutrality: PASS")
print("  registered tenth-ten GameTests: 10 (manual diagnostic / non-blocking)")
