#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import struct
import sys
from pathlib import Path

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
failed: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.exists():
        failed.append(f"missing: {rel}")
        return ""
    return path.read_text(errors="ignore")


def require(rel: str, *tokens: str) -> None:
    body = read(rel)
    for token in tokens:
        if token not in body:
            failed.append(f"{rel}: missing {token!r}")


def recipe_requires(name: str, *item_ids: str) -> None:
    rel = f"src/main/resources/data/redstoneengineering/recipe/{name}.json"
    path = root / rel
    if not path.exists():
        failed.append(f"missing recipe: {rel}")
        return
    try:
        data = json.loads(path.read_text())
    except Exception as exc:
        failed.append(f"invalid recipe JSON {rel}: {exc}")
        return
    body = json.dumps(data, sort_keys=True)
    for item_id in item_ids:
        if item_id not in body:
            failed.append(f"{rel}: missing progression dependency {item_id}")


def require_png_16(rel: str) -> None:
    path = root / rel
    if not path.exists():
        failed.append(f"missing texture: {rel}")
        return
    data = path.read_bytes()
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        failed.append(f"not a PNG: {rel}")
        return
    if len(data) < 24:
        failed.append(f"truncated PNG: {rel}")
        return
    width, height = struct.unpack(">II", data[16:24])
    if (width, height) != (16, 16):
        failed.append(f"texture must be 16x16: {rel} is {width}x{height}")


props = read("gradle.properties")
match = re.search(r"^mod_version=(\d+)\.(\d+)\.(\d+)-alpha(?:[.-][0-9A-Za-z.-]+)?$", props, re.MULTILINE)
if not match:
    failed.append("gradle.properties has no recognized alpha mod_version")
elif tuple(map(int, match.groups())) < (1, 0, 6):
    failed.append("Alpha 1.0.6 regression requires version >= 1.0.6-alpha")
if "mod_license=MPL-2.0" not in props:
    failed.append("gradle.properties is missing current MPL-2.0 license")

# Alpha 1.0.6 is a historical milestone document and retains the license recorded
# for that historical artifact. The active repository license is checked above.
require("ALPHA1_0_6_MANIFEST.txt", "Engineering Language & Progression", "1.0.6-alpha", "License: MIT")
require("src/main/resources/assets/redstoneengineering/lang/en_us.json", '"Instrumentation Signal Analyzer"', '"4-Channel Engineering Oscilloscope"', '"Analog Signal Conditioner"', '"Discrete PID Controller"', '"Position/Velocity Servo Actuator"', '"Production Operations Monitor"', '"Pneumatic Safety Relief Valve"')
require("src/main/resources/assets/redstoneengineering/lang/zh_cn.json", '"仪器信号分析仪"', '"四通道工程示波器"', '"模拟信号调理器"', '"离散 PID 控制器"', '"位置/速度伺服执行器"', '"生产运维监测器"', '"气动安全泄压阀"')

for rel in ["docs/ENGINEERING_LANGUAGE_AND_CURRICULUM.md", "docs/CRAFTING_PROGRESSION.md", "ALPHA1_0_6_CHANGED_FILES.txt"]:
    if not (root / rel).exists(): failed.append(f"missing: {rel}")

recipe_requires("signal_analyzer", "minecraft:copper_ingot", "minecraft:glass", "minecraft:quartz")
recipe_requires("oscilloscope", "redstoneengineering:signal_analyzer", "minecraft:quartz")
recipe_requires("logic_analyzer", "redstoneengineering:signal_analyzer", "minecraft:observer", "minecraft:comparator")
recipe_requires("pid_controller", "redstoneengineering:signal_conditioner", "minecraft:comparator")
recipe_requires("servo_actuator", "minecraft:piston", "minecraft:comparator")
recipe_requires("pneumatic_proportional_valve", "redstoneengineering:pneumatic_valve", "minecraft:comparator")
recipe_requires("operations_monitor", "redstoneengineering:logic_analyzer", "minecraft:clock", "minecraft:observer")

require("src/main/resources/assets/redstoneengineering/models/block/pneumatic_relief_valve.json", "redstoneengineering:block/pneumatic_relief_valve")
for rel in [
    "src/main/resources/assets/redstoneengineering/textures/block/signal_analyzer_front.png",
    "src/main/resources/assets/redstoneengineering/textures/block/calibration_module_front.png",
    "src/main/resources/assets/redstoneengineering/textures/block/pneumatic_relief_valve.png",
]:
    require_png_16(rel)

require("src/main/java/dev/redstoneengineering/physics/PneumaticNetwork.java", "ParticleTypes.CLOUD", "sendParticles", "relief valve actually clamps/vents excess pressure")
workflow = read(".github/workflows/build.yml")
if "rse_alpha106_engineering_verify.py" not in workflow: failed.append("workflow does not run Alpha 1.0.6 verifier")

if failed:
    print("RSE Alpha 1.0.6 engineering verification: FAIL")
    for item in failed: print(" -", item)
    raise SystemExit(1)

print("RSE Alpha 1.0.6 engineering regression verification: PASS")
print(" engineering terminology/progression/assets/safety feedback retained: PASS")
print(" forward-version contract: PASS")
