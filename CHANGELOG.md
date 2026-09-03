# Changelog

All notable RSE engineering milestones are recorded here. RSE remains in alpha development; interfaces and balancing may change as systems are validated in-game.

## [Unreleased]

- Future work should remain behind a focused branch/PR and pass the repository verification workflow before being merged into `main`.

## [1.0.13-alpha] — 2026-09-03

### Copper circuit topology renovation

- Added `PortKind.ELECTRICAL` and a shared `DirectionalCopperProcessorBlock` contract for axial copper components.
- Migrated Copper Series Resistor, Copper Capacitor, and Copper Fuse to explicit **BACK INPUT → FRONT OUTPUT** engineering ports without changing their `DomainNetwork`/`CircuitPhysics` ownership.
- Reworked the early compact implementations of the series resistor, capacitor, fuse, voltage source, resistive load, and circuit meter into maintainable source.
- Exposed Copper Voltage Source as a multi-face electrical OUTPUT node matching its existing network behavior.
- Exposed Copper Resistive Load as a multi-face terminal INPUT sink that remains non-transparent to propagation.
- Exposed Copper Circuit Meter as a single non-invasive FACING measurement port.
- Jade now obtains copper-domain topology and live voltage-equivalent snapshots through the common Engineering Port contract without copper-specific HUD simulation code.

### Runtime verification

- Added `RseCopperGameTests` and registered it alongside the existing topology suite.
- Added runtime tests for axial copper port direction, source→wire→series-resistor→wire→load attenuation, fuse trip/output cutoff, and SIDE-feed rejection.
- Added `tools/rse_alpha1013_copper_topology_verify.py`, milestone documentation, and a dedicated test matrix.
- Preserved all Alpha 1.0.11 cable/junction and Alpha 1.0.12 directional-I/O GameTests.

## [1.0.12-alpha] — 2026-09-03

### Directional I/O renovation

- Added `DirectionalRedstoneEndpointBlock` to centralize horizontal FRONT/BACK placement and Minecraft's reversed redstone-query convention for vanilla-compatible engineering endpoints.
- Added `DirectionalRedstoneSensorBlock` so sensor families share one FRONT-only `REDSTONE / SENSOR / OUTPUT` contract and a common bounded 0..15 update path.
- Rewrote the early compressed Redstone Reference Source implementation and changed it from six-face output to one explicit FRONT output.
- Migrated Engineering Light Sensor, Tank Level Sensor, and Entity Density Sensor from six-face outputs to the shared FRONT-only sensor contract.
- Replaced Analog Process Indicator's `LEGACY_OMNIDIRECTIONAL` behavior with a physical FRONT display and one BACK redstone input.
- Preserved the vanilla 0..15 world-facing boundary and kept the new orientation state low-cardinality.

### Runtime verification

- Extended `RseTopologyGameTests` with directional endpoint EngineeringPort/connectivity assertions.
- Added an in-world Analog Indicator test that reads 15 from a real BACK redstone block and rejects the same source when moved to a SIDE face.
- Added `tools/rse_alpha1012_directional_io_verify.py`, milestone documentation, and a test matrix.
- Preserved the Alpha 1.0.11 cable/junction placement-order, cross-domain isolation, terminal, and converter GameTests.

## [1.0.11-alpha] — 2026-09-03

### Legacy renovation wave II

- Migrated Redstone Cable Terminal to the shared `EngineeringPortProvider` contract with explicit Vanilla/Cable port direction that follows terminal mode.
- Reworked the early one-line Redstone Cable Junction implementation into maintainable source and exposed connected faces as `INSULATED_REDSTONE` bidirectional BUS ports.
- Migrated Copper Cable Junction to explicit `COPPER` bidirectional BUS ports and runtime voltage snapshots.
- Migrated the shared Lapis transducer base so the entire transducer family exposes a forward `LAPIS_PRECISION` sensor output with runtime quality.
- Added `PortKind.CONVERTER` and migrated Redstone → Lapis and Lapis → Redstone conversion blocks to explicit cross-domain input/output contracts.

### Executable Minecraft topology verification

