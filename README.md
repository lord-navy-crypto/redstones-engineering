# Redstone Systems Engineering (RSE)

[![RSE Build Verification](https://github.com/lord-navy-crypto/redstones-engineering/actions/workflows/build.yml/badge.svg)](https://github.com/lord-navy-crypto/redstones-engineering/actions/workflows/build.yml)

**Redstone Systems Engineering** is a NeoForge engineering-systems mod for Minecraft that extends vanilla redstone without replacing it. RSE keeps the vanilla **0–15 redstone signal as the world-facing engineering boundary** and builds measurement, conditioning, sampling, control, actuation, diagnostics, communications, safety, and operations tools around it.

> **Engineering path:** Measurement → Conditioning → Sampling → Control → Actuation → Optimization

## Project information

| Item | Current RSE baseline |
| --- | --- |
| Development milestone | **Alpha 1.0.14 — Metrology & Uncertainty** |
| Artifact version | `1.0.14-alpha` |
| Minecraft | `1.21.1` |
| NeoForge | `21.1.249` |
| Java | `21` |
| Mod ID | `redstoneengineering` |
| License | **MIT** |

## Alpha 1.0.14 — Metrology & Uncertainty

Alpha 1.0.14 upgrades RSE sensors from value-only devices into measurement systems that can also describe how trustworthy a reading is. The vanilla-facing engineering contract is still bounded to `0..15`; richer quality metadata stays in transient engineering runtime state rather than exploding BlockState cardinality.

The shared metrology layer now represents:

- **Repeatability** — short-window spread of repeated residuals;
- **Bias** — mean reading-minus-reference residual;
- **Drift** — change between earlier and later portions of the rolling window;
- **Noise** — first-difference noise proxy;
- **Resolution** — finite instrument/quantization resolution;
- **Saturation** — explicit indication that the real condition exceeded the representable range;
- **Sample age** — how old the most recent measurement is;
- **Measurement uncertainty proxy** — a conservative diagnostic RSS proxy, explicitly not a claim of formal GUM expanded uncertainty.

`MeasurementSnapshot`, `MeasurementQuality`, `MetrologyTracker`, and `MetrologyStore` provide the reusable architecture. The tracker uses a 32-sample rolling residual window and reports `GOOD`, `DEGRADED`, `SATURATED`, `STALE`, or `INVALID` quality without confusing uncertainty with measurement error.

The Tank Level Sensor is the first direct sensor integration. Its physical fluid-column state passes through the existing `SensorModel`, produces the same vanilla-compatible `0..15` redstone output, and simultaneously feeds repeatability/bias/drift/noise/resolution/sample-age/saturation diagnostics. Fluid columns above the representable range now report `SATURATED` instead of silently presenting a clipped reading as exact.

## Alpha 1.0.13 — Copper Topology + Mechatronics Visualization

Alpha 1.0.13 preserves the completed copper electrical renovation while also completing the intended first real GeckoLib mechatronics visualization layer.

The copper renovation introduced `PortKind.ELECTRICAL` and a shared `DirectionalCopperProcessorBlock` for axial two-port components. Copper Series Resistor, Copper Capacitor, and Copper Fuse expose explicit **BACK COPPER INPUT → FRONT COPPER OUTPUT** contracts with live `0..15 V-eq` snapshots for Jade. Their resistor-divider, RC and fuse-trip behavior remains owned by `CircuitPhysics`, `DomainNetwork`, and runtime state rather than by the UI layer.

Other copper devices keep their real physical roles:

- Copper Voltage Source — multi-face `COPPER / ELECTRICAL / OUTPUT` node;
- Copper Resistive Load — multi-face terminal `INPUT` sink that never becomes a transparent conductor;
- Copper Circuit Meter — one non-invasive `FACING / MEASUREMENT / INPUT` port.

The mechatronics completion now uses GeckoLib `4.9.2` for three articulated machines:

- Servo Actuator — authoritative simulation `position` drives shaft rotation; simulation velocity controls the rate of position change; brake stops motion in physics before rendering sees the state;
- Pneumatic Cylinder — pressure and position drive piston-rod extension plus a pressure indicator;
- Pneumatic Proportional Valve — `opening 0..15` drives valve-spool position.

The critical dependency direction is enforced in code: `RuntimeIntStore.peek()` returns cloned read-only snapshots, `MechatronicsVisualState` is immutable, and a synchronized `MechatronicsVisualBlockEntity` mirrors state to the client. **GeckoLib only displays simulation state; renderer FPS, bones, animation controllers, or client lifecycle never determine physics state.**

The Minecraft GameTest suite includes real copper-domain behavior plus metrology/visualization contracts. All previous cable/junction and directional-redstone tests remain required.

See [`ALPHA1_0_13_MANIFEST.txt`](ALPHA1_0_13_MANIFEST.txt) and [`ALPHA1_0_14_MANIFEST.txt`](ALPHA1_0_14_MANIFEST.txt).

## Alpha 1.0.12 — Directional I/O Renovation

Alpha 1.0.12 removed another early-system ambiguity: vanilla-redstone endpoints that had clear engineering meaning but still accepted or emitted signals on every face.

The directional endpoint renovation added two shared foundations:

- `DirectionalRedstoneEndpointBlock` — a low-cardinality horizontal `FACING` contract with explicit FRONT/BACK helpers and centralized handling of Minecraft's reversed redstone-query direction;
- `DirectionalRedstoneSensorBlock` — one reusable FRONT-only `REDSTONE / SENSOR / OUTPUT` port, bounded `0..15` output, shared server update logic, and EngineeringPort snapshots for Jade.

Migrated devices now have physical I/O that agrees with their engineering role:

- Redstone Reference Source — adjustable `0..15`, **FRONT output only**;
- Engineering Light Sensor — measured brightness, **FRONT output only**;
- Tank Level Sensor — bounded fluid-column measurement, **FRONT output only**;
- Entity Density Sensor — bounded nearby-entity count, **FRONT output only**;
- Analog Process Indicator — **FRONT display / BACK input only**, replacing its old omnidirectional behavior.

Minecraft GameTests exercise these rules as runtime behavior, including a real BACK redstone source and rejected SIDE source for the Analog Indicator.

## Alpha 1.0.11 — Legacy Renovation Wave II + Topology GameTests

Alpha 1.0.11 moved more early RSE infrastructure onto the shared Engineering Port Contract and made critical topology rules executable inside Minecraft rather than only checking source tokens.

The migration covered Redstone Cable Terminal, Redstone/Copper junctions, the shared Lapis transducer family, Redstone→Lapis scaler and Lapis→Redstone quantizer. CI launches NeoForge's Minecraft GameTest server and proves insulated-redstone Cable↔Junction connectivity in either placement order, cross-domain isolation, terminal-mode direction and explicit converter domains.

## Alpha 1.0.10 — Engineering Port Contract

Alpha 1.0.10 established the architectural base used by later renovation waves:

- `EngineeringPort` — physical side, engineering domain, semantic kind, flow direction, vanilla compatibility and unit;
- `EngineeringPortSnapshot` — dynamic value/range/quality kept outside BlockState;
- `EngineeringPortProvider` — one API for blocks to expose descriptors and observations;
- `PortCompatibility` — centralized direct domain/direction compatibility;
- `PortQuality` — `VALID`, `NO_SIGNAL`, `SATURATED`, `STALE`, `FAULT`, `DOMAIN_MISMATCH`, `TOPOLOGY_ERROR`.

Jade became the first required dependency to consume this contract through a server-backed, read-only engineering HUD.

## Required dependencies

Five mature ecosystem libraries are part of the RSE platform contract:

| Dependency | Pinned development version | Required side | RSE purpose |
| --- | --- | --- | --- |
| JEI | `19.27.0.336` | Client | recipe/use browsing, engineering progression and machine/recipe information |
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
Measurement, calibration, repeatability, bias, drift, noise, resolution, uncertainty proxies, electromagnetism, thermal/optical experiments, resonators, and model-versus-measurement reasoning.

### Electrical & Computer Engineering
Analog conditioning, sampling, timing, oscillators, PWM, buses, serial/differential links, radio, instrumentation, digital diagnostics, watchdogs and control implementation.

### Mechanical / Mechatronics
Servo motion, position feedback, articulated visualization, pneumatic pressure networks, regulators, valves, relief protection, cylinders, vibration and damping.

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
11. Prefer executable Minecraft behavior tests for topology contracts that source-only verification cannot prove.
12. Treat rendering as a downstream observer: visualization may consume physics state but never author it.

## Reference calculations

Redstone normalization:

```text
S ∈ {0, …, 15}
x = S / 15
```

Copper series divider:

```text
Vout = Vin × Rload / (Rseries + Rload)
```

Analyzer calibration remains display-only:

```text
S_display = clamp(S_raw + offset, 0, 15)
INLINE OUT = S_raw
```

Metrology diagnostic decomposition:

```text
residual = reading - reference
bias = mean(residual)
repeatability ≈ stddev(residual)
drift ≈ mean(late residuals) - mean(early residuals)
uncertainty proxy ≈ RSS(repeatability, noise, drift, calibration residual, quantization)
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

CI runs verifier syntax, repository/source/resource audits, deterministic reference models, historical Alpha regressions, required-dependency checks, Engineering Port/Jade gates, legacy-renovation checks, directional-I/O guards, Alpha 1.0.13 copper topology guards, **Alpha 1.0.14 metrology + GeckoLib one-way-boundary verification**, Java 21 compilation, Gradle tests, **NeoForge Minecraft GameTests**, a clean build, SHA-256 generation and verified artifact upload.

Interactive visual/UX behavior remains a separate `runClient` gate, while the simulation-to-render ownership boundary, metrology math, physical topology, directional I/O and copper runtime propagation have automated source/reference/GameTest gates.

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
- [`ALPHA1_0_14_MANIFEST.txt`](ALPHA1_0_14_MANIFEST.txt)
- [`ALPHA1_0_13_MANIFEST.txt`](ALPHA1_0_13_MANIFEST.txt)
- [`CONTRIBUTING.md`](CONTRIBUTING.md)
- [`docs/DEPENDENCY_POLICY.md`](docs/DEPENDENCY_POLICY.md)
- [`docs/ALPHA1_0_13_COPPER_TOPOLOGY_RENOVATION.md`](docs/ALPHA1_0_13_COPPER_TOPOLOGY_RENOVATION.md)
- [`docs/ALPHA1_0_13_TEST_MATRIX.md`](docs/ALPHA1_0_13_TEST_MATRIX.md)
- [`docs/ALPHA1_0_12_DIRECTIONAL_IO_RENOVATION.md`](docs/ALPHA1_0_12_DIRECTIONAL_IO_RENOVATION.md)
- [`docs/ALPHA1_0_12_TEST_MATRIX.md`](docs/ALPHA1_0_12_TEST_MATRIX.md)
- [`docs/ALPHA1_0_11_LEGACY_RENOVATION_AND_GAMETEST.md`](docs/ALPHA1_0_11_LEGACY_RENOVATION_AND_GAMETEST.md)
- [`docs/ALPHA1_0_10_ENGINEERING_PORT_ARCHITECTURE.md`](docs/ALPHA1_0_10_ENGINEERING_PORT_ARCHITECTURE.md)
- [`docs/ALPHA1_0_10_JADE_ENGINEERING_HUD.md`](docs/ALPHA1_0_10_JADE_ENGINEERING_HUD.md)
- [`docs/ENGINEERING_LANGUAGE_AND_CURRICULUM.md`](docs/ENGINEERING_LANGUAGE_AND_CURRICULUM.md)
- [`docs/CRAFTING_PROGRESSION.md`](docs/CRAFTING_PROGRESSION.md)

## License

RSE is released under the **MIT License**. See [`LICENSE`](LICENSE).
