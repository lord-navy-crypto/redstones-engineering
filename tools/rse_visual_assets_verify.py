#!/usr/bin/env python3
"""RSE visual resource integrity audit.

Checks semantic RSE texture references, inherited/explicit particle sprites, and
GeckoLib mechatronics texture dispatch without changing simulation behavior.
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
MECH_MODEL = ROOT / "src/main/java/dev/redstoneengineering/client/MechatronicsGeoModel.java"

PLACEHOLDER_TEXTURES = {
    "minecraft:block/iron_block",
    "minecraft:textures/block/iron_block.png",
}
MACHINES = (
    "servo_actuator",
    "pneumatic_cylinder",
    "pneumatic_proportional_valve",
)
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

    for texture in BLOCK_TEXTURES.rglob("*.png"):
        rel = texture.relative_to(BLOCK_TEXTURES).as_posix()
        if not RESOURCE_NAME.fullmatch(rel):
            errors.append(f"{texture.relative_to(ROOT)}: non-canonical texture resource name")

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

        # minecraft:block/cube_all already defines particle="#all". A child may
        # omit particle and inherit it; if the child overrides particle, keep it
        # synchronized with the body texture.
        if data.get("parent") == "minecraft:block/cube_all" and isinstance(textures.get("all"), str):
            particle = textures.get("particle")
            if particle is not None and particle not in (textures["all"], "#all"):
                errors.append(
                    f"{model.relative_to(ROOT)}: cube_all particle override must match textures.all "
                    f"({textures['all']!r}), got {particle!r}"
                )

    # GeckoLib geometry files are geometry-only; texture selection lives in the
    # Java GeoModel. Validate both sides instead of requiring a texture field in
    # .geo.json files.
    for machine in MACHINES:
        geo_path = GEO_MODELS / f"{machine}.geo.json"
        texture_path = BLOCK_TEXTURES / f"{machine}.png"
        if not geo_path.is_file():
            errors.append(f"{geo_path.relative_to(ROOT)}: required GeckoLib geometry is missing")
        else:
            data = load_json(geo_path, errors)
            if data is not None:
                for ref in walk_strings(data):
                    if ref in PLACEHOLDER_TEXTURES or "minecraft:textures/block/iron_block" in ref:
                        errors.append(f"{geo_path.relative_to(ROOT)}: vanilla iron-block placeholder remains: {ref!r}")
        if not texture_path.is_file():
            errors.append(f"{texture_path.relative_to(ROOT)}: required semantic machine texture is missing")

    if not MECH_MODEL.is_file():
        errors.append(f"{MECH_MODEL.relative_to(ROOT)}: GeckoLib texture dispatcher is missing")
    else:
        java = MECH_MODEL.read_text(encoding="utf-8")
        if "textures/block/iron_block.png" in java:
            errors.append(f"{MECH_MODEL.relative_to(ROOT)}: vanilla iron-block GeckoLib placeholder remains")
        for machine in MACHINES:
            expected = f"textures/block/{machine}.png"
            if expected not in java:
                errors.append(f"{MECH_MODEL.relative_to(ROOT)}: missing semantic GeckoLib texture {expected!r}")

    if errors:
        print(f"RSE visual asset audit FAILED ({len(errors)} issue(s)):", file=sys.stderr)
        for error in errors:
            print(f" - {error}", file=sys.stderr)
        return 1

    print("RSE visual asset audit passed: semantic textures, inherited/explicit particles, and GeckoLib resources are consistent.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
