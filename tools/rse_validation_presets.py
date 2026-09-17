from __future__ import annotations

from dataclasses import dataclass
from typing import Callable

Placement = tuple[tuple[int, int, int], str]
Builder = Callable[[], tuple[tuple[int, int, int], list[Placement]]]


@dataclass(frozen=True)
class Preset:
    title: str
    level: str
    purpose: str
    builder: Builder


def _floor(width: int, depth: int, block_id: str = "minecraft:smooth_stone") -> list[Placement]:
    return [((x, 0, z), block_id) for x in range(width) for z in range(depth)]


def _overlay(base: list[Placement], additions: list[Placement]) -> list[Placement]:
    by_pos = {position: block_id for position, block_id in base}
    for position, block_id in additions:
        by_pos[position] = block_id
    return [(position, by_pos[position]) for position in sorted(by_pos)]


def _border(width: int, depth: int, block_id: str) -> list[Placement]:
    out: list[Placement] = []
    for x in range(width):
        out.append(((x, 0, 0), block_id))
        out.append(((x, 0, depth - 1), block_id))
    for z in range(1, depth - 1):
        out.append(((0, 0, z), block_id))
        out.append(((width - 1, 0, z), block_id))
    return out


def _bench(width: int = 9, depth: int = 7, marker: str = "minecraft:blue_concrete") -> list[Placement]:
    p = _overlay(_floor(width, depth), _border(width, depth, marker))
    return _overlay(p, [
        ((1, 1, 1), "minecraft:sea_lantern"),
        ((width - 2, 1, depth - 2), "minecraft:sea_lantern"),
    ])


def _row(blocks: list[str], z: int = 3, start_x: int = 1) -> list[Placement]:
    return [((start_x + i, 1, z), block_id) for i, block_id in enumerate(blocks)]


def _simple(blocks: list[str], marker: str, width: int | None = None) -> tuple[tuple[int, int, int], list[Placement]]:
    width = width or max(9, len(blocks) + 2)
    depth = 7
    p = _bench(width, depth, marker)
    p = _overlay(p, _row(blocks, z=3, start_x=1))
    return (width, 4, depth), p


# 01 BASIC -----------------------------------------------------------------

def _redstone_input_output():
    return _simple([
        "minecraft:redstone_block",
        "minecraft:redstone_wire",
        "redstoneengineering:signal_probe",
        "minecraft:redstone_wire",
        "minecraft:redstone_lamp",
    ], "minecraft:red_concrete")


def _signal_probe():
    return _simple([
        "redstoneengineering:redstone_reference_source",
        "redstoneengineering:redstone_signal_cable",
        "redstoneengineering:signal_probe",
        "redstoneengineering:instrument_cable",
        "redstoneengineering:analog_indicator",
    ], "minecraft:light_blue_concrete")


def _signal_analyzer():
    return _simple([
        "redstoneengineering:redstone_reference_source",
        "redstoneengineering:redstone_signal_cable",
        "redstoneengineering:signal_analyzer",
        "redstoneengineering:instrument_cable",
        "redstoneengineering:analog_indicator",
    ], "minecraft:blue_concrete")


def _oscilloscope():
    return _simple([
        "redstoneengineering:redstone_reference_source",
        "redstoneengineering:signal_probe",
        "redstoneengineering:instrument_cable",
        "redstoneengineering:oscilloscope",
    ], "minecraft:cyan_concrete")


def _signal_conditioner():
    return _simple([
        "redstoneengineering:redstone_reference_source",
        "redstoneengineering:signal_probe",
        "redstoneengineering:instrument_cable",
        "redstoneengineering:signal_conditioner",
        "redstoneengineering:instrument_cable",
        "redstoneengineering:signal_analyzer",
    ], "minecraft:lime_concrete")


def _calibration_module():
    return _simple([
        "redstoneengineering:redstone_reference_source",
        "redstoneengineering:signal_probe",
        "redstoneengineering:instrument_cable",
        "redstoneengineering:calibration_module",
        "redstoneengineering:instrument_cable",
        "redstoneengineering:oscilloscope",
    ], "minecraft:yellow_concrete")


def _directional_io():
    return _simple([
        "redstoneengineering:redstone_reference_source",
        "redstoneengineering:redstone_cable_terminal",
        "redstoneengineering:redstone_signal_cable",
        "redstoneengineering:redstone_cable_junction",
        "redstoneengineering:redstone_signal_cable",
        "redstoneengineering:analog_indicator",
    ], "minecraft:orange_concrete")


def _instrument_chain():
    return _simple([
        "redstoneengineering:redstone_reference_source",
        "redstoneengineering:signal_probe",
        "redstoneengineering:instrument_cable",
        "redstoneengineering:signal_conditioner",
        "redstoneengineering:instrument_cable",
        "redstoneengineering:calibration_module",
        "redstoneengineering:instrument_cable",
        "redstoneengineering:signal_analyzer",
        "redstoneengineering:instrument_cable",
        "redstoneengineering:oscilloscope",
    ], "minecraft:light_blue_concrete", width=13)


# 02 SIGNAL ----------------------------------------------------------------

def _precision_filter():
    return _simple([
        "redstoneengineering:lapis_precision_source",
        "redstoneengineering:lapis_signal_line",
        "redstoneengineering:precision_filter",
        "redstoneengineering:lapis_signal_line",
        "redstoneengineering:lapis_precision_meter",
    ], "minecraft:blue_concrete")


def _sample_hold():
    return _simple([
        "redstoneengineering:lapis_precision_source",
        "redstoneengineering:lapis_signal_line",
        "redstoneengineering:sample_hold",
        "redstoneengineering:lapis_signal_line",
        "redstoneengineering:lapis_precision_meter",
    ], "minecraft:purple_concrete")


