#!/usr/bin/env python3
"""RSE range-configuration and slew-filter evidence contract.

This gate validates source logic and registered regression witnesses, not
Minecraft runtime acceptance. Source checks must not be advertised as GameTest.
"""
from pathlib import Path
import sys
root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors = []
def read(path):
    filename = root / path
    if not filename.is_file():
        errors.append("missing file: " + path)
        return ""
    return filename.read_text(encoding="utf-8")
base = "src/main/java/dev/redstoneengineering/"
range_source = read(base+"block/RangeSensorBlock.java")
menu = read(base+"ui/menu/RangeSensorMenu.java")
filter_source = read(base+"block/PrecisionFilterBlock.java")
game = read(base+"gametest/RseRangeReconfigurationGameTests.java")
registration = read(base+"gametest/RseGameTestRegistration.java")
workflow = read(".github/workflows/build.yml")
def require(value, why):
    if not value: errors.append(why)

# All manual/HMI configuration transitions must revoke old evidence and
# output, while preserving the three-element runtime scan layout and registry.
for needle in (
    "public static boolean applyConfiguration(Level level, BlockPos pos, BlockState requested)",
    "RuntimeIntStore.remove(serverLevel, RUNTIME_KEY, pos);",
    "BlockState safe = requested.setValue(OUTPUT, 0);",
    "serverLevel.setBlock(pos, safe, Block.UPDATE_CLIENTS);",
    "serverLevel.scheduleTick(pos, sensor, 1);",
    "return applyConfiguration(level, pos, state.setValue(FACING, updated));",
    "if (!applyConfiguration(level, pos, next))",
    "int[] runtime = RuntimeIntStore.get(level, RUNTIME_KEY, pos, 3);",
    "case TARGET -> distance >= 1 && distance <= range && coverage == distance;",
    "case CLEAR -> distance == 0 && coverage == range;",
    "case INCOMPLETE_UNLOADED -> distance == 0 && coverage >= 0 && coverage < range;",
):
    require(needle in range_source, "RangeSensorBlock lost safe configuration or scan validity: " + needle)
require(menu.count("RangeSensorBlock.applyConfiguration(level, blockPos, next)") == 2,
        "RangeSensorMenu mode/range/response buttons must use safe config transaction")
require("RangeSensorBlock.rotateSensingAxis(level, blockPos" in menu,
        "RangeSensorMenu must preserve physical sensing-axis rotation")
require("level.setBlock(blockPos, next, Block.UPDATE_CLIENTS)" not in menu,
        "RangeSensorMenu may not bypass scan invalidation with direct setBlock")
require("RedstoneObservationSupport.observe(level, pos, side)" in filter_source
        and "input.quality()" in filter_source
        and "base.get().port()" in filter_source,
        "PrecisionFilterBlock must provide observer-only source-quality at its input port")
require("FilterResponseSupport.slewStep" in filter_source
        and "if (nextValue != input) level.scheduleTick(pos, this, 1);" in filter_source,
        "original slew transfer and settling tick lifecycle must remain unchanged")
for name in (
    "changingRangeRevokesPreviouslyValidMeasurement",
    "rotatingSensorDropsOldOutputAndScanHistory",
    "filterInputReportsAbsentVersusDrivenZeroWithoutAllocatingRuntime",
):
    require("void "+name+"(" in game, "missing GameTest: "+name)
require(game.count("@GameTest(") == 3, "require precisely three targeted GameTests")
require("event.register(RseRangeReconfigurationGameTests.class);" in registration,
        "new GameTest suite must be registered")
require("tools/rse_range_config_safety_verify.py" in workflow,
        "range configuration safety check must be part of default CI")
if errors:
    print("RSE RANGE CONFIGURATION / FILTER QUALITY: FAIL")
    for error in errors: print(" - "+error)
    sys.exit(1)
print("RSE RANGE CONFIGURATION / FILTER QUALITY: PASS")
print(" HMI/Shift configuration removes stale range scan, de-energizes old output: PASS")
print(" Target/Clear/Incomplete scan evidence coherence: PASS")
print(" Slew filter input distinguishes absent/redstone-zero/unloaded source: PASS")
print(" Three Minecraft GameTest witnesses registered; runtime results unverified")
