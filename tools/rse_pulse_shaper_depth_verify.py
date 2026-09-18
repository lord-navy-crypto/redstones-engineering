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
entity = "src/main/java/dev/redstoneengineering/blockentity/PulseShaperBlockEntity.java"
registration = "src/main/java/dev/redstoneengineering/RedstoneEngineering.java"
menu = "src/main/java/dev/redstoneengineering/ui/menu/SignalProcessorMenu.java"
screen = "src/main/java/dev/redstoneengineering/client/ui/SignalProcessorScreen.java"

require(
    logic,
    "record State",
    "record Result",
    "public static Result step(",
    "acceptedTrigger",
    "suppressedTrigger",
    "retriggerable",
    "rearmThreshold",
    "schmittAbove",
)
require(
    block,
    "RETRIGGERABLE",
    "implements EntityBlock",
    "PulseShaperBlockEntity",
    "threshold(level, pos)",
    "hysteresis(level, pos)",
    "rearmThreshold(level, pos)",
    "stepHysteresis",
    "RedstoneObservationSupport.observe",
    "triggerCount",
    "suppressedTriggerCount",
    "lastTriggerAgeTicks",
    "PulseShaperLogic.step",
    "newBlockEntity",
    "RUNTIME_SIZE = 3",
)
require(
    entity,
    "class PulseShaperBlockEntity",
    "threshold",
    "hysteresis",
    "stepHysteresis",
    "acceptedTriggerCount",
    "suppressedTriggerCount",
    "lastTriggerTick",
    "loadAdditional",
    "saveAdditional",
    "setChanged",
)
require(
    registration,
    "PulseShaperBlockEntity",
    "PULSE_SHAPER_BLOCK_ENTITY",
    "BLOCK_ENTITY_TYPES.register(",
    '"pulse_shaper"',
)
require(
    menu,
    "BUTTON_THRESHOLD_PREVIOUS",
    "BUTTON_THRESHOLD_NEXT",
    "BUTTON_TOGGLE_RETRIGGER",
    "BUTTON_PULSE_HYSTERESIS_PREVIOUS",
    "BUTTON_PULSE_HYSTERESIS_NEXT",
    "PulseShaperBlock.threshold(level, blockPos)",
    "PulseShaperBlock.hysteresis(level, blockPos)",
    "PulseShaperBlock.stepThreshold",
    "PulseShaperBlock.stepHysteresis",
    "PulseShaperBlock.toggleRetriggerable",
)
require(
    screen,
    "Trigger threshold",
    "Hysteresis",
    "re-arm",
    "Retrigger",
    "Accepted triggers",
    "Suppressed triggers",
)

registration_text = (root / registration).read_text(errors="ignore") if (root / registration).is_file() else ""
if "BlockEntity;\\nimport dev.redstoneengineering.blockentity" in registration_text:
    failed.append("RedstoneEngineering.java contains an escaped import newline")

block_text = (root / block).read_text(errors="ignore") if (root / block).is_file() else ""
if "IntegerProperty THRESHOLD" in block_text:
    failed.append("PulseShaper threshold must not multiply BlockState variants")
if "builder.add(WIDTH, THRESHOLD" in block_text:
    failed.append("PulseShaper threshold leaked into BlockState definition")

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
        check(secondRiseBlocked.suppressedTrigger(),
                "non-retriggerable mode suppresses busy retrigger");
        check(!secondRiseBlocked.acceptedTrigger(),
                "suppressed trigger is not accepted");

        var rInit = PulseShaperLogic.step(0, 8, 4, true,
                new PulseShaperLogic.State(false, false, 0));
        var rRise = PulseShaperLogic.step(8, 8, 4, true, rInit.state());
        var rLow = PulseShaperLogic.step(0, 8, 4, true, rRise.state());
        var rSecond = PulseShaperLogic.step(8, 8, 4, true, rLow.state());
        check(rSecond.acceptedTrigger(),
                "retriggerable mode accepts busy retrigger");
        check(rSecond.state().remainingTicks() == 3,
                "retrigger reloads configured width");

        check(PulseShaperLogic.rearmThreshold(8, 2) == 6,
                "hysteresis 2 must re-arm two levels below trigger");
        var hInit = PulseShaperLogic.step(6, 8, 2, 4, true,
                new PulseShaperLogic.State(false, false, 0));
        var hRise = PulseShaperLogic.step(8, 8, 2, 4, true, hInit.state());
        check(hRise.acceptedTrigger(), "Schmitt trigger must fire at high threshold");
        var hBand = PulseShaperLogic.step(7, 8, 2, 4, true, hRise.state());
        check(hBand.state().lastAboveThreshold(),
                "input inside hysteresis band must stay latched high");
        check(!hBand.acceptedTrigger(),
                "hysteresis-band chatter must not create another trigger");
        var hRearm = PulseShaperLogic.step(6, 8, 2, 4, true, hBand.state());
        check(!hRearm.state().lastAboveThreshold(),
                "input at re-arm threshold must re-arm the one-shot");
        var hSecond = PulseShaperLogic.step(8, 8, 2, 4, true, hRearm.state());
        check(hSecond.acceptedTrigger(),
                "new high crossing after re-arm must trigger again");

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
                text=True,
                capture_output=True,
            )
            if compile_run.returncode != 0:
                failed.append(
                    "javac semantic harness failed: "
                    + (compile_run.stderr or compile_run.stdout).strip()
                )
            else:
                execute = subprocess.run(
                    ["java", "-cp", str(td), "PulseShaperHarness"],
                    text=True,
                    capture_output=True,
                )
                if execute.returncode != 0:
                    failed.append(
                        "semantic harness failed: "
                        + (execute.stderr or execute.stdout).strip()
                    )
    except FileNotFoundError:
        failed.append("javac/java unavailable for PulseShaper semantic harness")

if failed:
    print("RSE pulse-shaper engineering-depth verification: FAIL")
    for item in failed:
        print(" -", item)
    raise SystemExit(1)

print("RSE pulse-shaper engineering-depth verification: PASS")
print(" threshold-triggered Schmitt monostable semantics: PASS")
print(" hysteresis-band chatter rejection + re-arm behavior: PASS")
print(" retriggerable/non-retriggerable behavior: PASS")
print(" accepted/suppressed trigger diagnostics: PASS")
print(" field HMI controls and evidence: PASS")
print(" persistent threshold/evidence without high-cardinality BlockState: PASS")
