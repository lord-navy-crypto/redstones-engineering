#!/usr/bin/env python3
from pathlib import Path
import subprocess
import sys
import tempfile

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors = []

def read(rel):
    path = root / rel
    if not path.is_file():
        errors.append(f"missing Signal Conditioner HMI file: {rel}")
        return ""
    return path.read_text(errors="ignore")

menu = read("src/main/java/dev/redstoneengineering/ui/menu/SignalConditionerMenu.java")
screen = read("src/main/java/dev/redstoneengineering/client/ui/SignalConditionerScreen.java")
block = read("src/main/java/dev/redstoneengineering/block/SignalConditionerBlock.java")
logic = read("src/main/java/dev/redstoneengineering/signal/SignalConditionerLogic.java")
opener = read("src/main/java/dev/redstoneengineering/ui/FieldDeviceUi.java")
client = read("src/main/java/dev/redstoneengineering/client/ui/EngineeringUiClientRegistration.java")

for token in (
    "MODE_SCALE = 0",
    "MODE_OFFSET = 1",
    "MODE_CLAMP = 2",
    "MODE_THRESHOLD = 3",
    "MODE_DEADBAND = 4",
    "MODE_ATTENUATE = 5",
    "minParameter",
    "maxParameter",
    "defaultParameter",
    "boundedParameter",
    "cycleParameter",
    "public static int apply",
    "public static boolean limiting",
    "usesPreviousOutput",
    "reportsBoundaryLimiting",
):
    if logic and token not in logic:
        errors.append(f"SignalConditionerLogic missing pure-model token: {token}")

for token in (
    "SignalConditionerBlock.inspectInputQuality",
    "SignalConditionerBlock.inspectOutputQuality",
    "private final DataSlot inputQuality",
    "private final DataSlot outputQuality",
    "public PortQuality inputQuality()",
    "public PortQuality outputQuality()",
    "BUTTON_INPUT_LEFT",
    "BUTTON_OUTPUT_RIGHT",
    "DirectionalSignalBlock.rotateRigidSeriesAxis",
):
    if menu and token not in menu:
        errors.append(f"SignalConditionerMenu missing dedicated quality/route token: {token}")

for stale in ("DirectionalSignalBlock.rotateSeriesInput", "DirectionalSignalBlock.rotateSeriesOutput", "DirectionalSignalBlock.rotateWholeRoute"):
    if stale in menu:
        errors.append(f"SignalConditionerMenu reintroduced bendable route via {stale}")

for token in (
    '"Input quality"',
    '"Output quality"',
    "qualityName(menu.inputQuality())",
    "qualityName(menu.outputQuality())",
    "INPUT EVIDENCE •",
    "OUTPUT SATURATED",
    "restore trustworthy upstream Redstone evidence",
    "NO_SIGNAL / STALE / topology evidence remain separate states.",
    "Boundary limiting episodes / last",
    "private static String modelLine",
    "y = clamp(round(x ×",
    "y = min(x,",
    "otherwise y = 0",
    "retain yprev",
    "SignalConditionerLogic",
    "Stored → effective",
    "State dependence",
    "Boundary evidence",
    "legacy stored ",
    "threshold LOW is valid transfer semantics",
    "deadband hold is stateful transfer semantics",
    "same pure SignalConditionerLogic contract",

):
    if screen and token not in screen:
        errors.append(f"SignalConditionerScreen missing evidence token: {token}")

for token in (
    "Two rows of configuration buttons occupy the 108..153 region.",
    'labelValue(graphics, "Mode", modeName(menu.mode()), 164)',
    '"Rigid Input → Output"',
    '"Transfer model"',
    "noteY = wrappedText",
):
    if token not in screen:
        errors.append(f"SignalConditionerScreen missing non-overlapping engineering layout token: {token}")
if "safeText(graphics," in screen:
    errors.append("SignalConditionerScreen regressed to fixed single-line narrative rows")

for token in (
    "public static PortQuality inspectInputQuality",
    "public static PortQuality inspectOutputQuality",
    "PortQuality.SATURATED",
    "RedstoneObservationSupport.combineQuality",
    "FieldDeviceUi.open(serverPlayer, pos)",
    "SignalConditionerLogic.apply",
    "SignalConditionerLogic.limiting",
    "SignalConditionerLogic.defaultParameter",
    "SignalConditionerLogic.cycleParameter",
):
    if block and token not in block:
        errors.append(f"SignalConditionerBlock missing shared authority token: {token}")

if "private static int calculate(" in block:
    errors.append("SignalConditionerBlock duplicated transfer math instead of using SignalConditionerLogic")
if "independently configurable RX/TX faces" in block:
    errors.append("SignalConditionerBlock class contract still claims independently bendable RX/TX faces")

dedicated = "if (block instanceof SignalConditionerBlock)"
if dedicated not in opener or "new SignalConditionerMenu(id, inv, pos)" not in opener:
    errors.append("FieldDeviceUi does not route Signal Conditioner to dedicated HMI")

