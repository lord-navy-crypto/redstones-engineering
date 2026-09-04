# Redstone Systems Engineering (RSE)

[![RSE Build Verification](https://github.com/lord-navy-crypto/redstones-engineering/actions/workflows/build.yml/badge.svg)](https://github.com/lord-navy-crypto/redstones-engineering/actions/workflows/build.yml)

**Redstone Systems Engineering** is a NeoForge engineering-systems mod for Minecraft that extends vanilla redstone without replacing it. RSE keeps the vanilla **0–15 redstone signal as the world-facing engineering boundary** and builds measurement, conditioning, sampling, control, actuation, diagnostics, communications, safety, reliability, and operations tools around it.

> **Engineering path:** Measurement → Conditioning → Sampling → Control → Actuation → Optimization → Acceptance → Evidence → Comparison

## Project information

| Item | Current RSE baseline |
| --- | --- |
| Development milestone | **Alpha 1.0.20 — Commissioning Run History & Baseline Comparison** |
| Artifact version | `1.0.20-alpha` |
| Minecraft | `1.21.1` |
| NeoForge | `21.1.249` |
| Java | `21` |
| Mod ID | `redstoneengineering` |
| License | **MPL-2.0** |

## Alpha 1.0.20 — Commissioning Run History & Baseline Comparison

Alpha 1.0.20 turns the acceptance result from a momentary HUD observation into an **explicitly captured engineering run record**. A player can crouch and right-click the **FRONT face of a PID Controller** to freeze the current authoritative acceptance snapshot together with game tick, tuning preset, and a monotonic local sequence number.

```text
Shift + FRONT click → capture acceptance evidence
Normal click         → cycle PID tuning preset
Shift + other face   → reset PID runtime
```

Capture is intentionally player-owned. Jade does not create records, renderer FPS does not define record cadence, and the PID controller does not automatically accumulate history every tick. This keeps a commissioning record conceptually close to an engineer choosing when to document a test result.

`AcceptanceEvidenceTimeline` retains at most **8 records per PID Controller**. `AcceptanceEvidenceStore` retains at most **256 controller timelines per loaded level** and lives outside BlockState and outside the PID `RuntimeIntStore`. The Alpha 1.0.20 history is deliberately **transient diagnostic evidence**, not durable world-save data; that boundary avoids pretending an in-memory alpha cache is a permanent laboratory notebook.

The latest two captured runs can be compared through `AcceptanceEvidenceComparison`:

```text
#2→#3 IMPROVED Δscore=+22 Δissues=0
```

Comparison reports commissioning-score delta, topology-issue delta, and an `IMPROVED / SAME / REGRESSED / INCOMPARABLE` trend. Topology issue changes take priority, then final acceptance severity, then commissioning score. `NOT_READY` evidence remains incomparable rather than being treated as a misleading numerical improvement or regression.

The Jade Engineering HUD remains read-only and adds captured-run history only when records actually exist. It can show the latest record and latest-versus-previous comparison, but HUD polling cannot capture, delete, or rewrite evidence.

See [`ALPHA1_0_20_MANIFEST.txt`](ALPHA1_0_20_MANIFEST.txt).

## Alpha 1.0.19 — Acceptance UX & Evidence Presentation

Historical artifact: `1.0.19-alpha`.

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

The server-backed **Jade Engineering HUD** keeps its targeted-face detail and now adds an all-face topology summary. A player can inspect, for example, whether FRONT is a control output, BACK is a sensor input, a side is isolated, or a neighboring interface is wrong-domain/wrong-direction without guessing from block orientation alone.

```text
Targeted face: value / range / quality
Topology: connected ports / total ports / issues
Per face: DOMAIN / DIRECTION → LINK STATUS [QUALITY]
```

This is deliberately a downstream observer layer: transmission/network ownership remains in the existing RSE topology and simulation code. Vanilla-compatible world-facing redstone remains **0..15**.

See [`ALPHA1_0_17_MANIFEST.txt`](ALPHA1_0_17_MANIFEST.txt).

## Alpha 1.0.16 — Closed-Loop Commissioning & Fault Injection

Alpha 1.0.16 turns the existing PID step-response counters into a stable, read-only commissioning contract and connects existing laboratory disturbance devices to an explicit fault-injection workflow.

`ClosedLoopCommissioning` consumes the PID runtime only through `RuntimeIntStore.peek()`. It exposes setpoint, process value, control output, error, 90% rise time, settling time, overshoot, saturation events, step age, operating mode, inhibit state, mode-transfer count, a bounded commissioning score and `IDLE/RUNNING/PASS/MARGINAL/FAIL` status. Diagnostics never create, resize or mutate controller state.

Commissioning supports **baseline-versus-disturbed comparison** through `CommissioningComparison`, reporting score loss, settling penalty, overshoot increase and saturation increase. This makes controller tuning testable under controlled non-ideal conditions instead of judging a loop only by whether it eventually reaches the setpoint.

Fault-injection primitives remain physically typed rather than becoming a universal cable feature:

- **Lapis Noise Source → NOISE fault injection** — deterministic position/time-seeded disturbance in the existing `0..100` Lapis precision domain;
- **Quartz Phase Delay → LATENCY fault injection** — bounded rising-edge delay in the Quartz timing domain;
- `FaultInjectionModel` also provides bounded bias, dropout and actuator-saturation primitives for repeatable engineering tests.

The world-facing vanilla redstone boundary remains **0..15**. Fault injection changes test conditions; it does not give UI, rendering, GeckoLib, Jade, or diagnostics ownership of simulation state.

See [`ALPHA1_0_16_MANIFEST.txt`](ALPHA1_0_16_MANIFEST.txt).

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
- **Alpha 1.0.16 — Closed-Loop Commissioning & Fault Injection** — promotes PID response metrics into a stable commissioning snapshot and adds repeatable typed disturbance testing.
- **Alpha 1.0.17 — Engineering UX & Topology Visualization** — exposes all-face port/link/quality diagnostics as a read-only projection in the Jade Engineering HUD.
- **Alpha 1.0.18 — Engineering Acceptance & Traceability** — combines authoritative topology and commissioning evidence into a deterministic read-only engineering verdict with stable trace codes.
- **Alpha 1.0.19 — Acceptance UX & Evidence Presentation** — exposes PID acceptance evidence through concise server-backed Jade diagnostics while retaining a deterministic trace.
- **Alpha 1.0.20 — Commissioning Run History & Baseline Comparison** — adds explicit bounded capture of acceptance runs and deterministic latest-versus-previous comparison.

## Engineering Port architecture

The **Engineering Port Contract** describes physical side, engineering domain, semantic kind, input/output/bidirectional direction, vanilla compatibility, and unit. `EngineeringPortSnapshot` carries live value/range/quality separately from static topology. `PortQuality` includes `VALID`, `NO_SIGNAL`, `SATURATED`, `STALE`, `FAULT`, `DOMAIN_MISMATCH`, and `TOPOLOGY_ERROR`.

## Required dependencies

Five mature ecosystem libraries are part of the RSE platform contract:

| Dependency | Pinned development version | Required side | RSE purpose |
| --- | --- | --- | --- |
| JEI | `19.27.0.336` | Client | recipe/use browsing and engineering progression |
| Jade | `15.10.6` | Client + Server | engineering HUD and server-backed port/acceptance/run-history diagnostics |
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
6. **Commissioning & Acceptance** — inspect topology, inject bounded faults, compare responses, record a verdict, explicitly capture runs, and compare evidence.

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
18. Capture run history explicitly, bound its memory cost, and distinguish transient evidence from durable persistence.

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
Run comparison: Δscore = candidate score - baseline score
Run comparison: Δissues = candidate topology issues - baseline topology issues
```

## Verification architecture

CI runs verifier syntax, repository/source/resource audits, deterministic reference models, historical Alpha regressions, dependency checks, Engineering Port/Jade gates, legacy-renovation checks, directional-I/O guards, copper topology guards, **Alpha 1.0.14 metrology**, **Alpha 1.0.15 multi-domain rollout/calibration**, **Alpha 1.0.16 closed-loop commissioning/fault injection**, **Alpha 1.0.17 engineering UX/topology visualization**, **Alpha 1.0.18 engineering acceptance/traceability**, **Alpha 1.0.19 acceptance UX/evidence presentation**, **Alpha 1.0.20 commissioning run-history/baseline comparison**, Java 21 compilation, Gradle tests, **NeoForge Minecraft GameTests**, a clean build, SHA-256 generation and verified artifact upload.

Interactive visual/UX behavior remains a separate `runClient` gate. Automated gates protect simulation-to-render ownership, metrology math, physical topology, directional I/O, copper runtime propagation, calibration semantics, commissioning read-only ownership, fault bounds, all-face topology projection, acceptance evidence ownership, explicit-capture history ownership, HUD read-only ownership and sampling ownership.

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
- [`ALPHA1_0_20_MANIFEST.txt`](ALPHA1_0_20_MANIFEST.txt)
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

RSE is released under the **Mozilla Public License 2.0 (MPL-2.0)**. See [`LICENSE`](LICENSE).