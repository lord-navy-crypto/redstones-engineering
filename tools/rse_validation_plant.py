from __future__ import annotations

from dataclasses import dataclass
from typing import Callable

BlockSpec = str | tuple[str, dict[str, str]]
Placement = tuple[tuple[int, int, int], BlockSpec]
Builder = Callable[[], tuple[tuple[int, int, int], list[Placement]]]

CELL_SIZE = (19, 5, 13)
CONTROL_ROOM_SIZE = (27, 5, 13)


@dataclass(frozen=True)
class StatusPanel:
    wait_lamp: tuple[int, int, int]
    pass_lamp: tuple[int, int, int]
    fail_lamp: tuple[int, int, int]
    wait_power: tuple[int, int, int]
    pass_power: tuple[int, int, int]
    fail_power: tuple[int, int, int]


LOCAL_STATUS_PANEL = StatusPanel(
    wait_lamp=(14, 2, 2), pass_lamp=(15, 2, 2), fail_lamp=(16, 2, 2),
    wait_power=(14, 2, 3), pass_power=(15, 2, 3), fail_power=(16, 2, 3),
)

MASTER_STATUS_PANEL = StatusPanel(
    wait_lamp=(11, 2, 8), pass_lamp=(12, 2, 8), fail_lamp=(13, 2, 8),
    wait_power=(11, 2, 9), pass_power=(12, 2, 9), fail_power=(13, 2, 9),
)
MASTER_RETEST_BUTTON = (2, 1, 10)

CELL_STATUS_PANELS: dict[str, StatusPanel] = {
    cell: StatusPanel(
        wait_lamp=(x, 2, 4), pass_lamp=(x + 1, 2, 4), fail_lamp=(x + 2, 2, 4),
        wait_power=(x, 2, 5), pass_power=(x + 1, 2, 5), fail_power=(x + 2, 2, 5),
    )
    for cell, x in zip("ABCDEF", (2, 6, 10, 14, 18, 22), strict=True)
}

PLANT_MODULE_OFFSETS: dict[str, tuple[int, int, int]] = {
    "control_room": (15, 0, 0),
    "cell_a_acquisition": (0, 0, 16),
    "cell_b_conditioning": (19, 0, 16),
    "cell_c_instrumentation": (38, 0, 16),
    "cell_d_control": (38, 0, 29),
    "cell_e_safety": (19, 0, 29),
    "cell_f_process": (0, 0, 29),
}

PLANT_STRUCTURE_ORDER = (
    "control_room",
    "cell_a_acquisition",
    "cell_b_conditioning",
    "cell_c_instrumentation",
    "cell_d_control",
    "cell_e_safety",
    "cell_f_process",
)


def _overlay(base: list[Placement], additions: list[Placement]) -> list[Placement]:
    merged: dict[tuple[int, int, int], BlockSpec] = {pos: block for pos, block in base}
    for pos, block in additions:
        merged[pos] = block
    return [(pos, merged[pos]) for pos in sorted(merged)]


def _floor(width: int, depth: int, block: BlockSpec = "minecraft:smooth_stone") -> list[Placement]:
    return [((x, 0, z), block) for x in range(width) for z in range(depth)]


def _border(width: int, depth: int, block: BlockSpec) -> list[Placement]:
    result: list[Placement] = []
    for x in range(width):
        result.append(((x, 0, 0), block))
        result.append(((x, 0, depth - 1), block))
    for z in range(1, depth - 1):
        result.append(((0, 0, z), block))
        result.append(((width - 1, 0, z), block))
    return result


def _panel_blocks(panel: StatusPanel) -> list[Placement]:
    return [
        (panel.wait_lamp, "minecraft:redstone_lamp"),
        (panel.pass_lamp, "minecraft:redstone_lamp"),
        (panel.fail_lamp, "minecraft:redstone_lamp"),
        (panel.wait_power, "minecraft:redstone_block"),
        (panel.pass_power, "minecraft:air"),
        (panel.fail_power, "minecraft:air"),
    ]


def _cell_base(marker: BlockSpec) -> list[Placement]:
    width, _height, depth = CELL_SIZE
    base = _overlay(_floor(width, depth), _border(width, depth, marker))
    return _overlay(base, [
        ((1, 1, 1), "minecraft:sea_lantern"),
        ((17, 1, 11), "minecraft:sea_lantern"),
        *_panel_blocks(LOCAL_STATUS_PANEL),
    ])