- Added a dedicated `empty5x4x5` GameTest structure and NeoForge 1.21.1 `RegisterGameTestsEvent` registration.
- Added in-world tests proving Redstone Cable ↔ Redstone Junction connectivity and Redstone Cable ↔ Copper Junction isolation.
- Added tests for Redstone Cable Terminal mode-dependent port direction and both explicit Redstone/Lapis converter contracts.
- Promoted `./gradlew runGameTestServer --no-daemon --stacktrace` to a required GitHub Actions gate before the clean build.
- Added `tools/rse_alpha1011_legacy_gametest_verify.py` and milestone documentation.

## [1.0.10-alpha] — 2026-09-03

### Engineering Port Architecture

- Expanded the early port descriptor into a shared `EngineeringPort` contract carrying physical side, domain, semantic kind, direction, vanilla compatibility, and units.
- Added `EngineeringPortSnapshot`, `EngineeringPortProvider`, `PortQuality`, and `PortCompatibility` while keeping high-cardinality runtime observations out of BlockState.
- Migrated the Directional Signal family, legacy light/tank/entity sensors, Analog Indicator, Insulated Redstone Cable, and Instrument Bus to the common contract.
- Added the first real required-dependency consumer: a server-backed, read-only Jade engineering HUD that displays the targeted port's domain, direction, value/range, quality, and structural state.
- Enforced a dependency boundary so Jade remains in `integration/jade` and cannot become a second simulation model.

## [1.0.9-alpha] — 2026-09-03

### Required ecosystem platform

- Promoted JEI `19.27.0.336`, Jade `15.10.6`, GeckoLib `4.9.2`, Cloth Config `15.0.140`, and Fusion `1.3.14` to deliberate required RSE platform dependencies on their appropriate runtime sides.
- Switched the Gradle development classpath from optional `compileOnly`/`localRuntime` forms to direct implementations so RSE can build integrations against the APIs without fallback glue.
- Kept physics, topology, control, and diagnostics authoritative in RSE while reserving the ecosystem libraries for recipe information, HUD diagnostics, animation, configuration UI, and advanced visual rendering.
- Added required-dependency policy verification and real Maven-resolution gates.

## [1.0.8-alpha] — Dependency & Interoperability Foundation

### Dependency architecture

- Established the first JEI/Jade integration foundation before Alpha 1.0.9 intentionally promoted the ecosystem platform to required dependencies.
- Added JEI `19.27.0.336` and Jade `15.10.6` to the development environment and centralized integration detection through `IntegrationStatus`.
- Added `docs/DEPENDENCY_POLICY.md` and dependency-resolution verification.

### Quality gates

- Added `tools/rse_alpha108_dependency_verify.py`.
- CI Java compilation resolves the pinned ecosystem artifacts so stale or invalid Maven coordinates fail before merge.
- Made the Alpha 1.0.7 verifier forward-compatible so later versions continue to protect legacy wiring/port contracts.

## [1.0.7-alpha] — Legacy Wiring & Port Diagnostics

- Upgraded Instrument Cable to a six-direction connected instrumentation bus.
- Made InstrumentNetwork traverse physical connected edges rather than raw adjacency.
- Clarified Signal Probe sensing face versus instrumentation-bus connection face.
- Added domain/port diagnostics for insulated redstone, copper, lapis, quartz, instrumentation cable, and cable terminals.
- Preserved explicit cross-domain isolation with `DOMAIN_MISMATCH` diagnostics.

## [1.0.6-alpha] — Engineering Language & Progression

### Engineering vocabulary

- Renamed the core instrumentation/control/mechatronics/operations devices with more precise engineering terminology in English and Chinese.
- Added explicit discipline mapping for Engineering Physics, ECE, Mechanical/Mechatronics, and IOE.
- Added school-angle development guidance for Michigan-style interdisciplinary engineering, UIUC-style ECE depth, UW–Madison-style experimental/engineering-physics depth, and CMU-style ECE/robotics systems thinking.

### Crafting progression

- Organized representative devices into five engineering tiers: basic measurement, acquisition/processing, control/mechatronics, safety/reliability, and integrated operations.
- Signal Analyzer now uses copper, glass, quartz, and redstone as a Tier-1 instrument.
- Oscilloscope and Logic Analyzer now depend on the Signal Analyzer.
- PID Controller now depends on Signal Conditioner and comparator logic.
- Servo Actuator now uses piston, copper, iron, redstone, and comparator control components.
- Pneumatic Proportional Control Valve now depends on a Pneumatic Isolation Valve and comparator control.
- Operations Monitor now depends on a Logic Analyzer, clock, and observer.

