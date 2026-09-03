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
if not match or tuple(map(int, match.groups())) < (1, 0, 10):
    failed.append("Alpha 1.0.10 requires mod_version >= 1.0.10-alpha")

require("src/main/java/dev/redstoneengineering/core/port/EngineeringPort.java",
        "EngineeringDomain", "String unit", "canReceive", "canTransmit", "EngineeringPortSnapshot")
require("src/main/java/dev/redstoneengineering/core/port/PortQuality.java",
        "VALID", "SATURATED", "STALE", "FAULT", "DOMAIN_MISMATCH", "TOPOLOGY_ERROR")
require("src/main/java/dev/redstoneengineering/core/port/EngineeringPortSnapshot.java",
        "double value", "normalized()", "redstone(", "0.0, 15.0")
require("src/main/java/dev/redstoneengineering/core/port/EngineeringPortProvider.java",
        "engineeringPorts", "engineeringPort", "engineeringSnapshot")
require("src/main/java/dev/redstoneengineering/core/port/PortCompatibility.java",
        "COMPATIBLE", "DOMAIN_MISMATCH", "DIRECTION_MISMATCH", "evaluate")
require("src/main/java/dev/redstoneengineering/core/domain/EngineeringDomain.java",
        "INSULATED_REDSTONE", "INSTRUMENT_BUS", "PNEUMATIC")

# Representative migrations may implement the contract directly or inherit it
# from a deliberately shared base introduced by a later milestone. The historical
# Alpha 1.0.10 invariant is that these devices expose the EngineeringPort contract,
# not that every leaf class duplicates the same methods forever.
for rel in [
    "src/main/java/dev/redstoneengineering/block/DirectionalSignalBlock.java",
    "src/main/java/dev/redstoneengineering/block/AnalogIndicatorBlock.java",
    "src/main/java/dev/redstoneengineering/block/RedstoneSignalCableBlock.java",
    "src/main/java/dev/redstoneengineering/block/InstrumentCableBlock.java",
]:
    require(rel, "EngineeringPortProvider", "engineeringPorts")

sensor_base = "src/main/java/dev/redstoneengineering/block/DirectionalRedstoneSensorBlock.java"
if (root / sensor_base).exists():
    require(sensor_base, "EngineeringPortProvider", "engineeringPorts", "engineeringSnapshot")
    for rel in [
        "src/main/java/dev/redstoneengineering/block/EngineeringLightSensorBlock.java",
        "src/main/java/dev/redstoneengineering/block/EntityDensitySensorBlock.java",
        "src/main/java/dev/redstoneengineering/block/TankLevelSensorBlock.java",
    ]:
        require(rel, "extends DirectionalRedstoneSensorBlock")
else:
    for rel in [
        "src/main/java/dev/redstoneengineering/block/EngineeringLightSensorBlock.java",
        "src/main/java/dev/redstoneengineering/block/EntityDensitySensorBlock.java",
        "src/main/java/dev/redstoneengineering/block/TankLevelSensorBlock.java",
    ]:
        require(rel, "EngineeringPortProvider", "engineeringPorts")

require("src/main/java/dev/redstoneengineering/block/DirectionalSignalBlock.java",
        "\"INPUT\"", "\"OUTPUT\"", "PortDirection.INPUT", "PortDirection.OUTPUT", "engineeringSnapshot")
require("src/main/java/dev/redstoneengineering/block/RedstoneSignalCableBlock.java",
        "PortDirection.BIDIRECTIONAL", "EngineeringDomain.REDSTONE", "engineeringPorts=")
require("src/main/java/dev/redstoneengineering/block/InstrumentCableBlock.java",
        "EngineeringDomain.INSTRUMENT_BUS", "PortKind.BUS", "PortDirection.BIDIRECTIONAL")

forbidden_imports = [
    "mezz.jei",
    "snownee.jade",
    "software.bernie.geckolib",
    "me.shedaniel.cloth",
    "com.supermartijn642.fusion",
]
for subtree in ["core", "physics", "signal"]:
    base = root / "src/main/java/dev/redstoneengineering" / subtree
    for java in base.rglob("*.java"):
        body = java.read_text(errors="ignore")
        for token in forbidden_imports:
            if token in body:
                failed.append(f"core boundary violation: {java.relative_to(root)} imports {token}")

build = text("build.gradle")
for token in ["mezz.jei", "nvQzSEkH", "geckolib-neoforge", "cloth-config-neoforge", "fusion-connected-textures"]:
    if token not in build:
        failed.append(f"required dependency disappeared from build.gradle: {token}")

# Historical release evidence belongs in the historical manifest/documentation.
# The README is allowed to advance to the current Alpha instead of permanently
# retaining every old artifact-version literal.
require("ALPHA1_0_10_MANIFEST.txt", "1.0.10-alpha")
require("docs/ALPHA1_0_10_ENGINEERING_PORT_ARCHITECTURE.md", "Engineering Port")
readme = text("README.md")
for token in ["Alpha 1.0.10", "Engineering Port Contract"]:
    if token not in readme:
        failed.append(f"README missing historical architecture reference {token}")

workflow = text(".github/workflows/build.yml")
if "rse_alpha1010_port_architecture_verify.py" not in workflow:
    failed.append("workflow missing Alpha 1.0.10 verifier")

if failed:
    print("RSE Alpha 1.0.10 engineering-port architecture verification: FAIL")
    for item in failed:
        print(" -", item)
    raise SystemExit(1)

print("RSE Alpha 1.0.10 engineering-port architecture verification: PASS")
print(" static port descriptor + runtime snapshot separation: PASS")
print(" domain/direction compatibility model: PASS")
print(" representative legacy migration/inheritance: PASS")
print(" required-dependency core boundary: PASS")
print(" forward-compatible historical documentation gate: PASS")
