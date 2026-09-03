# Redstone Systems Engineering (RSE)

[![RSE Build Verification](https://github.com/lord-navy-crypto/redstones-engineering/actions/workflows/build.yml/badge.svg)](https://github.com/lord-navy-crypto/redstones-engineering/actions/workflows/build.yml)

**Redstone Systems Engineering** is a NeoForge engineering-systems mod for Minecraft that extends vanilla redstone without replacing it.

RSE treats the vanilla **0–15 redstone signal as the world-facing engineering boundary** and builds instrumentation, signal conditioning, sampling, control, actuation, diagnostics, communications, and operations tools around it.

> **Engineering path:** Measurement → Conditioning → Sampling → Control → Actuation → Optimization

## Project information

| Item | Current RSE baseline |
| --- | --- |
| Development milestone | **Alpha 1.0.3 — Closed-Loop Systems & Diagnostics** |
| Artifact version | `1.0.3-alpha` |
| Minecraft | `1.21.1` |
| NeoForge | `21.1.249` |
| Java | `21` |
| Mod ID | `redstoneengineering` |
| License | **MIT** |
| Language | Java |
| CI status | Static verification + `compileJava` + clean build confirmed on `main` |
| Interactive release gate | Local `runClient` Alpha 1.0.3 test matrix |

Alpha 1.0.3 keeps the project **Vanilla-first**: ordinary redstone remains useful, while RSE adds engineering behavior where measurement, non-ideal response, diagnostics, topology, and control logic matter.

## Design principles

1. **Preserve the 0–15 boundary.** RSE devices may keep richer internal state, but normal world-facing redstone signals remain compatible with Minecraft's 0–15 scale.
2. **Measure before controlling.** Analyzers, probes, scopes, monitors, and diagnostic state are first-class systems rather than decorative extras.
3. **Configuration should have physical meaning.** Gain, threshold, slew, setpoint, channel, valve opening, pressure limit, and controller tuning represent engineering concepts.
4. **Non-ideal behavior matters.** Saturation, anti-windup, finite actuator speed, bus contention, radio interference, pressure loss, queue buildup, and safety limits are intentional parts of the model.
5. **Avoid BlockState explosion.** High-cardinality runtime values belong in transient/runtime storage or dedicated data structures rather than hundreds of model-state variants.
6. **Automation should remain inspectable.** Control and optimization features should expose their inputs, outputs, state, and diagnostics instead of becoming unexplained magic blocks.

## Engineering systems

RSE currently includes or develops the following layers:

- **Instrumentation & measurement** — signal probes, analyzers, oscilloscopes, logic/instrumentation tools, network diagnostics.
- **Signal conditioning** — gain, offset, clamp, threshold, deadband, conversion and signal-processing primitives.
- **Digital systems & communications** — data buses, serial-style information systems, contention diagnostics, radio channels and link-quality behavior.
- **Closed-loop control** — PID control with Manual/Auto operation, output limiting, anti-windup, bumpless transfer and step-response diagnostics.
- **Mechatronics** — finite-speed servo behavior with Position/Velocity modes, braking and soft-limit diagnostics.
- **Pneumatics** — compressors, reservoirs, pipes, regulators, valves, check valves, flow measurement, proportional valves, relief protection and cylinders.
- **Operations / IOE monitoring** — throughput, utilization, downtime, cycle time, queue/WIP proxies and derived operating-state classification.
- **Additional engineering domains** — mechanical, thermal and other experimental systems developed under the same measurement/control philosophy.

## Alpha 1.0.3 focus

Alpha 1.0.3 closes several gaps between the earlier design documents and the actual implementation:

- PID **AUTO/MANUAL** operation with external mode/manual-output ports and bumpless Manual→Auto transfer.
- Servo **POSITION/VELOCITY** operation with a centered velocity command, braking and soft-limit accounting.
- Data-bus diagnostics that distinguish **physical driver count** from **distinct driven values**, including same-value multi-driver contention.
- Radio receiver accumulation of valid, undecodable, collision and dropout statistics while keeping payload and link quality separate.
- Pneumatic proportional control, relief/safety behavior and finite actuator components.
- IOE operating-state classification using queue, running state, downtime and instability proxies.

Milestone references:

- [`docs/ALPHA1_0_3_CLOSED_LOOP_DIAGNOSTICS.md`](docs/ALPHA1_0_3_CLOSED_LOOP_DIAGNOSTICS.md) — engineering behavior and port contracts.
- [`docs/ALPHA1_0_3_TEST_MATRIX.md`](docs/ALPHA1_0_3_TEST_MATRIX.md) — repeatable local `runClient` validation.
- [`CHANGELOG.md`](CHANGELOG.md) — milestone history and validation state.
- [`CONTRIBUTING.md`](CONTRIBUTING.md) — engineering and verification rules for changes.

## Reference calculations

These equations describe the engineering conventions implemented by the current code. Minecraft-facing outputs are clamped to the legal redstone range unless a subsystem explicitly uses a richer internal scale.

### 1. Redstone normalization

For a redstone signal `S ∈ {0, …, 15}`:

```text
x = S / 15
```

`x` is a normalized engineering command or measurement in the range `[0, 1]`.

### 2. Signal conditioning

Gain:

```text
S_out = clamp(round(G × S_in), 0, 15)
```

Offset:

```text
S_out = clamp(S_in + b, 0, 15)
```

Threshold:

```text
S_out = S_in,  if S_in >= T
        0,     otherwise
```

Linear range mapping:

```text
t = clamp((S_in - in_min) / (in_max - in_min), 0, 1)
S_out = clamp(round(out_min + t × (out_max - out_min)), 0, 15)
```

### 3. PID reference model

For setpoint `r` and measured process value `y`:

```text
e = r - y
```

The current Alpha 1.0.3 controller applies a one-signal-unit deadband, bounded integral state, filtered derivative, output saturation and anti-windup. Its discrete controller is conceptually:

```text
I_candidate = clamp(I_previous + e, -180, 180)
D_filtered  = low_pass(e - e_previous)
u_raw       = bias + P + I + D
u           = clamp(u_raw, 0, 15)
```

When the controller transfers **Manual → Auto**, the bias is recomputed around the previous manual output to reduce the control-output bump.

### 4. Servo reference model

Position mode uses the back input as a target position on the 0–15 engineering scale.

Velocity mode uses a centered command:

```text
v_command = S_command - 7
```

so `7 = stop`, values below 7 command reverse motion, and values above 7 command forward motion. Applied velocity changes at a bounded rate, the position remains within `0…15`, and attempted travel beyond a soft limit is counted diagnostically.

### 5. Pneumatic reference model

The current pneumatic network uses an internal pressure scale of approximately `0…100` while redstone commands remain `0…15`.

A proportional valve applies:

```text
P_out ≈ (P_in × opening + 7) / 15
```

where `opening ∈ [0, 15]`. The current network also models a nominal pressure loss of one internal pressure unit per traversed network step, regulator limits, one-way flow protection and relief-valve clamping.

### 6. Operations metrics

For a 60-second monitoring window:

```text
utilization = running_ticks / window_ticks
throughput  = completed_cycles / minute
average_WIP = sum(queue_proxy) / sampled_ticks
```

RSE also tracks downtime, cycle-time statistics, starvation (`RUN` with empty queue), blocking/fault proxies (`STOPPED` with queued work), high-queue running, queue variation and run-state transitions.

## Build and development

Requirements:

- JDK 21
- Git
- A NeoForge-compatible development environment

Compile Java:

```bash
./gradlew compileJava --no-daemon --stacktrace
```

Build the mod:

```bash
./gradlew clean build --no-daemon --stacktrace
```

Launch the development client:

```bash
./gradlew runClient
```

Build output is placed under `build/libs/`.

## Verification and CI artifacts

The repository contains static engineering audits under [`tools/`](tools/) and a GitHub Actions workflow at [`.github/workflows/build.yml`](.github/workflows/build.yml).

The automated verification pipeline checks:

1. Repository metadata/version/license/hygiene invariants.
2. RSE static/audit invariants.
3. Alpha milestone-specific feature contracts.
4. Alpha 1.0.3 closed-loop and pneumatic integrity.
5. Java compilation with JDK 21.
6. A clean Gradle build.
7. SHA-256 generation for built JARs.
8. Upload of the verified JAR(s) and `SHA256SUMS.txt` as a short-retention GitHub Actions artifact.

The CI artifact is a **test binary**, not a tagged public release. `runClient` remains a local interactive validation step because the Minecraft client is not launched in the headless CI runner.

## Repository status

The integrity-hardened Alpha 1.0.3 source is merged into `main`, and `main` has passed repository/static verification, Java 21 `compileJava`, and a clean Gradle build in GitHub Actions. The remaining public-release gate is interactive `runClient` validation of the Alpha 1.0.3 critical paths using the documented test matrix.

## License

RSE is released under the **MIT License**. See [`LICENSE`](LICENSE).

## Project links

- Repository: <https://github.com/lord-navy-crypto/redstones-engineering>
- Issues: <https://github.com/lord-navy-crypto/redstones-engineering/issues>
- Actions: <https://github.com/lord-navy-crypto/redstones-engineering/actions>
