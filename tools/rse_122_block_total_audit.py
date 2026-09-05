#!/usr/bin/env python3
"""RSE 122-block total audit.

This audit is intentionally different from the historical batch verifiers. It derives the
registered block set from RedstoneEngineering.java, maps each registered id to its Java class,
walks local inheritance and directly referenced block-support helpers, checks resources and
engineering contracts, measures direct GameTest/static-verifier evidence, and reconciles the
complete 15-batch audit ledger.

Hard failures are reserved for objective coverage/integrity regressions. Quality gaps are
reported and ranked so they can be hardened without turning heuristic scoring into a false
compile gate.
"""
from __future__ import annotations

from collections import Counter, defaultdict
from pathlib import Path
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "src/main/java/dev/redstoneengineering/RedstoneEngineering.java"
BLOCK_DIR = ROOT / "src/main/java/dev/redstoneengineering/block"
GAMETEST_DIR = ROOT / "src/main/java/dev/redstoneengineering/gametest"
TOOLS_DIR = ROOT / "tools"
ASSETS = ROOT / "src/main/resources/assets/redstoneengineering"
DATA = ROOT / "src/main/resources/data/redstoneengineering"
OUT_DIR = ROOT / ".rse-audit"
EXPECTED_REGISTERED = 122

# Historical deep-audit ledger. The only intentional duplicate is pid_controller, which was
# accepted in batch 1 and then re-audited more deeply in the CPS/mechatronics batch.
BATCHES: dict[int, tuple[str, ...]] = {
    1: (
        "signal_conditioner", "pid_controller", "signal_analyzer", "oscilloscope",
        "logic_analyzer", "calibration_module", "sample_hold", "pwm_controller",
    ),
    2: (
        "instrument_cable", "signal_probe", "redstone_signal_cable", "redstone_cable_junction",
        "redstone_cable_terminal", "redstone_reference_source", "analog_indicator", "precision_filter",
    ),
    3: (
        "lapis_temperature_transducer", "lapis_magnetic_transducer", "lapis_optical_transducer",
        "lapis_voltage_transducer", "lapis_precision_range_sensor", "lapis_to_redstone_quantizer",
        "redstone_to_lapis_scaler", "quartz_triggered_lapis_sampler",
    ),
    4: (
        "eight_bit_data_bus", "redstone_byte_encoder", "byte_to_redstone_decoder", "serial_data_line",
        "serializer", "deserializer", "differential_data_pair", "digital_regenerator",
    ),
    5: (
        "differential_driver", "differential_receiver", "radio_transmitter", "radio_receiver",
        "free_space_optical_transmitter", "free_space_optical_receiver", "quartz_clock_divider",
        "quartz_stability_monitor",
    ),
    6: (
        "amethyst_resonator", "amethyst_resonance_dust", "amethyst_frequency_filter",
        "amethyst_tuned_resonator", "amethyst_spectrum_analyzer", "mechanical_exciter",
        "slime_vibration_conduit", "mechanical_vibration_receiver",
    ),
    7: (
        "honey_vibration_damper", "sculk_vibration_interface", "hydroacoustic_tube",
        "hydroacoustic_exciter", "hydroacoustic_receiver", "phonon_conduit",
        "thermal_pulse_encoder", "thermal_pulse_receiver",
    ),
    8: (
        "pid_controller", "watchdog", "shielded_instrument_cable", "servo_actuator",
        "servo_position_sensor", "redundant_voter", "fault_latch", "operations_monitor",
    ),
    9: (
        "air_compressor", "pneumatic_pipe", "air_reservoir", "pressure_regulator",
        "pneumatic_receiver", "pneumatic_valve", "pneumatic_check_valve", "pneumatic_flow_meter",
    ),
    10: (
        "edge_detector", "pulse_shaper", "signal_tap", "range_sensor", "lapis_signal_line",
        "lapis_precision_source", "quartz_timing_line", "quartz_oscillator",
    ),
    11: (
        "pneumatic_proportional_valve", "pneumatic_relief_valve", "pneumatic_cylinder",
        "electromagnet", "permanent_magnet", "induction_coil", "magnetic_field_sensor",
        "magnetic_gradient_meter",
    ),
    12: (
        "optical_fiber", "optical_emitter", "optical_receiver", "optical_power_meter",
        "optical_splitter", "optical_channel_filter", "optical_attenuator", "optical_fiber_junction",
    ),
    13: (
        "copper_wire", "copper_cable_junction", "copper_voltage_source", "copper_resistive_load",
        "copper_series_resistor", "copper_capacitor", "copper_fuse", "copper_circuit_meter",
        "thermal_heater", "thermal_mass",
    ),
    14: (
        "thermal_radiator", "thermal_calorimeter", "temperature_sensor", "iron_core",
        "lapis_noise_source", "lapis_low_pass_filter", "lapis_precision_meter",
        "quartz_lab_oscillator", "quartz_phase_delay", "engineering_light_sensor",
    ),
    15: (
        "tank_level_sensor", "entity_density_sensor", "soul_soil_conduit", "soul_sand_reservoir",
        "soul_flux_injector", "soul_flux_meter", "molecular_cloud_receiver",
    ),
}

