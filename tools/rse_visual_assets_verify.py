#!/usr/bin/env python3
"""RSE visual resource integrity audit.

Keeps block texture references, particle sprites, and GeckoLib machine textures
inside the redstoneengineering namespace. Vanilla parent geometry is allowed;
vanilla placeholder *textures* are not.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/redstoneengineering"
BLOCK_MODELS = ASSETS / "models/block"
BLOCK_TEXTURES = ASSETS / "textures/block"
GEO_MODELS = ASSETS / "geo/block"

PLACEHOLDER_TEXTURES = {
    "minecraft:block/iron_block",
    "minecraft:textures/block/iron_block.png",
}
MACHINE_TEXTURES = {
    "servo_actuator": GEO_MODELS / "servo_actuator.geo.json",
    "pneumatic_cylinder": GEO_MODELS / "pneumatic_cylinder.geo.json",
    "pneumatic_proportional_valve": GEO_MODELS / "pneumatic_proportional_valve.geo.json",
}
RESOURCE_NAME = re.compile(r"^[a-z0-9_./-]+$")


def load_json(path: Path, errors: list[str]) -> dict | None:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        errors.append(f"{path.relative_to(ROOT)}: invalid JSON: {exc}")
        return None


def rse_block_texture_exists(ref: str) -> bool:
    prefix = "redstoneengineering:block/"
    if not ref.startswith(prefix):
        return True
    name = ref[len(prefix):]
    return (BLOCK_TEXTURES / f"{name}.png").is_file()


def walk_strings(value):
    if isinstance(value, dict):
        for child in value.values():
            yield from walk_strings(child)
    elif isinstance(value, list):
        for child in value:
            yield from walk_strings(child)
    elif isinstance(value, str):
        yield value


def main() -> int:
    errors: list[str] = []

    if not BLOCK_MODELS.is_dir() or not BLOCK_TEXTURES.is_dir():
        print("RSE visual audit: required asset directories are missing", file=sys.stderr)
        return 2

    # Resource names must be stable, portable Minecraft identifiers.
    for texture in BLOCK_TEXTURES.rglob("*.png"):
        rel = texture.relative_to(BLOCK_TEXTURES).as_posix()
        if not RESOURCE_NAME.fullmatch(rel):
            errors.append(f"{texture.relative_to(ROOT)}: non-canonical texture resource name")

    # Ordinary block models: ban known placeholder textures, validate RSE refs,
    # and keep simple cube particle sprites synchronized with their body texture.
    for model in sorted(BLOCK_MODELS.rglob("*.json")):
        data = load_json(model, errors)
        if data is None:
            continue
        textures = data.get("textures", {})
        if textures is not None and not isinstance(textures, dict):
            errors.append(f"{model.relative_to(ROOT)}: textures must be an object")
            continue

        for key, ref in textures.items():
            if not isinstance(ref, str):
                continue
            if ref in PLACEHOLDER_TEXTURES:
                errors.append(f"{model.relative_to(ROOT)}: placeholder texture {ref!r} at textures.{key}")
            if ref.startswith("redstoneengineering:block/") and not rse_block_texture_exists(ref):
                errors.append(f"{model.relative_to(ROOT)}: missing texture for {ref!r}")

        # cube_all models with an explicit body texture should explicitly declare
        # the same particle sprite. This makes hit/break particles deterministic.
        if data.get("parent") == "minecraft:block/cube_all" and isinstance(textures.get("all"), str):
            particle = textures.get("particle")
            if particle != textures["all"]:
                errors.append(
                    f"{model.relative_to(ROOT)}: cube_all particle must match textures.all "
                    f"({textures['all']!r}), got {particle!r}"
                )

    # GeckoLib mechatronics models: render-only assets must use semantic RSE
    # textures rather than the vanilla iron-block placeholder.
    for machine, geo_path in MACHINE_TEXTURES.items():
        expected_file = BLOCK_TEXTURES / f"{machine}.png"
        if not expected_file.is_file():
            errors.append(f"{expected_file.relative_to(ROOT)}: required semantic machine texture is missing")
        data = load_json(geo_path, errors)
        if data is None:
            continue
        strings = list(walk_strings(data))
        for ref in strings:
            if ref in PLACEHOLDER_TEXTURES or "minecraft:textures/block/iron_block" in ref:
                errors.append(f"{geo_path.relative_to(ROOT)}: vanilla iron-block placeholder remains: {ref!r}")
        expected_ref = f"redstoneengineering:textures/block/{machine}.png"
        texture_refs = [s for s in strings if "textures/" in s and s.endswith(".png")]
        if expected_ref not in texture_refs:
            errors.append(
                f"{geo_path.relative_to(ROOT)}: expected semantic texture {expected_ref!r}; "
                f"found {texture_refs or 'none'}"
            )

    if errors:
        print(f"RSE visual asset audit FAILED ({len(errors)} issue(s)):", file=sys.stderr)
        for error in errors:
            print(f" - {error}", file=sys.stderr)
        return 1

    print("RSE visual asset audit passed: block textures, particles, and GeckoLib machine resources are consistent.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
