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

logic = "src/main/java/dev/redstoneengineering/signal/MechanicalExciterLogic.java"
block = "src/main/java/dev/redstoneengineering/block/MechanicalExciterBlock.java"

require(logic,
        "run-up/coast-down",
        "approachAsymmetric",
        "targetAmp > 0",
        "settled")
require(block,
        "RUNTIME_KEY = "mechanical_exciter"",
        "ACTUAL_AMPLITUDE",
        "ACTUAL_FREQUENCY",
        "TARGET_AMPLITUDE",
        "MechanicalExciterLogic.step",
        "A powered exciter is a continuous mechanical source",
        "VibrationNetwork.propagate(level, pos",
        "level.scheduleTick(pos, this, 1)",
        "RuntimeIntStore.remove(level, RUNTIME_KEY, pos)")

logic_path = root / logic
if logic_path.is_file():
    harness = r"""
import dev.redstoneengineering.signal.MechanicalExciterLogic;

public final class MechanicalExciterHarness {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        var s = new MechanicalExciterLogic.State(0, 0);

        var a = MechanicalExciterLogic.step(10, 8, s);
        check(a.amplitude() == 2 && a.frequency() == 1,
                "exciter must run up gradually from rest");

        var b = MechanicalExciterLogic.step(10, 8, a);
        check(b.amplitude() == 4 && b.frequency() == 2,
                "amplitude and frequency must continue tracking finite-rate targets");

        var c = MechanicalExciterLogic.step(0, 8, b);
        check(c.amplitude() == 3 && c.frequency() == 1,
                "power loss must coast down instead of stopping instantly");

        var d = MechanicalExciterLogic.step(0, 8, c);
        var e = MechanicalExciterLogic.step(0, 8, d);
        var f = MechanicalExciterLogic.step(0, 8, e);
        check(f.amplitude() == 0 && f.frequency() == 0,
                "coast-down must eventually reach rest");
        check(MechanicalExciterLogic.settled(0, 8, f),
                "rest state must be recognized as settled");

        System.out.println("MechanicalExciterLogic harness: PASS");
    }
}
"""
    try:
        with tempfile.TemporaryDirectory(prefix="rse-mech-exciter-") as td:
            td = Path(td)
            hp = td / "MechanicalExciterHarness.java"
            hp.write_text(harness)
            compile_run = subprocess.run(
                ["javac", "-d", str(td), str(logic_path), str(hp)],
                cwd=root, capture_output=True, text=True
            )
            if compile_run.returncode != 0:
                failed.append("MechanicalExciterLogic javac failed: " + compile_run.stderr.strip())
            else:
                run = subprocess.run(
                    ["java", "-cp", str(td), "MechanicalExciterHarness"],
                    cwd=root, capture_output=True, text=True
                )
                if run.returncode != 0:
                    failed.append("MechanicalExciterLogic harness failed: " + (run.stderr or run.stdout).strip())
    except FileNotFoundError:
        failed.append("javac/java unavailable for mechanical-exciter harness")

if failed:
    print("RSE mechanical-exciter dynamics verification: FAIL")
    for item in failed:
        print(" -", item)
    raise SystemExit(1)

print("RSE mechanical-exciter dynamics verification: PASS")
print(" finite run-up / coast-down: PASS")
print(" frequency tracking inertia: PASS")
print(" sustained-drive continuous propagation contract: PASS")
print(" runtime cleanup contract: PASS")
