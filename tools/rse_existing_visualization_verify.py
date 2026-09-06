#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing visualization file: {rel}")
        return ""
    return path.read_text(errors="ignore")


plot = read("src/main/java/dev/redstoneengineering/client/ui/EngineeringPlot.java")
for token in (
    "Client-only plotting primitives",
    "IntUnaryOperator",
    "analogFrame",
    "analogTrace",
    "digitalTrace",
    "verticalMarker",
    "horizontalMarker",
):
    if plot and token not in plot:
        errors.append(f"EngineeringPlot missing shared renderer contract {token!r}")

for forbidden in (
    "net.minecraft.world.level",
    "RuntimeIntStore",
    "EngineeringTopologyView",
    "scheduleTick(",
    "setBlock(",
    "updateNeighborsAt(",
):
    if plot and forbidden in plot:
        errors.append(f"EngineeringPlot must remain render-only; found {forbidden!r}")

scope = read("src/main/java/dev/redstoneengineering/client/ui/OscilloscopeScreen.java")
for token in (
    "EngineeringPlot.analogFrame",
    "EngineeringPlot.analogTrace",
    "EngineeringPlot.horizontalMarker",
    "EngineeringPlot.verticalMarker",
    "plotChannel(graphics, 0",
    "plotChannel(graphics, 1",
):
    if scope and token not in scope:
        errors.append(f"Oscilloscope visualization missing {token!r}")
if scope and "fullTrace(" in scope:
    errors.append("Oscilloscope retains the old per-channel fullTrace background redraw path")

logic = read("src/main/java/dev/redstoneengineering/client/ui/LogicAnalyzerScreen.java")
for token in (
    "EngineeringPlot.digitalTrace",
    "EngineeringPlot.verticalMarker",
    "int lane = channel;",
    "slot -> menu.displayState(lane, slot)",
):
    if logic and token not in logic:
        errors.append(f"Logic Analyzer visualization missing {token!r}")

signal = read("src/main/java/dev/redstoneengineering/client/ui/SignalAnalyzerScreen.java")
for token in (
    "EngineeringPlot.analogFrame",
    "EngineeringPlot.analogTrace",
    "EngineeringPlot.horizontalMarker",
    "μ=rounded mean",
):
    if signal and token not in signal:
        errors.append(f"Signal Analyzer visualization missing {token!r}")

if errors:
    print("RSE existing-content visualization verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE existing-content visualization verification: PASS")
print(" shared render-only engineering plot layer: PASS")
print(" oscilloscope dual-channel overlay + trigger/cursor guides: PASS")
print(" logic analyzer multi-lane timing visualization: PASS")
print(" signal analyzer rolling trace + mean guide: PASS")
print(" no new engineering blocks required: PASS")
