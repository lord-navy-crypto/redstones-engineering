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
menu="src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java"
screen="src/main/java/dev/redstoneengineering/client/ui/UniversalFieldDeviceScreen.java"

req(logic,"stepField","nextThermal","deratedTarget","thermalState")
req(block,
    "RuntimeIntStore",
    "ElectromagnetLogic.stepField",
    "ElectromagnetLogic.nextThermal",
    "targetField","thermalLoad","trackingError",
    "runTicks","thermalDerated")
req(menu,
    "CONFIG_ELECTROMAGNET",
    "ElectromagnetBlock.targetField",
    "ElectromagnetBlock.thermalLoad",
    "ElectromagnetBlock.trackingError")
req(screen,
    "ELECTROMAGNET COIL",
    "Target / actual field",
    "Thermal load",
    "Tracking error")

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
print(" HMI coil evidence: PASS")
