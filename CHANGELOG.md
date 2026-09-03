# Changelog

All notable RSE engineering milestones are recorded here. RSE remains in alpha development; interfaces and balancing may change as systems are validated in-game.

## [Unreleased]

- Future work should remain behind a focused branch/PR and pass the repository verification workflow before being merged into `main`.

## [1.0.8-alpha] — Dependency & Interoperability Foundation

### Dependency architecture

- Kept the RSE core launchable with Minecraft 1.21.1 + NeoForge alone.
- Added JEI `19.27.0.336` through its official Maven as compile-only API plus full `localRuntime` development mod.
- Added Jade `15.10.6+neoforge` as compile-only + `localRuntime` using a narrowly filtered Modrinth Maven repository.
- Added centralized optional-integration detection through `IntegrationStatus` without importing optional-mod classes into the RSE core startup path.
- Added `docs/DEPENDENCY_POLICY.md` to classify native, optional, future feature-driven, and disallowed accidental dependency patterns.

### Quality gates

- Added `tools/rse_alpha108_dependency_verify.py`.
- CI now verifies that JEI/Jade remain optional, are not shaded/bundled, and are not declared as required NeoForge dependencies.
- CI Java compilation also resolves the pinned ecosystem artifacts so a stale or invalid Maven coordinate fails before merge.
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
