#!/usr/bin/env python3
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
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
require(
    "src/main/java/dev/redstoneengineering/diagnostics/topology/TopologyFaceSnapshot.java",
    "linkStatus == TopologyLinkStatus.UNIT_MISMATCH",
)
require(
    "src/main/java/dev/redstoneengineering/diagnostics/topology/TopologyDiagnosticsReport.java",
    "DOMAIN_MISMATCH, QUANTITY_MISMATCH, UNIT_MISMATCH, DIRECTION_MISMATCH",
    "incompatible domain/quantity/unit/direction",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/DiagnosticTabletScreen.java",
    'line.contains("→ UNIT_MISMATCH")',
)


def run_java_semantics() -> None:
    javac = shutil.which("javac")
    java = shutil.which("java")
    if javac is None or java is None:
        failed.append("Java toolchain unavailable: javac/java required for EngineeringUnits semantic verification")
        return

    source = root / "src/main/java/dev/redstoneengineering/core/port/EngineeringUnits.java"
    if not source.is_file():
        return

    harness = """package dev.redstoneengineering.core.port;

public final class EngineeringUnitsHarness {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void close(double actual, double expected, String message) {
        if (Math.abs(actual - expected) > 1.0e-12) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    public static void main(String[] args) {
        require(EngineeringUnits.compatible("V", "mV"), "V/mV must be compatible");
        require(EngineeringUnits.compatible("Pa", "kPa"), "Pa/kPa must be compatible");
        require(!EngineeringUnits.compatible("V", "Pa"), "V/Pa must not be compatible");
        require(EngineeringUnits.compatible("V-eq", "signal"), "legacy/custom labels must remain permissive");
        close(EngineeringUnits.conversionFactor("V", "mV").orElseThrow(), 1000.0, "V -> mV");
        close(EngineeringUnits.conversionFactor("mV", "V").orElseThrow(), 0.001, "mV -> V");
        close(EngineeringUnits.conversionFactor("Pa", "kPa").orElseThrow(), 0.001, "Pa -> kPa");
        require(EngineeringUnits.conversionFactor("V", "Pa").isEmpty(), "V -> Pa must be undefined");
        require(EngineeringUnits.conversionFactor("custom", "custom").orElseThrow() == 1.0,
                "identical custom units must have identity conversion");
    }
}
"""

    with tempfile.TemporaryDirectory(prefix="rse-engineering-units-") as tmp:
        tmp_root = Path(tmp)
        package_dir = tmp_root / "dev/redstoneengineering/core/port"
        package_dir.mkdir(parents=True)
        shutil.copy2(source, package_dir / "EngineeringUnits.java")
        (package_dir / "EngineeringUnitsHarness.java").write_text(harness)

        compiled = subprocess.run(
            [javac, "-d", str(tmp_root), str(package_dir / "EngineeringUnits.java"), str(package_dir / "EngineeringUnitsHarness.java")],
            capture_output=True,
            text=True,
        )
        if compiled.returncode != 0:
            failed.append("EngineeringUnits javac failed: " + (compiled.stderr.strip() or compiled.stdout.strip()))
            return

        executed = subprocess.run(
            [java, "-cp", str(tmp_root), "dev.redstoneengineering.core.port.EngineeringUnitsHarness"],
            capture_output=True,
            text=True,
        )
        if executed.returncode != 0:
            failed.append("EngineeringUnits semantic harness failed: " + (executed.stderr.strip() or executed.stdout.strip()))


if not failed:
    run_java_semantics()

if failed:
    print("RSE engineering unit compatibility verification: FAIL")
    for item in failed:
        print(" -", item)
    raise SystemExit(1)

print("RSE engineering unit compatibility verification: PASS")
print(" SI-prefix convertible voltage units: PASS")
print(" SI-prefix convertible pressure units: PASS")
print(" V -> mV factor=1000 and mV -> V factor=0.001: PASS")
print(" Pa -> kPa factor=0.001: PASS")
print(" incompatible recognized unit families -> UNIT_MISMATCH contract: PASS")
print(" legacy/custom unit labels remain backward-compatible: PASS")
print(" topology UNIT_MISMATCH projection + issue count + report: PASS")
print(" diagnostic tablet UNIT_MISMATCH visibility: PASS")