def _ref(power: int, facing: str) -> BlockSpec:
    return "redstoneengineering:redstone_reference_source", {"facing": facing, "power": str(power)}


def _series(block_id: str, facing: str, input_facing: str, **extra: str) -> BlockSpec:
    props = {"facing": facing, "input_facing": input_facing, "output": "0"}
    props.update(extra)
    return block_id, props


def _unity_buffer(facing: str, input_facing: str) -> BlockSpec:
    return _series("redstoneengineering:signal_conditioner", facing, input_facing, mode="0", param="1")


def _buffer_run_x(x0: int, x1: int, z: int, facing: str) -> list[Placement]:
    lo, hi = sorted((x0, x1))
    input_facing = "west" if facing == "east" else "east"
    return [((x, 1, z), _unity_buffer(facing, input_facing)) for x in range(lo, hi + 1)]


def _buffer_run_z(x: int, z0: int, z1: int, facing: str) -> list[Placement]:
    lo, hi = sorted((z0, z1))
    input_facing = "north" if facing == "south" else "south"
    return [((x, 1, z), _unity_buffer(facing, input_facing)) for z in range(lo, hi + 1)]


def _indicator(facing: str) -> BlockSpec:
    return "redstoneengineering:analog_indicator", {"facing": facing, "level": "0"}


def _analyzer(facing: str, mode: int) -> BlockSpec:
    return "redstoneengineering:signal_analyzer", {
        "facing": facing, "mode": str(mode), "output": "0", "calibration": "2"
    }


def _control_room() -> tuple[tuple[int, int, int], list[Placement]]:
    width, _height, depth = CONTROL_ROOM_SIZE
    p = _overlay(_floor(width, depth, "minecraft:polished_andesite"), _border(width, depth, "minecraft:black_concrete"))
    additions: list[Placement] = [
        ((1, 1, 1), "redstoneengineering:engineering_compass"),
        (MASTER_RETEST_BUTTON, ("minecraft:stone_button", {"face": "floor", "facing": "north", "powered": "false"})),
        *_panel_blocks(MASTER_STATUS_PANEL),
    ]
    for panel in CELL_STATUS_PANELS.values():
        additions.extend(_panel_blocks(panel))
    return CONTROL_ROOM_SIZE, _overlay(p, additions)


def _cell_a_acquisition() -> tuple[tuple[int, int, int], list[Placement]]:
    # A single wire tap provides a physical probe point; unity conditioners then regenerate the exact analog value.
    p = _cell_base("minecraft:light_blue_concrete")
    p = _overlay(p, [
        ((2, 1, 6), _ref(6, "east")),
        ((3, 1, 6), "minecraft:redstone_wire"),
        *_buffer_run_x(4, 18, 6, "east"),
        ((3, 1, 5), ("redstoneengineering:signal_probe", {"facing": "south", "channel": "0"})),
        ((3, 1, 4), "redstoneengineering:instrument_cable"),
        ((3, 1, 3), "redstoneengineering:oscilloscope"),
    ])
    return CELL_SIZE, p


def _cell_b_conditioning() -> tuple[tuple[int, int, int], list[Placement]]:
    p = _cell_base("minecraft:green_concrete")
    p = _overlay(p, [
        *_buffer_run_x(0, 2, 6, "east"),
        ((3, 1, 6), _series("redstoneengineering:signal_conditioner", "east", "west", mode="0", param="2")),
        ((4, 1, 6), _series("redstoneengineering:precision_filter", "east", "west", rate="1")),
        *_buffer_run_x(5, 7, 6, "east"),
        ((8, 1, 6), "minecraft:redstone_wire"),
        *_buffer_run_x(9, 18, 6, "east"),
        ((8, 1, 5), _indicator("north")),
    ])
    return CELL_SIZE, p


