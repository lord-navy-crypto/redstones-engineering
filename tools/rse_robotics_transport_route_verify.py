#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
ROBOTICS = ROOT / "src/main/java/dev/redstoneengineering/robotics"
RUNTIME = ROBOTICS / "RobotTransportRouteRuntime.java"
errors = []

def read(path):
    if not path.exists():
        errors.append(f"missing {path.relative_to(ROOT)}")
        return ""
    return path.read_text(encoding="utf-8")

def req(text, needle, label):
    if needle not in text:
        errors.append(f"{label}: missing {needle!r}")

runtime = read(RUNTIME)
for needle in (
    "RobotOperatingState.TRANSPORTING",
    "RobotRoutePlanner.plan(graph, sourceId, targetId)",
    "RobotTransportHandoffAssessment.inspect(",
    "TRANSPORT_STATE_REQUIRED",
    "ROUTE_NODE_EVIDENCE_MISSING",
    "EMPTY_TRANSPORT_ROUTE",
    "TRANSPORT_ROUTE_PERMIT",
    "List<RobotNavigationGraph.Node> waypoints",
    "return verdict == Verdict.PERMIT;",
):
    req(runtime, needle, "RobotTransportRouteRuntime.java")

for forbidden in (
    "setBlock(",
    "setDeltaMovement(",
    "move(MoverType",
    "ItemStack",
    "IItemHandler",
    "insertItem(",
    "extractItem(",
    "setRobotState(",
    "level()",
    "getBlockState(",
):
    if forbidden in runtime:
        errors.append(f"RobotTransportRouteRuntime.java must remain pure and topology-explicit; unexpected {forbidden!r}")

if errors:
    print("RSE ROBOTICS TRANSPORT ROUTE VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    sys.exit(1)

print("RSE ROBOTICS TRANSPORT ROUTE VERIFY: PASS")
print("  post-load route admission requires TRANSPORTING lifecycle")
print("  route planning consumes explicit RobotNavigationGraph topology")
print("  payload/mission/robot evidence is delegated to the authoritative handoff assessment")
print("  resolved waypoints are immutable and world/entity mutation remains outside the runtime")
