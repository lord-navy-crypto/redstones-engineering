# Redstone Systems Engineering (RSE)

[![RSE Build Verification](https://github.com/lord-navy-crypto/redstones-engineering/actions/workflows/build.yml/badge.svg)](https://github.com/lord-navy-crypto/redstones-engineering/actions/workflows/build.yml)

**Redstone Systems Engineering** is a NeoForge engineering-systems mod for Minecraft that extends vanilla redstone without replacing it.

RSE keeps the vanilla **0–15 redstone signal as the world-facing engineering boundary** and builds measurement, conditioning, sampling, control, actuation, diagnostics, communications, safety, and operations tools around it.

> **Engineering path:** Measurement → Conditioning → Sampling → Control → Actuation → Optimization

## Project information

| Item | Current RSE baseline |
| --- | --- |
| Development milestone | **Alpha 1.0.6 — Engineering Language & Progression** |
| Artifact version | `1.0.6-alpha` |
| Minecraft | `1.21.1` |
| NeoForge | `21.1.249` |
| Java | `21` |
| Mod ID | `redstoneengineering` |
| License | **MIT** |

Alpha 1.0.6 translates the core block set into more precise engineering vocabulary, introduces a tiered crafting progression, begins a coherent engineering visual language, and adds event-driven safety feedback for pneumatic relief.

## Engineering language

Representative in-game translations now include:

- **Instrumentation Signal Analyzer** — measurement / metrology;
- **4-Channel Engineering Oscilloscope** — waveform capture;
- **Analog Signal Conditioner** — gain/offset/threshold conditioning;
- **Instrument Calibration Reference** — metrology reference;
- **Sample-and-Hold Module** — data acquisition;
- **4-Channel Logic Analyzer** — digital timing diagnostics;
- **Discrete PID Controller** — feedback control;
- **Position/Velocity Servo Actuator** — mechatronic actuation;
- **Position Feedback Transducer** — feedback sensing;
- **Fault Watchdog Timer / Latched Fault Memory / Redundant Signal Voter** — reliability and safety;
- **Pneumatic Proportional Control Valve / Pneumatic Safety Relief Valve / Pneumatic Linear Actuator** — fluid-power control;
- **Production Operations Monitor** — industrial and operations engineering.

The full translation and discipline map is in [`docs/ENGINEERING_LANGUAGE_AND_CURRICULUM.md`](docs/ENGINEERING_LANGUAGE_AND_CURRICULUM.md).

## Crafting progression

Recipes now follow engineering dependency more strongly than raw rarity:

1. **Tier 1 — Basic Measurement**: probes, instrumentation cable, Signal Analyzer.
2. **Tier 2 — Signal Processing & Data Acquisition**: conditioner, calibration, oscilloscope, logic analyzer.
3. **Tier 3 — Control & Mechatronics**: PID, PWM, servo, proportional pneumatic control.
4. **Tier 4 — Safety & Reliability**: watchdog, redundancy, fault memory, relief protection.
5. **Tier 5 — Integrated Operations**: Operations Monitor and system-level networks.

Examples:

- Oscilloscope and Logic Analyzer require a Signal Analyzer.
- PID requires a Signal Conditioner and comparator logic.
- Pneumatic Proportional Control Valve requires a Pneumatic Isolation Valve plus comparator control.
- Production Operations Monitor requires a Logic Analyzer, clock and observer.

See [`docs/CRAFTING_PROGRESSION.md`](docs/CRAFTING_PROGRESSION.md).

## Visual and feedback language

Alpha 1.0.6 starts a consistent visual hierarchy:

- instrumentation: dark chassis, cyan measurement screen/grid, amber calibration/status accents;
- calibration: dial/scale visual language;
- safety: metal body + red safety marking + visible pressure indication;
- pneumatic relief: cloud particles appear **only when excess pressure is actually vented**, not as constant decoration.

The first refreshed textures cover the Signal Analyzer front panel, Calibration Module front panel, and Pneumatic Safety Relief Valve.

## Engineering systems already represented

### Engineering Physics
Measurement, calibration, uncertainty proxies, electromagnetism, thermal/optical experiments, resonators, and model-versus-measurement reasoning.

### Electrical & Computer Engineering
Analog conditioning, sampling, timing, oscillators, PWM, buses, serial/differential links, radio, instrumentation, digital diagnostics, watchdogs and control implementation.

### Mechanical / Mechatronics
Servo motion, position feedback, pneumatic pressure networks, regulators, valves, relief protection, cylinders, vibration and damping.

### Industrial & Operations Engineering
Throughput, utilization, cycle time, downtime, queue/WIP proxies, operating-state classification, fault handling and system reliability.

## School-angle development

RSE remains one coherent system, but different academic perspectives guide depth:

- **Michigan-style Engineering Physics + ECE + ME + IOE:** physical modeling → measurement → feedback → actuation → operations.
- **UIUC-style ECE depth:** signals, circuits, timing, data acquisition, communications and control implementation.
- **UW–Madison-style Engineering Physics / experimental systems:** calibration, uncertainty, instrument response, field/thermal/optical measurement and model-vs-data comparison.
- **CMU-style ECE / robotics systems:** sensor fusion, fault-aware autonomy, actuator feedback, distributed communication and system diagnostics.

Recommended next additions are uncertainty/metrology, fault injection, sensor fusion/state estimation, system identification, reliability dashboards, and energy/efficiency accounting.

## Design principles

1. Preserve the vanilla **0–15** boundary.
2. Measure before controlling.
3. Make ports explicit.
4. Separate measurement from intervention.
5. Give configuration physical meaning.
6. Model useful non-ideal behavior.
7. Keep high-cardinality runtime data out of BlockState.
8. Keep automation inspectable.
9. Treat regression gates as part of the product.

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
INLINE OUT = S_raw
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

`7 = stop`, lower values command reverse motion, and higher values command forward motion.

### Pneumatic proportional control

The pneumatic domain uses an internal pressure scale of approximately `0..100`, while redstone commands remain `0..15`:

```text
P_out ≈ (P_in × opening + 7) / 15
```

### Operations metrics

```text
utilization = running_ticks / window_ticks
throughput  = completed_cycles / minute
average_WIP = sum(queue_proxy) / sampled_ticks
```

## Verification architecture

CI runs:

1. Python verifier syntax;
2. repository and source/resource audits;
3. deterministic reference-model tests;
4. historical Alpha regression verifiers;
5. current milestone verifier;
6. Java 21 `compileJava`;
7. Gradle `test`;
8. clean Gradle build;
9. SHA-256 generation and verified artifact upload.

Interactive Minecraft behavior remains a separate `runClient` gate.

## Build

```bash
./gradlew compileJava --no-daemon --stacktrace
./gradlew test --no-daemon --stacktrace
./gradlew clean build --no-daemon --stacktrace
./gradlew runClient
```

Build output is under `build/libs/`.

## Documentation

- [`CHANGELOG.md`](CHANGELOG.md)
- [`CONTRIBUTING.md`](CONTRIBUTING.md)
- [`docs/ENGINEERING_LANGUAGE_AND_CURRICULUM.md`](docs/ENGINEERING_LANGUAGE_AND_CURRICULUM.md)
- [`docs/CRAFTING_PROGRESSION.md`](docs/CRAFTING_PROGRESSION.md)
- [`docs/ALPHA1_0_5_QUALITY_CALIBRATION.md`](docs/ALPHA1_0_5_QUALITY_CALIBRATION.md)
- [`docs/ALPHA1_0_5_TEST_MATRIX.md`](docs/ALPHA1_0_5_TEST_MATRIX.md)

## License

RSE is released under the **MIT License**. See [`LICENSE`](LICENSE).
