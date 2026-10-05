from __future__ import annotations

from dataclasses import dataclass
import json
from typing import Callable

NbtValue = bool | int | str | list[str] | dict[str, object]
BlockSpec = str | tuple[str, dict[str, str]] | tuple[str, dict[str, str], dict[str, NbtValue]]
Placement = tuple[tuple[int, int, int], BlockSpec]
Builder = Callable[[], tuple[tuple[int, int, int], list[Placement]]]

CELL_SIZE = (19, 5, 13)
CONTROL_ROOM_SIZE = (27, 5, 13)
SERVICE_SPINE_SIZE = (57, 5, 3)


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
    "service_spine": (0, 0, 13),
    "cell_a_acquisition": (0, 0, 16),
    "cell_b_conditioning": (19, 0, 16),
    "cell_c_instrumentation": (38, 0, 16),
    "cell_d_control": (38, 0, 29),
    "cell_e_safety": (19, 0, 29),
    "cell_f_process": (0, 0, 29),
}

PLANT_STRUCTURE_ORDER = (
    "control_room",
    "service_spine",
    "cell_a_acquisition",
    "cell_b_conditioning",
    "cell_c_instrumentation",
    "cell_d_control",
    "cell_e_safety",
    "cell_f_process",
)

CELL_SIGN_TEXT: dict[str, tuple[str, str, str, str]] = {
    "A": ("CELL A", "ACQUISITION", "SOURCE + PROBE", "EXPECT 6 VALID"),
    "B": ("CELL B", "CONDITIONING", "GAIN x2 + FILTER", "SATURATION TEST"),
    "C": ("CELL C", "INSTRUMENT", "INLINE ANALYZER", "READBACK CHECK"),
    "D": ("CELL D", "CONTROL", "PWM BRANCH", "ACTIVITY CHECK"),
    "E": ("CELL E", "SAFETY + FAULT", "INTERLOCK + ALARM", "TRIP RECOVER"),
    "F": ("CELL F", "PROCESS", "SERVO + SENSOR", "FEEDBACK CHECK"),
}


def _overlay(base: list[Placement], additions: list[Placement]) -> list[Placement]:
    merged: dict[tuple[int, int, int], BlockSpec] = {pos: block for pos, block in base}
    for pos, block in additions:
        merged[pos] = block
    return [(pos, merged[pos]) for pos in sorted(merged)]


def _clear_volume(size: tuple[int, int, int]) -> list[Placement]:
    width, height, depth = size
    return [((x, y, z), "minecraft:air") for x in range(width) for y in range(height) for z in range(depth)]


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


def _json_text(line: str) -> str:
    return json.dumps({"text": line}, separators=(",", ":"), ensure_ascii=False)


def _sign(lines: tuple[str, ...] | list[str], rotation: int = 8, color: str = "black") -> BlockSpec:
    padded = list(lines[:4]) + [""] * max(0, 4 - len(lines))
    messages = [_json_text(line) for line in padded[:4]]
    text = {
        "has_glowing_text": True,
        "color": color,
        "messages": messages,
    }
    return (
        "minecraft:oak_sign",
        {"rotation": str(rotation % 16), "waterlogged": "false"},
        {
            "id": "minecraft:sign",
            "front_text": dict(text),
            "back_text": dict(text),
            "is_waxed": True,
        },
    )


