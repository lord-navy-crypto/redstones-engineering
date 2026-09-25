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

logic = "src/main/java/dev/redstoneengineering/signal/PneumaticReliefValveLogic.java"
block = "src/main/java/dev/redstoneengineering/block/PneumaticReliefValveBlock.java"
network = "src/main/java/dev/redstoneengineering/physics/PneumaticNetwork.java"
screen = "src/main/java/dev/redstoneengineering/client/ui/PneumaticSystemScreen.java"

require(logic,
        "MIN_PRESSURE = 0",
        "MAX_PRESSURE = 100",
        "MIN_CONFIGURED_SETPOINT = 1",
        "MAX_CONFIGURED_SETPOINT = 100",
        "MIN_BLOWDOWN = 1",
        "MAX_BLOWDOWN = 25",
        "DEFAULT_BLOWDOWN = 5",
        "boundedConfiguredSetpoint",
        "boundedConfiguredBlowdown",
        "reseatPressure",
        "shouldVent",
        "currentlyVenting")
require(block,
        "BLOWDOWN_PRESSURE = PneumaticReliefValveLogic.DEFAULT_BLOWDOWN",
        "PneumaticReliefValveLogic.boundedConfiguredSetpoint",
        "PneumaticReliefValveLogic.boundedConfiguredBlowdown",
        "reseatPressure(BlockState state)",
        "shouldVent(Level level",
        "VENTING",
        "SEATED")
require(screen,
        "PneumaticReliefValveLogic.MIN_CONFIGURED_SETPOINT",
        "PneumaticReliefValveLogic.MAX_CONFIGURED_SETPOINT",
        "PneumaticReliefValveLogic.MIN_BLOWDOWN",
        "PneumaticReliefValveLogic.MAX_BLOWDOWN",
        "RELIEF PROTECTION PARAMETERS")
require(network,
        "PneumaticReliefValveBlock.shouldVent",
        "Keep the relief episode latched through the blowdown band",
        "Math.min(pressure, setpoint)")

logic_path = root / logic
if logic_path.is_file():
    harness = r"""
import dev.redstoneengineering.signal.PneumaticReliefValveLogic;

public final class PneumaticReliefValveHarness {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        int setpoint = 75;
        int blowdown = 5;
        check(PneumaticReliefValveLogic.reseatPressure(setpoint, blowdown) == 70,
                "reseat pressure must be setpoint minus blowdown");

        check(!PneumaticReliefValveLogic.shouldVent(75, setpoint, blowdown, false),
                "seated relief must not open at exactly setpoint");
        check(PneumaticReliefValveLogic.shouldVent(76, setpoint, blowdown, false),
                "seated relief must open above setpoint");

        check(PneumaticReliefValveLogic.shouldVent(74, setpoint, blowdown, true),
                "open relief must stay open inside blowdown band");
        check(PneumaticReliefValveLogic.shouldVent(71, setpoint, blowdown, true),
                "open relief must remain open until reseat threshold is reached");
        check(!PneumaticReliefValveLogic.shouldVent(70, setpoint, blowdown, true),
                "open relief must reseat at the lower threshold");
        check(PneumaticReliefValveLogic.boundedConfiguredSetpoint(0) == 1,
                "configured setpoint lower bound");
        check(PneumaticReliefValveLogic.boundedConfiguredSetpoint(150) == 100,
                "configured setpoint upper bound");
        check(PneumaticReliefValveLogic.boundedConfiguredBlowdown(10, 99) == 10,
                "blowdown cannot exceed setpoint");
        check(PneumaticReliefValveLogic.boundedConfiguredBlowdown(100, 99) == 25,
                "blowdown engineering cap");

        System.out.println("PneumaticReliefValveLogic harness: PASS");
    }
}
"""
    try:
        with tempfile.TemporaryDirectory(prefix="rse-relief-valve-") as td:
            td = Path(td)
            hp = td / "PneumaticReliefValveHarness.java"
            hp.write_text(harness)
            compile_run = subprocess.run(
                ["javac", "-d", str(td), str(logic_path), str(hp)],
                cwd=root, capture_output=True, text=True
            )
            if compile_run.returncode != 0:
                failed.append("PneumaticReliefValveLogic javac failed: " + compile_run.stderr.strip())
            else:
                run = subprocess.run(
                    ["java", "-cp", str(td), "PneumaticReliefValveHarness"],
                    cwd=root, capture_output=True, text=True
                )
                if run.returncode != 0:
                    failed.append("PneumaticReliefValveLogic harness failed: " + (run.stderr or run.stdout).strip())
    except FileNotFoundError:
        failed.append("javac/java unavailable for relief-valve harness")

if failed:
    print("RSE pneumatic relief-valve blowdown verification: FAIL")
    for item in failed:
        print(" -", item)
    raise SystemExit(1)

print("RSE pneumatic relief-valve blowdown verification: PASS")
print(" opening setpoint threshold: PASS")
print(" lower reseat threshold / blowdown: PASS")
print(" anti-chatter vent-state retention: PASS")
print(" solver integration contract: PASS")
print(" pure setpoint/blowdown bounds shared by Block/HMI/verifier: PASS")
