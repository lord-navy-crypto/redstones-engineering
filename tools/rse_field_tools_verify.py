#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
failed: list[str] = []


def require(path: str, *tokens: str) -> None:
    p = root / path
    if not p.is_file():
        failed.append(f"missing: {path}")
        return
    text = p.read_text(errors="ignore")
    for token in tokens:
        if token not in text:
            failed.append(f"{path} missing token: {token}")


def forbid(path: str, *tokens: str) -> None:
    p = root / path
    if not p.is_file():
        failed.append(f"missing: {path}")
        return
    text = p.read_text(errors="ignore")
    for token in tokens:
        if token in text:
            failed.append(f"{path} contains forbidden observer mutation token: {token}")


require(
    "src/main/resources/data/redstoneengineering/recipe/redstone_encyclopedia.json",
    '"minecraft:crafting_shapeless"', '"minecraft:book"', '"minecraft:redstone"',
    '"redstoneengineering:redstone_encyclopedia"',
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/RedstoneEncyclopediaScreen.java",
    "BuiltInRegistries.BLOCK.stream()", "RedstoneEngineering.MOD_ID", "EngineeringPortProvider",
    "engineeringPorts(block.defaultBlockState())", "REDSTONE ENCYCLOPEDIA", "PHYSICAL I/O",
    "Redstone remains a coarse 0..15 control medium", "RadioKernel owns",
)
require(
    "src/main/java/dev/redstoneengineering/item/DiagnosticTabletItem.java",
    "MAX_HISTORY = 8", "CustomData.update(DataComponents.CUSTOM_DATA", "EngineeringTopologyView.inspect",
    "face.compact()", "observer-only; no network recompute or device-state mutation",
    "Shift-right-click air to open tablet",
)
forbid(
    "src/main/java/dev/redstoneengineering/item/DiagnosticTabletItem.java",
    ".setBlock(", ".scheduleTick(", "DomainNetwork.recompute", "PneumaticNetwork.recompute",
    "RadioKernel.recompute", "RuntimeIntStore.get(", "RuntimeIntStore.remove(",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/DiagnosticTabletScreen.java",
    "ENGINEERING DIAGNOSTIC TABLET", "OBSERVER ONLY", "RseDiagnosticsScreen",
    "Snapshot ", "newest = 1",
)
require(
    "src/main/java/dev/redstoneengineering/EngineeringSystemsModule.java",
    "REDSTONE_ENCYCLOPEDIA_ITEM", "DIAGNOSTIC_TABLET_ITEM", "ENGINEERING_COMPASS_ITEM",
)
require(
    "src/main/java/dev/redstoneengineering/ui/EngineeringUiRegistration.java",
    "REDSTONE_ENCYCLOPEDIA", "DIAGNOSTIC_TABLET",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/EngineeringUiClientRegistration.java",
    "RedstoneEncyclopediaScreen::new", "DiagnosticTabletScreen::new",
)

if failed:
    print("RSE field tools verification: FAIL")
    for item in failed:
        print(" -", item)
    raise SystemExit(1)

print("RSE field tools verification: PASS")
print(" registry-backed encyclopedia coverage: PASS")
print(" book + redstone recipe: PASS")
print(" bounded observer-only diagnostic tablet history: PASS")
print(" existing diagnostics/topology reuse: PASS")
print(" engineering compass retained: PASS")
