#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing operations world integration file: {rel}")
        return ""
    return path.read_text(errors="ignore")


provider = read("src/main/java/dev/redstoneengineering/operations/world/OperationWorldResourceProvider.java")
snapshot = read("src/main/java/dev/redstoneengineering/operations/world/OperationWorldResourceSnapshot.java")
resolver = read("src/main/java/dev/redstoneengineering/operations/world/OperationWorldResourceResolver.java")

for token in (
    "interface OperationWorldResourceProvider",
    "operationResourceSnapshot",
):
    if provider and token not in provider:
        errors.append(f"World resource provider missing contract {token!r}")

for token in (
    "record OperationWorldResourceSnapshot",
    "resourceId",
    "processCapabilities",
    "available",
    "running",
    "completionEvidenceAvailable",
    "faultActive",
    "PortQuality",
    "validEvidence",
):
    if snapshot and token not in snapshot:
        errors.append(f"World resource snapshot missing fail-closed evidence field/helper {token!r}")

for token in (
    "class OperationWorldResourceResolver",
    "resolve",
    "BlockPos",
    "OperationWorldResourceProvider",
):
    if resolver and token not in resolver:
        errors.append(f"World resource resolver missing explicit-position resolution {token!r}")

for body, label in ((provider, "provider"), (snapshot, "snapshot"), (resolver, "resolver")):
    for forbidden in (
        "OperationDispatchRuntime",
        "OperationQueueRuntime",
        "OperationMaintenanceRuntime",
        "OperationChangeoverRuntime",
        "RobotMission",
        "setBlock(",
        "setDeltaMovement(",
        "scheduleTick(",
        "Minecraft.getInstance",
        "client.ui",
    ):
        if body and forbidden in body:
            errors.append(f"Operations world {label} must remain evidence-only; found {forbidden!r}")

if resolver:
    for forbidden in ("getEntitiesOfClass", "inflate(", "closerThan", "nearest"):
        if forbidden in resolver:
            errors.append(f"World resource resolver must not use proximity discovery; found {forbidden!r}")

if errors:
    print("RSE OPERATIONS WORLD INTEGRATION VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE OPERATIONS WORLD INTEGRATION VERIFY: PASS")
print(" explicit-position resource resolution: PASS")
print(" fail-closed evidence snapshot: PASS")
print(" dispatch/queue/world-motion/client authority leakage: NONE")
print(" proximity auto-discovery: NONE")
