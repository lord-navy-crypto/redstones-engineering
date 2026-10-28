#!/usr/bin/env python3
"""All-registry UI contract gate: 130 block entries + every bound LDLib2 menu.

A source check, NOT a claim of Minecraft runClient, screenshots or GameTest
success. The one explicit passive world-datum exception is preserved.
"""
from pathlib import Path
from collections import Counter
import json
import re
import sys

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
SRC = ROOT / "src/main/java/dev/redstoneengineering"
OUT = ROOT / ".rse-audit"
OUT.mkdir(parents=True, exist_ok=True)
errors = []

def read(path):
    path = ROOT / path
    if not path.is_file():
        errors.append(f"missing source: {path.relative_to(ROOT)}")
        return ""
    return path.read_text(encoding="utf-8", errors="replace")

registration = read("src/main/java/dev/redstoneengineering/client/ui/EngineeringUiClientRegistration.java")
base = read("src/main/java/dev/redstoneengineering/ui/menu/EngineeringDeviceMenu.java")
shared = read("src/main/java/dev/redstoneengineering/ui/ldlib/RseLdUiComponents.java")
audit_path = OUT / "rse-130-block-hmi-coverage.json"
if not audit_path.exists():
    errors.append("missing prerequisite registry audit; run tools/rse_130_block_hmi_coverage.py first")
    audit = {"blocks": [], "summary": {}}
else:
    audit = json.loads(audit_path.read_text(encoding="utf-8"))
records = audit.get("blocks", [])
if len(records) != 130:
    errors.append(f"expected 130 registered block records, got {len(records)}")
if audit.get("summary", {}).get("errors"):
    errors.append("the 130-block source interaction audit contains unresolved errors")
if Counter(row.get("registry") for row in records) != Counter({"core": 122, "systems": 8}):
    errors.append("core/system registration split changed without re-auditing")

exceptions = [x for x in records if x.get("status") == "INTENTIONAL_NON_GUI"]
if [x.get("id") for x in exceptions] != ["engineering_compass"]:
    errors.append("the sole permitted non-menu block must be engineering_compass")
compass = read("src/main/java/dev/redstoneengineering/block/EngineeringCompassBlock.java")
for token in ('"World datum', '• Pos=', 'player.displayClientMessage', 'InteractionResult.sidedSuccess'):
    if token not in compass:
        errors.append(f"compass read-only coordinate interaction missing: {token!r}")

for row in records:
    if row.get("status") not in ("DECLARED_HMI_ENTRY", "INTENTIONAL_NON_GUI"):
        errors.append(f"{row.get('id')}: no declared client-facing interaction")
    if row.get("status") == "DECLARED_HMI_ENTRY" and row.get("entry_kind") == "NO_GUI":
        errors.append(f"{row.get('id')}: declared UI has no source opener")

# Each registered block menu must eventually use the same contextual
# server-status control. Tool menus (encyclopedia/tablet) are not block HMI.
registrations = re.findall(
    r'event\.register\(EngineeringUiRegistration\.([A-Z0-9_]+)\.get\(\),'
    r'\s*([A-Za-z0-9_]+Screen)::new\);', registration
)
tool_only = {"REDSTONE_ENCYCLOPEDIA", "DIAGNOSTIC_TABLET"}
block_screens = [(key, screen) for key, screen in registrations if key not in tool_only]
if len(block_screens) != 23 or len({key for key, _ in block_screens}) != 23:
    errors.append(f"expected exactly 23 registered block-screen families, got {len(block_screens)}")
if len(registrations) != 25:
    errors.append(f"expected 23 block screens + 2 tools, got {len(registrations)}")
for key in tool_only:
    if sum(k == key for k, _ in registrations) != 1:
        errors.append(f"tool menu registration missing or duplicated: {key}")

required_base = (
    "public int operationalHealth()",
    "public String operationalHealthLabel()",
    "public String evidenceStateLabel()",
    "public String topologyRoleLabel()",
    "public String portRouteLabel()",
    "public BlockPos blockPos()",
    "this.operationalHealth.set(HEALTH_UNVERIFIED)",
    "this.evidenceState.set(EVIDENCE_NOT_READY)",
    "refreshOperationalHealth();",
    "refreshEvidenceState();",
)
for token in required_base:
    if token not in base:
        errors.append(f"common menu loses authoritative evidence contract: {token!r}")
for token in (
    "public static UIElement deviceContext(EngineeringDeviceMenu menu)",
    "EngineeringDeviceMenu.HEALTH_UNVERIFIED",
    "menu.blockPos().toShortString()",
    "menu.operationalHealthLabel()",
    "menu.topologyRoleLabel()",
    "menu.evidenceStateLabel()",
    "menu.portRouteLabel()",
    "liveText(() ->",
):
    if token not in shared:
        errors.append(f"shared HMI evidence context missing: {token!r}")

