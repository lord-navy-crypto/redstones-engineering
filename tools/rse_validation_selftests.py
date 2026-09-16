from __future__ import annotations

from dataclasses import dataclass
from typing import Callable

BlockSpec = str | tuple[str, dict[str, str]]
Placement = tuple[tuple[int, int, int], BlockSpec]
Builder = Callable[[], tuple[tuple[int, int, int], list[Placement]]]


@dataclass(frozen=True)
class StatusPanel:
    wait_label: tuple[int, int, int]
    pass_label: tuple[int, int, int]
    fail_label: tuple[int, int, int]
    wait_lamp: tuple[int, int, int]
    pass_lamp: tuple[int, int, int]
    fail_lamp: tuple[int, int, int]
    wait_power: tuple[int, int, int]
    pass_power: tuple[int, int, int]
    fail_power: tuple[int, int, int]


@dataclass(frozen=True)
class SelfTest:
    title: str
    level: str
    purpose: str
    evaluator: str
    builder: Builder
    panel: StatusPanel
    stimulus: tuple[int, int, int]
    dut: tuple[int, int, int]
    observe: tuple[int, int, int]
    expected_value: int | None = None
    settling_ticks: int = 0


PANEL = StatusPanel(
    wait_label=(8, 1, 1),
    pass_label=(9, 1, 1),
    fail_label=(10, 1, 1),
    wait_lamp=(8, 2, 1),
    pass_lamp=(9, 2, 1),
    fail_lamp=(10, 2, 1),
    wait_power=(8, 2, 2),
    pass_power=(9, 2, 2),
    fail_power=(10, 2, 2),
)


def _normalize(block: BlockSpec) -> tuple[str, tuple[tuple[str, str], ...]]:
    if isinstance(block, str):
        return block, ()
    block_id, properties = block
    return block_id, tuple(sorted((str(k), str(v)) for k, v in properties.items()))


def _overlay(base: list[Placement], additions: list[Placement]) -> list[Placement]:
    by_pos: dict[tuple[int, int, int], BlockSpec] = {pos: block for pos, block in base}
    for pos, block in additions:
        by_pos[pos] = block
    return [(pos, by_pos[pos]) for pos in sorted(by_pos)]


def _base(marker: str, width: int = 13, depth: int = 9) -> list[Placement]:
    p: list[Placement] = [((x, 0, z), "minecraft:smooth_stone") for x in range(width) for z in range(depth)]
    border: list[Placement] = []
    for x in range(width):
        border.append(((x, 0, 0), marker))
        border.append(((x, 0, depth - 1), marker))
    for z in range(1, depth - 1):
        border.append(((0, 0, z), marker))
        border.append(((width - 1, 0, z), marker))
    p = _overlay(p, border)
    p = _overlay(p, [
        (PANEL.wait_label, "minecraft:yellow_concrete"),
        (PANEL.pass_label, "minecraft:lime_concrete"),
        (PANEL.fail_label, "minecraft:red_concrete"),
        (PANEL.wait_lamp, "minecraft:redstone_lamp"),
        (PANEL.pass_lamp, "minecraft:redstone_lamp"),
        (PANEL.fail_lamp, "minecraft:redstone_lamp"),
        (PANEL.wait_power, "minecraft:redstone_block"),
        ((1, 1, 1), "minecraft:sea_lantern"),
        ((11, 1, 7), "minecraft:sea_lantern"),
    ])
    return p


def _ref(power: int, facing: str = "east") -> BlockSpec:
    return ("redstoneengineering:redstone_reference_source", {"facing": facing, "power": str(power)})


def _probe(facing: str = "west", channel: int = 0) -> BlockSpec:
    return ("redstoneengineering:signal_probe", {"facing": facing, "channel": str(channel)})


def _analyzer(facing: str, mode: int) -> BlockSpec:
    return (
        "redstoneengineering:signal_analyzer",
        {"facing": facing, "mode": str(mode), "output": "0", "calibration": "2"},
    )


def _indicator(facing: str = "east") -> BlockSpec:
    return ("redstoneengineering:analog_indicator", {"facing": facing, "level": "0"})


