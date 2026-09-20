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

logic="src/main/java/dev/redstoneengineering/signal/IronCoreLogic.java"
block="src/main/java/dev/redstoneengineering/block/IronCoreBlock.java"
physics="src/main/java/dev/redstoneengineering/physics/MagneticPhysics.java"

req(logic,"nextMagnetization","remanentFloor","approach")
req(block,
    "RuntimeIntStore","magnetization","remanentField","degauss",
    "IronCoreLogic.nextMagnetization","APPLIED_FIELD_RADIUS")
req(physics,
    "IronCoreBlock.remanentField(level, cursor)")

lp=root/logic
if lp.is_file():
    harness=r'''
import dev.redstoneengineering.signal.IronCoreLogic;
public final class IronHarness{
 static void c(boolean x,String m){if(!x)throw new AssertionError(m);}
 public static void main(String[]a){
  c(IronCoreLogic.nextMagnetization(0,12,true)==3,"finite magnetize");
  c(IronCoreLogic.nextMagnetization(12,12,true)==12,"hold under field");
  c(IronCoreLogic.nextMagnetization(12,0,true)<12,"decay when field removed");
  c(IronCoreLogic.nextMagnetization(2,0,true)>=0,"bounded remanence");
  c(IronCoreLogic.nextMagnetization(7,15,false)==7,"incomplete field freezes evidence state");
  System.out.println("IronCoreLogic semantic harness: PASS");
 }}
'''
    try:
        with tempfile.TemporaryDirectory(prefix="rse-iron-") as td:
            td=Path(td); hp=td/"IronHarness.java"; hp.write_text(harness)
            cp=subprocess.run(["javac","-d",str(td),str(lp),str(hp)],text=True,capture_output=True)
            if cp.returncode: failed.append("javac semantic harness failed: "+(cp.stderr or cp.stdout).strip())
            else:
                rn=subprocess.run(["java","-cp",str(td),"IronHarness"],text=True,capture_output=True)
                if rn.returncode: failed.append("semantic harness failed: "+(rn.stderr or rn.stdout).strip())
    except FileNotFoundError:
        failed.append("javac/java unavailable for iron-core harness")

if failed:
    print("RSE iron-core engineering-depth verification: FAIL")
    for x in failed: print(" -",x)
    raise SystemExit(1)
print("RSE iron-core engineering-depth verification: PASS")
print(" induced magnetization finite response: PASS")
print(" soft remanence decays after field removal: PASS")
print(" incomplete applied-field evidence freezes state: PASS")
print(" magnetic solver consumes variable remanence: PASS")
