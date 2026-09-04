# Alpha 1.0.15 — Metrology Rollout & Calibration

Alpha 1.0.15 turns the measurement-quality architecture introduced in 1.0.14 into shared infrastructure rather than a Tank Level Sensor special case.

## Shared measurement pipeline

Measurement devices use the same conceptual pipeline:

```text
physical/reference state
        ↓
sensor conditioning / finite resolution
        ↓
measured reading
        ↓
MetrologyTracker rolling history
        ↓
reading + repeatability + bias + drift + noise + resolution
        + saturation + sample age + measurement uncertainty proxy
```

The world-facing redstone boundary remains 0..15. Higher-cardinality diagnostics stay in transient level-scoped state.

`MetrologySupport` provides the common sampling, snapshot, bounded-conditioning, Engineering Port quality mapping and compact diagnostic formatting used by sensors and meters.

## Rollout coverage

- Engineering Light Sensor — repeated conditioned 0..15 light measurement.
- Entity Density Sensor — retains the real entity count long enough to distinguish a true 15 from an over-range reading and report `SATURATED`.
- Tank Level Sensor — now consumes shared support while preserving the existing public measurement snapshot API.
- Servo Position Sensor — compares sensor feedback to authoritative servo simulation position; the sensor never writes servo physics.
- Copper Circuit Meter — samples voltage on a fixed schedule instead of allowing Jade/HUD query rate to become the sampling clock.
- Pneumatic Flow Meter — samples its 0..100 flow proxy on a fixed schedule and detects physical over-range from pressure drop before display clamping.

## Calibration workflow

The Calibration Module now has three engineering roles tied to physical sides:

```text
LEFT  : REFERENCE INPUT  (known reference)
BACK  : OBSERVED INPUT   (instrument under test)
FRONT : CALIBRATED OUTPUT
```

The original five transfer profiles remain available. The module continuously compares its corrected reading against the independent reference and records signed residual bias separately from the measurement uncertainty proxy.

This distinction matters: a reading can have a repeatable signed bias while uncertainty expresses confidence/spread; the two are not interchangeable.

## Engineering Port projection

`MeasurementQuality` is richer than `PortQuality`. Explicit hard states are preserved:

- `SATURATED → SATURATED`
- `STALE → STALE`
- `INVALID → FAULT`

`GOOD` and `DEGRADED` remain valid Engineering Port observations because `PortQuality` has no dedicated degraded-measurement state. Detailed degradation remains visible through metrology diagnostics rather than being mislabeled as a hard fault.

## Sampling ownership

UI and integration consumers are observers. Scheduled block ticks own the sampling history for meters that otherwise have no output tick. This prevents HUD/Jade polling frequency from changing repeatability/noise/drift statistics.

## Verification

Alpha 1.0.15 adds static rollout gates plus executable GameTests for shared quality-state projection and calibration residual semantics. All previous metrology, topology, copper, directional-I/O and visualization gates remain required.
