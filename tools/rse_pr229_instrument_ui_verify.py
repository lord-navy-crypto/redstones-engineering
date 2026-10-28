#!/usr/bin/env python3
"""Source-level regression for PR229 instrument legends and evidence-first help.

No claim of in-game visual acceptance or Minecraft GameTest success is made.
"""
from pathlib import Path
import re
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
base = Path("src/main/java/dev/redstoneengineering/ui/ldlib")
errors = []

def read(filename: str) -> str:
    path = root / base / filename
    if not path.is_file():
        errors.append(f"{filename}: source missing")
        return ""
    return path.read_text(encoding="utf-8")

shared = read("RseLdUiComponents.java")
legend = read("RsePlotLegendElement.java")
scope = read("OscilloscopeLdUi.java")
scope_plot = read("OscilloscopePlotElement.java")
logic = read("LogicAnalyzerLdUi.java")
logic_plot = read("LogicAnalyzerPlotElement.java")
pid = read("PidControllerLdUi.java")
pid_plot = read("PidTrendPlotElement.java")

for token in (
    'operatorGuide(String heading, Supplier<String> nextAction, String readingKey)',
    'liveText(nextAction)',
):
    if token not in shared:
        errors.append(f"shared operator guide regressed: {token}")

for token in (
    "extends UIElement",
    "void drawBackgroundAdditional(GUIContext context)",
    "getSizeWidth()",
    "getSizeHeight()",
    "if (x != startX && x + itemWidth > right)",
    "context.graphics.fill(",
    "context.graphics.drawString(",
    "labels.clone()",
    "colors.clone()",
):
    if token not in legend:
        errors.append(f"legend drawing or responsive bounds lost: {token}")
if re.search(r"import\s+net\.minecraft\.world\.", legend):
    errors.append("Legend must be render-only, not read the Minecraft world")

def find_legend(name: str, hmi: str):
    matched = re.search(
        r'new RsePlotLegendElement\(\s*new String\[\]\s*\{([^}]*)\}\s*,'
        r'\s*new int\[\]\s*\{([^}]*)\}\s*\)',
        hmi, re.S)
    if not matched:
        errors.append(f"{name}: missing live plot legend")
        return [], []
    labels = re.findall(r'"([^"]+)"', matched.group(1))
    colors = [int(v, 16) for v in re.findall(r"0x[0-9a-fA-F]{8}", matched.group(2))]
    if len(labels) != len(colors):
        errors.append(f"{name}: mismatched swatch labels and colors")
    return labels, colors

s_labels, s_colors = find_legend("scope", scope)
l_labels, l_colors = find_legend("logic", logic)
p_labels, p_colors = find_legend("PID", pid)

# Text labels provide a second, noncolor identification channel; sampled plot
# colors must not accidentally diverge from their swatches.
scope_values = [
    re.search(rf"private static final int {key}\s*=\s*(0x[0-9A-Fa-f]{{8}})", scope_plot)
    for key in ("CHANNEL_A", "CHANNEL_B", "TRIGGER", "CURSOR_A", "CURSOR_B")
]
if any(x is None for x in scope_values):
    errors.append("scope: original plot colors could not be audited")
elif s_labels != ["CH-A", "CH-B", "TRIGGER", "CURSOR A", "CURSOR B"] or s_colors != [
        int(x.group(1), 16) for x in scope_values]:
    errors.append("scope legend does not match actual plotted channel/marker colors")

logic_palette = re.search(r"COLORS\s*=\s*\{([^}]*)\}", logic_plot)
if not logic_palette:
    errors.append("logic: plot channel colors unavailable")
else:
    actual = [int(v, 16) for v in re.findall(r"0x[0-9A-Fa-f]{8}", logic_palette.group(1))]
    if l_labels != ["CH-A", "CH-B", "CH-C", "CH-D", "CURSOR A", "CURSOR B"] or l_colors != actual + [
            0xFF68D391, 0xFFC084FC]:
        errors.append("logic legend does not match actual plotted channels/cursors")
for marker in ("0xFF68D391", "0xFFC084FC"):
    if marker not in logic_plot:
        errors.append(f"logic cursor marker changed without legend update: {marker}")

pid_colors = [
    re.search(rf"private static final int {key}\s*=\s*(0x[0-9A-Fa-f]{{8}})", pid_plot)
    for key in ("SP", "PV", "OUT")
]
if any(x is None for x in pid_colors):
    errors.append("PID: original plot colors could not be audited")
elif p_labels != ["SP", "PV", "OUT"] or p_colors != [int(x.group(1), 16) for x in pid_colors]:
    errors.append("PID legend does not match plotted SP/PV/OUT")

guards = {
    "scope": (scope, [
        "private static String nextScopeStep(OscilloscopeMenu menu)",
        "if (menu.sampleCount() <= 0)",
        "if (!menu.bounded())",
        "if (menu.duplicateChannels() > 0)",
        "menu.displaySample(0, menu.cursorA())",
        "menu.displaySample(1, menu.cursorB())",
        "menu.aliasRisk(0) != 3",
    ]),
    "logic": (logic, [
        "private static String nextLogicStep(LogicAnalyzerMenu m)",
        "if (m.sampleCount() <= 0)",
        "if (!m.bounded())",
        "if (m.duplicateChannels() > 0)",
        "if (m.validChannels() <= 0)",
        "m.displayState(ch, m.cursorA())",
        "m.displayState(ch, m.cursorB())",
    ]),
    "PID": (pid, [
        "private static String nextPidStep(PidControllerMenu m)",
        "if (m.inhibited())",
        "if (!m.available())",
        "if (m.manualMode())",
        "if (!m.runtimeTermsAvailable())",
        "if (m.trendCount() <= 0)",
        "if (m.plantDetected() && !m.plantReady())",
    ]),
}
for name, (source, tokens) in guards.items():
    if "RseLdUiComponents.operatorGuide(" not in source:
        errors.append(f"{name}: missing contextual overview")
    for token in tokens:
        if token not in source:
            errors.append(f"{name}: missing evidence guard {token!r}")

for name, source in (("scope", scope), ("logic", logic), ("PID", pid)):
    if "RsePlotLegendElement(" not in source:
        errors.append(f"{name}: visual legend missing")
    if re.search(r"import dev\.redstoneengineering\.physics\.", source):
        errors.append(f"{name}: UI must not import independent physics")

if errors:
    print("RSE PR229 INSTRUMENT UI: FAIL")
    for error in errors:
        print(" -", error)
    sys.exit(1)
print("RSE PR229 INSTRUMENT UI: PASS")
print(" real plotted colors match labeled legends for Scope, Logic and PID")
print(" acquisition/measurement/plant hints derived from existing server menus")
print(" no in-game render, multiplayer sync or GameTest acceptance asserted")
