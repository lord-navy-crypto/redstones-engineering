#!/usr/bin/env python3
"""Generate RSE Validation Factory structures, presets, self-tests, plant modules, and real-world ZIPs.

Preferred usage:
    python3 tools/rse_validation_factory.py generate
    python3 tools/rse_validation_factory.py generate-presets
    python3 tools/rse_validation_factory.py generate-selftests
    python3 tools/rse_validation_factory.py generate-plant
    python3 tools/rse_validation_factory.py package-presets
    python3 tools/rse_validation_factory.py package
    python3 tools/rse_validation_factory.py package --world "run/saves/RSE Validation Factory"

Backward-compatible aliases:
    python3 tools/rse_validation_factory.py --generate-structures
    python3 tools/rse_validation_factory.py --package-world "run/saves/RSE Validation Factory"

The world package command refuses to fabricate a Minecraft world. The source directory must
already contain a real level.dat created by Minecraft/NeoForge.
"""
from __future__ import annotations

import argparse
import gzip
from pathlib import Path
import shutil
import struct
import sys
import zipfile

try:
    from tools.rse_validation_presets import PRESETS
    from tools.rse_validation_selftests import SELFTESTS
    from tools.rse_validation_plant import PLANT_MODULE_OFFSETS, PLANT_STRUCTURE_ORDER, PLANT_STRUCTURES
except ModuleNotFoundError:  # direct `python3 tools/...` execution
    from rse_validation_presets import PRESETS
    from rse_validation_selftests import SELFTESTS
    from rse_validation_plant import PLANT_MODULE_OFFSETS, PLANT_STRUCTURE_ORDER, PLANT_STRUCTURES

ROOT = Path(__file__).resolve().parents[1]
STRUCTURE_DIR = ROOT / "src/generated/resources/data/redstoneengineering/structure/validation"
PRESET_STRUCTURE_DIR = STRUCTURE_DIR / "presets"
SELFTEST_STRUCTURE_DIR = STRUCTURE_DIR / "selftest"
PLANT_STRUCTURE_DIR = STRUCTURE_DIR / "plant"
PRESET_INDEX_PATH = ROOT / "build/validation/preset-index.txt"
SELFTEST_INDEX_PATH = ROOT / "build/validation/selftest-index.txt"
PLANT_INDEX_PATH = ROOT / "build/validation/plant-index.txt"
PRESET_PACKAGE_PATH = ROOT / "build/validation/RSE-Preset-Pack.zip"
DEFAULT_WORLD = ROOT / "run/saves/RSE Validation Factory"
PACKAGE_PATH = ROOT / "build/validation/RSE-Validation-Factory.zip"
DATA_VERSION = 3955  # Minecraft 1.21.1; matches the repository's existing empty5x4x5 template.
ARCHIVE_ROOT = Path("RSE Validation Factory")

BlockSpec = str | tuple[str, dict[str, str]]
Placement = tuple[tuple[int, int, int], BlockSpec]
PaletteKey = tuple[str, tuple[tuple[str, str], ...]]

EXPECTED_STRUCTURES = (
    "material_release",
    "queue_dispatch",
    "maintenance_hold",
    "quality_output",
    "amr_lane",
    "operations_monitor",
    "full_factory",
)

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


def _named_compound(name: str, entries: list[bytes]) -> bytes:
    return bytes([TAG_COMPOUND]) + _name(name) + _compound_payload(entries)


def _list_of_compounds_payload(compounds: list[bytes]) -> bytes:
    return bytes([TAG_COMPOUND]) + _i32(len(compounds)) + b"".join(compounds)


def _named_compound_list(name: str, compounds: list[bytes]) -> bytes:
    return bytes([TAG_LIST]) + _name(name) + _list_of_compounds_payload(compounds)


def _normalize_block_spec(block: BlockSpec) -> PaletteKey:
    if isinstance(block, str):
        return block, ()
    block_id, properties = block
    return block_id, tuple(sorted((str(key), str(value)) for key, value in properties.items()))


