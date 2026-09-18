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

logic = "src/main/java/dev/redstoneengineering/signal/PwmCarrierLogic.java"
block = "src/main/java/dev/redstoneengineering/block/PwmControllerBlock.java"
menu = "src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java"
screen = "src/main/java/dev/redstoneengineering/client/ui/UniversalFieldDeviceScreen.java"

require(logic,
        "period-boundary command latching",
        "latchedCommand",
        "pendingUpdate",
        "quantizedOnTicks")
require(block,
        "LATCHED_COMMAND_SLOT",
        "CYCLE_COUNT_SLOT",
        "PwmCarrierLogic.step",
        "appliedCommand",
        "completedCycles",
        "pendingUpdate",
        "resetCarrier")
require(menu,
        "assessment.appliedCommand()",
        "assessment.pendingUpdate()",
        "assessment.completedCycles()")
require(screen,
        "DUTY UPDATE PENDING",
        "Command requested / active",
        "carrier-cycle boundary",
        "runt pulse")

logic_path = root / logic
if logic_path.is_file():
    harness = r"""
import dev.redstoneengineering.signal.PwmCarrierLogic;

public final class PwmCarrierHarness {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        int period = 8;
        var state = new PwmCarrierLogic.State(false, 0, 0, 0);

        var p0 = PwmCarrierLogic.step(4, period, state);
        check(p0.state().latchedCommand() == 4 && p0.outputHigh(),
                "first cycle must latch command 4 at phase zero");
        check(p0.onTicks() == 2, "command 4 at period 8 must quantize to two on ticks");

        var p1 = PwmCarrierLogic.step(12, period, p0.state());
        check(p1.state().latchedCommand() == 4,
                "mid-cycle command change must not alter active duty");
        check(p1.pendingUpdate(), "new command must be reported pending");
        check(p1.outputHigh(), "second tick remains high under old two-tick pulse");

        var s = p1.state();
        boolean sawOldLow = false;
        while (s.phase() != 0) {
            var r = PwmCarrierLogic.step(12, period, s);
            if (!r.outputHigh()) sawOldLow = true;
            check(r.state().latchedCommand() == 4,
                    "old command must remain active through the carrier cycle");
            s = r.state();
        }
        check(sawOldLow,
                "old command pulse must end normally instead of stretching to the new duty");

        var nextCycle = PwmCarrierLogic.step(12, period, s);
        check(nextCycle.state().latchedCommand() == 12 && !nextCycle.pendingUpdate(),
                "pending command must latch at next phase-zero boundary");
        check(nextCycle.onTicks() == 6,
                "command 12 at period 8 must quantize to six on ticks");

        var off = PwmCarrierLogic.step(0, period, nextCycle.state());
        check(!off.outputHigh() && off.state().phase() == 0
                        && off.state().latchedCommand() == 0 && !off.pendingUpdate(),
                "0% endpoint must shut down immediately");

        var full = PwmCarrierLogic.step(15, period, off.state());
        check(full.outputHigh() && full.state().phase() == 0
                        && full.state().latchedCommand() == 15 && !full.pendingUpdate(),
                "100% endpoint must apply immediately");

        System.out.println("PwmCarrierLogic harness: PASS");
    }
}
"""
    try:
        with tempfile.TemporaryDirectory(prefix="rse-pwm-") as td:
            td = Path(td)
            hp = td / "PwmCarrierHarness.java"
            hp.write_text(harness)
            compile_run = subprocess.run(
                ["javac", "-d", str(td), str(logic_path), str(hp)],
                cwd=root, capture_output=True, text=True
            )
            if compile_run.returncode != 0:
                failed.append("PwmCarrierLogic javac failed: " + compile_run.stderr.strip())
            else:
                run = subprocess.run(
                    ["java", "-cp", str(td), "PwmCarrierHarness"],
                    cwd=root, capture_output=True, text=True
                )
                if run.returncode != 0:
                    failed.append("PwmCarrierLogic harness failed: " + (run.stderr or run.stdout).strip())
    except FileNotFoundError:
        failed.append("javac/java unavailable for PWM carrier harness")

if failed:
    print("RSE PWM carrier-latch verification: FAIL")
    for item in failed:
        print(" -", item)
    raise SystemExit(1)

print("RSE PWM carrier-latch verification: PASS")
print(" period-boundary shadow/active duty update: PASS")
print(" runt-pulse prevention across command changes: PASS")
print(" immediate 0%/100% endpoints: PASS")
print(" synchronized active/pending drive state: PASS")
