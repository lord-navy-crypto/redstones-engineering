#!/usr/bin/env python3
"""Create a reproducible, offline/read-only RSE install kit from one CI-built JAR."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile

from rse_install_preflight import inspect_jar, parse_version, REQUIRED

ROOT = Path(__file__).resolve().parent.parent
ZIP_TIMESTAMP = (2024, 1, 1, 0, 0, 0)

WINDOWS = r'''@echo off
setlocal
where py >nul 2>&1
if %errorlevel%==0 (
  py -3 "%~dp0rse_install_preflight.py" %*
) else (
  python "%~dp0rse_install_preflight.py" %*
)
set "EXITCODE=%errorlevel%"
echo.
pause
exit /b %EXITCODE%
'''
MACOS = r'''#!/bin/zsh
DIR="$(cd "$(dirname "$0")" && pwd)"
python3 "$DIR/rse_install_preflight.py" "$@"
code=$?
echo "Press Enter to close..."
read _
exit $code
'''

def sha256(blob: bytes) -> str:
    return hashlib.sha256(blob).hexdigest()

def build_kit(jar: Path, out: Path, source_head: str, checkout_sha: str, preflight: Path, guide: Path) -> Path:
    """Fail closed if the selected artifact is not the expected RSE NeoForge build."""
    for key, value in (("source head", source_head), ("checkout SHA", checkout_sha)):
        if not re.fullmatch(r"[0-9a-fA-F]{40}", value):
            raise ValueError(f"{key} must be a full 40-hex-digit commit SHA")
    if not jar.is_file() or not preflight.is_file() or not guide.is_file():
        raise FileNotFoundError("Missing RSE JAR, preflight script or install guide")
    loader, entries, reason = inspect_jar(jar)
    versions = [version for mod_id, version in entries if mod_id == "redstoneengineering"]
    if loader != "neoforge" or len(versions) != 1:
        raise ValueError(f"Expected one NeoForge redstoneengineering entry, got {reason or loader}")
    version = versions[0]
    parsed = parse_version(version)
    minimum, maximum, _ = REQUIRED["redstoneengineering"]
    if parsed is None or not minimum <= parsed < maximum:
        raise ValueError(f"RSE version outside test-kit contract: {version}")
    if jar.name != f"redstoneengineering-{version}.jar":
        raise ValueError("JAR file name and internal mod version disagree")
    jar_bytes = jar.read_bytes()
    digest = sha256(jar_bytes)
    manifest = {
        "schema_version": 1,
        "project": "Redstone Systems Engineering",
        "minecraft_version": "1.21.1",
        "neoforge_minimum": "21.1.249",
        "java_version": 21,
        "rse_version": version,
        "jar_name": jar.name,
        "jar_sha256": digest,
        "source_head_sha": source_head.lower(),
        "build_checkout_sha": checkout_sha.lower(),
        "note": "PR CI checkout SHA may be a synthetic test-merge commit; runtime validation is separate.",
    }
    files = {
        f"RSE_JAR/{jar.name}": jar_bytes,
        "BUILD_INFO.json": (json.dumps(manifest, ensure_ascii=False, indent=2) + "\n").encode(),
        "SHA256SUMS.txt": f"{digest}  RSE_JAR/{jar.name}\n".encode(),
        "rse_install_preflight.py": preflight.read_bytes(),
        "check_rse_windows.bat": WINDOWS.replace("\n", "\r\n").encode(),
        "check_rse_macos.command": MACOS.encode(),
        "README_zh-CN.md": guide.read_bytes(),
    }
    out.mkdir(parents=True, exist_ok=True)
    dest = out / f"rse-install-kit-{version}-{source_head[:12].lower()}.zip"
    with zipfile.ZipFile(dest, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name in sorted(files):
            info = zipfile.ZipInfo(name, date_time=ZIP_TIMESTAMP)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = ((0o755 if name.endswith(".command") else 0o644) << 16)
            archive.writestr(info, files[name], compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    with zipfile.ZipFile(dest) as archive:
        if archive.testzip() is not None:
            raise ValueError("ZIP CRC verification failed")
        if sha256(archive.read(f"RSE_JAR/{jar.name}")) != digest:
            raise ValueError("Packaged JAR checksum mismatch")
        if json.loads(archive.read("BUILD_INFO.json")) != manifest:
            raise ValueError("Packaged build manifest mismatch")
    return dest

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--out", type=Path, default=Path("build/install-kit"))
    parser.add_argument("--source-head", required=True)
    parser.add_argument("--checkout-sha", required=True)
    parser.add_argument("--preflight", type=Path, default=ROOT / "tools/rse_install_preflight.py")
    parser.add_argument("--guide", type=Path, default=ROOT / "docs/RSE_INSTALL_KIT_zh-CN.md")
    args = parser.parse_args()
    result = build_kit(args.jar, args.out, args.source_head, args.checkout_sha, args.preflight, args.guide)
    print(f"Verified install kit: {result}")
    print(f"ZIP SHA-256: {sha256(result.read_bytes())}")

if __name__ == "__main__":
    main()
