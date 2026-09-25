#!/usr/bin/env python3
from pathlib import Path
import subprocess, sys, tempfile

root=Path(sys.argv[1] if len(sys.argv)>1 else ".").resolve()
failed=[]
def req(path,*tokens):
    p=root/path
    if not p.is_file(): failed.append(f"missing: {path}"); return
    t=p.read_text(errors="ignore")
    for token in tokens:
        if token not in t: failed.append(f"{path} missing token: {token}")

logic="src/main/java/dev/redstoneengineering/signal/ElectromagnetLogic.java"
block="src/main/java/dev/redstoneengineering/block/ElectromagnetBlock.java"
menu="src/main/java/dev/redstoneengineering/ui/menu/MagneticSystemMenu.java"
screen="src/main/java/dev/redstoneengineering/client/ui/MagneticSystemScreen.java"
opener="src/main/java/dev/redstoneengineering/ui/FieldDeviceUi.java"

req(logic,
    "MIN_FIELD = 0",
    "MAX_FIELD = 15",
    "MIN_RESPONSE_RATE = 1",
    "MAX_RESPONSE_RATE = 15",
    "DEFAULT_RISE_RATE = 2",
    "DEFAULT_FALL_RATE = 3",
    "MIN_COOLING_RATE = 1",
    "MAX_COOLING_RATE = 40",
    "DEFAULT_COOLING_RATE = 20",
    "MAX_THERMAL_LOAD = 1000",
    "WARM_DERATE_THRESHOLD = 700",
    "HOT_DERATE_THRESHOLD = 850",
    "WARM_FIELD_CAP = 10",
    "HOT_FIELD_CAP = 6",
    "boundedField",
    "boundedResponseRate",
    "boundedCoolingRate",
    "boundedThermalLoad",
    "isThermallyDerated",
    "stepField","nextThermal","deratedTarget","thermalState")
req(block,
    "RuntimeIntStore",
    "ElectromagnetLogic.stepField",
    "ElectromagnetLogic.nextThermal",
    "targetField","thermalLoad","trackingError",
    "runTicks","thermalDerated",
    "ElectromagnetLogic.boundedResponseRate",
    "ElectromagnetLogic.boundedCoolingRate",
    "ElectromagnetLogic.DEFAULT_RISE_RATE",
    "ElectromagnetLogic.DEFAULT_FALL_RATE",
    "ElectromagnetLogic.DEFAULT_COOLING_RATE")
req(menu,
    "KIND_ELECTROMAGNET",
    "ElectromagnetBlock.configuredResponse",
    "ElectromagnetBlock.setEngineeringParameters",
    "ElectromagnetBlock.targetField",
    "ElectromagnetBlock.thermalLoad",
    "ElectromagnetBlock.trackingError",
    "ElectromagnetBlock.runTicks")
req(screen,
    "ELECTROMAGNET",
    "Field rise rate Rrise",
    "Field fall rate Rfall",
    "Cooling rate C",
    "ElectromagnetLogic.MIN_RESPONSE_RATE",
    "ElectromagnetLogic.MAX_RESPONSE_RATE",
    "ElectromagnetLogic.MIN_COOLING_RATE",
    "ElectromagnetLogic.MAX_COOLING_RATE",
    "ElectromagnetLogic.MAX_THERMAL_LOAD",
    "ElectromagnetLogic.WARM_DERATE_THRESHOLD",
    "ElectromagnetLogic.HOT_DERATE_THRESHOLD",
    "Target / actual",
    "Thermal load",
    "Tracking error")
req(opener,
    "block instanceof ElectromagnetBlock",
    "new MagneticSystemMenu(id, inv, pos)")

lp=root/logic
if lp.is_file():
    harness=r'''
import dev.redstoneengineering.signal.ElectromagnetLogic;
public final class EmHarness{
 static void c(boolean x,String m){if(!x)throw new AssertionError(m);}
 public static void main(String[]a){
  c(ElectromagnetLogic.stepField(0,15)==2,"finite rise");
  c(ElectromagnetLogic.stepField(15,0)==12,"faster decay");
  c(ElectromagnetLogic.deratedTarget(15,0)==15,"cool no derate");
  c(ElectromagnetLogic.deratedTarget(15,750)==10,"warm derate");
  c(ElectromagnetLogic.deratedTarget(15,900)==6,"hot derate");
  c(ElectromagnetLogic.nextThermal(0,15)>ElectromagnetLogic.nextThermal(0,5),"higher excitation heats faster");
  c(ElectromagnetLogic.nextThermal(500,0)<500,"deenergized coil cools");
  c(ElectromagnetLogic.boundedResponseRate(0)==ElectromagnetLogic.MIN_RESPONSE_RATE,"response lower bound");
  c(ElectromagnetLogic.boundedResponseRate(99)==ElectromagnetLogic.MAX_RESPONSE_RATE,"response upper bound");
  c(ElectromagnetLogic.boundedCoolingRate(0)==ElectromagnetLogic.MIN_COOLING_RATE,"cooling lower bound");
  c(ElectromagnetLogic.boundedCoolingRate(99)==ElectromagnetLogic.MAX_COOLING_RATE,"cooling upper bound");
  c(ElectromagnetLogic.boundedThermalLoad(5000)==ElectromagnetLogic.MAX_THERMAL_LOAD,"thermal upper bound");
  c(ElectromagnetLogic.isThermallyDerated(ElectromagnetLogic.WARM_DERATE_THRESHOLD),"derating threshold");
  System.out.println("ElectromagnetLogic semantic harness: PASS");
 }}
'''
    try:
        with tempfile.TemporaryDirectory(prefix="rse-em-") as td:
            td=Path(td); hp=td/"EmHarness.java"; hp.write_text(harness)
            cp=subprocess.run(["javac","-d",str(td),str(lp),str(hp)],text=True,capture_output=True)
            if cp.returncode: failed.append("javac semantic harness failed: "+(cp.stderr or cp.stdout).strip())
            else:
                rn=subprocess.run(["java","-cp",str(td),"EmHarness"],text=True,capture_output=True)
                if rn.returncode: failed.append("semantic harness failed: "+(rn.stderr or rn.stdout).strip())
    except FileNotFoundError:
        failed.append("javac/java unavailable for electromagnet harness")

if failed:
    print("RSE electromagnet engineering-depth verification: FAIL")
    for x in failed: print(" -",x)
    raise SystemExit(1)
print("RSE electromagnet engineering-depth verification: PASS")
print(" finite inductive field response: PASS")
print(" excitation-dependent thermal accumulation: PASS")
print(" thermal derating/cooling: PASS")
print(" dedicated Magnetic HMI parameter/evidence contract: PASS")
print(" rise/fall/cooling + thermal thresholds share pure-model authority: PASS")