SUSPICIOUS_RUNTIME_PROPERTIES = {
    "phase", "held", "triggered", "remaining", "last", "buffer", "queue", "packet",
    "history", "raw", "filtered", "peak", "sample_count", "runtime", "stored_flux",
}

# These devices call into a state-owning network but do not own runtime state at their own
# position. Keeping this explicit prevents "uses a network" from being confused with "must clear
# local network state". The injector only writes adjacent Soul nodes through SoulFluxNetwork.inject.
STATELESS_NETWORK_CLIENTS = {"SoulFluxInjectorBlock"}


def text(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        return path.read_text(errors="ignore")


def inheritance_chain(class_name: str) -> list[tuple[str, Path, str]]:
    chain: list[tuple[str, Path, str]] = []
    seen: set[str] = set()
    current = class_name
    for _ in range(12):
        if not current or current in seen:
            break
        seen.add(current)
        path = BLOCK_DIR / f"{current}.java"
        if not path.exists():
            break
        source = text(path)
        chain.append((current, path, source))
        match = re.search(r"\bclass\s+" + re.escape(current) + r"\b[^\{]*?\bextends\s+([A-Za-z0-9_]+)", source, re.S)
        current = match.group(1) if match else ""
    return chain


def direct_block_support_source(source: str) -> str:
    """Include directly referenced local *Support helpers in contract detection.

    Some block families deliberately centralize port descriptors in a package-private helper
    (for example OpticalPortSupport). That helper is part of the block's static engineering
    contract even though it is not in the Java inheritance chain.
    """
    chunks: list[str] = []
    names = sorted(set(re.findall(r"\b([A-Z][A-Za-z0-9_]*(?:PortSupport|Support))\.", source)))
    for name in names:
        path = BLOCK_DIR / f"{name}.java"
        if path.is_file():
            chunks.append(text(path))
    return "\n".join(chunks)


def registration_data(main: str) -> tuple[list[str], dict[str, str]]:
    ids = sorted(set(re.findall(r"registerBlock\(\s*\"([a-z0-9_]+)\"", main, re.S)))
    codec_map = {
        block_id: class_name
        for block_id, class_name in re.findall(
            r"codec\(\s*\"([a-z0-9_]+)\"\s*,\s*([A-Za-z0-9_]+)::new\s*\)", main, re.S
        )
    }
    # Registration itself is the authoritative fallback if a codec declaration changes form.
    for block_id, class_name in re.findall(
        r"registerBlock\(\s*\"([a-z0-9_]+)\"\s*,\s*([A-Za-z0-9_]+)::new", main, re.S
    ):
        codec_map.setdefault(block_id, class_name)
    return ids, codec_map


def resource_state(block_id: str) -> tuple[bool, list[str]]:
    required = [
        ASSETS / "blockstates" / f"{block_id}.json",
        ASSETS / "models/item" / f"{block_id}.json",
        DATA / "loot_table/blocks" / f"{block_id}.json",
        DATA / "recipe" / f"{block_id}.json",
    ]
    missing = [str(p.relative_to(ROOT)) for p in required if not p.exists()]
    return not missing, missing


def collect_evidence() -> tuple[str, str, int, int]:
    game_files = sorted(GAMETEST_DIR.glob("*.java"))
    verifier_files = sorted(TOOLS_DIR.glob("rse_*verify.py"))
    game_blob = "\n".join(text(p) for p in game_files)
    verifier_blob = "\n".join(text(p) for p in verifier_files)
    return game_blob, verifier_blob, len(game_files), len(verifier_files)


def suspicious_properties(source: str) -> list[str]:
    names: list[str] = []
    for name in re.findall(r"(?:IntegerProperty|BooleanProperty|EnumProperty|DirectionProperty)\.create\(\s*\"([a-z0-9_]+)\"", source):
        if name in SUSPICIOUS_RUNTIME_PROPERTIES:
            names.append(name)
    return sorted(set(names))


def audit_block(block_id: str, class_name: str | None, batches: list[int], game_blob: str, verifier_blob: str) -> dict:
    result: dict = {
        "id": block_id,
        "class": class_name,
        "batches": batches,
        "score": 100,
        "status": "ULTRA",
        "findings": [],
    }
    score = 100
    findings: list[str] = result["findings"]

    resources_ok, missing_resources = resource_state(block_id)
    result["resources_ok"] = resources_ok
    result["missing_resources"] = missing_resources
    if not resources_ok:
        score -= 30
        findings.append("missing required resources: " + ", ".join(missing_resources))

    if not class_name:
        result.update({
            "class_found": False,
            "ports": False,
            "domain": False,
            "snapshot": False,
            "gametest": False,
            "verifier": False,
            "stateful": False,
            "cleanup": False,
            "redstone_contract": False,
            "suspicious_state_properties": [],
        })
        score = 0
        findings.append("unable to resolve registered block to Java class")
        result["score"] = score
        result["status"] = "CRITICAL"
        return result

    chain = inheritance_chain(class_name)
    result["inheritance"] = [name for name, _, _ in chain]
    class_found = bool(chain)
    result["class_found"] = class_found
    if not class_found:
        score = 0
        findings.append(f"missing Java source for {class_name}")
        result["score"] = score
        result["status"] = "CRITICAL"
        return result

    source = "\n".join(src for _, _, src in chain)
    own_source = chain[0][2]
    support_source = direct_block_support_source(own_source)
    contract_source = source + ("\n" + support_source if support_source else "")
    result["support_helpers"] = sorted(set(re.findall(
        r"\b([A-Z][A-Za-z0-9_]*(?:PortSupport|Support))\.", own_source
    )))

    ports = "EngineeringPortProvider" in contract_source or "engineeringPorts(" in contract_source
    domain = "EngineeringDomain." in contract_source
    snapshot = "engineeringSnapshot(" in contract_source or "MeasurementSnapshot" in contract_source
    result["ports"] = ports
    result["domain"] = domain
    result["snapshot"] = snapshot
    if not ports:
        score -= 22
        findings.append("no explicit/inherited EngineeringPortProvider contract detected")
    if not domain:
        score -= 15
        findings.append("no explicit engineering domain detected in inheritance/support contract")
    if not snapshot:
        score -= 7
        findings.append("no engineering snapshot/metrology surface detected")

    const = block_id.upper()
    gametest = (f"RedstoneEngineering.{const}" in game_blob) or (class_name in game_blob)
    verifier = (
        f"{class_name}.java" in verifier_blob
        or block_id in verifier_blob
        or const in verifier_blob
    )
    result["gametest"] = gametest
    result["verifier"] = verifier
    if not gametest:
        score -= 14
        findings.append("no direct GameTest reference detected")
    if not verifier:
        score -= 9
        findings.append("no direct static-verifier reference detected")

    stateful_tokens = (
        "RuntimeIntStore", "RuntimeLongStore", "DomainDriverRegistry", "SoulFluxNetwork",
        "PneumaticNetwork", "OpticalNetwork", "DataBusNetwork", "SerialDataNetwork",
    )
    stateful = any(token in source for token in stateful_tokens)
    if class_name in STATELESS_NETWORK_CLIENTS:
        local_owner_tokens = ("RuntimeIntStore", "RuntimeLongStore", "DomainDriverRegistry")
        stateful = any(token in own_source for token in local_owner_tokens)
        result["state_ownership_note"] = "network client only; no local runtime/network state ownership"
    cleanup_tokens = (
        "onRemove(", "RuntimeIntStore.remove", "RuntimeLongStore.remove", ".clear(",
        ".release(", "recomputeAround", "releaseDriver", "removeDriver",
    )
    cleanup = any(token in source for token in cleanup_tokens)
    result["stateful"] = stateful
    result["cleanup"] = cleanup
    if stateful and not cleanup:
        score -= 18
        findings.append("runtime/network state detected without an obvious lifecycle cleanup path")

    redstoneish = (
        "redstone" in block_id
        or any(token in source for token in ("getSignal(", "isSignalSource(", "getDirectSignal(", "canConnectRedstone("))
    )
    redstone_contract = (not redstoneish) or any(
        token in source for token in ("canConnectRedstone(", "DirectionalSignalBlock", "DirectionalRedstoneEndpointBlock")
    )
    result["redstone_contract"] = redstone_contract
    if redstoneish and not redstone_contract:
        score -= 7
        findings.append("redstone interaction exists without an obvious physical-side connection contract")

    props = suspicious_properties(own_source)
    result["suspicious_state_properties"] = props
    if props:
        score -= 5
        findings.append("review possible transient BlockState properties: " + ", ".join(props))

    if not batches:
        score -= 30
        findings.append("not present in historical deep-audit ledger")

    result["score"] = max(0, score)
    if not resources_ok or not class_found or not batches:
        status = "CRITICAL"
    elif score >= 90 and ports and domain and gametest and verifier:
        status = "ULTRA"
    elif score >= 80:
        status = "DEEP"
    elif score >= 70:
        status = "SOLID"
    else:
        status = "REVIEW"
    result["status"] = status
    return result


def main() -> int:
    hard_errors: list[str] = []
    main_source = text(MAIN)
    registered, class_map = registration_data(main_source)

    ledger: defaultdict[str, list[int]] = defaultdict(list)
    for batch, ids in BATCHES.items():
        for block_id in ids:
            ledger[block_id].append(batch)
    ledger_ids = sorted(ledger)

    if len(registered) != EXPECTED_REGISTERED:
        hard_errors.append(f"expected {EXPECTED_REGISTERED} registered blocks, found {len(registered)}")
    if len(ledger_ids) != EXPECTED_REGISTERED:
        hard_errors.append(f"expected {EXPECTED_REGISTERED} unique ledger blocks, found {len(ledger_ids)}")

    registered_set = set(registered)
    ledger_set = set(ledger_ids)
    unbatched = sorted(registered_set - ledger_set)
    stale_ledger = sorted(ledger_set - registered_set)
    if unbatched:
        hard_errors.append("registered blocks absent from audit ledger: " + ", ".join(unbatched))
    if stale_ledger:
        hard_errors.append("audit ledger entries not registered: " + ", ".join(stale_ledger))

    duplicates = {block_id: batches for block_id, batches in ledger.items() if len(batches) > 1}
    unexpected_duplicates = {k: v for k, v in duplicates.items() if k != "pid_controller"}
    if unexpected_duplicates:
        hard_errors.append("unexpected repeated audit-ledger entries: " + json.dumps(unexpected_duplicates, sort_keys=True))
    if duplicates.get("pid_controller") != [1, 8]:
        hard_errors.append(f"pid_controller expected in batches [1, 8], got {duplicates.get('pid_controller')}")

    game_blob, verifier_blob, game_file_count, verifier_file_count = collect_evidence()
    results = [
        audit_block(block_id, class_map.get(block_id), ledger.get(block_id, []), game_blob, verifier_blob)
        for block_id in registered
    ]

    for row in results:
        if not row["class_found"]:
            hard_errors.append(f"{row['id']}: Java class missing/unresolved")
        if not row["resources_ok"]:
            hard_errors.append(f"{row['id']}: required block resources missing")

    status_counts = Counter(row["status"] for row in results)
    direct_gametest = sum(bool(row["gametest"]) for row in results)
    direct_verifier = sum(bool(row["verifier"]) for row in results)
    ports_count = sum(bool(row["ports"]) for row in results)
    domain_count = sum(bool(row["domain"]) for row in results)
    snapshot_count = sum(bool(row["snapshot"]) for row in results)
    stateful_count = sum(bool(row["stateful"]) for row in results)
    stateful_cleanup_count = sum(bool(row["stateful"] and row["cleanup"]) for row in results)
    suspicious_count = sum(bool(row["suspicious_state_properties"]) for row in results)

    report = {
        "registered_blocks": len(registered),
        "ledger_unique_blocks": len(ledger_ids),
        "ledger_slots": sum(len(v) for v in BATCHES.values()),
        "intentional_duplicate": {"pid_controller": duplicates.get("pid_controller", [])},
        "unbatched": unbatched,
        "stale_ledger": stale_ledger,
        "gametest_files": game_file_count,
        "verifier_files": verifier_file_count,
        "direct_gametest_blocks": direct_gametest,
        "direct_verifier_blocks": direct_verifier,
        "port_contract_blocks": ports_count,
        "domain_contract_blocks": domain_count,
        "snapshot_blocks": snapshot_count,
        "stateful_blocks": stateful_count,
        "stateful_with_cleanup": stateful_cleanup_count,
        "suspicious_blockstate_blocks": suspicious_count,
        "status_counts": dict(status_counts),
        "hard_errors": hard_errors,
        "blocks": results,
    }

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    json_path = OUT_DIR / "rse-122-block-audit.json"
    md_path = OUT_DIR / "rse-122-block-audit.md"
    json_path.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    md: list[str] = []
    md.append("# RSE 122-Block Total Audit")
    md.append("")
    md.append("## Coverage summary")
    md.append("")
    md.append(f"- Registered blocks: **{len(registered)} / {EXPECTED_REGISTERED}**")
    md.append(f"- Historical deep-audit ledger: **{len(ledger_ids)} unique blocks / {sum(len(v) for v in BATCHES.values())} batch slots**")
    md.append(f"- Intentional repeated audit: **pid_controller -> batches {duplicates.get('pid_controller', [])}**")
    md.append(f"- Direct GameTest evidence: **{direct_gametest}/{len(results)}**")
    md.append(f"- Direct static-verifier evidence: **{direct_verifier}/{len(results)}**")
    md.append(f"- Engineering port contract detected: **{ports_count}/{len(results)}**")
    md.append(f"- Explicit engineering domain detected: **{domain_count}/{len(results)}**")
    md.append(f"- Snapshot/metrology surface detected: **{snapshot_count}/{len(results)}**")
    md.append(f"- Stateful blocks with cleanup evidence: **{stateful_cleanup_count}/{stateful_count}**")
    md.append(f"- Possible transient BlockState review flags: **{suspicious_count} blocks**")
    md.append(f"- Statuses: **{dict(status_counts)}**")
    md.append("")
    md.append("Scoring is a triage aid, not a substitute for compilation or GameTests. Missing registration/resources/ledger coverage are hard failures; lower scores identify where the next hardening pass should start.")
    md.append("")
    md.append("## Per-block matrix")
    md.append("")
    md.append("| Block | Class | Batch | Score | Status | Ports | Domain | Snapshot | GameTest | Verifier | Runtime cleanup | Findings |")
    md.append("|---|---|---:|---:|---|:---:|:---:|:---:|:---:|:---:|:---:|---|")
    for row in sorted(results, key=lambda r: (r["score"], r["id"])):
        batches = ",".join(str(x) for x in row["batches"]) or "-"
        cleanup = "n/a" if not row["stateful"] else ("yes" if row["cleanup"] else "NO")
        findings = "; ".join(row["findings"]) if row["findings"] else "none"
        md.append(
            f"| `{row['id']}` | `{row['class'] or '?'}` | {batches} | {row['score']} | **{row['status']}** | "
            f"{'yes' if row['ports'] else 'NO'} | {'yes' if row['domain'] else 'NO'} | "
            f"{'yes' if row['snapshot'] else 'NO'} | {'yes' if row['gametest'] else 'NO'} | "
            f"{'yes' if row['verifier'] else 'NO'} | {cleanup} | {findings} |"
        )
    if hard_errors:
        md.append("")
        md.append("## Hard failures")
        md.append("")
        for error in hard_errors:
            md.append(f"- {error}")
    md_path.write_text("\n".join(md) + "\n", encoding="utf-8")

    print("RSE 122-BLOCK TOTAL AUDIT")
    print(f"  registered: {len(registered)}/{EXPECTED_REGISTERED}")
    print(f"  ledger: {len(ledger_ids)} unique / {sum(len(v) for v in BATCHES.values())} slots")
    print(f"  duplicate: pid_controller -> {duplicates.get('pid_controller', [])}")
    print(f"  GameTest evidence: {direct_gametest}/{len(results)}")
    print(f"  verifier evidence: {direct_verifier}/{len(results)}")
    print(f"  port contracts: {ports_count}/{len(results)}")
    print(f"  domains: {domain_count}/{len(results)}")
    print(f"  snapshots: {snapshot_count}/{len(results)}")
    print(f"  stateful cleanup: {stateful_cleanup_count}/{stateful_count}")
    print(f"  statuses: {dict(status_counts)}")
    print("  lowest-scoring blocks:")
    for row in sorted(results, key=lambda r: (r["score"], r["id"]))[:20]:
        print(f"    {row['score']:3d} {row['status']:<8} {row['id']}: " + ("; ".join(row["findings"]) or "none"))
    print(f"  report: {md_path.relative_to(ROOT)}")
    print(f"  json:   {json_path.relative_to(ROOT)}")

    if hard_errors:
        print(f"  FAIL: {len(hard_errors)} hard issue(s)")
        for error in hard_errors:
            print("   -", error)
        return 1
    print("  PASS: all 122 registered blocks are reconciled to the unique audit ledger and resource/class integrity gates")
    return 0


if __name__ == "__main__":
    sys.exit(main())