def _perimeter_frame(size: tuple[int, int, int], marker: BlockSpec) -> list[Placement]:
    width, height, depth = size
    additions: list[Placement] = []

    for x, z in ((0, 0), (width - 1, 0), (0, depth - 1), (width - 1, depth - 1)):
        for y in range(1, height):
            additions.append(((x, y, z), "minecraft:polished_deepslate"))

    top = height - 1
    for x in range(width):
        additions.append(((x, top, 0), "minecraft:polished_andesite"))
        additions.append(((x, top, depth - 1), "minecraft:polished_andesite"))
    for z in range(1, depth - 1):
        additions.append(((0, top, z), "minecraft:polished_andesite"))
        additions.append(((width - 1, top, z), "minecraft:polished_andesite"))

    for x in range(width):
        additions.append(((x, top, depth // 2), "minecraft:polished_andesite"))
    for z in range(depth):
        additions.append(((width // 2, top, z), "minecraft:polished_andesite"))

    entrance = {width // 2 - 1, width // 2, width // 2 + 1}
    for x in range(1, width - 1):
        if x not in entrance:
            additions.append(((x, 1, 0), "minecraft:iron_bars"))
            additions.append(((x, 1, depth - 1), "minecraft:iron_bars"))
    for z in range(1, depth - 1):
        if z not in {depth // 2, depth // 2 + 1}:
            additions.append(((0, 1, z), "minecraft:iron_bars"))
            additions.append(((width - 1, 1, z), "minecraft:iron_bars"))

    additions.extend(_border(width, depth, marker))
    return additions


def _panel_blocks(panel: StatusPanel, include_labels: bool = True) -> list[Placement]:
    wait_x, wait_y, wait_z = panel.wait_lamp
    pass_x, pass_y, pass_z = panel.pass_lamp
    fail_x, fail_y, fail_z = panel.fail_lamp
    result: list[Placement] = [
        ((wait_x, wait_y - 1, wait_z), "minecraft:yellow_concrete"),
        ((pass_x, pass_y - 1, pass_z), "minecraft:lime_concrete"),
        ((fail_x, fail_y - 1, fail_z), "minecraft:red_concrete"),
        (panel.wait_lamp, "minecraft:redstone_lamp"),
        (panel.pass_lamp, "minecraft:redstone_lamp"),
        (panel.fail_lamp, "minecraft:redstone_lamp"),
        (panel.wait_power, "minecraft:redstone_block"),
        (panel.pass_power, "minecraft:air"),
        (panel.fail_power, "minecraft:air"),
    ]
    if include_labels:
        result.extend([
            ((wait_x, wait_y + 1, wait_z), _sign(("WAIT", "YELLOW"), rotation=8)),
            ((pass_x, pass_y + 1, pass_z), _sign(("PASS", "GREEN"), rotation=8)),
            ((fail_x, fail_y + 1, fail_z), _sign(("FAIL", "RED"), rotation=8)),
        ])
    return result


def _cell_base(marker: BlockSpec, cell: str) -> list[Placement]:
    width, _height, depth = CELL_SIZE
    base = _clear_volume(CELL_SIZE)
    floor = _overlay(_floor(width, depth, "minecraft:smooth_stone"), _border(width, depth, marker))
    frame = _perimeter_frame(CELL_SIZE, marker)
    lane = [((x, 0, z), "minecraft:light_gray_concrete") for x in range(1, width - 1) for z in (5, 6, 7)]
    additions: list[Placement] = [
        *floor,
        *frame,
        *lane,
        ((1, 1, 1), "minecraft:sea_lantern"),
        ((17, 1, 11), "minecraft:sea_lantern"),
        ((3, 4, 3), "minecraft:sea_lantern"),
        ((15, 4, 9), "minecraft:sea_lantern"),
        ((2, 1, 2), _sign(CELL_SIGN_TEXT[cell], rotation=8)),
        *_panel_blocks(LOCAL_STATUS_PANEL),
    ]
    return _overlay(base, additions)


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
    width, height, depth = CONTROL_ROOM_SIZE
    p = _clear_volume(CONTROL_ROOM_SIZE)
    additions: list[Placement] = [
        *_floor(width, depth, "minecraft:polished_andesite"),
        *_border(width, depth, "minecraft:black_concrete"),
    ]

    for x, z in ((0, 0), (width - 1, 0), (0, depth - 1), (width - 1, depth - 1)):
        for y in range(1, height):
            additions.append(((x, y, z), "minecraft:polished_deepslate"))
    for x in range(width):
        additions.append(((x, height - 1, 0), "minecraft:black_concrete"))
        additions.append(((x, height - 1, depth - 1), "minecraft:black_concrete"))
    for z in range(depth):
        additions.append(((0, height - 1, z), "minecraft:black_concrete"))
        additions.append(((width - 1, height - 1, z), "minecraft:black_concrete"))
    for x in range(1, width - 1):
        for y in range(1, 4):
            additions.append(((x, y, 0), "minecraft:gray_concrete"))
    for z in range(1, depth - 1):
        for y in range(1, 4):
            additions.append(((0, y, z), "minecraft:gray_concrete"))
            additions.append(((width - 1, y, z), "minecraft:gray_concrete"))
    entrance = {width // 2 - 1, width // 2, width // 2 + 1}
    for x in range(1, width - 1):
        if x not in entrance:
            additions.append(((x, 1, depth - 1), "minecraft:iron_bars"))

    additions.extend([
        ((1, 1, 1), "redstoneengineering:engineering_compass"),
        ((12, 1, 1), _sign(("RSE VALIDATION", "PLANT v1.1", "CONTROL ROOM", "A-F + MASTER"), rotation=8)),
        ((11, 1, 6), _sign(("MASTER STATUS", "WAIT = YELLOW", "PASS = GREEN", "FAIL = RED"), rotation=8)),
        ((5, 1, 10), _sign(("DIAGNOSTICS", "RUN plant status", "REPORT PHASE", "CELL + DETAIL"), rotation=8)),
        (MASTER_RETEST_BUTTON, ("minecraft:stone_button", {"face": "floor", "facing": "north", "powered": "false"})),
        ((3, 1, 10), _sign(("MASTER RETEST", "PRESS BUTTON", "REBUILDS PLANT", "STARTS PRECHECK"), rotation=8)),
        *_panel_blocks(MASTER_STATUS_PANEL),
        ((4, 4, 3), "minecraft:sea_lantern"),
        ((13, 4, 3), "minecraft:sea_lantern"),
        ((22, 4, 3), "minecraft:sea_lantern"),
    ])

    for cell, panel in CELL_STATUS_PANELS.items():
        additions.extend(_panel_blocks(panel, include_labels=False))
        x = panel.pass_lamp[0]
        additions.append(((x, 1, 2), _sign((f"CELL {cell}", "STATUS", "YELLOW WAIT", "GREEN/RED"), rotation=8)))

    return CONTROL_ROOM_SIZE, _overlay(p, additions)


def _service_spine() -> tuple[tuple[int, int, int], list[Placement]]:
    width, height, depth = SERVICE_SPINE_SIZE
    p = _clear_volume(SERVICE_SPINE_SIZE)
    additions: list[Placement] = []
    for x in range(width):
        floor_block = "minecraft:white_concrete" if x % 6 == 0 else "minecraft:light_gray_concrete"
        for z in range(depth):
            additions.append(((x, 0, z), floor_block))
        additions.append(((x, height - 1, 0), "minecraft:polished_andesite"))
        additions.append(((x, height - 1, depth - 1), "minecraft:polished_andesite"))
    for x in (0, 14, 28, 42, 56):
        for z in (0, depth - 1):
            for y in range(1, height):
                additions.append(((x, y, z), "minecraft:polished_deepslate"))
    for x in range(3, width - 3, 7):
        additions.append(((x, height - 1, 1), "minecraft:sea_lantern"))
    additions.extend([
        ((27, 1, 1), _sign(("SERVICE SPINE", "CONTROL ROOM", "NORTH", "CELLS A B C SOUTH"), rotation=8)),
        ((8, 1, 1), _sign(("CELL A", "LEFT BAY", "ACQUISITION", "FOLLOW SIGNS"), rotation=8)),
        ((27, 1, 1), _sign(("SERVICE SPINE", "CONTROL ROOM", "CENTER", "CELLS A B C"), rotation=8)),
        ((46, 1, 1), _sign(("CELL C", "RIGHT BAY", "INSTRUMENT", "FOLLOW SIGNS"), rotation=8)),
    ])
    return SERVICE_SPINE_SIZE, _overlay(p, additions)


def _cell_a_acquisition() -> tuple[tuple[int, int, int], list[Placement]]:
    p = _cell_base("minecraft:light_blue_concrete", "A")
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
    p = _cell_base("minecraft:green_concrete", "B")
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
    p = _cell_base("minecraft:cyan_concrete", "C")
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
    # Branch one block north twice: WEST-facing PWM uses SOUTH as INHIBIT, so z=5 must remain air.
    p = _cell_base("minecraft:purple_concrete", "D")
    p = _overlay(p, [
        *_buffer_run_z(18, 0, 5, "south"),
        ((18, 1, 6), _unity_buffer("west", "north")),
        *_buffer_run_x(14, 17, 6, "west"),
        ((13, 1, 6), "minecraft:redstone_wire"),
        *_buffer_run_x(0, 12, 6, "west"),
        ((13, 1, 5), "minecraft:redstone_wire"),
        ((13, 1, 4), "minecraft:redstone_wire"),
        ((12, 1, 4), _series("redstoneengineering:pwm_controller", "west", "east", period_mode="2", invert="false")),
        ((11, 1, 4), _analyzer("east", 0)),
        ((10, 1, 3), _sign(("PWM DIAGNOSTIC", "COMMAND=EAST", "OUTPUT=WEST", "SOUTH INHIBIT CLEAR"), rotation=8)),
    ])
    return CELL_SIZE, p


def _cell_e_safety() -> tuple[tuple[int, int, int], list[Placement]]:
    p = _cell_base("minecraft:red_concrete", "E")
    p = _overlay(p, [
        *_buffer_run_x(15, 18, 6, "west"),
        ((14, 1, 6), _series("redstoneengineering:fault_injector", "west", "east", mode="2")),
        *_buffer_run_x(0, 13, 6, "west"),
        ((14, 1, 5), _ref(0, "south")),
        ((10, 1, 9), _ref(15, "west")),
        ((9, 1, 10), _ref(15, "north")),
        ((9, 1, 8), _ref(15, "south")),
        ((9, 1, 9), _series("redstoneengineering:safety_interlock", "west", "east")),
        *_buffer_run_x(0, 8, 9, "west"),
        ((10, 1, 3), _ref(0, "west")),
        ((9, 1, 4), _ref(0, "north")),
        ((9, 1, 2), _ref(0, "south")),
        ((9, 1, 3), _series("redstoneengineering:alarm_processor", "west", "east", severity="2")),
        ((8, 1, 3), _indicator("west")),
    ])
    return CELL_SIZE, p


def _cell_f_process() -> tuple[tuple[int, int, int], list[Placement]]:
    p = _cell_base("minecraft:orange_concrete", "F")
    p = _overlay(p, [
        *_buffer_run_x(17, 18, 6, "west"),
        ((16, 1, 6), "minecraft:redstone_wire"),
        ((16, 1, 7), "minecraft:redstone_lamp"),
        ((15, 1, 6), _unity_buffer("west", "east")),
        ((14, 1, 6), ("redstoneengineering:servo_actuator", {"facing": "west", "slew": "2"})),
        # WEST-facing servo uses NORTH as BRAKE. This validation-owned source is 0 normally and 15 on trip.
        ((14, 1, 5), _ref(0, "south")),
        ((16, 1, 5), _sign(("SERVO BRAKE", "TEST SOURCE", "0 = NORMAL", "15 = TRIP"), rotation=8)),
        ((13, 1, 6), _series("redstoneengineering:servo_position_sensor", "west", "east")),
        ((12, 1, 6), "minecraft:redstone_wire"),
        ((11, 1, 6), _unity_buffer("west", "east")),
        ((10, 1, 6), _unity_buffer("west", "east")),
        ((9, 1, 6), _indicator("west")),
        *_buffer_run_x(15, 18, 9, "west"),
        ((14, 1, 9), _indicator("west")),
    ])
    return CELL_SIZE, p


PLANT_STRUCTURES: dict[str, Builder] = {
    "control_room": _control_room,
    "service_spine": _service_spine,
    "cell_a_acquisition": _cell_a_acquisition,
    "cell_b_conditioning": _cell_b_conditioning,
    "cell_c_instrumentation": _cell_c_instrumentation,
    "cell_d_control": _cell_d_control,
    "cell_e_safety": _cell_e_safety,
    "cell_f_process": _cell_f_process,
}
