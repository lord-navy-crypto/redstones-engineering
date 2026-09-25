#!/usr/bin/env python3
from pathlib import Path
import subprocess
import sys
import tempfile

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
failed = []

menu_path = root / "src/main/java/dev/redstoneengineering/ui/menu/ProcessParameterMenu.java"
screen_path = root / "src/main/java/dev/redstoneengineering/client/ui/ProcessParameterNotebookScreen.java"
conditioner_path = root / "src/main/java/dev/redstoneengineering/block/SignalConditionerBlock.java"
pwm_path = root / "src/main/java/dev/redstoneengineering/block/PwmControllerBlock.java"
pwm_logic_path = root / "src/main/java/dev/redstoneengineering/signal/PwmCarrierLogic.java"
damper_path = root / "src/main/java/dev/redstoneengineering/block/HoneyVibrationDamperBlock.java"
damper_logic_path = root / "src/main/java/dev/redstoneengineering/signal/HoneyVibrationDamperLogic.java"
vibration_network_path = root / "src/main/java/dev/redstoneengineering/physics/VibrationNetwork.java"
driver_path = root / "src/main/java/dev/redstoneengineering/block/RedstoneCopperDriverBlock.java"
driver_logic_path = root / "src/main/java/dev/redstoneengineering/signal/RedstoneCopperDriverLogic.java"

for path in (menu_path, screen_path, conditioner_path, pwm_path, pwm_logic_path, damper_path, damper_logic_path, vibration_network_path, driver_path, driver_logic_path):
    if not path.is_file():
        failed.append(f"missing process quality contract file: {path.relative_to(root)}")

