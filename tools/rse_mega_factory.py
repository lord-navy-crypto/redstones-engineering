from __future__ import annotations

from dataclasses import dataclass
import json
from typing import Callable

NbtValue = bool | int | str | list[str] | dict[str, object]
BlockSpec = str | tuple[str, dict[str, str]] | tuple[str, dict[str, str], dict[str, NbtValue]]
Placement = tuple[tuple[int, int, int], BlockSpec]
Builder = Callable[[], tuple[tuple[int, int, int], list[Placement]]]

CELL_SIZE = (31, 7, 25)
CONTROL_HALL_SIZE = (63, 7, 17)
SPINE_SIZE = (133, 7, 3)
STATION_X = (3, 9, 15, 21, 27)
DUT_POS_OVERRIDES: dict[int, tuple[int, int, int]] = {
    # D38 must be physically adjacent to the D37 servo's mechanical FRONT.
    38: (10, 1, 12),
}


@dataclass(frozen=True)
class StatusPanel:
    wait_lamp: tuple[int, int, int]
    pass_lamp: tuple[int, int, int]
    fail_lamp: tuple[int, int, int]
    wait_power: tuple[int, int, int]
    pass_power: tuple[int, int, int]
    fail_power: tuple[int, int, int]


@dataclass(frozen=True)
class StationSpec:
    number: int
    cell: str
    block_id: str
    short_name: str
    role: str
    module_id: str
    dut_pos: tuple[int, int, int]
    sign_pos: tuple[int, int, int]
    panel: StatusPanel


MEGA_MASTER_PANEL = StatusPanel(
    wait_lamp=(50, 2, 13), pass_lamp=(51, 2, 13), fail_lamp=(52, 2, 13),
    wait_power=(50, 2, 14), pass_power=(51, 2, 14), fail_power=(52, 2, 14),
)
MEGA_MASTER_RETEST_BUTTON = (58, 1, 14)
LOCAL_CELL_PANEL = StatusPanel(
    wait_lamp=(13, 2, 21), pass_lamp=(14, 2, 21), fail_lamp=(15, 2, 21),
    wait_power=(13, 2, 22), pass_power=(14, 2, 22), fail_power=(15, 2, 22),
)


def _station_panel(x: int) -> StatusPanel:
    return StatusPanel(
        wait_lamp=(x - 1, 2, 4), pass_lamp=(x, 2, 4), fail_lamp=(x + 1, 2, 4),
        wait_power=(x - 1, 2, 5), pass_power=(x, 2, 5), fail_power=(x + 1, 2, 5),
    )


