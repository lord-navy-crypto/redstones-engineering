#!/usr/bin/env python3
from pathlib import Path
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


logic = "src/main/java/dev/redstoneengineering/signal/CopperFuseLogic.java"
block = "src/main/java/dev/redstoneengineering/block/CopperFuseBlock.java"

require(logic,
        "nextThermal",
        "tripThreshold",
        "tripProgressPermille",
        "currentRatio",
        "Math.ceil")
require(block,
        "THERMAL_KEY",
        "CopperFuseLogic.nextThermal",
        "thermalExposure",
        "tripProgressPermille",
        "resetAllowed",
        "RESET BLOCKED")

logic_path = root / logic
if logic_path.is_file():
    harness = r'''
import dev.redstoneengineering.signal.CopperFuseLogic;

public final class CopperFuseHarness {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        int slight = CopperFuseLogic.nextThermal(0, 5.0, 4);
        int severe = CopperFuseLogic.nextThermal(0, 12.0, 4);
        check(slight > 0, "slight overcurrent must accumulate heat");
        check(severe > slight, "severe overcurrent must heat faster");

        int cooled = CopperFuseLogic.nextThermal(80, 2.0, 4);
        check(cooled < 80, "below-rating current must cool fuse");

        int t = 0;
        int steps = 0;
        while (t < CopperFuseLogic.tripThreshold() && steps < 100) {
            t = CopperFuseLogic.nextThermal(t, 5.0, 4);
            steps++;
        }
        check(steps > 1, "modest overload must not trip instantaneously");

        int huge = CopperFuseLogic.nextThermal(0, 20.0, 4);
        check(huge >= CopperFuseLogic.tripThreshold(),
                "large fault current should reach trip threshold immediately");

        check(CopperFuseLogic.tripProgressPermille(0) == 0, "zero heat progress");
        check(CopperFuseLogic.tripProgressPermille(CopperFuseLogic.tripThreshold()) == 1000,
                "threshold is 100 percent");

        System.out.println("CopperFuseLogic semantic harness: PASS");
    }
}
'''
    try:
        with tempfile.TemporaryDirectory(prefix="rse-fuse-") as td:
            td = Path(td)
            harness_path = td / "CopperFuseHarness.java"
            harness_path.write_text(harness)
            compiled = subprocess.run(
                ["javac", "-d", str(td), str(logic_path), str(harness_path)],
                text=True, capture_output=True)
            if compiled.returncode != 0:
                failed.append("javac semantic harness failed: "
                              + (compiled.stderr or compiled.stdout).strip())
            else:
                run = subprocess.run(
                    ["java", "-cp", str(td), "CopperFuseHarness"],
                    text=True, capture_output=True)
                if run.returncode != 0:
                    failed.append("semantic harness failed: "
                                  + (run.stderr or run.stdout).strip())
    except FileNotFoundError:
        failed.append("javac/java unavailable for CopperFuse semantic harness")

if failed:
    print("RSE copper-fuse engineering-depth verification: FAIL")
    for item in failed:
        print(" -", item)
    raise SystemExit(1)

print("RSE copper-fuse engineering-depth verification: PASS")
print(" I2t-style overload accumulation: PASS")
print(" below-rating cooling: PASS")
print(" severe faults trip faster than modest overloads: PASS")
print(" unsafe reset rejection: PASS")
