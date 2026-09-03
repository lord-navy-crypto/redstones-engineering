# RSE alpha.7.3 — Verification / Network Kernel Hardening

This release intentionally adds almost no new gameplay blocks. It hardens the transport/network foundation before alpha.8 Sensors & Transducers.

## Frozen invariants

1. Vanilla redstone remains 0..15 and world-facing.
2. Surface traces (Lapis / Quartz / Amethyst) connect only N/E/S/W.
3. 3-D cables (Redstone / Copper / Optical) may bend in six directions.
4. Plain cable has at most two ports; branches require explicit junction/splitter semantics.
5. Iron is a field domain; Thermal is a physical-state domain — neither gets a fake signal cable.
6. Runtime measurements never belong in high-cardinality BlockState properties.
7. Every graph scan is bounded to 128 loaded nodes.
8. Graph traversal never intentionally crosses into unloaded chunks.
9. Compile success is necessary but not sufficient: topology semantics must also be tested.

## Important alpha.7.2.1 semantic correction

The compile hotfix changed an invalid Java expression into a compilable one, but the first compilable form rejected horizontal X/Z edges. alpha.7.3 fixes the intended rule:

```java
if (d.getAxis() == Direction.Axis.Y) return false;
```

Only vertical Up/Down edges are rejected for floor traces.

## Network diagnostics

The shared `NetworkKernel` records the most recent node count and whether a scan hit the 128-node budget. Redstone cable, Copper cable, and Optical fiber right-click diagnostics now surface this information.

`BUDGET-LIMITED` is not a crash. It means the network is intentionally too large for one bounded RSE graph and should be segmented or redesigned.

## Release gate

Before any future alpha is considered publishable:

1. `python3 tools/rse_verify.py`
2. `./gradlew compileJava --no-daemon --console=plain`
3. `./gradlew clean build --no-daemon --console=plain`
4. `./gradlew runClient --no-daemon --stacktrace`
5. New test world
6. Test horizontal trace straight/corner/T/cross
7. Test cable straight/corner/vertical
8. Test plain cable 3-way topology error
9. Test junction/splitter branch behavior
10. Build a 128-node stress network and verify no runaway loading

## Why this matters

RSE is a systems-engineering project. A feature is not complete merely because it compiles. The model, topology, runtime state, resource cost, diagnostics, and failure behavior must agree.
