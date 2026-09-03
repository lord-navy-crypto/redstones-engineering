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
if not match or tuple(map(int, match.groups())) < (1, 0, 13):
    failed.append("Alpha 1.0.13 requires mod_version >= 1.0.13-alpha")

port_kind = "src/main/java/dev/redstoneengineering/core/port/PortKind.java"
require(port_kind, "ELECTRICAL")

base = "src/main/java/dev/redstoneengineering/block/DirectionalCopperProcessorBlock.java"
require(
    base,
    "extends DirectionalDomainBlock implements EngineeringPortProvider",
    '"INPUT"',
    '"OUTPUT"',
    "EngineeringDomain.COPPER",
    "PortKind.ELECTRICAL",
    "PortDirection.INPUT",
    "PortDirection.OUTPUT",
    "inputSide(state)",
    "outputSide(state)",
    "DomainNetwork.sampleCopperVoltage",
    "observedOutputVoltage",
)

for rel in [
    "src/main/java/dev/redstoneengineering/block/CopperSeriesResistorBlock.java",
    "src/main/java/dev/redstoneengineering/block/CopperCapacitorBlock.java",
    "src/main/java/dev/redstoneengineering/block/CopperFuseBlock.java",
]:
    require(rel, "extends DirectionalCopperProcessorBlock", "observedOutputVoltage")
    body = text(rel)
    if "implements EngineeringPortProvider" in body:
        failed.append(f"{rel}: duplicated port-provider implementation should remain in shared copper base")

require(
    "src/main/java/dev/redstoneengineering/block/CopperVoltageSourceBlock.java",
    "implements EngineeringPortProvider",
    "EngineeringDomain.COPPER",
    "PortKind.ELECTRICAL",
    "PortDirection.OUTPUT",
    "Direction.values()",
)
require(
    "src/main/java/dev/redstoneengineering/block/CopperResistiveLoadBlock.java",
    "implements EngineeringPortProvider",
    "EngineeringDomain.COPPER",
    "PortKind.ELECTRICAL",
    "PortDirection.INPUT",
    "terminal sink",
)
require(
    "src/main/java/dev/redstoneengineering/block/CopperCircuitMeterBlock.java",
    "implements EngineeringPortProvider",
    '"MEASURE"',
    "PortKind.MEASUREMENT",
    "state.getValue(FACING)",
    "DomainNetwork.sampleCopperVoltage",
)

# Runtime tests, not source-only promises.
tests = "src/main/java/dev/redstoneengineering/gametest/RseCopperGameTests.java"
require(
    tests,
    "axialCopperProcessorsExposeBackAndFrontPorts",
    "seriesResistorPropagatesAttenuatedVoltage",
    "fuseTripsAndCutsProtectedOutput",
    "seriesResistorRejectsSideFeed",
    "DomainNetwork.sampleCopperVoltage",
    "CopperFuseBlock.TRIPPED",
)
require(
    "src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java",
    "event.register(RseTopologyGameTests.class)",
    "event.register(RseCopperGameTests.class)",
)

# Preserve simulation ownership: UI/rendering libraries must not define copper physics.
for rel in [
    base,
    "src/main/java/dev/redstoneengineering/block/CopperSeriesResistorBlock.java",
    "src/main/java/dev/redstoneengineering/block/CopperCapacitorBlock.java",
    "src/main/java/dev/redstoneengineering/block/CopperFuseBlock.java",
]:
    body = text(rel)
    for forbidden in ["snownee.jade", "software.bernie.geckolib", "fusion"]:
        if forbidden in body:
            failed.append(f"{rel}: simulation/core layer leaked dependency API {forbidden!r}")

require("ALPHA1_0_13_MANIFEST.txt", "1.0.13-alpha", "Copper Circuit Topology", "runGameTestServer")
require("docs/ALPHA1_0_13_COPPER_TOPOLOGY_RENOVATION.md", "BACK", "FRONT", "DomainNetwork", "GameTest")
require("docs/ALPHA1_0_13_TEST_MATRIX.md", "Series runtime propagation", "Fuse safety", "Side isolation")

workflow = text(".github/workflows/build.yml")
if "rse_alpha1013_copper_topology_verify.py" not in workflow:
    failed.append("workflow missing Alpha 1.0.13 verifier")
if "runGameTestServer" not in workflow:
    failed.append("workflow missing Minecraft GameTest gate")

if failed:
    print("RSE Alpha 1.0.13 copper topology verification: FAIL")
    for item in failed:
        print(" -", item)
    raise SystemExit(1)

print("RSE Alpha 1.0.13 copper topology verification: PASS")
print(" axial copper BACK/FRONT contract: PASS")
print(" source/load/meter semantic ports: PASS")
print(" runtime propagation and fuse GameTests present: PASS")
print(" dependency ownership boundary: PASS")
