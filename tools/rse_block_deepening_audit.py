#!/usr/bin/env python3
"""Non-blocking heuristic audit for RSE block depth.

The structural 122-block audit answers whether content is registered, covered and contract-safe.
This report asks a different question: where should existing blocks be deepened next?

Each registered block receives six independent 0/1/2 signals:
- function: time/state/domain behavior
- visual: world-readable state
- diagnostics: retained/quality/measurement evidence
- ui: explicit operator surface reachability
- integration: formal multi-device/system role
- failure: diagnosable abnormal/degraded behavior

These are planning signals, not quality grades. Passive transmission media are marked separately so
lack of a controller-style state machine is not treated as a defect.
"""
from __future__ import annotations

from collections import Counter
from pathlib import Path
import json
import re

import rse_122_block_total_audit as total

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "src/main/java/dev/redstoneengineering/RedstoneEngineering.java"
ASSETS = ROOT / "src/main/resources/assets/redstoneengineering"
UI_DIR = ROOT / "src/main/java/dev/redstoneengineering/ui"
OUT_DIR = ROOT / ".rse-audit"
EXPECTED_REGISTERED = 122

MEDIA_HINTS = (
    "cable", "wire", "line", "conduit", "tube", "fiber", "dust", "data_bus", "data_pair",
    "pneumatic_pipe", "instrument_cable", "shielded_instrument_cable", "phonon_conduit",
)

FUNCTION_STRONG = (
    "RuntimeIntStore", "RuntimeLongStore", "scheduleTick(", "BlockEntity", "PneumaticNetwork",
    "OpticalNetwork", "DataBusNetwork", "SerialNetwork", "DifferentialNetwork", "ThermalPulseKernel",
    "VibrationNetwork", "HydroacousticNetwork", "SoulFluxNetwork", "PidTelemetryStore",
)
FUNCTION_LIGHT = ("neighborChanged(", "recompute(", "recomputeAround(", "tick(", "setBlock(")

FAILURE_STRONG = (
    "PortQuality.STALE", "PortQuality.SATURATED", "PortQuality.FAULT", "PortQuality.TOPOLOGY_ERROR",
    "PortQuality.DOMAIN_MISMATCH", "stall", "timeout", "trip", "latched", "degraded", "saturation",
    "framing", "errorCount", "ventEvents", "fault", "inhibit", "recovery",
)
FAILURE_LIGHT = ("PortQuality", "NO_SIGNAL", "warning", "invalid", "quality", "disconnect")

DIAGNOSTIC_STRONG = (
    "MeasurementSnapshot", "Telemetry", "telemetry", "history", "History", "diagnosticsSnapshot",
    "compactDiagnostics", "Commissioning", "commissioning", "AcceptanceEvidence", "sampleCount",
    "sampleAge", "uncertainty", "trend", "event", "Event",
)
DIAGNOSTIC_LIGHT = ("engineeringSnapshot(", "EngineeringPortSnapshot", "PortQuality")

INTEGRATION_STRONG = (
    "PortKind.FEEDBACK", "PortKind.SAFETY", "PortKind.TRIGGER", "PortKind.RESET", "PortKind.CONTROL",
    "actuatorPathEvidence", "EngineeringTopology", "ClosedLoop", "Commissioning",
)

WORLD_VISUAL_HINTS = (
    "visualState(", "VisualState", "MechatronicsVisual", "RenderShape.ENTITYBLOCK_ANIMATED",
    "BooleanProperty", "IntegerProperty", "EnumProperty", "OPEN", "ACTIVE", "LIT", "POWERED",
    "OUTPUT", "TRIPPED", "LATCHED", "VENTING", "ARMED", "STEP", "LEVEL",
)

GOLDEN_SYSTEM_HINTS = {
    "control": ("pid", "pwm", "servo", "sequence", "sample_hold", "conditioner", "filter"),
    "pneumatic": ("pneumatic", "air_", "pressure", "cylinder"),
    "reliability": ("watchdog", "interlock", "fault", "fuse", "voter", "alarm", "operations"),
    "metrology": ("analyzer", "oscilloscope", "meter", "calibration", "sensor", "reference", "probe"),
    "communications": ("serial", "differential", "radio", "optical", "byte", "data_bus", "serializer"),
    "thermal": ("thermal", "temperature", "calorimeter"),
    "magnetic": ("magnet", "induction", "iron_core"),
}


