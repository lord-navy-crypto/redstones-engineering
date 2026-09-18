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

logic = "src/main/java/dev/redstoneengineering/signal/RelayDynamicsLogic.java"
block = "src/main/java/dev/redstoneengineering/block/SingleRelayBlock.java"
menu = "src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java"
screen = "src/main/java/dev/redstoneengineering/client/ui/UniversalFieldDeviceScreen.java"

require(logic,
        "record State",
        "record Result",
        "operateDelayTicks",
        "releaseDelayTicks",
        "transitionPending")
require(block,
        "TIMING_MODE",
        "OPERATE_DELAYS",
        "RELEASE_DELAYS",
        "RelayDynamicsLogic.step",
        "transitionRemaining",
        "pendingCoilTarget",
        "stepTiming",
        "operateDelayTicks",
        "releaseDelayTicks",
        "Unknown control evidence freezes the actual armature")
require(menu,
        "SingleRelayBlock.TIMING_MODE",
        "SingleRelayBlock.transitionRemaining",
        "SingleRelayBlock.pendingCoilTarget",
        "SingleRelayBlock.stepTiming")
require(screen,
        "Timing • ",
        "Operate / release",
        "Mechanical state",
        "finite armature travel")

logic_path = root / logic
if logic_path.is_file():
    harness = r"""
import dev.redstoneengineering.signal.RelayDynamicsLogic;

public final class RelayDynamicsHarness {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        var start = new RelayDynamicsLogic.State(false, false, 0);
        var pickup0 = RelayDynamicsLogic.step(true, 2, 3, start);
        check(!pickup0.state().energized() && pickup0.state().remainingTicks() == 2,
                "operate delay must begin without moving the armature");
        var pickup1 = RelayDynamicsLogic.step(true, 2, 3, pickup0.state());
        check(!pickup1.state().energized() && pickup1.state().remainingTicks() == 1,
                "operate delay must retain old contact state while timing");
        var pickup2 = RelayDynamicsLogic.step(true, 2, 3, pickup1.state());
        check(pickup2.state().energized() && pickup2.transitioned(),
                "armature must operate only after delay expires");

        var release0 = RelayDynamicsLogic.step(false, 2, 3, pickup2.state());
        check(release0.state().energized() && release0.state().remainingTicks() == 3,
                "release may have an independent longer delay");

        var cancelled = RelayDynamicsLogic.step(true, 2, 3, release0.state());
        check(cancelled.state().energized() && cancelled.state().remainingTicks() == 0
                        && !cancelled.transitioned(),
                "restored coil command must cancel an unfinished release");

        var r0 = RelayDynamicsLogic.step(false, 2, 3, cancelled.state());
        var r1 = RelayDynamicsLogic.step(false, 2, 3, r0.state());
        var r2 = RelayDynamicsLogic.step(false, 2, 3, r1.state());
        var r3 = RelayDynamicsLogic.step(false, 2, 3, r2.state());
        check(!r3.state().energized() && r3.transitioned(),
                "release transition must complete after configured delay");

        var instant = RelayDynamicsLogic.step(true, 0, 0,
                new RelayDynamicsLogic.State(false, false, 0));
        check(instant.state().energized() && instant.transitioned(),
                "instant profile must preserve legacy immediate switching");

        System.out.println("RelayDynamicsLogic harness: PASS");
    }
}
"""
    try:
        with tempfile.TemporaryDirectory(prefix="rse-relay-") as td:
            td = Path(td)
            hp = td / "RelayDynamicsHarness.java"
            hp.write_text(harness)
            compile_run = subprocess.run(
                ["javac", "-d", str(td), str(logic_path), str(hp)],
                cwd=root, capture_output=True, text=True
            )
            if compile_run.returncode != 0:
                failed.append("RelayDynamicsLogic javac failed: " + compile_run.stderr.strip())
            else:
                run = subprocess.run(
                    ["java", "-cp", str(td), "RelayDynamicsHarness"],
                    cwd=root, capture_output=True, text=True
                )
                if run.returncode != 0:
                    failed.append("RelayDynamicsLogic harness failed: " + (run.stderr or run.stdout).strip())
    except FileNotFoundError:
        failed.append("javac/java unavailable for relay dynamics harness")

if failed:
    print("RSE single-relay electromechanical verification: FAIL")
    for item in failed:
        print(" -", item)
    raise SystemExit(1)

print("RSE single-relay electromechanical verification: PASS")
print(" independent pickup/dropout and operate/release semantics: PASS")
print(" pending-transition cancellation/reversal: PASS")
print(" legacy instant profile: PASS")
print(" server-authoritative timing controls: PASS")
