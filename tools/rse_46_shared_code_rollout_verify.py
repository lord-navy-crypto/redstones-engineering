#!/usr/bin/env python3
"""Source-level 46-candidate shared-implementation matrix and contract gate.

A shared helper is not evidence of physics equivalence or runtime acceptance.
Do not merge registry IDs, media, topology, state or world-save contracts.
"""
from pathlib import Path
import csv
import json
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
out = root / ".rse-audit"
data = json.loads((out / "rse-130-block-slimming-inventory.json").read_text(encoding="utf-8"))
blocks = data["blocks"]
groups = data["candidate_groups"]
candidates = [item for item in blocks if item["review_group"] != "none"]
errors = []
if len(blocks) != 130 or len(candidates) != 46 or len(groups) != 15:
    errors.append(f"registry/groups drift: {len(blocks)} blocks, {len(candidates)} candidates, {len(groups)} groups")

src = root / "src/main/java/dev/redstoneengineering/block"
reads = {}
for filename in ("OpticalSeriesSupport", "PneumaticValveSupport", "ExciterDriveSupport", "QuartzClockSupport", "RangeScanSupport", "FilterResponseSupport", "SafetyObservationSupport", "CommunicationLineEvidenceSupport"):
    path = src / (filename + ".java")
    reads[filename] = path.read_text(encoding="utf-8") if path.is_file() else ""
    if not reads[filename]:
        errors.append(f"shared helper missing: {filename}")
forbidden_in_helper = (
    "setBlock(", "setValue(", "RuntimeIntStore.get(", "getBlockEntity(",
)
for file, source in reads.items():
    for token in forbidden_in_helper:
        if token in source:
            errors.append(f"unexpected world mutation or state injection {file}: {token}")
if "DomainNetwork.driveOptical(level, pos.relative(output), pos, 0, 0, false)" not in reads["OpticalSeriesSupport"]:
    errors.append("optical old-route invalidation lost")
if "PneumaticNetwork.recomputeAround(server, pos)" not in reads["PneumaticValveSupport"]:
    errors.append("pneumatic topology recompute lost")
if "PortQuality.NO_SIGNAL" not in reads["ExciterDriveSupport"]:
    errors.append("exciter zero-amplitude quality semantics lost")
if "observation.valid() && observation.value() > 0" not in reads["SafetyObservationSupport"]:
    errors.append("safety permissive must require valid asserted input, never unknown/absent signal")
if "quality == PortQuality.VALID" not in reads["CommunicationLineEvidenceSupport"]:
    errors.append("communication payload display must reject invalid/conflicted data")

