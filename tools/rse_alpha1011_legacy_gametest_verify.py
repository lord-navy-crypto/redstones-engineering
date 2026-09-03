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
if not match or tuple(map(int, match.groups())) < (1, 0, 11):
    failed.append("Alpha 1.0.11 requires mod_version >= 1.0.11-alpha")

require("src/main/java/dev/redstoneengineering/core/port/PortKind.java", "CONVERTER")

for rel in [
    "src/main/java/dev/redstoneengineering/block/RedstoneCableTerminalBlock.java",
    "src/main/java/dev/redstoneengineering/block/RedstoneCableJunctionBlock.java",
    "src/main/java/dev/redstoneengineering/block/CopperCableJunctionBlock.java",
    "src/main/java/dev/redstoneengineering/block/AbstractLapisTransducerBlock.java",
    "src/main/java/dev/redstoneengineering/block/RedstoneToLapisScalerBlock.java",
    "src/main/java/dev/redstoneengineering/block/LapisToRedstoneQuantizerBlock.java",
]:
    require(rel, "EngineeringPortProvider", "engineeringPorts", "engineeringSnapshot")

require("src/main/java/dev/redstoneengineering/block/RedstoneCableTerminalBlock.java",
        "VANILLA IN", "VANILLA OUT", "CABLE IN", "CABLE OUT", "PortKind.CONVERTER")
require("src/main/java/dev/redstoneengineering/block/RedstoneCableJunctionBlock.java",
        "EngineeringDomain.REDSTONE", "PortKind.BUS", "PortDirection.BIDIRECTIONAL")
require("src/main/java/dev/redstoneengineering/block/CopperCableJunctionBlock.java",
        "EngineeringDomain.COPPER", "PortKind.BUS", "PortDirection.BIDIRECTIONAL")
require("src/main/java/dev/redstoneengineering/block/AbstractLapisTransducerBlock.java",
        "EngineeringDomain.LAPIS", "PortKind.SENSOR", "PortQuality.NO_SIGNAL")
require("src/main/java/dev/redstoneengineering/block/RedstoneToLapisScalerBlock.java",
        "REDSTONE INPUT", "LAPIS OUTPUT", "EngineeringDomain.REDSTONE", "EngineeringDomain.LAPIS")
require("src/main/java/dev/redstoneengineering/block/LapisToRedstoneQuantizerBlock.java",
        "LAPIS INPUT", "REDSTONE OUTPUT", "EngineeringDomain.LAPIS", "EngineeringDomain.REDSTONE")

registration = "src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java"
tests = "src/main/java/dev/redstoneengineering/gametest/RseTopologyGameTests.java"
require(registration, "RegisterGameTestsEvent", "event.register(RseTopologyGameTests.class)", "Bus.MOD")
require(tests,
        "@GameTest", "PrefixGameTestTemplate(false)", "empty5x4x5",
        "redstoneCableConnectsToRedstoneJunction",
        "redstoneCableRejectsCopperJunction",
        "terminalDirectionFollowsMode",
        "explicitConvertersBridgeDomains")

# Minecraft 1.21.x loads structure templates from data/<namespace>/structure (singular).
template = root / "src/main/resources/data/redstoneengineering/structure/empty5x4x5.nbt"
legacy_template = root / "src/main/resources/data/redstoneengineering/structures/empty5x4x5.nbt"
if not template.exists():
    failed.append("missing GameTest template at singular structure path")
else:
    data = template.read_bytes()
    if len(data) < 64:
        failed.append("GameTest template is unexpectedly small")
    if data[:2] != b"\x1f\x8b":
        failed.append("GameTest template is not gzip-compressed NBT")
if legacy_template.exists():
    failed.append("legacy plural structures/ GameTest path must not remain")

require("ALPHA1_0_11_MANIFEST.txt", "1.0.11-alpha", "runGameTestServer", "Topology GameTests")
require("docs/ALPHA1_0_11_LEGACY_RENOVATION_AND_GAMETEST.md",
        "Legacy Renovation Wave II", "RegisterGameTestsEvent", "runGameTestServer", "Copper")

workflow = text(".github/workflows/build.yml")
if "rse_alpha1011_legacy_gametest_verify.py" not in workflow:
    failed.append("workflow missing Alpha 1.0.11 verifier")
if "runGameTestServer" not in workflow:
    failed.append("workflow missing executable GameTest server gate")

if failed:
    print("RSE Alpha 1.0.11 legacy renovation + GameTest verification: FAIL")
    for item in failed:
        print(" -", item)
    raise SystemExit(1)

print("RSE Alpha 1.0.11 legacy renovation + GameTest verification: PASS")
print(" terminal/junction/transducer/converter migration: PASS")
print(" domain-specific converter ports: PASS")
print(" singular Minecraft structure resource path: PASS")
print(" executable topology GameTest registration: PASS")
print(" CI runGameTestServer gate: PASS")
