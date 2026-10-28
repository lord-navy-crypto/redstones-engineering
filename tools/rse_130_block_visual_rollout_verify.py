#!/usr/bin/env python3
"""All-registry UI contract gate: 130 block entries + every bound LDLib2 menu.

A source check, NOT a claim of Minecraft runClient, screenshots or GameTest
success. The one explicit passive world-datum exception is preserved.
"""
from pathlib import Path
from collections import Counter
import json
import csv
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
    "public String deviceDisplayName()",
    "public String operatorCheckLabel()",
    "public static String operatorCheckLabel(int health, int evidence)",
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
    "menu.deviceDisplayName()",
    "menu.operatorCheckLabel()",
    'details.setDisplay(false)',
    'details.setDisplay(expanded[0])',
    'new Button().setText("Show evidence ▼")',
    "menu.operationalHealthLabel()",
    "menu.topologyRoleLabel()",
    "menu.evidenceStateLabel()",
    "menu.portRouteLabel()",
    "liveText(() ->",
):
    if token not in shared:
        errors.append(f"shared HMI evidence context missing: {token!r}")


# For ALL 23 families, guide priority must be deterministic and must not
# equate missing/unknown evidence with a physical fault.
guide_start = base.find("public static String operatorCheckLabel(int health, int evidence)")
guide_end = base.find("public int receivePortMask()", guide_start)
if guide_start < 0 or guide_end <= guide_start:
    errors.append("missing bounded common status-to-next-check projection")
    guide = ""
else:
    guide = base[guide_start:guide_end]
checks = [
    ("initial unsynced", "health == HEALTH_UNVERIFIED"),
    ("physical fault", "health == HEALTH_FAULT"),
    ("topology error", "evidence == EVIDENCE_TOPOLOGY_ERROR"),
    ("domain mismatch", "evidence == EVIDENCE_DOMAIN_MISMATCH"),
    ("evidence fault", "evidence == EVIDENCE_FAULT"),
    ("protective", "health == HEALTH_PROTECTIVE"),
    ("degraded", "health == HEALTH_DEGRADED"),
    ("stale", "evidence == EVIDENCE_STALE"),
    ("saturated", "evidence == EVIDENCE_SATURATED"),
    ("not ready", "evidence == EVIDENCE_NOT_READY"),
    ("no signal", "evidence == EVIDENCE_NO_SIGNAL"),
    ("unobserved", "evidence == EVIDENCE_UNOBSERVED"),
    ("valid evidence", "evidence != EVIDENCE_VALID"),
]
last = -1
for description, token in checks:
    pos = guide.find(token)
    if pos < 0:
        errors.append(f"missing next-check branch: {description}")
    elif pos <= last:
        errors.append(f"incorrect next-check priority ordering: {description}")
    else:
        last = pos
for token in ("NO PORT EVIDENCE • no fault verdict",
              "NO SIGNAL • verify expected source before treating this as a fault",
              "PROTECTIVE STATE • inspect safety conditions",
              "WAIT FOR SERVER • initial health"):
    if token not in guide:
        errors.append(f"unsafe or missing status language: {token!r}")
for forbidden in ("getBlockState(", "scheduleTick(", "setBlock(", "sendMenuButton(",
                  "handleInventoryButtonClick(", "onServerClick("):
    if forbidden in guide:
        errors.append(f"operator guidance must be read-only: {forbidden!r}")
if "details.setDisplay(false)" not in shared:
    errors.append("compact device header must keep detailed panel collapsed initially")
if "details.setDisplay(expanded[0])" not in shared:
    errors.append("evidence toggle must actually change detail visibility")
if "detailToggle.setOnClick(event ->" not in shared or "setOnServerClick" in shared.split(
        "public static UIElement deviceContext(EngineeringDeviceMenu menu)", 1)[-1].split(
        "public static UIElement fixedRow(", 1)[0]:
    errors.append("details toggle must be client-local, never a server parameter action")


# All-block detail text must distinguish formal port capabilities from
# observed connectivity; passive/source/sink devices are not necessarily bad.
role_start = base.find("public static String portRoleHintLabel(int role, int rxMask, int txMask)")
role_end = base.find("public String evidenceMeaningLabel()", role_start)
if role_start < 0 or role_end <= role_start:
    errors.append("missing pure formal-port role guidance")
    role_guide = ""
else:
    role_guide = base[role_start:role_end]
for role in (
    "TOPOLOGY_OBSERVER", "TOPOLOGY_SOURCE", "TOPOLOGY_SINK",
    "TOPOLOGY_PASSIVE", "TOPOLOGY_PASSIVE_SERIES",
    "TOPOLOGY_EXPLICIT_JUNCTION", "TOPOLOGY_CONTROLLED_SOURCE",
    "TOPOLOGY_CONTROLLED_SERIES", "TOPOLOGY_UNKNOWN",
):
    if role not in role_guide:
        errors.append(f"missing non-misleading port contract case: {role}")
for token in ("not automatic bridging", "not proof of live connectivity",
              "an absent TX face can be intentional",
              "an absent RX face can be intentional"):
    if token not in role_guide:
        errors.append(f"unsafe/absent role explanation: {token!r}")

