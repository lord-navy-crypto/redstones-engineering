#!/usr/bin/env python3
"""Fail-closed guard for the dedicated server-authoritative Copper electrical HMI."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []


def text(path: str) -> str:
    return (ROOT / path).read_text()


def require(body: str, needle: str, label: str) -> None:
    if needle not in body:
        errors.append(f"{label}: missing {needle!r}")


def forbid(body: str, needle: str, label: str) -> None:
    if needle in body:
        errors.append(f"{label}: forbidden {needle!r}")


block = text("src/main/java/dev/redstoneengineering/block/CopperCircuitMeterBlock.java")
menu = text("src/main/java/dev/redstoneengineering/ui/menu/CopperCircuitMeterMenu.java")
screen = text("src/main/java/dev/redstoneengineering/client/ui/CopperCircuitMeterScreen.java")
registration = text("src/main/java/dev/redstoneengineering/ui/EngineeringUiRegistration.java")
client_registration = text("src/main/java/dev/redstoneengineering/client/ui/EngineeringUiClientRegistration.java")
openers = text("src/main/java/dev/redstoneengineering/ui/FieldDeviceUi.java")

require(block, "record ElectricalDiagnostics", "shared electrical evidence")
require(block, "CircuitPhysics.equivalentLoadResistance", "server load model")
require(block, "CircuitPhysics.current", "server current model")
require(block, "CircuitPhysics.power", "server power model")
require(block, "FieldDeviceUi.open(serverPlayer, pos)", "dedicated HMI opener")

require(menu, "CopperCircuitMeterBlock.electricalDiagnostics", "menu server evidence")
require(menu, "CommissioningStatus", "commissioning contract")
require(menu, "commissioningStatus.set", "server commissioning synchronization")
require(menu, "rotateMeasurementFace", "real measurement-face routing")

require(screen, "COPPER POWER / LOAD NETWORK", "Copper medium identity")
require(screen, "SERVER-SYNCHRONIZED OBSERVER", "observer authority")
require(screen, "V, Req, I and P", "electrical telemetry explanation")
require(screen, "commissioningStatus()", "commissioning presentation")
forbid(screen, "CircuitPhysics", "client must not solve circuits")
forbid(screen, "DomainNetwork", "client must not sample network physics")

require(registration, "COPPER_CIRCUIT_METER", "menu registration")
require(client_registration, "CopperCircuitMeterScreen::new", "screen registration")
require(openers, "new CopperCircuitMeterMenu", "dedicated opener routing")

if errors:
    print("RSE Copper HMI verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE Copper HMI verification: PASS")
print("  server-authoritative V/Req/I/P evidence: PASS")
print("  dedicated power/load identity: PASS")
print("  commissioning synchronization: PASS")
print("  client physics isolation: PASS")
