#!/usr/bin/env python3
"""Static contract checks for the RSE handheld encyclopedia and diagnostic tablet."""
from pathlib import Path
import json, sys

ROOT = Path(__file__).resolve().parents[1]
ASSET = ROOT / "src/main/resources/assets/redstoneengineering/models/item"
ITEM = ROOT / "src/main/java/dev/redstoneengineering/item/DiagnosticTabletItem.java"
SCREEN = ROOT / "src/main/java/dev/redstoneengineering/client/ui/DiagnosticTabletScreen.java"
errors = []


def require(condition, message):
    if not condition:
        errors.append(message)


def load_model(name):
    path = ASSET / name
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        errors.append(f"{name}: invalid or missing JSON: {exc}")
        return {}


tablet = load_model("diagnostic_tablet.json")
book = load_model("redstone_encyclopedia.json")
for name, model, forbidden in (
    ("diagnostic_tablet.json", tablet, "minecraft:item/map"),
    ("redstone_encyclopedia.json", book, "minecraft:item/book"),
):
    encoded = json.dumps(model)
    require(model.get("parent") != "minecraft:item/generated", f"{name}: must not remain a flat generated item")
    require(len(model.get("elements", [])) >= 3, f"{name}: expected a dimensional multi-element model")
    require(forbidden not in encoded, f"{name}: still reuses legacy vanilla placeholder texture {forbidden}")
    require(":block:" not in encoded, f"{name}: malformed resource location uses namespace:block:texture instead of namespace:block/texture")
    require("display" in model and "gui" in model.get("display", {}), f"{name}: missing explicit handheld/GUI display transforms")

item = ITEM.read_text(encoding="utf-8") if ITEM.exists() else ""
screen = SCREEN.read_text(encoding="utf-8") if SCREEN.exists() else ""
require("openTablet(serverPlayer, tablet);" in item, "tablet: scanning a block must immediately open the retained snapshot")
require("openTablet(serverPlayer, stack);" in item, "tablet: right-click air must reopen retained history")
require("tag.putString(SNAPSHOT_PREFIX + 0, snapshot);" in item, "tablet: newest snapshot must be written to history slot 0")
require("MAX_HISTORY = 8" in item, "tablet: bounded eight-snapshot history contract missing")
require("TARGET FACE:" in item and "context.getClickedFace()" in item, "tablet: clicked-face context must be retained")
require("CONTEXT: dimension=" in item and 'append(" • tick=")' in item, "tablet: retained snapshot must carry dimension/tick chronology evidence")
for quality in ("VALID", "NO_SIGNAL", "SATURATED", "STALE", "FAULT", "DOMAIN_MISMATCH", "TOPOLOGY_ERROR"):
    require(f"q={quality}" in screen, f"tablet screen: current PortQuality {quality} is not classified")
require("q=DEGRADED" not in screen and "q=MISSING" not in screen, "tablet screen: stale/dead PortQuality names remain")
require("history.get(0)" in screen, "tablet screen: chronology must compare against newest retained slot 0")
require('return "NEWEST";' in screen, "tablet screen: newest snapshot chronology cue missing")
require('return "CROSS-DIMENSION";' in screen, "tablet screen: cross-dimension chronology cue missing")
require('"Δt=" + delta + " ticks"' in screen, "tablet screen: same-dimension tick delta cue missing")
require("SnapshotContext" in screen and "Long.parseLong(tickText)" in screen, "tablet screen: retained context parsing contract missing")
require("comparisonCue(history)" in screen and "comparisonColor(history)" in screen, "tablet screen: retained comparison row missing")
require('lineValue(newest, "ID:")' in screen and 'lineValue(selected, "ID:")' in screen, "tablet screen: comparison must retain block identity")
require('lineValue(newest, "POS:")' in screen and 'lineValue(selected, "POS:")' in screen, "tablet screen: comparison must retain target position")
require('return "OTHER TARGET • independent evidence";' in screen, "tablet screen: unrelated retained targets must not be presented as one timeline")
require('return "SAME TARGET • STATUS CHANGED";' in screen, "tablet screen: same-target status transition cue missing")
require('return "SAME TARGET • TOPOLOGY CHANGED";' in screen, "tablet screen: same-target topology transition cue missing")
require('return "SAME TARGET • status unchanged";' in screen, "tablet screen: same-target stable-status cue missing")

if errors:
    print("RSE HANDHELD TOOLS VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    sys.exit(1)

print("RSE HANDHELD TOOLS VERIFY: PASS")
print("  encyclopedia/tablet: dimensional RSE-owned engineering models")
print("  handheld model resource locations: canonical namespace:path form")
print("  tablet: scan -> retain -> immediate review; air -> retained history")
print("  tablet: newest slot 0, bounded history, clicked-face context")
print("  tablet: retained chronology distinguishes newest, same-dimension age, and cross-dimension evidence")
print("  tablet: retained comparison distinguishes same-target transitions from unrelated evidence")
print("  tablet: current PortQuality evidence semantics classified")
