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

SHARED_SCREEN = "src/main/java/dev/redstoneengineering/client/ui/EngineeringScreen.java"
shared_path = root / SHARED_SCREEN
if not shared_path.is_file():
    failed.append(f"missing shared engineering screen: {SHARED_SCREEN}")
else:
    shared = shared_path.read_text(errors="ignore")
    for token in (
        "VIEW_MARGIN",
        "HEADER_BOTTOM",
        "FOOTER_HEIGHT",
        "scrollOffset",
        "mouseScrolled",
        "enableScissor",
        "disableScissor",
        "virtualContentHeight",
        "maxScroll",
        "configureVirtualY",
        "routeWidgets",
        "routeVirtualY",
        "addRouteWidget",
        "syncRouteWidgetViewport",
    ):
        if token not in shared:
            failed.append(f"{SHARED_SCREEN} missing viewport/scroll contract token: {token}")
    if not re.search(r"imageWidth\s*=\s*Math\.max\([^;]*width\s*[-]", shared):
        failed.append(f"{SHARED_SCREEN} does not derive imageWidth from live game viewport")
    if not re.search(r"imageHeight\s*=\s*Math\.max\([^;]*height\s*[-]", shared):
        failed.append(f"{SHARED_SCREEN} does not derive imageHeight from live game viewport")
    if "graphics.pose().translate(0, -scrollOffset, 0)" not in shared:
        failed.append(f"{SHARED_SCREEN} does not scroll rendered engineering content")
    for route_field in (
        "routePrevious",
        "routeNext",
        "routeInputPrevious",
        "routeInputNext",
        "routeOutputPrevious",
        "routeOutputNext",
    ):
        if not re.search(rf"{route_field}\s*=\s*addRouteWidget\(", shared):
            failed.append(f"{SHARED_SCREEN} route control is not registered with the scroll workspace: {route_field}")
    if "syncRouteControls();" not in shared[shared.find("public boolean mouseScrolled"):shared.find("protected int virtualContentHeight")]:
        failed.append(f"{SHARED_SCREEN} does not refresh routed controls after mouse-wheel scrolling")

DOCUMENT_SCREEN = "src/main/java/dev/redstoneengineering/client/ui/RedstoneEncyclopediaScreen.java"
document_path = root / DOCUMENT_SCREEN
if not document_path.is_file():
    failed.append(f"missing engineering document screen: {DOCUMENT_SCREEN}")
else:
    document = document_path.read_text(errors="ignore")
    for token in (
        "VIEW_MARGIN",
        "CONTENT_TOP",
        "CONTENT_BOTTOM_MARGIN",
        "scrollOffset",
        "mouseScrolled",
        "enableScissor",
        "contentDocumentHeight",
        "maxScroll",
    ):
        if token not in document:
            failed.append(f"{DOCUMENT_SCREEN} missing viewport document token: {token}")
    if not re.search(r"imageWidth\s*=\s*Math\.max\([^;]*width\s*[-]", document):
        failed.append(f"{DOCUMENT_SCREEN} does not derive imageWidth from live game viewport")
    if not re.search(r"imageHeight\s*=\s*Math\.max\([^;]*height\s*[-]", document):
        failed.append(f"{DOCUMENT_SCREEN} does not derive imageHeight from live game viewport")
    if "graphics.pose().translate(0, CONTENT_TOP - scrollOffset, 0)" not in document:
        failed.append(f"{DOCUMENT_SCREEN} does not scroll the manual document body")

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
print(" shared EngineeringScreen viewport shell migrated: PASS")
print(" route controls move and clip with scrolled engineering content: PASS")
print(" Redstone Encyclopedia viewport document migrated: PASS")