def text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="ignore") if path.exists() else ""


def hits(source: str, needles: tuple[str, ...]) -> int:
    return sum(1 for needle in needles if needle in source)


def is_media(block_id: str, source: str) -> bool:
    if any(token in block_id for token in MEDIA_HINTS):
        return True
    return "PortDirection.BIDIRECTIONAL" in source and "RuntimeIntStore" not in source and "MeasurementSnapshot" not in source


def visual_score(block_id: str, source: str) -> tuple[int, str]:
    score = 0
    notes = []
    if hits(source, WORLD_VISUAL_HINTS) >= 2:
        score = 2
        notes.append("runtime/state visual projection")
    elif hits(source, WORLD_VISUAL_HINTS):
        score = 1
        notes.append("some world state projection")

    path = ASSETS / "blockstates" / f"{block_id}.json"
    if path.exists():
        raw = text(path)
        props = set(re.findall(r'"([a-z0-9_]+)=', raw))
        semantic = props - {"facing", "input_facing", "axis", "north", "south", "east", "west", "up", "down"}
        if semantic:
            score = max(score, 2)
            notes.append("semantic model variants")
        elif props or '"multipart"' in raw:
            score = max(score, 1)
            notes.append("orientation/connectivity variants")
    return score, "; ".join(notes) or "static world presentation"


def ui_score(class_name: str, source: str, dispatcher: str, menus: str) -> tuple[int, str]:
    reachable = "openMenu(" in source or "FieldDeviceUi.open(" in source or "FieldDeviceUi.openUniversal(" in source
    specialized = class_name in dispatcher or class_name in menus
    if reachable and specialized:
        return 2, "explicit specialized/shared HMI"
    if reachable:
        return 1, "fallback/universal HMI"
    if specialized:
        return 1, "UI route detected; reachability review"
    return 0, "no explicit HMI surface detected"


def function_score(source: str, media: bool) -> tuple[int, str]:
    strong = hits(source, FUNCTION_STRONG)
    light = hits(source, FUNCTION_LIGHT)
    if strong >= 2 or (strong and "scheduleTick(" in source):
        return 2, "timed/retained/network dynamics"
    if strong or light >= 2:
        return 1, "reactive or lightweight dynamics"
    if media:
        return 1, "passive transmission behavior"
    return 0, "mostly static/direct mapping"


def diagnostics_score(source: str) -> tuple[int, str]:
    strong = hits(source, DIAGNOSTIC_STRONG)
    light = hits(source, DIAGNOSTIC_LIGHT)
    if strong >= 2:
        return 2, "retained/measurement/commissioning evidence"
    if strong or light >= 2:
        return 1, "live snapshot/quality evidence"
    return 0, "little explicit diagnostic evidence"


def failure_score(source: str, media: bool) -> tuple[int, str]:
    strong = hits(source, FAILURE_STRONG)
    light = hits(source, FAILURE_LIGHT)
    if strong >= 2:
        return 2, "explicit abnormal/degraded/failure semantics"
    if strong or light >= 2:
        return 1, "basic quality or abnormal-state semantics"
    if media:
        return 1, "passive medium; failures mainly topology/quality driven"
    return 0, "no clear diagnosable failure behavior"


def integration_score(source: str, inheritance: list[str], media: bool) -> tuple[int, str]:
    port_count = len(re.findall(r"new\s+EngineeringPort\s*\(", source))
    strong = hits(source, INTEGRATION_STRONG)
    inherited_series = any(name in {"DirectionalSignalBlock", "DirectionalDomainBlock", "DirectionalRedstoneEndpointBlock"} for name in inheritance)
    if port_count >= 3 or strong >= 3:
        return 2, "multi-role/control/feedback system integration"
    if port_count >= 2 or inherited_series or media or "EngineeringPortProvider" in source:
        return 1, "formal system connectivity"
    return 0, "weak explicit cross-device role"


def system_tags(block_id: str) -> list[str]:
    result = []
    for name, tokens in GOLDEN_SYSTEM_HINTS.items():
        if any(token in block_id for token in tokens):
            result.append(name)
    return result or ["general"]