families = {
    "failure_diagnostics": ("SafetyObservationSupport", {
        "watchdog": ("WatchdogBlock", "timeoutTicks(s.getValue(TIMEOUT))"),
        "fault_latch": ("FaultLatchBlock", "manualReset(Level level, BlockPos pos)"),
        "alarm_processor": ("AlarmProcessorBlock", "SystemEventTimeline.record("),
        "safety_interlock": ("SafetyInterlockBlock", "SafetyObservationSupport.failedMask(a, b, c)"),
    }),
    "digital_transport": ("CommunicationLineEvidenceSupport", {
        "serial_data_line": ("SerialDataLineBlock", "SerialNetwork.recompute(level, pos)"),
        "differential_data_pair": ("DifferentialDataPairBlock", "DifferentialNetwork.recompute(level, pos)"),
    }),
    "filtering": ("FilterResponseSupport", {
        "precision_filter": ("PrecisionFilterBlock", "stepRate(Level level"),
        "lapis_low_pass_filter": ("LapisLowPassFilterBlock", "DomainNetwork.driveLapis("),
    }),
    "range_measurement": ("RangeScanSupport", {
        "range_sensor": ("RangeSensorBlock", "getEntitiesOfClass("),
        "lapis_precision_range_sensor": ("LapisPrecisionRangeSensorBlock", "RangeSample rangeSample("),
    }),
    "quartz_clock": ("QuartzClockSupport", {
        "quartz_oscillator": ("QuartzOscillatorBlock", "DomainNetwork.recomputeQuartz(l,p)"),
        "quartz_lab_oscillator": ("QuartzLabOscillatorBlock", "timingEvidence(Level level"),
    }),
    "optical_series": ("OpticalSeriesSupport", {
        "optical_attenuator": ("OpticalAttenuatorBlock", "EngineeringMath.opticalAfterLoss("),
        "optical_channel_filter": ("OpticalChannelFilterBlock", "input.channel() == target"),
    }),
    "pneumatic_valves": ("PneumaticValveSupport", {
        "pneumatic_valve": ("PneumaticValveBlock", "PortDirection.BIDIRECTIONAL"),
        "pneumatic_check_valve": ("PneumaticCheckValveBlock", "PortDirection.INPUT"),
        "pneumatic_proportional_valve": ("PneumaticProportionalValveBlock", "MechatronicsVisualBlockEntity.push("),
        "pneumatic_relief_valve": ("PneumaticReliefValveBlock", "recordVent("),
        "pressure_regulator": ("PressureRegulatorBlock", "PneumaticNetwork.recompute(server, pos)"),
    }),
    "vibration_excitation": ("ExciterDriveSupport", {
        "mechanical_exciter": ("MechanicalExciterBlock", "VibrationNetwork.propagate("),
        "hydroacoustic_exciter": ("HydroacousticExciterBlock", "HydroacousticNetwork.propagate("),
    }),
}
wired = {}
for family, (helper, mapping) in families.items():
    for block_id, (klass, distinct) in mapping.items():
        file = src / (klass + ".java")
        if not file.is_file():
            errors.append(f"{block_id}: class missing")
            continue
        source = file.read_text(encoding="utf-8")
        if helper + "." not in source:
            errors.append(f"{block_id}: no reuse of {helper}")
        if distinct not in source:
            errors.append(f"{block_id}: distinct physics/operation missing: {distinct}")
        if "engineeringSnapshot(" not in source and not (
                (block_id == "lapis_precision_range_sensor"
                 and "extends AbstractLapisTransducerBlock" in source
                 and "sense(ServerLevel" in source)
                or (block_id == "precision_filter"
                    and "extends DirectionalSignalBlock" in source
                    and "readBackInput(level, pos, state)" in source)):
            errors.append(f"{block_id}: formal port evidence missing")
        wired[block_id] = helper
if len(wired) != 21:
    errors.append(f"expected 21 helper users after safety/communication upgrade, got {len(wired)}")
if not set(wired).issubset({r["id"] for r in candidates}):
    errors.append("an implemented refactor is not a registered 46-candidate")

def recommendation(family):
    return {
        "quartz_clock": "Compare clock transitions and jitter metrics; retain lab-specific drift data.",
        "filtering": "Extract only identical pure transfer equations; keep domain and precision distinct.",
        "range_measurement": "Compare scan geometry/edge cases before sharing scanner logic.",
        "sampling_hold": "Review edge detection and stale-sample handling for safe reuse.",
        "amethyst_frequency": "Share frequency/quality calculations; never collapse source/filter/resonator.",
        "metering": "Reuse measurement-quality presentation; do not unify sensor calibration laws.",
        "optical_transport": "Share scoped optical quality helpers; preserve fiber vs line-of-sight.",
        "medium_junctions": "Reuse declared-face formatting, never auto-bridge domains.",
        "failure_diagnostics": "Share event record snapshots but preserve fail-safe triggers.",
        "digital_transport": "Reuse encode/decode bounds; keep driver, receiver and cable separate.",
        "instrument_cables": "Share topology scan and preserve shielding exposure semantics.",
        "optical_sensor_conversion": "Only share common observation; preserve quantization and signal medium.",
    }.get(family, "Candidate reviewed; no proved functional equivalence. Retain block identity.")

enhanced = {
    "sample_hold": ("SampleHoldBlock", ("RedstoneObservationSupport.observe", "DOWN=inspect", "outputQuality(level, pos)")),
    "quartz_triggered_lapis_sampler": ("QuartzTriggeredLapisSamplerBlock",
        ("WAITING_FOR_VALID_CLOCK", "PrecisionObservationSupport.quartz", "PrecisionObservationSupport.lapis")),
    "signal_conditioner": ("SignalConditionerBlock",
        ("CONDITIONER FIELD TEST", "RedstoneObservationSupport.observe", "limitingActive")),
}
for block_id, (klass, markers) in enhanced.items():
    source = (src / (klass + ".java")).read_text(encoding="utf-8")
    if any(marker not in source for marker in markers):
        errors.append(f"{block_id}: source-aware sampling improvement missing")
if "phasePort(port, state.getValue(ACTIVE))" not in (src / "QuartzLabOscillatorBlock.java").read_text():
    errors.append("lab oscillator may still emit a period value as the clock port output")
