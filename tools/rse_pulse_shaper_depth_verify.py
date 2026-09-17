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


logic = "src/main/java/dev/redstoneengineering/signal/PulseShaperLogic.java"
block = "src/main/java/dev/redstoneengineering/block/PulseShaperBlock.java"
menu = "src/main/java/dev/redstoneengineering/ui/menu/SignalProcessorMenu.java"
screen = "src/main/java/dev/redstoneengineering/client/ui/SignalProcessorScreen.java"

require(logic,
        "record State",
        "record Result",
        "public static Result step(",
        "acceptedTrigger",
        "suppressedTrigger",
        "retriggerable")
require(block,
        "THRESHOLD",
        "RETRIGGERABLE",
        "triggerCount",
        "suppressedTriggerCount",
        "lastTriggerAgeTicks",
        "PulseShaperLogic.step")
require(menu,
        "BUTTON_THRESHOLD_PREVIOUS",
        "BUTTON_THRESHOLD_NEXT",
        "BUTTON_TOGGLE_RETRIGGER",
        "PulseShaperBlock.stepThreshold",
        "PulseShaperBlock.toggleRetriggerable")
require(screen,
        "Trigger threshold",
        "Retrigger",
        "Accepted triggers",
        "Suppressed triggers")

# Compile and execute the pure logic against representative monostable cases.
logic_path = root / logic
if logic_path.is_file():
    harness = r'''
import dev.redstoneengineering.signal.PulseShaperLogic;

public final class PulseShaperHarness {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var init = PulseShaperLogic.step(0, 8, 4, false,
                new PulseShaperLogic.State(false, false, 0));
        check(init.state().initialized(), "initializes");
        check(!init.outputHigh(), "initial sample does not emit pulse");

        var below = PulseShaperLogic.step(7, 8, 4, false, init.state());
        check(!below.acceptedTrigger(), "below threshold must not trigger");

        var rise = PulseShaperLogic.step(8, 8, 4, false, below.state());
        check(rise.acceptedTrigger(), "threshold crossing must trigger");
        check(rise.outputHigh(), "accepted trigger drives output");
        check(rise.state().remainingTicks() == 3, "width is loaded then one tick consumed");

        var lowWhileBusy = PulseShaperLogic.step(0, 8, 4, false, rise.state());
        var secondRiseBlocked = PulseShaperLogic.step(8, 8, 4, false, lowWhileBusy.state());
        check(secondRiseBlocked.suppressedTrigger(), "non-retriggerable mode suppresses busy retrigger");
        check(!secondRiseBlocked.acceptedTrigger(), "suppressed trigger is not accepted");

        var rInit = PulseShaperLogic.step(0, 8, 4, true,
                new PulseShaperLogic.State(false, false, 0));
        var rRise = PulseShaperLogic.step(8, 8, 4, true, rInit.state());
        var rLow = PulseShaperLogic.step(0, 8, 4, true, rRise.state());
        var rSecond = PulseShaperLogic.step(8, 8, 4, true, rLow.state());
        check(rSecond.acceptedTrigger(), "retriggerable mode accepts busy retrigger");
        check(rSecond.state().remainingTicks() == 3, "retrigger reloads configured width");

        System.out.println("PulseShaperLogic semantic harness: PASS");
    }
}
'''
    try:
        with tempfile.TemporaryDirectory(prefix="rse-pulse-") as td:
            td = Path(td)
            harness_path = td / "PulseShaperHarness.java"
            harness_path.write_text(harness)
            compile_run = subprocess.run(
                ["javac", "-d", str(td), str(logic_path), str(harness_path)],
                text=True, capture_output=True
            )
            if compile_run.returncode != 0:
                failed.append("javac semantic harness failed: " + (compile_run.stderr or compile_run.stdout).strip())
            else:
                execute = subprocess.run(
                    ["java", "-cp", str(td), "PulseShaperHarness"],
                    text=True, capture_output=True
                )
                if execute.returncode != 0:
                    failed.append("semantic harness failed: " + (execute.stderr or execute.stdout).strip())
    except FileNotFoundError:
        failed.append("javac/java unavailable for PulseShaper semantic harness")

if failed:
    print("RSE pulse-shaper engineering-depth verification: FAIL")
    for item in failed:
        print(" -", item)
    raise SystemExit(1)

print("RSE pulse-shaper engineering-depth verification: PASS")
print(" threshold-triggered monostable semantics: PASS")
print(" retriggerable/non-retriggerable behavior: PASS")
print(" accepted/suppressed trigger diagnostics: PASS")
print(" field HMI controls and evidence: PASS")
