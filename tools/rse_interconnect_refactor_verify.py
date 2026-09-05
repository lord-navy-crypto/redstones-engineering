#!/usr/bin/env python3
"""Static regression gate for the early-interconnect engineering refactor."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/dev/redstoneengineering"
ASSETS = ROOT / "src/main/resources/assets/redstoneengineering"


def text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def require(path: Path, *needles: str) -> None:
    data = text(path)
    missing = [needle for needle in needles if needle not in data]
    if missing:
        raise SystemExit(f"{path.relative_to(ROOT)} missing required contract tokens: {missing}")


# Insulated-redstone cable and junction must advertise the same real 0..15 medium
# and must independently solve every component created by a cut.
require(
    JAVA / "block/RedstoneSignalCableBlock.java",
    "PortKind.REDSTONE_ANALOG",
    "RedstoneCableNetwork.recomputeAround(serverLevel, pos)",
)
require(
    JAVA / "block/RedstoneCableJunctionBlock.java",
    "PortKind.REDSTONE_ANALOG",
    "RedstoneCableNetwork.recomputeAround(server, pos)",
)
require(
    JAVA / "physics/RedstoneCableNetwork.java",
    "public static void recomputeAround",
    "recomputeComponent(level, component)",
    "processed.addAll(component)",
)

# Surface-trace diagnostics must describe actual topology, not four imaginary ports.
for name in ("LapisSignalLineBlock.java", "QuartzTimingLineBlock.java", "AmethystResonanceDustBlock.java"):
    require(
        JAVA / f"block/{name}",
        "SurfaceTraceBlock.connected",
        "Direction.Plane.HORIZONTAL",
    )

# Cutting resonance dust explicitly resolves every neighboring component.
require(
    JAVA / "block/AmethystResonanceDustBlock.java",
    "private static void recomputeAround",
    "DomainNetwork.recomputeAmethyst(level, neighbor)",
)

# Optical splice is a serviceable two-ended isolation point, never a hidden splitter.
require(
    JAVA / "block/OpticalFiberJunctionBlock.java",
    'BooleanProperty.create("service_open")',
    "SERVICE_OPEN",
    "public void setServiceOpen",
    "SERVICE OPEN — segments isolated",
    "DomainNetwork.recomputeOpticalAround(server, pos)",
)
require(
    JAVA / "block/TransmissionTopology.java",
    "b instanceof OpticalFiberJunctionBlock",
    "!s.getValue(OpticalFiberJunctionBlock.SERVICE_OPEN)",
)

# The two highest-risk behavioral changes have executable in-world regressions.
require(
    JAVA / "gametest/RseInterconnectRefactorGameTests.java",
    "void insulatedRedstoneCutClearsSeparatedOutput",
    "void opticalServiceSpliceIsolatesAndRestores",
    "RedstoneCableTerminalBlock.POWER",
    "setServiceOpen",
)
require(
    JAVA / "gametest/RseGameTestRegistration.java",
    "event.register(RseInterconnectRefactorGameTests.class);",
)

# Diamond shard must no longer be the full vanilla diamond icon.
model_path = ASSETS / "models/item/diamond_shard.json"
model = json.loads(text(model_path))
if model.get("textures", {}).get("layer0") == "minecraft:item/diamond":
    raise SystemExit("diamond_shard.json still reuses the full vanilla diamond icon")
if len(model.get("elements", [])) < 2:
    raise SystemExit("diamond_shard.json must use a distinct small-fragment geometry")
if model.get("display", {}).get("gui", {}).get("scale", [1])[0] >= 1:
    raise SystemExit("diamond shard GUI model should remain visibly smaller than a full item")

print("RSE interconnect refactor verification: PASS")