CELL_DEFINITIONS: dict[str, tuple[str, str, tuple[tuple[str, str, str], ...]]] = {
    "A": ("mega_cell_a_analog", "ANALOG METROLOGY", (
        ("redstone_reference_source", "REF SOURCE", "known analog stimulus"),
        ("signal_probe", "SIGNAL PROBE", "non-invasive observation"),
        ("signal_conditioner", "CONDITIONER", "gain and quality"),
        ("precision_filter", "PREC FILTER", "filtered analog path"),
        ("signal_analyzer", "ANALYZER", "sampled analog evidence"),
    )),
    "B": ("mega_cell_b_timing", "TIMING + WAVEFORM", (
        ("quartz_lab_oscillator", "QUARTZ OSC", "clock generation"),
        ("quartz_clock_divider", "CLOCK DIV", "clock division"),
        ("edge_detector", "EDGE DETECT", "transition detection"),
        ("pulse_shaper", "PULSE SHAPE", "pulse conditioning"),
        ("pwm_controller", "PWM CTRL", "duty-cycle output"),
    )),
    "C": ("mega_cell_c_precision", "PRECISION SIGNAL", (
        ("lapis_noise_source", "NOISE SOURCE", "noise stimulus"),
        ("lapis_low_pass_filter", "LAPIS LPF", "noise filtering"),
        ("lapis_precision_meter", "PREC METER", "precision measurement"),
        ("quartz_triggered_lapis_sampler", "QZ SAMPLER", "clocked sampling"),
        ("lapis_to_redstone_quantizer", "QUANTIZER", "precision conversion"),
    )),
    "D": ("mega_cell_d_data", "DIGITAL DATA", (
        ("redstone_byte_encoder", "BYTE ENC", "scalar-to-byte bridge"),
        ("eight_bit_data_bus", "8BIT BUS", "parallel data transport"),
        ("serializer", "SERIALIZER", "parallel-to-serial"),
        ("serial_data_line", "SERIAL LINE", "serial transport"),
        ("deserializer", "DESERIALIZER", "serial-to-parallel"),
    )),
    "E": ("mega_cell_e_comms", "ROBUST COMMS", (
        ("differential_driver", "DIFF DRIVER", "differential transmit"),
        ("differential_data_pair", "DIFF PAIR", "balanced medium"),
        ("digital_regenerator", "REGENERATOR", "signal regeneration"),
        ("differential_receiver", "DIFF RX", "differential receive"),
        ("watchdog", "WATCHDOG", "freshness supervision"),
    )),
    "F": ("mega_cell_f_optical", "OPTICAL LINK", (
        ("optical_emitter", "OPT EMITTER", "electro-optical source"),
        ("optical_fiber", "OPT FIBER", "guided optical path"),
        ("optical_splitter", "OPT SPLITTER", "optical branch"),
        ("optical_channel_filter", "OPT FILTER", "channel selectivity"),
        ("optical_receiver", "OPT RX", "optical receive"),
    )),
    "G": ("mega_cell_g_pneumatic", "PNEUMATIC PROCESS", (
        ("air_compressor", "COMPRESSOR", "air generation"),
        ("air_reservoir", "RESERVOIR", "pressure storage"),
        ("pressure_regulator", "REGULATOR", "pressure control"),
        ("pneumatic_proportional_valve", "PROP VALVE", "flow modulation"),
        ("pneumatic_cylinder", "CYLINDER", "linear actuation"),
    )),
    "H": ("mega_cell_h_control", "CONTROL + SAFETY", (
        ("pid_controller", "PID CTRL", "closed-loop control"),
        ("servo_actuator", "SERVO", "position actuation"),
        ("servo_position_sensor", "POS SENSOR", "position feedback"),
        ("safety_interlock", "INTERLOCK", "permissive safety"),
        ("alarm_processor", "ALARM", "latched alarm lifecycle"),
    )),
}


MEGA_MODULE_OFFSETS: dict[str, tuple[int, int, int]] = {
    "mega_control_hall": (35, 0, 0),
    "mega_north_spine": (0, 0, 17),
    "mega_cross_spine": (0, 0, 45),
    "mega_cell_a_analog": (0, 0, 20),
    "mega_cell_b_timing": (34, 0, 20),
    "mega_cell_c_precision": (68, 0, 20),
    "mega_cell_d_data": (102, 0, 20),
    "mega_cell_e_comms": (102, 0, 48),
    "mega_cell_f_optical": (68, 0, 48),
    "mega_cell_g_pneumatic": (34, 0, 48),
    "mega_cell_h_control": (0, 0, 48),
}
MEGA_MODULE_SIZES: dict[str, tuple[int, int, int]] = {
    "mega_control_hall": CONTROL_HALL_SIZE,
    "mega_north_spine": SPINE_SIZE,
    "mega_cross_spine": SPINE_SIZE,
    **{module_id: CELL_SIZE for module_id, _title, _stations in CELL_DEFINITIONS.values()},
}
MEGA_STRUCTURE_ORDER = tuple(MEGA_MODULE_OFFSETS)


_stations: list[StationSpec] = []
_number = 1
for _cell, (_module, _title, _defs) in CELL_DEFINITIONS.items():
    for _x, (_block, _short, _role) in zip(STATION_X, _defs, strict=True):
        _stations.append(StationSpec(
            number=_number,
            cell=_cell,
            block_id=f"redstoneengineering:{_block}",
            short_name=_short,
            role=_role,
            module_id=_module,
            dut_pos=DUT_POS_OVERRIDES.get(_number, (_x, 1, 12)),
            sign_pos=(_x, 1, 7),
            panel=_station_panel(_x),
        ))
        _number += 1
MEGA_STATIONS = tuple(_stations)
MEGA_CELL_STATIONS = {
    cell: tuple(station for station in MEGA_STATIONS if station.cell == cell)
    for cell in CELL_DEFINITIONS
}


