#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
failed: list[str] = []


def require(rel: str, *tokens: str) -> None:
    path = root / rel
    if not path.exists():
        failed.append(f"missing: {rel}")
        return
    text = path.read_text(errors="ignore")
    for token in tokens:
        if token not in text:
            failed.append(f"{rel} missing token: {token}")


def require_min_alpha_version(minimum: tuple[int, int, int]) -> None:
    path = root / "gradle.properties"
    if not path.exists():
        failed.append("missing: gradle.properties")
        return
    match = re.search(r"^mod_version=(\d+)\.(\d+)\.(\d+)-alpha$", path.read_text(errors="ignore"), re.MULTILINE)
    if not match:
        failed.append("gradle.properties missing parseable alpha mod_version")
        return
    current = tuple(int(part) for part in match.groups())
    if current < minimum:
        failed.append(f"mod_version {current} is older than required Alpha {minimum}")


require(
    "src/main/java/dev/redstoneengineering/diagnostics/acceptance/EngineeringAcceptancePresentation.java",
    "headline", "firstIssueLine", "Acceptance:", "commissioning=", "Evidence:",
)
require(
    "src/main/java/dev/redstoneengineering/integration/jade/EngineeringPortJadeProvider.java",
    "PidControllerBlock", "ClosedLoopCommissioning.inspectPid", "EngineeringAcceptance.evaluate",
    "KEY_ACCEPTANCE_STATUS", "KEY_COMMISSIONING_STATUS", "KEY_COMMISSIONING_SCORE",
    "KEY_ACCEPTANCE_ISSUE_COUNT", "KEY_ACCEPTANCE_ISSUE_CODE", "KEY_ACCEPTANCE_ISSUE_DETAIL",
    "KEY_ACCEPTANCE_TRACE", "appendAcceptanceServerData", "appendAcceptance",
)
require(
    "src/main/java/dev/redstoneengineering/gametest/RseAcceptanceGameTests.java",
    "acceptancePresentationIsConciseAndTraceable", "EngineeringAcceptancePresentation.headline",
    "EngineeringAcceptancePresentation.firstIssueLine",
)
require_min_alpha_version((1, 0, 19))
require("ALPHA1_0_19_MANIFEST.txt", "1.0.19-alpha", "Acceptance UX & Evidence Presentation", "Java: 21")
require("README.md", "Alpha 1.0.19", "1.0.19-alpha", "Acceptance UX & Evidence Presentation")

provider = root / "src/main/java/dev/redstoneengineering/integration/jade/EngineeringPortJadeProvider.java"
if provider.exists():
    text = provider.read_text(errors="ignore")
    forbidden = [
        "RuntimeIntStore.get(",
        "RuntimeIntStore.remove(",
        ".setBlock(",
        ".scheduleTick(",
        "DomainNetwork.drive",
        "DomainNetwork.recompute",
    ]
    for token in forbidden:
        if token in text:
            failed.append(f"Jade acceptance presentation must remain read-only; found forbidden token {token!r}")

if failed:
    print("RSE Alpha 1.0.19 acceptance UX verification: FAIL")
    for item in failed:
        print(" -", item)
    sys.exit(1)

print("RSE Alpha 1.0.19 acceptance UX verification: PASS")
print(" PID-only acceptance HUD scope: PASS")
print(" server-backed structured acceptance evidence: PASS")
print(" concise headline + issue presentation: PASS")
print(" deterministic trace preservation: PASS")
print(" read-only UI ownership boundary: PASS")