def _block_name(block: BlockSpec) -> str:
    return _normalize_block_spec(block)[0]


def _palette_entry(block: PaletteKey) -> bytes:
    block_id, properties = block
    entries = [_named_string("Name", block_id)]
    if properties:
        entries.append(_named_compound(
            "Properties",
            [_named_string(key, value) for key, value in properties],
        ))
    return _compound_payload(entries)


def _block_entry(x: int, y: int, z: int, state: int) -> bytes:
    return _compound_payload([
        _named_int_list("pos", [x, y, z]),
        _named_int("state", state),
    ])


def _structure_bytes(size: tuple[int, int, int], placements: list[Placement]) -> bytes:
    palette: list[PaletteKey] = []
    palette_index: dict[PaletteKey, int] = {}
    blocks: list[bytes] = []
    seen: set[tuple[int, int, int]] = set()
    for (x, y, z), block in placements:
        position = (x, y, z)
        if position in seen:
            raise ValueError(f"duplicate structure block position: {position}")
        seen.add(position)
        key = _normalize_block_spec(block)
        if key not in palette_index:
            palette_index[key] = len(palette)
            palette.append(key)
        blocks.append(_block_entry(x, y, z, palette_index[key]))

    root_payload = _compound_payload([
        _named_int("DataVersion", DATA_VERSION),
        _named_int_list("size", list(size)),
        _named_compound_list("palette", [_palette_entry(block) for block in palette]),
        _named_compound_list("blocks", blocks),
        _named_compound_list("entities", []),
    ])
    return bytes([TAG_COMPOUND]) + _u16(0) + root_payload


def _floor(width: int, depth: int, block_id: BlockSpec = "minecraft:smooth_stone") -> list[Placement]:
    return [((x, 0, z), block_id) for x in range(width) for z in range(depth)]


def _overlay(placements: list[Placement], additions: list[Placement]) -> list[Placement]:
    by_position: dict[tuple[int, int, int], BlockSpec] = {position: block_id for position, block_id in placements}
    for position, block_id in additions:
        by_position[position] = block_id
    return [(position, by_position[position]) for position in sorted(by_position)]


def _border(width: int, depth: int, block_id: BlockSpec) -> list[Placement]:
    placements: list[Placement] = []
    for x in range(width):
        placements.append(((x, 0, 0), block_id))
        placements.append(((x, 0, depth - 1), block_id))
    for z in range(1, depth - 1):
        placements.append(((0, 0, z), block_id))
        placements.append(((width - 1, 0, z), block_id))
    return placements


def _station_base(width: int, depth: int, marker: BlockSpec) -> list[Placement]:
    placements = _overlay(_floor(width, depth), _border(width, depth, marker))
    return _overlay(placements, [
        ((1, 1, 1), "minecraft:sea_lantern"),
        ((width - 2, 1, depth - 2), "minecraft:sea_lantern"),
    ])


def _material_release():
    p = _station_base(11, 9, "minecraft:light_blue_concrete")
    p = _overlay(p, [
        ((2, 1, 4), "redstoneengineering:industrial_buffer"),
        ((5, 1, 4), "redstoneengineering:workcell_controller"),
        ((8, 1, 4), "redstoneengineering:industrial_buffer"),
        ((5, 1, 2), "minecraft:yellow_concrete"),
        ((5, 1, 6), "minecraft:lime_concrete"),
    ])
    return (11, 4, 9), p


def _queue_dispatch():
    p = _station_base(11, 9, "minecraft:blue_concrete")
    p = _overlay(p, [
        ((2, 1, 4), "redstoneengineering:industrial_buffer"),
        ((5, 1, 4), "redstoneengineering:workcell_controller"),
        ((8, 1, 3), "redstoneengineering:workcell_controller"),
        ((8, 1, 5), "redstoneengineering:workcell_controller"),
        ((5, 1, 2), "minecraft:redstone_lamp"),
        ((5, 1, 6), "minecraft:redstone_lamp"),
    ])
    return (11, 4, 9), p


