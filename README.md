# Redstone Systems Engineering (RSE)

[![RSE Build Verification](https://github.com/lord-navy-crypto/redstones-engineering/actions/workflows/build.yml/badge.svg)](https://github.com/lord-navy-crypto/redstones-engineering/actions/workflows/build.yml)

**Redstone Systems Engineering** is a NeoForge engineering-systems mod for Minecraft that extends vanilla redstone without replacing it. RSE keeps the vanilla **0–15 redstone signal as the world-facing engineering boundary** and builds measurement, conditioning, sampling, control, actuation, diagnostics, communications, safety, and operations tools around it.

> **Engineering path:** Measurement → Conditioning → Sampling → Control → Actuation → Optimization

## Project information

| Item | Current RSE baseline |
| --- | --- |
| Development milestone | **Alpha 1.0.10 — Engineering Port Architecture** |
| Artifact version | `1.0.10-alpha` |
| Minecraft | `1.21.1` |
| NeoForge | `21.1.249` |
| Java | `21` |
| Mod ID | `redstoneengineering` |
| License | **MIT** |

## Alpha 1.0.10 — Engineering Port Contract

The first large post-dependency renovation replaces scattered per-block port descriptions with a shared core contract:

- `EngineeringPort` — physical side, engineering domain, semantic kind, flow direction, vanilla compatibility and unit;
- `EngineeringPortSnapshot` — dynamic value/range/quality kept outside BlockState;
- `EngineeringPortProvider` — one API for blocks to expose descriptors and observations;
- `PortCompatibility` — centralized direct domain/direction compatibility;
- `PortQuality` — `VALID`, `NO_SIGNAL`, `SATURATED`, `STALE`, `FAULT`, `DOMAIN_MISMATCH`, `TOPOLOGY_ERROR`.

The first migration wave covers the shared `DirectionalSignalBlock` family, Engineering Light Sensor, Entity Density Sensor, Tank Level Sensor, Analog Process Indicator, Insulated Redstone Cable and Instrument Bus. This lets later Jade/Fusion/Cloth/GeckoLib/JEI integration read one authoritative engineering model instead of reimplementing block logic.

See [`docs/ALPHA1_0_10_ENGINEERING_PORT_ARCHITECTURE.md`](docs/ALPHA1_0_10_ENGINEERING_PORT_ARCHITECTURE.md).

## Required dependencies

Five mature ecosystem libraries are part of the RSE platform contract:

| Dependency | Pinned development version | Required side | RSE purpose |
| --- | --- | --- | --- |
| JEI | `19.27.0.336` | Client | recipe/use browsing, engineering progression and future machine/recipe information |
| Jade | `15.10.6` | Client + Server | engineering HUD, port/domain/state diagnostics and server-backed data providers |
| GeckoLib | `4.9.2` | Client + Server | articulated servo, cylinder, valve and machine animation architecture |
| Cloth Config | `15.0.140` | Client | maintainable configuration screens and tuning UI |
| Fusion | `1.3.14` (`1.3.14-neoforge-mc1.21.1` Maven artifact) | Client | connected textures, advanced models and topology-aware engineering visuals |

NeoForge metadata declares these dependencies with `type="required"`. RSE does not shade or bundle their jars.

See [`docs/DEPENDENCY_POLICY.md`](docs/DEPENDENCY_POLICY.md).

## Native engineering core

Dependencies provide UI, diagnostics, animation and rendering infrastructure. RSE still owns:

- physics, measurement and signal models;
- explicit engineering ports and transmission domains;
- topology and network behavior;
- sampling and feedback control;
- pneumatics and mechatronics;
- reliability, safety and operations diagnostics.

The dependency direction is one-way: integrations read RSE state; they never define physical connectivity or control behavior.

## Transmission-domain rule

RSE deliberately does **not** turn every wire into a universal cable. Different engineering media remain separate:

- `INSULATED_REDSTONE` — Minecraft-compatible bounded `0..15` transport;
- `COPPER` — simplified electrical/voltage network;
- `LAPIS_PRECISION` — precision continuous-like signal domain;
- `QUARTZ_TIMING` — clock/timing domain;
- `INSTRUMENT_BUS` — measurement-channel transport;
- pneumatic, optical, magnetic, resonance and thermal domains remain explicit.

Cross-domain conversion happens through documented terminals, transducers, scalers, emitters/receivers or converters.

## Engineering systems represented

### Engineering Physics
Measurement, calibration, uncertainty proxies, electromagnetism, thermal/optical experiments, resonators, and model-versus-measurement reasoning.

### Electrical & Computer Engineering
Analog conditioning, sampling, timing, oscillators, PWM, buses, serial/differential links, radio, instrumentation, digital diagnostics, watchdogs and control implementation.

### Mechanical / Mechatronics
Servo motion, position feedback, pneumatic pressure networks, regulators, valves, relief protection, cylinders, vibration and damping.

### Industrial & Operations Engineering
Throughput, utilization, cycle time, downtime, queue/WIP proxies, operating-state classification, fault handling and system reliability.

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
10. Use mature dependencies where they remove duplicate infrastructure, while keeping physics/control/topology authoritative in RSE.

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

CI runs verifier syntax, repository/source/resource audits, deterministic reference models, historical Alpha regressions, required-dependency checks, the Alpha 1.0.10 engineering-port architecture gate, Java 21 compilation, Gradle tests, a clean build, SHA-256 generation and verified artifact upload.

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
- [`docs/ALPHA1_0_10_ENGINEERING_PORT_ARCHITECTURE.md`](docs/ALPHA1_0_10_ENGINEERING_PORT_ARCHITECTURE.md)
- [`docs/ENGINEERING_LANGUAGE_AND_CURRICULUM.md`](docs/ENGINEERING_LANGUAGE_AND_CURRICULUM.md)
- [`docs/CRAFTING_PROGRESSION.md`](docs/CRAFTING_PROGRESSION.md)
- [`docs/ALPHA1_0_7_LEGACY_WIRING_PORTS.md`](docs/ALPHA1_0_7_LEGACY_WIRING_PORTS.md)

## License

RSE is released under the **MIT License**. See [`LICENSE`](LICENSE).
