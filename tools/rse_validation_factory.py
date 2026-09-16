#!/usr/bin/env python3
"""Generate RSE Validation Factory structure templates and package a real test world.

Usage:
    python3 tools/rse_validation_factory.py generate
    python3 tools/rse_validation_factory.py package
    python3 tools/rse_validation_factory.py package --world "run/saves/RSE Validation Factory"

The package command refuses to fabricate a Minecraft world. The source directory must already
contain a real level.dat created by Minecraft/NeoForge.
"""
from __future__ import annotations

import argparse
import gzip
from pathlib import Path
import shutil
import struct
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
STRUCTURE_DIR = ROOT / "src/generated/resources/data/redstoneengineering/structure/validation"
DEFAULT_WORLD = ROOT / "run/saves/RSE Validation Factory"
PACKAGE_PATH = ROOT / "build/validation/RSE-Validation-Factory.zip"
DATA_VERSION = 3955  # Minecraft 1.21.1; matches the repository's existing empty5x4x5 template.

EXPECTED_STRUCTURES = (
    "material_release",
    "queue_dispatch",
    "maintenance_hold",
    "quality_output",
    "amr_lane",
    "operations_monitor",
    "full_factory",
)

# NBT tag ids used by Minecraft structure templates.
TAG_END = 0
TAG_INT = 3
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10


def _u16(value: int) -> bytes:
    return struct.pack(">H", value)


def _i32(value: int) -> bytes:
    return struct.pack(">i", value)


def _name(name: str) -> bytes:
    raw = name.encode("utf-8")
    return _u16(len(raw)) + raw


def _named_int(name: str, value: int) -> bytes:
    return bytes([TAG_INT]) + _name(name) + _i32(value)


def _named_string(name: str, value: str) -> bytes:
    raw = value.encode("utf-8")
    return bytes([TAG_STRING]) + _name(name) + _u16(len(raw)) + raw


def _int_list_payload(values: tuple[int, ...] | list[int]) -> bytes:
    return bytes([TAG_INT]) + _i32(len(values)) + b"".join(_i32(v) for v in values)


def _named_int_list(name: str, values: tuple[int, ...] | list[int]) -> bytes:
    return bytes([TAG_LIST]) + _name(name) + _int_list_payload(values)


def _compound_payload(entries: list[bytes]) -> bytes:
    return b"".join(entries) + bytes([TAG_END])


def _list_of_compounds_payload(compounds: list[bytes]) -> bytes:
    return bytes([TAG_COMPOUND]) + _i32(len(compounds)) + b"".join(compounds)


def _named_compound_list(name: str, compounds: list[bytes]) -> bytes:
    return bytes([TAG_LIST]) + _name(name) + _list_of_compounds_payload(compounds)


def _palette_entry(block_id: str) -> bytes:
    return _compound_payload([_named_string("Name", block_id)])


def _block_entry(x: int, y: int, z: int, state: int) -> bytes:
    return _compound_payload([
        _named_int_list("pos", [x, y, z]),
        _named_int("state", state),
    ])


def _structure_bytes(size: tuple[int, int, int], placements: list[tuple[tuple[int, int, int], str]]) -> bytes:
    palette: list[str] = []
    palette_index: dict[str, int] = {}
    blocks: list[bytes] = []
    for (x, y, z), block_id in placements:
        if block_id not in palette_index:
            palette_index[block_id] = len(palette)
            palette.append(block_id)
        blocks.append(_block_entry(x, y, z, palette_index[block_id]))

    root_payload = _compound_payload([
        _named_int("DataVersion", DATA_VERSION),
        _named_int_list("size", list(size)),
        _named_compound_list("palette", [_palette_entry(block_id) for block_id in palette]),
        _named_compound_list("blocks", blocks),
        _named_compound_list("entities", []),
    ])
    # TAG_Compound root with an empty name, matching vanilla structure NBT.
    return bytes([TAG_COMPOUND]) + _u16(0) + root_payload


def _floor(width: int, depth: int, block_id: str = "minecraft:smooth_stone") -> list[tuple[tuple[int, int, int], str]]:
    return [((x, 0, z), block_id) for x in range(width) for z in range(depth)]