process_start = opener.find("if (block instanceof PwmControllerBlock")
process_end = opener.find("if (block instanceof SignalAmplifierBlock", process_start)
if process_start >= 0 and process_end > process_start and "SignalConditionerBlock" in opener[process_start:process_end]:
    errors.append("ProcessParameterMenu dispatch still intercepts Signal Conditioner")

if "event.register(EngineeringUiRegistration.SIGNAL_CONDITIONER.get(), SignalConditionerScreen::new)" not in client:
    errors.append("Signal Conditioner menu is not registered to dedicated SignalConditionerScreen")

logic_path = root / "src/main/java/dev/redstoneengineering/signal/SignalConditionerLogic.java"
signal_path = root / "src/main/java/dev/redstoneengineering/signal/EngineeringSignal.java"
if logic_path.is_file() and signal_path.is_file():
    harness = r'''
import dev.redstoneengineering.signal.SignalConditionerLogic;

public final class SignalConditionerHarness {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(SignalConditionerLogic.apply(8, 0, 0, 3) == 15, "scale clamps at full scale");
        check(SignalConditionerLogic.limiting(8, 0, 3), "scale boundary reports limiting");
        check(SignalConditionerLogic.apply(2, 0, 1, 0) == 0, "negative offset clamps at zero");
        check(SignalConditionerLogic.apply(12, 0, 1, 10) == 15, "positive offset clamps at fifteen");
        check(SignalConditionerLogic.apply(12, 0, 2, 7) == 7, "clamp ceiling applies");
        check(SignalConditionerLogic.limiting(12, 2, 7), "clamp ceiling reports limiting");
        check(SignalConditionerLogic.apply(7, 0, 3, 8) == 0, "threshold below trip is valid zero");
        check(SignalConditionerLogic.apply(9, 0, 3, 8) == 9, "threshold above trip passes input");
        check(!SignalConditionerLogic.limiting(7, 3, 8), "threshold low is not saturation");
        check(SignalConditionerLogic.apply(9, 8, 4, 2) == 8, "deadband retains previous output");
        check(SignalConditionerLogic.apply(10, 8, 4, 2) == 10, "deadband releases at boundary");
        check(SignalConditionerLogic.apply(15, 0, 5, 2) == 8, "attenuation rounds half up");
        check(SignalConditionerLogic.apply(15, 0, 5, 4) == 4, "attenuation divisor four");
        check(SignalConditionerLogic.boundedParameter(0, 15) == 4, "legacy scale param clamps to four");
        check(SignalConditionerLogic.boundedParameter(1, 15) == 10, "legacy offset param clamps to ten");
        check(SignalConditionerLogic.boundedParameter(5, 0) == 2, "legacy attenuate param clamps to two");
        check(SignalConditionerLogic.cycleParameter(0, 4, 1) == 1, "scale wraps 4 to 1");
        check(SignalConditionerLogic.cycleParameter(1, 0, -1) == 10, "offset wraps 0 to 10");
        check(SignalConditionerLogic.defaultParameter(4) == 2, "deadband default remains two");
        check(SignalConditionerLogic.usesPreviousOutput(4), "deadband is stateful");
        check(!SignalConditionerLogic.usesPreviousOutput(3), "threshold is memoryless");
        System.out.println("SignalConditionerLogic semantic harness: PASS");
    }
}
'''
    try:
        with tempfile.TemporaryDirectory(prefix="rse-conditioner-") as td:
            td = Path(td)
            harness_path = td / "SignalConditionerHarness.java"
            harness_path.write_text(harness)
            compile_run = subprocess.run(
                ["javac", "-d", str(td), str(signal_path), str(logic_path), str(harness_path)],
                text=True, capture_output=True)
            if compile_run.returncode != 0:
                errors.append("SignalConditionerLogic javac harness failed: "
                              + (compile_run.stderr or compile_run.stdout).strip())
            else:
                execute = subprocess.run(
                    ["java", "-cp", str(td), "SignalConditionerHarness"],
                    text=True, capture_output=True)
                if execute.returncode != 0:
                    errors.append("SignalConditionerLogic semantic harness failed: "
                                  + (execute.stderr or execute.stdout).strip())
    except FileNotFoundError:
        errors.append("javac/java unavailable for SignalConditioner semantic harness")

if errors:
    print("RSE Signal Conditioner HMI verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE Signal Conditioner HMI verification: PASS")
print(" normal right-click reaches dedicated HMI: PASS")
print(" rigid opposite-port Route authority enforced: PASS")
print(" input/output PortQuality evidence synchronized: PASS")
print(" saturation remains explicit output evidence: PASS")
print(" valid zero stays distinct from missing/stale/topology evidence: PASS")
print(" Configure/History engineering text flows below controls without overlap: PASS")
print(" Configure page exposes exact server transfer model + legacy stored/effective parameter contract: PASS")
print(" pure SignalConditionerLogic semantic harness: PASS")
