# RSE Alpha 1.0.4 — Instrumentation & Explicit Topology

Alpha 1.0.4 is a refinement milestone centered on **measurement correctness, explicit ports, and diagnosable topology**. It intentionally adds depth to existing engineering systems instead of expanding the block count.

## 1. Signal Analyzer measurement topologies

The Signal Analyzer has two modes.

### TAP mode

`TAP` is a non-invasive side measurement.

```text
[target node] ← TEST | [Signal Analyzer]
```

- The analyzer does not connect to vanilla redstone.
- The adjacent TEST node is sampled continuously.
- No redstone output is produced.
- This mode is intended for observing a circuit without intentionally changing its topology.

### INLINE mode

`INLINE` inserts the analyzer into a signal path.

```text
source/network → TEST [Signal Analyzer] OUT → downstream network
```

Port contract:

```text
TEST = FACING side, measured input
OUT  = side opposite FACING, 0..15 pass-through output
```

At the RSE abstraction boundary:

```text
S_measure = clamp(S_test, 0, 15)
S_out     = S_measure
```

The analyzer itself adds no intentional gain or attenuation. Vanilla redstone downstream may still apply its normal propagation rules.

Only `TEST` and `OUT` are redstone-connectable in INLINE mode. TAP remains completely non-connectable.

## 2. Analyzer runtime diagnostics

Measurements are sampled every two game ticks and stored in transient runtime state rather than BlockState.

Tracked values:

- sample count;
- latest sample;
- minimum;
- maximum;
- number of value changes;
- positive/rising changes;
- negative/falling changes;
- latest delta;
- maximum absolute delta;
- game tick of the latest change;
- number of TAP/INLINE mode changes.

This prevents high-cardinality instrumentation data from multiplying block model states.

### Interaction contract

```text
Normal right-click on UP:   toggle TAP / INLINE
Shift + right-click UP:     reset analyzer statistics
Shift + other face:         six-side survey
Normal click other face:    current value + accumulated diagnostics
```

## 3. Direction-aware measurement

Earlier measurement logic could ask an active block for its strongest signal on any face. That is useful for a rough source survey but incorrect for a probe attached to a **specific face** of a directional device.

Alpha 1.0.4 therefore distinguishes two cases:

### Explicit node values

For redstone dust and RSE conductor-node blocks, read the node's stored/derived power directly.

This avoids a classic measurement error:

```text
15 source → dust node with POWER=14
```

The dust node must be reported as `14`, not contaminated back to `15` by the adjacent source.

### Directional active sources

When an analyzer or probe knows the physical tested side, it asks for the signal emitted toward that instrument face. If the block is a signal source but emits zero on the tested face, the result remains zero instead of falling back to the strongest unrelated side.

The legacy orientation-unknown overload remains available for callers that truly do not know the probe direction.

## 4. Signal Probe refinement

Signal Probe remains non-invasive and channelized (`A/B/C/D`), but its sample now carries the probe's physical `FACING` into the measurement routine.

This makes Oscilloscope and Logic Analyzer measurements direction-correct automatically because their remote probes now sample the face they are physically attached to.

## 5. Instrument network topology diagnostics

`InstrumentNetwork` remains a bounded breadth-first scan over Instrument Cable and Signal Probe blocks.

Alpha 1.0.4 adds topology metadata to each `ProbeSnapshot`:

```text
cableNodes
probeNodes
activeChannels
duplicateChannels
bounded / truncated
```

A channel is valid only when exactly one probe is assigned to it. Multiple probes assigned to one channel remain deliberately `AMBIGUOUS` rather than using last-writer-wins behavior.

Oscilloscope and Logic Analyzer status messages now expose a network summary such as:

```text
instrumentNet cables=12 probes=3 channels=3/4 duplicateChannels=0 scan=BOUNDED
```

This makes wiring mistakes diagnosable without turning instrument cable into a power network.

## 6. Pneumatic Cylinder explicit feedback and terminal topology

The Pneumatic Cylinder is a cross-domain terminal actuator: pneumatic pressure drives mechanical position, while redstone exposes a feedback measurement.

Alpha 1.0.4 makes both sides of that contract explicit:

```text
BACK/FACING.opposite = pneumatic input
FRONT/FACING         = redstone position feedback, 0..15
```

The cylinder no longer behaves like an all-side redstone source, and it no longer behaves like an inline pneumatic pipe.

### Terminal pneumatic behavior

The pneumatic solver treats the cylinder as a **one-port sink/actuator**:

- pressure may enter only through `BACK`;
- a cylinder cannot bridge two pneumatic networks;
- pressure does not propagate from the cylinder through `FRONT` or either side;
- network discovery follows the same one-port topology, so a network beyond a cylinder is not accidentally merged into the upstream network.

Conceptually:

```text
pipe → BACK [ Cylinder ] FRONT → redstone feedback
                    X
              no pneumatic pass-through
```

Feedback mapping remains:

```text
position ∈ [0, 15]
redstone_feedback = position
```

The pneumatic pressure domain remains approximately `0..100` internally.

Additional diagnostics include:

- current pressure;
- peak pressure;
- current/target position;
- velocity;
- position error;
- accumulated travel;
- motion reversals;
- sample count.

## 7. Engineering invariants

Alpha 1.0.4 must preserve these invariants:

1. TAP analyzer never connects to redstone.
2. INLINE analyzer has exactly two signal-path ports: TEST and OUT.
3. Analyzer output is always clamped to `0..15`.
4. High-cardinality analyzer statistics are not encoded as BlockState properties.
5. Direction-aware probes do not substitute an unrelated strongest source face.
6. Duplicate instrument channels remain invalid/ambiguous.
7. Instrument-network scanning is bounded.
8. Pneumatic Cylinder feedback is emitted only through its explicit FRONT/FACING port.
9. Pneumatic Cylinder accepts pneumatic pressure only through BACK and cannot pass pressure through itself.
10. Pneumatic domain data and redstone feedback remain conceptually separate.
11. Existing Alpha 1.0.3 PID/Servo/Bus/Radio/Pneumatic/IOE verification must continue to pass.

## 8. Validation

Repository validation:

```bash
python3 tools/rse_repo_verify.py
python3 tools/rse_alpha104_verify.py
./gradlew compileJava --no-daemon --stacktrace
./gradlew clean build --no-daemon --stacktrace
```

Interactive validation:

```bash
./gradlew runClient
```

Use `docs/ALPHA1_0_4_TEST_MATRIX.md` for the in-game behavioral gate.
