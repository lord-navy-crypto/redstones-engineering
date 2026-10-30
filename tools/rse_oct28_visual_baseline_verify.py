#!/usr/bin/env python3
"""Protect the 2026-10-28 first-round RSE visual architecture.

Later engineering content may be added to the LDLib2 pages, but the shared
workspace scroller, host surfaces and screen layout methods remain anchored
to the accepted first-round implementation.
"""
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
BASE = "d6ee5db74b92750f2c71cce50231d88c4177934d"
errors = []

def baseline_text(path: str) -> str:
    return subprocess.check_output(
        ["git", "show", f"{BASE}:{path}"], cwd=ROOT, text=True
    )

def extract_block(source: str, signature: str) -> str:
    start = source.find(signature)
    if start < 0:
        raise ValueError(f"method not found: {signature}")
    opening = source.find("{", start)
    if opening < 0:
        raise ValueError(f"method body not found: {signature}")
    depth = 0
    state = "code"
    escaped = False
    i = opening
    while i < len(source):
        ch = source[i]
        nxt = source[i + 1] if i + 1 < len(source) else ""
        if state == "line":
            if ch == "\n":
                state = "code"
        elif state == "block":
            if ch == "*" and nxt == "/":
                state = "code"
                i += 1
        elif state == "string":
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == '"':
                state = "code"
        elif state == "char":
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == "'":
                state = "code"
        else:
            if ch == "/" and nxt == "/":
                state = "line"
                i += 1
            elif ch == "/" and nxt == "*":
                state = "block"
                i += 1
            elif ch == '"':
                state = "string"
            elif ch == "'":
                state = "char"
            elif ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    return source[start:i + 1]
        i += 1
    raise ValueError(f"unterminated method: {signature}")

def compare_method(path: str, signature: str) -> None:
    old = baseline_text(path)
    current = (ROOT / path).read_text(encoding="utf-8")
    if extract_block(old, signature) != extract_block(current, signature):
        errors.append(f"first-round visual method changed: {path} :: {signature}")

def compare_file(path: str) -> None:
    old = baseline_text(path)
    current = (ROOT / path).read_text(encoding="utf-8")
    if old != current:
        errors.append(f"first-round visual host file changed: {path}")

unchanged_files = (
    "src/main/java/dev/redstoneengineering/client/ui/EngineeringScreen.java",
    "src/main/java/dev/redstoneengineering/client/ui/RedstoneEncyclopediaScreen.java",
    "src/main/java/dev/redstoneengineering/client/ui/ldlib/LdlibEngineeringHostScreen.java",
)
for path in unchanged_files:
    compare_file(path)

tablet = "src/main/java/dev/redstoneengineering/client/ui/DiagnosticTabletScreen.java"
for signature in (
    "protected void init()",
    "public boolean mouseScrolled(",
    "protected void renderBg(",
    "protected void renderLabels(",
    "public void render(",
):
    compare_method(tablet, signature)

console = "src/main/java/dev/redstoneengineering/client/ui/RseDiagnosticsScreen.java"
for signature in (
    "protected void init()",
    "public void render(",
    "private void renderEntries(",
    "private List<RseDiagnosticEntry> filteredEntries()",
):
    compare_method(console, signature)

shared = "src/main/java/dev/redstoneengineering/ui/ldlib/RseLdUiComponents.java"
compare_method(shared, "public static UIElement tabbedWorkspace(")
shared_text = (ROOT / shared).read_text(encoding="utf-8")
for marker in (
    ".mode(ScrollerMode.BOTH)",
    ".verticalScrollDisplay(ScrollDisplay.AUTO)",
    ".horizontalScrollDisplay(ScrollDisplay.AUTO)",
    "Math.min(contentWidth, 320)",
    ".minWidth(readableMinWidth)",
):
    if marker not in shared_text:
        errors.append(f"two-axis first-round scroller contract missing {marker!r}")

ldlib = ROOT / "src/main/java/dev/redstoneengineering/ui/ldlib"
for path in ldlib.glob("*.java"):
    text = path.read_text(encoding="utf-8")
    if "minHeight(0)" in text or "END OF PAGE" in text:
        errors.append(f"rejected later viewport/page-marker experiment found in {path.name}")

if (ROOT / "src/main/java/dev/redstoneengineering/client/ui/RseCrispGuiBackdrop.java").exists():
    errors.append("later crisp-backdrop visual replacement must not override the first-round screen")

if errors:
    print("RSE OCT28 FIRST-ROUND VISUAL BASELINE: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE OCT28 FIRST-ROUND VISUAL BASELINE: PASS")
print("screen layout methods and host files match the accepted first-round source")
print("shared LDLib2 workspace retains automatic vertical + horizontal scrolling")
print("later engineering content remains additive without replacing navigation architecture")
