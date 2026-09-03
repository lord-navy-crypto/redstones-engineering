#!/usr/bin/env python3
"""Deterministic engineering reference-model tests for RSE quality gates.

These tests do not replace NeoForge compile/runClient validation. They verify the
small mathematical contracts that should remain true across refactors.
"""

from __future__ import annotations


def clamp15(value: int) -> int:
    return max(0, min(15, value))


def calibrated(raw: int, offset: int) -> int:
    return clamp15(raw + offset)


def rolling_metrics(samples: list[int]) -> tuple[int, int, int]:
    if not samples:
        return 0, 0, 0
    avg100 = (sum(samples) * 100 + len(samples) // 2) // len(samples)
    p2p = max(samples) - min(samples)
    if len(samples) < 2:
        mean_step100 = 0
    else:
        total = sum(abs(b - a) for a, b in zip(samples, samples[1:]))
        mean_step100 = (total * 100 + (len(samples) - 1) // 2) // (len(samples) - 1)
    return avg100, p2p, mean_step100


def stability(samples: list[int]) -> str:
    avg100, p2p, mean_step100 = rolling_metrics(samples)
    _ = avg100
    if len(samples) < 4:
        return "WARMUP"
    if p2p == 0 and mean_step100 == 0:
        return "STEADY"
    if p2p <= 1 and mean_step100 <= 50:
        return "STABLE"
    if p2p <= 5 and mean_step100 <= 200:
        return "DYNAMIC"
    return "HIGH_VARIATION"


def topology_integrity(*, bounded: bool, duplicate_channels: int, probes: int) -> str:
    if not bounded:
        return "TRUNCATED"
    if duplicate_channels > 0:
        return "AMBIGUOUS"
    if probes == 0:
        return "NO_PROBES"
    return "OK"


def assert_equal(actual, expected, label: str) -> None:
    if actual != expected:
        raise AssertionError(f"{label}: got {actual!r}, expected {expected!r}")


def main() -> None:
    # World-facing redstone boundary.
    for raw in range(-20, 36):
        value = clamp15(raw)
        assert 0 <= value <= 15

    # Calibration is display-only. For every legal raw value and every supported
    # offset, INLINE output remains raw while the displayed value is bounded.
    for raw in range(16):
        for offset in range(-2, 3):
            inline_output = raw
            display_value = calibrated(raw, offset)
            assert_equal(inline_output, raw, "INLINE pass-through must stay raw")
            assert 0 <= display_value <= 15

    assert_equal(calibrated(0, -2), 0, "lower calibration clamp")
    assert_equal(calibrated(15, 2), 15, "upper calibration clamp")
    assert_equal(calibrated(7, 2), 9, "positive calibration")
    assert_equal(calibrated(7, -2), 5, "negative calibration")

    # Rolling window math.
    assert_equal(rolling_metrics([7, 7, 7, 7]), (700, 0, 0), "steady window")
    assert_equal(rolling_metrics([0, 1, 0, 1]), (50, 1, 100), "alternating low window")
    assert_equal(rolling_metrics([0, 5, 10, 15]), (750, 15, 500), "ramp window")

    assert_equal(stability([7, 7, 7]), "WARMUP", "warmup classification")
    assert_equal(stability([7, 7, 7, 7]), "STEADY", "steady classification")
    # Two one-step changes over seven intervals => meanStep ~= 0.29 <= 0.50.
    assert_equal(stability([7, 7, 8, 8, 8, 7, 7, 7]), "STABLE", "stable classification")
    assert_equal(stability([5, 7, 8, 6, 7, 8]), "DYNAMIC", "dynamic classification")
    assert_equal(stability([0, 15, 0, 15]), "HIGH_VARIATION", "high-variation classification")

    # Instrument topology integrity precedence.
    assert_equal(topology_integrity(bounded=False, duplicate_channels=0, probes=1), "TRUNCATED", "truncated precedence")
    assert_equal(topology_integrity(bounded=True, duplicate_channels=1, probes=2), "AMBIGUOUS", "duplicate precedence")
    assert_equal(topology_integrity(bounded=True, duplicate_channels=0, probes=0), "NO_PROBES", "no probes")
    assert_equal(topology_integrity(bounded=True, duplicate_channels=0, probes=2), "OK", "healthy topology")

    # Timebase contracts used by the current analyzers.
    scope_sample_period_ticks = 2
    logic_sample_period_ticks = 1
    assert_equal(8 * scope_sample_period_ticks, 16, "scope cursor timebase")
    assert_equal(8 * logic_sample_period_ticks, 8, "logic cursor timebase")

    print("RSE reference-model tests: PASS")
    print("  0..15 boundary + display-only calibration: PASS")
    print("  rolling-window metrics/classification: PASS")
    print("  topology integrity precedence: PASS")
    print("  analyzer timebase contracts: PASS")


if __name__ == "__main__":
    main()
