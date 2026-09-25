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
damper = "src/main/java/dev/redstoneengineering/block/HoneyVibrationDamperBlock.java"
menu = "src/main/java/dev/redstoneengineering/ui/menu/ProcessParameterMenu.java"
screen = "src/main/java/dev/redstoneengineering/client/ui/ProcessParameterNotebookScreen.java"

require(logic,
        "MIN_AMPLITUDE = 0",
        "MAX_AMPLITUDE = 15",
        "MIN_CONFIGURED_FREQUENCY = 1",
        "MAX_CONFIGURED_FREQUENCY = 15",
        "DEFAULT_CONFIGURED_FREQUENCY = 8",
        "MIN_RATE = 1",
        "MAX_RATE = 15",
        "DEFAULT_AMPLITUDE_RISE = 2",
        "DEFAULT_AMPLITUDE_FALL = 1",
        "DEFAULT_FREQUENCY_SLEW = 1",
        "CONTROL_TICK_TICKS = 1",
        "boundedAmplitude",
        "boundedRuntimeFrequency",
        "boundedConfiguredFrequency",
        "boundedRate",
        "fullScaleRampTicks",
        "run-up/coast-down",
        "approachAsymmetric",
        "targetAmp > 0",
        "settled")
require(block,
        'RUNTIME_KEY = "mechanical_exciter"',
        "ACTUAL_AMPLITUDE",
        "ACTUAL_FREQUENCY",
        "TARGET_AMPLITUDE",
        "MechanicalExciterLogic.step",
        "MechanicalExciterLogic.boundedRate(stored.a())",
        "MechanicalExciterLogic.boundedRate(stored.b())",
        "MechanicalExciterLogic.boundedRate(stored.c())",
        "MechanicalExciterLogic.boundedRate(rise)",
        "MechanicalExciterLogic.boundedRate(fall)",
        "MechanicalExciterLogic.boundedRate(frequencySlew)",
        "setConfiguredFrequency",
        "A powered exciter is a continuous mechanical source",
        "VibrationNetwork.propagate(level, pos",
        "level.scheduleTick(pos, this, 1)",
        "RuntimeIntStore.remove(level, RUNTIME_KEY, pos)",
        "public static PortQuality outputQuality",
        "drive.valid() ? PortQuality.NO_SIGNAL : drive.quality()")

require(damper,
        "localEnvelopeQuality",
        "localEnvelopeAgeTicks",
        'InformationRuntime.quality(level, "mech_wave", pos)',
        'InformationRuntime.ageTicks(level, "mech_wave", pos)')
require(menu,
        "HoneyVibrationDamperBlock.localEnvelopeQuality",
        "HoneyVibrationDamperBlock.localEnvelopeAgeTicks",
        "MechanicalExciterBlock.runTicks",
        "MechanicalExciterBlock.setConfiguredFrequency",
        "MechanicalExciterBlock.driveObservation",
        "MechanicalExciterBlock.outputQuality")
require(screen,
        '"Envelope quality"',
        '"Envelope age"',
        '"Run ticks"',
        '"Input quality"',
        '"Output quality"',
        "MechanicalExciterLogic.MIN_CONFIGURED_FREQUENCY",
        "MechanicalExciterLogic.MAX_CONFIGURED_FREQUENCY",
        "MechanicalExciterLogic.MIN_RATE",
        "MechanicalExciterLogic.MAX_RATE",
        "MechanicalExciterLogic.fullScaleRampTicks",
        "A[k+1]=toward(Acmd, Rrise/Rfall)",
        "low-quality retained envelope",
        "Actual amplitude may coast toward zero after command loss")

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
        check(MechanicalExciterLogic.boundedRate(0) == MechanicalExciterLogic.MIN_RATE,
                "rate lower bound");
        check(MechanicalExciterLogic.boundedRate(99) == MechanicalExciterLogic.MAX_RATE,
                "rate upper bound");
        check(MechanicalExciterLogic.boundedConfiguredFrequency(0)
                        == MechanicalExciterLogic.MIN_CONFIGURED_FREQUENCY,
                "configured frequency lower bound");
        check(MechanicalExciterLogic.boundedConfiguredFrequency(99)
                        == MechanicalExciterLogic.MAX_CONFIGURED_FREQUENCY,
                "configured frequency upper bound");
        check(MechanicalExciterLogic.fullScaleRampTicks(2) == 8,
                "15-level full-scale ramp at rate 2 takes 8 ticks");
        var fast = MechanicalExciterLogic.stepWithRates(
                15, 15, new MechanicalExciterLogic.State(0, 0), 99, 99, 99);
        check(fast.amplitude() == 15 && fast.frequency() == 15,
                "legacy over-range rates clamp to effective model maximum");

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
print(" exciter input/output quality + run evidence: PASS")
print(" configured dynamics stored/effective 1..15 contract: PASS")
print(" HMI finite-ramp timing derives from pure model authority: PASS")
print(" damper envelope quality/freshness evidence: PASS")
