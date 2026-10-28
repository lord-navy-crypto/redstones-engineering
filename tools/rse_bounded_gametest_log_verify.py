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
    progress = b"[07:41:44] [Server thread/INFO] [minecraft/GameTestServer]: [X+_+XX_]\n"
    post = progress * 78000 + filler * 125000
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
        "progress spam does not evict real errors": b"matching_lines=2" in body,
        "expected capture footer": b"=== END BOUNDED CAPTURE ===" in body,
        "stdout suppressed": len(run.stdout) < 32000,
    }
    failures = [name for name, valid in checks.items() if not valid]
    if failures:
        print("RSE BOUNDED GAMETEST LOG VERIFY: FAIL", ", ".join(failures))
        raise SystemExit(1)
    # The triage consumer must never mistake an all-tests-passed footer
    # for acceptance when a real LogTestReporter failure also exists.
    triage = ROOT / "tools/rse_gametest_failure_triage.py"
    if not triage.is_file():
        raise SystemExit("Missing GameTest failure triage tool")
    report = Path(folder) / "triage.md"
    target.write_text(
        "[Server thread/ERROR] [minecraft/LogTestReporter]: frozenregistrytest failed at 1,2,3! Registry is already frozen\n"
        "[Server thread/ERROR] [minecraft/LogTestReporter]: frozenregistrytest failed at 1,2,3! Registry is already frozen\n"
        "All 2 required tests passed :)\n",
        encoding="utf-8",
    )
    subprocess.run([sys.executable, str(triage), str(target), str(report)],
                   capture_output=True, timeout=20, check=True)
    suspicious = report.read_text(encoding="utf-8")
    if ("Distinct reported failures: **1**" not in suspicious
            or "INCOMPLETE OR FAILED" not in suspicious
            or suspicious.count("frozenregistrytest") != 1):
        raise SystemExit("GameTest triage counted duplicate failures or claimed false acceptance")
    target.write_text("All 2 required tests passed :)\n", encoding="utf-8")
    subprocess.run([sys.executable, str(triage), str(target), str(report)],
                   capture_output=True, timeout=20, check=True)
    if ("Evidence result: **PASS (log evidence)**" not in report.read_text(encoding="utf-8")):
        raise SystemExit("GameTest triage failed to recognize a clean formal pass")
    target.write_text("No conclusive test summary yet\n", encoding="utf-8")
    subprocess.run([sys.executable, str(triage), str(target), str(report)],
                   capture_output=True, timeout=20, check=True)
    if ("INCOMPLETE OR FAILED" not in report.read_text(encoding="utf-8")):
        raise SystemExit("GameTest triage treated a truncated log as a pass")

print("RSE BOUNDED GAMETEST LOG VERIFY: PASS")
print(" synthetic >8MiB stream preserves diagnostic/error/result while bounding output")