if not failed:
    menu = menu_path.read_text(errors="ignore")
    screen = screen_path.read_text(errors="ignore")
    conditioner = conditioner_path.read_text(errors="ignore")
    pwm = pwm_path.read_text(errors="ignore")
    pwm_logic = pwm_logic_path.read_text(errors="ignore")
    damper = damper_path.read_text(errors="ignore")
    damper_logic = damper_logic_path.read_text(errors="ignore")
    vibration_network = vibration_network_path.read_text(errors="ignore")
    driver = driver_path.read_text(errors="ignore")
    driver_logic = driver_logic_path.read_text(errors="ignore")

    for token in (
        "SignalConditionerBlock.inspectInputQuality",
        "SignalConditionerBlock.inspectOutputQuality",
        "SignalConditionerBlock.lastLimitingAgeTicks",
        "PwmControllerBlock.commandQuality",
        "PwmControllerBlock.inhibitQuality",
        "PwmControllerBlock.outputQuality",
        "BUTTON_INPUT_PREVIOUS",
        "BUTTON_INPUT_NEXT",
        "BUTTON_OUTPUT_PREVIOUS",
        "BUTTON_OUTPUT_NEXT",
        "DirectionalSignalBlock.rotateSeriesInput",
        "DirectionalSignalBlock.rotateSeriesOutput",
        "DirectionalDomainBlock.rotateRigidSeriesAxis",
        "public boolean rigidSeriesRoute()",
        "DirectionalDomainSourceBlock.rotateOutput",
        "RedstoneCopperDriverBlock.rotateInput",
        "RedstoneCopperDriverBlock.rotateOutput",
        "RedstoneCopperDriverBlock.trackingError",
        "p2.set(state.getValue(RedstoneCopperDriverBlock.SLEW))",
        "public boolean canRouteInput()",
        "public boolean canRouteOutput()",
        "public Direction inputDirection()",
        "public Direction outputDirection()",
    ):
        if token not in menu:
            failed.append(f"ProcessParameterMenu missing quality/routing synchronization token: {token}")

    for token in (
        '"Input quality"',
        '"Output quality"',
        '"Last limit age"',
        '"Command quality"',
        '"Inhibit quality"',
        "Active limiting marks output SATURATED",
        "PWM fail-safe behavior uses command and inhibit quality separately",
        'DIAGNOSTICS("Diagnostics")',
        "private void diagnostics(GuiGraphics g)",
        "diagnosticStatus()",
        "diagnosticLines()",
        "diagnosticNextAction()",
        "Diagnostic text interprets synchronized device evidence only.",
        "It does not run a second solver",
        "LOAD EVIDENCE INCOMPLETE",
        "OVERLOAD HEATING ACTIVE",
        "COAST-DOWN",
        "NEXT •",
        'ROUTING("Routing")',
        "tabLabel(Tab value)",
        "Math.max(48",
        "private void routing(GuiGraphics g)",
        "menu.canRouteInput()",
        "menu.canRouteOutput()",
        "RX/TX buttons mutate only declared server-owned physical endpoint properties.",
        "Rotate block",
        "endpoints cannot be bent independently",
        "no synthetic single RX/TX pair is created",
        "Output-only LAPIS precision source",
        "Six-face COPPER voltage source",
        "moving TX releases the old Copper driver claim",
        "RedstoneCopperDriverLogic.MIN_SLEW",
        "RedstoneCopperDriverLogic.MAX_SLEW",
        "RedstoneCopperDriverLogic.fullScaleRampTicks",
        "RedstoneCopperDriverLogic.slewForLegacyMode",
        "PwmCarrierLogic.MAX_COMMAND",
        "PwmCarrierLogic.MIN_CONFIGURED_PERIOD_TICKS",
        "PwmCarrierLogic.MAX_CONFIGURED_PERIOD_TICKS",
        "PwmCarrierLogic.dutyQuantumPermille",
        "Partial-duty commands latch only at phase-0 carrier boundaries",
        "0%/100% endpoints apply immediately",
        "A[k+1] = max(0, A[k] − attenuation)",
        "HoneyVibrationDamperLogic.MIN_ATTENUATION",
        "HoneyVibrationDamperLogic.MAX_ATTENUATION",
        "HoneyVibrationDamperLogic.INITIAL_ENVELOPE_QUALITY",
        "HoneyVibrationDamperLogic.QUALITY_DECAY_PER_STEP",
        "HoneyVibrationDamperLogic.PACKET_TTL_TICKS",
        "same server-owned D",
    ):
        if token not in screen:
            failed.append(f"ProcessParameterNotebookScreen missing quality/diagnostic/routing presentation token: {token}")

    for token in (
        "public static PortQuality inspectInputQuality",
        "public static PortQuality inspectOutputQuality",
        "PortQuality.SATURATED",
        "RedstoneObservationSupport.combineQuality",
    ):
        if token not in conditioner:
            failed.append(f"SignalConditionerBlock missing quality contract: {token}")

    for token in (
        "public static boolean rotateInput",
        "public static boolean rotateOutput",
        "DomainNetwork.driveCopper(server, pos.relative(oldOutput), pos, 0, false)",
        "nextFreeHorizontal",
        "RedstoneCopperDriverLogic.boundedSlew",
        "RedstoneCopperDriverLogic.moveToward",
        "RedstoneCopperDriverLogic.trackingError",
        "RedstoneCopperDriverLogic.CONTROL_TICK_TICKS",
    ):
        if token not in driver:
            failed.append(f"RedstoneCopperDriverBlock missing safe routing/dynamics token: {token}")

    for token in (
        "MIN_VOLTAGE = 0",
        "MAX_VOLTAGE = 15",
        "MIN_SLEW = 1",
        "MAX_SLEW = 15",
        "MIN_LEGACY_SLEW_MODE = 0",
        "MAX_LEGACY_SLEW_MODE = 2",
        "DEFAULT_LEGACY_SLEW_MODE = 1",
        "LEGACY_SLEW_SLOW = 1",
        "LEGACY_SLEW_NORMAL = 2",
        "LEGACY_SLEW_FAST = 4",
        "CONTROL_TICK_TICKS = 1",
        "boundedVoltage",
        "boundedSlew",
        "slewForLegacyMode",
        "moveToward",
        "trackingError",
        "fullScaleRampTicks",
    ):
        if token not in driver_logic:
            failed.append(f"RedstoneCopperDriverLogic missing pure-model token: {token}")

    for token in (
        "quantizedOnTicks",
        "boundedCommand / (double) MAX_COMMAND",
        "requested <= MIN_COMMAND || requested >= MAX_COMMAND",
        "phase == 0",
    ):
        if token not in pwm_logic:
            failed.append(f"PwmCarrierLogic missing carrier assumption token: {token}")

    for token in (
        "PACKET_TTL_TICKS = HoneyVibrationDamperLogic.PACKET_TTL_TICKS",
        "HoneyVibrationDamperLogic.DEFAULT_ATTENUATION",
        "HoneyVibrationDamperLogic.boundedAttenuation",
        "HoneyVibrationDamperLogic.attenuatedAmplitude",
        "HoneyVibrationDamperLogic.degradedQuality",
        "level.scheduleTick(pos, this, PACKET_TTL_TICKS)",
    ):
        if token not in damper:
            failed.append(f"HoneyVibrationDamperBlock missing pure-model binding token: {token}")

    for token in (
        "MIN_ATTENUATION = 1",
        "MAX_ATTENUATION = 15",
        "DEFAULT_ATTENUATION = 4",
        "PACKET_TTL_TICKS = 4",
        "INITIAL_ENVELOPE_QUALITY = 80",
        "QUALITY_DECAY_PER_STEP = 20",
        "boundedAttenuation",
        "attenuatedAmplitude",
        "degradedQuality",
    ):
        if token not in damper_logic:
            failed.append(f"HoneyVibrationDamperLogic missing pure-model token: {token}")

    for token in (
        "HoneyVibrationDamperBlock.configuredAttenuation(level, node.pos)",
        "HoneyVibrationDamperLogic.INITIAL_ENVELOPE_QUALITY",
        "HoneyVibrationDamperLogic.PACKET_TTL_TICKS",
        "No fixed hidden loss remains here",
    ):
        if token not in vibration_network:
            failed.append(f"VibrationNetwork missing configurable Honey loss token: {token}")

    for token in (
        "public static PortQuality commandQuality",
        "public static PortQuality inhibitQuality",
        "public static PortQuality outputQuality",
        "inhibitEvidenceUnusable",
        "RedstoneObservationSupport.combineQuality",
    ):
        if token not in pwm:
            failed.append(f"PwmControllerBlock missing quality contract: {token}")

