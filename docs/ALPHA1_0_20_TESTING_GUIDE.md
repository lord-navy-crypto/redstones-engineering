# RSE Alpha 1.0.20 Testing Guide

This guide is for the first public alpha-testing pass of **Redstone Systems Engineering 1.0.20-alpha**.

RSE is still alpha software. Back up any world you care about before testing, and prefer a new test world for the first run. Interfaces, balance, recipes, and internal data formats may change during alpha development.

## Supported test baseline

| Component | Alpha 1.0.20 baseline |
| --- | --- |
| Minecraft | `1.21.1` |
| NeoForge | `21.1.249` |
| Java | `21` |
| RSE | `1.0.20-alpha` |
| JEI | `19.27.0.336` |
| Jade | `15.10.6` |
| GeckoLib | `4.9.2` |
| Cloth Config | `15.0.140` |
| Fusion | `1.3.14` / `1.3.14-neoforge-mc1.21.1` |

The five ecosystem mods above are deliberate RSE runtime dependencies. RSE does not bundle their jars.

## Installation smoke test

1. Install the supported Minecraft, NeoForge, and Java versions above.
2. Put the RSE jar and all required dependency jars in the instance `mods` folder.
3. Start Minecraft and confirm it reaches the title screen without an RSE loading error.
4. Create a fresh test world for the first functional pass.
5. Confirm RSE blocks/items are present and JEI/Jade integrations load.

If the game fails before reaching the title screen, report the full `latest.log` and crash report if one was produced. Do not report only the last visible launcher line; dependency and initialization failures are often explained earlier in the log.

## Recommended first-pass test matrix

### 1. Vanilla-redstone boundary

Build a simple vanilla redstone source feeding an RSE measurement/control path. Verify that the world-facing redstone interface remains bounded to **0..15** and that RSE does not require a replacement redstone system for ordinary connections.

### 2. Measurement and signal conditioning

Exercise representative instruments such as Signal Probe, Signal Analyzer, Oscilloscope, Signal Conditioner, and Calibration Module. Look for:

- readings that disagree with the physical input;
- stale displays that do not update after the source changes;
- sampling behavior that appears tied to HUD/render polling;
- values escaping their documented range.

### 3. Directional I/O and topology

Test directional sensors, indicators, terminals, junctions, and Engineering Ports from correct and incorrect faces. Look for accidental SIDE connections, direction mismatches that are not diagnosed, or cross-domain links that incorrectly conduct.

### 4. Copper and other explicit domains

Build a small copper source/conductor/load chain and at least one explicit cross-domain converter. Confirm that incompatible domains remain isolated unless a documented converter/transducer is present.

### 5. PID commissioning

Build a closed-loop PID test setup and exercise the established interactions:

```text
Normal click         -> cycle PID tuning preset
Shift + FRONT click  -> capture acceptance evidence
Shift + other face   -> reset PID runtime
```

Confirm that captured evidence reflects the current authoritative commissioning/topology state rather than changing later with HUD refreshes.

### 6. Run-history comparison

Capture at least three PID acceptance runs with different conditions. Check that:

- each capture receives a monotonic local sequence number;
- latest-versus-previous comparison reports a sensible trend;
- score and topology-issue deltas match the captured runs;
- history remains bounded rather than growing forever.

Alpha 1.0.20 intentionally keeps this evidence **transient**. It is not yet a persistent laboratory notebook across world unload/reload. Losing captured run history after unloading is therefore a known Alpha 1.0.20 limitation, not by itself a bug.

### 7. Jade observer boundary

Inspect RSE devices with Jade enabled. Jade should present server-backed engineering information but must not change controller state, create acceptance captures, define sampling cadence, or alter network physics merely because the HUD is visible.

### 8. Visual/mechatronics pass

Exercise Servo Actuator, Pneumatic Cylinder, and Pneumatic Proportional Valve visuals. Report missing models/textures, broken animation state, or a visible state that clearly disagrees with the synchronized simulation state.

### 9. Save/reload and clean shutdown

Save, exit, reopen the test world, and repeat a small measurement/control setup. Report crashes, corrupted block state, missing registered content, or behavior that changes unexpectedly after reload. Remember that Alpha 1.0.20 captured run history itself is intentionally transient.

## What makes a useful bug report

Please include:

- **RSE version:** `1.0.20-alpha` and, if available, the jar SHA-256;
- **Minecraft / NeoForge / Java versions**;
- **required dependency versions** and any additional mods installed;
- **single-player or server/client**;
- **exact reproduction steps** from a clean world when possible;
- **expected behavior**;
- **actual behavior**;
- whether it reproduces after restarting the game;
- `latest.log`, relevant crash report, and screenshots/video when they materially show the problem.

For engineering-behavior bugs, also describe the physical topology: block names, orientation/faces used, signal/domain, and the smallest setup that still reproduces the issue.

## Suggested severity labels

- **Blocker** — prevents startup, world loading, or normal testing on the supported baseline.
- **High** — corrupts state, breaks a core engineering contract, or produces consistently wrong authoritative behavior.
- **Medium** — functional defect with a practical workaround or limited scope.
- **Low** — visual, wording, usability, or polish issue that does not change authoritative behavior.

## Important Alpha 1.0.20 boundaries

The following are intentional architecture boundaries and should be preserved while fixing bugs:

- vanilla-compatible world-facing redstone stays `0..15`;
- high-cardinality runtime diagnostics stay out of BlockState;
- UI, Jade, and GeckoLib remain downstream observers;
- measurement sampling is simulation-owned, not HUD/render-owned;
- domain crossing requires explicit converters/transducers/interfaces;
- captured acceptance history is bounded and explicit-player-owned;
- Alpha 1.0.20 run history is transient by design.

## Release-candidate gate

The alpha jar should be considered ready for testers only after the exact release commit passes the repository verification workflow, including verifier syntax, source/resource audits, Java 21 compilation, Gradle tests, NeoForge GameTests, clean build, checksum generation, and artifact upload.
