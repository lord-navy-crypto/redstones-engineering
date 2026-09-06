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

Current structural evidence includes:

- redstone dust count and powered-dust count;
- maximum observed dust power within the 0..15 vanilla boundary;
- repeater/comparator/observer inventory;
- configured repeater delay expressed in game ticks;
- source and actuator counts;
- maximum directly-adjacent redstone-component degree as a local fan-out proxy;
- possible quasi-connectivity dependency evidence for QC-capable blocks;
- unloaded traversal boundaries and traversal-cap status.

QC, observer density, component density, and fan-out are **advisories**, not automatic faults. The Topology Debugger raises its existing topology alarm only when the structural diagnostic observation itself is incomplete because a relevant boundary is unloaded or the bounded traversal cap is reached.

## Phase 2 — Runtime Update Profiling

Phase 2 adds server-authoritative, observer-only telemetry for actual NeoForge physics-notification traffic associated with vanilla-redstone components.

The source is NeoForge's server-side `BlockEvent.NeighborNotifyEvent`. RSE listens on `NeoForge.EVENT_BUS` and records two deliberately separate kinds of runtime evidence:

- **Neighbor Notification Events** — actual event-bus observations emitted by the NeoForge/Minecraft physics-notification path;
- **Observed State Transitions** — changes in selected vanilla-redstone state signatures between successive event observations at the same position, including dust `POWER`, `POWERED`, `LIT`, and `EXTENDED` where those properties exist.

These are real observations but they have strict semantics. Neighbor Notification Events are **not solver-evaluation counts**, and Observed State Transitions are not a claim to capture every internal block-state transition. Neither metric is TPS cost, CPU time, or an exact update-order trace.

Runtime retention and query scope are bounded:

- at most **4096** notification samples retained per loaded level;
- at most **1024** remembered source positions for transition comparison;
- level keys are weakly retained so the observer cache does not own world lifecycle;
- default query radius: **16 blocks**;
- hard query-radius cap: **64 blocks**;
- default rolling window: **100 game ticks**;
- hard rolling-window cap: **1200 game ticks**.

The runtime report exposes notification count, total notified sides, forced-redstone-update flags, observed state-transition count, unique source positions, and the busiest observed source position in the selected window. It intentionally does not derive a fabricated updates-per-second number from a partially populated rolling window.

The Topology Debugger now combines the bounded structural profile with this runtime report when inspecting a vanilla-redstone target. Runtime telemetry remains evidence only: it does not drive the debugger's alarm output, schedule vanilla ticks, cancel NeoForge events, mutate BlockState, or replace Minecraft's redstone solver.

Phase 2 therefore preserves the Phase-1 behavior boundary:

- no RSE mixin into vanilla redstone;
- no replacement solver;
- no changes to dust attenuation, repeaters, comparators, observers, pistons, quasi-connectivity, or vanilla update order;
- no unbounded world scan;
- no claim that event observations equal internal solver work.

## Phase 3 — Timing and Order Analysis

Phase 3 builds on the Phase-2 event ring and adds bounded **timing evidence** without pretending that an event listener can see Minecraft internals that it does not expose.

The timing report uses **server game ticks** (`ServerLevel.getGameTime()`) as its only timebase. It can therefore measure and report:

- first and last observed event game ticks within the bounded query window;
- active tick span occupied by the observations;
- minimum and maximum inter-observation spacing in game ticks;
- minimum and maximum spacing between separately observed state transitions;
- the number of same-tick observation pairs;
- same-tick pairs originating from distinct source positions;
- the monotonic listener observation-sequence range represented by the report.

Every retained event receives a monotonically increasing observer sequence number when RSE receives it. This sequence is useful for repeatable diagnostics and for detecting that two events were observed in a stable order within the same server tick. Its semantics are deliberately labeled:

`ORDER=OBSERVED_EVENT_ORDER_ONLY`

That label means the sequence is the order in which the RSE listener received events. It is **not causal update order**, not scheduler priority, not redstone solver order, and **not sub-tick time**. Phase 3 uses no wall-clock nanosecond timestamps because that would create false precision for server simulation ordering.

Configured timing and observed timing also remain separate. For example, a repeater configured to three redstone ticks contributes **6 game ticks** of configured delay evidence in the structural profile; an observed transition spacing of six game ticks is runtime evidence. The debugger may present both, but it does not claim causal equivalence unless a stronger future experiment establishes it.

The Topology Debugger now combines three evidence layers for vanilla targets:

1. bounded structural profile;
2. bounded NeighborNotifyEvent runtime profile;
3. bounded timing/order report.

Phase 3 also adds region-scoped observer-cache reset for GameTest isolation. Clearing RSE telemetry only clears diagnostic memory; it never changes world blocks, scheduled ticks, redstone power, or vanilla behavior.

Current Phase-3 boundaries therefore remain strict:

- no vanilla-redstone mixin;
- no replacement solver;
- no event cancellation;
- no telemetry-owned BlockState mutation or neighbor update;
- no wall-clock timing used as simulation timing;
- no claim that listener order is causal Minecraft update order;
- no claim of sub-tick timing precision;
- bounded radius/window/history remain inherited from Phase 2.

This foundation is intentionally narrower than the complete Phase-3 roadmap. Pulse-width classification, observer-feedback classification, QC activation correlation, and confidence-scored order-sensitive-circuit diagnosis should be layered on top of these measured primitives instead of guessed from static topology alone.

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

VRE does not replace the RSE EngineeringPort system. The views are complementary:

```text
Vanilla redstone target
        -> bounded structural VRE diagnostics
        -> bounded runtime NeighborNotifyEvent telemetry
        -> bounded game-tick timing / listener-order evidence

RSE engineering device
        -> EngineeringTopologyView / EngineeringPort diagnostics
```

Both may feed the common event/operations evidence layer, but neither diagnostic view owns the physical simulation.
