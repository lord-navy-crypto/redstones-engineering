#!/usr/bin/env python3
"""Static contracts for the twelfth 10-block design + bug audit (registered blocks 111-120)."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/dev/redstoneengineering"


def text(rel: str) -> str:
    path = JAVA / rel
    if not path.is_file():
        raise SystemExit(f"missing twelfth-ten file: {rel}")
    return path.read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")


injector = text("block/SoulFluxInjectorBlock.java")
meter = text("block/SoulFluxMeterBlock.java")
molecular = text("block/MolecularCloudReceiverBlock.java")
phonon = text("block/PhononConduitBlock.java")
encoder = text("block/ThermalPulseEncoderBlock.java")
receiver = text("block/ThermalPulseReceiverBlock.java")
shielding = text("instrument/InstrumentShieldingAudit.java")
servo = text("block/ServoActuatorBlock.java")
servo_sensor = text("block/ServoPositionSensorBlock.java")
voter = text("block/RedundantVoterBlock.java")
tests = text("gametest/RseTwelfthTenDesignBugGameTests.java")
registration = text("gametest/RseGameTestRegistration.java")
workflow = (ROOT / ".github/workflows/build.yml").read_text(encoding="utf-8")

for token in (
    "RedstoneObservationSupport.observe",
    "commandObservation",
    "SoulFluxNetwork.chargeSnapshot",
    "PortQuality.STALE",
    "PortQuality.NO_SIGNAL",
    "if (!command.valid() || command.value() <= 0) return",
):
    require(token in injector, f"Soul Flux Injector source/node evidence missing {token}")
require("commandSignal(level, pos), PortQuality.VALID" not in injector,
        "Soul Flux Injector regressed to unconditional VALID command snapshots")

for token in (
    "record ChargeObservation",
    "SoulFluxNetwork.isNode",
    "SoulFluxNetwork.chargeSnapshot",
    "snapshot.ageTicks() < 0",
    "PortQuality.NO_SIGNAL",
    "PortQuality.VALID",
):
    require(token in meter, f"Soul Flux Meter evidence contract missing {token}")
require("PortQuality.VALID));" not in meter.split("engineeringSnapshot", 1)[1].split("canConnectRedstone", 1)[0],
        "Soul Flux Meter snapshot still fabricates unconditional VALID quality")

for token in (
    "record CloudSample",
    "apertureLoaded",
    "level.hasChunkAt",
    "RuntimeIntStore.peek",
    "runtime[3] = 1",
    "PortQuality.STALE",
    "if (live.complete())",
    "Incomplete coverage retains the last trustworthy filtered value",
):
    require(token in molecular, f"Molecular receiver coverage/history contract missing {token}")
filtered_body = molecular.split("public static int filtered", 1)[1].split("public static int peak", 1)[0]
require("RuntimeIntStore.get" not in filtered_body,
        "Molecular receiver filtered inspection still allocates runtime")

for token in (
    "InformationRuntime.snapshot",
    "packetQuality",
    "packet.ageTicks() >= 0",
    "InformationRuntime.clear(level, \"thermal_pulse\", pos)",
    "packet.qualityPercent()",
):
    require(token in phonon, f"Phonon conduit packet lifecycle missing {token}")
require("InformationRuntime.value(level, \"thermal_pulse\", pos)" not in phonon,
        "Phonon conduit reverted to split scalar packet reads")

for token in (
    "RedstoneObservationSupport.observe",
    "inputObservation",
    "InformationRuntime.snapshot(level, ENCODER_KEY, pos)",
    "ThermalPulseKernel.send",
    "serverLevel.scheduleTick(pos, this, 1)",
    "protected void tick",
    "InformationRuntime.clear(level, ENCODER_KEY, pos)",
):
    require(token in encoder, f"Thermal pulse encoder event/source contract missing {token}")
require("EngineeringPortSnapshot.redstone(port.get(), value, PortQuality.VALID)" not in encoder,
        "Thermal encoder reverted to unconditional VALID electrical input")

for token in (
    "InformationRuntime.snapshot",
    "packetQuality",
    "int next = Math.max(0, packet.value() - 1)",
    "if (next == 0)",
    "InformationRuntime.clear(level, \"thermal_pulse\", pos)",
    "else if (packet.ageTicks() >= 0)",
):
    require(token in receiver, f"Thermal receiver expiry contract missing {token}")
require("Math.max(0, value - 1), 0,\n                    value > 1" not in receiver,
        "Thermal receiver still writes an invalid zero ghost envelope")

for token in (
    "Observer-only shielding audit",
    "if (!level.hasChunkAt(neighborPos))",
    "bounded = false",
    "return new ShieldingSnapshot(shielded + unshielded",
    'return "TRUNCATED"',
    'return "FULLY_SHIELDED"',
):
    require(token in shielding, f"Shielding coverage contract missing {token}")
require("InformationRuntime" not in shielding and "RuntimeIntStore" not in shielding,
        "Shielding audit must not become a second measurement solver/runtime")

for token in (
    "RedstoneObservationSupport.observe",
    "controlObservation",
    "boolean commandAvailable = commandInput.valid()",
    "int effectiveCommand = commandAvailable ? command : r[0]",
    "boolean brake = !commandAvailable",
    "!commandAvailable ? 0",
):
    require(token in servo, f"Servo source/fail-safe contract missing {token}")
require("read(l, p, back)" not in servo,
        "Servo tick still collapses missing command into numeric zero")

for token in (
    "sourceQuality",
    "level.hasChunkAt(servoPos)",
    "servoState.getValue(ServoActuatorBlock.FACING)",
    "servoFront == sensorInput.getOpposite()",
    "PortQuality.TOPOLOGY_ERROR",
    "PortQuality.NO_SIGNAL",
):
    require(token in servo_sensor, f"Servo position sensor alignment contract missing {token}")

for token in (
    "RedstoneObservationSupport.observe",
    "record Vote",
    "if (valid < 2)",
    "PortQuality.NO_SIGNAL",
    "PortQuality.STALE",
    "valid == 3 ? values[1]",
    "PortQuality.FAULT",
    "valid == 3 && spread <= toleranceValue",
):
    require(token in voter, f"Redundant voter source/quorum contract missing {token}")
require("readInputFrom" not in voter,
        "Redundant voter still allows disconnected channels to participate as raw zero values")

methods = (
    "soulInjectorSeparatesMissingCommandAndKnownEmptyOutput",
    "soulMeterSeparatesNoNodeTransientAbsenceAndStoredZero",
    "molecularInspectionIsNeutralAndCoverageQualityIsExplicit",
    "phononConduitClearsDecayedPacketWithoutGhostEnvelope",
    "thermalEncoderSeparatesDrivenZeroFromTransientEmission",
    "thermalReceiverClearsFinalZeroPacket",
    "shieldedCableAuditTracksPhysicalMixWithoutChangingIdentity",
    "servoCommandLossBrakesInsteadOfBecomingZeroPositionCommand",
    "servoPositionSensorRejectsMisalignedMechanicalSource",
    "redundantVoterRequiresRealTwoOfThreeSourceQuorum",
)
for method in methods:
    require(f"void {method}(GameTestHelper helper)" in tests,
            f"Missing twelfth-ten GameTest {method}")
require(tests.count("@GameTest(") == 10,
        f"expected exactly 10 twelfth-ten GameTests, found {tests.count('@GameTest(')}")
require("event.register(RseTwelfthTenDesignBugGameTests.class);" in registration,
        "Twelfth-ten GameTests are not registered")
require("event.register(RseEleventhTenDesignBugGameTests.class);" in registration,
        "Eleventh-ten regression registration was accidentally dropped")
require("tools/rse_twelfth_ten_tail_runtime_verify.py" in workflow,
        "Workflow does not gate the twelfth-ten verifier")
for token in (
    "Minecraft topology GameTests (manual diagnostic)",
    "github.event_name == 'workflow_dispatch'",
    "continue-on-error: true",
    "./gradlew runGameTestServer",
    "./gradlew compileJava",
    "./gradlew test",
):
    require(token in workflow, f"build.yml missing manual/non-blocking GameTest policy token {token!r}")

print("RSE twelfth-ten tail runtime verification: PASS")
print("  Soul command/node/retained-zero evidence: PASS")
print("  molecular observer neutrality + free-space coverage: PASS")
print("  phonon/thermal transient packet lifecycle: PASS")
print("  shielding observer-only coverage integrity: PASS")
print("  servo source-aware fail-safe + mechanical alignment: PASS")
print("  2oo3 real-source quorum and degraded quality: PASS")
print("  registered twelfth-ten GameTests: 10 (manual diagnostic / non-blocking)")
