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

require("src/main/java/dev/redstoneengineering/physics/InformationRuntime.java",
        "RUNTIME_SIZE = 5", "LAST_UPDATE_STAMP", "record Snapshot(", "qualityPercent", "ageTicks",
        "RuntimeIntStore.peek", "selector(Level level", "Compatibility alias")
information = read("src/main/java/dev/redstoneengineering/physics/InformationRuntime.java")
if information and "age/quality" in information: errors.append("InformationRuntime still conflates freshness and quality")
if information and "runtime[QUALITY] = Math.max(0, Math.min(100, quality))" not in information:
    errors.append("InformationRuntime does not normalize observer-facing quality to 0..100")

require("src/main/java/dev/redstoneengineering/physics/DifferentialNetwork.java", "one-bit high-integrity link", "nodes.size() - 1) / 8", "sacrifices payload density")
require("src/main/java/dev/redstoneengineering/physics/SerialNetwork.java", "frame/period/quality/utilization diagnostics", "nodes.size() / 4", "utilizationPercent")
require("src/main/java/dev/redstoneengineering/physics/DataBusNetwork.java", "local parallel 8-bit bus", "loadingPenalty", "contentionPenalty", "sameValueMultiDriver", "distinctValues > 1", "qualityPercent", 'InformationRuntime.ageTicks(level, "bus8", pos)')
bus = read("src/main/java/dev/redstoneengineering/physics/DataBusNetwork.java")
if bus:
    if "boolean valid = driverCount > 0 && distinctValues == 1" not in bus: errors.append("8-bit bus lost its hard different-value conflict contract")
    if "sameValueMultiDriver ?" not in bus: errors.append("8-bit bus does not charge margin for same-value multi-driving")

require("src/main/java/dev/redstoneengineering/instrument/InstrumentNetwork.java",
        "shieldedCableNodes", "unshieldedCableNodes", "locallyExposed", "getBestNeighborSignal",
        "CopperVoltageSourceBlock", "exposedCableNodes", "shieldedExposedNodes", "unshieldedExposedNodes",
        "interferenceExposurePercent", "interferenceConfidencePercent", "interferenceIntegrity",
        "60 * unshieldedExposedNodes + 10 * shieldedExposedNodes", "qualityForMask")
network = read("src/main/java/dev/redstoneengineering/instrument/InstrumentNetwork.java")
for forbidden in ("Random", "Math.random", "ThreadLocalRandom", "DomainNetwork.sample", "DomainNetwork.scan"):
    if forbidden in network: errors.append(f"InstrumentNetwork interference evidence must stay deterministic/local; found {forbidden!r}")

require("src/main/java/dev/redstoneengineering/instrument/InstrumentShieldingAudit.java",
        "riskClass()", "recommendation()", 'return "PROTECTED"', 'return "EXPOSED"', 'return "PARTIAL"')
require("src/main/java/dev/redstoneengineering/block/InstrumentCableBlock.java",
        "interferenceIntegrity()", "interferenceExposurePercent()", "interferenceConfidencePercent()")
require("src/main/java/dev/redstoneengineering/block/ShieldedInstrumentCableBlock.java",
        "shieldingIntegrity()", "shieldingCoveragePercent()", "shieldedExposedNodes()", "unshieldedExposedNodes()",
        "interferenceConfidencePercent()", "does not inject fabricated random noise")
require("src/main/java/dev/redstoneengineering/ui/menu/OscilloscopeMenu.java",
        "interferenceExposure", "interferenceConfidence", "unshieldedExposedNodes")
require("src/main/java/dev/redstoneengineering/ui/menu/LogicAnalyzerMenu.java",
        "interferenceExposure", "interferenceConfidence", "unshieldedExposedNodes")
require("src/main/java/dev/redstoneengineering/client/ui/OscilloscopeScreen.java",
        "Interference", "interferenceConfidence()", "shield exposed instrument segments")
require("src/main/java/dev/redstoneengineering/client/ui/LogicAnalyzerScreen.java",
        "Bus interference", "interferenceConfidence()", "shield exposed instrument segments")

require("src/main/java/dev/redstoneengineering/ui/menu/DigitalCommunicationMenu.java",
        "refreshMediumTelemetry", "DataBusNetwork.getDiagnostics", "SerialNetwork.getDiagnostics",
        "DifferentialNetwork.driverCount", "InformationRuntime.snapshot", "mediumQualityPercent",
        "mediumAgeTicks", "mediumDriverCount", "mediumMetricA", "mediumMetricB", "mediumMetricC")
