#!/usr/bin/env python3
"""Phase 7: frequency-domain, no-unloaded-reads and source-correct Amethyst observations.

Source gate only; Minecraft GameTests are registered but NOT executed here.
"""
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors = []
def src(rel):
    path = root / rel
    if not path.is_file():
        errors.append("missing source: " + rel)
        return ""
    return path.read_text(encoding="utf-8")
base = "src/main/java/dev/redstoneengineering/"
common = src(base+"block/AmethystFrequencySupport.java")
source = src(base+"block/AmethystResonatorBlock.java")
filter_ = src(base+"block/AmethystFrequencyFilterBlock.java")
tuned = src(base+"block/AmethystTunedResonatorBlock.java")
network = src(base+"physics/DomainNetwork.java")
hmi = src(base+"ui/menu/FieldDeviceMenu.java")
tests = src(base+"gametest/RseAmethystFrequencyIntegrityGameTests.java")
registration = src(base+"gametest/RseGameTestRegistration.java")
workflow = src(".github/workflows/build.yml")
def require(ok, why):
    if not ok: errors.append(why)

for t in ("if (!level.hasChunkAt(samplePos)) return PortQuality.STALE;",
          "case FREQUENCY_CONFLICT -> PortQuality.TOPOLOGY_ERROR;",
          "case STALE -> PortQuality.STALE;",
          "case IDLE -> PortQuality.NO_SIGNAL;",
          "sample.active() ? PortQuality.VALID : PortQuality.NO_SIGNAL;",
          "value > 0 ? PortQuality.VALID : PortQuality.NO_SIGNAL",
          "if (upstream == PortQuality.TOPOLOGY_ERROR || upstream == PortQuality.STALE)",
          "return upstream;", "if (upstream != PortQuality.VALID) return PortQuality.NO_SIGNAL;",
          "quality == PortQuality.VALID || quality == PortQuality.SATURATED"):
    require(t in common, "shared quality boundary missing: "+t)
for forbidden in ("setBlock(", "RuntimeIntStore.get(", "DomainNetwork.driveAmethyst("):
    require(forbidden not in common, "shared observation helper must not mutate physics: "+forbidden)
require("AmethystFrequencySupport.sourcePort(" in source,
        "resonator source amplitude must distinguish configured vs active pulse")
for label,body in (("filter",filter_),("tuned",tuned)):
    require("AmethystFrequencySupport.qualityAt(" in body
            and "AmethystFrequencySupport.outputQuality(" in body
            and "AmethystFrequencySupport.readableAmplitude(" in body,
            label+" lost shared source/quality/zero-on-invalid rules")
    require("level.hasChunkAt(samplePos)" in body
            and "new DomainNetwork.AmethystSample(false, 0, 0)" in body,
            label+" must never load an uncovered input or output neighbor")
    require("private static PortQuality qualityAt(" not in body,
            label+" retains independently drifting quality classifier")
require("if (!level.hasChunkAt(pos)) return new AmethystSample(false, 0, 0);" in network,
        "all passive Amethyst sampling must reject missing chunk")
require("if (diff == 0) raw = input.amplitude() + q * 2;" in tuned
        and "else if (diff <= bandwidth) raw = input.amplitude() - Math.max(1, diff * q);" in tuned,
        "tuned resonance gain/bandwidth physics must remain independent")
require("input.active() && input.frequency() == state.getValue(TARGET)" in filter_
        and "Math.max(0, input.amplitude() - 1)" in filter_,
        "exact matching filter must keep separate frequency and loss physics")
require("var observed = filter.engineeringSnapshot(" in hmi
        and "var observed = tuned.engineeringSnapshot(" in hmi,
        "Field HMI must use quality-aware ports rather than retained adjacent trace value")
for method in ("filterCannotPresentRetainedAmplitudeFromConflictedInput",
               "tunedResonatorCannotPresentRetainedAmplitudeFromStaleInput",
               "sourcePortDistinguishesConfiguredAmplitudeFromAnActualPulse"):
    require("void "+method+"(GameTestHelper helper)" in tests,
            "missing registered GameTest witness: "+method)
require(tests.count("@GameTest(") == 3, "require exactly 3 new Amethyst GameTests")
require("event.register(RseAmethystFrequencyIntegrityGameTests.class);" in registration,
        "Amethyst GameTests unregistered")
require("tools/rse_amethyst_frequency_integrity_verify.py" in workflow,
        "Amethyst quality gate not in default CI")
if errors:
    print("RSE AMETHYST FREQUENCY INTEGRITY: FAIL")
    for e in errors: print(" -",e)
    sys.exit(1)
print("RSE AMETHYST FREQUENCY INTEGRITY: PASS")
print(" Three devices share read-only frequency source quality; distinct transfer retained")
print(" Missing chunk STALE; conflicted/stale output amplitudes masked, HMI consistent")
print(" Three registered GameTests: PRESENT, NOT RUN IN STATIC GATE")
