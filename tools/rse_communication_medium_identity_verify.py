#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing communication identity file: {rel}")
        return ""
    return path.read_text(errors="ignore")


def require(rel: str, *tokens: str) -> None:
    body = read(rel)
    for token in tokens:
        if body and token not in body:
            errors.append(f"{rel}: missing communication identity contract {token!r}")


require(
    "src/main/java/dev/redstoneengineering/physics/InformationRuntime.java",
    "RUNTIME_SIZE = 5",
    "LAST_UPDATE_STAMP",
    "record Snapshot(",
    "qualityPercent",
    "ageTicks",
    "RuntimeIntStore.peek",
    "selector(Level level",
    "Compatibility alias",
)

information = read("src/main/java/dev/redstoneengineering/physics/InformationRuntime.java")
if information and "age/quality" in information:
    errors.append("InformationRuntime still conflates freshness and quality")
if information and "runtime[QUALITY] = Math.max(0, Math.min(100, quality))" not in information:
    errors.append("InformationRuntime does not normalize observer-facing quality to 0..100")

require(
    "src/main/java/dev/redstoneengineering/physics/DifferentialNetwork.java",
    "one-bit high-integrity link",
    "nodes.size() - 1) / 8",
    "sacrifices payload density",
)
require(
    "src/main/java/dev/redstoneengineering/physics/SerialNetwork.java",
    "frame/period/quality/utilization diagnostics",
    "nodes.size() / 4",
    "utilizationPercent",
)
require(
    "src/main/java/dev/redstoneengineering/physics/DataBusNetwork.java",
    "local parallel 8-bit bus",
    "loadingPenalty",
    "contentionPenalty",
    "sameValueMultiDriver",
    "distinctValues > 1",
    "qualityPercent",
    'InformationRuntime.ageTicks(level, "bus8", pos)',
)

bus = read("src/main/java/dev/redstoneengineering/physics/DataBusNetwork.java")
if bus:
    if "boolean valid = driverCount > 0 && distinctValues == 1" not in bus:
        errors.append("8-bit bus lost its hard different-value conflict contract")
    if "sameValueMultiDriver ?" not in bus:
        errors.append("8-bit bus does not charge margin for same-value multi-driving")

require(
    "docs/COMMUNICATION_MEDIUM_IDENTITY.md",
    "Shared information envelope",
    "Medium identity rule",
    "8-bit Data Bus",
    "Serial Data",
    "Differential Data",
    "Radio",
    "Guided Optical Fiber",
    "Free-space Optical",
    "Hydroacoustic",
    "Quality and freshness are independent",
    "Information is not electrical power",
)

runtime_tests = "src/main/java/dev/redstoneengineering/gametest/RseCommunicationIdentityGameTests.java"
for method in (
    "informationFreshnessIsIndependentFromQuality",
    "differentialTradesPayloadDensityForLinkMargin",
    "dataBusSameValueContentionConsumesMarginAndConflictInvalidates",
):
    require(runtime_tests, f"void {method}(GameTestHelper helper)")
require(
    runtime_tests,
    "InformationRuntime.snapshot",
    "differentialQuality <= serialQuality",
    "contendedQuality >= 100",
    "conflicted.conflictFrames() < 1",
)
require(
    "src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java",
    "event.register(RseCommunicationIdentityGameTests.class);",
)

workflow = read(".github/workflows/build.yml")
if workflow and "tools/rse_communication_medium_identity_verify.py" not in workflow:
    errors.append("communication medium identity verifier is not wired into CI")
if workflow:
    thresholds = [int(value) for value in re.findall(r"test_count\s*<\s*(\d+)", workflow)]
    if not thresholds or max(thresholds) < 200:
        errors.append("Minecraft runtime gate has not been raised to at least 200 GameTests")

if errors:
    print("RSE communication medium identity verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE communication medium identity verification: PASS")
print(" shared payload/selector/validity/quality/freshness envelope: PASS")
print(" 8-bit bus local-loading + contention identity: PASS")
print(" serial timing/utilization identity preserved: PASS")
print(" differential one-bit high-integrity identity: PASS")
print(" communication medium design contract: PASS")
print(" three executable identity GameTests registered: PASS")
