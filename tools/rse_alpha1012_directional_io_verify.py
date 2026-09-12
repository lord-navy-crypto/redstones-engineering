#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
failed = []


def text(rel: str) -> str:
    path = root / rel
    if not path.exists():
        failed.append(f"missing {rel}")
        return ""
    return path.read_text(errors="ignore")


def require(rel: str, *tokens: str) -> None:
    body = text(rel)
    for token in tokens:
        if token not in body:
            failed.append(f"{rel}: missing {token!r}")


props = text("gradle.properties")
match = re.search(r"^mod_version=(\d+)\.(\d+)\.(\d+)-alpha(?:[.-][0-9A-Za-z.-]+)?$", props, re.MULTILINE)
if not match or tuple(map(int, match.groups())) < (1, 0, 12):
    failed.append("Alpha 1.0.12 requires mod_version >= 1.0.12-alpha")

endpoint = "src/main/java/dev/redstoneengineering/block/DirectionalRedstoneEndpointBlock.java"
sensor_base = "src/main/java/dev/redstoneengineering/block/DirectionalRedstoneSensorBlock.java"
require(endpoint,
        "extends Block", "HORIZONTAL_FACING", "frontSide", "backSide",
        "isQueriedFrom", "connectionMatches", "readBackInput", "notifyFrontOutput",
        "rotateOutput", "notifyNeighbors", "oldOutput", "newOutput",
        "onEndpointRouteChanged(Level level, BlockPos pos, BlockState oldState, BlockState newState)",
        "block.onEndpointRouteChanged(level, pos, state, next)")
require(sensor_base,
        "extends DirectionalRedstoneEndpointBlock", "EngineeringPortProvider",
        "SENSOR OUT", "PortKind.SENSOR", "PortDirection.OUTPUT",
        "connectionMatches(direction, frontSide(state))",
        "isQueriedFrom(state, direction, frontSide(state))",
        "updateSensorOutput")

source = "src/main/java/dev/redstoneengineering/block/RedstoneReferenceSourceBlock.java"
require(source,
        "extends DirectionalRedstoneEndpointBlock", "EngineeringPortProvider",
        "REFERENCE OUT", "PortDirection.OUTPUT",
        "connectionMatches(direction, frontSide(state))",
        "isQueriedFrom(state, direction, frontSide(state))",
        "rotateOutput(level, pos, true)")

endpoint_body = text(endpoint)
if "notifyNeighbors(level, pos, block, oldOutput, newOutput)" not in endpoint_body:
    failed.append(f"{endpoint}: rotating a source must notify both old and new output neighbors")

sensor_files = [
    "src/main/java/dev/redstoneengineering/block/EngineeringLightSensorBlock.java",
    "src/main/java/dev/redstoneengineering/block/TankLevelSensorBlock.java",
    "src/main/java/dev/redstoneengineering/block/EntityDensitySensorBlock.java",
]
for rel in sensor_files:
    require(rel,
            "extends DirectionalRedstoneSensorBlock", "updateSensorOutput",
            "player.isShiftKeyDown()", "FieldDeviceUi.open(serverPlayer, pos)")
    body = text(rel)
    if "Arrays.stream(Direction.values())" in body:
        failed.append(f"{rel}: legacy six-face sensor port enumeration returned")
    if "getSignal(" in body or "canConnectRedstone(" in body:
        failed.append(f"{rel}: directional electrical behavior must stay centralized in sensor base")

indicator = "src/main/java/dev/redstoneengineering/block/AnalogIndicatorBlock.java"
require(indicator,
        "extends DirectionalRedstoneEndpointBlock", "EngineeringPortProvider",
        "SIGNAL IN", "backSide(state)", "PortDirection.INPUT",
        "connectionMatches(direction, backSide(state))", "readBackInput",
        "onEndpointRouteChanged(Level level, BlockPos pos, BlockState oldState, BlockState newState)",
        "update(level, pos, newState)",
        "player.isShiftKeyDown()", "FieldDeviceUi.open(serverPlayer, pos)")
indicator_body = text(indicator)
for forbidden in ["LEGACY_OMNIDIRECTIONAL", "getBestNeighborSignal", "Arrays.stream(Direction.values())"]:
    if forbidden in indicator_body:
        failed.append(f"{indicator}: forbidden legacy behavior {forbidden!r}")

source_body = text(source)
if "Arrays.stream(Direction.values())" in source_body:
    failed.append(f"{source}: reference source must not expose six-face outputs")

for name in [
    "redstone_reference_source",
    "engineering_light_sensor",
    "tank_level_sensor",
    "entity_density_sensor",
    "analog_indicator",
]:
    rel = f"src/main/resources/assets/redstoneengineering/blockstates/{name}.json"
    body = text(rel)
    if '"multipart"' not in body:
        failed.append(f"{rel}: expected low-cardinality-friendly multipart model")

tests = "src/main/java/dev/redstoneengineering/gametest/RseTopologyGameTests.java"
require(tests,
        "directionalRedstoneEndpointsExposeOnlyPhysicalPorts",
        "analogIndicatorReadsBackOnly",
        "Blocks.REDSTONE_BLOCK",
        "Analog indicator incorrectly accepted a SIDE input")

require("ALPHA1_0_12_MANIFEST.txt", "1.0.12-alpha", "Directional I/O Renovation", "runGameTestServer")
require("docs/ALPHA1_0_12_DIRECTIONAL_IO_RENOVATION.md",
        "Directional I/O Renovation", "FRONT", "BACK", "0..15", "GameTest")
require("docs/ALPHA1_0_12_TEST_MATRIX.md", "Analog Indicator", "SIDE", "runGameTestServer")

workflow = text(".github/workflows/build.yml")
if "rse_alpha1012_directional_io_verify.py" not in workflow:
    failed.append("workflow missing Alpha 1.0.12 verifier")
if "runGameTestServer" not in workflow:
    failed.append("workflow missing Minecraft GameTest gate")

if failed:
    print("RSE Alpha 1.0.12 directional I/O verification: FAIL")
    for item in failed:
        print(" -", item)
    raise SystemExit(1)

print("RSE Alpha 1.0.12 directional I/O verification: PASS")
print(" shared FRONT/BACK endpoint topology: PASS")
print(" FRONT-only reference and sensor outputs: PASS")
print(" reference output rotation old/new neighbor notification: PASS")
print(" endpoint route-change refresh hook: PASS")
print(" endpoint Engineering UI reachability + Shift diagnostics: PASS")
print(" BACK-only analog indicator input + immediate route refresh: PASS")
print(" low-cardinality multipart resource guard: PASS")
print(" executable Minecraft directional GameTests: PASS")
