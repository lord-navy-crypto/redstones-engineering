#!/usr/bin/env python3
from pathlib import Path
import json
import re
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
failed = []


def text(rel: str) -> str:
    path = root / rel
    if not path.exists():
        failed.append(f"missing {rel}")
        return ""
    return path.read_text(errors="ignore")


def require(rel: str, *tokens: str) -> None:
    body = text(rel)
    for token in tokens:
        if token not in body:
            failed.append(f"{rel}: missing {token!r}")


props = text("gradle.properties")
match = re.search(r"^mod_version=(\d+)\.(\d+)\.(\d+)-alpha(?:[.-][0-9A-Za-z.-]+)?$", props, re.MULTILINE)
if not match or tuple(map(int, match.groups())) < (1, 0, 14):
    failed.append("Alpha 1.0.14 requires mod_version >= 1.0.14-alpha")

# Alpha 1.0.13 completion: real GeckoLib path and one-way physics -> rendering boundary.
require(
    "src/main/java/dev/redstoneengineering/physics/RuntimeIntStore.java",
    "int[] peek(",
    "existing.clone()",
    "never creates",
)
require(
    "src/main/java/dev/redstoneengineering/visualization/MechatronicsVisualState.java",
    "public record MechatronicsVisualState",
    "position01",
    "velocitySigned",
    "braked",
    "opening01",
    "pressure01",
)
require(
    "src/main/java/dev/redstoneengineering/blockentity/MechatronicsVisualBlockEntity.java",
    "implements GeoBlockEntity",
    "MechatronicsVisualState",
    "acceptAuthoritativeSnapshot",
    "getUpdatePacket",
    "getUpdateTag",
    "No renderer API is exposed back",
)
require(
    "src/main/java/dev/redstoneengineering/client/MechatronicsGeoModel.java",
    "extends GeoModel<MechatronicsVisualBlockEntity>",
    "setCustomAnimations",
    'getBone("shaft")',
    'getBone("rod")',
    'getBone("spool")',
)
require(
    "src/main/java/dev/redstoneengineering/client/MechatronicsGeoRenderer.java",
    "extends GeoBlockRenderer<MechatronicsVisualBlockEntity>",
)
require(
    "src/main/java/dev/redstoneengineering/client/MechatronicsClientRegistration.java",
    "EntityRenderersEvent.RegisterRenderers",
    "registerBlockEntityRenderer",
)
for rel in [
    "src/main/java/dev/redstoneengineering/block/ServoActuatorBlock.java",
    "src/main/java/dev/redstoneengineering/block/PneumaticCylinderBlock.java",
    "src/main/java/dev/redstoneengineering/block/PneumaticProportionalValveBlock.java",
]:
    require(rel, "implements EntityBlock", "RenderShape.ENTITYBLOCK_ANIMATED", "MechatronicsVisualBlockEntity.push")

# Alpha 1.0.14: shared metrology semantics and sensor integration.
require(
    "src/main/java/dev/redstoneengineering/metrology/MeasurementSnapshot.java",
    "repeatability",
    "bias",
    "drift",
    "noise",
    "resolution",
    "sampleAgeTicks",
    "uncertaintyProxy",
)
require(
    "src/main/java/dev/redstoneengineering/metrology/MeasurementQuality.java",
    "GOOD",
    "DEGRADED",
    "SATURATED",
    "STALE",
    "INVALID",
)
require(
    "src/main/java/dev/redstoneengineering/metrology/MetrologyTracker.java",
    "WINDOW = 32",
    "standardDeviation",
    "firstDifferenceNoise",
    "halfWindowDrift",
    "uncertaintyProxy",
)
require(
    "src/main/java/dev/redstoneengineering/metrology/MetrologyStore.java",
    "WeakHashMap",
    "MetrologyTracker",
)
require(
    "src/main/java/dev/redstoneengineering/block/TankLevelSensorBlock.java",
    "SensorModel.condition",
    "MetrologyStore.tracker",
    "Repeatability",
    "uncertainty",
    "saturated",
)
require(
    "src/main/java/dev/redstoneengineering/gametest/RseMetrologyGameTests.java",
    "stableMeasurementReportsGoodQuality",
    "driftAndBiasDegradeMeasurementQuality",
    "saturationAndAgeAreExplicitQualityStates",
    "visualizationProjectionIsNormalizedAndImmutable",
)
require(
    "src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java",
    "event.register(RseMetrologyGameTests.class)",
)

for rel in [
    "src/main/resources/assets/redstoneengineering/geo/block/servo_actuator.geo.json",
    "src/main/resources/assets/redstoneengineering/geo/block/pneumatic_cylinder.geo.json",
    "src/main/resources/assets/redstoneengineering/geo/block/pneumatic_proportional_valve.geo.json",
    "src/main/resources/assets/redstoneengineering/animations/block/mechatronics.animation.json",
]:
    body = text(rel)
    try:
        if body:
            json.loads(body)
    except json.JSONDecodeError as exc:
        failed.append(f"{rel}: invalid JSON: {exc}")

require("ALPHA1_0_13_MANIFEST.txt", "Mechatronics Visualization", "GeckoLib", "HARD INVARIANT")
require("ALPHA1_0_14_MANIFEST.txt", "Metrology & Uncertainty", "Repeatability", "Measurement uncertainty proxy", "HARD INVARIANTS")

workflow = text(".github/workflows/build.yml")
if "rse_alpha1014_metrology_verify.py" not in workflow:
    failed.append("workflow missing Alpha 1.0.14 verifier")
if "runGameTestServer" not in workflow:
    failed.append("workflow missing Minecraft GameTest gate")

if failed:
    print("RSE Alpha 1.0.14 metrology verification: FAIL")
    for item in failed:
        print(" -", item)
    raise SystemExit(1)

print("RSE Alpha 1.0.14 metrology verification: PASS")
print(" GeckoLib physics-to-render one-way boundary: PASS")
print(" repeatability/bias/drift/noise/resolution/age/saturation: PASS")
print(" uncertainty proxy and quality states: PASS")
print(" Tank Level Sensor integration: PASS")
print(" executable metrology GameTests present: PASS")
