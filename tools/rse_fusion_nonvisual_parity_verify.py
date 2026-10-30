#!/usr/bin/env python3
"""RSE technology-code parity and no-visual-change regression gate.

References are pinned to reviewed Git commits. These are Git blob equality
checks, not claims of physical runtime correctness or Minecraft GUI acceptance.
"""
from __future__ import annotations

from collections import Counter
from datetime import datetime, timezone
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
TECH_REFERENCE = "b321325d792fb446295e8e095e15195d1e4b81ba"  # PR 241, latest reviewed code
FROZEN_VISUAL = "a9ba1da067e629d51440a6f03e22b5aaec744618"  # accepted Oct28 fusion UI
HISTORICAL_REFERENCES = {
    "PR230_pioneer": "1ba69f8cae64e16dc1aa742dd25c6320da458e11",
    "PR239_diagnostics": "a815e257db15d5c728919530d902beb2b0c9c79c",
    "PR240_superseded_visual": "13caec28fee2815b95f140e16df38f5f4fc0a562",
}
EXPECTED_TECH_FILES = 756  # exact scoped file count of reviewed PR241
REPORT_JSON = ROOT / ".rse-audit" / "rse-fusion-nonvisual-parity.json"
REPORT_MD = ROOT / ".rse-audit" / "rse-fusion-nonvisual-parity.md"


def git(*args: str, allow_failure: bool = False) -> subprocess.CompletedProcess:
    return subprocess.run(
        ["git", *args], cwd=ROOT, stdout=subprocess.PIPE,
        stderr=subprocess.PIPE, check=not allow_failure
    )


def ensure_commit(sha: str) -> None:
    probe = git("cat-file", "-e", sha + "^{commit}", allow_failure=True)
    if probe.returncode == 0:
        return
    # The workflow uses fetch-depth: 0, but a pinned PR SHA can be
    # unreachable through default refs when another PR is closed.
    fetch = git("fetch", "--no-tags", "origin", sha, allow_failure=True)
    if fetch.returncode or git("cat-file", "-e", sha + "^{commit}", allow_failure=True).returncode:
        raise RuntimeError(
            "Reviewed Git reference cannot be verified: " + sha +
            " (fetch: " + fetch.stderr.decode(errors="replace")[:300] + ")"
        )


def tree(ref: str) -> dict[str, str]:
    raw = git("ls-tree", "-r", "-z", ref, "--", "src/").stdout
    out: dict[str, str] = {}
    for item in raw.split(b"\x00"):
        if not item:
            continue
        meta, path = item.split(b"\t", 1)
        mode, kind, blob_sha = meta.split()
        if kind != b"blob":
            continue
        out[path.decode("utf-8")] = blob_sha.decode("ascii")
    return out


def visual(path: str) -> bool:
    # All client-side source, GUI presentation (except server-owned menus),
    # and graphical assets are frozen to the already accepted fusion state.
    return (
        path.startswith("src/main/java/dev/redstoneengineering/client/")
        or (
            path.startswith("src/main/java/dev/redstoneengineering/ui/")
            and not path.startswith("src/main/java/dev/redstoneengineering/ui/menu/")
        )
        or path.startswith("src/main/resources/assets/")
        or path.startswith("src/main/java/dev/redstoneengineering/render/")
        or path.startswith("src/main/java/dev/redstoneengineering/visual/")
    )


def technology(path: str) -> bool:
    return path.startswith("src/") and not visual(path)


def mismatch(reference: dict[str, str], actual: dict[str, str], scope) -> list[dict[str, str]]:
    changed = []
    for path in sorted(set(reference) | set(actual)):
        if not scope(path) or reference.get(path) == actual.get(path):
            continue
        changed.append({
            "path": path,
            "reference_blob": reference.get(path, "ABSENT"),
            "actual_blob": actual.get(path, "ABSENT"),
        })
    return changed