def _conditioner(mode: int = 0, param: int = 2) -> BlockSpec:
    return (
        "redstoneengineering:signal_conditioner",
        {"facing": "east", "input_facing": "west", "output": "0", "mode": str(mode), "param": str(param)},
    )


def _reference_source() -> tuple[tuple[int, int, int], list[Placement]]:
    p = _base("minecraft:red_concrete")
    p = _overlay(p, [
        ((3, 1, 4), _ref(7)),
        ((4, 1, 4), "minecraft:redstone_wire"),
        ((5, 1, 4), "minecraft:redstone_lamp"),
    ])
    return (13, 4, 9), p


def _signal_probe() -> tuple[tuple[int, int, int], list[Placement]]:
    p = _base("minecraft:light_blue_concrete")
    p = _overlay(p, [
        ((3, 1, 4), _ref(9)),
        ((4, 1, 4), _probe("west", 0)),
        ((5, 1, 4), "redstoneengineering:instrument_cable"),
        ((6, 1, 4), "redstoneengineering:oscilloscope"),
    ])
    return (13, 4, 9), p


def _signal_analyzer_tap() -> tuple[tuple[int, int, int], list[Placement]]:
    p = _base("minecraft:cyan_concrete")
    p = _overlay(p, [
        ((3, 1, 4), _ref(7)),
        ((4, 1, 4), _analyzer("west", 0)),
    ])
    return (13, 4, 9), p


def _signal_analyzer_inline() -> tuple[tuple[int, int, int], list[Placement]]:
    p = _base("minecraft:blue_concrete")
    p = _overlay(p, [
        ((3, 1, 4), _ref(7)),
        ((4, 1, 4), _analyzer("west", 1)),
        ((5, 1, 4), "minecraft:redstone_wire"),
        ((6, 1, 4), _indicator("east")),
    ])
    return (13, 4, 9), p


def _analog_indicator() -> tuple[tuple[int, int, int], list[Placement]]:
    p = _base("minecraft:lime_concrete")
    p = _overlay(p, [
        ((3, 1, 4), _ref(11)),
        ((4, 1, 4), "minecraft:redstone_wire"),
        ((5, 1, 4), _indicator("east")),
    ])
    return (13, 4, 9), p


def _signal_conditioner_gain() -> tuple[tuple[int, int, int], list[Placement]]:
    p = _base("minecraft:green_concrete")
    p = _overlay(p, [
        ((3, 1, 4), _ref(6)),
        ((4, 1, 4), _conditioner(0, 2)),
        ((5, 1, 4), "minecraft:redstone_wire"),
        ((6, 1, 4), _indicator("east")),
    ])
    return (13, 4, 9), p


def _directional_io() -> tuple[tuple[int, int, int], list[Placement]]:
    p = _base("minecraft:orange_concrete")
    p = _overlay(p, [
        ((2, 1, 3), _ref(8)),
        ((3, 1, 3), _conditioner(0, 1)),
        ((4, 1, 3), _indicator("east")),
        ((3, 1, 5), _ref(8, "north")),
        ((4, 1, 5), _conditioner(0, 1)),
    ])
    return (13, 4, 9), p


def _instrument_bus() -> tuple[tuple[int, int, int], list[Placement]]:
    p = _base("minecraft:light_blue_concrete")
    p = _overlay(p, [
        ((2, 1, 4), _ref(5)),
        ((3, 1, 4), _probe("west", 0)),
        ((4, 1, 4), "redstoneengineering:instrument_cable"),
        ((5, 1, 4), "redstoneengineering:instrument_cable"),
        ((6, 1, 4), "redstoneengineering:oscilloscope"),
    ])
    return (13, 4, 9), p


def _conditioner_saturation() -> tuple[tuple[int, int, int], list[Placement]]:
    p = _base("minecraft:red_concrete")
    p = _overlay(p, [
        ((3, 1, 4), _ref(10)),
        ((4, 1, 4), _conditioner(0, 2)),
        ((5, 1, 4), _indicator("east")),
    ])
    return (13, 4, 9), p


