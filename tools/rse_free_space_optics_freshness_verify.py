#!/usr/bin/env python3
"""Fail-closed free-space optical freshness and channel reconfiguration gate.

Static source/contract test; Minecraft GameTests must separately run in game.
"""
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors = []
def read(rel):
    p = root / rel
    if not p.is_file():
        errors.append("missing "+rel)
        return ""
    return p.read_text(encoding="utf-8")
base = "src/main/java/dev/redstoneengineering/"
common = read(base+"block/FreeSpaceOpticalChannelSupport.java")
tx = read(base+"block/FreeSpaceOpticalTransmitterBlock.java")
rx = read(base+"block/FreeSpaceOpticalReceiverBlock.java")
menu = read(base+"ui/menu/OpticalSystemMenu.java")
field = read(base+"ui/menu/FieldDeviceMenu.java")
tests = read(base+"gametest/RseFreeSpaceOpticalFreshnessGameTests.java")
registration = read(base+"gametest/RseGameTestRegistration.java")
workflow = read(".github/workflows/build.yml")
def require(ok, message):
    if not ok: errors.append(message)

for token in (
    "public static final int FRESH_TICKS = 8;",
    "public static final int RETAIN_STALE_TICKS = 40;",
    "packet.ageTicks() > FRESH_TICKS",
    "packet.selector() != selectedChannel",
    "return packet.valid() ? PortQuality.VALID : PortQuality.STALE;",
    "int nextChannel(int channel)",
    "boolean supportedChannel(int channel)",
):
    require(token in common, "freshness classifier missing: "+token)
for bad in ("RuntimeIntStore.get(", "InformationRuntime.write(", "setBlock(", "scheduleTick("):
    require(bad not in common, "shared channel helper must be pure: "+bad)
for token in (
    "public static boolean setChannel(Level level, BlockPos pos, int channel)",
    "FreeSpaceOpticalChannelSupport.supportedChannel(channel)",
    "transmitter.emit(server, pos, updated);",
    "FreeSpaceOpticalChannelSupport.nextChannel(state.getValue(CHANNEL))",
    "FreeSpaceOpticalChannelSupport.boundedPower(inputPower(level, pos, state))",
):
    require(token in tx, "optical TX loses bounded channel configuration: "+token)
for token in (
    "public static boolean setChannel(Level level, BlockPos pos, int channel)",
    'InformationRuntime.clear(level, "free_optical", pos);',
    "invalidateReception(level, pos);",
    "public static void invalidateReception(Level level, BlockPos pos)",
    "receiver.updateOutput(level, pos, updated, 0);",
    "FreeSpaceOpticalChannelSupport.receiveQuality(packet, state.getValue(CHANNEL))",
    "packet.ageTicks() > FreeSpaceOpticalChannelSupport.RETAIN_STALE_TICKS",
    "level.scheduleTick(pos, this, 2);",
):
    require(token in rx, "optical RX loses channel invalidation/true freshness: "+token)
tick_start = rx.find("protected void tick(BlockState state,")
tick_end = rx.find("protected void onRemove(", tick_start)
tick = rx[tick_start:tick_end]
require(tick_start >= 0 and tick_end > tick_start, "RX scheduled tick missing")
require("InformationRuntime.write(" not in tick,
        "RX own maintenance tick must NEVER rewrite/refresh a stale packet")
require("packet.qualityPercent() - 20" not in rx,
        "quality confidence must no longer double as artificial signal freshness")
require(menu.count("FreeSpaceOpticalTransmitterBlock.setChannel(level, blockPos,") == 2,
        "TX direct/cycle HMI paths must use authoritative setChannel")
require(menu.count("FreeSpaceOpticalReceiverBlock.setChannel(level, blockPos,") == 2,
        "RX direct/cycle HMI paths must use authoritative setChannel")
require("FreeSpaceOpticalReceiverBlock.invalidateReception(level, blockPos);" in menu,
        "RX route rotation must revoke old optical packet and output immediately")
require("opticalPort.map(p -> p.quality())" in field
        and "dataValid.set(opticalQuality == PortQuality.VALID ? 1 : 0);" in field,
        "Field Device HMI must not use raw retained payload validity as fresh signal")
for method in (
    "void receiverTimeoutCannotBeExtendedByOwnTicks(",
    "void receiverChannelRoundTripCannotResurrectOldPacket(",
    "void receiverRouteChangeInvalidatesOldEvidence(",
    "void transmitterNoOpChannelDoesNotScheduleOrChangeBlock(",
):
    require(method in tests, "missing GameTest: "+method)
require(tests.count("@GameTest(") == 4, "must register exactly 4 optical GameTests")
require("event.register(RseFreeSpaceOpticalFreshnessGameTests.class);" in registration,
        "GameTests not registered")
require("tools/rse_free_space_optics_freshness_verify.py" in workflow,
        "source gate not in default CI")
if errors:
    print("RSE FREE-SPACE OPTICAL FRESHNESS: FAIL")
    for e in errors: print(" - "+e)
    sys.exit(1)
print("RSE FREE-SPACE OPTICAL FRESHNESS: PASS")
print(" TX/RX authoritative channel controls; RX old packet revoked on switch")
print(" true TX-timestamp freshness; no RX self-renewal; output fails closed")
print(" four registered in-world GameTests (runtime still unexecuted)")
