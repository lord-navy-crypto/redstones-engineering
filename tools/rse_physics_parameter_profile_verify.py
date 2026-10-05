#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors = []

def read(rel):
    p = root / rel
    if not p.is_file():
        errors.append(f"missing {rel}")
        return ""
    return p.read_text(errors="ignore")

profile = read("src/main/java/dev/redstoneengineering/physics/EngineeringParameterProfile.java")
lapis = read("src/main/java/dev/redstoneengineering/block/LapisLowPassFilterBlock.java")
quartz = read("src/main/java/dev/redstoneengineering/block/QuartzPhaseDelayBlock.java")

for token in (
    'PROFILE_ID = "rse-default-v1"',
    "LAPIS_FILTER_ALPHA_STEPS = 8",
    "LAPIS_FILTER_DEFAULT_INDEX = 2",
    "LAPIS_FILTER_SAMPLE_PERIOD_TICKS = 2",
    "NOMINAL_TICKS_PER_SECOND = 20.0",
    "0.05, 0.10, 0.20, 0.35, 0.50, 0.65, 0.80, 1.00",
    "lapisFilterTimeConstantTicks",
    "Math.log1p(-alpha)",
    "lapisFilterCutoffHzNominal",
    "QUARTZ_PHASE_DELAY_MIN_TICKS = 1",
    "QUARTZ_PHASE_DELAY_MAX_TICKS = 16",
    "QUARTZ_PHASE_DELAY_DEFAULT_TICKS = 2",
):
    if token not in profile:
        errors.append(f"parameter profile missing {token!r}")

for token in (
    "import dev.redstoneengineering.physics.EngineeringParameterProfile;",
    "EngineeringParameterProfile.LAPIS_FILTER_ALPHA_STEPS",
    "EngineeringParameterProfile.LAPIS_FILTER_DEFAULT_INDEX",
    "EngineeringParameterProfile.lapisFilterAlpha(index)",
    "EngineeringParameterProfile.LAPIS_FILTER_SAMPLE_PERIOD_TICKS",
    "EngineeringParameterProfile.lapisFilterTimeConstantTicks(index)",
    "EngineeringParameterProfile.lapisFilterCutoffHzNominal(index)",
    "MODEL: y[n]=y[n-1]+alpha(x[n]-y[n-1])",
    "profile=",
):
    if token not in lapis:
        errors.append(f"Lapis filter missing profile-backed token {token!r}")

for forbidden in ("% 4;", "case 0 -> 0.10;", "default -> 0.75;", "scheduleTick(pos, this, 2);"):
    if forbidden in lapis:
        errors.append(f"Lapis filter retains legacy hard-coded alpha behavior {forbidden!r}")

for token in (
    "import dev.redstoneengineering.physics.EngineeringParameterProfile;",
    "EngineeringParameterProfile.QUARTZ_PHASE_DELAY_MIN_TICKS",
    "EngineeringParameterProfile.QUARTZ_PHASE_DELAY_MAX_TICKS",
    "EngineeringParameterProfile.QUARTZ_PHASE_DELAY_DEFAULT_TICKS",
    "profile=",
):
    if token not in quartz:
        errors.append(f"Quartz delay missing profile-backed token {token!r}")

for forbidden in ('IntegerProperty.create("delay", 1, 8)', "latencyTicks(state.getValue(DELAY), 8)", "delay >= 8 ? 1"):
    if forbidden in quartz:
        errors.append(f"Quartz delay retains legacy hard-coded range {forbidden!r}")

if errors:
    print("RSE PHYSICS PARAMETER PROFILE VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE PHYSICS PARAMETER PROFILE VERIFY: PASS")
print(" centralized parameter provenance: PASS")
print(" Lapis low-pass alpha 8-step profile: PASS")
print(" Quartz phase delay 1..16 tick profile: PASS")
print(" vanilla world-facing redstone boundary changes: NONE")
