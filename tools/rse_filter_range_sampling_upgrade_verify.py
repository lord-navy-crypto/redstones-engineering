#!/usr/bin/env python3
"""Physical-contract gates for filter, range and sampled-data strengthening.

Static verification + small reference-model arithmetic; Minecraft GameTests
are registered separately and must actually run before runtime acceptance.
"""
from pathlib import Path
import math
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
src = root / "src/main/java/dev/redstoneengineering"
errors = []

def read(rel):
    p = src / rel
    if not p.is_file():
        errors.append("missing: "+str(p))
        return ""
    return p.read_text(encoding="utf-8")

def require(condition, why):
    if not condition: errors.append(why)

ray = read("block/RangeScanSupport.java")
range_sensor = read("block/RangeSensorBlock.java")
precision_range = read("block/LapisPrecisionRangeSensorBlock.java")
filters = read("block/FilterResponseSupport.java")
slew = read("block/PrecisionFilterBlock.java")
lapis = read("block/LapisLowPassFilterBlock.java")
conditioner = read("block/SignalConditionerBlock.java")
sampler = read("block/SampleHoldBlock.java")
gt = read("gametest/RseFilterRangeSamplingUpgradeGameTests.java")
registration = read("gametest/RseGameTestRegistration.java")

for fragment in ("hasChunkAt(cell)", "INCOMPLETE_UNLOADED", "distance - 1",
                 "new RayResult(0, maxRange, maxRange, Status.CLEAR)",
                 "maxRange < 1 || maxRange > 64", "solidOrFluid"):
    require(fragment in ray, "shared ray missing conservative bounded scan: "+fragment)
for fragment in ("RangeScanSupport.scan(", "case INCOMPLETE_UNLOADED -> ScanStatus.INCOMPLETE_UNLOADED",
                 "scan.complete() ? PortQuality.VALID : PortQuality.STALE",
                 "hitResult.getDirection() == Direction.UP",
                 "if (mode == 0) return false;"):
    require(fragment in range_sensor, "redstone range lost coverage/no-entity-query contract: "+fragment)
for fragment in ("RangeScanSupport.scan(", "RangeScanSupport::solidOrFluid",
                 "RangeSample rangeSample", "RangeScanSupport.Status.TARGET",
                 "hit.getDirection() == Direction.UP", "outputQuality(level, pos)"):
    require(fragment in precision_range, "Lapis range lost distinct solids/fluids and evidence: "+fragment)
require("getEntitiesOfClass(" in range_sensor and "getEntitiesOfClass(" not in precision_range,
        "two sensing devices must preserve their different target classes")

for fragment in ("SignalMath.approach(previous, input, rate)",
                 "Math.round(previous + alpha * (input - previous))",
                 "nominalNinetyPercentSamples", "Math.log1p(-alpha)"):
    require(fragment in filters, "pure shared filter transfer/nominal-response missing: "+fragment)
for fragment in ("FilterResponseSupport.slewStep(", "FilterResponseSupport.slewTicksToTarget(",
                 "RedstoneObservationSupport.observe(level, pos, seriesInputSide(state))",
                 "hitResult.getDirection() == net.minecraft.core.Direction.UP"):
    require(fragment in slew, "slew filter lost rate or valid-source commissioning: "+fragment)
for fragment in ("FilterResponseSupport.lapisLowPassStep(", "FilterResponseSupport.predictedNextLapis(",
                 "if (!level.hasChunkAt(inputPos)) return PortQuality.STALE;",
                 "runtime[PREVIOUS_OUTPUT_SLOT] = previous",
                 "hit.getDirection() == Direction.UP"):
    require(fragment in lapis, "Lapis first-order filter lost state/coverage/predictor: "+fragment)
for fragment in ("CONDITIONER FIELD TEST", "RedstoneObservationSupport.observe",
                 "calculate(observed.value(), actual, mode, param)",
                 "limitingActive(level, pos, state)", "SignalConditionerMenu.BUTTON_PARAM_INCREASE"):
    require(fragment in conditioner, "conditioner lost bounded formula or source quality: "+fragment)
for fragment in ("boolean sample = trigger.valid()", "if (!trigger.valid())",
                 "rt[INITIALIZED_SLOT] = 0", "if (input.valid())",
                 "rt[HELD_SLOT] = input.value()", "rt[OUTPUT_READY_SLOT] = 1",
                 "RedstoneObservationSupport.observe(level, pos, inputSide(state))",
                 "SampleHoldBlock"):
    require(fragment in sampler, "Sample & Hold lost valid-zero/unknown-clock safety: "+fragment)

for forbidden in ("setBlock(", "scheduleTick(", "RuntimeIntStore.get(", "getBlockEntity("):
    require(forbidden not in ray and forbidden not in filters,
            "pure helper must not mutate world or allocate runtime: "+forbidden)
for method in ("redstoneRangeSeparatesUninitializedCoverageFromActualTarget",
               "lapisRangeRayKeepsOwnDirectionAndNormalizedDistance",
               "redstoneSamplerOnlyCapturesValidZeroAndRejectsMissingInput"):
    require("void "+method+"(GameTestHelper helper)" in gt,
            "missing Minecraft test witness: "+method)
require("event.register(RseFilterRangeSamplingUpgradeGameTests.class);" in registration,
        "three new runtime tests are not registered")
workflow = (root / ".github/workflows/build.yml").read_text()
require("tools/rse_filter_range_sampling_upgrade_verify.py" in workflow,
        "new static regression is not in CI")

# Independent exact reference values for the equations used by the shared
# pure helpers. This is arithmetic sanity, not a Java invocation or GameTest.
def approach(prev, target, step):
    if prev < target: return min(target, prev+step)
    return max(target, prev-step)
for current, target, step, expected in [(0,15,2,2),(14,15,3,15),(10,0,4,6),
                                         (0,0,1,0),(15,0,4,11)]:
    require(approach(current,target,step)==expected, "slew arithmetic regression")
    require((abs(target-current)+step-1)//step >= 0, "invalid remaining tick prediction")
def lapis_step(prev, x, a):
    return max(0,min(100,math.floor(prev+a*(x-prev)+0.5)))
for prev,x,a,expected in [(0,100,.2,20),(100,0,.5,50),(80,80,.35,80),
                           (0,100,1.0,100),(0,0,.05,0)]:
    require(lapis_step(prev,x,a)==expected, "LPF reference equation regression")
for alpha in [.05,.1,.2,.35,.5,.65,.8,1.0]:
    n = 1 if alpha == 1 else math.ceil(math.log(.1)/math.log1p(-alpha))
    require(1<=n<=60, "invalid nominal response estimate")

if errors:
    print("RSE FILTER/RANGE/SAMPLING UPGRADE: FAIL")
    for error in errors: print(" -",error)
    sys.exit(1)
print("RSE FILTER/RANGE/SAMPLING UPGRADE: PASS")
print("  bounded non-loading ray with TARGET/CLEAR/INCOMPLETE quality: PASS")
print("  device-specific entity versus solid/fluid sensing: PASS")
print("  distinct slew and first-order low-pass transfer laws: PASS")
print("  read-only observed source/response/parameter commissioning: PASS")
print("  valid zero capture and no spurious edge after missing clock: PASS")
print("  three Minecraft GameTests registered (runtime execution separately required)")
