# Redstone Systems Engineering (RSE)

[![RSE Build Verification](https://github.com/lord-navy-crypto/redstones-engineering/actions/workflows/build.yml/badge.svg)](https://github.com/lord-navy-crypto/redstones-engineering/actions/workflows/build.yml)

**Redstone Systems Engineering** is a NeoForge engineering-systems mod for Minecraft that extends vanilla redstone without replacing it. RSE keeps the vanilla **0–15 redstone signal as the world-facing engineering boundary** and builds measurement, conditioning, sampling, control, actuation, diagnostics, communications, safety, reliability, and operations tools around it.

> **Engineering path:** Measurement → Conditioning → Sampling → Control → Actuation → Optimization

## Project information

| Item | Current RSE baseline |
| --- | --- |
| Development milestone | **Alpha 1.0.15 — Metrology Rollout & Calibration** |
| Artifact version | `1.0.15-alpha` |
| Minecraft | `1.21.1` |
| NeoForge | `21.1.249` |
| Java | `21` |
| Mod ID | `redstoneengineering` |
| License | **MIT** |

## Alpha 1.0.15 — Metrology Rollout & Calibration

Alpha 1.0.15 promotes the measurement-quality architecture introduced in 1.0.14 from a single-sensor demonstration into shared, multi-domain instrumentation infrastructure.

`MetrologySupport` centralizes scheduled sampling, snapshots, deterministic bounded sensor conditioning, Engineering Port quality projection, and diagnostic formatting. The rollout covers:

- **Engineering Light Sensor** — conditioned repeated light measurements;
- **Entity Density Sensor** — preserves the physical count before clamping so over-range occupancy is explicitly `SATURATED`;
- **Tank Level Sensor** — migrated to shared support while preserving its public measurement snapshot API;
- **Servo Position Sensor** — compares sensor feedback with authoritative servo simulation state;
- **Copper Circuit Meter** — scheduled voltage measurement independent of Jade/HUD polling rate;
- **Pneumatic Flow Meter** — scheduled `0..100` flow-proxy measurement with explicit over-range detection.

The **Calibration Module** is now a real comparison instrument:

```text
LEFT  : REFERENCE INPUT  — known reference
BACK  : OBSERVED INPUT   — instrument under test
FRONT : CALIBRATED OUTPUT
```

Its five historical transfer profiles remain available. The module records corrected reading versus reference so signed residual/bias remains distinct from the **measurement uncertainty proxy**. The uncertainty proxy is diagnostic engineering metadata; it is not formal GUM expanded uncertainty and is not interchangeable with measurement error.

Sampling ownership remains server-side and deterministic. UI, Jade, GeckoLib, rendering, and client polling may observe measurements but never define sampling cadence or authoritative physics state.

See [`ALPHA1_0_15_MANIFEST.txt`](ALPHA1_0_15_MANIFEST.txt) and [`docs/ALPHA1_0_15_METROLOGY_ROLLOUT.md`](docs/ALPHA1_0_15_METROLOGY_ROLLOUT.md).

## Alpha 1.0.14 — Metrology & Uncertainty

Alpha 1.0.14 established `MeasurementSnapshot`, `MeasurementQuality`, `MetrologyTracker`, and `MetrologyStore` plus repeatability, bias, drift, noise, resolution, saturation, sample age, and a measurement uncertainty proxy. High-cardinality diagnostic state remains outside BlockState and the Tank Level Sensor provided the first direct integration.

## Alpha 1.0.13 — Copper Topology + Mechatronics Visualization

Alpha 1.0.13 completed explicit copper electrical topology and the first GeckoLib mechatronics visualization layer. Copper devices expose Engineering Port roles while `CircuitPhysics` and `DomainNetwork` remain authoritative. GeckoLib `4.9.2` visualizes Servo Actuator, Pneumatic Cylinder, and Pneumatic Proportional Valve through a one-way dependency:

```text
Simulation / Physics State → synchronized visual state → GeckoLib animation
```

GeckoLib, renderer FPS, client lifecycle, bones, or animation controllers **never determine physics state**.

## Historical architecture milestones

These historical contracts remain active regression targets and are intentionally named here because newer milestones build on them rather than replacing them.

- **Alpha 1.0.10 — Engineering Port Contract** — introduced `EngineeringPort`, `EngineeringPortSnapshot`, `EngineeringPortProvider`, `PortCompatibility`, `PortQuality`, and the server-backed **Jade Engineering HUD** observer boundary.
- **Alpha 1.0.11 — Legacy Renovation Wave II + Topology GameTests** — migrated cable terminals, redstone/copper junctions, Lapis transducers and cross-domain converters, and promoted executable NeoForge GameTests into CI.
- **Alpha 1.0.12 — Directional I/O Renovation** — established explicit FRONT/BACK redstone endpoints and directional sensor/indicator behavior while retaining the 0..15 boundary.
- **Alpha 1.0.13 — Copper Topology + Mechatronics Visualization** — established axial copper contracts, runtime copper GameTests and the GeckoLib display-only mechatronics boundary.
- **Alpha 1.0.14 — Metrology & Uncertainty** — established reusable measurement quality and uncertainty-proxy infrastructure.
- **Alpha 1.0.15 — Metrology Rollout & Calibration** — applies that infrastructure across multiple engineering domains and adds reference-versus-observed calibration.

## Engineering Port architecture

The **Engineering Port Contract** describes physical side, engineering domain, semantic kind, input/output/bidirectional direction, vanilla compatibility, and unit. `EngineeringPortSnapshot` carries live value/range/quality separately from static topology. `PortQuality` includes `VALID`, `NO_SIGNAL`, `SATURATED`, `STALE`, `FAULT`, `DOMAIN_MISMATCH`, and `TOPOLOGY_ERROR`.

## Required dependencies

Five mature ecosystem libraries are part of the RSE platform contract:

| Dependency | Pinned development version | Required side | RSE purpose |
| --- | --- | --- | --- |
| JEI | `19.27.0.336` | Client | recipe/use browsing and engineering progression |
| Jade | `15.10.6` | Client + Server | engineering HUD and server-backed port diagnostics |
| GeckoLib | `4.9.2` | Client + Server | articulated machine visualization |
| Cloth Config | `15.0.140` | Client | configuration and tuning UI |
| Fusion | `1.3.14` (`1.3.14-neoforge-mc1.21.1`) | Client | connected textures, advanced models, topology-aware visuals |

RSE does not shade or bundle their jars. Physics, topology, measurement, control, reliability and operations behavior remain native RSE responsibilities. See [`docs/DEPENDENCY_POLICY.md`](docs/DEPENDENCY_POLICY.md).

## Transmission-domain rule

RSE deliberately does **not** turn every wire into a universal cable:

- `INSULATED_REDSTONE` — Minecraft-compatible bounded `0..15` transport;
- `COPPER` — simplified electrical/voltage network;
- `LAPIS_PRECISION` — precision continuous-like signal domain;
- `QUARTZ_TIMING` — timing/clock domain;
- `INSTRUMENT_BUS` — measurement-channel transport;
- pneumatic, optical, magnetic, resonance and thermal domains remain explicit.

Cross-domain transfer happens through documented terminals, transducers, scalers, emitters/receivers or converters.

## Engineering systems represented

### Engineering Physics
Measurement, calibration, repeatability, bias, drift, noise, resolution, uncertainty proxies, electromagnetism, thermal/optical experiments, resonators, and model-versus-measurement reasoning.

### Electrical & Computer Engineering
Analog conditioning, sampling, timing, oscillators, PWM, buses, serial/differential links, radio, instrumentation, digital diagnostics, watchdogs and control implementation.

### Mechanical / Mechatronics
Servo motion, feedback, articulated visualization, pneumatic pressure networks, regulators, valves, safety relief, cylinders, vibration and damping.

### Industrial & Operations Engineering
Throughput, utilization, cycle time, downtime, queue/WIP proxies, operating-state classification, fault handling, redundancy and system reliability.

## Survival engineering progression

Recipes follow engineering dependency rather than raw rarity alone:

1. **Basic Measurement** — probes, references, analyzers, sensors;
2. **Signal Processing & Data Acquisition** — conditioners, filters, sample-and-hold, scopes;
3. **Control & Mechatronics** — PID, PWM, servo, pneumatic control;
4. **System Safety & Reliability** — watchdog, voter, fault latch, relief protection;
5. **Operations & Integrated Systems** — communications, multi-sensor networks, production monitoring.

See [`docs/CRAFTING_PROGRESSION.md`](docs/CRAFTING_PROGRESSION.md).

## Design principles

1. Preserve the vanilla **0–15** world-facing boundary.
2. Measure before controlling.
3. Make ports and transmission domains explicit.
4. Separate measurement from intervention.
5. Give configuration physical meaning.
6. Model useful non-ideal behavior.
7. Keep high-cardinality runtime data out of BlockState.
8. Keep automation inspectable.
9. Treat regression gates as part of the product.
10. Use mature dependencies for infrastructure while keeping physics/control/topology authoritative in RSE.
11. Prefer executable Minecraft behavior tests for contracts that source-only verification cannot prove.
12. Treat rendering and UI as downstream observers.
13. Keep instrument sample cadence owned by simulation, never by HUD/render polling.

## Reference calculations

```text
Redstone: x = S / 15,  S ∈ {0, …, 15}
Copper divider: Vout = Vin × Rload / (Rseries + Rload)
Metrology residual: residual = reading - reference
Bias: mean(residual)
Uncertainty proxy ≈ RSS(repeatability, noise, drift, bias, quantization)
PID: e = setpoint - process
```

## Verification architecture

CI runs verifier syntax, repository/source/resource audits, deterministic reference models, historical Alpha regressions, dependency checks, Engineering Port/Jade gates, legacy-renovation checks, directional-I/O guards, copper topology guards, **Alpha 1.0.14 metrology**, **Alpha 1.0.15 multi-domain rollout/calibration verification**, Java 21 compilation, Gradle tests, **NeoForge Minecraft GameTests**, a clean build, SHA-256 generation and verified artifact upload.

Interactive visual/UX behavior remains a separate `runClient` gate. Automated gates protect simulation-to-render ownership, metrology math, physical topology, directional I/O, copper runtime propagation, calibration semantics and sampling ownership.

## Build and test

```bash
./gradlew compileJava --no-daemon --stacktrace
./gradlew test --no-daemon --stacktrace
./gradlew runGameTestServer --no-daemon --stacktrace
./gradlew clean build --no-daemon --stacktrace
./gradlew runClient
```

Build output is under `build/libs/`.

## Documentation

- [`CHANGELOG.md`](CHANGELOG.md)
- [`ALPHA1_0_15_MANIFEST.txt`](ALPHA1_0_15_MANIFEST.txt)
- [`ALPHA1_0_14_MANIFEST.txt`](ALPHA1_0_14_MANIFEST.txt)
- [`ALPHA1_0_13_MANIFEST.txt`](ALPHA1_0_13_MANIFEST.txt)
- [`docs/ALPHA1_0_15_METROLOGY_ROLLOUT.md`](docs/ALPHA1_0_15_METROLOGY_ROLLOUT.md)
- [`docs/DEPENDENCY_POLICY.md`](docs/DEPENDENCY_POLICY.md)
- [`docs/ALPHA1_0_13_COPPER_TOPOLOGY_RENOVATION.md`](docs/ALPHA1_0_13_COPPER_TOPOLOGY_RENOVATION.md)
- [`docs/ALPHA1_0_12_DIRECTIONAL_IO_RENOVATION.md`](docs/ALPHA1_0_12_DIRECTIONAL_IO_RENOVATION.md)
- [`docs/ALPHA1_0_11_LEGACY_RENOVATION_AND_GAMETEST.md`](docs/ALPHA1_0_11_LEGACY_RENOVATION_AND_GAMETEST.md)
- [`docs/ALPHA1_0_10_ENGINEERING_PORT_ARCHITECTURE.md`](docs/ALPHA1_0_10_ENGINEERING_PORT_ARCHITECTURE.md)
- [`docs/ALPHA1_0_10_JADE_ENGINEERING_HUD.md`](docs/ALPHA1_0_10_JADE_ENGINEERING_HUD.md)
- [`docs/ENGINEERING_LANGUAGE_AND_CURRICULUM.md`](docs/ENGINEERING_LANGUAGE_AND_CURRICULUM.md)
- [`docs/CRAFTING_PROGRESSION.md`](docs/CRAFTING_PROGRESSION.md)
- [`CONTRIBUTING.md`](CONTRIBUTING.md)

## License

RSE is released under the **MIT License**. See [`LICENSE`](LICENSE).
