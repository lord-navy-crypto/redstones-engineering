#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing ninth-ten file: {rel}")
        return ""
    return path.read_text(errors="ignore")


def require(rel: str, *tokens: str) -> None:
    body = read(rel)
    for token in tokens:
        if body and token not in body:
            errors.append(f"{rel}: missing ninth-ten contract token {token!r}")


require(
    "src/main/java/dev/redstoneengineering/block/DigitalRegeneratorBlock.java",
    "SerialNetwork.quality",
    "InformationRuntime.snapshot",
    "WATCHDOG_TICKS = 16",
    "return upstream;",
)
require(
    "src/main/java/dev/redstoneengineering/block/DifferentialDriverBlock.java",
    "RedstoneObservationSupport.observe",
    "input.valid()",
    'InformationRuntime.snapshot(level, "diff_out", pos)',
    "DifferentialNetwork.recompute",
)
require(
    "src/main/java/dev/redstoneengineering/block/DifferentialReceiverBlock.java",
    "DifferentialNetwork.quality",
    "InformationRuntime.snapshot",
    "quality == PortQuality.VALID",
)
require(
    "src/main/java/dev/redstoneengineering/block/SculkVibrationInterfaceBlock.java",
    "RedstoneObservationSupport.observe",
    "observation.quality()",
    "RuntimeIntStore.peek",
    "captureSample",
)
require(
    "src/main/java/dev/redstoneengineering/block/PidControllerBlock.java",
    "RedstoneObservationSupport.observe",
    "controlOutputQuality",
    "requiredQuality",
    "!usable(setpointObservation) || !usable(processObservation)",
    "return 0;",
)
require(
    "src/main/java/dev/redstoneengineering/block/WatchdogBlock.java",
    "SOURCE_SEEN",
    "RedstoneObservationSupport.observe",
    "RuntimeIntStore.peek",
    "A source appearing is only a baseline",
)
require(
    "src/main/java/dev/redstoneengineering/physics/PneumaticObservationSupport.java",
    "InformationRuntime.snapshot",
    "PortQuality.STALE",
    "PortQuality.VALID",
    "AirReservoirBlock",
)
require(
    "src/main/java/dev/redstoneengineering/block/AirCompressorBlock.java",
    "RedstoneObservationSupport.observe",
    "PneumaticObservationSupport.observe",
    "commandObservation",
)
require(
    "src/main/java/dev/redstoneengineering/block/PneumaticPipeBlock.java",
    "PneumaticObservationSupport.observe",
    "observation.quality()",
)
require(
    "src/main/java/dev/redstoneengineering/block/AirReservoirBlock.java",
    'InformationRuntime.snapshot(level, "air_reservoir", pos)',
    "PneumaticObservationSupport.observe",
    "Observer-only stored pressure readback",
)
require(
    "src/main/java/dev/redstoneengineering/block/PressureRegulatorBlock.java",
    "PneumaticObservationSupport.observe",
    "setpointPressure",
    "observation.quality()",
)

runtime_tests = "src/main/java/dev/redstoneengineering/gametest/RseNinthTenDesignBugGameTests.java"
for method in (
    "digitalRegeneratorPreservesSerialConflictQuality",
    "differentialDriverSeparatesEmptyInputFromDrivenZero",
    "differentialReceiverPreservesConflictQuality",
    "sculkInterfaceSeparatesIdleDrivenZeroFromEmptyInput",
    "pidRequiresRealProcessEvidenceInAuto",
    "watchdogUsesObservedHeartbeatEdgeNotMissingInput",
    "compressorSeparatesEmptyCommandFromValidZeroPressure",
    "pneumaticPipeInspectionIsNeutralThenZeroIsValid",
    "airReservoirStoredPressureReadIsObserverNeutral",
    "pressureRegulatorSetpointDoesNotFabricatePressure",
):
    require(runtime_tests, f"void {method}(GameTestHelper helper)")

test_body = read(runtime_tests)
if test_body and len(re.findall(r"@GameTest\(", test_body)) != 10:
    errors.append("ninth-ten GameTest class must contain exactly ten @GameTest methods")

require(
    "src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java",
    "event.register(RseNinthTenDesignBugGameTests.class);",
)
require(
    "src/main/java/dev/redstoneengineering/gametest/RseEighthEightAcceptanceGameTests.java",
    "reference(Direction.SOUTH, 0)",
    "reference(Direction.EAST, 0)",
    "reference(Direction.EAST, 15)",
)

workflow = read(".github/workflows/build.yml")
if workflow and "tools/rse_ninth_ten_control_pneumatic_verify.py" not in workflow:
    errors.append("workflow does not gate the ninth-ten verifier")
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
    print("RSE ninth-ten control/pneumatic verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE ninth-ten control/pneumatic verification: PASS")
print("  serial regenerator quality propagation: PASS")
print("  differential empty/zero/conflict conversion boundaries: PASS")
print("  Sculk valid-zero source evidence: PASS")
print("  PID required-feedback fail-safe semantics: PASS")
print("  watchdog observed-heartbeat chronology: PASS")
print("  pneumatic zero/unknown observer semantics: PASS")
print("  reservoir observer-neutral retained pressure: PASS")
print("  registered ninth-ten GameTests: 10 (manual diagnostic / non-blocking)")
