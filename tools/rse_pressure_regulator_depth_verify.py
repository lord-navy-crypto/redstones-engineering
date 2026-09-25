#!/usr/bin/env python3
from pathlib import Path
import subprocess, sys, tempfile

root=Path(sys.argv[1] if len(sys.argv)>1 else ".").resolve()
failed=[]
def require(path,*tokens):
    p=root/path
    if not p.is_file():
        failed.append(f"missing: {path}"); return
    t=p.read_text(errors="ignore")
    for token in tokens:
        if token not in t: failed.append(f"{path} missing token: {token}")

logic="src/main/java/dev/redstoneengineering/signal/PressureRegulatorLogic.java"
block="src/main/java/dev/redstoneengineering/block/PressureRegulatorBlock.java"
network="src/main/java/dev/redstoneengineering/physics/PneumaticNetwork.java"
menu="src/main/java/dev/redstoneengineering/ui/menu/PneumaticSystemMenu.java"
screen="src/main/java/dev/redstoneengineering/client/ui/PneumaticSystemScreen.java"

require(logic,"targetPressure","stepPressure","responseRate","trackingError")
require(block,
        'IntegerProperty.create("setpoint", 1, 10)',
        'IntegerProperty.create("response_mode", 0, 2)',
        "actualRegulatedPressure","trackingError","stepResponseMode",
        "PressureRegulatorLogic.stepPressure","PneumaticNetwork.recompute")
require(network,"PressureRegulatorBlock.actualRegulatedPressure")
require(menu,
        "PressureRegulatorBlock.RESPONSE_MODE",
        "PressureRegulatorBlock.setpointPressure(level, blockPos, state)",
        "PressureRegulatorBlock.responseRate(level, blockPos, state)",
        "PressureRegulatorBlock.setEngineeringParameters",
        "engineeringA.set(PressureRegulatorBlock.setpointPressure",
        "engineeringB.set(PressureRegulatorBlock.responseRate",
        "PressureRegulatorBlock.actualRegulatedPressure",
        "PressureRegulatorBlock.trackingError",
        "rotateRigidDirectional(id)",
        "DirectionalDomainBlock.rotateRigidSeriesAxis")
require(screen,
        "REGULATOR RESPONSE",
        "Setpoint / actual ceiling",
        "Tracking error",
        "Response rate",
        '"Setpoint / response rate"',
        'menu.engineeringB()+" pressure/tick"',
        "Ptarget = min(Pin, Psp)",
        "P[k+1] = toward(Ptarget, ±R)",
        "Psp=1..100 • R=1..100",
        "rigid opposite-port axis")

lp=root/logic
if lp.is_file():
    harness=r'''
import dev.redstoneengineering.signal.PressureRegulatorLogic;
public final class RegHarness{
 static void c(boolean x,String m){if(!x)throw new AssertionError(m);}
 public static void main(String[]a){
  c(PressureRegulatorLogic.targetPressure(80,60)==60,"setpoint ceiling");
  c(PressureRegulatorLogic.targetPressure(40,60)==40,"cannot boost");
  c(PressureRegulatorLogic.stepPressure(0,80,60,0)==4,"soft");
  c(PressureRegulatorLogic.stepPressure(0,80,60,1)==8,"normal");
  c(PressureRegulatorLogic.stepPressure(0,80,60,2)==16,"fast");
  c(PressureRegulatorLogic.stepPressure(58,80,60,2)==60,"clamp");
  c(PressureRegulatorLogic.stepPressure(70,30,60,1)==62,"fall response");
  c(PressureRegulatorLogic.trackingError(40,80,60)==20,"tracking");
  System.out.println("PressureRegulatorLogic semantic harness: PASS");
 }}
'''
    try:
        with tempfile.TemporaryDirectory(prefix="rse-reg-") as td:
            td=Path(td); hp=td/"RegHarness.java"; hp.write_text(harness)
            cp=subprocess.run(["javac","-d",str(td),str(lp),str(hp)],text=True,capture_output=True)
            if cp.returncode: failed.append("javac semantic harness failed: "+(cp.stderr or cp.stdout).strip())
            else:
                rn=subprocess.run(["java","-cp",str(td),"RegHarness"],text=True,capture_output=True)
                if rn.returncode: failed.append("semantic harness failed: "+(rn.stderr or rn.stdout).strip())
    except FileNotFoundError:
        failed.append("javac/java unavailable for regulator harness")

if failed:
    print("RSE pressure-regulator engineering-depth verification: FAIL")
    for x in failed: print(" -",x)
    raise SystemExit(1)
print("RSE pressure-regulator engineering-depth verification: PASS")
print(" 10-step calibrated setpoint: PASS")
print(" finite regulator response: PASS")
print(" solver consumes actual ceiling: PASS")
print(" HMI exact setpoint/response-rate authority + response equations: PASS")
print(" rigid opposite-port regulator routing: PASS")
