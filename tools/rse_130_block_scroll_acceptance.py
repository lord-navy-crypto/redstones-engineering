#!/usr/bin/env python3
"""130-block source-to-scroll-host trace and real-client acceptance checklist.

This is a static integration audit. It proves that each registered GUI block
resolves through its source-declared right-click route to a registered menu,
a client screen, and a bounded LDLib2 tab scroller with a visible last row.
It does NOT execute a Minecraft graphics client or certify a wheel gesture.
"""
import csv
import json
from collections import Counter
from pathlib import Path
import re
import sys

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
SRC = ROOT / "src/main/java/dev/redstoneengineering"
AUDIT = ROOT / ".rse-audit"
AUDIT.mkdir(parents=True, exist_ok=True)
failures = []

def get(relative):
    path = SRC / relative
    if not path.is_file():
        failures.append("Missing source: " + relative)
        return ""
    return path.read_text(encoding="utf-8", errors="replace")

inventory_path = AUDIT / "rse-130-block-hmi-coverage.json"
if not inventory_path.exists():
    raise SystemExit("Run rse_130_block_hmi_coverage.py first")
inventory = json.loads(inventory_path.read_text(encoding="utf-8"))
registered = inventory["blocks"]
if len(registered) != 130:
    failures.append("Expected 130 registered blocks, saw " + str(len(registered)))

registration = get("ui/EngineeringUiRegistration.java")
client_registration = get("client/ui/EngineeringUiClientRegistration.java")
router = get("ui/FieldDeviceUi.java")
common = get("ui/ldlib/RseLdUiComponents.java")
menu_to_screen = {}
menu_by_key = {}
for match in re.finditer(
    r'MenuType<([A-Za-z0-9_]+Menu)>>\s+([A-Z0-9_]+)\s*=\s*MENUS\.register', registration
):
    menu, key = match.groups()
    menu_by_key[key] = menu
for match in re.finditer(
    r'event\.register\(EngineeringUiRegistration\.([A-Z0-9_]+)\.get\(\),\s*([A-Za-z0-9_]+Screen)::new\);',
    client_registration,
):
    key, screen = match.groups()
    menu = menu_by_key.get(key)
    if menu is None:
        failures.append("Unresolved screen menu: " + key)
    elif menu in menu_to_screen:
        failures.append("Duplicated screen binding for " + menu)
    else:
        menu_to_screen[menu] = screen

# Read the actual conditional FieldDeviceUi dispatcher, preserving declared
# order: the first matching instanceof wins. Class inheritance is respected.
routes = []
for match in re.finditer(r'if\s*\((.*?)\)\s*\{(.*?)\breturn;\s*\}', router, re.S):
    condition, body = match.groups()
    classes = re.findall(r'block\s+instanceof\s+([A-Za-z0-9_]+)', condition)
    dest = re.search(r'new\s+([A-Za-z0-9_]+Menu)\s*\(', body)
    if not classes:
        continue
    menu = dest.group(1) if dest else ("UniversalFieldDeviceMenu" if "openUniversal(player, pos)" in body else None)
    if menu is None:
        failures.append("Unknown FieldDeviceUi conditional target: " + condition[:100])
    else:
        routes.append((classes, menu))

parents = {}
def ancestry(cls):
    seen = set()
    while cls and cls not in seen:
        yield cls
        seen.add(cls)
        if cls not in parents:
            source = get("block/" + cls + ".java")
            pattern = r'\bclass\s+' + re.escape(cls) + r'\b[^\{]*?\bextends\s+([A-Za-z0-9_]+)'
            match = re.search(pattern, source, re.S)
            parents[cls] = match.group(1) if match else ""
        cls = parents[cls]

def route_for_shared(cls):
    chain = set(ancestry(cls))
    for accepted, menu in routes:
        if chain.intersection(accepted):
            return menu
    return "FieldDeviceMenu"

