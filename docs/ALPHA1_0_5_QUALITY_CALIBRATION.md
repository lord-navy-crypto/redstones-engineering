# RSE Alpha 1.0.5 — Quality, Calibration & Regression Hardening

Alpha 1.0.5 strengthens **measurement quality, capture integrity, save/reload consistency, and repository-level regression detection**. It intentionally prioritizes correctness and diagnosability over adding new engineering domains.

## 1. Measurement quality philosophy

RSE keeps the vanilla redstone `0..15` scale as the world-facing boundary. Alpha 1.0.5 adds richer diagnostics around that boundary without changing the boundary itself.

The distinction is:

```text
raw signal     = what the Minecraft/RSE circuit actually carries
calibrated read = what an instrument displays after a small measurement correction
```

A calibration operation must never silently modify the circuit under test.

## 2. Signal Analyzer rolling window

The Signal Analyzer now keeps a transient 16-sample rolling window in `RuntimeIntStore`.

With the current two-tick sampling period:

```text
window span ≈ 16 samples × 2 ticks/sample = 32 ticks
```

The analyzer reports both lifetime and recent-window behavior.

### Lifetime diagnostics

- total sample count;
- lifetime minimum/maximum;
- number of value changes;
- rising/falling changes;
- last delta;
- maximum absolute delta;
- time since last change;
- mode switches;
- calibration switches.

### Rolling diagnostics

For recent samples `x_1 ... x_n`, `n <= 16`:

```text
average = sum(x_i) / n
p2p     = max(x_i) - min(x_i)
```

Mean absolute step:

```text
meanStep = sum(|x_i - x_(i-1)|) / (n - 1)
```

This is deliberately called a **variation/activity metric**, not a universal signal-quality score. A rapidly changing signal may be perfectly valid.

The analyzer also reports `sampleAge`, allowing stale measurements to be distinguished from fresh ones.

## 3. Stability classification

The rolling window is classified for quick inspection:

```text
WARMUP         fewer than 4 recent samples
STEADY         p2p = 0 and meanStep = 0
STABLE         p2p <= 1 and meanStep <= 0.50
DYNAMIC        p2p <= 5 and meanStep <= 2.00
HIGH_VARIATION otherwise
```

These classes describe recent signal behavior, not whether the signal is "good" or "bad".

## 4. Display calibration

Signal Analyzer has a small persistent calibration configuration encoded as five states:

```text
encoded 0 1 2 3 4
 offset -2 -1 0 +1 +2
```

Displayed calibrated reading:

```text
S_display = clamp(S_raw + offset, 0, 15)
```

The range is intentionally small because it represents instrument correction, not signal conditioning.

### Critical INLINE invariant

In INLINE mode:

```text
S_out = S_raw
```

**not**:

```text
S_out = S_display
```

Therefore changing calibration can change the displayed reading while the downstream circuit remains unchanged.

Interaction contract:

```text
Right-click UP:        TAP <-> INLINE
Shift + UP:            reset transient measurement statistics
Right-click DOWN:      cycle calibration -2..+2
Shift + other face:    six-side raw survey
Other normal click:    detailed raw/calibrated diagnostics
```

## 5. Instrument-network structural integrity

`InstrumentNetwork` now reports more than cable/probe counts.

Additional topology diagnostics:

- `validChannels`;
- `activeChannels`;
- `duplicateChannels`;
- `duplicateProbes`;
- maximum cable traversal depth;
- maximum probe depth;
- structural integrity state.

Integrity classification precedence:

```text
TRUNCATED  network scan hit the bounded traversal limit
AMBIGUOUS  one or more channels have duplicate probes
NO_PROBES  bounded network contains no probes
OK         bounded network with no duplicate channels and at least one probe
```

A truncated scan takes precedence because the instrument cannot claim a complete structural view of the network.

## 6. Oscilloscope capture quality

The Oscilloscope still samples every two ticks and keeps a 32-sample capture history.

Alpha 1.0.5 adds:

- valid-sample count;
- capture coverage percentage;
- average signal value;
- mean absolute sample-to-sample step;
- period in samples **and ticks**;
- cursor delta in samples **and ticks**;
- explicit capture-quality state.

Coverage:

```text
coverage = valid_samples / captured_samples
```

Capture states:

```text
NO_DATA        no capture
WARMUP         100% valid but fewer than 8 samples
COMPLETE       100% valid and at least 8 samples
PARTIAL        >=75% valid
POOR_COVERAGE  <75% valid
```

Coverage describes acquisition completeness; it does not judge whether signal variation is desirable.

## 7. Logic Analyzer capture quality

Logic Analyzer samples every tick.

Alpha 1.0.5 adds:

- valid-sample coverage per channel;
- total edge count;
- transition rate;
- capture-quality classification;
- cursor timing in ticks.

For a channel:

```text
transitionRate ~= edge_count / (valid_samples - 1)
```

The value is bounded to 100% for display.

## 8. Save/reload trigger integrity

Earlier versions persisted trigger mode/state but did not persist all post-trigger progress counters.

Alpha 1.0.5 persists:

```text
Oscilloscope: samplesSinceTrigger
Logic Analyzer: postTriggerSamples
```

This prevents a world save/reload during a triggered capture from silently extending or resetting the post-trigger collection phase.

## 9. Repository-wide source quality audit

`tools/rse_source_quality_audit.py` adds structural checks that are independent of a single milestone:

- Java package declaration matches source path;
- Java brace-balance smoke check;
- trailing-whitespace detection;
- IntegerProperty range/cardinality guard;
- all JSON resources are non-empty and parse;
- case-insensitive resource-name collision detection;
- local `redstoneengineering:block/...` and `item/...` model references resolve;
- placeable blocks with a block model/blockstate have an item model;
- temporary compiler/copy-like root artifacts are rejected.

This audit is intentionally conservative. Real Java correctness remains the responsibility of `compileJava` and Gradle build.

## 10. Deterministic reference-model tests

`tools/rse_reference_model_tests.py` verifies small engineering equations independently of Java implementation details:

- clamping to `0..15`;
- calibration bounds;
- calibration never changes INLINE raw output;
- rolling average/p2p/mean-step examples;
- stability classification boundaries;
- topology-integrity precedence;
- Scope/Logic sample-to-tick timebase contracts.

These tests complement source verifiers: source verifiers confirm implementation structure; reference tests confirm intended mathematics.

## 11. CI quality ladder

Alpha 1.0.5 CI runs:

```text
Python verifier syntax
        ↓
Repository metadata/hygiene
        ↓
Source/resource quality audit
        ↓
Reference-model math tests
        ↓
All historical Alpha regression verifiers
        ↓
Alpha 1.0.5 verifier
        ↓
Java 21 compileJava
        ↓
Gradle test
        ↓
Clean Gradle build
        ↓
SHA-256 checksums
        ↓
Verified JAR artifact upload
```

A public release candidate still requires local `runClient` validation because headless CI cannot verify interactive Minecraft behavior.

## 12. Preserved engineering invariants

1. World-facing redstone remains `0..15`.
2. Analyzer TAP remains non-invasive.
3. Analyzer INLINE has explicit TEST/OUT topology.
4. Display calibration never changes INLINE output.
5. High-cardinality statistics remain outside BlockState.
6. Direction-aware measurement remains mandatory.
7. Duplicate probe channels remain ambiguous rather than last-writer-wins.
8. Instrument scans remain bounded.
9. Pneumatic Cylinder remains a terminal pneumatic actuator with explicit redstone feedback.
10. Alpha 1.0.3 and 1.0.4 regression verifiers remain in CI.