def _maintenance_hold():
    p = _station_base(11, 9, "minecraft:yellow_concrete")
    p = _overlay(p, [
        ((3, 1, 4), "redstoneengineering:workcell_controller"),
        ((7, 1, 4), "redstoneengineering:workcell_controller"),
        ((3, 1, 2), "minecraft:lime_concrete"),
        ((7, 1, 2), "minecraft:yellow_concrete"),
        ((3, 1, 6), "minecraft:redstone_lamp"),
        ((7, 1, 6), "minecraft:redstone_lamp"),
    ])
    return (11, 4, 9), p


def _quality_output():
    p = _station_base(11, 9, "minecraft:purple_concrete")
    p = _overlay(p, [
        ((2, 1, 4), "redstoneengineering:workcell_controller"),
        ((5, 1, 4), "redstoneengineering:industrial_buffer"),
        ((8, 1, 4), "redstoneengineering:industrial_buffer"),
        ((5, 1, 2), "minecraft:lime_concrete"),
        ((8, 1, 2), "minecraft:red_concrete"),
    ])
    return (11, 4, 9), p


def _amr_lane():
    width, depth = 21, 9
    p = _station_base(width, depth, "minecraft:orange_concrete")
    lane = [((x, 1, 4), "minecraft:white_concrete") for x in range(2, width - 2)]
    p = _overlay(p, lane + [
        ((2, 1, 2), "redstoneengineering:industrial_buffer"),
        ((18, 1, 6), "redstoneengineering:industrial_buffer"),
        ((2, 1, 4), "minecraft:lime_concrete"),
        ((18, 1, 4), "minecraft:cyan_concrete"),
        ((10, 1, 2), "redstoneengineering:workcell_controller"),
    ])
    return (width, 4, depth), p


def _operations_monitor():
    p = _station_base(11, 9, "minecraft:cyan_concrete")
    p = _overlay(p, [
        ((5, 1, 4), "redstoneengineering:workcell_controller"),
        ((3, 1, 4), "redstoneengineering:operations_monitor"),
        ((7, 1, 4), "redstoneengineering:industrial_buffer"),
        ((5, 1, 2), "minecraft:sea_lantern"),
        ((5, 1, 6), "minecraft:sea_lantern"),
    ])
    return (11, 4, 9), p


def _full_factory():
    width, depth = 48, 48
    p = _overlay(_floor(width, depth, "minecraft:light_gray_concrete"), _border(width, depth, "minecraft:black_concrete"))
    aisle: list[Placement] = []
    for x in range(2, width - 2):
        aisle.append(((x, 1, 23), "minecraft:white_concrete"))
        aisle.append(((x, 1, 24), "minecraft:white_concrete"))
    for z in range(2, depth - 2):
        aisle.append(((23, 1, z), "minecraft:white_concrete"))
        aisle.append(((24, 1, z), "minecraft:white_concrete"))
    p = _overlay(p, aisle + [
        ((23, 1, 23), "redstoneengineering:engineering_compass"),
        ((24, 1, 24), "minecraft:sea_lantern"),
    ])
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


def _write_structure(path: Path, size: tuple[int, int, int], placements: list[Placement]) -> None:
    payload = _structure_bytes(size, placements)
    compressed = gzip.compress(payload, compresslevel=9, mtime=0)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(compressed)
    print(f"generated {path.relative_to(ROOT) if path.is_relative_to(ROOT) else path} ({len(compressed)} bytes)")


def _preset_index_text() -> str:
    lines = [
        "RSE VALIDATION PRESET PACK",
        "path\tlevel\ttitle\tpurpose",
    ]
    for name in sorted(PRESETS):
        preset = PRESETS[name]
        lines.append(f"{name}\t{preset.level}\t{preset.title}\t{preset.purpose}")
    return "\n".join(lines) + "\n"