def _precision_filter() -> tuple[tuple[int, int, int], list[Placement]]:
    p = _base("minecraft:blue_concrete")
    p = _overlay(p, [
        ((2, 1, 4), "redstoneengineering:lapis_precision_source"),
        ((3, 1, 4), "redstoneengineering:lapis_signal_line"),
        ((4, 1, 4), "redstoneengineering:precision_filter"),
        ((5, 1, 4), "redstoneengineering:lapis_signal_line"),
        ((6, 1, 4), "redstoneengineering:lapis_precision_meter"),
    ])
    return (13, 4, 9), p


def _sample_hold() -> tuple[tuple[int, int, int], list[Placement]]:
    p = _base("minecraft:purple_concrete")
    p = _overlay(p, [
        ((2, 1, 4), "redstoneengineering:lapis_precision_source"),
        ((3, 1, 4), "redstoneengineering:lapis_signal_line"),
        ((4, 1, 4), "redstoneengineering:sample_hold"),
        ((5, 1, 4), "redstoneengineering:lapis_signal_line"),
        ((6, 1, 4), "redstoneengineering:lapis_precision_meter"),
        ((4, 1, 5), "redstoneengineering:quartz_oscillator"),
    ])
    return (13, 4, 9), p


def _edge_detector() -> tuple[tuple[int, int, int], list[Placement]]:
    p = _base("minecraft:red_concrete")
    p = _overlay(p, [
        ((3, 1, 4), _ref(7)),
        ((4, 1, 4), ("redstoneengineering:edge_detector", {"facing": "east", "input_facing": "west", "output": "0"})),
        ((5, 1, 4), _indicator("east")),
    ])
    return (13, 4, 9), p


def _pulse_shaper() -> tuple[tuple[int, int, int], list[Placement]]:
    p = _base("minecraft:pink_concrete")
    p = _overlay(p, [
        ((2, 1, 4), _ref(7)),
        ((3, 1, 4), ("redstoneengineering:edge_detector", {"facing": "east", "input_facing": "west", "output": "0"})),
        ((4, 1, 4), ("redstoneengineering:pulse_shaper", {"facing": "east", "input_facing": "west", "output": "0"})),
        ((5, 1, 4), "redstoneengineering:signal_tap"),
        ((6, 1, 4), "redstoneengineering:oscilloscope"),
    ])
    return (13, 4, 9), p


def _pwm_control() -> tuple[tuple[int, int, int], list[Placement]]:
    p = _base("minecraft:orange_concrete")
    p = _overlay(p, [
        ((3, 1, 4), _ref(8)),
        ((4, 1, 4), ("redstoneengineering:pwm_controller", {"facing": "east", "input_facing": "west", "output": "0"})),
        ((5, 1, 4), _indicator("east")),
    ])
    return (13, 4, 9), p


def _noise_vs_filter() -> tuple[tuple[int, int, int], list[Placement]]:
    p = _base("minecraft:magenta_concrete")
    p = _overlay(p, [
        ((2, 1, 3), "redstoneengineering:lapis_noise_source"),
        ((3, 1, 3), "redstoneengineering:lapis_signal_line"),
        ((4, 1, 3), "redstoneengineering:lapis_precision_meter"),
        ((2, 1, 5), "redstoneengineering:lapis_noise_source"),
        ((3, 1, 5), "redstoneengineering:lapis_low_pass_filter"),
        ((4, 1, 5), "redstoneengineering:lapis_signal_line"),
        ((5, 1, 5), "redstoneengineering:lapis_precision_meter"),
    ])
    return (13, 4, 9), p


def _quantizer_scaler() -> tuple[tuple[int, int, int], list[Placement]]:
    p = _base("minecraft:cyan_concrete")
    p = _overlay(p, [
        ((2, 1, 4), _ref(9)),
        ((3, 1, 4), "redstoneengineering:redstone_to_lapis_scaler"),
        ((4, 1, 4), "redstoneengineering:lapis_signal_line"),
        ((5, 1, 4), "redstoneengineering:lapis_to_redstone_quantizer"),
        ((6, 1, 4), _indicator("east")),
    ])
    return (13, 4, 9), p


