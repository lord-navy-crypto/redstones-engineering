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

logic = "src/main/java/dev/redstoneengineering/signal/PneumaticCheckValveLogic.java"
block = "src/main/java/dev/redstoneengineering/block/PneumaticCheckValveBlock.java"
network = "src/main/java/dev/redstoneengineering/physics/PneumaticNetwork.java"

require(logic,
        "transmittedPressure",
        "crackedOpen",
        "upstream - crack")
require(block,
        "CRACKING_PRESSURE = 4",
        "crackingPressure()",
        "transmittedPressure(int upstreamPressure)",
        "crackedOpen(int upstreamPressure)",
        "CRACKED OPEN",
        "SEATED")
require(network,
        "state.getBlock() instanceof PneumaticCheckValveBlock",
        "PneumaticCheckValveBlock.transmittedPressure(pressure)",
        "instanceof PneumaticCheckValveBlock && !directionalForward",
        "instanceof PneumaticCheckValveBlock && !directionalBackwardEntry")

logic_path = root / logic
if logic_path.is_file():
    harness = r"""
import dev.redstoneengineering.signal.PneumaticCheckValveLogic;

public final class PneumaticCheckValveHarness {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(PneumaticCheckValveLogic.transmittedPressure(3, 4) == 0,
                "pressure below cracking threshold must not propagate");
        check(!PneumaticCheckValveLogic.crackedOpen(4, 4),
                "pressure equal to cracking threshold remains seated");
        check(PneumaticCheckValveLogic.transmittedPressure(5, 4) == 1,
                "pressure just above cracking threshold must propagate residual pressure");
        check(PneumaticCheckValveLogic.crackedOpen(5, 4),
                "pressure above cracking threshold must unseat the check valve");
        check(PneumaticCheckValveLogic.transmittedPressure(80, 4) == 76,
                "check valve must preserve pressure minus its cracking drop");
        System.out.println("PneumaticCheckValveLogic harness: PASS");
    }
}
"""
    try:
        with tempfile.TemporaryDirectory(prefix="rse-check-valve-") as td:
            td = Path(td)
            hp = td / "PneumaticCheckValveHarness.java"
            hp.write_text(harness)
            compile_run = subprocess.run(
                ["javac", "-d", str(td), str(logic_path), str(hp)],
                cwd=root, capture_output=True, text=True
            )
            if compile_run.returncode != 0:
                failed.append("PneumaticCheckValveLogic javac failed: " + compile_run.stderr.strip())
            else:
                run = subprocess.run(
                    ["java", "-cp", str(td), "PneumaticCheckValveHarness"],
                    cwd=root, capture_output=True, text=True
                )
                if run.returncode != 0:
                    failed.append("PneumaticCheckValveLogic harness failed: " + (run.stderr or run.stdout).strip())
    except FileNotFoundError:
        failed.append("javac/java unavailable for check-valve harness")

if failed:
    print("RSE pneumatic check-valve verification: FAIL")
    for item in failed:
        print(" -", item)
    raise SystemExit(1)

print("RSE pneumatic check-valve verification: PASS")
print(" one-way topology contract: PASS")
print(" finite cracking pressure: PASS")
print(" forward cracking pressure drop: PASS")
print(" low-pressure reseated behavior: PASS")
