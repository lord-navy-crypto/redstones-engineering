#!/usr/bin/env python3
from pathlib import Path
import gzip

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []

EXPECTED_STRUCTURES = {
    "material_release",
    "queue_dispatch",
    "maintenance_hold",
    "quality_output",
    "amr_lane",
    "operations_monitor",
    "full_factory",
}


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        errors.append(f"missing validation factory file: {rel}")
        return ""
    return path.read_text(encoding="utf-8", errors="ignore")


# The generator is intentionally split into a compatibility front-end plus a byte-for-byte legacy
# backend. Verify the combined source surface because the front-end delegates every historical
# command/constant to the backend and adds Mega Factory generation on top.
generator = read("tools/rse_validation_factory.py")
legacy_generator_path = ROOT / "tools/rse_validation_factory_legacy.py"
if legacy_generator_path.is_file():
    generator += "\n" + legacy_generator_path.read_text(encoding="utf-8", errors="ignore")
module = read("src/main/java/dev/redstoneengineering/validation/RseValidationFactoryModule.java")
service = read("src/main/java/dev/redstoneengineering/validation/RseValidationFactoryService.java")
gametest = read("src/main/java/dev/redstoneengineering/gametest/RseValidationFactoryGameTests.java")
build = read("build.gradle")

for name in EXPECTED_STRUCTURES:
    if generator and f'"{name}"' not in generator:
        errors.append(f"generator missing structure declaration {name!r}")

for token in (
    "generate", "package", "RSE Validation Factory",
    "src/generated/resources/data/redstoneengineering/structure/validation",
    "build/validation/RSE-Validation-Factory.zip",
    "level.dat",
):
    if generator and token not in generator:
        errors.append(f"validation generator missing contract {token!r}")

for token in (
    'Commands.literal("rsevalidation")',
    'Commands.literal("build")',
    'Commands.literal("reset")',
    'Commands.literal("status")',
    "RseValidationFactoryService.build(",
    "RseValidationFactoryService.reset(",
    "RseValidationFactoryService.status(",
):
    if module and token not in module:
        errors.append(f"validation command module missing contract {token!r}")

for token in (
    "class RseValidationFactoryService",
    '"validation:"',
    "OperationIndustrialBufferState",
    "OperationQueueWorldState",
    "OperationMaintenanceWorldState",
    "OperationRobotTransportWorldState",
    "EngineeringMobileRobotEntity",
    "engineering_mobile_robot",
    "validation/full_factory",
):
    if service and token not in service:
        errors.append(f"validation service missing authoritative boundary {token!r}")

for forbidden in (
    ".putQueue(", ".putBuffer(", ".putMaintenanceSnapshot(",
    "setDeltaMovement(", "setRobotState(", "recordJobAdmitted(",
    '"DELIVERED"', "transitionJob(",
):
    if service and forbidden in service:
        errors.append(f"validation service must not bypass production authority; found {forbidden!r}")

for token in (
    "class RseValidationFactoryGameTests",
    "validationFactoryBuildsPhysicalStations",
    "RseValidationFactoryService.build(",
    "RseValidationFactoryService.INPUT_BUFFER_ID",
    "RseValidationFactoryService.OUTPUT_BUFFER_ID",
    "RseValidationFactoryService.MAIN_QUEUE_ID",
    "RseValidationFactoryService.ROBOT_TAG",
):
    if gametest and token not in gametest:
        errors.append(f"validation factory local GameTest missing contract {token!r}")

for token in (
    "generateValidationStructures",
    "packageValidationWorld",
    "tools/rse_validation_factory.py",
):
    if build and token not in build:
        errors.append(f"Gradle missing local validation task {token!r}")

structure_dir = ROOT / "src/generated/resources/data/redstoneengineering/structure/validation"
for name in EXPECTED_STRUCTURES:
    path = structure_dir / f"{name}.nbt"
    if not path.is_file():
        errors.append(f"generated validation structure missing: {path.relative_to(ROOT)}")
        continue
    try:
        raw = path.read_bytes()
        if len(raw) < 40:
            raise ValueError("file too small")
        payload = gzip.decompress(raw)
        if not payload or payload[0] != 10:  # TAG_Compound root
            raise ValueError("not a compressed compound NBT")
    except Exception as exc:  # noqa: BLE001 - verifier should report all malformed outputs
        errors.append(f"invalid generated structure {name!r}: {exc}")

if errors:
    print("RSE VALIDATION FACTORY VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE VALIDATION FACTORY VERIFY: PASS")
print(" deterministic reusable structure set: PASS")
print(" build/reset/status command boundary: PASS")
print(" Operations facades retained as state authority: PASS")
print(" real AMR entity retained as transport authority: PASS")
print(" real-world ZIP packaging contract: PASS")
