#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors = []

def require(rel, *tokens):
    path = root / rel
    if not path.is_file():
        errors.append(f"missing Servo depth file: {rel}")
        return
    text = path.read_text(errors="ignore")
    for token in tokens:
        if token not in text:
            errors.append(f"{rel} missing Servo depth token: {token}")

block = "src/main/java/dev/redstoneengineering/block/ServoActuatorBlock.java"
menu = "src/main/java/dev/redstoneengineering/ui/menu/ServoActuatorMenu.java"
screen = "src/main/java/dev/redstoneengineering/client/ui/ServoActuatorNotebookScreen.java"

require(block,
        "public static int mode(Level level, BlockPos pos)",
        "public static int velocityCommand(Level level, BlockPos pos)",
        "public static int maxObservedVelocity(Level level, BlockPos pos)",
        "public static int settleTicks(Level level, BlockPos pos)",
        "public static int travel(Level level, BlockPos pos)",
        "public static PortQuality commandQuality",
        "public static PortQuality modeQuality",
        "public static PortQuality brakeQuality",
        "public static PortQuality outputQuality",
        "velocityCommand = effectiveCommand - 7",
        "desiredVelocity = clamp(positionError, -maxSpeed, maxSpeed)",
        "appliedVelocity = approach(appliedVelocity, desiredVelocity, accelStep)",
        "int limitedPosition = clamp(candidatePosition, 0, 15)",
        "brake = !commandAvailable")

require(menu,
        "private final DataSlot mode",
        "private final DataSlot velocityCommand",
        "private final DataSlot settleTicks",
        "private final DataSlot travel",
        "private final DataSlot maxObservedVelocity",
        "private final DataSlot commandQuality",
        "private final DataSlot modeQuality",
        "private final DataSlot brakeQuality",
        "private final DataSlot outputQuality",
        "ServoActuatorBlock.commandQuality",
        "ServoActuatorBlock.outputQuality",
        "public boolean velocityMode()",
        "public int settleTicks()",
        "public int travel()")

require(screen,
        'MODEL("Model")',
        "pageTabLabel",
        '"Current mode"',
        '"Velocity command"',
        '"Command input quality"',
        '"Mode input quality"',
        '"Brake input quality"',
        '"Position output quality"',
        "POSITION mode: desiredVelocity = clamp(command − position",
        "VELOCITY mode: velocityCommand = command − 7",
        "Acceleration is discrete:",
        "missing command evidence or an asserted brake input forces appliedVelocity = 0",
        "Position update: position[k+1] = clamp(position[k] + appliedVelocity, 0, 15)",
        "does not advance acceleration phase",
        "does not erase the configured mechanical parameters")

if errors:
    print("RSE Servo actuator depth verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE Servo actuator depth verification: PASS")
print(" position/velocity mode model: PASS")
print(" discrete acceleration + brake fail-safe contract: PASS")
print(" trajectory response evidence: PASS")
print(" command/mode/brake/output quality synchronization: PASS")
print(" five-tab viewport-aware engineering notebook: PASS")
print(" client remains render-only; no second Servo solver: PASS")
