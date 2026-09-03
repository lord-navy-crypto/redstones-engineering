# RSE v0.1.0-alpha.8 — Sensors & Transducers

## Design thesis

Alpha 8 connects physical domains to signal domains without turning every physical quantity into another wire.

```text
PHYSICAL QUANTITY
       ↓
    SENSOR
       ↓
  TRANSDUCER
       ↓
LAPIS / REDSTONE / QUARTZ
```

The transmission kernel from alpha.7.3 remains frozen: surface traces stay horizontal, cable topology remains explicit, networks remain bounded to 128 nodes, and high-cardinality live measurements stay outside BlockState.

## Sensor model

The physical-to-Lapis transducers expose four measurement profiles:

| Profile | Sample period | Noise | Resolution | Latency |
|---|---:|---:|---:|---:|
| FAST | 2 ticks | ±3/100 | 2/100 | 0 samples |
| BALANCED | 4 ticks | ±2/100 | 1/100 | 1 sample |
| PRECISION | 8 ticks | ±1/100 | 1/100 | 1 sample |
| RUGGED | 6 ticks | ±1/100 | 5/100 | 1 sample |

This creates an explicit engineering trade-off: fast measurement is noisier, precision costs time, and rugged instrumentation sacrifices resolution.

## New devices

1. Lapis Temperature Transducer — Thermal → Lapis
2. Lapis Hall / Field Transducer — Iron magnetic field → Lapis
3. Lapis Optical Power Transducer — Optical intensity → Lapis
4. Lapis Voltage Transducer — Copper voltage → Lapis
5. Lapis Precision Range Sensor — distance → Lapis
6. Lapis → Redstone Quantizer — continuous-like 0..100 → vanilla 0..15
7. Redstone → Lapis Scaler — vanilla 0..15 → normalized 0..100
8. Quartz Triggered Lapis Sampler — rising clock edge samples and holds Lapis

## Port semantics

For directional alpha.8 devices:

```text
BACK  = measured/input domain
FRONT = output domain
LEFT  = Quartz trigger (sampler only)
```

Processors/transducers are intentionally not transparent network nodes. A transducer terminates one physical/signal domain and explicitly drives another.

## Engineering references used in design

- Michigan EECS 461 laboratory sequence: digital I/O, queued ADC, PWM, timing/frequency analysis, CAN, rapid prototyping.
- CMU 18-474: event/clock sampling, PWM, PID, state feedback/estimation, sensors and actuators.
- AP Physics C: E&M: circuits, capacitors, magnetic fields, induction; used as the intended macroscopic physics boundary for Copper/Iron.
