#!/usr/bin/env python3
"""Fail-closed static guard for the explicit AMR navigation network layer."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
GRAPH = ROOT / "src/main/java/dev/redstoneengineering/robotics/RobotNavigationGraph.java"
PLANNER = ROOT / "src/main/java/dev/redstoneengineering/robotics/RobotRoutePlanner.java"
errors: list[str] = []


def read(path: Path) -> str:
    if not path.is_file():
        errors.append(f"missing: {path.relative_to(ROOT)}")
        return ""
    return path.read_text(encoding="utf-8", errors="ignore")


def require(body: str, token: str, label: str) -> None:
    if token not in body:
        errors.append(f"{label}: missing {token!r}")


graph = read(GRAPH)
planner = read(PLANNER)

for token in (
    "record Node(String id, BlockPos position)",
    "record Edge(String fromId, String toId, double cost, boolean enabled)",
    "duplicate navigation node id",
    "edge references unknown source node",
    "edge references unknown target node",
    "edge cost must be finite and positive",
    "entry.getValue().sort(EDGE_ORDER)",
    "Collections.unmodifiableMap",
):
    require(graph, token, "RobotNavigationGraph.java")

for token in (
    "ROUTE_AVAILABLE",
    "GRAPH_MISSING",
    "SOURCE_UNKNOWN",
    "TARGET_UNKNOWN",
    "ROUTE_UNAVAILABLE",
    "PriorityQueue<QueueEntry>",
    "thenComparing(QueueEntry::nodeId)",
    "if (!edge.enabled()) continue;",
    "candidate < known",
    '"NO_EXPLICIT_ROUTE"',
    '"INCOMPLETE_ROUTE_EVIDENCE"',
    '"SHORTEST_EXPLICIT_ROUTE"',
):
    require(planner, token, "RobotRoutePlanner.java")

# This layer must consume declared topology only. World discovery and chunk
# manipulation belong to later authoritative infrastructure adapters, not the planner.
for label, body in (("RobotNavigationGraph.java", graph), ("RobotRoutePlanner.java", planner)):
    for forbidden in (
        "net.minecraft.world.level.Level",
        "getBlockState(",
        "getEntities(",
        "noCollision(",
        "setChunkForced(",
        "forceLoad",
        "getChunk(",
        "BlockPos.betweenClosed",
    ):
        if forbidden in body:
            errors.append(f"{label}: forbidden implicit-world discovery/runtime mutation {forbidden!r}")

if errors:
    print("RSE ROBOTICS NAVIGATION VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE ROBOTICS NAVIGATION VERIFY: PASS")
print("  topology: explicit immutable node/edge snapshot")
print("  planner: deterministic positive-cost shortest path")
print("  disabled edges: excluded from route evidence")
print("  missing endpoints/no-route: explicit fail-closed results")
print("  world scans/chunk forcing/runtime mutation: absent")
