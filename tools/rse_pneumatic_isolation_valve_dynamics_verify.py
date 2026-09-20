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

logic = "src/main/java/dev/redstoneengineering/signal/PneumaticIsolationValveLogic.java"
block = "src/main/java/dev/redstoneengineering/block/PneumaticValveBlock.java"
network = "src/main/java/dev/redstoneengineering/physics/PneumaticNetwork.java"

require(logic,
        "finite-travel state machine",
        "actualOpen",
        "pendingTargetOpen",
        "remainingTicks",
        "public static Result step(")
require(block,
        "OPEN is the operator command",
        "ACTUAL_OPEN",
        "PENDING_TARGET_OPEN",
        "TRANSITION_REMAINING",
        "OPENING_TRAVEL_TICKS",
        "CLOSING_TRAVEL_TICKS",
        "PneumaticIsolationValveLogic.step",
        "actualOpen(Level level",
        "transitionRemaining",
        "server.scheduleTick(pos, this, 1)")
require(network,
        "PneumaticValveBlock.actualOpen(level, from, a)",
        "PneumaticValveBlock.actualOpen(level, to, b)")

logic_path = root / logic
if logic_path.is_file():
    harness = r"""
import dev.redstoneengineering.signal.PneumaticIsolationValveLogic;

public final class PneumaticIsolationValveHarness {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        var closed = new PneumaticIsolationValveLogic.State(false, false, 0);

        var o0 = PneumaticIsolationValveLogic.step(true, 3, 2, closed);
        check(!o0.state().actualOpen() && o0.state().remainingTicks() == 3,
                "opening command must not change topology immediately");
        var o1 = PneumaticIsolationValveLogic.step(true, 3, 2, o0.state());
        var o2 = PneumaticIsolationValveLogic.step(true, 3, 2, o1.state());
        var o3 = PneumaticIsolationValveLogic.step(true, 3, 2, o2.state());
        check(o3.state().actualOpen() && o3.transitioned(),
                "actual flow path must open only after travel completes");

        var c0 = PneumaticIsolationValveLogic.step(false, 3, 2, o3.state());
        check(c0.state().actualOpen() && c0.state().remainingTicks() == 2,
                "closing command must retain open flow path during travel");

        var reversed = PneumaticIsolationValveLogic.step(true, 3, 2, c0.state());
        check(reversed.state().actualOpen() && reversed.state().remainingTicks() == 0
                        && !reversed.transitioned(),
                "reversing during closing must cancel the unfinished close");

        var c1 = PneumaticIsolationValveLogic.step(false, 3, 2, reversed.state());
        var c2 = PneumaticIsolationValveLogic.step(false, 3, 2, c1.state());
        var c3 = PneumaticIsolationValveLogic.step(false, 3, 2, c2.state());
        check(!c3.state().actualOpen() && c3.transitioned(),
                "closing topology change must occur only after close travel expires");

        System.out.println("PneumaticIsolationValveLogic harness: PASS");
    }
}
"""
    try:
        with tempfile.TemporaryDirectory(prefix="rse-pneumatic-valve-") as td:
            td = Path(td)
            hp = td / "PneumaticIsolationValveHarness.java"
            hp.write_text(harness)
            compile_run = subprocess.run(
                ["javac", "-d", str(td), str(logic_path), str(hp)],
                cwd=root, capture_output=True, text=True
            )
            if compile_run.returncode != 0:
                failed.append("PneumaticIsolationValveLogic javac failed: " + compile_run.stderr.strip())
            else:
                run = subprocess.run(
                    ["java", "-cp", str(td), "PneumaticIsolationValveHarness"],
                    cwd=root, capture_output=True, text=True
                )
                if run.returncode != 0:
                    failed.append("PneumaticIsolationValveLogic harness failed: " + (run.stderr or run.stdout).strip())
    except FileNotFoundError:
        failed.append("javac/java unavailable for valve dynamics harness")

if failed:
    print("RSE pneumatic isolation-valve dynamics verification: FAIL")
    for item in failed:
        print(" -", item)
    raise SystemExit(1)

print("RSE pneumatic isolation-valve dynamics verification: PASS")
print(" operator command / actual topology separation: PASS")
print(" finite opening/closing travel: PASS")
print(" in-flight reversal cancellation: PASS")
print(" pneumatic solver consumes actual valve position: PASS")