### Visual language and feedback

- Added refreshed 16×16 engineering pixel art for the Signal Analyzer front panel and Calibration Module front panel.
- Replaced the Pneumatic Relief Valve's generic redstone-block appearance with a dedicated metal/safety/gauge texture.
- Added cloud particles only when a Pneumatic Safety Relief Valve actually vents excess pressure.

### Verification

- Added `tools/rse_alpha106_engineering_verify.py` to check terminology, tier dependencies, PNG dimensions, safety particle implementation, documentation, and version synchronization.
- Preserved all previous repository/source/reference-model and Alpha regression gates.

## [1.0.5-alpha] — 2026-09-03

### Measurement quality

- Added a 16-sample rolling window to Signal Analyzer runtime diagnostics.
- Added rolling average, peak-to-peak, mean absolute sample-step, sample-age, and recent stability classification.
- Kept lifetime min/max/change/rising/falling/max-delta statistics separate from recent-window behavior.
- Added small persistent Analyzer display calibration (`-2..+2`) with clamped displayed readings.
- Enforced the critical invariant that Analyzer calibration is **display-only** and never changes INLINE raw `0..15` pass-through output.

### Instrument-network integrity

- Added valid-channel versus active-channel counts.
- Added duplicate-probe count in addition to duplicate-channel count.
- Added maximum cable/probe depth diagnostics.
- Added explicit structural integrity states: `OK`, `NO_PROBES`, `AMBIGUOUS`, and `TRUNCATED`.

### Oscilloscope quality

- Added valid-sample coverage percentage and capture-quality classification.
- Added average signal and mean absolute sample-step diagnostics.
- Added period reporting in both samples and physical ticks.
- Added cursor delta reporting in samples and ticks.
- Persisted `samplesSinceTrigger` so save/reload does not silently restart post-trigger progress.

### Logic Analyzer quality

- Added channel capture coverage and capture-quality classification.
- Added total edge count and transition-rate diagnostics.
- Added explicit one-tick sample timebase and cursor timing in ticks.
- Persisted `postTriggerSamples` across save/reload.
- Reworked the block-entity implementation into readable, bounds-checked source rather than compact/minified code.

### Repository-wide quality gates

- Added `tools/rse_source_quality_audit.py` for Java package/path consistency, brace/whitespace smoke checks, JSON parsing, resource-name collisions, local model-reference integrity, item-model pairing, BlockState cardinality, and root hygiene.
- Added `tools/rse_reference_model_tests.py` for deterministic `0..15`, calibration, rolling-metric, topology-integrity, and analyzer-timebase mathematics.
- Added `tools/rse_alpha105_quality_verify.py` for Alpha 1.0.5 implementation and regression contracts.
- Made the Alpha 1.0.4 verifier forward-compatible so it continues to protect 1.0.4 topology contracts on later Alpha versions instead of hard-coding the old version number.
- CI validates Python verifier syntax, Gradle `test`, Java compilation, clean build, SHA-256, and verified artifacts.

### Validation status

- Alpha 1.0.5 was squash-merged into `main` at commit `48036c377a95d0d9891697a4cfb75c681f9b269e` after the strengthened PR quality ladder passed.
- The canonical `main` commit independently passed the full quality ladder.

## [1.0.4-alpha] — 2026-09-03

- Added Signal Analyzer `TAP` and `INLINE` measurement topologies.
- Added direction-aware measurement and instrumentation topology diagnostics.
- Made Pneumatic Cylinder position feedback directional and made the cylinder a terminal one-port pneumatic actuator.
- Added Alpha 1.0.4 regression verification and interactive test documentation.

## [1.0.3-alpha] — 2026-09-03

- Completed PID Manual/Auto control, anti-windup and bumpless transfer.
- Added Servo Position/Velocity modes, braking and soft-limit diagnostics.
- Strengthened bus/radio diagnostics, pneumatic proportional/safety/actuation components and IOE operating-state classification.
- Added MIT licensing, repository hardening and GitHub Actions build verification.
