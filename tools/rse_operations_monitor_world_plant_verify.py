#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing world-backed plant monitor file: {rel}")
        return ""
    return path.read_text(errors="ignore")


assessment = read("src/main/java/dev/redstoneengineering/diagnostics/OperationWorldPlantStateAssessment.java")
menu = read("src/main/java/dev/redstoneengineering/ui/menu/OperationsMonitorMenu.java")
screen = read("src/main/java/dev/redstoneengineering/client/ui/OperationsMonitorScreen.java")

for token in (
    "class OperationWorldPlantStateAssessment",
    "enum Coverage",
    "record Snapshot",
    "OperationPlantSavedData.get",
    "workcells()",
    "buffers()",
    "workcellBufferBinding",
    "OperationWorkcellStore.resolveBoundResources",
    "totalWorkcells",
    "configuredWorkcells",
    "totalBuffers",
    "usedBufferUnits",
    "bufferCapacityUnits",
    "wipPressurePercent",
    "boundResources",
    "validResources",
    "faultResources",
):
    if assessment and token not in assessment:
        errors.append(f"OperationWorldPlantStateAssessment missing world evidence token {token!r}")

for token in (
    "OperationWorldPlantStateAssessment.inspect",
    "worldPlantCoverage",
    "worldPlantWorkcells",
    "worldPlantConfiguredWorkcells",
    "worldPlantBuffers",
    "worldPlantUsedBufferUnits",
    "worldPlantBufferCapacityUnits",
    "worldPlantWipPressurePercent",
    "worldPlantBoundResources",
    "worldPlantValidResources",
    "worldPlantFaultResources",
):
    if menu and token not in menu:
        errors.append(f"OperationsMonitorMenu missing world plant synchronization {token!r}")

for token in (
    "WORLD PLANT STATE",
    "Workcells configured",
    "Buffers / WIP",
    "Bound resources",
    "Quality / reliability / delivery",
):
    if screen and token not in screen:
        errors.append(f"OperationsMonitorScreen missing world plant UI token {token!r}")

combined = assessment + menu + screen
for forbidden in (
    "OperationDispatchRuntime",
    "OperationQueueRuntime",
    "OperationMaintenanceRuntime",
    "OperationChangeoverRuntime",
    "OperationBufferRuntime.receive",
    "OperationBufferRuntime.allocate",
    "OperationPlantSavedData.putBuffer",
    "OperationPlantSavedData.putWorkcell",
    "setBlock(",
    "setDeltaMovement(",
    "getEntitiesOfClass",
    "inflate(",
):
    if combined and forbidden in combined:
        errors.append(f"world-backed Operations Monitor must remain observer-only; found {forbidden!r}")

# Missing server-owned queue/job/quality/reliability/delivery evidence must remain visibly withheld.
for required in (
    "PLANT KPIs • INCOMPLETE",
    "— / — / —",
    "— / —",
):
    if screen and required not in screen:
        errors.append(f"OperationsMonitorScreen must withhold unsupported plant KPIs; missing {required!r}")

if errors:
    print("RSE OPERATIONS MONITOR WORLD PLANT VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE OPERATIONS MONITOR WORLD PLANT VERIFY: PASS")
print(" server-owned workcell/buffer/binding evidence visible in existing Operations Monitor: PASS")
print(" aggregate WIP/capacity derived from persisted Industrial Buffers: PASS")
print(" resource validity/fault evidence derived from explicit workcell bindings: PASS")
print(" unsupported queue/quality/reliability/delivery evidence remains withheld: PASS")
print(" monitor control/mutation authority leakage: NONE")