def main() -> int:
    for sha in (TECH_REFERENCE, FROZEN_VISUAL, *HISTORICAL_REFERENCES.values()):
        ensure_commit(sha)
    references = {name: tree(sha) for name, sha in HISTORICAL_REFERENCES.items()}
    latest = tree(TECH_REFERENCE)
    original_ui = tree(FROZEN_VISUAL)
    current = tree("HEAD")

    tech_reference = {p: sha for p, sha in latest.items() if technology(p)}
    visual_reference = {p: sha for p, sha in original_ui.items() if visual(p)}

    errors = []
    if len(tech_reference) != EXPECTED_TECH_FILES:
        errors.append(
            f"Reference technology inventory unexpectedly changed: "
            f"{len(tech_reference)} != {EXPECTED_TECH_FILES}"
        )

    # Missing or altered reviewed science/engineering source is a hard fail.
    # New backend files are allowed as additions, never as silent replacements.
    absent = sorted(set(tech_reference) - set(current))
    modified = sorted(
        p for p in tech_reference.keys() & current.keys()
        if current[p] != tech_reference[p]
    )
    additions = sorted(p for p in current if technology(p) and p not in tech_reference)
    if absent:
        errors.append(f"{len(absent)} reviewed technology files missing")
    if modified:
        errors.append(f"{len(modified)} reviewed technology files altered")
    visual_drift = mismatch(original_ui, current, visual)
    if visual_drift:
        errors.append(f"{len(visual_drift)} FROZEN visual files added/removed/modified")

    # Compare all historical candidate technology trees, not just the newest.
    # Any older, independent nonvisual change is an unported candidate.
    historical_drift = {
        name: mismatch(latest, candidate, technology)
        for name, candidate in references.items()
    }
    for name, delta in historical_drift.items():
        if delta:
            errors.append(f"{name}: {len(delta)} technology deviations not in latest reviewed source")

    counts = Counter(
        "block" if p.startswith("src/main/java/dev/redstoneengineering/block/")
        else "physics" if p.startswith("src/main/java/dev/redstoneengineering/physics/")
        else "server_menu" if p.startswith("src/main/java/dev/redstoneengineering/ui/menu/")
        else "gametest" if p.startswith("src/main/java/dev/redstoneengineering/gametest/")
        else "other_engineering" for p in tech_reference
    )
    report = {
        "timestamp_utc": datetime.now(timezone.utc).isoformat(),
        "head": git("rev-parse", "HEAD").stdout.decode().strip(),
        "technology_reference": TECH_REFERENCE,
        "frozen_visual_reference": FROZEN_VISUAL,
        "technology_reference_count": len(tech_reference),
        "technology_file_groups": dict(sorted(counts.items())),
        "retained_identical_technology": len(tech_reference) - len(absent) - len(modified),
        "technology_missing": absent,
        "technology_modified": modified,
        "technology_additions_since_reference": additions,
        "historical_technology_differences": historical_drift,
        "frozen_visual_count": len(visual_reference),
        "frozen_visual_drift": visual_drift,
        "errors": errors,
        "note": "Static blob parity only; no claim of Minecraft runClient, GameTest or multiplayer acceptance.",
    }
    REPORT_JSON.parent.mkdir(parents=True, exist_ok=True)
    REPORT_JSON.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    lines = [
        "# RSE nonvisual engineering parity audit",
        "",
        f"- Result: **{'FAIL' if errors else 'PASS'}**",
        f"- Technology reference: \x60{TECH_REFERENCE}\x60",
        f"- Frozen visual anchor: \x60{FROZEN_VISUAL}\x60",
        f"- Engineering files matched: **{report['retained_identical_technology']} / {len(tech_reference)}**",
        f"- New nonvisual additions: **{len(additions)}** (reported, not deleted)",
        f"- Frozen visual changes: **{len(visual_drift)}**",
        "- Historical PR230/239/240 nonvisual deviations: " +
            ", ".join(f"{k}={len(v)}" for k, v in historical_drift.items()),
        "",
        "## Important distinction",
        "Matching source files proves forward-port completeness against the reviewed references;",
        "it does not certify the correctness of the physics, runtime block behavior, or UI in Minecraft.",
        "",
    ]
    if errors:
        lines.extend(["## Failures", *("- " + x for x in errors), ""])
        for group, details in (
            ("Missing engineering files", absent),
            ("Modified engineering files", modified),
            ("Visual drift", [x["path"] for x in visual_drift]),
        ):
            if details:
                lines.extend(["### " + group, *("- \x60" + p + "\x60" for p in details[:100]), ""])
    REPORT_MD.write_text("\n".join(lines) + "\n", encoding="utf-8")

    print("RSE FUSION NONVISUAL PARITY: " + ("FAIL" if errors else "PASS"))
    print(f"  retained latest backend code: {report['retained_identical_technology']}/{len(tech_reference)}")
    print("  groups: " + json.dumps(dict(counts), sort_keys=True))
    print(f"  frozen visual drift: {len(visual_drift)}")
    print("  historical technology mismatches: " +
          ", ".join(f"{key}={len(value)}" for key, value in historical_drift.items()))
    print(f"  new nonvisual additions: {len(additions)}")
    for message in errors:
        print("  ERROR: " + message)
    return 1 if errors else 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (subprocess.CalledProcessError, OSError, RuntimeError, ValueError) as exc:
        print("RSE FUSION NONVISUAL PARITY: ERROR", file=sys.stderr)
        print(exc, file=sys.stderr)
        raise SystemExit(2)