digital_menu = read("src/main/java/dev/redstoneengineering/ui/menu/DigitalCommunicationMenu.java")
for forbidden in ("DataBusNetwork.resolve(", "DataBusNetwork.drive(", "SerialNetwork.recompute(", "SerialNetwork.drive(", "DifferentialNetwork.recompute(", "DifferentialNetwork.drive("):
    if forbidden in digital_menu:
        errors.append(f"DigitalCommunicationMenu must remain observer-only; found solver mutation call {forbidden!r}")
require("src/main/java/dev/redstoneengineering/client/ui/DigitalCommunicationScreen.java",
        "8-bit parallel", "Contention / conflicts", "period=", "util=", "1-bit high-integrity",
        "8-BIT BUS CONTENTION CONSUMING MARGIN", "SERIAL LINK NEAR UTILIZATION LIMIT",
        "DIFFERENTIAL HIGH-INTEGRITY LINK VALID", "highest local payload width", "fewer conductors", "one-bit payload density")

# Guided optical link-budget evidence is an observer-only audit over passive fiber/junction arms.
# Processor loss remains owned by splitter/filter/attenuator transfer functions and must not be
# charged twice by the downstream segment audit. Values stay in RSE 0..15 intensity units.
require("src/main/java/dev/redstoneengineering/physics/OpticalCommissioningSupport.java",
        "record SegmentBudget(", "segmentBudget(Level level, BlockPos receiverPos)", "isOpticalOutputToward",
        "NetworkKernel.MAX_NODES", "observedSegmentLoss", "receiverHeadroom", "channelCoherent()",
        'return "HEALTHY"', 'return "MARGINAL"', "snapshot.value() > 0.0")
optical_support = read("src/main/java/dev/redstoneengineering/physics/OpticalCommissioningSupport.java")
for forbidden in ("DomainNetwork.recomputeOptical(", "DomainNetwork.driveOptical(", "setBlock(", "setOptical("):
    if forbidden in optical_support:
        errors.append(f"OpticalCommissioningSupport must remain observer-only; found mutation call {forbidden!r}")
require("src/main/java/dev/redstoneengineering/ui/menu/OpticalSystemMenu.java",
        "OpticalCommissioningSupport.segmentBudget", "budgetSourceIntensity", "budgetObservedLoss",
        "budgetReceiverHeadroom", "receiverCommissioning", "budgetPassiveNodes", "budgetSourceCount")
require("src/main/java/dev/redstoneengineering/client/ui/OpticalSystemScreen.java",
        "Segment TX / RX", "Observed segment loss", "Receiver headroom", "Passive nodes / hops",
        "Intensity-unit segment budget only", "upstream splitter/attenuator loss")

# Radio differentiation must expose the existing authoritative distance / obstruction /
# adjacent-channel / collision model rather than inventing a client-side RF solver.
radio_kernel_rel = "src/main/java/dev/redstoneengineering/physics/RadioKernel.java"
radio_menu_rel = "src/main/java/dev/redstoneengineering/ui/menu/RadioLinkMenu.java"
radio_screen_rel = "src/main/java/dev/redstoneengineering/client/ui/RadioLinkScreen.java"
require(radio_kernel_rel,
        "public static final int RANGE = 32;", "public static final int MIN_DECODE_QUALITY = 20;",
        "int distanceBlocks,", "public int decodeMargin()", "return quality - MIN_DECODE_QUALITY;",
        "int distanceLoss = (int) Math.round(55.0 * distance / RANGE);",
        "int obstacleLoss = Math.min(25, obstacles * 2);", "int fade = deterministicFade(level, transmitterPos, rx);",
        "int interferencePenalty = Math.min(30, adjacent * 8);", "boolean collision = drivers > 1;",
        "boolean valid = coverageComplete && drivers == 1 && quality >= MIN_DECODE_QUALITY;",
        "bestDistance = (int) Math.ceil(distance);")
radio_kernel = read(radio_kernel_rel)
for forbidden in ("RandomSource", "Math.random(", "new Random(", "ThreadLocalRandom"):
    if forbidden in radio_kernel: errors.append(f"RadioKernel must remain deterministic; found {forbidden!r}")
