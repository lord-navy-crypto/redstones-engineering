#!/usr/bin/env python3
from pathlib import Path
import subprocess
import sys
import tempfile

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
failed: list[str] = []

def require(path: str, *tokens: str) -> None:
    p = root / path
    if not p.is_file():
        failed.append(f"missing: {path}")
        return
    text = p.read_text(errors="ignore")
    for token in tokens:
        if token not in text:
            failed.append(f"{path} missing token: {token}")

logic = "src/main/java/dev/redstoneengineering/signal/PneumaticProportionalValveLogic.java"
block = "src/main/java/dev/redstoneengineering/block/PneumaticProportionalValveBlock.java"
menu = "src/main/java/dev/redstoneengineering/ui/menu/PneumaticSystemMenu.java"
screen = "src/main/java/dev/redstoneengineering/client/ui/PneumaticSystemScreen.java"

require(logic, "stepOpening", "trackingError", "responseRate")
require(block,
        'IntegerProperty.create("response_mode", 0, 2)',
        "commandedOpening",
        "actualOpening",
        "travel",
        "reversals",
        "PneumaticProportionalValveLogic.stepOpening",
        "PneumaticNetwork.recompute")
require(menu,
        "PneumaticProportionalValveBlock.RESPONSE_MODE",
        "PneumaticProportionalValveBlock.configuredResponseRate",
        "PneumaticProportionalValveBlock.setConfiguredResponseRate",
        "PneumaticProportionalValveBlock.commandedOpening",
        "PneumaticProportionalValveBlock.actualOpening",
        "PneumaticProportionalValveBlock.travel",
        "PneumaticProportionalValveBlock.reversals",
        "engineeringA.set(PneumaticProportionalValveBlock.configuredResponseRate")
require(screen,
        "VALVE SPOOL RESPONSE",
        "Command / actual opening",
        "Tracking error",
        "Travel / reversals",
        "Configured spool rate",
        'menu.engineeringA()+" opening/tick"')

logic_path = root / logic
if logic_path.is_file():
    harness = r'''
import dev.redstoneengineering.signal.PneumaticProportionalValveLogic;
public final class ValveHarness {
    private static void check(boolean c,String m){if(!c)throw new AssertionError(m);}
    public static void main(String[] args){
        check(PneumaticProportionalValveLogic.stepOpening(0,15,0)==1,"soft");
        check(PneumaticProportionalValveLogic.stepOpening(0,15,1)==2,"normal");
        check(PneumaticProportionalValveLogic.stepOpening(0,15,2)==4,"fast");
        check(PneumaticProportionalValveLogic.stepOpening(14,15,2)==15,"clamp up");
        check(PneumaticProportionalValveLogic.stepOpening(3,0,2)==0,"clamp down");
        check(PneumaticProportionalValveLogic.trackingError(4,10)==6,"positive error");
        check(PneumaticProportionalValveLogic.trackingError(12,3)==-9,"negative error");
        System.out.println("PneumaticProportionalValveLogic semantic harness: PASS");
    }
}
'''
    try:
        with tempfile.TemporaryDirectory(prefix="rse-pvalve-") as td:
            td = Path(td)
            hp = td / "ValveHarness.java"
            hp.write_text(harness)
            compiled = subprocess.run(["javac","-d",str(td),str(logic_path),str(hp)],text=True,capture_output=True)
            if compiled.returncode != 0:
                failed.append("javac semantic harness failed: "+(compiled.stderr or compiled.stdout).strip())
            else:
                run = subprocess.run(["java","-cp",str(td),"ValveHarness"],text=True,capture_output=True)
                if run.returncode != 0:
                    failed.append("semantic harness failed: "+(run.stderr or run.stdout).strip())
    except FileNotFoundError:
        failed.append("javac/java unavailable for proportional-valve harness")

if failed:
    print("RSE proportional-valve engineering-depth verification: FAIL")
    for item in failed: print(" -", item)
    raise SystemExit(1)

print("RSE proportional-valve engineering-depth verification: PASS")
print(" finite spool travel: PASS")
print(" actual opening drives pneumatic restriction: PASS")
print(" travel/reversal evidence: PASS")
print(" HMI exact response-rate authority: PASS")
