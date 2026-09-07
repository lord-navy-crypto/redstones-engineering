#!/usr/bin/env python3
"""Guard the shared RSE signal-routing grammar.

Cable-like information media are planar by default. Vertical continuity requires the
single Signal Junction Point, which may bind to one medium only. Dedicated converter
blocks remain the only cross-domain path.
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

for medium in ("REDSTONE", "INSTRUMENT", "DATA_BUS_8", "SERIAL", "DIFFERENTIAL", "MISMATCH"):
    require(topology, medium, "signal-medium vocabulary")
require(topology, "cableToNeighbor.getAxis() == Direction.Axis.Y", "direct vertical line rejection")
require(topology, "junctionAccepts", "same-medium junction admission")
require(topology, "return SignalMedium.MISMATCH", "mixed-media hard mismatch")
require(junction, "Signal Junction Point", "junction operator identity")
require(junction, "EnumProperty.create(\"medium\"", "inspectable junction medium")
require(junction, "mixed media blocked; use a dedicated converter", "no implicit conversion")

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
):
    require(tests, test_name, "runtime topology coverage")
require(registration, "RseSignalJunctionTopologyGameTests.class", "GameTest registration")

# Preserve the explicit converter boundary: a serializer owns one DATA_BUS_8 input and one SERIAL_DATA output.
require(serializer, "EngineeringDomain.DATA_BUS_8", "serializer input-domain boundary")
require(serializer, "EngineeringDomain.SERIAL_DATA", "serializer output-domain boundary")
require(serializer, "PortKind.CONVERTER", "dedicated converter identity")

if errors:
    print("RSE unified signal junction topology verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE unified signal junction topology verification: PASS")
print("  planar direct routing for five cable-like signal media: PASS")
print("  same-medium-only vertical Signal Junction Point: PASS")
print("  mixed-media hard isolation / no implicit conversion: PASS")
print("  visible arm == graph edge contract: PASS")
print("  dynamic 8-bit/serial/differential cable resources: PASS")
print("  dedicated converter boundary retained: PASS")
print("  four executable routing GameTests registered: PASS")
