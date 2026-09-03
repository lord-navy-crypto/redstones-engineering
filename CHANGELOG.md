# Changelog

All notable RSE engineering milestones are recorded here. RSE remains in alpha development; interfaces and balancing may change as systems are validated in-game.

## [Unreleased]

- Future work should remain behind a focused branch/PR and pass the repository verification workflow before being merged into `main`.

## [1.0.5-alpha] — Quality candidate

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
- CI now validates all Python verifier syntax with `compileall`.
- CI explicitly runs Gradle `test` in addition to `compileJava` and clean `build`.
- All Alpha 1.0.3 and Alpha 1.0.4 regression verifiers remain required.

### Validation status

- Branch must pass the strengthened quality ladder before merge to `main`.
- Local `runClient` quality/calibration and save/reload validation remains required before a public Alpha 1.0.5 release candidate.

## [1.0.4-alpha] — 2026-09-03

### Instrumentation and topology

- Added Signal Analyzer `TAP` and `INLINE` measurement topologies.
- `TAP` remains completely non-invasive; `INLINE` provides an explicit TEST input and opposite-side raw `0..15` pass-through output.
- Added continuous analyzer min/max/change/rising/falling/delta/stability/sample diagnostics in transient runtime storage.
- Added direction-aware measurement for active directional sources.
- Preserved direct node-value measurement for redstone dust and RSE conductors.
- Added instrument cable/probe/channel/ambiguity/bounded-scan topology diagnostics.
- Exposed network topology summaries in Oscilloscope and Logic Analyzer status.

### Pneumatic / feedback topology

- Changed Pneumatic Cylinder redstone position feedback from all-side emission to an explicit FRONT/FACING output.
- Preserved pneumatic input on BACK.
- Made the Cylinder a terminal one-port pneumatic actuator so it cannot bridge pressure through FRONT/sides into another network.
- Added peak-pressure, motion-reversal, and sample-count diagnostics.

### Repository and validation

- Advanced artifact version to `1.0.4-alpha`.
- Made repository verification milestone-agnostic.
- Added Alpha 1.0.4 verifier and interactive test documentation.
- Main branch passed static verification, Java 21 `compileJava`, clean Gradle build, SHA-256 generation, and verified artifact upload after merge.

## [1.0.3-alpha] — 2026-09-03

### Closed-loop control

- Completed PID Manual/Auto operation using explicit engineering ports.
- Added bounded manual output, output saturation, anti-windup, derivative filtering, deadband, and bumpless Manual→Auto transfer.
- Preserved step-response rise-time, settling-time, overshoot, saturation, and controller-runtime diagnostics.

### Mechatronics

- Added Servo Position and Velocity modes.
- Added centered velocity command semantics (`7=stop`, lower=reverse, higher=forward).
- Added braking, bounded slew/acceleration behavior, `0..15` soft limits, and soft-limit-hit diagnostics.

### Digital systems and communications

- Separated data-bus physical driver count from distinct driven values.
- Added conflict, contention, and same-value multi-driver diagnostics.
- Added accumulated radio valid/undecodable/collision/dropout/channel-handoff diagnostics.
- Kept decoded radio payload and link-quality/noise measurements separate.

### Pneumatics

- Integrated Pneumatic Proportional Valve, Pneumatic Relief Valve, and Pneumatic Cylinder.
- Added network recognition, proportional pressure limiting, relief/safety behavior, and actuator diagnostics.
- Preserved the redstone `0..15` command boundary while using a richer internal pneumatic pressure scale.

### Operations / IOE

- Added derived operating-state classification: `NOMINAL`, `CONGESTED`, `NOISY`, `UNSTABLE`, `OVERLOADED`, `SAFETY_LIMITED`, and `FAILED`.
- Added starvation, blocked/fault, high-queue-running, longest-downtime, queue-variation, and run-state-transition metrics.

### Repository and validation

- Replaced the NeoForge MDK README with RSE-specific engineering documentation and reference calculations.
- Added MIT licensing and aligned NeoForge/Gradle metadata.
- Added repository hygiene verification, Java 21 GitHub Actions compile/build validation, and SHA-256-addressed test artifacts.
