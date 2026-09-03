# Redstone Systems Engineering (RSE)

[![RSE Build Verification](https://github.com/lord-navy-crypto/redstones-engineering/actions/workflows/build.yml/badge.svg)](https://github.com/lord-navy-crypto/redstones-engineering/actions/workflows/build.yml)

**Redstone Systems Engineering** is a NeoForge engineering-systems mod for Minecraft that extends vanilla redstone without replacing it. RSE keeps the vanilla **0–15 redstone signal as the world-facing engineering boundary** and builds measurement, conditioning, sampling, control, actuation, diagnostics, communications, safety, and operations tools around it.

> **Engineering path:** Measurement → Conditioning → Sampling → Control → Actuation → Optimization

## Project information

| Item | Current RSE baseline |
| --- | --- |
| Development milestone | **Alpha 1.0.8 — Dependency & Interoperability Foundation** |
| Artifact version | `1.0.8-alpha` |
| Minecraft | `1.21.1` |
| NeoForge | `21.1.249` |
| Java | `21` |
| Mod ID | `redstoneengineering` |
| License | **MIT** |
| Required mod dependency | **NeoForge only** |
| Optional dev/runtime integrations | **JEI + Jade** |

Alpha 1.0.8 establishes a controlled dependency policy: mature ecosystem APIs may be used when they clearly improve RSE, while the engineering simulation remains launchable without optional quality-of-life mods.

## Alpha 1.0.8 — dependency foundation

### Built into the RSE core

RSE continues to use NeoForge-native facilities for the parts that belong to the engineering architecture itself:

- sided/block capability infrastructure for future interoperable engineering ports;
- GameTest run infrastructure for topology and in-game behavior tests;
- data generation, configuration, registries, networking, and normal NeoForge lifecycle facilities;
- RSE transmission domains, measurement models, port diagnostics, control logic, pneumatics, and operations models.

### Optional integrations available to development

**JEI 19.27.0.336** is now available through the official JEI Maven as compile-only APIs plus a full local runtime mod. This prepares recipe/use browsing and future RSE engineering recipe information without making JEI mandatory.

**Jade 15.10.6+neoforge** for Minecraft 1.21.1 is now available as compile-only + local-runtime integration. This prepares an optional engineering HUD for port direction, transmission domain, measurements, and diagnostics without making Jade mandatory.

RSE also includes a centralized optional-integration detector so later compatibility code can safely distinguish `AVAILABLE` from `ABSENT` integrations without contaminating core simulation code.

See [`docs/DEPENDENCY_POLICY.md`](docs/DEPENDENCY_POLICY.md).

### Not added merely as empty dependencies

GeckoLib, Ponder, Fusion, YACL and similar libraries remain candidates. They will be added only together with the feature that materially benefits from them—for example articulated actuator animation, interactive engineering tutorials, or connected visual materials.

## Alpha 1.0.7 — legacy wiring renovation

- **Instrument Cable** is a six-direction `ConnectedCableBlock` multi-drop measurement bus rather than a plain adjacency-scanned block.
- **InstrumentNetwork** traverses only real connected cable edges.
- **Signal Probe** keeps its sensing face pointed at the measured target; its opposite/back face is the instrumentation-bus connection.
- **Insulated Redstone, Copper, Lapis and Quartz** report exact connected directions.
- Touching incompatible media remains electrically isolated and reports `DOMAIN_MISMATCH` instead of implying a valid connection.
- **Redstone Cable Terminal** reports which physical face is Vanilla redstone, which is insulated cable, and the current logical signal direction.
- Vanilla-facing redstone remains bounded to **0..15**.

See [`docs/ALPHA1_0_7_LEGACY_WIRING_PORTS.md`](docs/ALPHA1_0_7_LEGACY_WIRING_PORTS.md) and [`docs/ALPHA1_0_7_TEST_MATRIX.md`](docs/ALPHA1_0_7_TEST_MATRIX.md).

## Transmission-domain rule

RSE deliberately does **not** turn every wire into a universal cable. Different engineering media remain separate:

- `INSULATED_REDSTONE` — Minecraft-compatible bounded 0..15 signal transport;
- `COPPER` — simplified electrical/voltage network;
- `LAPIS_PRECISION` — precision continuous-like signal domain;
- `QUARTZ_TIMING` — clock/timing domain;
- `INSTRUMENT_BUS` — measurement-channel transport;
- optical and resonance domains remain separate as well.

Cross-domain conversion happens through explicit terminals, transducers, scalers, emitters/receivers, or other documented interfaces.

## Engineering language and progression

Representative engineering translations include:

- **Instrumentation Signal Analyzer** — measurement / metrology;
- **4-Channel Engineering Oscilloscope** — waveform capture;
- **Analog Signal Conditioner** — gain/offset/threshold conditioning;
- **Instrument Calibration Reference** — metrology reference;
- **Sample-and-Hold Module** — data acquisition;
- **4-Channel Logic Analyzer** — digital timing diagnostics;
- **Discrete PID Controller** — feedback control;
- **Position/Velocity Servo Actuator** — mechatronic actuation;
- **Fault Watchdog Timer / Latched Fault Memory / Redundant Signal Voter** — reliability and safety;
- **Pneumatic Proportional Control Valve / Pneumatic Safety Relief Valve / Pneumatic Linear Actuator** — fluid-power control;
- **Production Operations Monitor** — industrial and operations engineering.

The full discipline map is in [`docs/ENGINEERING_LANGUAGE_AND_CURRICULUM.md`](docs/ENGINEERING_LANGUAGE_AND_CURRICULUM.md). Crafting follows engineering dependency from basic measurement through signal processing, control, safety/reliability and integrated operations; see [`docs/CRAFTING_PROGRESSION.md`](docs/CRAFTING_PROGRESSION.md).

## Engineering systems represented

### Engineering Physics
Measurement, calibration, uncertainty proxies, electromagnetism, thermal/optical experiments, resonators, and model-versus-measurement reasoning.

### Electrical & Computer Engineering
Analog conditioning, sampling, timing, oscillators, PWM, buses, serial/differential links, radio, instrumentation, digital diagnostics, watchdogs and control implementation.

### Mechanical / Mechatronics
Servo motion, position feedback, pneumatic pressure networks, regulators, valves, relief protection, cylinders, vibration and damping.

### Industrial & Operations Engineering
Throughput, utilization, cycle time, downtime, queue/WIP proxies, operating-state classification, fault handling and system reliability.

## School-angle development

- **Michigan-style Engineering Physics + ECE + ME + IOE:** physical modeling → measurement → feedback → actuation → operations.
- **UIUC-style ECE depth:** signals, circuits, timing, data acquisition, communications and control implementation.
- **UW–Madison-style Engineering Physics / experimental systems:** calibration, uncertainty, instrument response and model-vs-data comparison.
- **CMU-style ECE / robotics systems:** sensor fusion, fault-aware autonomy, actuator feedback, distributed communication and diagnostics.

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
10. Use mature dependencies when they solve a real problem, but keep optional integrations optional.

## Reference calculations

Redstone normalization:

```text
S ∈ {0, …, 15}
x = S / 15
```

Analyzer calibration remains display-only:

```text
S_display = clamp(S_raw + offset, 0, 15)
INLINE OUT = S_raw
```

PID reference form:

```text
e = setpoint - process
I_candidate = clamp(I_previous + e, -180, 180)
u = clamp(bias + P + I + D, 0, 15)
```

Pneumatic proportional control uses an internal pressure scale while commands remain 0..15:

```text
P_out ≈ (P_in × opening + 7) / 15
```

## Verification architecture

CI runs verifier syntax, repository/source/resource audits, deterministic reference models, historical Alpha regressions, dependency-policy checks, Java 21 `compileJava`, Gradle `test`, a clean build, SHA-256 generation, and verified artifact upload. `compileJava` also proves that the pinned optional integration artifacts can be resolved from their configured repositories.

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
- [`docs/DEPENDENCY_POLICY.md`](docs/DEPENDENCY_POLICY.md)
- [`docs/ENGINEERING_LANGUAGE_AND_CURRICULUM.md`](docs/ENGINEERING_LANGUAGE_AND_CURRICULUM.md)
- [`docs/CRAFTING_PROGRESSION.md`](docs/CRAFTING_PROGRESSION.md)
- [`docs/ALPHA1_0_7_LEGACY_WIRING_PORTS.md`](docs/ALPHA1_0_7_LEGACY_WIRING_PORTS.md)
- [`docs/ALPHA1_0_7_TEST_MATRIX.md`](docs/ALPHA1_0_7_TEST_MATRIX.md)

## License

RSE is released under the **MIT License**. See [`LICENSE`](LICENSE).