if "new EngineeringPortSnapshot(port, active ? 1.0 : 0.0, 0.0, 1.0, PortQuality.VALID)" not in reads["QuartzClockSupport"]:
    errors.append("shared clock must publish 0/1 phase with VALID source evidence")
hardened = {
    "range_sensor": "CONFIG_CHANGE_REVOKES_SCAN_AND_OLD_REDSTONE_OUTPUT",
    "precision_filter": "INPUT_PORT_REPORTS_DRIVEN_ZERO_VS_NO_SIGNAL",
    "signal_analyzer": "HMI_SNAPSHOT_OBSERVER_NEUTRAL_MISSING_CHUNK_STALE",
    "copper_circuit_meter": "NO_UNLOADED_COPPER_READ_OR_STALE_VOLTAGE",
    "optical_power_meter": "NO_UNLOADED_OPTICAL_SAMPLE",
}
for block_id, evidence in hardened.items():
    source_path = src / (next(item["class"] for item in candidates if item["id"] == block_id) + ".java")
    source_text = source_path.read_text(encoding="utf-8")
    marker = {
        "range_sensor": "applyConfiguration(level, pos, next)",
        "precision_filter": "RedstoneObservationSupport.observe(level, pos, side)",
        "signal_analyzer": "RuntimeIntStore.peek(level, KEY, pos)",
        "copper_circuit_meter": "return new CopperObservationSupport.Observation(0, PortQuality.STALE);",
        "optical_power_meter": "return new Measurement(0, 0, PortQuality.STALE);",
    }[block_id]
    if marker not in source_text:
        errors.append(f"{block_id}: hardening regression {evidence}")
rows = []
for r in candidates:
    family = r["review_group"]
    is_done = r["id"] in wired
    has_new_diagnostics = r["id"] in enhanced
    has_hardening = r["id"] in hardened
    rows.append({
        "registry_id": r["id"], "source_class": r["class"], "family": family,
        "public_block_id": "PRESERVE", "physics_transfer": "PRESERVE",
        "port_domain_and_direction": "PRESERVE", "old_world_save": "PRESERVE",
        "review": ("SHARED_HELPER_WIRED_SOURCE_CHECKED" if is_done
                   else "FUNCTIONAL_EVIDENCE_ENHANCED" if has_new_diagnostics
                   else "MEASUREMENT_SAFETY_HARDENED" if has_hardening
                   else "NOT_REFACTORED"),
        "shared_helper": wired.get(r["id"], ""),
        "latest_hardening": hardened.get(r["id"], ""),
        "next_action": ("Run runtime GameTest, old-save and multiplayer checks."
                        if (is_done or has_new_diagnostics or has_hardening) else recommendation(family)),
        "runtime_acceptance": "NOT_TESTED",
        "behavioral_equivalence": "NOT_PROVEN",
        "save_migration": "NOT_REQUIRED_NO_ID_CHANGE",
    })
rows.sort(key=lambda x: (x["family"], x["registry_id"]))
with (out / "rse-46-shared-code-rollout.csv").open("w", newline="", encoding="utf-8") as f:
    writer = csv.DictWriter(f, fieldnames=list(rows[0]))
    writer.writeheader()
    writer.writerows(rows)
summary = {
    "review_candidates": len(rows),
    "groups": len(groups),
    "shared_helper_wired": len(wired),
    "other_functional_enhancements": len(enhanced),
    "additional_robustness_hardening": len(hardened),
    "queued": len(rows)-len(set(wired) | set(enhanced) | set(hardened)),
    "registry_changed": False,
    "runtime_acceptance": False,
    "errors": errors,
}
(out / "rse-46-shared-code-rollout.json").write_text(json.dumps(
    {"summary": summary, "blocks": rows}, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")
if errors:
    print("RSE 46 SHARED CODE ROLLOUT: FAIL")
    for err in errors:
        print(" -", err)
    sys.exit(1)
print("RSE 46 SHARED CODE ROLLOUT: PASS")
print(f" {len(rows)} candidates / {len(groups)} families; {len(wired)} shared-helper users, "
      f"{len(enhanced)} quality/diagnostics upgrades, {len(hardened)} robust-safety flags (some overlap), "
      f"{len(rows)-len(set(wired) | set(enhanced) | set(hardened))} queued")
print(" no deleted IDs, no physics law merges; runtime equivalence not yet verified")
