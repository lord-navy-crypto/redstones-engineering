#!/usr/bin/env python3
"""Small functional fixture for RSE bounded GameTest log capture."""
from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
script = ROOT / "tools/rse_bounded_gametest_log.py"
if not script.is_file():
    raise SystemExit("Missing GameTest bounded capture tool")

with TemporaryDirectory(prefix="rse-gametest-log-") as folder:
    target = Path(folder) / "runtime.log"
    # The middle includes a critical error that MUST remain in the marker
    # section even after the rotating tail evicts its surrounding context.
    filler = b"benign repetitive game-server status line 0123456789\n"
    pre = b"RSE_TEST_BOOT unique startup header\n" + filler * 26000
    mid = b"ERROR: RSE_TEST_MIDSTREAM_FAIL unique root-cause context\n"
    post = filler * 165000
    final = b"All 2 required tests passed :)\nRSE_TEST_FOOTER unique shutdown\n"
    payload = pre + mid + post + final
    run = subprocess.run(
        [sys.executable, str(script), str(target)],
        input=payload,
        capture_output=True,
        timeout=45,
        check=True,
    )
    body = target.read_bytes()
    checks = {
        "startup header": b"RSE_TEST_BOOT" in body,
        "dropped middle from tail but retained diagnostic": b"RSE_TEST_MIDSTREAM_FAIL" in body,
        "conclusive all-tests summary": b"All 2 required tests passed :)" in body,
        "shutdown tail": b"RSE_TEST_FOOTER" in body,
        "bounded file": len(body) <= 9 * 1024 * 1024,
        "full source accounting": f"source_bytes={len(payload)}".encode() in body,
        "expected capture footer": b"=== END BOUNDED CAPTURE ===" in body,
        "stdout suppressed": len(run.stdout) < 32000,
    }
    failures = [name for name, valid in checks.items() if not valid]
    if failures:
        print("RSE BOUNDED GAMETEST LOG VERIFY: FAIL", ", ".join(failures))
        raise SystemExit(1)
print("RSE BOUNDED GAMETEST LOG VERIFY: PASS")
print(" synthetic >8MiB stream preserves diagnostic/error/result while bounding output")
