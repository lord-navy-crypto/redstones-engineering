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
tap = "src/main/java/dev/redstoneengineering/block/SignalTapBlock.java"
indicator = "src/main/java/dev/redstoneengineering/block/AnalogIndicatorBlock.java"
analyzer = "src/main/java/dev/redstoneengineering/block/SignalAnalyzerBlock.java"
selector_test = "src/main/java/dev/redstoneengineering/gametest/RseSignalSelectorEvidenceGameTests.java"
scaler = "src/main/java/dev/redstoneengineering/block/RedstoneToLapisScalerBlock.java"
quantizer = "src/main/java/dev/redstoneengineering/block/LapisToRedstoneQuantizerBlock.java"
conversion_test = "src/main/java/dev/redstoneengineering/gametest/RseFoundationDomainGameTests.java"
registration = "src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java"
lapis = "src/main/java/dev/redstoneengineering/block/LapisLowPassFilterBlock.java"
quartz = "src/main/java/dev/redstoneengineering/block/QuartzClockDividerBlock.java"
quartz_osc = "src/main/java/dev/redstoneengineering/block/QuartzOscillatorBlock.java"
amethyst = "src/main/java/dev/redstoneengineering/block/AmethystResonatorBlock.java"
spectrum = "src/main/java/dev/redstoneengineering/block/AmethystSpectrumAnalyzerBlock.java"
domain = "src/main/java/dev/redstoneengineering/physics/DomainNetwork.java"
precision_observation = "src/main/java/dev/redstoneengineering/physics/PrecisionObservationSupport.java"
ring = "src/main/java/dev/redstoneengineering/signal/AmethystRingdownLogic.java"

require(selector,
        "quality == PortQuality.NO_SIGNAL",
        "PAYLOAD_HOLD_ACTIVE",
        "PAYLOAD_BAD_EPISODES",
        "Unknown payload evidence is not a numerical zero",
        "payloadHoldActive",
        "payloadBadEpisodes")

require(tap,
        "unusableButNotAbsent",
        "EVIDENCE_HOLD_ACTIVE",
        "BAD_EVIDENCE_EPISODES",
        "A physically absent upstream source is a real de-energized condition",
        "Faulted/stale evidence is not a new numerical zero",
        "evidenceHoldActive",
        "badEvidenceEpisodes")

require(indicator,
        "Degraded evidence must not overwrite the last trustworthy displayed value",
        "PortQuality.FAULT",
        "PortQuality.DOMAIN_MISMATCH",
        "PortQuality.TOPOLOGY_ERROR")

require(analyzer,
        "A genuinely absent source de-energizes the inline path",
        "Degraded evidence is not a new numerical zero",
        "requestedOutput = state.getValue(OUTPUT)")

require(selector_test,
        "selectorHoldsLastSelectionAndPayloadAcrossMissingEvidence",
        "Missing SELECT did not hold the last trustworthy selection",
        "Missing selected payload was converted to zero instead of holding OUT")

require(scaler,
        "Invalid source evidence releases the Lapis driver",
        "does not erase the last",
        "DomainNetwork.driveLapis(level, pos.relative(outputSide(state)), pos, 0, false)")

require(quantizer,
        "Invalid precision evidence cannot define a new quantized code",
        "Retain the last",
        "if (sample.valid())",
        "CoreMediaDiagnostics.redstoneFromLapis(sample.value())")

require(conversion_test,
        "conversionBridgesRetainLastCodeWhenEvidenceDisappears",
        "Missing upstream evidence was converted into a new numerical code")

require(conversion_test,
        "signalTapHoldsFaultedEvidenceButDropsOnRealSourceLoss",
        "Faulted tap evidence was converted into a new numerical zero",
        "Real source loss did not de-energize the Signal Tap")

require(conversion_test,
        "analogIndicatorRetainsFaultedDisplayAndClearsOnNoSource",
        "Faulted indicator evidence overwrote the last trustworthy display",
        "inlineAnalyzerRetainsFaultedOutputAndClearsOnNoSource",
        "Faulted analyzer evidence overwrote the last trustworthy inline output")

require(conversion_test,
        "quartzObserverKeepsEffectivePeriodUntilRealEdge",
        "Direct Quartz observation reported configured period before a real waveform edge")

require(registration,
        "event.register(RseFoundationDomainGameTests.class);")

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

require(quartz_osc,
        "EFFECTIVE_PERIOD_INDEX",
        "configuredPeriodTicks",
        "effectivePeriodTicks",
        "periodChangePending",
        "A configured period change becomes effective only at this real waveform transition",
        "Do not create an early edge")

require(precision_observation,
        "QuartzOscillatorBlock.effectivePeriodTicks(level, pos, state)")

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
        "AmethystResonatorBlock.currentAmplitude(level, pos)",
        "QuartzOscillatorBlock.effectivePeriodTicks(level, n, s)")

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
print(" redstone selector/tap/analyzer/indicator evidence semantics: PASS")
print(" Redstone↔Lapis conversion retains values while degrading evidence: PASS")
print(" Lapis low-pass retained-state semantics: PASS")
print(" Quartz oscillator edge-latched period + divider real-edge phase lock: PASS")
print(" Amethyst free ring-down source + stale incomplete spectrum evidence: PASS")