CONTROL_STATION_PANELS: dict[int, StatusPanel] = {}
for station in MEGA_STATIONS:
    row = (station.number - 1) // 5
    col = (station.number - 1) % 5
    x = 3 + col * 6
    z = 1 + row * 2
    CONTROL_STATION_PANELS[station.number] = StatusPanel(
        wait_lamp=(x - 1, 2, z), pass_lamp=(x, 2, z), fail_lamp=(x + 1, 2, z),
        wait_power=(x - 1, 2, z + 1), pass_power=(x, 2, z + 1), fail_power=(x + 1, 2, z + 1),
    )

CONTROL_CELL_PANELS: dict[str, StatusPanel] = {}
for index, cell in enumerate(CELL_DEFINITIONS):
    z = 1 + index * 2
    CONTROL_CELL_PANELS[cell] = StatusPanel(
        wait_lamp=(39, 2, z), pass_lamp=(40, 2, z), fail_lamp=(41, 2, z),
        wait_power=(39, 2, z + 1), pass_power=(40, 2, z + 1), fail_power=(41, 2, z + 1),
    )


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


def _json_text(line: str) -> str:
    return json.dumps({"text": line}, separators=(",", ":"), ensure_ascii=False)


def _sign(lines: tuple[str, ...] | list[str], rotation: int = 8, color: str = "black") -> BlockSpec:
    padded = list(lines[:4]) + [""] * max(0, 4 - len(lines))
    messages = [_json_text(line) for line in padded[:4]]
    text = {"has_glowing_text": True, "color": color, "messages": messages}
    return (
        "minecraft:oak_sign",
        {"rotation": str(rotation % 16), "waterlogged": "false"},
        {"id": "minecraft:sign", "front_text": dict(text), "back_text": dict(text), "is_waxed": True},
    )


def _panel_blocks(panel: StatusPanel, labels: bool = False) -> list[Placement]:
    wx, wy, wz = panel.wait_lamp
    px, py, pz = panel.pass_lamp
    fx, fy, fz = panel.fail_lamp
    blocks: list[Placement] = [
        ((wx, wy - 1, wz), "minecraft:yellow_concrete"),
        ((px, py - 1, pz), "minecraft:lime_concrete"),
        ((fx, fy - 1, fz), "minecraft:red_concrete"),
        (panel.wait_lamp, "minecraft:redstone_lamp"),
        (panel.pass_lamp, "minecraft:redstone_lamp"),
        (panel.fail_lamp, "minecraft:redstone_lamp"),
        (panel.wait_power, "minecraft:redstone_block"),
        (panel.pass_power, "minecraft:air"),
        (panel.fail_power, "minecraft:air"),
    ]
    if labels:
        blocks.extend([
            ((wx, wy + 1, wz), _sign(("WAIT", "YELLOW"))),
            ((px, py + 1, pz), _sign(("PASS", "GREEN"))),
            ((fx, fy + 1, fz), _sign(("FAIL", "RED"))),
        ])
    return blocks