def main() -> int:
    main_source = text(MAIN)
    ids, codec_map = total.registration_data(main_source)
    if len(ids) != EXPECTED_REGISTERED:
        print(f"BLOCK DEEPENING AUDIT: FAIL — expected {EXPECTED_REGISTERED} registered blocks, found {len(ids)}")
        return 1

    dispatcher = text(UI_DIR / "FieldDeviceUi.java")
    menus = "\n".join(text(p) for p in sorted((UI_DIR / "menu").glob("*.java")))
    rows = []
    gaps = Counter()
    dimension_totals = Counter()

    for block_id in ids:
        class_name = codec_map.get(block_id, "")
        chain = total.inheritance_chain(class_name) if class_name else []
        inheritance = [name for name, _, _ in chain]
        own = chain[0][2] if chain else ""
        source = "\n".join(src for _, _, src in chain)
        support = total.direct_block_support_source(own) if own else ""
        source += "\n" + support
        media = is_media(block_id, source)

        values = {}
        notes = {}
        for key, result in {
            "function": function_score(source, media),
            "visual": visual_score(block_id, source),
            "diagnostics": diagnostics_score(source),
            "ui": ui_score(class_name, source, dispatcher, menus),
            "integration": integration_score(source, inheritance, media),
            "failure": failure_score(source, media),
        }.items():
            values[key], notes[key] = result
            dimension_totals[key] += values[key]

        labels = []
        if values["function"] == 0 and not media: labels.append("Function shallow")
        if values["visual"] == 0: labels.append("Visual shallow")
        if values["diagnostics"] == 0: labels.append("Diagnostic shallow")
        if values["ui"] == 0: labels.append("UI shallow")
        if values["integration"] == 0: labels.append("Integration shallow")
        if values["failure"] == 0 and not media: labels.append("Failure shallow")
        if not labels: labels.append("Already deep")
        for label in labels: gaps[label] += 1

        rows.append({
            "id": block_id,
            "class": class_name,
            "media": media,
            "scores": values,
            "notes": notes,
            "labels": labels,
            "system_tags": system_tags(block_id),
            "depth_total": sum(values.values()),
        })

    # Prioritize low-depth non-media devices first; passive media get their own later pass.
    priority = sorted(rows, key=lambda r: (r["media"], r["depth_total"], r["id"]))

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    json_path = OUT_DIR / "rse-block-deepening-audit.json"
    md_path = OUT_DIR / "rse-block-deepening-audit.md"
    json_path.write_text(json.dumps({
        "registered": len(rows),
        "gap_counts": dict(gaps),
        "dimension_totals": dict(dimension_totals),
        "blocks": rows,
    }, indent=2), encoding="utf-8")

    lines = [
        "# RSE Block Deepening Audit",
        "",
        "This is a planning report, not a release gate or quality grade.",
        "Scores are coarse 0/1/2 signals for function, visual, diagnostics, UI, integration and failure depth.",
        "Passive media are marked separately and are not penalized for lacking controller-style state machines.",
        "",
        "## Gap counts",
    ]
    for label, count in sorted(gaps.items()):
        lines.append(f"- **{label}:** {count}")
    lines += ["", "## First deepening candidates", ""]
    for row in [r for r in priority if not r["media"]][:24]:
        s = row["scores"]
        lines.append(
            f"- `{row['id']}` — depth {row['depth_total']}/12 — "
            f"F{s['function']} V{s['visual']} D{s['diagnostics']} U{s['ui']} I{s['integration']} X{s['failure']} — "
            + ", ".join(row["labels"]) + " — systems: " + ", ".join(row["system_tags"])
        )
    lines += ["", "## Passive / transmission media", ""]
    for row in [r for r in priority if r["media"]]:
        lines.append(f"- `{row['id']}` — depth {row['depth_total']}/12 — " + ", ".join(row["labels"]))
    md_path.write_text("\n".join(lines) + "\n", encoding="utf-8")

    print("RSE BLOCK DEEPENING AUDIT: PASS")
    print(f"  registered blocks: {len(rows)}")
    for label, count in sorted(gaps.items()):
        print(f"  {label}: {count}")
    print("  first non-media deepening candidates:")
    for row in [r for r in priority if not r["media"]][:12]:
        print(f"   - {row['id']}: {row['depth_total']}/12 | {'; '.join(row['labels'])}")
    print(f"  report: {md_path.relative_to(ROOT)}")
    print(f"  json:   {json_path.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
