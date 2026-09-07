#!/usr/bin/env python3
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/redstoneengineering"
JAVA = ROOT / "src/main/java/dev/redstoneengineering"
DIRECTIONS = ("north", "east", "south", "west", "up", "down")


def load(path: Path):
    return json.loads(path.read_text())


def require(condition: bool, message: str):
    if not condition:
        raise SystemExit(f"FAIL: {message}")


def verify_variant(name: str, texture: str):
    state = load(ASSETS / "blockstates" / f"{name}.json")
    parts = state.get("multipart", [])
    require(len(parts) == 7, f"{name} must render one center plus six conditional arms")
    require(parts[0].get("apply", {}).get("model") == f"redstoneengineering:block/{name}_center",
            f"{name} center model must always render")
    for direction in DIRECTIONS:
        matches = [part for part in parts if part.get("when") == {direction: "true"}]
        require(len(matches) == 1, f"{name} needs exactly one {direction}=true arm")
        require(matches[0].get("apply", {}).get("model") == f"redstoneengineering:block/{name}_{direction}",
                f"{name} {direction} arm must use the matching directional model")

    geometry_names = ("center",) + DIRECTIONS
    for geometry in geometry_names:
        model = load(ASSETS / "models/block" / f"{name}_{geometry}.json")
        expected_parent = f"redstoneengineering:block/redstone_signal_cable_{geometry}"
        require(model.get("parent") == expected_parent,
                f"{name}_{geometry} must reuse proven six-direction cable geometry")
        require(model.get("textures", {}).get("line") == f"redstoneengineering:block/{texture}",
                f"{name}_{geometry} must retain its own cable texture")

    item_backing = load(ASSETS / "models/block" / f"{name}.json")
    require(item_backing.get("parent") != "minecraft:block/cube_all", f"{name} item preview must not be a full cube")
    require(len(item_backing.get("elements", [])) == 3,
            f"{name} item preview must be a center plus two straight arms")


verify_variant("instrument_cable", "instrument_cable")
verify_variant("shielded_instrument_cable", "shielded_instrument_cable")

connected = (JAVA / "block/ConnectedCableBlock.java").read_text()
instrument = (JAVA / "block/InstrumentCableBlock.java").read_text()
network = (JAVA / "instrument/InstrumentNetwork.java").read_text()
for direction in ("NORTH", "EAST", "SOUTH", "WEST", "UP", "DOWN"):
    require(f"BooleanProperty.create(\"{direction.lower()}\")" in connected,
            f"ConnectedCableBlock must keep the {direction} topology state")
require("protected int maxConnections() { return 6; }" in instrument,
        "Instrument Cable must allow six-direction branching")
require("ConnectedCableBlock.connected(cableState, direction)" in network,
        "InstrumentNetwork traversal must follow visible cable arms")
require("ConnectedCableBlock.connected(state, direction.getOpposite())" in network,
        "InstrumentNetwork traversal must require reciprocal physical connection")

print("RSE instrument cable visual topology verification: PASS")
print("  plain + shielded cables: center + six conditional arms")
print("  horizontal turns, vertical risers, T/cross and full six-way nodes: representable")
print("  render topology and InstrumentNetwork traversal share the same six BlockState edges")
print("  inventory preview is cable-shaped rather than fixed cross/cube")
