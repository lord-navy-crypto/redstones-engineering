# Vanilla Redstone Engineering (VRE)

## Purpose

Vanilla Redstone Engineering is the RSE program for improving the engineering experience of Minecraft's native redstone without treating vanilla mechanics as disposable implementation details.

The governing rule is **Vanilla-first**: existing redstone builds should continue to behave as Minecraft defines them unless the player explicitly opts into a future experimental runtime mode.

## Phase 1 — Diagnostics Only

The first phase is observation and explanation only.

- No mixins into vanilla redstone blocks.
- No replacement redstone solver.
- No changes to dust attenuation, repeater delay, comparator logic, observer pulses, piston behavior, quasi-connectivity, or vanilla update order.
- No unbounded world scans.
- Diagnostics must distinguish measured runtime facts from structural proxies and advisories.

The existing Topology Debugger can inspect a vanilla-redstone target and switch from RSE EngineeringPort topology to a bounded vanilla profile. The profile follows directly adjacent redstone-relevant components only and therefore must not claim to be an exact Minecraft update graph.

Current evidence includes:

- redstone dust count and powered-dust count;
- maximum observed dust power within the 0..15 vanilla boundary;
- repeater/comparator/observer inventory;
- configured repeater delay expressed in game ticks;
- source and actuator counts;
- maximum directly-adjacent redstone-component degree as a local fan-out proxy;
- possible quasi-connectivity dependency evidence for QC-capable blocks;
- unloaded traversal boundaries and traversal-cap status.

QC, observer density, component density, and fan-out are **advisories**, not automatic faults. In phase one, the Topology Debugger raises its existing alarm only when the diagnostic observation itself is incomplete because a relevant boundary is unloaded or the bounded traversal cap is reached.

## Phase 2 — Runtime Update Profiling

A later phase may add server-authoritative telemetry for actual redstone-related update activity. It must measure real events instead of converting structural density into a fabricated updates-per-second metric.

Candidate outputs:

- actual redstone-relevant neighbor notifications over a bounded rolling window;
- update hotspots;
- cascade depth where observable without changing vanilla scheduling;
- repeated/redundant-update evidence;
- observer pulse activity;
- per-plant redstone activity summaries.

Any event hook must remain read-only and bounded.

## Phase 3 — Timing and Order Analysis

Build on runtime evidence to explain:

- configured repeater timing;
- observed signal transition timing;
- pulse-width issues;
- likely order-sensitive circuits;
- observer feedback;
- QC-dependent activation evidence;
- chunk-boundary or unloaded-boundary diagnostic uncertainty.

The tool must report evidence and confidence rather than claim a causal sequence that has not been observed.

## Phase 4 — Reliability and Optimization Guidance

Use the diagnostics to recommend player-controlled redesigns before changing the engine itself:

- isolate high-activity regions;
- reduce unnecessary fan-out;
- replace fragile order dependencies;
- identify long dust runs and timing bottlenecks;
- expose feedback loops;
- compare two circuit designs using repeatable measurements.

This is the preferred form of optimization because it preserves vanilla behavior.

## Phase 5 — Optional Optimized Runtime

Only after strong regression fixtures exist should RSE consider an opt-in optimized vanilla runtime. Compatibility tests must cover at minimum:

- dust 0..15 attenuation;
- repeater timing;
- comparator behavior;
- observer pulses;
- pistons and quasi-connectivity;
- rotation/location-sensitive update-order cases;
- chunk load/unload boundaries;
- interaction with RSE instruments.

Any optimized mode must be explicitly optional and fail closed to vanilla semantics when equivalence is not demonstrated.

## Architectural boundary

VRE does not replace the RSE EngineeringPort system. The two views are complementary:

```text
Vanilla redstone target
        -> bounded VRE diagnostics

RSE engineering device
        -> EngineeringTopologyView / EngineeringPort diagnostics
```

Both may feed the common event/operations evidence layer, but neither diagnostic view owns the physical simulation.
