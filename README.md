# Redstone Systems Engineering (RSE)

[![RSE Build Verification](https://github.com/lord-navy-crypto/redstones-engineering/actions/workflows/build.yml/badge.svg)](https://github.com/lord-navy-crypto/redstones-engineering/actions/workflows/build.yml)

**Redstone Systems Engineering** is a NeoForge engineering-systems mod for Minecraft that extends vanilla redstone without replacing it. RSE keeps the vanilla **0–15 redstone signal as the world-facing engineering boundary** and builds measurement, conditioning, sampling, control, actuation, diagnostics, communications, safety, reliability, and operations tools around it.

> **Engineering path:** Measurement → Conditioning → Sampling → Control → Actuation → Optimization → Acceptance → Evidence

## Project information

| Item | Current RSE baseline |
| --- | --- |
| Development milestone | **Alpha 1.0.19 — Acceptance UX & Evidence Presentation** |
| Artifact version | `1.0.19-alpha` |
| Minecraft | `1.21.1` |
| NeoForge | `21.1.249` |
| Java | `21` |
| Mod ID | `redstoneengineering` |
| License | **MIT** |

## Alpha 1.0.19 — Acceptance UX & Evidence Presentation

Alpha 1.0.19 makes the engineering acceptance contract visible during normal play without turning Jade into a second controller or diagnostic engine. When the player inspects a **PID Controller**, the server-backed Jade provider combines the authoritative all-face topology snapshot, the read-only closed-loop commissioning snapshot, and the Alpha 1.0.18 acceptance evaluator.

The default HUD is intentionally compact:

```text
Acceptance: PASS | commissioning=PASS 94/100
```

When acceptance evidence contains an issue, one additional evidence line is shown, for example:

```text
Evidence: TOPOLOGY_MISMATCH - 1 domain/direction topology mismatch(es)
```

The server payload also retains structured acceptance status, commissioning status/score, issue count, first issue code/detail, and the deterministic `traceKey()`. That richer evidence is available for regression tests and future advanced diagnostics without forcing it into the normal HUD. Ordinary sensors, cables, converters, and instruments continue to show their existing targeted-face and topology diagnostics and do not receive irrelevant `NOT_READY` acceptance messages.

`EngineeringAcceptancePresentation` formats immutable evidence only. Jade never writes controller state, network state, sampling cadence, BlockState, or machine physics. Vanilla-compatible world-facing redstone remains **0..15**.

See [`ALPHA1_0_19_MANIFEST.txt`](ALPHA1_0_19_MANIFEST.txt).

## Alpha 1.0.18 — Engineering Acceptance & Traceability

Alpha 1.0.18 closes the loop between **what RSE measures**, **what its topology says is physically connected**, and **whether a controlled system has actually been commissioned successfully**. The `EngineeringAcceptance` layer consumes the immutable topology projection introduced in 1.0.17 and the closed-loop commissioning snapshot introduced in 1.0.16, then produces one deterministic engineering verdict:

```text
NOT_READY | PASS | MARGINAL | FAIL
```

Acceptance deliberately does not become another simulator or controller. It does not inspect the world directly, create runtime state, solve networks, schedule ticks, modify BlockState, or retune PID physics. It only combines already-authoritative evidence.

A known Engineering Port `DOMAIN_MISMATCH` or `DIRECTION_MISMATCH` is a structural acceptance failure even when commissioning otherwise passes. By contrast, `OPEN`, `ISOLATED`, and `UNLOADED` remain observations rather than automatic failures, preserving the topology semantics established in 1.0.17. Healthy topology plus unavailable, idle, or still-running commissioning evidence is `NOT_READY`; established commissioning `MARGINAL` and `FAIL` states retain their original severity.

`EngineeringAcceptanceSnapshot` records topology counts, commissioning state and score, a final verdict, immutable issue records, and a deterministic `traceKey()`. Stable issue codes (`TOPOLOGY_MISMATCH`, `COMMISSIONING_NOT_READY`, `COMMISSIONING_MARGINAL`, `COMMISSIONING_FAIL`) make results suitable for regression tests, logs, and read-only HUD surfaces without granting those surfaces authority over simulation.

See [`ALPHA1_0_18_MANIFEST.txt`](ALPHA1_0_18_MANIFEST.txt).

## Alpha 1.0.17 — Engineering UX & Topology Visualization

Alpha 1.0.17 makes the Engineering Port architecture legible in normal play without creating a second simulation model. `EngineeringTopologyView` projects the authoritative `EngineeringPortProvider`, live `EngineeringPortSnapshot`, and existing `PortCompatibility` rules into immutable all-face diagnostics.

Every physical face is classified as `CONNECTED`, `OPEN`, `ISOLATED`, `DOMAIN_MISMATCH`, `DIRECTION_MISMATCH`, or `UNLOADED`. The projection records the port domain, semantic kind, input/output direction, neighbor identity, live measurement quality when available, and compact issue counts. It does **not** solve networks, schedule ticks, mutate BlockState, write runtime stores, or drive machine physics.

The server-backed **Jade Engineering HUD** keeps its targeted-face detail and adds an all-face topology summary. A player can inspect whether FRONT is a control output, BACK is a sensor input, a side is isolated, or a neighboring interface is wrong-domain/wrong-direction without guessing from block orientation alone.

See [`ALPHA1_0_17_MANIFEST.txt`](ALPHA1_0_17_MANIFEST.txt).

## Alpha 1.0.16 — Closed-Loop Commissioning & Fault Injection

Alpha 1.0.16 turns the existing PID step-response counters into a stable, read-only commissioning contract and connects laboratory disturbance devices to an explicit fault-injection workflow. Commissioning exposes setpoint, process value, control output, error, rise/settling time, overshoot, saturation events, mode state and a bounded score. Baseline-versus-disturbed comparison makes controller tuning testable under controlled non-ideal conditions.

- **Lapis Noise Source → NOISE fault injection** — deterministic bounded disturbance;
- **Quartz Phase Delay → LATENCY fault injection** — bounded rising-edge delay;
- bias, dropout and actuator-saturation primitives remain explicit engineering test conditions.

See [`ALPHA1_0_16_MANIFEST.txt`](ALPHA1_0_16_MANIFEST.txt).

## Alpha 1.0.15 — Metrology Rollout & Calibration

Alpha 1.0.15 promotes measurement quality into shared multi-domain infrastructure. `MetrologySupport` centralizes scheduled sampling, snapshots, deterministic bounded sensor conditioning, Engineering Port quality projection, and diagnostic formatting across light, entity density, tank level, servo position, copper circuit and pneumatic flow measurements.

The **Calibration Module** compares a known reference against an observed instrument channel while preserving signed residual/bias separately from the measurement uncertainty proxy. Sampling ownership remains server-side and deterministic.

See [`ALPHA1_0_15_MANIFEST.txt`](ALPHA1_0_15_MANIFEST.txt) and [`docs/ALPHA1_0_15_METROLOGY_ROLLOUT.md`](docs/ALPHA1_0_15_METROLOGY_ROLLOUT.md).

## Alpha 1.0.14 — Metrology & Uncertainty

Alpha 1.0.14 established `MeasurementSnapshot`, `MeasurementQuality`, `MetrologyTracker`, and `MetrologyStore` plus repeatability, bias, drift, noise, resolution, saturation, sample age, and a measurement uncertainty proxy.

## Alpha 1.0.13 — Copper Topology + Mechatronics Visualization

Alpha 1.0.13 completed explicit copper electrical topology and the first GeckoLib mechatronics visualization layer. Copper devices expose Engineering Port roles while `CircuitPhysics` and `DomainNetwork` remain authoritative. GeckoLib visualizes Servo Actuator, Pneumatic Cylinder, and Pneumatic Proportional Valve through a one-way dependency:

```text
Simulation / Physics State → synchronized visual state → GeckoLib animation
```

## Historical architecture milestones

These historical contracts remain active regression targets and newer milestones build on them rather than replacing them.

- **Alpha 1.0.10 — Engineering Port Contract** — introduced descriptors, runtime snapshots, compatibility, quality and the server-backed Jade observer boundary.
- **Alpha 1.0.11 — Legacy Renovation + Topology GameTests** — migrated legacy topology and promoted executable NeoForge GameTests into CI.
- **Alpha 1.0.12 — Directional I/O Renovation** — established explicit FRONT/BACK redstone endpoints and directional sensor/indicator behavior.
- **Alpha 1.0.13 — Copper Topology + Mechatronics Visualization** — established axial copper contracts and display-only GeckoLib visualization.
- **Alpha 1.0.14 — Metrology & Uncertainty** — established reusable measurement-quality infrastructure.
- **Alpha 1.0.15 — Metrology Rollout & Calibration** — expanded metrology across domains and added reference-versus-observed calibration.
- **Alpha 1.0.16 — Closed-Loop Commissioning & Fault Injection** — added stable commissioning evidence and typed disturbance testing.
- **Alpha 1.0.17 — Engineering UX & Topology Visualization** — exposed all-face port/link/quality diagnostics in Jade.
- **Alpha 1.0.18 — Engineering Acceptance & Traceability** — combined topology and commissioning evidence into deterministic verdicts.
- **Alpha 1.0.19 — Acceptance UX & Evidence Presentation** — exposes PID acceptance evidence through concise server-backed Jade diagnostics while retaining a deterministic trace.

## Engineering Port architecture

The **Engineering Port Contract** describes physical side, engineering domain, semantic kind, input/output/bidirectional direction, vanilla compatibility, and unit. `EngineeringPortSnapshot` carries live value/range/quality separately from static topology. `PortQuality` includes `VALID`, `NO_SIGNAL`, `SATURATED`, `STALE`, `FAULT`, `DOMAIN_MISMATCH`, and `TOPOLOGY_ERROR`.

## Required dependencies

| Dependency | Pinned development version | Required side | RSE purpose |
| --- | --- | --- | --- |
| JEI | `19.27.0.336` | Client | recipe/use browsing and engineering progression |
| Jade | `15.10.6` | Client + Server | engineering HUD and server-backed diagnostics |
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
5. **Operations & Integrated Systems** — communications, multi-sensor networks, production monitoring;
6. **Commissioning & Acceptance** — inspect topology, inject bounded faults, compare responses, record a verdict, and inspect its evidence.

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
14. Commission control loops against baseline and disturbed conditions before optimization.
15. Visualize authoritative ports/topology; never duplicate the topology solver in UI code.
16. Accept systems from authoritative evidence; never let an acceptance report become a second controller or simulator.
17. Present engineering evidence progressively: concise by default, structured and traceable underneath.

## Reference calculations

```text
Redstone: x = S / 15,  S ∈ {0, …, 15}
Copper divider: Vout = Vin × Rload / (Rseries + Rload)
Metrology residual: residual = reading - reference
Bias: mean(residual)
Uncertainty proxy ≈ RSS(repeatability, noise, drift, bias, quantization)
PID: e = setpoint - process
Commissioning score = bounded penalty model(error, settling, overshoot, saturation)
Topology face = EngineeringPort descriptor + live snapshot + PortCompatibility(local, neighbor)
Acceptance verdict = topology evidence + established commissioning status (read-only)
Acceptance HUD = concise projection(acceptance snapshot), never a second evaluator
```

## Verification architecture

CI runs verifier syntax, repository/source/resource audits, deterministic reference models, historical Alpha regressions, dependency checks, Engineering Port/Jade gates, legacy-renovation checks, directional-I/O guards, copper topology guards, **Alpha 1.0.14 metrology**, **Alpha 1.0.15 rollout/calibration**, **Alpha 1.0.16 commissioning/fault injection**, **Alpha 1.0.17 topology UX**, **Alpha 1.0.18 acceptance/traceability**, **Alpha 1.0.19 acceptance UX/evidence presentation**, Java 21 compilation, Gradle tests, **NeoForge Minecraft GameTests**, a clean build, SHA-256 generation and verified artifact upload.

Interactive visual behavior remains a separate `runClient` gate. Automated gates protect simulation-to-render ownership, metrology math, physical topology, directional I/O, copper runtime propagation, calibration semantics, commissioning ownership, fault bounds, topology projection, acceptance evidence ownership, HUD read-only ownership and sampling ownership.

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
- [`ALPHA1_0_19_MANIFEST.txt`](ALPHA1_0_19_MANIFEST.txt)
- [`ALPHA1_0_18_MANIFEST.txt`](ALPHA1_0_18_MANIFEST.txt)
- [`ALPHA1_0_17_MANIFEST.txt`](ALPHA1_0_17_MANIFEST.txt)
- [`ALPHA1_0_16_MANIFEST.txt`](ALPHA1_0_16_MANIFEST.txt)
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
