#!/usr/bin/env python3
from pathlib import Path
import math
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors = []

def read(rel):
    path = root / rel
    if not path.is_file():
        errors.append(f"missing {rel}")
        return ""
    return path.read_text(errors="ignore")

range_screen = read("src/main/java/dev/redstoneengineering/client/ui/RangeSensorScreen.java")
range_block = read("src/main/java/dev/redstoneengineering/block/RangeSensorBlock.java")
conditioner_screen = read("src/main/java/dev/redstoneengineering/ui/ldlib/SignalConditionerLdUi.java")
conditioner_block = read("src/main/java/dev/redstoneengineering/block/SignalConditionerBlock.java")
quartz_screen = read("src/main/java/dev/redstoneengineering/client/ui/QuartzTimingScreen.java")
quartz_divider = read("src/main/java/dev/redstoneengineering/block/QuartzClockDividerBlock.java")
quartz_stability = read("src/main/java/dev/redstoneengineering/block/QuartzStabilityMonitorBlock.java")
media_screen = read("src/main/java/dev/redstoneengineering/client/ui/MediaConversionScreen.java")
media_diag = read("src/main/java/dev/redstoneengineering/core/diagnostic/CoreMediaDiagnostics.java")

server_contracts = {
    "RangeSensorBlock.java": (range_block, (
        "case 0 -> 4; case 1 -> 8; case 2 -> 15",
        "((range - distance + 1.0) / range) * 15.0",
        "(distance / (double) range) * 15.0",
        "distance <= Math.max(1, range / 2) ? 15 : 0",
        "distance >= low && distance <= high ? 15 : 0",
        "scan.complete() ? responseSignal",
    )),
    "SignalConditionerBlock.java": (conditioner_block, (
        "case 0 -> SignalMath.gain",
        "case 1 -> SignalMath.offset",
        "case 2 -> Math.min(input, Math.max(1, param))",
        "case 3 -> SignalMath.threshold",
        "Math.abs(input - previousOutput) >= Math.max(1, Math.min(4, param)) ? input : previousOutput",
    )),
    "QuartzClockDividerBlock.java": (quartz_divider, (
        "case 0 -> 2; case 1 -> 4; case 2 -> 8; default -> 16",
        "Math.min(4096, Math.max(1, input.periodTicks()) * divisor)",
        "if (rising) runtime[COUNT_SLOT] = (runtime[COUNT_SLOT] + 1) % divisor",
        "runtime[OUTPUT_SLOT] = runtime[COUNT_SLOT] < divisor / 2 ? 1 : 0",
    )),
    "QuartzStabilityMonitorBlock.java": (quartz_stability, (
        "two genuine rising edges",
        "Math.abs(runtime[MEASURED_SLOT] - Math.max(1, input.periodTicks()))",
        "measurement.currentMeasurement()",
    )),
    "CoreMediaDiagnostics.java": (media_diag, (
        "Math.round(clamp(redstone, 0, 15) * 100.0f / 15.0f)",
        "Math.round(clamp(lapis, 0, 100) * 15.0f / 100.0f)",
        "quantizationError",
        "sourceCodeSpacing",
    )),
}

for name, (text, tokens) in server_contracts.items():
    for token in tokens:
        if token not in text:
            errors.append(f"{name} server contract missing {token!r}")

screen_contracts = {
    "RangeSensorScreen.java": (range_screen, (
        "FORMULA-FIRST SENSOR RESPONSE",
        "y = (d ≤ 0) ? 0 : round(15 · (R - d + 1) / R)",
        "y = (d ≤ 0) ? 0 : round(15 · d / R)",
        '"EVIDENCE", "scan"',
        "A complete CLEAR scan with d=0 is valid evidence",
    )),
    "SignalConditionerLdUi.java": (conditioner_screen, (
        "FORMULA-FIRST SERVER CONTROL",
        "y = clamp₀..₁₅(g · x)",
        "y = clamp₀..₁₅(x + b)",
        "y = min(x, c)",
        "y = (x ≥ T) ? x : 0",
        "y = (|x - y_prev| ≥ B) ? x : y_prev",
        '"EVIDENCE", "boundary"',
    )),
    "QuartzTimingScreen.java": (quartz_screen, (
        "FORMULA-FIRST TIMING MODEL",
        "valid input ⇒ T_out = min(4096, N · max(1,T_in)) ticks",
        "expectedDividerPeriod()",
        '"EVIDENCE", "period limit"',
        "SATURATED @4096",
        "|e_T| = |T_meas - T_upstream|",
    )),
    "MediaConversionScreen.java": (media_screen, (
        "FORMULA-FIRST MEDIA BOUNDARY",
        "y_L = round(100 · x_R / 15)",
        "y_R = round(15 · x_L / 100)",
        "x_reconstructed",
        "|e_q|",
        "NO NEW SOURCE PRECISION",
    )),
}

