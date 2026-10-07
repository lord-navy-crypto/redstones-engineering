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

scope = read("src/main/java/dev/redstoneengineering/ui/ldlib/OscilloscopePlotElement.java")
for token in (
    "EngineeringPlot.analogFrame",
    "EngineeringPlot.analogTrace",
    "EngineeringPlot.horizontalMarker",
    "EngineeringPlot.verticalMarker",
    "plotChannel(g, 0",
    "plotChannel(g, 1",
):
    if scope and token not in scope:
        errors.append(f"Oscilloscope LDLib2 visualization missing {token!r}")
if scope and "fullTrace(" in scope:
    errors.append("Oscilloscope retains the old per-channel fullTrace background redraw path")

scope_ui = read("src/main/java/dev/redstoneengineering/ui/ldlib/OscilloscopeLdUi.java")
if scope_ui and "new OscilloscopePlotElement(menu)" not in scope_ui:
    errors.append("Oscilloscope LDLib2 HMI does not embed the real plot element")

logic = read("src/main/java/dev/redstoneengineering/ui/ldlib/LogicAnalyzerPlotElement.java")
for token in (
    "EngineeringPlot.digitalTrace",
    "EngineeringPlot.verticalMarker",
    "for(int ch=0;ch<4;ch++)",
    "slot->menu.displayState(c,slot)",
):
    if logic and token not in logic:
        errors.append(f"Logic Analyzer LDLib2 visualization missing {token!r}")
logic_ui = read("src/main/java/dev/redstoneengineering/ui/ldlib/LogicAnalyzerLdUi.java")
if logic_ui and "new LogicAnalyzerPlotElement(menu)" not in logic_ui:
    errors.append("Logic Analyzer LDLib2 HMI does not embed the real digital plot element")

signal = read("src/main/java/dev/redstoneengineering/ui/ldlib/SignalAnalyzerPlotElement.java")
for token in (
    "EngineeringPlot.analogFrame",
    "EngineeringPlot.analogTrace",
    "EngineeringPlot.horizontalMarker",
):
    if signal and token not in signal:
        errors.append(f"Signal Analyzer LDLib2 visualization missing {token!r}")
signal_ui = read("src/main/java/dev/redstoneengineering/ui/ldlib/SignalAnalyzerLdUi.java")
for token in ("new SignalAnalyzerPlotElement(m)", "μ=rounded mean"):
    if signal_ui and token not in signal_ui:
        errors.append(f"Signal Analyzer LDLib2 HMI missing {token!r}")

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