families = []
for menu_key, screen in block_screens:
    family = screen.removesuffix("Screen")
    hmi_name = family + "LdUi"
    ui_path = f"src/main/java/dev/redstoneengineering/ui/ldlib/{hmi_name}.java"
    screen_path = f"src/main/java/dev/redstoneengineering/client/ui/{screen}.java"
    ui_src = read(ui_path)
    screen_src = read(screen_path)
    match = re.search(
        r'public\s+static\s+ModularUI\s+create\(\s*([A-Za-z0-9_]+Menu)'
        r'\s+([A-Za-z0-9_]+)\s*,\s*Player\s+player\s*\)', ui_src)
    if not match:
        errors.append(f"{hmi_name}: missing typed LDLib2 create entry")
        continue
    menu_class, arg = match.groups()
    menu_src = read(f"src/main/java/dev/redstoneengineering/ui/menu/{menu_class}.java")

    context_call = f"RseLdUiComponents.deviceContext({arg})"
    if ui_src.count(context_call) != 1:
        errors.append(f"{hmi_name}: must show exactly one universal evidence strip ({context_call})")
    if "RseLdUiComponents.responsiveUi(root, player" not in ui_src:
        errors.append(f"{hmi_name}: must retain responsive LDLib2 layout")
    root_index = ui_src.find("root.addChildren(")
    context_index = ui_src.find(context_call)
    if root_index < 0 or not (root_index < context_index < ui_src.find("return RseLdUiComponents.responsiveUi(", root_index)):
        errors.append(f"{hmi_name}: context not mounted inside actual rendered root")
    if f"extends LdlibEngineeringHostScreen<{menu_class}>" not in screen_src:
        errors.append(f"{screen}: not a registered typed LDLib2 menu host")
    if f"{hmi_name}.create(this, inventory.player)" not in menu_src:
        errors.append(f"{menu_class}: menu does not instantiate its checked LDLib2 HMI")
    if f"extends EngineeringDeviceMenu" not in menu_src:
        errors.append(f"{menu_class}: cannot provide uniform server evidence")
    families.append({"menu_registry": menu_key, "screen": screen,
                     "menu": menu_class, "hmi": hmi_name,
                     "context_mounted": context_call in ui_src})

if len(families) != 23:
    errors.append(f"verified {len(families)} / 23 block-facing UI families")

# Per-block report: interaction route verified by the complementary registry
# audit, and the 23 registered screens verified here. This is NOT a traced
# assertion that every block maps to a particular dynamic FieldDeviceUi branch.
matrix = []
for row in records:
    is_exception = row.get("status") == "INTENTIONAL_NON_GUI"
    matrix.append({
        "registry": row.get("registry"),
        "id": row.get("id"),
        "class": row.get("class"),
        "source_entry": row.get("entry_kind"),
        "interaction_owner": row.get("interaction_owner"),
        "coverage": "PASSIVE_DATUM_READOUT" if is_exception else "REGISTERED_BLOCK_MENU_CONTRACT",
        "runtime_client_checked": False,
        "multiplayer_checked": False,
        "screenshot_checked": False,
    })
summary = {
    "registered": len(matrix),
    "core": sum(x["registry"] == "core" for x in matrix),
    "systems": sum(x["registry"] == "systems" for x in matrix),
    "shared_hmi_families": len(families),
    "exception": "engineering_compass",
    "source_contract_pass": not errors,
    "errors": errors,
    "scope": "static registry/registration/menu/screen/LDLib2 contract; no real-client or GameTest acceptance",
}
(OUT / "rse-130-block-visual-rollout.json").write_text(
    json.dumps({"summary": summary, "families": families, "blocks": matrix},
               indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
lines = [
    "# RSE all-registry visual source contract",
    "",
    "This report is **static evidence only**, not verified Minecraft screenshots.",
    "",
    f"- Registry: {len(matrix)} blocks (122 core + 8 systems)",
    f"- Block-facing LDLib2 families: {len(families)} / 23",
    "- Intentional non-menu block: engineering_compass (server world-datum actionbar)",
    "- Every other block: declared HMI entry; all registered block menus: shared identity/health/evidence/route header",
    "- In-game opening, layout, interactions, multiplayer and GameTests: **not certified**",
    "",
    "| ID | Registry | Source entry | Contract |",
    "| --- | --- | --- | --- |",
]
for row in matrix:
    lines.append(f"| {row['id']} | {row['registry']} | {row['source_entry']} | {row['coverage']} |")
(OUT / "rse-130-block-visual-rollout.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
if errors:
    print("RSE ALL-BLOCK VISUAL CONTRACT: FAIL")
    for error in errors:
        print(" -", error)
    sys.exit(1)
print("RSE ALL-BLOCK VISUAL CONTRACT: PASS")
print(f" 130 registry interactions: 129 declared menus + 1 intentional passive datum")
print(f" {len(families)} block HMI families: registered screen + typed menu + live device strip")
print(" JSON/Markdown evidence generated; runtime and screenshots are not certified")
