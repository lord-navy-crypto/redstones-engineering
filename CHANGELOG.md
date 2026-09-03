# Changelog

All notable RSE engineering milestones are recorded here. RSE is still in alpha development; interfaces and balancing may change as systems are validated in-game.

## [Unreleased]

- No committed feature set yet. New work should remain behind a branch/PR and pass the repository verification workflow before being described here.

## [1.0.3-alpha] — 2026-09-03

### Closed-loop control

- Completed PID Manual/Auto operation using explicit engineering ports.
- Added bounded manual output, output saturation, anti-windup, derivative filtering, deadband, and bumpless Manual→Auto transfer.
- Preserved step-response rise-time, settling-time, overshoot, saturation, and controller-runtime diagnostics.

### Mechatronics

- Added Servo Position and Velocity modes.
- Added centered velocity command semantics (`7=stop`, lower=reverse, higher=forward).
- Added braking, bounded slew/acceleration behavior, 0–15 soft limits, and soft-limit-hit diagnostics.

### Digital systems and communications

- Separated data-bus physical driver count from distinct driven values.
- Added conflict, contention, and same-value multi-driver diagnostics.
- Added accumulated radio valid/undecodable/collision/dropout/channel-handoff diagnostics.
- Kept decoded radio payload and link-quality/noise measurements separate.

### Pneumatics

- Integrated Pneumatic Proportional Valve, Pneumatic Relief Valve, and Pneumatic Cylinder.
- Added network recognition, proportional pressure limiting, relief/safety behavior, and actuator diagnostics.
- Preserved the redstone 0–15 command boundary while using a richer internal pneumatic pressure scale.

### Operations / IOE

- Added derived operating-state classification: `NOMINAL`, `CONGESTED`, `NOISY`, `UNSTABLE`, `OVERLOADED`, `SAFETY_LIMITED`, and `FAILED`.
- Added starvation, blocked/fault, high-queue-running, longest-downtime, queue-variation, and run-state-transition metrics.

### Repository and validation

- Replaced the NeoForge MDK README with RSE-specific engineering documentation and reference calculations.
- Added MIT licensing and aligned NeoForge/Gradle metadata.
- Normalized the artifact version to `1.0.3-alpha`.
- Added repository hygiene verification and strengthened Alpha 1.0.3 static verification.
- Added Java 21 GitHub Actions `compileJava` and clean-build validation.
- Added CI build artifacts with SHA-256 checksums for traceable test binaries.

### Validation status

- Pre-integrity Alpha 1.0.3 baseline: local Gradle build confirmed on macOS/JDK 21.
- Integrity-hardened Alpha 1.0.3: GitHub Actions static verification, `compileJava`, and `clean build` confirmed.
- Interactive `runClient` behavior validation remains required before creating a public Alpha 1.0.3 release.