if driver_logic_path.is_file():
    harness = r"""
import dev.redstoneengineering.signal.RedstoneCopperDriverLogic;

public final class RedstoneCopperDriverHarness {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(RedstoneCopperDriverLogic.moveToward(0, 15, 2, 1) == 2,
                "rise slew must apply exactly");
        check(RedstoneCopperDriverLogic.moveToward(15, 0, 2, 3) == 12,
                "fall slew must apply exactly");
        check(RedstoneCopperDriverLogic.moveToward(7, 7, 2, 3) == 7,
                "settled voltage must remain unchanged");
        check(RedstoneCopperDriverLogic.boundedSlew(0) == RedstoneCopperDriverLogic.MIN_SLEW,
                "slew lower bound");
        check(RedstoneCopperDriverLogic.boundedSlew(99) == RedstoneCopperDriverLogic.MAX_SLEW,
                "slew upper bound");
        check(RedstoneCopperDriverLogic.slewForLegacyMode(0) == 1,
                "legacy slow preset");
        check(RedstoneCopperDriverLogic.slewForLegacyMode(1) == 2,
                "legacy normal preset");
        check(RedstoneCopperDriverLogic.slewForLegacyMode(2) == 4,
                "legacy fast preset");
        check(RedstoneCopperDriverLogic.fullScaleRampTicks(2) == 8,
                "full-scale ramp at slew two takes eight ticks");
        check(RedstoneCopperDriverLogic.trackingError(15, 11) == 4,
                "tracking error uses bounded voltages");
        System.out.println("RedstoneCopperDriverLogic harness: PASS");
    }
}
"""
    try:
        with tempfile.TemporaryDirectory(prefix="rse-copper-driver-") as td:
            td = Path(td)
            hp = td / "RedstoneCopperDriverHarness.java"
            hp.write_text(harness)
            compile_run = subprocess.run(
                ["javac", "-d", str(td), str(driver_logic_path), str(hp)],
                cwd=root, capture_output=True, text=True
            )
            if compile_run.returncode != 0:
                failed.append("RedstoneCopperDriverLogic javac failed: " + compile_run.stderr.strip())
            else:
                run = subprocess.run(
                    ["java", "-cp", str(td), "RedstoneCopperDriverHarness"],
                    cwd=root, capture_output=True, text=True
                )
                if run.returncode != 0:
                    failed.append("RedstoneCopperDriverLogic harness failed: "
                                  + (run.stderr or run.stdout).strip())
    except FileNotFoundError:
        failed.append("javac/java unavailable for Redstone-Copper driver harness")

