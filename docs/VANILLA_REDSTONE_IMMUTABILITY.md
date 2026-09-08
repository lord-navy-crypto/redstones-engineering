# Vanilla Redstone Immutability Gate

## Current plan status

RSE's current Vanilla Redstone Engineering (VRE) implementation remains an observer/diagnostics overlay. **Phase 5 optimized runtime is not active.** There is no replacement redstone solver in the current build and no opt-in engine rewrite is enabled.

The active implementation is limited to:

- bounded structural inspection;
- bounded server-side `NeighborNotifyEvent` telemetry;
- game-tick timing and listener-observation-order evidence;
- conservative pulse / feedback / QC / order-sensitivity classifications;
- player-facing diagnostics and redesign guidance.

The future `Phase 5 — Optional Optimized Runtime` in `VANILLA_REDSTONE_ENGINEERING.md` remains a proposal only. Any future optimized runtime must be **explicitly optional** and **fail closed to vanilla semantics** whenever equivalence has not been demonstrated.

## Non-negotiable production boundary

The active VRE layer must not:

- mixin or inject vanilla redstone dust, repeater, comparator, observer or piston behavior;
- cancel vanilla/NeoForge redstone physics events;
- write vanilla redstone `BlockState` as part of observation;
- schedule vanilla redstone ticks;
- issue neighbor updates on behalf of the diagnostic observer;
- replace Minecraft's redstone solver;
- run bounded structural scans from the `NeighborNotifyEvent` hot path.

GameTest fixtures may of course place and change vanilla blocks; that is how the suite proves that Minecraft's normal behavior still works with RSE installed.

## Vanilla Immutability Regression Gate

The automated preservation suite runs on the normal RSE GameTest server with VRE telemetry registered. It therefore tests vanilla behavior while the RSE observer is actually active.

Current core preservation fixtures lock:

1. redstone dust attenuation: `15 -> 14 -> 13 -> 12`;
2. repeater configured delay: a delay-4 repeater must not fire early, must later propagate, and its configured delay must remain unchanged;
3. comparator modes: equal rear/side inputs must remain powered in `COMPARE` and zero-output in `SUBTRACT`;
4. observer pulses: a real neighbor change must produce a finite pulse that returns LOW;
5. piston direct-power lifecycle: direct power must extend the piston and power removal must retract it.

A fail-closed static verifier additionally checks the VRE production surface for mutation APIs, rejects vanilla-redstone mixin/coremod/access-transformer hooks, and keeps the `NeighborNotifyEvent` listener limited to bounded observer bookkeeping.

## What this gate does not authorize

Passing this regression gate does **not** authorize Phase 5. Before an optional optimized runtime can even be considered, the broader compatibility matrix in `VANILLA_REDSTONE_ENGINEERING.md` still needs explicit equivalence coverage for at least quasi-connectivity, rotation/location-sensitive update-order cases, chunk load/unload boundaries, and RSE-instrument interoperability in addition to the core fixtures above.

Until that larger matrix is complete, the safe architecture remains:

```text
Minecraft vanilla redstone simulation
             |
             | read-only evidence
             v
RSE VRE diagnostics / telemetry / guidance
```

not:

```text
RSE -> replacement vanilla redstone solver
```
