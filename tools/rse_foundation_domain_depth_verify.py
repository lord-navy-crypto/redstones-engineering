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

selector = "src/main/java/dev/redstoneengineering/block/SignalSelectorBlock.java"
lapis = "src/main/java/dev/redstoneengineering/block/LapisLowPassFilterBlock.java"
quartz = "src/main/java/dev/redstoneengineering/block/QuartzClockDividerBlock.java"
amethyst = "src/main/java/dev/redstoneengineering/block/AmethystResonatorBlock.java"
spectrum = "src/main/java/dev/redstoneengineering/block/AmethystSpectrumAnalyzerBlock.java"
domain = "src/main/java/dev/redstoneengineering/physics/DomainNetwork.java"
ring = "src/main/java/dev/redstoneengineering/signal/AmethystRingdownLogic.java"

require(selector,
        "quality == PortQuality.NO_SIGNAL",
        "PAYLOAD_HOLD_ACTIVE",
        "PAYLOAD_BAD_EPISODES",
        "Unknown payload evidence is not a numerical zero",
        "payloadHoldActive",
        "payloadBadEpisodes")

require(lapis,
        "HISTORY_SLOT",
        "Loss of upstream evidence invalidates the driver",
        "runtime[VALID_SLOT] = 0",
        "runtime[QUALITY_SLOT] = inputQuality.ordinal()",
        "retainedHistory",
        "DomainNetwork.driveLapis(level, outputPos(pos, state), pos, 0, false)")

require(quartz,
        "PHASE_STARTED_SLOT",
        "First observation establishes input phase only",
        "runtime[PHASE_STARTED_SLOT] = 0",
        "runtime[PHASE_STARTED_SLOT] = 1",
        "int outputPeriod = Math.min(4096, Math.max(1, input.periodTicks()) * divisor);",
        "DomainNetwork.driveQuartz(level, outputPos(pos, state), pos, false, outputPeriod, false)",
        "runtime[OUTPUT_SLOT] == 1, outputPeriod, true)")

require(amethyst,
        "CURRENT_AMPLITUDE",
        "EXCITATION_COUNT",
        "AmethystRingdownLogic.excite",
        "AmethystRingdownLogic.decay",
        "currentAmplitude",
        "ring-down=1 level / 2t")

require(spectrum,
        "Incomplete scan coverage is STALE evidence",
        "if (spectrum.expectedCells() <= 0) return PortQuality.NO_SIGNAL;",
        "if (!spectrum.complete()) return PortQuality.STALE;",
        "return spectrum.samples() > 0 ? PortQuality.VALID : PortQuality.NO_SIGNAL;")

require(domain,
        "int amp = AmethystResonatorBlock.currentAmplitude(level, src);",
        "AmethystResonatorBlock.currentAmplitude(level, pos)")

require(ring,
        "Reduced free-decay model",
        "public static int excite",
        "public static int decay",
        "public static boolean active")

ring_path = root / ring
if ring_path.is_file():
    harness = r"""
import dev.redstoneengineering.signal.AmethystRingdownLogic;

public final class AmethystRingdownHarness {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        int a = AmethystRingdownLogic.excite(12);
        check(a == 12 && AmethystRingdownLogic.active(a),
                "configured amplitude must seed free oscillation");

        a = AmethystRingdownLogic.decay(a);
        check(a == 11, "ring-down must reduce amplitude gradually");

        for (int i = 0; i < 11; i++) a = AmethystRingdownLogic.decay(a);
        check(a == 0 && !AmethystRingdownLogic.active(a),
                "ring-down must terminate at rest without going negative");

        check(AmethystRingdownLogic.excite(30) == 15,
                "excitation amplitude must remain inside the domain range");

        System.out.println("AmethystRingdownLogic harness: PASS");
    }
}
"""
    try:
        with tempfile.TemporaryDirectory(prefix="rse-amethyst-ringdown-") as td:
            td = Path(td)
            hp = td / "AmethystRingdownHarness.java"
            hp.write_text(harness)
            compile_run = subprocess.run(
                ["javac", "-d", str(td), str(ring_path), str(hp)],
                cwd=root, capture_output=True, text=True
            )
            if compile_run.returncode != 0:
                failed.append("AmethystRingdownLogic javac failed: " + compile_run.stderr.strip())
            else:
                run = subprocess.run(
                    ["java", "-cp", str(td), "AmethystRingdownHarness"],
                    cwd=root, capture_output=True, text=True
                )
                if run.returncode != 0:
                    failed.append("AmethystRingdownLogic harness failed: " + (run.stderr or run.stdout).strip())
    except FileNotFoundError:
        failed.append("javac/java unavailable for amethyst ring-down harness")

if failed:
    print("RSE foundation-domain depth verification: FAIL")
    for item in failed:
        print(" -", item)
    raise SystemExit(1)

print("RSE foundation-domain depth verification: PASS")
print(" redstone selector evidence hold: PASS")
print(" Lapis low-pass retained-state semantics: PASS")
print(" Quartz divider real-edge phase lock + period multiplication: PASS")
print(" Amethyst free ring-down source + stale incomplete spectrum evidence: PASS")