def _border(width: int, depth: int, block_id: str) -> list[tuple[tuple[int, int, int], str]]:
    placements: list[tuple[tuple[int, int, int], str]] = []
    for x in range(width):
        placements.append(((x, 0, 0), block_id))
        placements.append(((x, 0, depth - 1), block_id))
    for z in range(1, depth - 1):
        placements.append(((0, 0, z), block_id))
        placements.append(((width - 1, 0, z), block_id))
    return placements


def _station_base(width: int, depth: int, marker: str) -> list[tuple[tuple[int, int, int], str]]:
    placements = _floor(width, depth)
    placements.extend(_border(width, depth, marker))
    placements.extend([
        ((1, 1, 1), "minecraft:sea_lantern"),
        ((width - 2, 1, depth - 2), "minecraft:sea_lantern"),
    ])
    return placements


def _material_release() -> tuple[tuple[int, int, int], list[tuple[tuple[int, int, int], str]]]:
    p = _station_base(11, 9, "minecraft:light_blue_concrete")
    p += [
        ((2, 1, 4), "redstoneengineering:industrial_buffer"),
        ((5, 1, 4), "redstoneengineering:workcell_controller"),
        ((8, 1, 4), "redstoneengineering:industrial_buffer"),
        ((5, 1, 2), "minecraft:yellow_concrete"),
        ((5, 1, 6), "minecraft:lime_concrete"),
    ]
    return (11, 4, 9), p


def _queue_dispatch() -> tuple[tuple[int, int, int], list[tuple[tuple[int, int, int], str]]]:
    p = _station_base(11, 9, "minecraft:blue_concrete")
    p += [
        ((2, 1, 4), "redstoneengineering:industrial_buffer"),
        ((5, 1, 4), "redstoneengineering:workcell_controller"),
        ((8, 1, 3), "redstoneengineering:workcell_controller"),
        ((8, 1, 5), "redstoneengineering:workcell_controller"),
        ((5, 1, 2), "minecraft:redstone_lamp"),
        ((5, 1, 6), "minecraft:redstone_lamp"),
    ]
    return (11, 4, 9), p


def _maintenance_hold() -> tuple[tuple[int, int, int], list[tuple[tuple[int, int, int], str]]]:
    p = _station_base(11, 9, "minecraft:yellow_concrete")
    p += [
        ((3, 1, 4), "redstoneengineering:workcell_controller"),
        ((7, 1, 4), "redstoneengineering:workcell_controller"),
        ((3, 1, 2), "minecraft:lime_concrete"),
        ((7, 1, 2), "minecraft:yellow_concrete"),
        ((3, 1, 6), "minecraft:redstone_lamp"),
        ((7, 1, 6), "minecraft:redstone_lamp"),
    ]
    return (11, 4, 9), p


def _quality_output() -> tuple[tuple[int, int, int], list[tuple[tuple[int, int, int], str]]]:
    p = _station_base(11, 9, "minecraft:purple_concrete")
    p += [
        ((2, 1, 4), "redstoneengineering:workcell_controller"),
        ((5, 1, 4), "redstoneengineering:industrial_buffer"),
        ((8, 1, 4), "redstoneengineering:industrial_buffer"),
        ((5, 1, 2), "minecraft:lime_concrete"),
        ((8, 1, 2), "minecraft:red_concrete"),
    ]
    return (11, 4, 9), p


def _amr_lane() -> tuple[tuple[int, int, int], list[tuple[tuple[int, int, int], str]]]:
    width, depth = 21, 9
    p = _station_base(width, depth, "minecraft:orange_concrete")
    # Explicit physical lane: white centerline, source and destination staging pads.
    for x in range(2, width - 2):
        p.append(((x, 1, 4), "minecraft:white_concrete"))
    p += [
        ((2, 1, 2), "redstoneengineering:industrial_buffer"),
        ((18, 1, 6), "redstoneengineering:industrial_buffer"),
        ((2, 1, 4), "minecraft:lime_concrete"),
        ((18, 1, 4), "minecraft:cyan_concrete"),
        ((10, 1, 2), "redstoneengineering:workcell_controller"),
    ]
    return (width, 4, depth), p


