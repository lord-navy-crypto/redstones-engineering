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


logic = "src/main/java/dev/redstoneengineering/signal/CopperCapacitorLogic.java"
block = "src/main/java/dev/redstoneengineering/block/CopperCapacitorBlock.java"
menu = "src/main/java/dev/redstoneengineering/ui/menu/ProcessParameterMenu.java"
screen = "src/main/java/dev/redstoneengineering/client/ui/ProcessParameterNotebookScreen.java"

require(
    logic,
    "stepCharge",
    "chargeTau",
    "dischargeTau",
    "Double.isInfinite(loadResistance)",
    "inputValid",
)
require(
    block,
    "CircuitPhysics.equivalentLoadResistance",
    "CopperCapacitorLogic.stepCharge",
    "effectiveTau",
    "observedLoadResistance",
    "loadTruncated",
)

require(
    block,
    "public static PortQuality inputQuality",
    "public static PortQuality outputQuality",
)
require(
    menu,
    "liveF.set(CopperCapacitorBlock.inputQuality",
    "liveG.set(CopperCapacitorBlock.outputQuality",
)
require(
    screen,
    '"Input quality"',
    '"Output quality"',
    "if(i==5||i==6) return qualityName(v);",
    "retained physical state from input/output evidence quality",
)

logic_path = root / logic
if logic_path.is_file():
    harness = r'''
import dev.redstoneengineering.signal.CopperCapacitorLogic;

public final class CopperCapacitorHarness {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        int lowLoad = CopperCapacitorLogic.stepCharge(100, 0, false, 1, 1.0);
        int highLoad = CopperCapacitorLogic.stepCharge(100, 0, false, 1, 15.0);
        int open = CopperCapacitorLogic.stepCharge(100, 0, false, 1, Double.POSITIVE_INFINITY);
        check(lowLoad < highLoad, "low resistance must discharge faster than high resistance");
        check(highLoad < open, "finite load must discharge faster than open-circuit leakage");

        int charge = CopperCapacitorLogic.stepCharge(0, 15, true, 1, 1.0);
        check(charge > 0, "valid source must charge capacitor");

        int validLow = CopperCapacitorLogic.stepCharge(100, 0, true, 1, Double.POSITIVE_INFINITY);
        check(validLow < 100, "valid zero source must actively discharge toward zero");

        check(CopperCapacitorLogic.dischargeTau(1, 1.0)
                        < CopperCapacitorLogic.dischargeTau(1, 15.0),
                "discharge tau increases with load resistance");
        check(CopperCapacitorLogic.dischargeTau(1, Double.POSITIVE_INFINITY)
                        > CopperCapacitorLogic.dischargeTau(1, 15.0),
                "open circuit keeps only slow leakage");

        System.out.println("CopperCapacitorLogic semantic harness: PASS");
    }
}
'''
    try:
        with tempfile.TemporaryDirectory(prefix="rse-cap-") as td:
            td = Path(td)
            harness_path = td / "CopperCapacitorHarness.java"
            harness_path.write_text(harness)
            compiled = subprocess.run(
                ["javac", "-d", str(td), str(logic_path), str(harness_path)],
                text=True, capture_output=True)
            if compiled.returncode != 0:
                failed.append("javac semantic harness failed: "
                              + (compiled.stderr or compiled.stdout).strip())
            else:
                run = subprocess.run(
                    ["java", "-cp", str(td), "CopperCapacitorHarness"],
                    text=True, capture_output=True)
                if run.returncode != 0:
                    failed.append("semantic harness failed: "
                                  + (run.stderr or run.stdout).strip())
    except FileNotFoundError:
        failed.append("javac/java unavailable for CopperCapacitor semantic harness")

if failed:
    print("RSE copper-capacitor engineering-depth verification: FAIL")
    for item in failed:
        print(" -", item)
    raise SystemExit(1)

print("RSE copper-capacitor engineering-depth verification: PASS")
print(" load-dependent discharge: PASS")
print(" open-circuit leakage slower than loaded discharge: PASS")
print(" valid source charge/discharge semantics: PASS")
print(" load-truncation evidence: PASS")
print(" explicit capacitor input/output evidence quality in notebook: PASS")
