#!/usr/bin/env python3
"""Static gates for Quartz phase and source-aware sample/hold improvements.

This is not runtime GameTest acceptance. The actual registered GameTests must
be executed in Minecraft to confirm final world behavior.
"""
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors = []
def source(path):
    file = root / path
    if not file.exists():
        errors.append("missing source: " + path)
        return ""
    return file.read_text(encoding="utf-8")
base = "src/main/java/dev/redstoneengineering/"
clock = source(base+"block/QuartzClockSupport.java")
osc = source(base+"block/QuartzOscillatorBlock.java")
lab = source(base+"block/QuartzLabOscillatorBlock.java")
hold = source(base+"block/SampleHoldBlock.java")
sampler = source(base+"block/QuartzTriggeredLapisSamplerBlock.java")
tests = source(base+"gametest/RseQuartzSamplingUpgradeGameTests.java")
reg = source(base+"gametest/RseGameTestRegistration.java")
workflow = source(".github/workflows/build.yml")

def require(ok, message):
    if not ok: errors.append(message)

require("new EngineeringPortSnapshot(port, active ? 1.0 : 0.0, 0.0, 1.0, PortQuality.VALID)" in clock,
        "Quartz outgoing port must represent actual low/high level on the [0,1] scale")
require("QuartzTimingLineBlock.periodTicks(periodIndex) / 2" in clock,
        "common nominal half-period rule missing")
require("QuartzClockSupport.phasePort(port, s.getValue(ACTIVE))" in osc,
        "ordinary Quartz oscillator no longer uses phase-only observation")
require("QuartzClockSupport.phasePort(port, state.getValue(ACTIVE))" in lab,
        "laboratory oscillator still reports nominal period as clock amplitude")
require('PortKind.TRIGGER, PortDirection.OUTPUT, false, "clock");' in lab,
        "lab oscillator port unit must describe clock phase, not nominal ticks")
require("QuartzClockSupport.halfPeriodTicks" in osc and "QuartzClockSupport.halfPeriodTicks" in lab,
        "two oscillator timers have drifted apart")
require("QuartzClockSupport.fieldReport(l, p, s)" in osc
        and "hit.getDirection() == Direction.DOWN" in osc,
        "clock must offer a non-mutating bottom-face inspection")
require("QuartzClockSupport.fieldReport(level, pos, state)" in lab
        and "FieldDeviceUi.openUniversal(serverPlayer, pos)" in lab,
        "lab clock should preserve original HMI with explicit timing diagnostics")
require("timingEvidence(Level level, BlockPos pos, BlockState state)" in lab
        and "runtime[EVIDENCE_VALID_SLOT] != 1" in lab,
        "jitter evidence must remain explicit NOT_READY when not observed")
require("RedstoneObservationSupport.observe(level, pos, side)" in hold
        and "input.quality()" in hold
        and "outputQuality(level, pos)" in hold,
        "sample-hold snapshots must distinguish unconnected, zero and held output")
require("hitResult.getDirection() == Direction.DOWN" in hold
        and "clearHeldValue(level, pos)" in hold,
        "non-mutating inspector must be separate from existing clear-held shortcut")
require("WAITING_FOR_VALID_CLOCK" in sampler and "clock.quality()" in sampler
        and "heldQuality(level, pos)" in sampler
        and "runtime[CLOCK_SEEN] == 0" in sampler,
        "Quartz/Lapis sampler must expose baseline/held evidence without changing edge gate")
for marker in ("void quartzLabPortReportsPhaseNotNominalPeriod(",
               "void sampleHoldSeparatesUnconnectedInputFromDrivenZeroWithoutRuntimeMutation(",
               "PortQuality.NO_SIGNAL", "PortQuality.VALID", "RuntimeIntStore.entryCount"):
    require(marker in tests, "missing runtime test witness: " + marker)
require("event.register(RseQuartzSamplingUpgradeGameTests.class);" in reg,
        "new GameTests must be registered in the runtime suite")
require("tools/rse_quartz_sampling_upgrade_verify.py" in workflow,
        "static contract must run on every build")
for forbidden in ("setBlock(", "RuntimeIntStore.get(", "scheduleTick(", "recomputeQuartz("):
    require(forbidden not in clock,
            "QuartzClockSupport inspection must not mutate world: " + forbidden)
if errors:
    print("RSE QUARTZ/SAMPLING UPGRADE: FAIL")
    for e in errors: print(" -",e)
    sys.exit(1)
print("RSE QUARTZ/SAMPLING UPGRADE: PASS")
print(" Clock source-level phase 0/1, half-period and inspection route: PASS")
print(" Redstone sample-hold real source-quality readout / no-op inspection: PASS")
print(" Quartz/Lapis edge baseline and held-sample evidence: PASS")
print(" Two registered Minecraft runtime GameTests: PRESENT (not executed by this check)")