def _cell_c_instrumentation() -> tuple[tuple[int, int, int], list[Placement]]:
    p = _cell_base("minecraft:cyan_concrete")
    p = _overlay(p, [
        *_buffer_run_x(0, 2, 6, "east"),
        ((3, 1, 6), _analyzer("west", 1)),
        *_buffer_run_x(4, 7, 6, "east"),
        ((8, 1, 6), "minecraft:redstone_wire"),
        *_buffer_run_x(9, 17, 6, "east"),
        ((18, 1, 6), _unity_buffer("south", "west")),
        *_buffer_run_z(18, 7, 12, "south"),
        ((8, 1, 5), _indicator("north")),
    ])
    return CELL_SIZE, p


def _cell_d_control() -> tuple[tuple[int, int, int], list[Placement]]:
    # The main analog command is actively regenerated. One explicit wire tap feeds the PWM diagnostic branch.
    p = _cell_base("minecraft:purple_concrete")
    p = _overlay(p, [
        *_buffer_run_z(18, 0, 5, "south"),
        ((18, 1, 6), _unity_buffer("west", "north")),
        *_buffer_run_x(14, 17, 6, "west"),
        ((13, 1, 6), "minecraft:redstone_wire"),
        *_buffer_run_x(0, 12, 6, "west"),
        ((13, 1, 5), "minecraft:redstone_wire"),
        ((12, 1, 5), _series("redstoneengineering:pwm_controller", "west", "east", period_mode="2", invert="false")),
        ((11, 1, 5), _analyzer("east", 0)),
    ])
    return CELL_SIZE, p


def _cell_e_safety() -> tuple[tuple[int, int, int], list[Placement]]:
    p = _cell_base("minecraft:red_concrete")
    p = _overlay(p, [
        # Main analog command path: east -> fault injector -> west, with active unity regeneration.
        *_buffer_run_x(15, 18, 6, "west"),
        ((14, 1, 6), _series("redstoneengineering:fault_injector", "west", "east", mode="2")),
        *_buffer_run_x(0, 13, 6, "west"),
        ((14, 1, 5), _ref(0, "south")),

        # Independent safety-permit path exits west toward Cell F and preserves exact 15/0 semantics.
        ((10, 1, 9), _ref(15, "west")),
        ((9, 1, 10), _ref(15, "north")),
        ((9, 1, 8), _ref(15, "south")),
        ((9, 1, 9), _series("redstoneengineering:safety_interlock", "west", "east")),
        *_buffer_run_x(0, 8, 9, "west"),

        # Alarm lifecycle is driven by validation-owned condition/ACK/RESET sources.
        ((10, 1, 3), _ref(0, "west")),
        ((9, 1, 4), _ref(0, "north")),
        ((9, 1, 2), _ref(0, "south")),
        ((9, 1, 3), _series("redstoneengineering:alarm_processor", "west", "east", severity="2")),
        ((8, 1, 3), _indicator("west")),
    ])
    return CELL_SIZE, p


def _cell_f_process() -> tuple[tuple[int, int, int], list[Placement]]:
    p = _cell_base("minecraft:orange_concrete")
    p = _overlay(p, [
        # Stable analog command arrives from Cell E. The one wire tap powers a visible process lamp.
        *_buffer_run_x(17, 18, 6, "west"),
        ((16, 1, 6), "minecraft:redstone_wire"),
        ((16, 1, 7), "minecraft:redstone_lamp"),
        ((15, 1, 6), _unity_buffer("west", "east")),
        ((14, 1, 6), ("redstoneengineering:servo_actuator", {"facing": "west", "slew": "2"})),
        ((13, 1, 6), _series("redstoneengineering:servo_position_sensor", "west", "east")),
        ((12, 1, 6), "minecraft:redstone_wire"),
        ((11, 1, 6), _unity_buffer("west", "east")),
        ((10, 1, 6), _unity_buffer("west", "east")),
        ((9, 1, 6), _indicator("west")),

        # Safety permit arrives independently and is actively regenerated to retain exact permit semantics.
        *_buffer_run_x(15, 18, 9, "west"),
        ((14, 1, 9), _indicator("west")),
    ])
    return CELL_SIZE, p


PLANT_STRUCTURES: dict[str, Builder] = {
    "control_room": _control_room,
    "cell_a_acquisition": _cell_a_acquisition,
    "cell_b_conditioning": _cell_b_conditioning,
    "cell_c_instrumentation": _cell_c_instrumentation,
    "cell_d_control": _cell_d_control,
    "cell_e_safety": _cell_e_safety,
    "cell_f_process": _cell_f_process,
}