quality_start = base.find("public static String evidenceMeaningLabel(int evidence)")
quality_end = base.find("/** Formal receive/transmit faces", quality_start)
if quality_start < 0 or quality_end <= quality_start:
    errors.append("missing quality semantics projection")
    quality_guide = ""
else:
    quality_guide = base[quality_start:quality_end]
for state in (
    "EVIDENCE_VALID", "EVIDENCE_NO_SIGNAL", "EVIDENCE_SATURATED",
    "EVIDENCE_STALE", "EVIDENCE_FAULT", "EVIDENCE_DOMAIN_MISMATCH",
    "EVIDENCE_TOPOLOGY_ERROR", "EVIDENCE_NOT_READY",
):
    if state not in quality_guide:
        errors.append(f"missing evidence explanation: {state}")
for phrase in ("not full acceptance", "not a fault verdict",
               "could be an expected idle input", "as a current sample"):
    if phrase not in quality_guide:
        errors.append(f"missing quality caveat: {phrase}")

for scoped in (role_guide, quality_guide):
    for mutation in ("getBlockState(", "setBlock(", "scheduleTick(", "onServerClick("):
        if mutation in scoped:
            errors.append(f"the shared explanation must not mutate the world: {mutation}")
for token in ('"PORT CONTRACT • " + menu.portRoleHintLabel()',
              '"QUALITY MEANING • " + menu.evidenceMeaningLabel()'):
    if token not in shared:
        errors.append(f"all HMI family details no longer show {token}")
if "details.setDisplay(false)" not in shared:
    errors.append("extended role/quality guidance must not block compact devices")

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
                     "context_mounted": context_call in ui_src,
                     "shared_next_check": "menu.operatorCheckLabel()" in shared,
                     "expandable_evidence": "details.setDisplay(expanded[0])" in shared})

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
        "common_diagnostics": "PASSIVE_NOT_APPLICABLE" if is_exception else "SHARED_ALL_FAMILIES",
        "runtime_client_checked": False,
        "multiplayer_checked": False,
        "screenshot_checked": False,
    })
summary = {
    "registered": len(matrix),
    "core": sum(x["registry"] == "core" for x in matrix),
    "systems": sum(x["registry"] == "systems" for x in matrix),
    "shared_hmi_families": len(families),
    "operator_next_check": "HEALTH_EVIDENCE_READ_ONLY",
    "diagnostics_toggle": "CLIENT_ONLY_EXPANDABLE",
    "port_role_guidance": "ROLE_AWARE_NON_CONNECTIVITY_PROOF",
    "quality_guidance": "EVIDENCE_STATE_NOT_FAULT_VERDICT",
    "manual_checklist": "130_ROWS_ALL_NOT_TESTED",
    "exception": "engineering_compass",
    "source_contract_pass": not errors,
    "errors": errors,
    "scope": "static registry/registration/menu/screen/LDLib2 contract; no real-client or GameTest acceptance",
}

# In-game checklist is generated for EVERY block, not only selected screens.
# A status of NOT_TESTED is intentional: static analysis cannot claim the GUI
# opened, controls were usable or multiplayer values remained server-owned.
manual_rows = []
for row in matrix:
    passive = row["id"] == "engineering_compass"
    manual_rows.append({
        "id": row["id"],
        "registry": row["registry"],
        "java_class": row["class"],
        "source_entry": row["source_entry"],
        "expected_interaction": (
            "right-click: server-side world-direction/coordinate readout"
            if passive else "right-click: corresponding LDLib2 engineering HMI"
        ),
        "verify_identity": "NOT_APPLICABLE" if passive else "NOT_TESTED",
        "verify_health_and_evidence": "NOT_APPLICABLE" if passive else "NOT_TESTED",
        "verify_port_role_and_route": "NOT_APPLICABLE" if passive else "NOT_TESTED",
        "verify_detail_toggle": "NOT_APPLICABLE" if passive else "NOT_TESTED",
        "verify_original_controls": "NOT_APPLICABLE" if passive else "NOT_TESTED",
        "verify_small_gui_scales": "NOT_APPLICABLE" if passive else "NOT_TESTED",
        "verify_multiplayer": "NOT_TESTED",
        "verify_game_test": "NOT_TESTED",
        "notes": "",
    })
if len(manual_rows) != 130 or len({x["id"] for x in manual_rows}) != 130:
    errors.append("not all 130 registry IDs have a unique acceptance checklist row")
check_path = OUT / "rse-130-block-manual-ui-checklist.csv"
with check_path.open("w", newline="", encoding="utf-8") as stream:
    writer = csv.DictWriter(stream, fieldnames=list(manual_rows[0]))
    writer.writeheader()
    writer.writerows(manual_rows)

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
    "- All 23 menu families: next-check advice + role-specific port and quality interpretation + local-only expanded details",
    "- All 130 blocks: manual in-game acceptance row in rse-130-block-manual-ui-checklist.csv (initially NOT_TESTED)",
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
print(" shared health/quality next-check, port policy and collapsed evidence inspector: SOURCE CHECKED")
print(" 130-row in-game acceptance checklist emitted; all items start NOT_TESTED")
print(" JSON/Markdown evidence generated; runtime and screenshots are not certified")