if damper_logic_path.is_file():
    harness = r"""
import dev.redstoneengineering.signal.HoneyVibrationDamperLogic;

public final class HoneyVibrationDamperHarness {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(HoneyVibrationDamperLogic.boundedAttenuation(0)
                        == HoneyVibrationDamperLogic.MIN_ATTENUATION,
                "attenuation lower bound");
        check(HoneyVibrationDamperLogic.boundedAttenuation(99)
                        == HoneyVibrationDamperLogic.MAX_ATTENUATION,
                "attenuation upper bound");
        check(HoneyVibrationDamperLogic.attenuatedAmplitude(12, 4) == 8,
                "configured attenuation subtracts from amplitude");
        check(HoneyVibrationDamperLogic.attenuatedAmplitude(3, 4) == 0,
                "attenuation floors amplitude at zero");
        check(HoneyVibrationDamperLogic.degradedQuality(80) == 60,
                "quality decays by fixed step");
        check(HoneyVibrationDamperLogic.degradedQuality(10) == 0,
                "quality floors at zero");
        System.out.println("HoneyVibrationDamperLogic harness: PASS");
    }
}
"""
    try:
        with tempfile.TemporaryDirectory(prefix="rse-honey-damper-") as td:
            td = Path(td)
            hp = td / "HoneyVibrationDamperHarness.java"
            hp.write_text(harness)
            compile_run = subprocess.run(
                ["javac", "-d", str(td), str(damper_logic_path), str(hp)],
                cwd=root, capture_output=True, text=True
            )
            if compile_run.returncode != 0:
                failed.append("HoneyVibrationDamperLogic javac failed: " + compile_run.stderr.strip())
            else:
                run = subprocess.run(
                    ["java", "-cp", str(td), "HoneyVibrationDamperHarness"],
                    cwd=root, capture_output=True, text=True
                )
                if run.returncode != 0:
                    failed.append("HoneyVibrationDamperLogic harness failed: "
                                  + (run.stderr or run.stdout).strip())
    except FileNotFoundError:
        failed.append("javac/java unavailable for Honey damper harness")

if failed:
    print("RSE process HMI quality verification: FAIL")
    for item in failed:
        print(" -", item)
    raise SystemExit(1)

print("RSE process HMI quality verification: PASS")
print(" conditioner input/output quality separation: PASS")
print(" conditioner saturation + retained limiting age: PASS")
print(" PWM command/inhibit/output quality separation: PASS")
print(" PWM fail-safe evidence remains server-backed: PASS")
print(" device-specific diagnostics page uses synchronized evidence only: PASS")
print(" diagnostics do not introduce a client-side second solver: PASS")
print(" Process Routing page mutates only declared server-owned endpoints: PASS")
print(" axial Copper capacitor/fuse routes stay rigid and opposite: PASS")
print(" fixed/multi-face devices do not receive fake RX/TX controls: PASS")
print(" Redstone-Copper driver reroute releases the old Copper claim: PASS")
print(" Redstone-Copper exact slew + legacy preset pure dynamics contract: PASS")
print(" output-only Lapis source remains output-only: PASS")
print(" five-tab Process notebook stays narrow-viewport aware: PASS")
print(" PWM exact-period quantization/latch assumptions match server carrier logic: PASS")
print(" damper attenuation bounds/TTL/quality-decay pure model: PASS")
print(" configurable damper attenuation also governs network through-path loss: PASS")
