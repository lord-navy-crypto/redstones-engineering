#!/usr/bin/env python3
"""Static gate for the twelfth guided-optical integrity campaign."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


targets = {
    "OpticalFiberBlock.java": ("implements EngineeringPortProvider", "EngineeringDomain.OPTICAL", "RuntimeIntStore.peek", "recomputeOpticalAround"),
    "OpticalEmitterBlock.java": ("implements EngineeringPortProvider", "PortDirection.OUTPUT", "EngineeringDomain.OPTICAL", "recomputeOpticalAround"),
    "OpticalReceiverBlock.java": ("implements EngineeringPortProvider", "PortDirection.INPUT", "RuntimeIntStore.peek", "recomputeOpticalAround"),
    "OpticalPowerMeterBlock.java": ("implements EngineeringPortProvider", "OPTICAL POWER INPUT", "DomainNetwork.sampleOptical"),
    "OpticalSplitterBlock.java": ("implements EngineeringPortProvider", "OPTICAL OUTPUT A", "OPTICAL OUTPUT B", "recomputeOpticalAround"),
    "OpticalChannelFilterBlock.java": ("implements EngineeringPortProvider", "OPTICAL FILTERED OUTPUT", "TARGET", "recomputeOpticalAround"),
    "OpticalAttenuatorBlock.java": ("implements EngineeringPortProvider", "OPTICAL ATTENUATED OUTPUT", "LOSS", "recomputeOpticalAround"),
    # A junction is now an explicit two-ended service splice rather than a
    # cosmetic duplicate of fiber. It may isolate both sides, but never branch.
    "OpticalFiberJunctionBlock.java": (
        "implements EngineeringPortProvider",
        "OPTICAL SERVICE SPLICE",
        "SERVICE_OPEN",
        "RuntimeIntStore.peek",
        "recomputeOpticalAround",
    ),
}

for name, needles in targets.items():
    body = read(f"src/main/java/dev/redstoneengineering/block/{name}")
    missing = [needle for needle in needles if needle not in body]
    if missing:
        raise SystemExit(f"{name}: missing {missing}")

network = read("src/main/java/dev/redstoneengineering/physics/DomainNetwork.java")
for needle in ("recomputeOpticalAround", "opticalEdgeAllowed", "addRawOpticalClaims"):
    if needle not in network:
        raise SystemExit(f"DomainNetwork missing {needle}")

topology = read("src/main/java/dev/redstoneengineering/block/TransmissionTopology.java")
for needle in (
    "b instanceof OpticalFiberJunctionBlock",
    "!s.getValue(OpticalFiberJunctionBlock.SERVICE_OPEN)",
):
    if needle not in topology:
        raise SystemExit(f"TransmissionTopology missing optical splice isolation contract {needle}")

menu = read("src/main/java/dev/redstoneengineering/ui/menu/FieldDeviceMenu.java")
screen = read("src/main/java/dev/redstoneengineering/client/ui/FieldDeviceScreen.java")
for needle in ("KIND_OPTICAL_FIBER", "KIND_OPTICAL_EMITTER", "KIND_OPTICAL_RECEIVER", "KIND_OPTICAL_POWER_METER", "KIND_OPTICAL_SPLITTER", "KIND_OPTICAL_CHANNEL_FILTER", "KIND_OPTICAL_ATTENUATOR", "KIND_OPTICAL_FIBER_JUNCTION"):
    if needle not in menu or needle not in screen:
        raise SystemExit(f"UI missing {needle}")

tests = read("src/main/java/dev/redstoneengineering/gametest/RseTwelfthEightAcceptanceGameTests.java")
if tests.count("@GameTest(") != 8:
    raise SystemExit(f"expected 8 twelfth GameTests, found {tests.count('@GameTest(')}")
registration = read("src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java")
if "RseTwelfthEightAcceptanceGameTests.class" not in registration:
    raise SystemExit("twelfth GameTests are not registered")

print("rse_twelfth_eight_verify: PASS")
