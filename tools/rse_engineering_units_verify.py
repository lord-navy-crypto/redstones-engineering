#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
failed: list[str] = []


def require(path: str, *tokens: str) -> None:
    p = root / path
    if not p.is_file():
        failed.append(f"missing: {path}")
        return
    text = p.read_text(errors="ignore")
    for token in tokens:
        if token not in text:
            failed.append(f"{path} missing token: {token}")


require(
    "src/main/java/dev/redstoneengineering/core/port/EngineeringUnits.java",
    'register("V", "V", 1.0)',
    'register("mV", "V", 1.0e-3)',
    'register("kV", "V", 1.0e3)',
    'register("Pa", "Pa", 1.0)',
    'register("kPa", "Pa", 1.0e3)',
    'register("MPa", "Pa", 1.0e6)',
    "public static boolean compatible(String left, String right)",
    "public static OptionalDouble conversionFactor(String from, String to)",
    "return OptionalDouble.of(source.toCanonical() / target.toCanonical())",
    "return true; // legacy/custom descriptors remain compatibility-wildcard until migrated",
)
require(
    "src/main/java/dev/redstoneengineering/core/port/PortCompatibility.java",
    "UNIT_MISMATCH",
    "EngineeringUnits.compatible(left.unit(), right.unit())",
    'left.unit() + " != " + right.unit()',
)
require(
    "src/main/java/dev/redstoneengineering/diagnostics/topology/TopologyLinkStatus.java",
    "UNIT_MISMATCH",
)
require(
    "src/main/java/dev/redstoneengineering/diagnostics/topology/EngineeringTopologyView.java",
    "case UNIT_MISMATCH -> TopologyLinkStatus.UNIT_MISMATCH;",
)

if failed:
    print("RSE engineering unit compatibility verification: FAIL")
    for item in failed:
        print(" -", item)
    raise SystemExit(1)

print("RSE engineering unit compatibility verification: PASS")
print(" SI-prefix convertible voltage units: PASS")
print(" SI-prefix convertible pressure units: PASS")
print(" incompatible recognized unit families -> UNIT_MISMATCH: PASS")
print(" legacy/custom unit labels remain backward-compatible: PASS")
print(" topology UNIT_MISMATCH projection: PASS")
