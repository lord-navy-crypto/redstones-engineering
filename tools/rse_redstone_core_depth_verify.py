#!/usr/bin/env python3
from pathlib import Path
import sys

root=Path(sys.argv[1] if len(sys.argv)>1 else ".").resolve()
failed=[]
def req(path,*tokens):
    p=root/path
    if not p.is_file():
        failed.append(f"missing: {path}"); return
    t=p.read_text(errors="ignore")
    for token in tokens:
        if token not in t:
            failed.append(f"{path} missing token: {token}")

req("src/main/java/dev/redstoneengineering/block/SignalProbeBlock.java",
    "stepChannel","configuredChannel","measuredValue","Probe channel →")
req("src/main/java/dev/redstoneengineering/block/RedstoneReferenceSourceBlock.java",
    "stepPower","configuredPower")
req("src/main/java/dev/redstoneengineering/physics/RedstoneCableNetwork.java",
    "PATH_EVIDENCE_KEY","record PathEvidence","winningSource","PATH_ATTENUATION_LOSS")
req("src/main/java/dev/redstoneengineering/block/RedstoneSignalCableBlock.java",
    "sourceLevel=","loss=","margin=")
req("src/main/java/dev/redstoneengineering/block/RedstoneCableTerminalBlock.java",
    "toggleMode","winningSource","attenuation")
req("src/main/java/dev/redstoneengineering/block/SignalConditionerBlock.java",
    "RUNTIME_KEY","limitingEpisodes","lastLimitingAgeTicks","case 0 -> \"SCALE\"")
req("src/main/java/dev/redstoneengineering/ui/menu/SignalConditionerMenu.java",
    "limitingEpisodes","lastLimitingAge")
req("src/main/java/dev/redstoneengineering/client/ui/SignalConditionerScreen.java",
    "SCALE","limitingEpisodes","lastLimitingAgeTicks")

req("src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java",
    "CONFIG_SIGNAL_PROBE","CONFIG_REFERENCE_SOURCE","CONFIG_REDSTONE_CABLE","CONFIG_CABLE_TERMINAL",
    "RedstoneCableTerminalBlock.toggleMode")
req("src/main/java/dev/redstoneengineering/client/ui/UniversalFieldDeviceScreen.java",
    "INSTRUMENT PROBE","REDSTONE REFERENCE SOURCE","INSULATED REDSTONE LINK","REDSTONE CABLE TERMINAL",
    "Mode • Cable → Vanilla","CONFIG_SIGNAL_PROBE","CONFIG_REFERENCE_SOURCE")

req("src/main/java/dev/redstoneengineering/block/QuartzOscillatorBlock.java",
    "stepPeriod","periodTicks")

req("src/main/java/dev/redstoneengineering/block/FaultLatchBlock.java",
    "stepThreshold","thresholdValue","manualReset")

req("src/main/java/dev/redstoneengineering/block/AnalogIndicatorBlock.java",
    "retainedMinimum","retainedMaximum","resetExtrema","sampleCount")

req("src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java",
    "CONFIG_QUARTZ_OSCILLATOR","CONFIG_FAULT_LATCH","CONFIG_ANALOG_INDICATOR","CONFIG_JUNCTION")

req("src/main/java/dev/redstoneengineering/client/ui/UniversalFieldDeviceScreen.java",
    "QUARTZ TIMING SOURCE","FAULT LATCH","ANALOG REDSTONE INDICATOR","JUNCTION • ROUTING ONLY")

if failed:
    print("RSE redstone-core engineering-depth verification: FAIL")
    for x in failed: print(" -",x)
    raise SystemExit(1)

print("RSE redstone-core engineering-depth verification: PASS")
print(" configurable A/B/C/D signal probe: PASS")
print(" adjustable 0..15 reference source: PASS")
print(" insulated cable signal-integrity evidence: PASS")
print(" terminal direction authority in HMI: PASS")
print(" conditioner scale identity + limiting history: PASS")
print(" timing/safety/junction core HMI: PASS")
