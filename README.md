# Redstone Systems Engineering (RSE)

[![RSE Build Verification](https://github.com/lord-navy-crypto/redstones-engineering/actions/workflows/build.yml/badge.svg)](https://github.com/lord-navy-crypto/redstones-engineering/actions/workflows/build.yml)

**Redstone Systems Engineering** is a NeoForge engineering-systems mod for Minecraft that extends vanilla redstone without replacing it.

RSE keeps the vanilla **0–15 redstone signal as the world-facing engineering boundary** and builds measurement, conditioning, sampling, control, actuation, diagnostics, communications, and operations tools around it.

> **Engineering path:** Measurement → Conditioning → Sampling → Control → Actuation → Optimization

## Project information

| Item | Current RSE baseline |
| --- | --- |
| Development milestone | **Alpha 1.0.5 — Quality, Calibration & Regression Hardening** |
| Artifact version | `1.0.5-alpha` |
| Minecraft | `1.21.1` |
| NeoForge | `21.1.249` |
| Java | `21` |
| Mod ID | `redstoneengineering` |
| License | **MIT** |
| Language | Java |

Alpha 1.0.5 is a quality milestone. It deepens **measurement quality, capture integrity, save/reload consistency, source/resource auditing, and regression detection** rather than adding blocks for the sake of block count.

## Design principles

1. **Preserve the 0–15 boundary.** Normal Minecraft-facing redstone remains compatible with vanilla signal strengths.
2. **Measure before controlling.** Probes, analyzers, scopes, monitors, and diagnostics are first-class engineering systems.
3. **Make ports explicit.** Input, output, feedback, inhibit, measurement, and domain ports should have understandable directions.
4. **Separate measurement from intervention.** Instrument calibration may change a displayed reading but must not silently alter the circuit under test.
5. **Configuration should have physical meaning.** Gain, threshold, slew, setpoint, channel, valve opening, pressure limit, and controller tuning represent engineering concepts.
6. **Model useful non-ideal behavior.** Saturation, finite slew, loss, contention, interference, pressure drop, queue buildup, and safety limits are intentional.
7. **Avoid BlockState explosion.** High-cardinality runtime measurements belong in transient/runtime storage or dedicated data structures.
8. **Keep automation inspectable.** Control and optimization features expose state and diagnostics instead of acting as unexplained magic blocks.
9. **Regression gates are part of the product.** A new feature is not accepted if it breaks older validated engineering contracts.

## Alpha 1.0.5 focus

### Signal Analyzer: recent-window quality

Signal Analyzer keeps the Alpha 1.0.4 `TAP` and `INLINE` topologies and now adds a **16-sample rolling window**.

Recent diagnostics include:

- rolling average;
- peak-to-peak range;
- mean absolute sample step;
- sample age;
- `WARMUP`, `STEADY`, `STABLE`, `DYNAMIC`, or `HIGH_VARIATION` classification.

Lifetime diagnostics remain separate:

- sample count;
- lifetime min/max;
- total changes;
- rising/falling changes;
- last and maximum delta;
- time since last change;
- mode/calibration switch counts.

This distinction matters because an old transient should not make a currently stable signal look permanently unstable.

### Analyzer display calibration

Analyzer now has a small persistent display calibration offset:

```text
-2, -1, 0, +1, +2
```

Displayed value:

```text
S_display = clamp(S_raw + calibration_offset, 0, 15)
```

The critical rule is:

```text
INLINE OUT = S_raw
```

Calibration changes the instrument readout only. It does **not** condition or modify the downstream redstone signal.

Interaction summary:

```text
Right-click UP:         TAP ↔ INLINE
Shift + UP:             reset transient analyzer statistics
Right-click DOWN:       cycle display calibration -2..+2
Shift + other face:     six-side raw survey
Other normal click:     raw + calibrated + rolling/lifetime diagnostics
```

### Instrument-network structural integrity

Instrument networks now report:

- cable count;
- probe count;
- valid channels;
- active channels;
- duplicate channels;
- duplicate probes;
- maximum cable depth;
- maximum probe depth;
- structural integrity state.

Integrity states:

```text
OK
NO_PROBES
AMBIGUOUS
TRUNCATED
```

Duplicate channels remain deliberately invalid rather than using last-writer-wins behavior.

### Oscilloscope capture quality

Oscilloscope now reports:

- sample period: `2 ticks`;
- capture count;
- valid-sample coverage;
- `NO_DATA`, `WARMUP`, `COMPLETE`, `PARTIAL`, or `POOR_COVERAGE` capture state;
- average signal value;
- mean absolute sample step;
- min/max/peak-to-peak;
- estimated period in samples and ticks;
- cursor delta in samples and ticks.

Post-trigger progress is now persisted across save/reload.

### Logic Analyzer capture quality

Logic Analyzer now reports:

- sample period: `1 tick`;
- capture coverage per channel;
- edge count;
- transition rate;
- duty cycle;
- capture-quality classification;
- cursor timing in samples and ticks.

Its post-trigger capture progress is also persisted across save/reload.

## Alpha 1.0.4 topology foundation

Alpha 1.0.5 preserves the topology work completed in Alpha 1.0.4:

- Analyzer `TAP` is non-invasive.
- Analyzer `INLINE` has explicit TEST and opposite OUT signal-path faces.
- Direction-aware measurement reads the actual tested face of directional sources.
- Redstone dust and explicit RSE conductor nodes report their own node power.
- Instrument networks expose bounded/ambiguous topology diagnostics.
- Pneumatic Cylinder emits position feedback only from FRONT/FACING.
- Pneumatic Cylinder is a terminal one-port pneumatic actuator: pressure enters BACK and cannot pass through FRONT/sides to bridge another network.

## Alpha 1.0.3 control foundation

The current quality work also preserves:

- PID **AUTO/MANUAL**, output limits, anti-windup, deadband, filtered derivative, and bumpless Manual→Auto transfer;
- Servo **POSITION/VELOCITY**, centered `7=stop` velocity command, brake, and soft-limit diagnostics;
- Data Bus physical-driver versus distinct-value contention diagnostics;
- accumulated Radio valid/undecodable/collision/dropout diagnostics;
- Pneumatic proportional and relief/safety behavior;
- IOE operating-state classification and queue/downtime metrics.

## Engineering systems

RSE currently includes or develops:

- **Instrumentation & measurement** — probes, analyzer, oscilloscope, logic analyzer, instrumentation cable, measurement windows, calibration, capture coverage, and topology diagnostics.
- **Signal conditioning** — gain, offset, clamp, threshold, deadband, and mapping primitives.
- **Digital systems & communications** — data buses, serial-style information systems, contention diagnostics, and radio links.
- **Closed-loop control** — PID control with manual/auto operation and response diagnostics.
- **Mechatronics** — finite-speed servo behavior with position/velocity control.
- **Pneumatics** — compressors, reservoirs, pipes, regulators, valves, flow measurement, safety relief, and terminal cylinders.
- **Operations / IOE** — throughput, utilization, cycle time, downtime, queue/WIP, and operating-state classification.
- **Additional domains** — mechanical, thermal, and other experimental engineering systems under the same measurement/control philosophy.

## Reference calculations

### Redstone normalization

```text
S ∈ {0, …, 15}
x = S / 15
```

### Analyzer rolling metrics

For recent samples `x_1 ... x_n`, `n <= 16`:

```text
average  = sum(x_i) / n
p2p      = max(x_i) - min(x_i)
meanStep = sum(|x_i - x_(i-1)|) / (n - 1)
```

### Analyzer calibration

```text
S_display = clamp(S_raw + offset, 0, 15)
offset ∈ {-2, -1, 0, +1, +2}
```

INLINE remains:

```text
S_out = S_raw
```

### Signal conditioning

```text
Gain:       S_out = clamp(round(G × S_in), 0, 15)
Offset:     S_out = clamp(S_in + b, 0, 15)
Threshold:  S_out = S_in if S_in >= T else 0
```

### PID reference model

```text
e = setpoint - process
I_candidate = clamp(I_previous + e, -180, 180)
D_filtered  = low_pass(e - e_previous)
u_raw       = bias + P + I + D
u            = clamp(u_raw, 0, 15)
```

### Servo velocity convention

```text
v_command = S_command - 7
```

`7 = stop`, values below 7 command reverse motion, and values above 7 command forward motion.

### Pneumatic proportional-valve model

The pneumatic domain currently uses an internal pressure scale of approximately `0..100` while redstone commands remain `0..15`:

```text
P_out ≈ (P_in × opening + 7) / 15
```

### Operations metrics

For a 60-second monitoring window:

```text
utilization = running_ticks / window_ticks
throughput  = completed_cycles / minute
average_WIP = sum(queue_proxy) / sampled_ticks
```

## Quality and verification architecture

Alpha 1.0.5 treats verification as an engineering layer.

CI now runs this ladder:

1. **Python verifier syntax** — `python3 -m compileall -q tools`.
2. **Repository verifier** — metadata, version, license, workflow, and root hygiene.
3. **Source/resource quality audit** — Java package/path consistency, brace/whitespace smoke checks, JSON parse/collision checks, local model-reference integrity, item-model pairing, BlockState cardinality, and temporary-file detection.
4. **Deterministic reference-model tests** — `0..15`, calibration isolation, rolling metrics, topology integrity, and timebase mathematics.
5. **Historical regression verifiers** — redstone plus previous Alpha milestones.
6. **Alpha 1.0.5 verifier** — quality/calibration/save-reload contracts.
7. **Java 21 `compileJava`**.
8. **Gradle `test`**.
9. **Clean Gradle build**.
10. **SHA-256 generation and verified JAR artifact upload**.

CI proves repository/build integrity. Interactive Minecraft behavior remains a separate `runClient` validation gate.

## Build and development

Requirements: **JDK 21**, Git, and the included NeoForge/Gradle project.

```bash
./gradlew compileJava --no-daemon --stacktrace
./gradlew test --no-daemon --stacktrace
./gradlew clean build --no-daemon --stacktrace
./gradlew runClient
```

Build output is placed under `build/libs/`.

## Documentation

- [`CHANGELOG.md`](CHANGELOG.md) — milestone history.
- [`CONTRIBUTING.md`](CONTRIBUTING.md) — engineering and verification rules.
- [`docs/ALPHA1_0_5_QUALITY_CALIBRATION.md`](docs/ALPHA1_0_5_QUALITY_CALIBRATION.md) — current quality/calibration engineering contract.
- [`docs/ALPHA1_0_5_TEST_MATRIX.md`](docs/ALPHA1_0_5_TEST_MATRIX.md) — current interactive validation matrix.
- [`docs/ALPHA1_0_4_INSTRUMENTATION_TOPOLOGY.md`](docs/ALPHA1_0_4_INSTRUMENTATION_TOPOLOGY.md) — explicit measurement/topology foundation.
- [`docs/ALPHA1_0_4_TEST_MATRIX.md`](docs/ALPHA1_0_4_TEST_MATRIX.md) — Alpha 1.0.4 interactive matrix.
- [`docs/ALPHA1_0_3_CLOSED_LOOP_DIAGNOSTICS.md`](docs/ALPHA1_0_3_CLOSED_LOOP_DIAGNOSTICS.md) — control/diagnostics foundation.

## License

RSE is released under the **MIT License**. See [`LICENSE`](LICENSE).

## Project links

- Repository: <https://github.com/lord-navy-crypto/redstones-engineering>
- Issues: <https://github.com/lord-navy-crypto/redstones-engineering/issues>
- Actions: <https://github.com/lord-navy-crypto/redstones-engineering/actions>
