# Redstone Systems Engineering (RSE)

[![RSE Build Verification](https://github.com/lord-navy-crypto/redstones-engineering/actions/workflows/build.yml/badge.svg)](https://github.com/lord-navy-crypto/redstones-engineering/actions/workflows/build.yml)

**Redstone Systems Engineering** is a NeoForge engineering-systems mod for Minecraft that extends vanilla redstone without replacing it.

RSE keeps the vanilla **0–15 redstone signal as the world-facing engineering boundary** and builds measurement, conditioning, sampling, control, actuation, diagnostics, communications, and operations tools around it.

> **Engineering path:** Measurement → Conditioning → Sampling → Control → Actuation → Optimization

## Project information

| Item | Current RSE baseline |
| --- | --- |
| Development milestone | **Alpha 1.0.4 — Instrumentation & Explicit Topology** |
| Artifact version | `1.0.4-alpha` |
| Minecraft | `1.21.1` |
| NeoForge | `21.1.249` |
| Java | `21` |
| Mod ID | `redstoneengineering` |
| License | **MIT** |
| Language | Java |

Alpha 1.0.4 is a refinement milestone: it improves **how engineering systems are measured and how ports/topology are represented**, rather than increasing block count for its own sake.

## Design principles

1. **Preserve the 0–15 boundary.** Normal Minecraft-facing redstone remains compatible with vanilla signal strengths.
2. **Measure before controlling.** Probes, analyzers, scopes, monitors, and diagnostics are first-class engineering systems.
3. **Make ports explicit.** Input, output, feedback, inhibit, measurement, and domain ports should have understandable directions.
4. **Configuration should have physical meaning.** Gain, threshold, slew, setpoint, channel, valve opening, pressure limit, and controller tuning represent engineering concepts.
5. **Model useful non-ideal behavior.** Saturation, finite slew, loss, contention, interference, pressure drop, queue buildup, and safety limits are intentional.
6. **Avoid BlockState explosion.** High-cardinality runtime measurements belong in transient/runtime storage or dedicated data structures.
7. **Keep automation inspectable.** Control and optimization features expose state and diagnostics instead of acting as unexplained magic blocks.

## Alpha 1.0.4 focus

### Signal Analyzer: TAP and INLINE

The Signal Analyzer now has two measurement topologies:

- **TAP** — non-invasive side measurement. The analyzer does not connect to the redstone network.
- **INLINE** — the TEST face becomes the measured input and the opposite face becomes a lossless `0..15` pass-through output.

The analyzer continuously tracks transient diagnostics outside BlockState:

- sample count;
- min/max;
- total changes;
- rising/falling changes;
- last delta and maximum delta;
- time since the last change;
- mode-switch count.

Interaction summary:

```text
Right-click top:       TAP ↔ INLINE
Shift + top:           reset analyzer statistics
Shift + other face:    six-side survey
Right-click other face: measurement + diagnostics
```

### Direction-aware measurement

Signal Analyzer and Signal Probe measurement now use the **actual tested face** when reading directional signal sources. A directional block is no longer reported using its strongest unrelated output side.

Explicit conductor nodes such as redstone dust and RSE signal cable still report their node value directly, preventing adjacent stronger sources from contaminating an attenuated-node measurement.

### Instrument-network topology diagnostics

The instrument network used by probes, oscilloscopes, and logic analyzers now reports:

- cable-node count;
- probe-node count;
- active channels;
- duplicate/ambiguous channels;
- bounded versus truncated network scan state.

Oscilloscope and Logic Analyzer status output exposes this topology information alongside waveform/trigger data.

### Directional pneumatic feedback

The Pneumatic Cylinder keeps its pneumatic input on the back side and now provides its `0..15` position-feedback redstone signal **only through the front/FACING side**.

```text
BACK  = pneumatic input
FRONT = redstone position feedback 0..15
```

This replaces the earlier all-side feedback behavior and makes the actuator usable as an explicit plant + sensor element in closed-loop systems.

Cylinder diagnostics also expose pressure, peak pressure, target position, velocity, position error, travel, reversals, and sample count.

## Alpha 1.0.3 foundation

Alpha 1.0.4 builds on the closed-loop and diagnostics work completed in Alpha 1.0.3:

- PID **AUTO/MANUAL**, output limits, anti-windup, deadband, filtered derivative and bumpless Manual→Auto transfer;
- Servo **POSITION/VELOCITY**, centered `7=stop` velocity command, brake and soft-limit diagnostics;
- Data Bus physical-driver versus distinct-value contention diagnostics;
- accumulated Radio valid/undecodable/collision/dropout diagnostics;
- Pneumatic Proportional Valve, Relief Valve and Cylinder integration;
- IOE operating-state classifications and queue/downtime metrics.

## Engineering systems

RSE currently includes or develops:

- **Instrumentation & measurement** — probes, analyzer, oscilloscope, logic analyzer, instrumentation cables and topology diagnostics.
- **Signal conditioning** — gain, offset, clamp, threshold, deadband and mapping primitives.
- **Digital systems & communications** — data buses, serial-style information systems, contention diagnostics and radio links.
- **Closed-loop control** — PID control with manual/auto operation and response diagnostics.
- **Mechatronics** — finite-speed servo behavior with position/velocity control.
- **Pneumatics** — compressors, reservoirs, pipes, regulators, valves, flow measurement, safety relief and cylinders.
- **Operations / IOE** — throughput, utilization, cycle time, downtime, queue/WIP and operating-state classification.
- **Additional domains** — mechanical, thermal and other experimental engineering systems.

## Reference calculations

Minecraft-facing outputs are clamped to the legal redstone range unless a subsystem explicitly uses a richer internal engineering scale.

### Redstone normalization

```text
S ∈ {0, …, 15}
x = S / 15
```

### Signal conditioning

```text
Gain:       S_out = clamp(round(G × S_in), 0, 15)
Offset:     S_out = clamp(S_in + b, 0, 15)
Threshold:  S_out = S_in if S_in >= T else 0
```

Linear mapping:

```text
t = clamp((S_in - in_min) / (in_max - in_min), 0, 1)
S_out = clamp(round(out_min + t × (out_max - out_min)), 0, 15)
```

### Analyzer INLINE mode

The analyzer is an ideal measurement pass-through at the RSE redstone abstraction boundary:

```text
S_measure = S_test
S_out     = S_measure
0 <= S_out <= 15
```

It does not intentionally add gain or attenuation; downstream vanilla redstone can still apply its normal propagation behavior.

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

## Build and development

Requirements: **JDK 21**, Git, and the included NeoForge/Gradle project.

```bash
./gradlew compileJava --no-daemon --stacktrace
./gradlew clean build --no-daemon --stacktrace
./gradlew runClient
```

Build output is placed under `build/libs/`.

## Verification and CI artifacts

The GitHub Actions pipeline performs:

1. repository metadata/license/hygiene verification;
2. RSE static and milestone-specific verification;
3. Java 21 `compileJava`;
4. clean Gradle build;
5. SHA-256 generation for built JARs;
6. upload of verified JAR(s) plus `SHA256SUMS.txt` as a short-retention test artifact.

CI proves repository/build integrity; **interactive `runClient` behavior remains a separate validation gate**.

## Documentation

- [`CHANGELOG.md`](CHANGELOG.md) — milestone history.
- [`CONTRIBUTING.md`](CONTRIBUTING.md) — engineering and verification rules.
- [`docs/ALPHA1_0_3_CLOSED_LOOP_DIAGNOSTICS.md`](docs/ALPHA1_0_3_CLOSED_LOOP_DIAGNOSTICS.md) — Alpha 1.0.3 control/diagnostics foundation.
- [`docs/ALPHA1_0_3_TEST_MATRIX.md`](docs/ALPHA1_0_3_TEST_MATRIX.md) — Alpha 1.0.3 interactive test gate.
- `docs/ALPHA1_0_4_INSTRUMENTATION_TOPOLOGY.md` — Alpha 1.0.4 engineering contract.
- `docs/ALPHA1_0_4_TEST_MATRIX.md` — Alpha 1.0.4 interactive validation matrix.

## License

RSE is released under the **MIT License**. See [`LICENSE`](LICENSE).

## Project links

- Repository: <https://github.com/lord-navy-crypto/redstones-engineering>
- Issues: <https://github.com/lord-navy-crypto/redstones-engineering/issues>
- Actions: <https://github.com/lord-navy-crypto/redstones-engineering/actions>
