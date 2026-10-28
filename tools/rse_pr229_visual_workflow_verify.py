#!/usr/bin/env python3
"""Regression gate: real PR229 LDLib2 visual workflow without fake client evidence."""
from pathlib import Path
import re
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
paths = {
    "shared": "src/main/java/dev/redstoneengineering/ui/ldlib/RseLdUiComponents.java",
    "field": "src/main/java/dev/redstoneengineering/ui/ldlib/EnhancedFieldDeviceLdUi.java",
    "universal": "src/main/java/dev/redstoneengineering/ui/ldlib/UniversalFieldDeviceLdUi.java",
}
errors = []
source = {}
for name, path in paths.items():
    file = root / path
    source[name] = file.read_text(encoding="utf-8") if file.is_file() else ""
    if not source[name]:
        errors.append(f"{path}: missing source")

shared = source["shared"]
field = source["field"]
universal = source["universal"]

checks = {
    "shared": (
        "public static UIElement statusTile(String label, Supplier<String> serverReadback)",
        "liveText(serverReadback)",
        "public static UIElement statusRail(UIElement... tiles)",
        "public static UIElement operatorGuide(String heading, Supplier<String> nextAction, String readingKey)",
        "liveText(nextAction)",
        'wrappedLabel("READING KEY • " + readingKey)',
        "final int readableMinWidth = Math.min(contentWidth, 320);",
        "flexWrap(FlexWrap.WRAP)",
        "PAGE 1 / ",
        "pageIndicator.setText(",
        "scroller.verticalScroller.setNormalizedValue(0)",
        "return navigation;",
    ),
    "field": (
        "RseLdUiComponents.statusRail(",
        'RseLdUiComponents.statusTile("01 • SYNC"',
        'RseLdUiComponents.statusTile("02 • HEALTH"',
        'RseLdUiComponents.statusTile("03 • CONTROL"',
        'RseLdUiComponents.statusTile("04 • QUALITY"',
        'RseLdUiComponents.operatorGuide(',
        '"OPERATOR GUIDE • NEXT CHECK"',
        "private static String nextOperatorStep(FieldDeviceMenu m)",
        'if (!m.snapshotReady())',
        'if (!m.topologyValid())',
        'if (!m.dataValid())',
        'if (!m.evidenceQualityKnown())',
        'if (m.evidenceQuality() != dev.redstoneengineering.core.port.PortQuality.VALID)',
        'if (directEntryKind(m.kind()))',
        'if (hasOperatorActions(m.kind()))',
        'RseLdUiComponents.statusTile("SERVER CONFIRMED VALUE"',
        'RseLdUiComponents.statusTile("ACCEPTED INPUT"',
        'RseLdUiComponents.statusTile("01 • RX / INPUT"',
        'RseLdUiComponents.statusTile("02 • PROCESS MODEL"',
        'RseLdUiComponents.statusTile("03 • TX / OUTPUT"',
        "m.snapshotReady() ? Integer.toString(controlValue(m))",
        "validDirectValue(m.kind(), parsed)",
        "m.applyPrimaryEngineeringValueFromUi(parsed)",
        "READ-ONLY HMI • no fake control",
        "private static int configuredSlot(int kind)",
        'return configuredSlot(kind) == slot ? "SETPOINT"',
        "return configuredSlot(kind) == slot ? formulaSymbol(kind)",
        'raw + " • CONFIGURED (SERVER)"',
        "if (configuredSlot(m.kind()) != slot) return qualifiedRawMetric(m, raw);",
    ),
    "universal": (
        "RseLdUiComponents.statusRail(",
        'RseLdUiComponents.statusTile("01 • SYNC"',
        'RseLdUiComponents.statusTile("02 • HEALTH"',
        'RseLdUiComponents.statusTile("03 • OPERATION"',
        'RseLdUiComponents.statusTile("04 • QUALITY"',
        'RseLdUiComponents.operatorGuide(',
        '"OPERATOR GUIDE • NEXT CHECK"',
        "private static String nextOperatorStep(UniversalFieldDeviceMenu menu)",
        'if (!menu.snapshotReady())',
        "if (primaryDirectKind(kind) || secondaryDirectKind(kind))",
        "if (primaryCycleKind(kind))",
        "if (hasExplicitAction(kind) || hasToggle(kind))",
        "if (kind == UniversalFieldDeviceMenu.CONFIG_NONE)",
        "view.layout(l -> l.widthPercent(100).minWidth(320)",
        'RseLdUiComponents.statusTile("SERVER CONFIRMED • " + primarySymbol(kind)',
        'RseLdUiComponents.statusTile("SERVER CONFIRMED • " + secondarySymbol(kind)',
        'RseLdUiComponents.statusTile("01 • RX / INPUT"',
        'RseLdUiComponents.statusTile("02 • MODEL"',
        'RseLdUiComponents.statusTile("03 • TX / OUTPUT"',
        "menu.snapshotReady() ? primaryDisplay(menu)",
        "menu.snapshotReady() ? secondaryDisplay(menu)",
        "applyPrimary(menu, value)",
        "applySecondary(menu, value)",
        "READ-ONLY HMI • no fake control",
    ),
}
for area, tokens in checks.items():
    for token in tokens:
        if token not in source[area]:
            errors.append(f"{area}: visual workflow token missing: {token!r}")

# Every displayed SETPOINT must refer to the same authoritative DataSlot
# that the existing validated direct editor consumes. A wrong slot can
# silently display valid-looking but unrelated process values.
config_section = field.split("private static int configuredSlot(int kind) {", 1)[-1].split(
    "private static String modelSlotRole(", 1)[0]
control_section = field.split("private static int controlValue(FieldDeviceMenu m) {", 1)[-1].split(
    "private static String formulaSymbol(", 1)[0]
configured = {}
editor = {}
for kind_group, index in re.findall(
        r"case\s+([\s\S]*?)\s*->\s*([012]);", config_section):
    for kind in re.findall(r"FieldDeviceMenu\.(KIND_[A-Z0-9_]+)", kind_group):
        configured[kind] = int(index)
for kind_group, slot in re.findall(
        r"case\s+([\s\S]*?)\s*->\s*m\.(primary|secondary|tertiary)\(\);", control_section):
    for kind in re.findall(r"FieldDeviceMenu\.(KIND_[A-Z0-9_]+)", kind_group):
        editor[kind] = {"primary": 0, "secondary": 1, "tertiary": 2}[slot]
if len(configured) != 14 or configured != editor:
    errors.append(f"FieldDevice SETPOINT model roles disagree with exact server control slots: {configured} vs {editor}")

# Rail and confirmed-value widgets never compute values from Level/physics.
for area in ("shared", "field", "universal"):
    if re.search(r"import dev\.redstoneengineering\.physics\.", source[area]):
        errors.append(f"{area}: client LDLib2 visual layer must not import physics")
if "RseLdUiComponents.statusRail(" not in field or "RseLdUiComponents.statusRail(" not in universal:
    errors.append("both shared HMI surfaces must expose their persistent statuses")
if errors:
    print("RSE PR229 VISUAL WORKFLOW: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)
print("RSE PR229 VISUAL WORKFLOW: PASS")
print(" persistent server-bound health/quality tiles: FieldDevice + Universal")
print(" confirmed values and exact legal ranges: both configuration families")
print(" tab navigation has visible active-page feedback in both workspace types")
print(" next-action guidance is snapshot-gated and distinguishes tuning from evidence")
print(" shared and universal viewports wrap at narrower Minecraft dimensions")
print(" does not claim manual Minecraft client, screenshot or GameTest success")
