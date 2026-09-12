#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing dual-endpoint contract file: {rel}")
        return ""
    return path.read_text(encoding="utf-8", errors="ignore")


def require(rel: str, *tokens: str) -> None:
    body = read(rel)
    for token in tokens:
        if body and token not in body:
            errors.append(f"{rel}: missing dual-endpoint contract token {token!r}")


# Universal HMI must expose server-authoritative RX-only, TX-only, and whole-route actions.
require(
    "src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java",
    "BUTTON_INPUT_LEFT", "BUTTON_INPUT_RIGHT", "BUTTON_OUTPUT_LEFT", "BUTTON_OUTPUT_RIGHT",
    "case BUTTON_INPUT_LEFT -> rotateInput(false)",
    "case BUTTON_INPUT_RIGHT -> rotateInput(true)",
    "case BUTTON_OUTPUT_LEFT -> rotateOutput(false)",
    "case BUTTON_OUTPUT_RIGHT -> rotateOutput(true)",
    "DirectionalSignalBlock.rotateWholeRoute(level, blockPos, clockwise)",
    "DirectionalDomainBlock.rotateWholeRoute(level, blockPos, clockwise)",
    "DirectionalSignalBlock.rotateSeriesInput(level, blockPos, clockwise)",
    "DirectionalDomainBlock.rotateSeriesInput(level, blockPos, clockwise)",
    "DirectionalSignalBlock.rotateSeriesOutput(level, blockPos, clockwise)",
    "DirectionalDomainBlock.rotateSeriesOutput(level, blockPos, clockwise)",
    "independentRouteEndpoints()", "routeKind.get() == ROUTE_SERIES_AXIS",
)

# Shared Route HMI must actually wire the four endpoint controls to Universal menu actions.
require(
    "src/main/java/dev/redstoneengineering/client/ui/EngineeringScreen.java",
    "UniversalFieldDeviceMenu.BUTTON_INPUT_LEFT", "UniversalFieldDeviceMenu.BUTTON_INPUT_RIGHT",
    "UniversalFieldDeviceMenu.BUTTON_OUTPUT_LEFT", "UniversalFieldDeviceMenu.BUTTON_OUTPUT_RIGHT",
    "universal.independentRouteEndpoints()",
)

# Remaining generic directional processors must not fall through to the legacy kind-table menu,
# whose historical snapshots still contain TX.opposite() assumptions. Specialized menus may match first.
field_ui = read("src/main/java/dev/redstoneengineering/ui/FieldDeviceUi.java")
redirect = "if (block instanceof DirectionalSignalBlock || block instanceof DirectionalDomainBlock)"
legacy_open = "new FieldDeviceMenu(id, inv, pos)"
if field_ui:
    if redirect not in field_ui:
        errors.append("FieldDeviceUi.java: missing universal redirect for remaining directional processors")
    if "openUniversal(player, pos); return;" not in field_ui:
        errors.append("FieldDeviceUi.java: missing Universal HMI handoff")
    redirect_at = field_ui.find(redirect)
    legacy_at = field_ui.find(legacy_open)
    if redirect_at < 0 or legacy_at < 0 or redirect_at > legacy_at:
        errors.append("FieldDeviceUi.java: directional Universal redirect must occur before legacy FieldDeviceMenu fallback")

# The physical model itself must retain explicit independent endpoint state and operations.
require(
    "src/main/java/dev/redstoneengineering/block/DirectionalSignalBlock.java",
    "INPUT_FACING", "seriesInputSide(BlockState state)", "seriesOutputSide(BlockState state)",
    "rotateWholeRoute", "rotateSeriesInput", "rotateSeriesOutput",
)
require(
    "src/main/java/dev/redstoneengineering/block/DirectionalDomainBlock.java",
    "INPUT_FACING", "seriesInputSide(BlockState state)", "seriesOutputSide(BlockState state)",
    "rotateWholeRoute", "rotateSeriesInput", "rotateSeriesOutput",
)

if errors:
    print("RSE UNIVERSAL DUAL-ENDPOINT VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE UNIVERSAL DUAL-ENDPOINT VERIFY: PASS")
print(" Universal port-driven HMI RX/TX/ALL authority: PASS")
print(" shared Route endpoint wiring: PASS")
print(" remaining directional fallback bypasses legacy TX.opposite snapshots: PASS")
print(" explicit INPUT_FACING/FACING physical contract preserved: PASS")
