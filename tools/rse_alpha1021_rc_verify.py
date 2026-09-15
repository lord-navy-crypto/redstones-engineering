#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]

def read(path: str) -> str:
    p = ROOT / path
    if not p.is_file():
        fail(f"missing required RC file: {path}")
    return p.read_text(encoding="utf-8")

def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        fail(f"missing {label}: {needle!r}")

def forbid(text: str, needle: str, label: str) -> None:
    if needle in text:
        fail(f"forbidden {label}: {needle!r}")

def fail(message: str) -> None:
    print(f"[RSE Alpha 1.0.21 RC VERIFY] FAIL: {message}", file=sys.stderr)
    raise SystemExit(1)

props = read("gradle.properties")
workflow = read(".github/workflows/build.yml")
manifest = read("ALPHA1_0_21_MANIFEST.txt")
guide = read("docs/ALPHA1_0_21_RC1_TESTING_GUIDE.md")
readme = read("README.md")

require(props, "mod_version=1.0.21-alpha-rc1", "RC artifact version")
require(manifest, "Artifact: 1.0.21-alpha-rc1", "manifest artifact identity")
require(manifest, "Integrated Systems Stabilization Test Candidate", "manifest milestone")
require(readme, "1.0.21-alpha-rc1", "README artifact identity")
require(readme, "ALPHA1_0_21_MANIFEST.txt", "README manifest link")
require(guide, "RSE Alpha 1.0.21 RC1", "testing-guide identity")
require(guide, "blocking NeoForge GameTests", "testing-guide runtime gate")
require(guide, "Do not substitute an older green commit", "latest-HEAD rule")

require(workflow, "Minecraft topology GameTests (release candidate gate)", "blocking RC GameTest step")
require(workflow, "startsWith(github.head_ref, 'test-candidate-')", "RC branch selector")
require(workflow, "timeout --signal=TERM 12m ./gradlew runGameTestServer", "RC GameTest runtime invocation")
require(workflow, "Too many chained neighbor updates", "neighbor-update safety check")
require(workflow, "Release-candidate GameTests completed without an all-tests-passed summary", "required GameTest summary")

start = workflow.index("- name: Minecraft topology GameTests (release candidate gate)")
end_marker = "- name: Minecraft topology GameTests (manual diagnostic)"
if end_marker not in workflow[start:]:
    fail("manual GameTest marker missing after RC gate")
end = workflow.index(end_marker, start)
rc_block = workflow[start:end]
forbid(rc_block, "continue-on-error: true", "non-blocking RC GameTest configuration")
require(rc_block, "set -euo pipefail", "fail-closed RC shell")
require(rc_block, "exit 124", "RC timeout failure")
require(rc_block, "exit \"$gametest_status\"", "RC non-zero runtime failure")
require(rc_block, "exit 1", "RC semantic failure")

# The RC is a stabilization snapshot, not a feature branch. Its guide must explicitly exercise the
# high-risk integration boundaries introduced by the latest vertical-depth phase.
for phrase in (
    "Redstone → Lapis",
    "Instrument Cable vs Shielded Instrument Cable",
    "8-bit Bus / Serial / Differential",
    "Copper power/load domain",
    "Guided optical segment budget",
    "Pneumatic pressure-response test",
    "Radio link margin and interference",
    "Save / reload / shutdown",
):
    require(guide, phrase, f"integrated RC test coverage for {phrase}")

print("[RSE Alpha 1.0.21 RC VERIFY] PASS")
print("  version: 1.0.21-alpha-rc1")
print("  RC GameTests: blocking on test-candidate-* PRs")
print("  integrated one-pass testing guide: present")