def _selftest_index_text() -> str:
    lines = [
        "RSE SELF-CHECKING VALIDATION SERIES",
        "path\tlevel\ttitle\tevaluator\texpected\tsettling_ticks\tpurpose",
    ]
    for name in sorted(SELFTESTS):
        test = SELFTESTS[name]
        expected = "runtime" if test.expected_value is None else str(test.expected_value)
        lines.append(
            f"{name}\t{test.level}\t{test.title}\t{test.evaluator}\t{expected}\t{test.settling_ticks}\t{test.purpose}"
        )
    return "\n".join(lines) + "\n"


def _plant_index_text() -> str:
    lines = [
        "RSE INTEGRATED VALIDATION PLANT V1",
        "module\toffset_x\toffset_y\toffset_z\tsize_x\tsize_y\tsize_z",
    ]
    for name in PLANT_STRUCTURE_ORDER:
        size, _placements = PLANT_STRUCTURES[name]()
        offset = PLANT_MODULE_OFFSETS[name]
        lines.append(f"{name}\t{offset[0]}\t{offset[1]}\t{offset[2]}\t{size[0]}\t{size[1]}\t{size[2]}")
    return "\n".join(lines) + "\n"


def generate_presets(structure_dir: Path = PRESET_STRUCTURE_DIR, index_path: Path = PRESET_INDEX_PATH) -> int:
    if structure_dir.exists():
        shutil.rmtree(structure_dir)
    structure_dir.mkdir(parents=True, exist_ok=True)
    for name in sorted(PRESETS):
        preset = PRESETS[name]
        size, placements = preset.builder()
        _write_structure(structure_dir / f"{name}.nbt", size, placements)
    index_path.parent.mkdir(parents=True, exist_ok=True)
    index_path.write_text(_preset_index_text(), encoding="utf-8")
    print(f"generated {index_path.relative_to(ROOT) if index_path.is_relative_to(ROOT) else index_path}")
    return 0


def generate_selftests(structure_dir: Path = SELFTEST_STRUCTURE_DIR, index_path: Path = SELFTEST_INDEX_PATH) -> int:
    if structure_dir.exists():
        shutil.rmtree(structure_dir)
    structure_dir.mkdir(parents=True, exist_ok=True)
    for name in sorted(SELFTESTS):
        test = SELFTESTS[name]
        size, placements = test.builder()
        _write_structure(structure_dir / f"{name}.nbt", size, placements)
    index_path.parent.mkdir(parents=True, exist_ok=True)
    index_path.write_text(_selftest_index_text(), encoding="utf-8")
    print(f"generated {index_path.relative_to(ROOT) if index_path.is_relative_to(ROOT) else index_path}")
    return 0


def generate_plant(structure_dir: Path = PLANT_STRUCTURE_DIR, index_path: Path = PLANT_INDEX_PATH) -> int:
    if structure_dir.exists():
        shutil.rmtree(structure_dir)
    structure_dir.mkdir(parents=True, exist_ok=True)
    for name in PLANT_STRUCTURE_ORDER:
        size, placements = PLANT_STRUCTURES[name]()
        _write_structure(structure_dir / f"{name}.nbt", size, placements)
    index_path.parent.mkdir(parents=True, exist_ok=True)
    index_path.write_text(_plant_index_text(), encoding="utf-8")
    print(f"generated {index_path.relative_to(ROOT) if index_path.is_relative_to(ROOT) else index_path}")
    return 0


def generate() -> int:
    STRUCTURE_DIR.mkdir(parents=True, exist_ok=True)
    stale = {p.stem for p in STRUCTURE_DIR.glob("*.nbt")} - set(EXPECTED_STRUCTURES)
    for name in stale:
        (STRUCTURE_DIR / f"{name}.nbt").unlink()
    for name in EXPECTED_STRUCTURES:
        size, placements = STRUCTURES[name]()
        _write_structure(STRUCTURE_DIR / f"{name}.nbt", size, placements)
    rc = generate_presets()
    if rc:
        return rc
    rc = generate_selftests()
    return rc if rc else generate_plant()


def _zip_info(archive_name: str) -> zipfile.ZipInfo:
    info = zipfile.ZipInfo(archive_name, date_time=(1980, 1, 1, 0, 0, 0))
    info.compress_type = zipfile.ZIP_DEFLATED
    info.external_attr = 0o644 << 16
    return info


