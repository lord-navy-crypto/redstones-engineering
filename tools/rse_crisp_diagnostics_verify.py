#!/usr/bin/env python3
"""Static safety / integration regression for pixel-crisp RSE logs.

This is intentionally not a screenshot or GPU-driver validation.
"""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
base=root/"src/main/java/dev/redstoneengineering/client/ui"
helper=(base/"RseCrispBackground.java").read_text("utf-8")
assert "graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), DIMMER)" in helper
assert "renderBackground" in helper, "Document the reason for avoiding blur"
assert "renderBackground(" not in helper, "Helper must never invoke blur"
assert "graphics.pose().scale" not in helper
assert "setShader" not in helper
screens=(
    base/"RseDiagnosticsScreen.java",
    base/"DiagnosticTabletScreen.java",
    base/"EngineeringScreen.java",
    base/"RedstoneEncyclopediaScreen.java",
    base/"ldlib/LdlibEngineeringHostScreen.java",
)
for path in screens:
    data=path.read_text("utf-8")
    assert "RseCrispBackground.render(graphics);" in data, path
    assert "renderBackground(graphics" not in data, path
    assert "graphics.pose().scale(" not in data, path
    assert "RseDiagnostics.record(" not in data, path
diagnostics=(base/"RseDiagnosticsScreen.java").read_text("utf-8")
assert "Copy Report" in diagnostics
assert "keyboardHandler.setClipboard" in diagnostics
assert "GLFW.GLFW_KEY_C" in diagnostics and "hasControlDown()" in diagnostics
assert "compactLayout()" in diagnostics and "truncateToWidth(runtimeSummary()" in diagnostics
assert "RseDiagnostics.exportReport(runtimeSummary())" in diagnostics
assert "setBlock(" not in diagnostics and "scheduleTick(" not in diagnostics
tablet=(base/"DiagnosticTabletScreen.java").read_text("utf-8")
assert "graphics.enableScissor(" in tablet and "graphics.disableScissor();" in tablet
assert "drawWrapped(" in tablet and "menu.history()" in tablet
host=(base/"ldlib/LdlibEngineeringHostScreen.java").read_text("utf-8")
assert "IModularUIHolderMenu" in host
assert "RseCrispBackground.render(graphics);" in host
print("RSE_CRISP_DIAGNOSTICS_OK: native unblurred overlay, preserved actual logs, compact widgets, Ctrl+C export")
