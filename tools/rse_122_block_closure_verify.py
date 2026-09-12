#!/usr/bin/env python3
"""Static closure gate for the audited 122-block core plus systems extensions."""
from pathlib import Path
import re, subprocess, sys
ROOT=Path(__file__).resolve().parents[1]
BLOCK=ROOT/"src/main/java/dev/redstoneengineering/block"; GT=ROOT/"src/main/java/dev/redstoneengineering/gametest/RseTotalAuditClosureGameTests.java"; REG=ROOT/"src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java"; WORKFLOW=ROOT/".github/workflows/build.yml"; TOTAL_AUDIT=ROOT/"tools/rse_122_block_total_audit.py"; SYSTEMS_VERIFY=ROOT/"tools/rse_engineering_systems_verify.py"
errors=[]
def read(p):
    if not p.exists(): errors.append(f"missing {p.relative_to(ROOT)}"); return ""
    return p.read_text(encoding="utf-8")
def req(s,n,l):
    if n not in s: errors.append(f"{l}: missing {n!r}")
def allreq(s,ns,l):
    for n in ns:req(s,n,l)
signal=read(BLOCK/"SignalAnalyzerBlock.java"); scope=read(BLOCK/"OscilloscopeBlock.java"); logic=read(BLOCK/"LogicAnalyzerBlock.java"); iron=read(BLOCK/"IronCoreBlock.java"); magnet=read(BLOCK/"PermanentMagnetBlock.java"); field=read(BLOCK/"MagneticFieldSensorBlock.java"); gradient=read(BLOCK/"MagneticGradientMeterBlock.java"); directional=read(BLOCK/"DirectionalSignalBlock.java"); precision=read(BLOCK/"PrecisionFilterBlock.java"); pwm=read(BLOCK/"PwmControllerBlock.java"); sample=read(BLOCK/"SampleHoldBlock.java"); gt=read(GT); reg=read(REG); workflow=read(WORKFLOW); total=read(TOTAL_AUDIT)
allreq(signal,("implements EngineeringPortProvider","EngineeringDomain.REDSTONE",'"TAP APERTURE"','"INLINE OUT"',"RuntimeIntStore.remove(level, KEY, pos)","canConnectRedstone("),"SignalAnalyzerBlock.java")
for name,src in (("OscilloscopeBlock.java",scope),("LogicAnalyzerBlock.java",logic)): allreq(src,("implements EntityBlock, EngineeringPortProvider","EngineeringDomain.INSTRUMENT_BUS","PortKind.BUS","PortDirection.INPUT","engineeringSnapshot(","canConnectRedstone("),name)
allreq(iron,("implements EngineeringPortProvider","EngineeringDomain.IRON_MAGNETIC",'"MAGNETIC COUPLING "',"PortDirection.BIDIRECTIONAL"),"IronCoreBlock.java"); allreq(magnet,("implements EngineeringPortProvider","EngineeringDomain.IRON_MAGNETIC",'"MAGNETIC FIELD "',"PortDirection.OUTPUT"),"PermanentMagnetBlock.java"); allreq(field,("implements EngineeringPortProvider","EngineeringDomain.IRON_MAGNETIC",'"MAGNETIC APERTURE "',"PortKind.MEASUREMENT"),"MagneticFieldSensorBlock.java"); allreq(gradient,("implements EngineeringPortProvider","EngineeringDomain.IRON_MAGNETIC",'"GRADIENT APERTURE "',"PortKind.MEASUREMENT"),"MagneticGradientMeterBlock.java")
allreq(directional,("implements EngineeringPortProvider","EngineeringDomain.REDSTONE","engineeringSnapshot(","canConnectRedstone("),"DirectionalSignalBlock.java"); req(precision,"class PrecisionFilterBlock extends DirectionalSignalBlock","PrecisionFilterBlock.java")
allreq(pwm,("class PwmControllerBlock extends DirectionalSignalBlock",'"COMMAND IN"','"PWM OUT"','"INHIBIT"',"PortKind.SAFETY","engineeringSnapshot("),"PwmControllerBlock.java"); allreq(sample,("class SampleHoldBlock extends DirectionalSignalBlock",'"VALUE IN"','"HELD OUT"','"TRIGGER"','"RESET"',"PortKind.TRIGGER","PortKind.RESET","engineeringSnapshot("),"SampleHoldBlock.java")
count=len(re.findall(r"@GameTest\s*\(",gt))
if count!=4: errors.append(f"RseTotalAuditClosureGameTests.java: expected exactly 4 @GameTest methods, found {count}")
req(reg,"event.register(RseTotalAuditClosureGameTests.class);","RseGameTestRegistration.java"); req(workflow,"tools/rse_122_block_total_audit.py","build.yml"); req(workflow,"tools/rse_122_block_closure_verify.py","build.yml"); req(workflow,"tools/rse_alarm_topology_verify.py","build.yml"); req(workflow,"Too many chained neighbor updates","build.yml"); req(total,"EXPECTED_REGISTERED = 122","rse_122_block_total_audit.py"); req(total,'"pid_controller"',"rse_122_block_total_audit.py")
if not SYSTEMS_VERIFY.exists(): errors.append("missing tools/rse_engineering_systems_verify.py")
else:
    p=subprocess.run([sys.executable,str(SYSTEMS_VERIFY)],cwd=ROOT,text=True,capture_output=True,check=False)
    if p.stdout: print(p.stdout,end="")
    if p.stderr: print(p.stderr,end="",file=sys.stderr)
    if p.returncode!=0: errors.append(f"engineering systems verifier failed with exit code {p.returncode}")
if errors:
    print("RSE 128-BLOCK AGGREGATE CLOSURE VERIFY: FAIL")
    for e in errors: print(" -",e)
    sys.exit(1)
print("RSE 128-BLOCK AGGREGATE CLOSURE VERIFY: PASS")
print("  historical deep-audit core: 122")
print("  systems extension: 6")
print("  systems closure: sequence controller, safety interlock, fault injector, alarm processor, topology debugger, engineering compass")
print("  neighbor-update storm runtime gate: present")
print("  legacy closure GameTests: 4")