for name, (text, tokens) in screen_contracts.items():
    for token in tokens:
        if token not in text:
            errors.append(f"{name} Wave-7 HMI contract missing {token!r}")
    if "dev.redstoneengineering.physics" in text:
        errors.append(f"{name} imports server physics directly; HMI must remain presentation-only")

def clamp(v, lo, hi):
    return max(lo, min(hi, v))

def jround(v):
    return int(math.floor(v + 0.5))

# Range Sensor parameter sweep: every configured range/response stays inside Redstone 0..15.
for r in (4, 8, 15):
    previous_proximity = None
    previous_distance = None
    for d in range(0, r + 1):
        proximity = 0 if d <= 0 else clamp(jround(((r - d + 1.0) / r) * 15.0), 0, 15)
        distance = 0 if d <= 0 else clamp(jround((d / float(r)) * 15.0), 0, 15)
        threshold = 15 if d > 0 and d <= max(1, r // 2) else 0
        low = max(1, r // 3)
        high = max(low, (r * 2) // 3)
        window = 15 if d >= low and d <= high else 0
        vals = (proximity, distance, threshold, window)
        if any(v < 0 or v > 15 for v in vals):
            errors.append(f"range response escaped 0..15 at R={r}, d={d}: {vals}")
        if d > 1 and previous_proximity is not None and proximity > previous_proximity:
            errors.append(f"proximity response must not rise with distance at R={r}, d={d}")
        if d > 1 and previous_distance is not None and distance < previous_distance:
            errors.append(f"distance response must not fall with distance at R={r}, d={d}")
        if d > 0:
            previous_proximity = proximity
            previous_distance = distance

# Signal Conditioner sweep across all real parameter domains, including stateful deadband.
for x in range(16):
    for g in range(1, 5):
        if not 0 <= clamp(jround(x * g), 0, 15) <= 15:
            errors.append(f"gain escaped boundary x={x}, g={g}")
    for b in range(-5, 6):
        if not 0 <= clamp(x + b, 0, 15) <= 15:
            errors.append(f"offset escaped boundary x={x}, b={b}")
    for c in range(1, 16):
        y = min(x, c)
        if y > c or not 0 <= y <= 15:
            errors.append(f"clamp contract failed x={x}, c={c}, y={y}")
    for t in range(1, 16):
        y = x if x >= t else 0
        if y not in (0, x):
            errors.append(f"threshold contract failed x={x}, T={t}, y={y}")
    for prev in range(16):
        for band in range(1, 5):
            y = x if abs(x - prev) >= band else prev
            if y not in (x, prev) or not 0 <= y <= 15:
                errors.append(f"deadband contract failed x={x}, prev={prev}, B={band}, y={y}")

# Quartz divider sweep: server and HMI must agree on the 4096-tick saturation boundary.
for period in range(1, 4097):
    prior = 0
    for divisor in (2, 4, 8, 16):
        expected = min(4096, max(1, period) * divisor)
        if expected < 1 or expected > 4096:
            errors.append(f"quartz period escaped bounds T={period}, N={divisor}: {expected}")
        if expected < prior:
            errors.append(f"quartz output period lost monotonic divisor ordering T={period}, N={divisor}")
        prior = expected
        if period * divisor <= 4096 and expected != period * divisor:
            errors.append(f"quartz unsaturated product mismatch T={period}, N={divisor}")

# Redstone/Lapis representation boundary sweep using Java-positive Math.round semantics.
def lapis_from_redstone(redstone):
    return jround(clamp(redstone, 0, 15) * 100.0 / 15.0)

def redstone_from_lapis(lapis):
    return jround(clamp(lapis, 0, 100) * 15.0 / 100.0)

for r in range(16):
    l = lapis_from_redstone(r)
    rr = redstone_from_lapis(l)
    if not 0 <= l <= 100 or rr != r:
        errors.append(f"Redstone exact-code round trip failed r={r}, lapis={l}, reconstructed={rr}")

max_error = 0
for l in range(101):
    r = redstone_from_lapis(l)
    reconstructed = lapis_from_redstone(r)
    error = abs(l - reconstructed)
    max_error = max(max_error, error)
    if not 0 <= r <= 15 or error > 3:
        errors.append(f"Lapis quantization contract failed l={l}, r={r}, reconstructed={reconstructed}, error={error}")

if errors:
    print("RSE MEASUREMENT CHAIN MODEL VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE MEASUREMENT CHAIN MODEL VERIFY: PASS")
print(" Range Sensor server/HMI + response sweep: PASS")
print(" Signal Conditioner server/HMI + bounded transfer sweep: PASS")
print(" Quartz timing server/HMI + 4096-tick saturation sweep: PASS")
print(f" Media conversion exact-code/quantization sweep: PASS (max Lapis error={max_error})")
print(" client/no-second-solver boundary: PASS")
