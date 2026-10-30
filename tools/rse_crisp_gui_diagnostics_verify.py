#!/usr/bin/env python3
"""Regression gate: engineering screens render text above a shader-free scrim.

This source check detects accidental reintroduction of Minecraft's menu blur
path. It does not substitute for a real client-side screenshot at GUI Scale.
"""
from pathlib import Path
import sys

root=Path(sys.argv[1] if len(sys.argv)>1 else ".").resolve()
ui=root/"src/main/java/dev/redstoneengineering/client/ui"
backdrop=(ui/"RseCrispGuiBackdrop.java").read_text(encoding="utf-8")
targets=[
    "RseDiagnosticsScreen.java",
    "DiagnosticTabletScreen.java",
    "EngineeringScreen.java",
    "RedstoneEncyclopediaScreen.java",
    "ldlib/LdlibEngineeringHostScreen.java",
]
assert "graphics.fill(0, 0, guiWidth, guiHeight, SCRIM);" in backdrop
assert "renderBackground(" not in backdrop
assert "RenderSystem." not in backdrop
assert "pose().scale(" not in backdrop
assert "0xC8000000" in backdrop

for path in targets:
    src=(ui/path).read_text(encoding="utf-8")
    assert "RseCrispGuiBackdrop.render(graphics, width, height);" in src, path
    assert "renderBackground(graphics" not in src, path
    assert "pose().scale(" not in src, path
    assert "super.render(graphics, mouseX, mouseY, partialTick);" in src, path
    assert "drawString(font" in src or path.endswith("LdlibEngineeringHostScreen.java"), path

# Read-only telemetry and log controls must not be removed by a visual fix.
console=(ui/"RseDiagnosticsScreen.java").read_text(encoding="utf-8")
for token in ("RseDiagnostics.snapshot()", "RseDiagnostics.exportReport(",
              "RseDiagnostics.clear()", "Filter.values()", "renderEntries(graphics)",
              "RseDiagnostics.count("):
    assert token in console, token

assert "IModularUIHolderMenu" in (ui/"ldlib/LdlibEngineeringHostScreen.java").read_text()
print("RSE_CRISP_GUI_DIAGNOSTICS_OK: 5 Minecraft/LDLib2 screens use a plain "
      "GUI scrim instead of menu blur; session log/filter/copy preserved")
print("Manual testing required: GUI Scale 1-4, F2 screenshot, shaders/HUD mods, "
      "windowed/full-screen and fresh modpack.")
