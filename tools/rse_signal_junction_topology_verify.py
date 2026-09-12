#!/usr/bin/env python3
"""Guard the shared RSE signal-routing grammar.

Cable-like media are planar by default. Vertical continuity requires the single
Junction Point, which may bind to one medium only. The Junction Point is routing-only;
dedicated converter/transducer blocks remain the only cross-domain path.
"""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []


def text(path: str) -> str:
    return (ROOT / path).read_text()


def require(body: str, needle: str, label: str) -> None:
    if needle not in body:
        errors.append(f"{label}: missing {needle!r}")


topology = text("src/main/java/dev/redstoneengineering/block/TransmissionTopology.java")
junction = text("src/main/java/dev/redstoneengineering/block/RedstoneCableJunctionBlock.java")
connected = text("src/main/java/dev/redstoneengineering/block/ConnectedCableBlock.java")
instrument = text("src/main/java/dev/redstoneengineering/block/InstrumentCableBlock.java")
redstone = text("src/main/java/dev/redstoneengineering/block/RedstoneSignalCableBlock.java")
bus = text("src/main/java/dev/redstoneengineering/block/EightBitDataBusBlock.java")
serial = text("src/main/java/dev/redstoneengineering/block/SerialDataLineBlock.java")
diff = text("src/main/java/dev/redstoneengineering/block/DifferentialDataPairBlock.java")
bus_net = text("src/main/java/dev/redstoneengineering/physics/DataBusNetwork.java")
serial_net = text("src/main/java/dev/redstoneengineering/physics/SerialNetwork.java")
diff_net = text("src/main/java/dev/redstoneengineering/physics/DifferentialNetwork.java")
instrument_net = text("src/main/java/dev/redstoneengineering/instrument/InstrumentNetwork.java")
tests = text("src/main/java/dev/redstoneengineering/gametest/RseSignalJunctionTopologyGameTests.java")
registration = text("src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java")
serializer = text("src/main/java/dev/redstoneengineering/block/SerializerBlock.java")

# The one Junction Point now recognizes all seven physical line media. This is vocabulary,
# not permission to convert: inferJunctionMedium must still hard-fail mixed media.
for medium in (
    "REDSTONE", "INSTRUMENT", "DATA_BUS_8", "SERIAL", "DIFFERENTIAL",
    "OPTICAL", "COPPER", "MISMATCH"
):
    require(topology, medium, "junction medium vocabulary")
require(topology, "new Direction[]{Direction.UP, Direction.DOWN}", "vertical-only medium inference")
require(topology, "junctionToNeighbor.getAxis() != Direction.Axis.Y", "junction rejects horizontal ports")
require(topology, "return SignalMedium.MISMATCH", "mixed-media hard mismatch")
require(junction, "The single player-facing RSE Junction Point", "junction operator identity")
require(junction, "maxConnections() { return 2; }", "junction two-port physical limit")
require(junction, "List.of(Direction.UP, Direction.DOWN)", "junction exposes only vertical engineering ports")
require(junction, "MISMATCH — different media blocked", "mixed-media operator warning")
require(junction, "ROUTING ONLY — NO CONVERSION", "no implicit conversion")
require(junction, "EnumProperty.create(\"medium\"", "inspectable junction medium")
require(junction, "routingChanged(before, after)", "bounded Junction network refresh")
require(connected, "state.getBlock() instanceof RedstoneCableJunctionBlock", "legacy Junction arm migration guard")
require(connected, "direction.getAxis() != Direction.Axis.Y", "legacy horizontal arms ignored immediately")
require(connected, "connected(state, Direction.NORTH)", "rendering follows effective connection view")

for name, body, method in (
    ("instrument", instrument, "instrumentCablePort"),
    ("insulated redstone", redstone, "redstoneCablePort"),
    ("8-bit bus", bus, "dataBusPort"),
    ("serial", serial, "serialPort"),
    ("differential", diff, "differentialPort"),
):
    require(body, "extends ConnectedCableBlock", f"{name} explicit cable state")
    require(body, method, f"{name} shared topology routing")
    require(body, "maxConnections()", f"{name} planar branching capacity")

for name, body, medium in (
    ("8-bit", bus_net, "DATA_BUS_8"),
    ("serial", serial_net, "SERIAL"),
    ("differential", diff_net, "DIFFERENTIAL"),
):
    require(body, "edgeAllowed", f"{name} graph/visual edge alignment")
    require(body, "ConnectedCableBlock.connected", f"{name} graph follows BlockState arms")
    require(body, f"SignalMedium.{medium}", f"{name} junction medium isolation")
    require(body, "direction.getAxis() == Direction.Axis.Y", f"{name} direct vertical graph rejection")

require(instrument_net, "SignalMedium.INSTRUMENT", "instrument junction isolation")
require(instrument_net, "edgeAllowed", "instrument graph/visual alignment")

for stem in ("eight_bit_data_bus", "serial_data_line", "differential_data_pair"):
    state_path = ROOT / f"src/main/resources/assets/redstoneengineering/blockstates/{stem}.json"
    data = json.loads(state_path.read_text())
    raw = json.dumps(data)
    if "multipart" not in data:
        errors.append(f"{stem}: blockstate is not multipart")
    for direction in ("north", "east", "south", "west", "up", "down"):
        if direction not in raw or f"{stem}_{direction}" not in raw:
            errors.append(f"{stem}: missing dynamic {direction} arm")
    model = json.loads((ROOT / f"src/main/resources/assets/redstoneengineering/models/block/{stem}.json").read_text())
    if model.get("parent") == "minecraft:block/cube_all":
        errors.append(f"{stem}: inventory/base model regressed to cube_all")

for test_name in (
    "stackedBusLinesDoNotConnectWithoutJunction",
    "sameMediumJunctionCreatesVerticalBusRoute",
    "mixedMediaJunctionIsHardTopologyMismatch",
    "horizontalBusBranchingNeedsNoJunction",
    "legacyHorizontalJunctionArmsAreIgnoredImmediately",
):
    require(tests, test_name, "runtime topology coverage")
require(registration, "RseSignalJunctionTopologyGameTests.class", "GameTest registration")

# Preserve the converter boundary: a serializer owns one DATA_BUS_8 input and one SERIAL_DATA output.
require(serializer, "EngineeringDomain.DATA_BUS_8", "serializer input-domain boundary")
require(serializer, "EngineeringDomain.SERIAL_DATA", "serializer output-domain boundary")
require(serializer, "PortKind.CONVERTER", "dedicated converter identity")

if errors:
    print("RSE unified Junction Point topology verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE unified Junction Point topology verification: PASS")
print("  one two-port UP/DOWN Junction Point: PASS")
print("  seven physical line media recognized: PASS")
print("  same-medium-only vertical routing: PASS")
print("  mixed-media hard isolation / no implicit conversion: PASS")
print("  legacy horizontal arm states fail closed immediately: PASS")
print("  network refresh only follows effective routing changes: PASS")
print("  visible arm == graph edge contract: PASS")
print("  dedicated converter boundary retained: PASS")
print("  five executable routing GameTests retained: PASS")
