#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
failed = []

SCREENS = [
    "src/main/java/dev/redstoneengineering/client/ui/ProcessParameterNotebookScreen.java",
    "src/main/java/dev/redstoneengineering/client/ui/AdvancedParameterNotebookScreen.java",
    "src/main/java/dev/redstoneengineering/client/ui/MultiPhysicsParameterNotebookScreen.java",
    "src/main/java/dev/redstoneengineering/client/ui/PidEngineeringNotebookScreen.java",
    "src/main/java/dev/redstoneengineering/client/ui/LapisLowPassFilterScreen.java",
    "src/main/java/dev/redstoneengineering/client/ui/ServoActuatorNotebookScreen.java",
]

for rel in SCREENS:
    path = root / rel
    if not path.is_file():
        failed.append(f"missing engineering notebook screen: {rel}")
        continue
    text = path.read_text(errors="ignore")

    for token in (
        "VIEW_MARGIN",
        "CONTENT_TOP",
        "CONTENT_BOTTOM_MARGIN",
        "scrollOffset",
        "mouseScrolled",
        "enableScissor",
        "disableScissor",
        "maxScroll",
    ):
        if token not in text:
            failed.append(f"{rel} missing viewport/scroll contract token: {token}")

    if not re.search(r"imageWidth\s*=\s*Math\.max\([^;]*width\s*[-]", text):
        failed.append(f"{rel} does not derive imageWidth from live game viewport")
    if not re.search(r"imageHeight\s*=\s*Math\.max\([^;]*height\s*[-]", text):
        failed.append(f"{rel} does not derive imageHeight from live game viewport")

    if "g.pose().translate(0, -scrollOffset, 0)" not in text and "g.pose().translate(0,-scrollOffset,0)" not in text:
        failed.append(f"{rel} does not scroll rendered engineering content")

# Protect the actual principle, not one exact pixel value.
process = (root / SCREENS[0]).read_text(errors="ignore") if (root / SCREENS[0]).is_file() else ""
if "VIEW_MARGIN=8" not in process and "VIEW_MARGIN = 8" not in process:
    failed.append("viewport notebook shell no longer keeps the small 8px safety margin baseline")

workflow = root / ".github/workflows/build.yml"
if not workflow.is_file() or "rse_engineering_notebook_viewport_verify.py" not in workflow.read_text(errors="ignore"):
    failed.append("workflow missing engineering notebook viewport regression verifier")

if failed:
    print("RSE engineering notebook viewport verification: FAIL")
    for item in failed:
        print(" -", item)
    raise SystemExit(1)

print("RSE engineering notebook viewport verification: PASS")
print(" viewport-derived width/height: PASS")
print(" fixed header/footer + clipped scroll workspace: PASS")
print(" mouse-wheel scroll contract: PASS")
print(" six engineering notebook screens migrated: PASS")
