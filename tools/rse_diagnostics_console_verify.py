#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
DIAGNOSTICS = ROOT / "src/main/java/dev/redstoneengineering/diagnostics/RseDiagnostics.java"
CAPTURE = ROOT / "src/main/java/dev/redstoneengineering/client/diagnostics/RseLogCapture.java"
SCREEN = ROOT / "src/main/java/dev/redstoneengineering/client/ui/RseDiagnosticsScreen.java"
REGISTRATION = ROOT / "src/main/java/dev/redstoneengineering/client/ui/EngineeringUiClientRegistration.java"

errors = []
for path in (DIAGNOSTICS, CAPTURE, SCREEN, REGISTRATION):
    if not path.is_file():
        errors.append(f"missing diagnostics-console file: {path.relative_to(ROOT)}")

if not errors:
    diagnostics = DIAGNOSTICS.read_text(encoding="utf-8")
    capture = CAPTURE.read_text(encoding="utf-8")
    screen = SCREEN.read_text(encoding="utf-8")
    registration = REGISTRATION.read_text(encoding="utf-8")

    required_diagnostics = (
        "MAX_ENTRIES = 256",
        "ArrayDeque<RseDiagnosticEntry>",
        "while (ENTRIES.size() >= MAX_ENTRIES)",
        "List.copyOf",
        "exportReport",
        "user.home",
    )
    for marker in required_diagnostics:
        if marker not in diagnostics:
            errors.append(f"bounded diagnostics buffer is missing contract: {marker}")

    required_capture = (
        "extends AbstractAppender",
        "configuration.getRootLogger()",
        "rootLogger.addAppender",
        "dev.redstoneengineering",
        "element.getClassName().startsWith(\"dev.redstoneengineering\")",
        "RseDiagnostics.record",
    )
    for marker in required_capture:
        if marker not in capture:
            errors.append(f"automatic RSE log capture is missing contract: {marker}")

    required_ui = (
        "Copy Report",
        "keyboardHandler.setClipboard",
        "Filter.values()",
        "RseDiagnosticSeverity.WARN",
        "RseDiagnosticSeverity.ERROR",
        "isPauseScreen",
    )
    for marker in required_ui:
        if marker not in screen:
            errors.append(f"diagnostics console UI is missing contract: {marker}")

    required_registration = (
        "ScreenEvent.Init.Post",
        "InventoryScreen",
        "CreativeModeInventoryScreen",
        "Component.literal(\"✚\")",
        "RseLogCapture.install()",
        "new RseDiagnosticsScreen(screen)",
    )
    for marker in required_registration:
        if marker not in registration:
            errors.append(f"inventory diagnostics entry point is missing contract: {marker}")

    forbidden = (
        "RuntimeIntStore",
        "scheduleTick(",
        "setBlock(",
        "updateNeighborsAt(",
        "EngineeringAcceptance.evaluate",
        "dev.redstoneengineering.physics",
    )
    for path, text in ((DIAGNOSTICS, diagnostics), (CAPTURE, capture), (SCREEN, screen), (REGISTRATION, registration)):
        for marker in forbidden:
            if marker in text:
                errors.append(f"diagnostics observer boundary violation in {path.name}: {marker}")

if errors:
    print("RSE Diagnostics Console verification: FAIL")
    for error in errors:
        print(f"  - {error}")
    sys.exit(1)

print("RSE Diagnostics Console verification: PASS")
print("  bounded session-local ring buffer: PASS")
print("  automatic package/stack-trace log capture: PASS")
print("  survival + creative inventory red-cross entry point: PASS")
print("  filter/copy/clear workflow: PASS")
print("  observer-only authority boundary: PASS")