def package_presets(
    structure_dir: Path = PRESET_STRUCTURE_DIR,
    index_path: Path = PRESET_INDEX_PATH,
    output: Path = PRESET_PACKAGE_PATH,
) -> int:
    files = sorted(structure_dir.rglob("*.nbt")) if structure_dir.is_dir() else []
    if not files or not index_path.is_file():
        print("ERROR: preset structures/index missing; run 'generate-presets' first.", file=sys.stderr)
        return 2
    output.parent.mkdir(parents=True, exist_ok=True)
    if output.exists():
        output.unlink()
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        archive.writestr(_zip_info("preset-index.txt"), index_path.read_bytes())
        for path in files:
            relative = path.relative_to(structure_dir).as_posix()
            archive.writestr(_zip_info(relative), path.read_bytes())
    print(f"packaged {len(files)} presets -> {output}")
    return 0


def _package_excluded(relative: Path) -> bool:
    if relative.name in {"session.lock", ".DS_Store"}:
        return True
    if relative.parts and relative.parts[0] in {"logs", "crash-reports"}:
        return True
    return False


def package_world(world: Path, output: Path) -> int:
    world = world.resolve()
    output = output.resolve()
    level_dat = world / "level.dat"
    if not world.is_dir() or not level_dat.is_file():
        print(
            "ERROR: package requires a real Minecraft-created 'RSE Validation Factory' save containing level.dat; refusing to fabricate a world.",
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
            relative = path.relative_to(world)
            if _package_excluded(relative):
                continue
            archive_name = (ARCHIVE_ROOT / relative).as_posix()
            archive.writestr(_zip_info(archive_name), path.read_bytes())
    print(f"packaged {world} -> {output}")
    return 0


def _legacy_cli(argv: list[str]) -> list[str]:
    if not argv:
        return argv
    if argv[0] == "--generate-structures":
        return ["generate", *argv[1:]]
    if argv[0] == "--package-world":
        if len(argv) < 2:
            return ["package"]
        return ["package", "--world", argv[1], *argv[2:]]
    return argv


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="RSE Validation Factory, self-test, and integrated plant asset tool")
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("generate", help="generate Validation Factory plus preset, self-test, and integrated plant NBT files")
    sub.add_parser("generate-presets", help="generate only reusable preset NBT files")
    sub.add_parser("generate-selftests", help="generate only self-checking validation NBT files")
    sub.add_parser("generate-plant", help="generate only the seven modular Integrated Validation Plant v1 NBT files")
    sub.add_parser("package-presets", help="package generated presets into a pure NBT ZIP bundle")
    package = sub.add_parser("package", help="package an existing real Validation Factory world")
    package.add_argument("--world", type=Path, default=DEFAULT_WORLD)
    package.add_argument("--output", type=Path, default=PACKAGE_PATH)
    sub.add_parser("clean", help="remove generated validation structures")
    return parser.parse_args(_legacy_cli(list(sys.argv[1:] if argv is None else argv)))


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    if args.command == "generate":
        return generate()
    if args.command == "generate-presets":
        return generate_presets()
    if args.command == "generate-selftests":
        return generate_selftests()
    if args.command == "generate-plant":
        return generate_plant()
    if args.command == "package-presets":
        rc = generate_presets()
        return rc if rc else package_presets()
    if args.command == "package":
        return package_world(args.world, args.output)
    if args.command == "clean":
        if STRUCTURE_DIR.exists():
            shutil.rmtree(STRUCTURE_DIR)
            print(f"removed {STRUCTURE_DIR.relative_to(ROOT)}")
        for path in (PRESET_INDEX_PATH, SELFTEST_INDEX_PATH, PLANT_INDEX_PATH, PRESET_PACKAGE_PATH):
            if path.exists():
                path.unlink()
        return 0
    raise AssertionError(args.command)


if __name__ == "__main__":
    raise SystemExit(main())