SELFTESTS: dict[str, SelfTest] = {
    "01_basic/reference_source": SelfTest("Reference Source", "L1", "Verify a configured 0..15 source emits on its selected face.", "reference_source", _reference_source, PANEL, (3, 1, 4), (3, 1, 4), (4, 1, 4), 7, 2),
    "01_basic/signal_probe": SelfTest("Signal Probe", "L1", "Verify non-invasive VALID measurement of a powered node.", "signal_probe", _signal_probe, PANEL, (3, 1, 4), (4, 1, 4), (4, 1, 4), 9, 2),
    "01_basic/signal_analyzer_tap": SelfTest("Analyzer TAP", "L1", "Verify TAP measurement without inline conduction.", "signal_analyzer_tap", _signal_analyzer_tap, PANEL, (3, 1, 4), (4, 1, 4), (4, 1, 4), 7, 8),
    "01_basic/signal_analyzer_inline": SelfTest("Analyzer INLINE", "L2", "Verify INLINE measurement and pass-through.", "signal_analyzer_inline", _signal_analyzer_inline, PANEL, (3, 1, 4), (4, 1, 4), (6, 1, 4), 7, 8),
    "01_basic/analog_indicator": SelfTest("Analog Indicator", "L1", "Verify VALID input quality and displayed level.", "analog_indicator", _analog_indicator, PANEL, (3, 1, 4), (5, 1, 4), (5, 1, 4), 11, 2),
    "01_basic/signal_conditioner_gain": SelfTest("Conditioner Gain x2", "L2", "Verify 6 maps to 12 without saturation.", "conditioner_gain", _signal_conditioner_gain, PANEL, (3, 1, 4), (4, 1, 4), (6, 1, 4), 12, 4),
    "01_basic/directional_io": SelfTest("Directional I/O", "L2", "Verify correct-face transfer and wrong-face rejection.", "directional_io", _directional_io, PANEL, (2, 1, 3), (3, 1, 3), (4, 1, 3), 8, 4),
    "01_basic/instrument_bus": SelfTest("Instrument Bus", "L2", "Verify probe channel appears on the instrument network.", "instrument_bus", _instrument_bus, PANEL, (2, 1, 4), (3, 1, 4), (5, 1, 4), 5, 8),
    "02_signal/conditioner_saturation": SelfTest("Conditioner Saturation", "L2", "Verify expected clamp to 15 is reported as SATURATED.", "conditioner_saturation", _conditioner_saturation, PANEL, (3, 1, 4), (4, 1, 4), (5, 1, 4), 15, 4),
    "02_signal/precision_filter": SelfTest("Precision Filter", "L2", "Verify a valid precision signal survives the filter path.", "precision_filter", _precision_filter, PANEL, (2, 1, 4), (4, 1, 4), (6, 1, 4), None, 8),
    "02_signal/sample_hold": SelfTest("Sample/Hold", "L3", "Verify held output remains coherent after sampling.", "sample_hold", _sample_hold, PANEL, (2, 1, 4), (4, 1, 4), (6, 1, 4), None, 20),
    "02_signal/edge_detector": SelfTest("Edge Detector", "L2", "Verify edge evidence appears only after an input transition.", "edge_detector", _edge_detector, PANEL, (3, 1, 4), (4, 1, 4), (5, 1, 4), None, 8),
    "02_signal/pulse_shaper": SelfTest("Pulse Shaper", "L3", "Verify a detected edge becomes bounded pulse evidence.", "pulse_shaper", _pulse_shaper, PANEL, (2, 1, 4), (4, 1, 4), (6, 1, 4), None, 12),
    "02_signal/pwm_control": SelfTest("PWM Control", "L3", "Verify bounded toggling over a sufficient runtime window.", "pwm_control", _pwm_control, PANEL, (3, 1, 4), (4, 1, 4), (5, 1, 4), None, 40),
    "02_signal/noise_vs_filter": SelfTest("Noise vs Filter", "L3", "Verify filtered peak-to-peak is lower than raw noise.", "noise_vs_filter", _noise_vs_filter, PANEL, (2, 1, 3), (3, 1, 5), (5, 1, 5), None, 40),
    "02_signal/quantizer_scaler": SelfTest("Scaler/Quantizer Round Trip", "L3", "Verify redstone->lapis->redstone stays within quantization tolerance.", "quantizer_scaler", _quantizer_scaler, PANEL, (2, 1, 4), (4, 1, 4), (6, 1, 4), 9, 8),
}