def _edge_detector():
    return _simple([
        "redstoneengineering:redstone_reference_source",
        "redstoneengineering:redstone_signal_cable",
        "redstoneengineering:edge_detector",
        "redstoneengineering:redstone_signal_cable",
        "redstoneengineering:analog_indicator",
    ], "minecraft:red_concrete")


def _pulse_shaper():
    return _simple([
        "redstoneengineering:redstone_reference_source",
        "redstoneengineering:edge_detector",
        "redstoneengineering:pulse_shaper",
        "redstoneengineering:signal_tap",
        "redstoneengineering:oscilloscope",
    ], "minecraft:pink_concrete")


def _pwm_control():
    return _simple([
        "redstoneengineering:redstone_reference_source",
        "redstoneengineering:redstone_signal_cable",
        "redstoneengineering:pwm_controller",
        "redstoneengineering:redstone_signal_cable",
        "redstoneengineering:analog_indicator",
    ], "minecraft:orange_concrete")


def _noise_vs_filter():
    width, depth = 11, 9
    p = _bench(width, depth, "minecraft:magenta_concrete")
    p = _overlay(p, [
        ((1, 1, 3), "redstoneengineering:lapis_noise_source"),
        ((2, 1, 3), "redstoneengineering:lapis_signal_line"),
        ((3, 1, 3), "redstoneengineering:lapis_precision_meter"),
        ((1, 1, 5), "redstoneengineering:lapis_noise_source"),
        ((2, 1, 5), "redstoneengineering:lapis_signal_line"),
        ((3, 1, 5), "redstoneengineering:lapis_low_pass_filter"),
        ((4, 1, 5), "redstoneengineering:lapis_signal_line"),
        ((5, 1, 5), "redstoneengineering:lapis_precision_meter"),
        ((7, 1, 4), "redstoneengineering:oscilloscope"),
    ])
    return (width, 4, depth), p


def _quantizer_scaler():
    return _simple([
        "redstoneengineering:redstone_reference_source",
        "redstoneengineering:redstone_signal_cable",
        "redstoneengineering:redstone_to_lapis_scaler",
        "redstoneengineering:lapis_signal_line",
        "redstoneengineering:lapis_to_redstone_quantizer",
        "redstoneengineering:redstone_signal_cable",
        "redstoneengineering:analog_indicator",
    ], "minecraft:cyan_concrete", width=10)


def _multi_stage_signal_chain():
    return _simple([
        "redstoneengineering:lapis_noise_source",
        "redstoneengineering:lapis_signal_line",
        "redstoneengineering:lapis_low_pass_filter",
        "redstoneengineering:lapis_signal_line",
        "redstoneengineering:precision_filter",
        "redstoneengineering:lapis_signal_line",
        "redstoneengineering:sample_hold",
        "redstoneengineering:lapis_signal_line",
        "redstoneengineering:lapis_precision_meter",
    ], "minecraft:purple_concrete", width=12)


PRESETS: dict[str, Preset] = {
    "01_basic/redstone_input_output": Preset("Vanilla/RSE Redstone Boundary", "L1", "Smoke-test vanilla redstone input/output and a real RSE probe.", _redstone_input_output),
    "01_basic/signal_probe": Preset("Signal Probe Bench", "L1", "Exercise a probe between a reference source and indicator.", _signal_probe),
    "01_basic/signal_analyzer": Preset("Signal Analyzer Bench", "L1", "Observe a reference signal with the analyzer.", _signal_analyzer),
    "01_basic/oscilloscope": Preset("Oscilloscope Bench", "L1", "Feed a measured signal into the oscilloscope.", _oscilloscope),
    "01_basic/signal_conditioner": Preset("Signal Conditioner Chain", "L2", "Compare source, conditioned signal, and analyzer readback.", _signal_conditioner),
    "01_basic/calibration_module": Preset("Calibration Chain", "L2", "Test probe-to-calibration-to-scope behavior.", _calibration_module),
    "01_basic/directional_io": Preset("Directional I/O Bench", "L2", "Exercise terminal, cable, junction, and indicator connectivity.", _directional_io),
    "01_basic/instrument_chain": Preset("Full Instrument Chain", "L3", "Run source through probe, conditioning, calibration, analyzer, and scope.", _instrument_chain),
    "02_signal/precision_filter": Preset("Precision Filter Bench", "L2", "Test a precision source through a filter into a meter.", _precision_filter),
    "02_signal/sample_hold": Preset("Sample/Hold Bench", "L2", "Exercise sample/hold behavior on a lapis signal path.", _sample_hold),
    "02_signal/edge_detector": Preset("Edge Detector Bench", "L2", "Observe edge detection on an explicit redstone signal path.", _edge_detector),
    "02_signal/pulse_shaper": Preset("Pulse Shaper Bench", "L2", "Exercise edge detection, pulse shaping, tapping, and scope observation.", _pulse_shaper),
    "02_signal/pwm_control": Preset("PWM Control Bench", "L2", "Drive a PWM controller from a reference source and observe output.", _pwm_control),
    "02_signal/noise_vs_filter": Preset("Noise vs Filter Comparison", "L3", "Compare unfiltered noise against a low-pass-filtered path.", _noise_vs_filter),
    "02_signal/quantizer_scaler": Preset("Scaler/Quantizer Round Trip", "L3", "Cross redstone to lapis and back through explicit converters.", _quantizer_scaler),
    "02_signal/multi_stage_signal_chain": Preset("Multi-stage Signal Chain", "L3", "Stress a longer noise/filter/sample/meter signal chain.", _multi_stage_signal_chain),
}
