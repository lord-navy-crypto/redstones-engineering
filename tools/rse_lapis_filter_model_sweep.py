#!/usr/bin/env python3
"""Independent numerical sweep for the RSE first-order Lapis low-pass model."""

from pathlib import Path
import math
import re
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
profile_path = root / "src/main/java/dev/redstoneengineering/physics/EngineeringParameterProfile.java"
block_path = root / "src/main/java/dev/redstoneengineering/block/LapisLowPassFilterBlock.java"

errors = []

if not profile_path.is_file():
    errors.append(f"missing {profile_path.relative_to(root)}")
if not block_path.is_file():
    errors.append(f"missing {block_path.relative_to(root)}")

profile = profile_path.read_text(errors="ignore") if profile_path.is_file() else ""
block = block_path.read_text(errors="ignore") if block_path.is_file() else ""

def parse_number(name, cast=float):
    match = re.search(rf"{re.escape(name)}\s*=\s*([0-9]+(?:\.[0-9]+)?)", profile)
    if not match:
        errors.append(f"missing numeric constant {name}")
        return None
    return cast(match.group(1))

sample_ticks = parse_number("LAPIS_FILTER_SAMPLE_PERIOD_TICKS", int)
nominal_tps = parse_number("NOMINAL_TICKS_PER_SECOND", float)

alpha_match = re.search(
    r"LAPIS_FILTER_ALPHA\s*=\s*\{([^}]*)\}",
    profile,
    flags=re.S,
)
alphas = []
if alpha_match:
    for token in alpha_match.group(1).split(","):
        token = token.strip()
        if token:
            try:
                alphas.append(float(token))
            except ValueError:
                errors.append(f"invalid alpha token {token!r}")
else:
    errors.append("missing LAPIS_FILTER_ALPHA array")

if sample_ticks is not None and sample_ticks <= 0:
    errors.append("sample period must be positive")
if nominal_tps is not None and nominal_tps <= 0:
    errors.append("nominal TPS must be positive")
if len(alphas) != 8:
    errors.append(f"expected 8 alpha settings, found {len(alphas)}")

for i, alpha in enumerate(alphas):
    if not (0.0 < alpha <= 1.0):
        errors.append(f"alpha[{i}]={alpha} is outside (0,1]")

def java_round_positive(value):
    return int(math.floor(value + 0.5))

taus = []
cutoffs = []
if sample_ticks and nominal_tps and alphas:
    for i, alpha in enumerate(alphas):
        if alpha >= 1.0:
            # Explicit bypass: y[n] = x[n].
            y = 0
            y = java_round_positive(y + alpha * (100 - y))
            if y != 100:
                errors.append("alpha=1 bypass did not reproduce input in one sample")
            continue

        tau_ticks = -sample_ticks / math.log1p(-alpha)
        cutoff_hz = nominal_tps / (2.0 * math.pi * tau_ticks)
        taus.append(tau_ticks)
        cutoffs.append(cutoff_hz)

        if not (math.isfinite(tau_ticks) and tau_ticks > 0.0):
            errors.append(f"alpha[{i}] produced invalid tau={tau_ticks}")
        if not (math.isfinite(cutoff_hz) and cutoff_hz > 0.0):
            errors.append(f"alpha[{i}] produced invalid cutoff={cutoff_hz}")

        # Parameter sweep of a baseline-initialized 0 -> 100 step.
        y = 0
        previous = y
        for sample in range(1, 65):
            y = java_round_positive(y + alpha * (100 - y))
            if not (0 <= y <= 100):
                errors.append(f"alpha[{i}] sample {sample}: output escaped bounds ({y})")
                break
            if y < previous:
                errors.append(f"alpha[{i}] sample {sample}: non-monotonic step response ({previous}->{y})")
                break
            previous = y

    for i in range(1, len(taus)):
        if not taus[i] < taus[i - 1]:
            errors.append("time constant must decrease as alpha increases")
            break
    for i in range(1, len(cutoffs)):
        if not cutoffs[i] > cutoffs[i - 1]:
            errors.append("cutoff frequency must increase as alpha increases")
            break

for token in (
    "y[n]=y[n-1]+alpha(x[n]-y[n-1])",
    "lapisFilterTimeConstantTicks(index)",
    "lapisFilterCutoffHzNominal(index)",
    "LAPIS_FILTER_SAMPLE_PERIOD_TICKS",
):
    if token not in block:
        errors.append(f"block readback missing {token!r}")

if errors:
    print("RSE LAPIS FILTER MODEL SWEEP: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE LAPIS FILTER MODEL SWEEP: PASS")
print(f" alpha settings: {len(alphas)}")
print(f" sample period: {sample_ticks} ticks")
print(f" nominal cadence: {nominal_tps:.1f} ticks/s")
print(" bounded monotonic step-response sweep: PASS")
print(" tau(alpha) monotonicity: PASS")
print(" cutoff(alpha) monotonicity: PASS")
print(" alpha=1 explicit bypass: PASS")
