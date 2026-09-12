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


# Universal HMI must expose server-authoritative RX-only and TX-only actions based on
# the device's declared physical port contract, not on a hard-coded route-kind whitelist.
require(
    "src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java",
    "BUTTON_INPUT_LEFT", "BUTTON_INPUT_RIGHT", "BUTTON_OUTPUT_LEFT", "BUTTON_OUTPUT_RIGHT",
    "case BUTTON_INPUT_LEFT -> rotateInput(false)",
    "case BUTTON_INPUT_RIGHT -> rotateInput(true)",
    "case BUTTON_OUTPUT_LEFT -> rotateOutput(false)",
    "case BUTTON_OUTPUT_RIGHT -> rotateOutput(true)",
    "hasInputEndpoint()", "hasOutputEndpoint()",
    "(inputMask.get() | bidirectionalMask.get()) != 0",
    "(outputMask.get() | bidirectionalMask.get()) != 0",
    "DirectionalSignalBlock.rotateWholeRoute(level, blockPos, clockwise)",
    "DirectionalDomainBlock.rotateWholeRoute(level, blockPos, clockwise)",
    "DirectionalSignalBlock.rotateSeriesInput(level, blockPos, clockwise)",
    "DirectionalDomainBlock.rotateSeriesInput(level, blockPos, clockwise)",
    "DirectionalSignalBlock.rotateSeriesOutput(level, blockPos, clockwise)",
    "DirectionalDomainBlock.rotateSeriesOutput(level, blockPos, clockwise)",
    "routeKind(block) == ROUTE_MULTI_PORT_LAYOUT",
)

# Shared Route HMI must wire endpoint controls to Universal actions and decide visibility
# from actual RX/TX presence.
require(
    "src/main/java/dev/redstoneengineering/client/ui/EngineeringScreen.java",
    "UniversalFieldDeviceMenu.BUTTON_INPUT_LEFT", "UniversalFieldDeviceMenu.BUTTON_INPUT_RIGHT",
    "UniversalFieldDeviceMenu.BUTTON_OUTPUT_LEFT", "UniversalFieldDeviceMenu.BUTTON_OUTPUT_RIGHT",
    "universal.hasInputEndpoint()", "universal.hasOutputEndpoint()",
    'Component.literal("RX ▲")', 'Component.literal("RX ▼")',
    'Component.literal("TX ▲")', 'Component.literal("TX ▼")',
)

# Remaining generic directional processors should still prefer Universal before the legacy field HMI.
# The legacy fallback is now endpoint-aware as well, so this is an architecture preference rather than
# protection against TX.opposite() snapshots.
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

# Legacy FieldDevice fallback must also expose endpoint-aware controls without converting media
# or standalone measurement/interface axes into fake RX/TX controls.
require(
    "src/main/java/dev/redstoneengineering/ui/menu/FieldDeviceMenu.java",
    "BUTTON_INPUT_PREVIOUS", "BUTTON_INPUT_NEXT", "BUTTON_OUTPUT_PREVIOUS", "BUTTON_OUTPUT_NEXT",
    "hasInputEndpoint()", "hasOutputEndpoint()", "endpointRoutable(block)",
    "DirectionalSignalBlock.rotateSeriesInput", "DirectionalSignalBlock.rotateSeriesOutput",
    "DirectionalDomainBlock.rotateSeriesInput", "DirectionalDomainBlock.rotateSeriesOutput",
)

if errors:
    print("RSE UNIVERSAL DUAL-ENDPOINT VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE UNIVERSAL DUAL-ENDPOINT VERIFY: PASS")
print(" port-driven Universal RX/TX authority: PASS")
print(" shared Route RX/TX endpoint wiring: PASS")
print(" multi-port rigid-layout collision guard: PASS")
print(" legacy FieldDevice endpoint-aware fallback: PASS")
print(" explicit INPUT_FACING/FACING physical contract preserved: PASS")
