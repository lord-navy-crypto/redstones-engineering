#!/usr/bin/env python3
from pathlib import Path
import subprocess
import tempfile
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
errors = []

def require(rel: str, *tokens: str) -> None:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing: {rel}")
        return
    text = path.read_text(errors="ignore")
    for token in tokens:
        if token not in text:
            errors.append(f"{rel} missing token: {token}")

logic = "src/main/java/dev/redstoneengineering/signal/PidActuatorLogic.java"
parameters = "src/main/java/dev/redstoneengineering/physics/EngineeringDeviceParameters.java"
block = "src/main/java/dev/redstoneengineering/block/PidControllerBlock.java"
menu = "src/main/java/dev/redstoneengineering/ui/menu/PidControllerMenu.java"
screen = "src/main/java/dev/redstoneengineering/client/ui/PidEngineeringNotebookScreen.java"

require(logic,
        "record SlewResult",
        "public static SlewResult slew(",
        "filteredMeasurementDerivative",
        "next != requested")
require(parameters,
        "MIN_KP = 0",
        "MAX_KP = 12",
        "MIN_KI_DIVISOR = 0",
        "MAX_KI_DIVISOR = 64",
        "MIN_KD = 0",
        "MAX_KD = 12",
        "MIN_DERIVATIVE_SMOOTHING = 1",
        "MAX_DERIVATIVE_SMOOTHING = 16",
        "MIN_RISE_LIMIT = 1",
        "MAX_RISE_LIMIT = 15",
        "MIN_FALL_LIMIT = 1",
        "MAX_FALL_LIMIT = 15")

require(block,
        "CONTROL_CYCLE_TICKS = 2",
        "DEADBAND_LEVELS = 1",
        "INTEGRAL_MIN = -180",
        "INTEGRAL_MAX = 180",
        "clamp(rt[0] + controlError, INTEGRAL_MIN, INTEGRAL_MAX)",
        "scheduleTick(p, this, CONTROL_CYCLE_TICKS)",
        "PidActuatorLogic.filteredMeasurementDerivative",
        "applyActuatorDynamics",
        "rateLimitedAgainstError",
        "ACTUATOR_TARGET_SLOT",
        "SLEW_ACTIVE_SLOT",
        "SLEW_EPISODES_SLOT",
        "riseLimit(BlockState state)",
        "fallLimit(BlockState state)")
require(menu,
        "actuatorTarget",
        "slewActive",
        "slewEvents",
        "riseLimit",
        "fallLimit")
require(screen,
        'MODEL("Model")',
        '"Actuator target"',
        '"Slew limiting"',
        "Derivative is taken on the measured process value",
        "Anti-windup is conditional integration",
        "Manual → AUTO transfer is bumpless",
        "does not execute a second PID solver",
        "rise limit per control cycle",
        "independent fall limit",
        '"Control cycle"',
        '"Error deadband"',
        '"Integral state clamp"',
        '"0 → 15 actuator slew"',
        '"15 → 0 actuator slew"',
        "fullScaleRiseTicks()",
        "fullScaleFallTicks()",
        "PidControllerBlock.MIN_KP",
        "PidControllerBlock.MAX_KI_DIVISOR",
        "PidControllerBlock.MAX_DERIVATIVE_SMOOTHING",
        "PidControllerBlock.MAX_RISE_LIMIT",
        "PidControllerBlock.MAX_FALL_LIMIT")

logic_path = root / logic
if logic_path.is_file():
    harness = r"""
import dev.redstoneengineering.signal.PidActuatorLogic;

public final class PidActuatorHarness {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        var rise = PidActuatorLogic.slew(0, 15, 2, 3);
        check(rise.output() == 2 && rise.limited(), "rise slew must bound actuator command");

        var fall = PidActuatorLogic.slew(10, 0, 2, 3);
        check(fall.output() == 7 && fall.limited(), "fall slew must use independent fall limit");

        var settled = PidActuatorLogic.slew(7, 7, 2, 3);
        check(settled.output() == 7 && !settled.limited(), "target equality must not report limiting");

        int noKick = PidActuatorLogic.filteredMeasurementDerivative(6, 6, 0, 3);
        check(noKick == 0, "unchanged PV must have zero derivative regardless of SP changes");

        int pvRise = PidActuatorLogic.filteredMeasurementDerivative(6, 9, 0, 3);
        check(pvRise == 1, "PV derivative must follow measurement movement");

        System.out.println("PidActuatorLogic harness: PASS");
    }
}
"""
    try:
        with tempfile.TemporaryDirectory(prefix="rse-pid-actuator-") as td:
            td = Path(td)
            hp = td / "PidActuatorHarness.java"
            hp.write_text(harness)
            compile_run = subprocess.run(
                ["javac", "-d", str(td), str(logic_path), str(hp)],
                cwd=root, capture_output=True, text=True
            )
            if compile_run.returncode != 0:
                errors.append("PidActuatorLogic javac failed: " + compile_run.stderr.strip())
            else:
                run = subprocess.run(
                    ["java", "-cp", str(td), "PidActuatorHarness"],
                    cwd=root, capture_output=True, text=True
                )
                if run.returncode != 0:
                    errors.append("PidActuatorLogic harness failed: " + (run.stderr or run.stdout).strip())
    except FileNotFoundError:
        errors.append("javac/java unavailable for PID actuator harness")

if errors:
    print("RSE PID actuator dynamics verification: FAIL")
    for e in errors:
        print(" -", e)
    raise SystemExit(1)

print("RSE PID actuator dynamics verification: PASS")
print(" asymmetric actuator command slew: PASS")
print(" derivative-on-measurement primitive: PASS")
print(" current PID engineering notebook model contract: PASS")
print(" exact PID parameter bounds + 2-tick control-cycle contract: PASS")
print(" derived full-scale actuator slew timing shown without pretending to model plant response: PASS")
print(" anti-windup / bumpless / asymmetric slew HMI explanation: PASS")