def method_body(owner):
    source = get("block/" + owner + ".java")
    start = re.search(r'\buseWithoutItem\s*\(', source)
    if not start:
        return ""
    begin = source.find("{", start.end())
    if begin < 0:
        return ""
    level = 1
    i = begin + 1
    while i < len(source) and level:
        if source[i] == "{": level += 1
        elif source[i] == "}": level -= 1
        i += 1
    return source[begin:i]

def resolve_menu(record):
    kind = record["entry_kind"]
    cls = record["class"]
    body = method_body(record["interaction_owner"])
    if kind == "UNIVERSAL_LDLIB2":
        return "UniversalFieldDeviceMenu"
    if kind == "SHARED_OR_DEDICATED_LDLIB2":
        if "FieldDeviceUi.open(player, " not in body and "FieldDeviceUi.open(" not in body:
            failures.append(cls + ": shared opener lost from interaction")
        return route_for_shared(cls)
    if kind == "DEDICATED_LDLIB2":
        match = re.search(r'new\s+([A-Za-z0-9_]+Menu)\s*\(', body)
        if match:
            return match.group(1)
    if kind == "DEDICATED_HELPER_LDLIB2":
        call = re.search(r'\b([A-Za-z0-9_]+Ui)\.open\s*\(', body)
        if call:
            helper = get("ui/" + call.group(1) + ".java")
            match = re.search(r'new\s+([A-Za-z0-9_]+Menu)\s*\(', helper)
            if match:
                return match.group(1)
    failures.append(cls + ": cannot trace GUI route " + kind)
    return ""

scroll_spec = {
    "tested_gui_scale": "2,3,4,Auto",
    "mouse_wheel_down_to_footer": "NOT_TESTED",
    "mouse_wheel_up_to_start": "NOT_TESTED",
    "tab_switch_reset_and_reopen": "NOT_TESTED",
    "scrollbar_drag_to_footer": "NOT_TESTED",
    "shift_wheel_horizontal_where_needed": "NOT_TESTED",
    "formula_and_evidence_visible": "NOT_TESTED",
    "controls_and_server_readback_unchanged": "NOT_TESTED",
    "no_blurry_background": "NOT_TESTED",
    "screenshot_evidence": "",
}
rows = []
for block in registered:
    passive = block["status"] == "INTENTIONAL_NON_GUI"
    menu = "" if passive else resolve_menu(block)
    screen = menu_to_screen.get(menu, "") if menu else ""
    if not passive and not screen:
        failures.append(block["id"] + ": no registered client screen for " + menu)

    hmi = ("EnhancedFieldDeviceLdUi" if screen == "EnhancedFieldDeviceScreen"
           else screen.removesuffix("Screen") + "LdUi") if screen else ""
    source = get("ui/ldlib/" + hmi + ".java") if hmi else ""
    shared = "RseLdUiComponents.tabbedWorkspace(" in source
    standalone = "new ScrollerView()" in source
    if not passive and shared == standalone:
        failures.append(block["id"] + ": ambiguous scroll host: " + hmi)
    if not passive and not (
            "RseLdUiComponents.responsiveUi(root, player" in source and
            (shared or "RseLdUiComponents.standaloneTabs(workspace," in source)):
        failures.append(block["id"] + ": no bound scrollable HMI root")

    if not passive and standalone:
        for token in ("workspace.layout(l -> l.widthPercent(100).flex(1).minHeight(0));",
                      "workspace.viewPort(view -> view.layout(l -> l.flex(1).minHeight(0)",
                      "verticalScrollDisplay(ScrollDisplay.AUTO)"):
            if token not in source:
                failures.append(block["id"] + ": standalone viewport missing " + token)

    rows.append({
        "block_id": block["id"],
        "block_class": block["class"],
        "registry": block["registry"],
        "interaction_owner": block["interaction_owner"],
        "menu": menu or "NONE_PASSIVE",
        "screen": screen or "NONE_PASSIVE",
        "scroll_host": "NOT_APPLICABLE" if passive else ("SHARED_18_FAMILIES" if shared else "STANDALONE_5_FAMILIES"),
        "static_scroll_route": "PASS" if not passive else "NOT_APPLICABLE",
        "footer_witness": "NOT_APPLICABLE" if passive else "SOURCE_PRESENT",
        "client_verified": False,
        **({key: ("NOT_APPLICABLE" if passive and value == "NOT_TESTED" else value)
            for key,value in scroll_spec.items()}),
    })

