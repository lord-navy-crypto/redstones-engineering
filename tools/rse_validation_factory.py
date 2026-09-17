#!/usr/bin/env python3
"""Compatibility front-end for the RSE validation asset generator.

The validated v1/preset/selftest/world-packaging implementation is retained byte-for-byte in
``rse_validation_factory_legacy.py``. This front-end re-exports its public and private helper
surface and adds Mega Validation Factory v2 generation without destabilizing older workflows.
"""
from __future__ import annotations

from pathlib import Path
import shutil
import sys

try:
    from tools import rse_validation_factory_legacy as _legacy
    from tools.rse_mega_factory import (
        MEGA_MODULE_OFFSETS,
        MEGA_STRUCTURE_ORDER,
        MEGA_STRUCTURES,
        MEGA_STATIONS,
    )
except ModuleNotFoundError:  # direct `python3 tools/...` execution
    import rse_validation_factory_legacy as _legacy
    from rse_mega_factory import (
        MEGA_MODULE_OFFSETS,
        MEGA_STRUCTURE_ORDER,
        MEGA_STRUCTURES,
        MEGA_STATIONS,
    )

# Preserve the complete legacy module API, including the underscore-prefixed NBT helpers used by
# validation contract tests. Mega-specific definitions below intentionally override only generation
# entry points that need extending.
for _name, _value in vars(_legacy).items():
    if not _name.startswith("__"):
        globals()[_name] = _value

MEGA_STRUCTURE_DIR = STRUCTURE_DIR / "mega"
MEGA_INDEX_PATH = ROOT / "build/validation/mega-index.txt"


def _terminal(facing: str, output_mode: bool) -> BlockSpec:
    """Explicit vanilla-redstone <-> insulated-cable boundary used by Mega fixtures."""
    return (
        "redstoneengineering:redstone_cable_terminal",
        {
            "facing": facing,
            "output_mode": "true" if output_mode else "false",
            "power": "0",
        },
    )


def _mega_structure(name: str) -> tuple[tuple[int, int, int], list[Placement]]:
    """Return the generated Mega module after applying integration-boundary repairs.

    The original Mega layout intentionally remains the visual/layout authority. This thin repair
    layer corrects only places where an insulated cable had been placed directly against a vanilla
    redstone engineering port, and replaces Cell E's constant level with a real PWM heartbeat so
    the watchdog receives transitions rather than a permanently-high pseudo-heartbeat.
    """
    size, placements = MEGA_STRUCTURES[name]()
    by_pos: dict[tuple[int, int, int], BlockSpec] = dict(placements)

    if name == "mega_cell_e_comms":
        # D21 receives an actual changing heartbeat source. A level of 8 with the 4-tick PWM mode
        # realizes two ticks high / two ticks low, which propagates through D21-D24 into D25.
        by_pos[(1, 1, 12)] = (
            "redstoneengineering:redstone_reference_source",
            {"facing": "east", "power": "8"},
        )
        by_pos[(2, 1, 12)] = (
            "redstoneengineering:pwm_controller",
            {
                "facing": "east",
                "input_facing": "west",
                "output": "0",
                "period_mode": "0",
                "invert": "false",
            },
        )
        # D24 is a vanilla-redstone endpoint. Insulated cable needs an explicit terminal on both
        # ends of the D24 -> D25 heartbeat run.
        by_pos[(22, 1, 12)] = _terminal("west", False)
        by_pos[(26, 1, 12)] = _terminal("east", True)

    if name == "mega_cell_h_control":
        # PID CONTROL OUT -> insulated command cable -> Servo command input.
        by_pos[(4, 1, 12)] = _terminal("west", False)
        by_pos[(8, 1, 12)] = _terminal("east", True)

        # Position sensor -> insulated feedback cable -> fault injector -> insulated feedback cable
        # -> PID PROCESS VALUE IN. Every vanilla/cable boundary is explicit and direction-correct.
        by_pos[(10, 1, 11)] = _terminal("south", False)
        by_pos[(8, 1, 10)] = _terminal("west", True)
        by_pos[(6, 1, 10)] = _terminal("east", False)
        by_pos[(3, 1, 11)] = _terminal("south", True)

    return size, [(pos, by_pos[pos]) for pos in sorted(by_pos)]


def _mega_index_text() -> str:
    lines = [
        "RSE MEGA VALIDATION FACTORY V2 — 40 DUT / 8 CELLS",
        "module\toffset_x\toffset_y\toffset_z\tsize_x\tsize_y\tsize_z",
    ]
    for name in MEGA_STRUCTURE_ORDER:
        size, _placements = _mega_structure(name)
        offset = MEGA_MODULE_OFFSETS[name]
        lines.append(
            f"{name}\t{offset[0]}\t{offset[1]}\t{offset[2]}\t{size[0]}\t{size[1]}\t{size[2]}"
        )
    lines.extend(["", "station\tcell\tblock\tmodule\tx\ty\tz\tname\trole"])
    for station in MEGA_STATIONS:
        x, y, z = station.dut_pos
        lines.append(
            f"D{station.number:02d}\t{station.cell}\t{station.block_id}\t{station.module_id}"
            f"\t{x}\t{y}\t{z}\t{station.short_name}\t{station.role}"
        )
    return "\n".join(lines) + "\n"


def generate_mega(
    structure_dir: Path = MEGA_STRUCTURE_DIR,
    index_path: Path = MEGA_INDEX_PATH,
) -> int:
    if structure_dir.exists():
        shutil.rmtree(structure_dir)
    structure_dir.mkdir(parents=True, exist_ok=True)
    for name in MEGA_STRUCTURE_ORDER:
        size, placements = _mega_structure(name)
        _write_structure(structure_dir / f"{name}.nbt", size, placements)
    index_path.parent.mkdir(parents=True, exist_ok=True)
    index_path.write_text(_mega_index_text(), encoding="utf-8")
    print(f"generated {index_path.relative_to(ROOT) if index_path.is_relative_to(ROOT) else index_path}")
    return 0


def generate() -> int:
    rc = _legacy.generate()
    return rc if rc else generate_mega()


def main(argv: list[str] | None = None) -> int:
    args = list(sys.argv[1:] if argv is None else argv)
    args = _legacy._legacy_cli(args)
    if args and args[0] == "generate-mega":
        return generate_mega()
    if args and args[0] == "generate":
        return generate()
    if args and args[0] == "clean":
        rc = _legacy.main(args)
        if MEGA_INDEX_PATH.exists():
            MEGA_INDEX_PATH.unlink()
        return rc
    return _legacy.main(args)


if __name__ == "__main__":
    raise SystemExit(main())