def _frame(size: tuple[int, int, int], marker: BlockSpec) -> list[Placement]:
    width, height, depth = size
    top = height - 1
    blocks: list[Placement] = []
    for x, z in ((0, 0), (width - 1, 0), (0, depth - 1), (width - 1, depth - 1)):
        for y in range(1, height):
            blocks.append(((x, y, z), "minecraft:polished_deepslate"))
    for x in range(width):
        blocks.append(((x, top, 0), "minecraft:polished_andesite"))
        blocks.append(((x, top, depth - 1), "minecraft:polished_andesite"))
    for z in range(1, depth - 1):
        blocks.append(((0, top, z), "minecraft:polished_andesite"))
        blocks.append(((width - 1, top, z), "minecraft:polished_andesite"))
    for x in range(width):
        blocks.append(((x, top, depth // 2), "minecraft:polished_andesite"))
    for z in range(depth):
        blocks.append(((width // 2, top, z), "minecraft:polished_andesite"))
    for x in range(width):
        blocks.append(((x, 0, 0), marker))
        blocks.append(((x, 0, depth - 1), marker))
    for z in range(1, depth - 1):
        blocks.append(((0, 0, z), marker))
        blocks.append(((width - 1, 0, z), marker))
    entrance = {width // 2 - 1, width // 2, width // 2 + 1}
    for x in range(1, width - 1):
        if x not in entrance:
            blocks.append(((x, 1, 0), "minecraft:iron_bars"))
            blocks.append(((x, 1, depth - 1), "minecraft:iron_bars"))
    for z in range(1, depth - 1):
        if z not in {depth // 2, depth // 2 + 1}:
            blocks.append(((0, 1, z), "minecraft:iron_bars"))
            blocks.append(((width - 1, 1, z), "minecraft:iron_bars"))
    return blocks


def _domain(block_id: str, facing: str, input_facing: str, **extra: str) -> BlockSpec:
    props = {"facing": facing, "input_facing": input_facing}
    props.update(extra)
    return block_id, props


def _series(block_id: str, facing: str, input_facing: str, **extra: str) -> BlockSpec:
    props = {"facing": facing, "input_facing": input_facing, "output": "0"}
    props.update(extra)
    return block_id, props


def _unity_buffer(facing: str, input_facing: str) -> BlockSpec:
    return _series("redstoneengineering:signal_conditioner", facing, input_facing, mode="0", param="1")


def _reference(power: int, facing: str) -> BlockSpec:
    return "redstoneengineering:redstone_reference_source", {"facing": facing, "power": str(power)}


def _terminal(facing: str, output_mode: bool) -> BlockSpec:
    return "redstoneengineering:redstone_cable_terminal", {
        "facing": facing,
        "output_mode": "true" if output_mode else "false",
        "power": "0",
    }


def _primary_dut(station: StationSpec) -> BlockSpec:
    n = station.number
    if n == 1:
        return _reference(9, "east")
    if n in (16, 18, 20, 21, 23):
        return _domain(station.block_id, "east", "west")
    if n in (24, 25):
        extra = {"timeout": "1"} if n == 25 else {}
        return _series(station.block_id, "east", "west", **extra)
    if n == 28:
        return _domain(station.block_id, "east", "west")
    if n == 29:
        return _domain(station.block_id, "east", "west", target="0")
    if n in (33, 34, 35):
        return _domain(station.block_id, "east", "west")
    if n == 36:
        return _series(station.block_id, "east", "west", tuning="2")
    if n == 37:
        return station.block_id, {"facing": "east", "slew": "0"}
    if n == 38:
        return _series(station.block_id, "north", "west")
    if n in (39, 40):
        return _series(station.block_id, "west", "east")
    return station.block_id


def _digital_cell_wiring() -> list[Placement]:
    blocks: list[Placement] = [((2, 1, 12), _reference(9, "east")), ((28, 1, 12), "redstoneengineering:eight_bit_data_bus")]
    blocks.extend(((x, 1, 12), "redstoneengineering:eight_bit_data_bus") for x in range(4, 15))
    blocks.extend(((x, 1, 12), "redstoneengineering:serial_data_line") for x in range(16, 27))
    return blocks


def _comms_cell_wiring() -> list[Placement]:
    blocks: list[Placement] = [
        ((2, 1, 12), _reference(15, "east")),
        ((14, 1, 12), "redstoneengineering:serial_data_line"),
        ((16, 1, 12), "redstoneengineering:serial_data_line"),
        # Independent real SERIAL source path into D23.
        ((14, 1, 6), _reference(11, "south")),
        ((14, 1, 7), _domain("redstoneengineering:redstone_byte_encoder", "south", "north")),
        ((14, 1, 8), "redstoneengineering:eight_bit_data_bus"),
        ((14, 1, 9), _domain("redstoneengineering:serializer", "south", "north")),
        ((14, 1, 10), "redstoneengineering:serial_data_line"),
        ((14, 1, 11), "redstoneengineering:serial_data_line"),
        # D23 regenerated output is decoded on a separate observer branch.
        ((16, 1, 11), "redstoneengineering:serial_data_line"),
        ((16, 1, 10), "redstoneengineering:serial_data_line"),
        ((16, 1, 9), _domain("redstoneengineering:deserializer", "north", "south")),
        ((16, 1, 8), "redstoneengineering:eight_bit_data_bus"),
        ((16, 1, 7), _series("redstoneengineering:byte_to_redstone_decoder", "north", "south")),
        ((16, 1, 6), "minecraft:redstone_lamp"),
    ]
    # Differential path routes around D23 because D23 is intentionally SERIAL, not DIFFERENTIAL.
    blocks.extend(((x, 1, 12), "redstoneengineering:differential_data_pair") for x in range(4, 14))
    blocks.extend(((x, 1, 13), "redstoneengineering:differential_data_pair") for x in range(13, 18))
    blocks.extend(((x, 1, 12), "redstoneengineering:differential_data_pair") for x in range(17, 21))
    # Differential receiver drives a real insulated heartbeat bus into D25.
    blocks.extend(((x, 1, 12), "redstoneengineering:redstone_signal_cable") for x in range(22, 27))
    return blocks


def _optical_cell_wiring() -> list[Placement]:
    blocks: list[Placement] = []
    blocks.extend(((x, 1, 12), "redstoneengineering:optical_fiber") for x in range(4, 15))
    blocks.extend(((x, 1, 12), "redstoneengineering:optical_fiber") for x in range(16, 21))
    blocks.extend(((x, 1, 12), "redstoneengineering:optical_fiber") for x in range(22, 27))
    blocks.extend([
        ((15, 1, 11), "redstoneengineering:optical_fiber"),
        ((15, 1, 10), "redstoneengineering:optical_receiver"),
    ])
    return blocks


def _pneumatic_cell_wiring() -> list[Placement]:
    blocks: list[Placement] = [
        ((3, 0, 12), "minecraft:redstone_block"),
        ((3, 2, 12), "redstoneengineering:pneumatic_pipe"),
        ((21, 2, 12), "minecraft:redstone_block"),
    ]
    blocks.extend(((x, 2, 12), "redstoneengineering:pneumatic_pipe") for x in range(4, 10))
    blocks.extend(((x, 1, 12), "redstoneengineering:pneumatic_pipe") for x in range(10, 15))
    blocks.extend(((x, 1, 12), "redstoneengineering:pneumatic_pipe") for x in range(16, 21))
    blocks.extend(((x, 1, 12), "redstoneengineering:pneumatic_pipe") for x in range(22, 27))
    return blocks


def _control_safety_cell_wiring() -> list[Placement]:
    blocks: list[Placement] = [
        # PID setpoint and command path into the servo BACK face.
        ((2, 1, 12), _reference(9, "east")),
        # D38 feedback exits NORTH, stays isolated from command bus at z=12, then returns to PID NORTH/process face.
        ((10, 1, 11), "redstoneengineering:redstone_signal_cable"),
        ((10, 1, 10), "redstoneengineering:redstone_signal_cable"),
        ((3, 1, 11), "redstoneengineering:redstone_signal_cable"),
        # D39 three real permissive channels A/B/C.
        ((22, 1, 12), _reference(15, "west")),
        ((21, 1, 13), _reference(15, "north")),
        ((21, 1, 11), _reference(15, "south")),
        # Permit-high healthy state drives a vanilla torch inverter. Trip -> torch ON.
        ((20, 1, 12), "minecraft:stone"),
        ((20, 1, 13), ("minecraft:redstone_wall_torch", {"facing": "south", "lit": "true"})),
        ((19, 1, 13), _terminal("east", False)),
        # Brake branch ends in a cable->vanilla terminal directly on Servo BRAKE (south) face.
        ((10, 1, 14), "redstoneengineering:redstone_signal_cable"),
        ((9, 1, 14), "redstoneengineering:redstone_signal_cable"),
        ((9, 1, 13), _terminal("north", True)),
        # Alarm condition branch comes from the same inverted trip signal.
        ((20, 1, 14), _terminal("north", False)),
        ((28, 1, 12), _terminal("west", True)),
        # Alarm ACK and RESET are explicit zero-valued physical operator/test inputs.
        ((27, 1, 13), _reference(0, "north")),
        ((27, 1, 11), _reference(0, "south")),
    ]
    blocks.extend(((x, 1, 12), "redstoneengineering:redstone_signal_cable") for x in range(4, 9))
    blocks.extend(((x, 1, 10), "redstoneengineering:redstone_signal_cable") for x in range(3, 11))
    blocks.extend(((x, 1, 13), "redstoneengineering:redstone_signal_cable") for x in range(10, 19))
    blocks.extend(((x, 1, 15), "redstoneengineering:redstone_signal_cable") for x in range(20, 30))
    blocks.extend([
        ((29, 1, 14), "redstoneengineering:redstone_signal_cable"),
        ((29, 1, 13), "redstoneengineering:redstone_signal_cable"),
        ((29, 1, 12), "redstoneengineering:redstone_signal_cable"),
    ])
    return blocks


def _cell_wiring(cell: str) -> list[Placement]:
    return {
        "D": _digital_cell_wiring,
        "E": _comms_cell_wiring,
        "F": _optical_cell_wiring,
        "G": _pneumatic_cell_wiring,
        "H": _control_safety_cell_wiring,
    }.get(cell, lambda: [])()


def _cell_builder(cell: str) -> tuple[tuple[int, int, int], list[Placement]]:
    module_id, title, _defs = CELL_DEFINITIONS[cell]
    marker = {
        "A": "minecraft:light_blue_concrete", "B": "minecraft:gray_concrete",
        "C": "minecraft:blue_concrete", "D": "minecraft:purple_concrete",
        "E": "minecraft:orange_concrete", "F": "minecraft:yellow_concrete",
        "G": "minecraft:white_concrete", "H": "minecraft:red_concrete",
    }[cell]
    p = _clear_volume(CELL_SIZE)
    additions: list[Placement] = [
        *_floor(CELL_SIZE[0], CELL_SIZE[2]),
        *_frame(CELL_SIZE, marker),
        *[((x, 0, z), "minecraft:light_gray_concrete") for x in range(1, 30) for z in (10, 11, 12, 13)],
        ((1, 1, 1), "minecraft:sea_lantern"), ((29, 1, 1), "minecraft:sea_lantern"),
        ((1, 1, 23), "minecraft:sea_lantern"), ((29, 1, 23), "minecraft:sea_lantern"),
        ((8, 6, 8), "minecraft:sea_lantern"), ((22, 6, 16), "minecraft:sea_lantern"),
        ((2, 1, 2), _sign((f"CELL {cell}", title, "5 DUT + INTEGRATION", "SEE Dxx STATIONS"))),
        ((14, 1, 19), _sign((f"CELL {cell} STATUS", "WAIT YELLOW", "PASS GREEN", "FAIL RED"))),
        *_panel_blocks(LOCAL_CELL_PANEL, labels=True),
    ]

    additions.extend([
        ((29, 1, 0), _unity_buffer("south", "north")),
        ((29, 1, 1), _unity_buffer("south", "north")),
        ((29, 1, 2), _unity_buffer("south", "north")),
        ((28, 1, 2), ("redstoneengineering:analog_indicator", {"facing": "east", "level": "0"})),
    ])

    if cell == "D":
        additions.extend([
            ((30, 1, 0), _unity_buffer("south", "north")),
            *[((30, 1, z), _unity_buffer("south", "north")) for z in range(1, 25)],
        ])

    for station in MEGA_CELL_STATIONS[cell]:
        additions.extend([
            (station.dut_pos, _primary_dut(station)),
            (station.sign_pos, _sign((f"D{station.number:02d}", station.short_name, station.role[:18], f"CELL {cell}"))),
            *_panel_blocks(station.panel),
            ((station.dut_pos[0], 0, station.dut_pos[2]), "minecraft:polished_andesite"),
        ])

    additions.extend(_cell_wiring(cell))
    return CELL_SIZE, _overlay(p, additions)


def _north_spine() -> tuple[tuple[int, int, int], list[Placement]]:
    p = _clear_volume(SPINE_SIZE)
    additions: list[Placement] = [
        *_floor(SPINE_SIZE[0], SPINE_SIZE[2], "minecraft:light_gray_concrete"),
        ((0, 1, 2), _reference(9, "east")),
        ((66, 1, 1), _sign(("MEGA BACKBONE", "A > B > C > D", "TOKEN SOURCE=9", "D TURNS SOUTH"))),
    ]
    additions.extend(((x, 1, 2), _unity_buffer("east", "west")) for x in range(1, 133))
    for x in range(0, 133, 8):
        additions.append(((x, 6, 1), "minecraft:sea_lantern"))
    for x in (0, 33, 67, 101, 132):
        for y in range(1, 7):
            additions.append(((x, y, 0), "minecraft:polished_deepslate"))
    return SPINE_SIZE, _overlay(p, additions)


def _cross_spine() -> tuple[tuple[int, int, int], list[Placement]]:
    p = _clear_volume(SPINE_SIZE)
    additions: list[Placement] = [
        *_floor(SPINE_SIZE[0], SPINE_SIZE[2], "minecraft:light_gray_concrete"),
        ((66, 1, 1), _sign(("MEGA BACKBONE", "E > F > G > H", "SERPENTINE RETURN", "FINAL AT WEST"))),
        ((132, 1, 0), _unity_buffer("south", "north")),
        ((132, 1, 1), _unity_buffer("south", "north")),
        ((132, 1, 2), _unity_buffer("west", "north")),
    ]
    additions.extend(((x, 1, 2), _unity_buffer("west", "east")) for x in range(0, 132))
    for x in range(0, 133, 8):
        additions.append(((x, 6, 1), "minecraft:sea_lantern"))
    for x in (0, 33, 67, 101, 132):
        for y in range(1, 7):
            additions.append(((x, y, 0), "minecraft:polished_deepslate"))
    return SPINE_SIZE, _overlay(p, additions)


def _control_hall() -> tuple[tuple[int, int, int], list[Placement]]:
    p = _clear_volume(CONTROL_HALL_SIZE)
    width, height, depth = CONTROL_HALL_SIZE
    additions: list[Placement] = [
        *_floor(width, depth, "minecraft:polished_andesite"),
        *_frame(CONTROL_HALL_SIZE, "minecraft:black_concrete"),
        ((31, 1, 1), _sign(("RSE MEGA FACTORY", "40 DUT / 8 CELLS", "v2 ACCEPTANCE", "A-H SERPENTINE"))),
        ((46, 1, 11), _sign(("MASTER STATUS", "WAIT YELLOW", "PASS GREEN", "FAIL RED"))),
        ((55, 1, 14), _sign(("MASTER RETEST", "PRESS BUTTON", "REBUILDS MEGA", "RESTARTS RUN"))),
        (MEGA_MASTER_RETEST_BUTTON, ("minecraft:stone_button", {"face": "floor", "facing": "north", "powered": "false"})),
        *_panel_blocks(MEGA_MASTER_PANEL, labels=True),
        ((31, 6, 4), "minecraft:sea_lantern"), ((31, 6, 12), "minecraft:sea_lantern"),
        ((10, 6, 8), "minecraft:sea_lantern"), ((52, 6, 8), "minecraft:sea_lantern"),
    ]
    for station in MEGA_STATIONS:
        panel = CONTROL_STATION_PANELS[station.number]
        additions.extend(_panel_blocks(panel))
        if station.number % 5 == 1:
            additions.append(((31, 1, panel.wait_lamp[2]), _sign((f"CELL {station.cell}", f"D{station.number:02d}-D{station.number + 4:02d}", "STATION MATRIX", "Y/G/R"))))
    for cell, panel in CONTROL_CELL_PANELS.items():
        additions.extend(_panel_blocks(panel))
        additions.append(((44, 1, panel.wait_lamp[2]), _sign((f"CELL {cell}", "INTEGRATED", "WAIT/PASS/FAIL", "5 DUT"))))
    additions.extend([
        ((46, 1, 2), _sign(("DIAGNOSTICS", "mega status", "mega station N", "mega cell A-H"))),
        ((55, 1, 2), _sign(("REPORT", "mega report", "20 PHASES", "HISTORY KEPT"))),
    ])
    return CONTROL_HALL_SIZE, _overlay(p, additions)


MEGA_STRUCTURES: dict[str, Builder] = {
    "mega_control_hall": _control_hall,
    "mega_north_spine": _north_spine,
    "mega_cross_spine": _cross_spine,
    "mega_cell_a_analog": lambda: _cell_builder("A"),
    "mega_cell_b_timing": lambda: _cell_builder("B"),
    "mega_cell_c_precision": lambda: _cell_builder("C"),
    "mega_cell_d_data": lambda: _cell_builder("D"),
    "mega_cell_e_comms": lambda: _cell_builder("E"),
    "mega_cell_f_optical": lambda: _cell_builder("F"),
    "mega_cell_g_pneumatic": lambda: _cell_builder("G"),
    "mega_cell_h_control": lambda: _cell_builder("H"),
}


def build_all() -> dict[str, tuple[tuple[int, int, int], list[Placement]]]:
    return {module_id: MEGA_STRUCTURES[module_id]() for module_id in MEGA_STRUCTURE_ORDER}
