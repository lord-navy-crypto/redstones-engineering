#!/usr/bin/env python3
"""RSE installation preflight: read-only, offline and safe for game instances.

Validates actual NeoForge metadata, required versions and duplicate JARs.
It never installs, downloads, deletes, renames or modifies a user's mods.
Python 3.9+ (standard library only).
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import zipfile

REQUIRED = {
    "redstoneengineering": ((1, 0, 20), (1, 0, 21), "RSE 1.0.20-alpha 测试 JAR"),
    "jade": ((15, 10, 6), (16, 0, 0), "Jade（工程信息提示）"),
    "geckolib": ((4, 9, 2), (5, 0, 0), "GeckoLib（设备动画）"),
    "ldlib2": ((2, 2, 26), (3, 0, 0), "LDLib2（工程界面）"),
}
OFFICIAL_VERSIONS = {
    "jade": "https://modrinth.com/mod/jade/version/15.10.6%2Bneoforge",
    "geckolib": "https://modrinth.com/mod/geckolib/version/tPkJmim6",
    "ldlib2": "https://modrinth.com/mod/ldlib/version/mc1.21.1-2.2.26-neoforge",
}

def default_mods() -> Path:
    home = Path.home()
    paths = [Path.cwd() / "mods"]
    if sys.platform == "darwin":
        paths.append(home / "Library" / "Application Support" / "minecraft" / "mods")
    elif os.name == "nt":
        paths.append(Path(os.environ.get("APPDATA", str(home))) / ".minecraft" / "mods")
    else:
        paths.append(home / ".minecraft" / "mods")
    return next((p for p in paths if p.is_dir()), paths[-1])

def entries_in_toml(text: str) -> list[tuple[str, str]]:
    pieces = re.split(r'^\s*\[\[mods\]\]\s*$', text, flags=re.MULTILINE)
    entries = []
    for piece in pieces[1:]:
        block = re.split(r'^\s*\[\[?[^]\n]+\]\]?\s*$', piece, maxsplit=1, flags=re.MULTILINE)[0]
        def value(key: str) -> str | None:
            found = re.search(r'^\s*' + re.escape(key) + r'\s*=\s*["\x27]([^"\x27\r\n]*)["\x27]', block, flags=re.MULTILINE)
            return found.group(1) if found else None
        mod_id = value("modId")
        if mod_id:
            entries.append((mod_id.lower(), value("version") or "UNKNOWN"))
    return entries

def inspect_jar(path: Path) -> tuple[str, list[tuple[str, str]], str]:
    if path.name.lower().endswith(("-sources.jar", "-javadoc.jar")):
        return "sources", [], "源码/文档 JAR 不是运行版"
    try:
        with zipfile.ZipFile(path) as jar:
            files = set(jar.namelist())
            name = "META-INF/neoforge.mods.toml"
            if name in files:
                if jar.getinfo(name).file_size > 1024 * 1024:
                    return "invalid", [], "模组元数据大小异常"
                entries = entries_in_toml(jar.read(name).decode("utf-8", errors="replace"))
                return "neoforge", entries, "" if entries else "没有 [[mods]] 条目"
            if "META-INF/mods.toml" in files:
                return "forge", [], "只有 Forge 元数据，不是确定的 NeoForge 运行版"
            if "fabric.mod.json" in files:
                return "fabric", [], "Fabric 模组不能替代 NeoForge 版本"
            return "other", [], "没有 NeoForge 模组元数据"
    except (OSError, zipfile.BadZipFile, ValueError, RuntimeError) as exc:
        return "invalid", [], "无法读取 JAR：" + type(exc).__name__

def parse_version(value: str) -> tuple[int, int, int] | None:
    found = re.match(r'^(\d+)\.(\d+)\.(\d+)(?:$|[-+.]|\b)', value)
    return tuple(map(int, found.groups())) if found else None

def digest(path: Path) -> str:
    value = hashlib.sha256()
    with path.open("rb") as handle:
        for buf in iter(lambda: handle.read(1024 * 1024), b""):
            value.update(buf)
    return value.hexdigest()

def check(mods: Path, manifest: Path | None = None) -> dict:
    report = {"ready": False, "mods": {}, "errors": [], "warnings": [],
              "integrity": "not_checked",
              "note": "只验证文件版本；不代表游戏或 GameTest 已通过。"}
    if not mods.is_dir():
        report["errors"].append("找不到 mods 目录，请指定实际使用的实例文件夹")
        return report
    paths = sorted(mods.glob("*.jar"), key=lambda p: p.name.lower())
    if not paths:
        report["errors"].append("mods 目录内没有任何 .jar 文件")
    found: dict[str, list[tuple[Path, str]]] = {}
    for path in paths:
        loader, models, issue = inspect_jar(path)
        if loader != "neoforge":
            if loader in ("sources", "fabric", "forge", "invalid"):
                report["warnings"].append(path.name + "：" + issue)
            continue
        for mod_id, version in models:
            found.setdefault(mod_id, []).append((path, version))
    for mod_id, (minimum, maximum, label) in REQUIRED.items():
        entries = found.get(mod_id, [])
        report["mods"][mod_id] = {"files": [p.name for p, _ in entries],
                                  "versions": [v for _, v in entries]}
        if not entries:
            report["errors"].append("缺少 " + label)
            if mod_id in OFFICIAL_VERSIONS:
                report["warnings"].append("官方版本页：" + OFFICIAL_VERSIONS[mod_id])
        elif len(entries) > 1:
            report["errors"].append("重复安装 " + label + "：" + ", ".join(p.name for p, _ in entries))
        else:
            v = parse_version(entries[0][1])
            if v is None:
                report["errors"].append(label + " 版本无法识别：" + entries[0][1])
            elif not minimum <= v < maximum:
                report["errors"].append(label + " 版本不符：" + entries[0][1])
    # If a kit manifest exists it is a trust boundary: malformed metadata must
    # fail, not quietly downgrade integrity verification into a warning.
    if manifest and manifest.is_file() and len(found.get("redstoneengineering", [])) == 1:
        try:
            obj = json.loads(manifest.read_text(encoding="utf-8"))
            if not isinstance(obj, dict):
                raise ValueError("not a JSON object")
            expected = obj.get("jar_sha256")
            if not isinstance(expected, str) or not re.fullmatch(r"[a-fA-F0-9]{64}", expected):
                raise ValueError("missing/invalid jar_sha256")
            rse_path = found["redstoneengineering"][0][0]
            declared_name = obj.get("jar_name")
            if declared_name is not None and declared_name != rse_path.name:
                report["errors"].append("RSE JAR 文件名与测试包 BUILD_INFO.json 不一致")
            match = digest(rse_path) == expected.lower()
            report["mods"]["redstoneengineering"]["sha256_matches_test_build"] = match
            report["integrity"] = "verified" if match and (declared_name is None or declared_name == rse_path.name) else "failed"
            if not match:
                report["errors"].append("RSE JAR 的 SHA-256 与本测试包原始构建不一致")
        except (OSError, ValueError, UnicodeError) as exc:
            report["integrity"] = "failed"
            report["errors"].append("BUILD_INFO.json 无法用于安全校验：" + type(exc).__name__)
    elif manifest and not manifest.is_file():
        report["warnings"].append("无 BUILD_INFO.json：仅检查模组元数据，未验证 RSE JAR 来源")
    report["ready"] = not report["errors"]
    return report

def main() -> int:
    parser = argparse.ArgumentParser(description="RSE NeoForge 1.21.1 本地前置检查，仅读文件")
    parser.add_argument("--mods", type=Path, help="指定 Minecraft 实例的 mods 文件夹")
    parser.add_argument("--manifest", type=Path, default=Path(__file__).resolve().parent / "BUILD_INFO.json",
                        help="测试包 BUILD_INFO.json 路径")
    parser.add_argument("--minecraft", default="", help="可选：启动器显示的 Minecraft 版本")
    parser.add_argument("--neoforge", default="", help="可选：启动器显示的 NeoForge 版本")
    parser.add_argument("--report", type=Path, help="可选：保存匿名化 JSON 检查结果")
    args = parser.parse_args()
    folder = args.mods.expanduser() if args.mods else default_mods()
    result = check(folder, args.manifest)
    if args.minecraft and args.minecraft != "1.21.1":
        result["errors"].append("Minecraft 应为 1.21.1")
    if args.neoforge:
        neo = parse_version(args.neoforge)
        if neo is None or not (21, 1, 249) <= neo < (21, 2, 0):
            result["errors"].append("NeoForge 应为 21.1.249 或更新的兼容 21.1.x")
    result["ready"] = not result["errors"]
    print("RSE 1.0.20-alpha / PR #229 · 前置核查（只读，不联网）")
    print("目录：", folder)
    for mod_id, info in result["mods"].items():
        print(("✓" if info["files"] else "✗"), mod_id, " | ".join(info["files"]) or "缺失",
              " / ".join(info["versions"]))
    for error in result["errors"]:
        print("[错误]", error)
    for warning in result["warnings"]:
        print("[提示]", warning)
    print("检查结果：", "文件初检通过" if result["ready"] else "文件初检未通过")
    print("此外请在启动器确认 Java 21 / Minecraft 1.21.1 / NeoForge 21.1.249+ (21.1.x)。")
    print("不等于客户端 UI 或 Minecraft GameTest 验收通过。")
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print("JSON 报告已保存", args.report)
    return 0 if result["ready"] else 2

if __name__ == "__main__":
    sys.exit(main())