def _operations_monitor() -> tuple[tuple[int, int, int], list[tuple[tuple[int, int, int], str]]]:
    p = _station_base(11, 9, "minecraft:cyan_concrete")
    p += [
        ((5, 1, 4), "redstoneengineering:workcell_controller"),
        ((3, 1, 4), "redstoneengineering:topology_debugger"),
        ((7, 1, 4), "redstoneengineering:industrial_buffer"),
        ((5, 1, 2), "minecraft:sea_lantern"),
        ((5, 1, 6), "minecraft:sea_lantern"),
    ]
    return (11, 4, 9), p


def _full_factory() -> tuple[tuple[int, int, int], list[tuple[tuple[int, int, int], str]]]:
    width, depth = 48, 48
    p = _floor(width, depth, "minecraft:light_gray_concrete")
    p.extend(_border(width, depth, "minecraft:black_concrete"))
    # Central aisle and station anchors. The Java builder overlays the individual station templates.
    for x in range(2, width - 2):
        p.append(((x, 1, 23), "minecraft:white_concrete"))
        p.append(((x, 1, 24), "minecraft:white_concrete"))
    for z in range(2, depth - 2):
        p.append(((23, 1, z), "minecraft:white_concrete"))
        p.append(((24, 1, z), "minecraft:white_concrete"))
    p += [
        ((23, 1, 23), "redstoneengineering:engineering_compass"),
        ((24, 1, 24), "minecraft:sea_lantern"),
    ]
    return (width, 4, depth), p


STRUCTURES = {
    "material_release": _material_release,
    "queue_dispatch": _queue_dispatch,
    "maintenance_hold": _maintenance_hold,
    "quality_output": _quality_output,
    "amr_lane": _amr_lane,
    "operations_monitor": _operations_monitor,
    "full_factory": _full_factory,
}


def generate() -> int:
    STRUCTURE_DIR.mkdir(parents=True, exist_ok=True)
    stale = {p.stem for p in STRUCTURE_DIR.glob("*.nbt")} - set(EXPECTED_STRUCTURES)
    for name in stale:
        (STRUCTURE_DIR / f"{name}.nbt").unlink()

    for name in EXPECTED_STRUCTURES:
        size, placements = STRUCTURES[name]()
        payload = _structure_bytes(size, placements)
        # mtime=0 makes generation deterministic across machines/runs.
        compressed = gzip.compress(payload, compresslevel=9, mtime=0)
        out = STRUCTURE_DIR / f"{name}.nbt"
        out.write_bytes(compressed)
        print(f"generated {out.relative_to(ROOT)} ({len(compressed)} bytes)")
    return 0


def package_world(world: Path, output: Path) -> int:
    world = world.resolve()
    output = output.resolve()
    level_dat = world / "level.dat"
    if not world.is_dir() or not level_dat.is_file():
        print(
            "ERROR: package requires a real Minecraft-created 'RSE Validation Factory' save "
            "containing level.dat; refusing to fabricate a world.",
            file=sys.stderr,
        )
        return 2

    output.parent.mkdir(parents=True, exist_ok=True)
    if output.exists():
        output.unlink()
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in sorted(world.rglob("*")):
            if not path.is_file():
                continue
            relative = path.relative_to(world.parent)
            archive.write(path, relative.as_posix())
    print(f"packaged {world} -> {output}")
    return 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="RSE Validation Factory asset tool")
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("generate", help="generate reusable validation structure NBT files")
    package = sub.add_parser("package", help="package an existing real Validation Factory world")
    package.add_argument("--world", type=Path, default=DEFAULT_WORLD)
    package.add_argument("--output", type=Path, default=PACKAGE_PATH)
    clean = sub.add_parser("clean", help="remove generated validation structures")
    clean.set_defaults(command="clean")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.command == "generate":
        return generate()
    if args.command == "package":
        return package_world(args.world, args.output)
    if args.command == "clean":
        if STRUCTURE_DIR.exists():
            shutil.rmtree(STRUCTURE_DIR)
            print(f"removed {STRUCTURE_DIR.relative_to(ROOT)}")
        return 0
    raise AssertionError(args.command)


if __name__ == "__main__":
    raise SystemExit(main())
