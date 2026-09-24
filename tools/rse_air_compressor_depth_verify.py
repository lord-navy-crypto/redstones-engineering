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

logic = "src/main/java/dev/redstoneengineering/signal/AirCompressorLogic.java"
block = "src/main/java/dev/redstoneengineering/block/AirCompressorBlock.java"
network = "src/main/java/dev/redstoneengineering/physics/PneumaticNetwork.java"
menu = "src/main/java/dev/redstoneengineering/ui/menu/PneumaticSystemMenu.java"
screen = "src/main/java/dev/redstoneengineering/client/ui/PneumaticSystemScreen.java"

require(logic,
        "rampUpRate",
        "rampDownRate",
        "stepPressure",
        "trackingError")
require(block,
        'IntegerProperty.create("response_mode", 0, 2)',
        "actualPressure",
        "startCount",
        "runTicks",
        "AirCompressorLogic.stepPressure",
        "PneumaticNetwork.recompute")
require(network,
        "AirCompressorBlock.actualPressure",
        "if (AirCompressorBlock.actualPressure(level, pos) > 0)",
        "int supply = AirCompressorBlock.actualPressure(level, pos)")
require(menu,
        "AirCompressorBlock.RESPONSE_MODE",
        "AirCompressorBlock.configuredResponse",
        "AirCompressorBlock.setResponseRates",
        "engineeringA.set(response.a())",
        "engineeringB.set(response.b())",
        "AirCompressorBlock.actualPressure",
        "AirCompressorBlock.startCount",
        "AirCompressorBlock.runTicks")
require(screen,
        "COMPRESSOR RESPONSE",
        "Target / actual",
        "Tracking error",
        "Starts / run ticks",
        "Ramp up",
        "Ramp down",
        'menu.engineeringA()+" pressure/tick"',
        'menu.engineeringB()+" pressure/tick"')

logic_path = root / logic
if logic_path.is_file():
    harness = r'''
import dev.redstoneengineering.signal.AirCompressorLogic;

public final class AirCompressorHarness {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(AirCompressorLogic.stepPressure(0, 100, 0) == 5, "soft ramp up");
        check(AirCompressorLogic.stepPressure(0, 100, 1) == 10, "normal ramp up");
        check(AirCompressorLogic.stepPressure(0, 100, 2) == 20, "fast ramp up");
        check(AirCompressorLogic.stepPressure(95, 100, 2) == 100, "up clamp");
        check(AirCompressorLogic.stepPressure(100, 0, 1) == 85, "ramp down");
        check(AirCompressorLogic.stepPressure(6, 0, 2) == 0, "down clamp");
        check(AirCompressorLogic.trackingError(70, 100) == 30, "positive tracking error");
        check(AirCompressorLogic.trackingError(90, 40) == -50, "negative tracking error");
        System.out.println("AirCompressorLogic semantic harness: PASS");
    }
}
'''
    try:
        with tempfile.TemporaryDirectory(prefix="rse-compressor-") as td:
            td = Path(td)
            harness_path = td / "AirCompressorHarness.java"
            harness_path.write_text(harness)
            compiled = subprocess.run(
                ["javac", "-d", str(td), str(logic_path), str(harness_path)],
                text=True, capture_output=True)
            if compiled.returncode != 0:
                failed.append("javac semantic harness failed: "
                              + (compiled.stderr or compiled.stdout).strip())
            else:
                run = subprocess.run(
                    ["java", "-cp", str(td), "AirCompressorHarness"],
                    text=True, capture_output=True)
                if run.returncode != 0:
                    failed.append("semantic harness failed: "
                                  + (run.stderr or run.stdout).strip())
    except FileNotFoundError:
        failed.append("javac/java unavailable for AirCompressor semantic harness")

if failed:
    print("RSE air-compressor engineering-depth verification: FAIL")
    for item in failed:
        print(" -", item)
    raise SystemExit(1)

print("RSE air-compressor engineering-depth verification: PASS")
print(" finite spool-up/spool-down pressure dynamics: PASS")
print(" pneumatic network consumes actual supply, not command target: PASS")
print(" retained start/run evidence: PASS")
print(" pneumatic HMI exact ramp-rate authority: PASS")