# Source-level protection: every page, in both classes of scroll hosts,
# MUST append a visible bottom marker; all pages must be selectable.
for token in (
    'page.addChild(note("END OF " + labels[i] + " • all page content above"));',
    'pages[i].addChild(note("END OF " + labels[i] + " • all page content above"));',
    'scroller.verticalScroller.setNormalizedValue(0);',
    'pageIndicator.setText(',
    'root.layout(layout -> layout.minHeight(0));',
):
    if token not in common:
        failures.append("Shared scroll/last-row invariant missing: " + token)

summary = {
    "total_registered": len(rows),
    "menu_expected": 129,
    "menu_source_routes": sum(x["menu"] != "NONE_PASSIVE" for x in rows),
    "non_menu_exceptions": [x["block_id"] for x in rows if x["menu"] == "NONE_PASSIVE"],
    "screen_families_used": dict(Counter(x["screen"] for x in rows if x["screen"] != "NONE_PASSIVE")),
    "scroll_host_distribution": dict(Counter(x["scroll_host"] for x in rows)),
    "real_client_verified": 0,
    "scroll_to_bottom_gameplay_certified": False,
    "method": "130 block register -> useWithoutItem owner -> server menu router -> client screen -> LDLib2 scroll host",
    "errors": failures,
}
if summary["menu_source_routes"] != 129:
    failures.append("Expected 129 block menus")
if summary["non_menu_exceptions"] != ["engineering_compass"]:
    failures.append("Unexpected non-menu exception list")

out_csv = AUDIT / "rse-130-block-scroll-acceptance.csv"
with out_csv.open("w",newline="",encoding="utf-8") as fp:
    writer=csv.DictWriter(fp,fieldnames=list(rows[0]))
    writer.writeheader()
    writer.writerows(rows)
(AUDIT / "rse-130-block-scroll-route-matrix.json").write_text(
    json.dumps({"summary":summary,"blocks":rows},indent=2,ensure_ascii=False) + "\n",encoding="utf-8")
md = [
    "# RSE 130-block scroll-route witness",
    "",
    "**Source-level route check only**. Client interaction and GUI Scale remain NOT_TESTED.",
    "",
    "- Source route trace: registered block → actual server opener → registered screen → LDLib2 scroll host",
    "- After opening each tab, the last visible content should be `END OF <TAB NAME>`",
    "- Test Mouse Wheel ↓ → footer, Mouse Wheel ↑ → top, scrollbar drag, tab reset",
    "- Check responsive GUI scales, controls/readback, graphs, clear text and multiplayer",
    "- A passive Engineering Compass has no GUI and is excluded from wheel-scroll testing",
    "",
    "| Block | Menu | Screen | Host | Client |",
    "| --- | --- | --- | --- | --- |",
]
md.extend(f"| {r['block_id']} | {r['menu']} | {r['screen']} | {r['scroll_host']} | NOT TESTED |" for r in rows)
(AUDIT / "rse-130-block-scroll-route-matrix.md").write_text("\n".join(md) + "\n",encoding="utf-8")
if failures:
    print("RSE 130-BLOCK SCROLL ROUTE: FAIL")
    for failure in failures[:70]:
        print(" -", failure)
    raise SystemExit(1)
print("RSE 130-BLOCK SCROLL ROUTE: PASS")
print(" 130 registered, 129 GUI routes, one passive compass")
print(" Screen families:", len(summary["screen_families_used"]))
print(" Scrollable host distribution:", summary["scroll_host_distribution"])
print(" Written: CSV manual acceptance + JSON/Markdown route reports")
print(" Minecraft client acceptance: NOT TESTED")
