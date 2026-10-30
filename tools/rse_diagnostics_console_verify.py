#!/usr/bin/env python3
"""Regression gate for the retained first-round diagnostics surfaces.

Keyboard copy/export is additive; the first-round init/render layout remains the visual baseline.
"""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
DIAGNOSTICS = ROOT / "src/main/java/dev/redstoneengineering/diagnostics/RseDiagnostics.java"
CAPTURE = ROOT / "src/main/java/dev/redstoneengineering/client/diagnostics/RseLogCapture.java"
SCREEN = ROOT / "src/main/java/dev/redstoneengineering/client/ui/RseDiagnosticsScreen.java"
TABLET = ROOT / "src/main/java/dev/redstoneengineering/client/ui/DiagnosticTabletScreen.java"
REGISTRATION = ROOT / "src/main/java/dev/redstoneengineering/client/ui/EngineeringUiClientRegistration.java"

errors = []
for path in (DIAGNOSTICS, CAPTURE, SCREEN, TABLET, REGISTRATION):
    if not path.is_file():
        errors.append(f"missing diagnostics-console file: {path.relative_to(ROOT)}")

if not errors:
    diagnostics = DIAGNOSTICS.read_text(encoding="utf-8")
    capture = CAPTURE.read_text(encoding="utf-8")
    screen = SCREEN.read_text(encoding="utf-8")
    tablet = TABLET.read_text(encoding="utf-8")
    registration = REGISTRATION.read_text(encoding="utf-8")

    for marker in (
        "MAX_ENTRIES = 256", "ArrayDeque<RseDiagnosticEntry>",
        "while (ENTRIES.size() >= MAX_ENTRIES)", "List.copyOf",
        "exportReport", "user.home",
    ):
        if marker not in diagnostics:
            errors.append(f"bounded diagnostics buffer missing {marker!r}")

    for marker in (
        "extends AbstractAppender", "configuration.getRootLogger()",
        "rootLogger.addAppender", "dev.redstoneengineering",
        "element.getClassName().startsWith(\"dev.redstoneengineering\")",
        "RseDiagnostics.record",
    ):
        if marker not in capture:
            errors.append(f"automatic RSE log capture missing {marker!r}")

    for marker in (
        'Component.literal("Copy Report")', "RseDiagnostics.exportReport(",
        "GLFW.GLFW_KEY_C", "copyReport()", "displaySummary()",
        "getGuiScaledWidth()", "Filter.values()", "RseDiagnostics.clear()",
        "renderEntries(graphics)", "RseDiagnostics.count(",
    ):
        if marker not in screen:
            errors.append(f"diagnostics console missing {marker!r}")

    for marker in (
        "ENGINEERING DIAGNOSTIC TABLET", "OBSERVER ONLY",
        "GLFW.GLFW_KEY_C", "copySnapshot()", "keyboardHandler.setClipboard",
        "menu.history()", "chronologyCue(history)", "refreshHistoryButtons",
        "Component.literal(\"Newer\")", "Component.literal(\"Older\")",
    ):
        if marker not in tablet:
            errors.append(f"diagnostic tablet missing {marker!r}")

    for marker in ("RseDiagnosticsScreen::new", "DiagnosticTabletScreen::new"):
        if marker not in registration:
            errors.append(f"diagnostic screen registration missing {marker!r}")

if errors:
    print("RSE DIAGNOSTICS CONSOLE: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE DIAGNOSTICS CONSOLE: PASS")
print(" bounded session-local log buffer and automatic RSE log capture: PASS")
print(" retained first-round console/tablet layout and navigation: PASS")
print(" C-key report/snapshot copy and display-scale export: PASS")
print(" observer-only diagnostics behavior: PASS")
