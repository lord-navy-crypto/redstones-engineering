#!/usr/bin/env python3
"""RSE industrial safety and data carrier quality regressions.
Does not claim that compiling registered GameTests runs them in Minecraft.
"""
from pathlib import Path
import sys

root=Path(sys.argv[1] if len(sys.argv)>1 else ".").resolve()
java=root/"src/main/java/dev/redstoneengineering"
errors=[]
def read(file):
    p=java/file
    if not p.is_file():
        errors.append("missing "+str(file));return ""
    return p.read_text(encoding="utf-8")
def ensure(test,why):
    if not test:errors.append(why)
safety=read("block/SafetyObservationSupport.java")
interlock=read("block/SafetyInterlockBlock.java")
watchdog=read("block/WatchdogBlock.java")
latch=read("block/FaultLatchBlock.java")
alarm=read("block/AlarmProcessorBlock.java")
comm=read("block/CommunicationLineEvidenceSupport.java")
serial=read("block/SerialDataLineBlock.java")
diff=read("block/DifferentialDataPairBlock.java")
tests=read("gametest/RseSafetyCommunicationUpgradeGameTests.java")
registry=read("gametest/RseGameTestRegistration.java")

ensure("observation.valid() && observation.value() > 0" in safety,
       "fail-closed requires a real VALID positive signal")
ensure("permissive(a) ? 0 : 1" in safety and "permissive(b) ? 0 : 2" in safety
       and "permissive(c) ? 0 : 4" in safety,"three explicit input-failure bits lost")
ensure("SafetyObservationSupport.failedMask(a, b, c)" in interlock
       and "SafetyObservationSupport.inputPort(level, pos, side, port.get())" in interlock,
       "safety interlock compute/port must use authoritative source quality")
ensure("hit.getDirection() == Direction.DOWN" in interlock
       and "resetDiagnostics(level, pos)" in interlock,
       "interlock must separate non-destructive inspection from reset")
for source,name in ((watchdog,"watchdog"),(latch,"fault latch"),(alarm,"alarm")):
    ensure("SafetyObservationSupport.input(" in source
           and "getDirection() == Direction.DOWN" in source,
           name+" missing read-only quality-aware maintenance inspection")
ensure("resetDiagnostics(l, p)" in watchdog
       and "heartbeat.valid()" in watchdog
       and "rt[SOURCE_SEEN] = 0" in watchdog,
       "watchdog transition baseline or reset path lost")
ensure("manualReset(level, pos)" in latch
       and "Fault latch manual reset" in latch,
       "fault latch manual reset path lost")
ensure("acknowledge(level, pos)" in alarm
       and "SystemEventTimeline.record(" in alarm,
       "alarm acknowledgement/event semantics lost")
ensure("PortQuality.STALE : latched(level, pos) ? PortQuality.FAULT : PortQuality.VALID" in alarm,
       "alarm output must distinguish uninitialized / latched / cleared")
ensure("SafetyObservationSupport.inputPort(level, pos, side, port.get())" in alarm,
       "alarm controls must report actual source quality")

for token in ("InformationRuntime.snapshot(level, carrier, pos)",
              "quality == PortQuality.VALID", "case TOPOLOGY_ERROR",
              "case STALE", "case NO_SIGNAL"):
    ensure(token in comm, "shared bit/byte evidence missing "+token)
for src,name,medium,solver,drivers in (
        (serial,"serial","\"serial\"","SerialNetwork.recompute(level, pos)","info.driverCount()"),
        (diff,"differential","\"diff\"","DifferentialNetwork.recompute(level, pos)",
         "DifferentialNetwork.driverCount(level, pos)")):
    ensure("CommunicationLineEvidenceSupport.snapshot(" in src
           and "CommunicationLineEvidenceSupport.report(" in src,
           name+" lost shared inspector and normalized snapshot")
    ensure(medium in src and solver in src and drivers in src,
           name+" lost distinct solver/driver-count contract")
ensure("maximum" not in serial or "255" in serial,
       "serial byte must preserve 0..255 magnitude")
ensure("0xFF, 255" in serial and '"diff", 1, 1' in diff,
       "serial and differential payload ranges must remain distinct")
for helper in (safety,comm):
    for forbidden in ("setBlock(", "scheduleTick(", "RuntimeIntStore.get(", "getBlockEntity(",
                      "SystemEventTimeline.record(", "InformationRuntime.write("):
        ensure(forbidden not in helper, "observation helper must not mutate runtime: "+forbidden)
for name in ("interlockRequiresThreeValidAssertedRedstoneSources",
             "communicationCableInspectionIsObserverNeutralBeforeNetworkSolve"):
    ensure("void "+name+"(GameTestHelper helper)" in tests,"missing runtime witness "+name)
ensure("event.register(RseSafetyCommunicationUpgradeGameTests.class);" in registry,
       "new GameTests not registered")
workflow=(root/".github/workflows/build.yml").read_text()
ensure("tools/rse_safety_communication_upgrade_verify.py" in workflow,
       "CI missing safety & communication gate")
if errors:
    print("RSE SAFETY + COMMUNICATION UPGRADE: FAIL")
    for e in errors:print(" -",e)
    sys.exit(1)
print("RSE SAFETY + COMMUNICATION UPGRADE: PASS")
print("  interlock source validity / fail-closed 3-bit mask: PASS")
print("  watchdog, latch, alarm, interlock independent read-only inspections: PASS")
print("  serial and differential quality-aware bounded readout: PASS")
print("  manual reset / acknowledgement lifecycle retained: PASS")
print("  two GameTests registered; execution requires Minecraft test server")
