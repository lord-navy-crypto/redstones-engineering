#!/usr/bin/env python3
"""Last 10 candidate shared-implementation integrity gate. Source test, NOT Minecraft runtime."""
from pathlib import Path
import sys
root=Path(sys.argv[1] if len(sys.argv)>1 else ".").resolve()
errors=[]
def read(p):
    f=root/p
    if not f.is_file():
        errors.append("missing "+p)
        return ""
    return f.read_text(encoding="utf-8")
base="src/main/java/dev/redstoneengineering/"
names=["LapisPrecisionMeterBlock","LapisOpticalTransducerBlock","TopologyDebuggerBlock",
       "OpticalEmitterBlock","OpticalReceiverBlock","RedstoneCableJunctionBlock",
       "CopperCableJunctionBlock","OpticalFiberJunctionBlock",
       "InstrumentCableBlock","ShieldedInstrumentCableBlock"]
src={n:read(base+"block/"+n+".java") for n in names}
helpers={n:read(base+"block/"+n+".java") for n in (
    "MeterEvidenceSupport","GuidedOpticalEvidenceSupport",
    "JunctionEvidenceSupport","InstrumentCableEvidenceSupport")}
game=read(base+"gametest/RseFinalTenIntegrityGameTests.java")
reg=read(base+"gametest/RseGameTestRegistration.java")
audit=read("tools/rse_46_shared_code_rollout_verify.py")
workflow=read(".github/workflows/build.yml")
def must(c,why):
    if not c: errors.append(why)

# Physical roles may NEVER be coalesced, but they must reuse pure evidence.
required={
 "LapisPrecisionMeterBlock":("MeterEvidenceSupport.snapshot(","PrecisionObservationSupport.lapis("),
 "LapisOpticalTransducerBlock":("MeterEvidenceSupport.readable(","OpticalObservationSupport.observe("),
 "TopologyDebuggerBlock":("MeterEvidenceSupport.alarmSnapshot(","recordTopologyTransition("),
 "OpticalEmitterBlock":("GuidedOpticalEvidenceSupport.source(","DomainNetwork.recomputeOptical("),
 "OpticalReceiverBlock":("GuidedOpticalEvidenceSupport.sink(","DomainNetwork.recomputeOpticalAround("),
 "RedstoneCableJunctionBlock":("JunctionEvidenceSupport.loadedPortQuality(","TransmissionTopology.inferJunctionMedium("),
 "CopperCableJunctionBlock":("JunctionEvidenceSupport.loadedPortQuality(","CopperNetworkSupport.recomputeAround("),
 "OpticalFiberJunctionBlock":("JunctionEvidenceSupport.loadedPortQuality(","setServiceOpen(Level level"),
 "InstrumentCableBlock":("InstrumentCableEvidenceSupport.snapshot(","TransmissionTopology.instrumentCablePort("),
 "ShieldedInstrumentCableBlock":("InstrumentCableEvidenceSupport.interference(","bus.shieldedExposedNodes()"),
}
for n,tokens in required.items():
    for t in tokens: must(t in src[n],n+" missing "+t)
for n,s in helpers.items():
    for forbidden in ("setBlock(", "RuntimeIntStore.get(", "DomainNetwork.drive", "level.scheduleTick("):
        must(forbidden not in s,n+" must be observer-only: "+forbidden)
must("return quality == PortQuality.VALID || quality == PortQuality.SATURATED;" in helpers["MeterEvidenceSupport"],
     "meter source gate no longer distinguishes valid/saturated from stale/conflict")
must("return usable(quality) ? Math.max(0, Math.min(maximum, value)) : 0;" in helpers["MeterEvidenceSupport"],
     "invalid measured payload must be masked to zero")
must("inputCount > 1 || driverCount > 1" in helpers["GuidedOpticalEvidenceSupport"]
     and "PortQuality.TOPOLOGY_ERROR" in helpers["GuidedOpticalEvidenceSupport"],
     "multiple guided-optical inputs or drivers must reject retained carrier")
must("drivers <= 1" in src["OpticalReceiverBlock"],
     "receiver must not mark contested carrier valid")
must("if (!level.hasChunkAt(neighborPos)) continue;" in src["OpticalReceiverBlock"],
     "guided receiver must not read unloaded neighboring optical fiber")
must("quality == PortQuality.VALID ? Math.max(0, Math.min(15, voltage)) : 0" in src["CopperCableJunctionBlock"],
     "copper junction cannot retain voltage with an invalid driver")
must("PortQuality.STALE" in helpers["JunctionEvidenceSupport"] and "level.hasChunkAt(pos.relative(side))" in helpers["JunctionEvidenceSupport"],
     "junction port must flag an unobserved connected arm")
must("level.hasChunkAt(pos.relative(side))" in helpers["InstrumentCableEvidenceSupport"]
     and "PortQuality.STALE" in helpers["InstrumentCableEvidenceSupport"],
     "instrument ports must not call partial coverage VALID")
must("scan.qualityForMask(0xF)" in helpers["InstrumentCableEvidenceSupport"],
     "instrument duplicate channel and scan budget must retain network's quality")
must("!level.hasChunkAt(targetPos(pos, state))) return 15;" in src["TopologyDebuggerBlock"],
     "unloaded target must assert fail-safe topology alarm")
must("!level.hasChunkAt(targetPos(pos, state))) {" in src["TopologyDebuggerBlock"],
     "unloaded target field inspection must not load target chunk")
must("level.hasChunkAt(target)" in src["TopologyDebuggerBlock"],
     "vanilla topology target classification must guard unavailable chunk")
must("setServiceOpen(Level level, BlockPos pos, boolean open)" in src["OpticalFiberJunctionBlock"],
     "optical service isolation must remain functional")
must('expected 40 shared-helper users' in audit and '"queued": len(rows)-len(set(wired) | set(enhanced) | set(hardened))' in audit,
     "last-ten audit must cover actual 46 candidates without relabeling incomplete ones")
for name in ("guidedOpticalReceiverConflictCannotExposeRetainedIntensity",
             "copperJunctionInvalidDriverSuppressesOldVoltage",
             "guidedOpticalZeroSettingIsValidButNotALiveCarrier",
             "serviceOpenSpliceExportsNoOpticalPorts"):
    must("void "+name+"(GameTestHelper helper)" in game,"missing GameTest "+name)
must(game.count("@GameTest(")==4,"expected exactly four targeted in-game tests")
must("event.register(RseFinalTenIntegrityGameTests.class);" in reg,
     "new game test suite not registered")
must("tools/rse_final_ten_integrity_verify.py" in workflow,"source gate absent in default CI")
if errors:
    print("RSE FINAL TEN INTEGRITY: FAIL")
    for e in errors: print(" - "+e)
    sys.exit(1)
print("RSE FINAL TEN INTEGRITY: PASS")
print(" 10 distinct block implementations use 4 pure observer evidence helpers")
print(" missing-chunk fail-closed measurement, guided carrier collision, service isolation: PASS")
print(" 4 in-game GameTests registered; runtime acceptance still NOT_RUN")
