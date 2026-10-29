#!/usr/bin/env python3
"""Fail-closed digital converter source contracts, not Minecraft runtime execution."""
from pathlib import Path
import sys
root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors = []
def read(rel):
    p = root / rel
    if not p.is_file():
        errors.append("missing: "+rel)
        return ""
    return p.read_text(encoding="utf-8")
base = "src/main/java/dev/redstoneengineering/"
support = read(base+"block/DigitalEndpointEvidenceSupport.java")
serializer = read(base+"block/SerializerBlock.java")
deserializer = read(base+"block/DeserializerBlock.java")
driver = read(base+"block/DifferentialDriverBlock.java")
tests = read(base+"gametest/RseDigitalEndpointWatchdogGameTests.java")
registration = read(base+"gametest/RseGameTestRegistration.java")
workflow = read(".github/workflows/build.yml")
def require(value, why):
    if not value: errors.append(why)

for token in (
    "if (upstream != PortQuality.VALID) return upstream;",
    "output.ageTicks() < 0 || !output.valid()",
    "quality == PortQuality.VALID && envelope.valid()",
    "envelope.value() & mask : 0",
):
    require(token in support, "shared endpoint safety gate missing: "+token)
for bad in ("setBlock(", "RuntimeIntStore.get(", "InformationRuntime.write(", "scheduleTick("):
    require(bad not in support, "pure endpoint evidence helper must not write: "+bad)

for name, body, outcarrier in (
    ("Serializer", serializer, '"serial"'),
    ("Deserializer", deserializer, '"bus8_out"'),
    ("DifferentialDriver", driver, '"diff_out"'),
):
    require("DigitalEndpointEvidenceSupport.outputQuality(" in body
            and "DigitalEndpointEvidenceSupport.valueWhenValid(" in body,
            name+" port must convey coherent validity and zero-on-invalid payload")
    require("WATCHDOG_TICKS = 16;" in body
            and "scheduleTick(pos, this, WATCHDOG_TICKS);" in body,
            name+" must periodically verify input authority")
    require("protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)"
            in body, name+" missing bounded watchdog")
    require(outcarrier in body, name+" lost established information runtime envelope")
    require("level.hasChunkAt(output)" in body,
            name+" must not query or recompute unloaded output chunk")
    require('inputQuality == PortQuality.STALE ? previous.value() & 0xFF' not in body,
            name+" must not retain obsolete byte on stale input")

require("int value = valid ? DataBusNetwork.sample(level, input) : 0;" in serializer,
        "serializer must revoke stale bus input byte")
require("boolean valid = inputQuality == PortQuality.VALID && serial.valid();" in deserializer,
        "deserializer must require both authoritative line and valid serial packet")
require("int value = valid ? serial.value() & 0xFF : 0;" in deserializer,
        "deserializer must revoke old bus driver value")
require("int bit = input.valid() && input.value() > 0 ? 1 : 0;" in driver,
        "differential source loss must deassert bit and validity")
require("previous.value() != bit || previous.valid() != input.valid()" in driver,
        "differential loss transition must trigger wire recomputation")
for name in (
    "serializerWatchdogRevokesOrphanedFrame",
    "deserializerWatchdogWithdrawsOldBusDriver",
    "differentialDriverRechecksLostSource",
):
    require("void "+name+"(GameTestHelper helper)" in tests,
            "in-world watchdog regression absent: "+name)
require(tests.count("@GameTest(") == 3, "exactly 3 in-world watchdog tests expected")
require("event.register(RseDigitalEndpointWatchdogGameTests.class);" in registration,
        "watchdog GameTests missing from registration")
require("tools/rse_digital_endpoint_fail_closed_verify.py" in workflow,
        "new fail-closed verifier not in default CI")
if errors:
    print("RSE DIGITAL ENDPOINT FAIL-CLOSED: FAIL")
    for e in errors: print(" - "+e)
    sys.exit(1)
print("RSE DIGITAL ENDPOINT FAIL-CLOSED: PASS")
print(" output 0 on invalid stored packet, no stale byte replay")
print(" serializer/deserializer/differential watchdogs and bounded chunk queries")
print(" three registered GameTests; Minecraft execution still NOT_RUN")