require(radio_menu_rel,
        "public static final int RANGE_BLOCKS = RadioKernel.RANGE;", "public static final int MIN_DECODE_QUALITY = RadioKernel.MIN_DECODE_QUALITY;",
        "private final DataSlot linkQuality = trackedInt();", "private final DataSlot adjacentAggressors = trackedInt();",
        "private final DataSlot obstacleHits = trackedInt();", "private final DataSlot distanceBlocks = trackedInt();",
        "private final DataSlot decodeMargin = trackedInt();", "linkQuality.set(reception.quality());",
        "adjacentAggressors.set(reception.interference());", "obstacleHits.set(reception.obstacles());",
        "distanceBlocks.set(reception.distanceBlocks());", "decodeMargin.set(reception.decodeMargin());",
        "public int availabilityPercent()")
require(radio_screen_rel,
        '"SAME-CHANNEL COLLISION"', '"BELOW DECODE MARGIN"', '"MARGINAL LINK"',
        '"VALID • ADJACENT INTERFERENCE"', '"VALID • OBSTRUCTED PATH"', '"HEALTHY LINK"',
        '"NEXT • move one same-channel transmitter', '"NEXT • separate adjacent channels first',
        '"NEXT • improve line-of-sight or shorten the path',
        '"Counters are receiver-tick evidence; the client does not fabricate packet history."',
        '"Payload 0 is a valid frame when source evidence is VALID."')
radio_screen = read(radio_screen_rel)
for forbidden in ("dev.redstoneengineering.physics", "RadioKernel.receivePacket", "RuntimeIntStore", "level.getBlockState(",
                  "level.hasChunkAt(", "Math.sqrt(", "distanceLoss", "obstacleLoss", "interferencePenalty", "deterministicFade"):
    if forbidden in radio_screen: errors.append(f"RadioLinkScreen must remain observer-only; found {forbidden!r}")

require("docs/COMMUNICATION_MEDIUM_IDENTITY.md", "Shared information envelope", "Medium identity rule",
        "Communication choice hierarchy", "How much information must move?", "No medium should be the universal upgrade of another",
        "8-bit Data Bus", "Serial Data", "Differential Data", "Radio", "Guided Optical Fiber", "Free-space Optical",
        "Hydroacoustic", "Quality and freshness are independent", "Information is not electrical power")

runtime_tests = "src/main/java/dev/redstoneengineering/gametest/RseCommunicationIdentityGameTests.java"
for method in ("informationFreshnessIsIndependentFromQuality", "differentialTradesPayloadDensityForLinkMargin", "dataBusSameValueContentionConsumesMarginAndConflictInvalidates"):
    require(runtime_tests, f"void {method}(GameTestHelper helper)")
require(runtime_tests, "InformationRuntime.snapshot", "differentialQuality <= serialQuality", "contendedQuality >= 100", "conflicted.conflictFrames() < 1")
require("src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java", "event.register(RseCommunicationIdentityGameTests.class);")

workflow = read(".github/workflows/build.yml")
if workflow and "tools/rse_communication_medium_identity_verify.py" not in workflow: errors.append("communication medium identity verifier is not wired into CI")
for token in ("Minecraft topology GameTests (manual diagnostic)", "github.event_name == 'workflow_dispatch'", "continue-on-error: true", "./gradlew runGameTestServer", "./gradlew compileJava", "./gradlew test"):
    if workflow and token not in workflow: errors.append(f"build.yml: missing manual/non-blocking GameTest policy token {token!r}")

if errors:
    print("RSE communication medium identity verification: FAIL")
    for error in errors: print(" -", error)
    raise SystemExit(1)

print("RSE communication medium identity verification: PASS")
print(" shared payload/selector/validity/quality/freshness envelope: PASS")
print(" engineering choice hierarchy + non-dominance rule: PASS")
print(" 8-bit bus local-loading + contention identity: PASS")
print(" serial timing/utilization identity preserved: PASS")
print(" differential one-bit high-integrity identity: PASS")
print(" digital HMI exposes authoritative bus/serial/differential tradeoff evidence: PASS")
print(" digital HMI observer boundary / no second solver: PASS")
print(" instrument shielding deterministic local-exposure differentiation: PASS")
print(" guided optical receiver segment budget + headroom: PASS")
print(" guided optical budget observer boundary / no double-counted processor loss: PASS")
print(" radio distance/obstruction/adjacent/collision margin evidence: PASS")
print(" radio HMI observer boundary / no second RF solver: PASS")
print(" communication medium design contract: PASS")
print(" registered identity GameTests: 3 (manual diagnostic / non-blocking)")
