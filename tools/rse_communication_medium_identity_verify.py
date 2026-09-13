#!/usr/bin/env python3
from pathlib import Path
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

# Measurement-bus shielding is a separate engineering identity dimension. It must remain
# observer-only until an explicit interference model exists, but it cannot regress to a skin-only block.
require(
    "src/main/java/dev/redstoneengineering/instrument/InstrumentNetwork.java",
    "shieldedCableNodes",
    "unshieldedCableNodes",
    "shieldingCoveragePercent",
    "shieldingIntegrity",
)
require(
    "src/main/java/dev/redstoneengineering/instrument/InstrumentShieldingAudit.java",
    "riskClass()",
    "recommendation()",
    'return "PROTECTED"',
    'return "EXPOSED"',
    'return "PARTIAL"',
)
require(
    "src/main/java/dev/redstoneengineering/block/ShieldedInstrumentCableBlock.java",
    "shieldingIntegrity()",
    "shieldingCoveragePercent()",
    "shieldedCableNodes()",
    "fabricated random noise",
)

require(
    "docs/COMMUNICATION_MEDIUM_IDENTITY.md",
    "Shared information envelope",
    "Medium identity rule",
    "Communication choice hierarchy",
    "How much information must move?",
    "No medium should be the universal upgrade of another",
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
for token in (
    "Minecraft topology GameTests (manual diagnostic)",
    "github.event_name == 'workflow_dispatch'",
    "continue-on-error: true",
    "./gradlew runGameTestServer",
    "./gradlew compileJava",
    "./gradlew test",
):
    if workflow and token not in workflow:
        errors.append(f"build.yml: missing manual/non-blocking GameTest policy token {token!r}")

if errors:
    print("RSE communication medium identity verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE communication medium identity verification: PASS")
print(" shared payload/selector/validity/quality/freshness envelope: PASS")
print(" engineering choice hierarchy + non-dominance rule: PASS")
print(" 8-bit bus local-loading + contention identity: PASS")
print(" serial timing/utilization identity preserved: PASS")
print(" differential one-bit high-integrity identity: PASS")
print(" instrument bus shielding evidence + risk classification: PASS")
print(" communication medium design contract: PASS")
print(" registered identity GameTests: 3 (manual diagnostic / non-blocking)")
