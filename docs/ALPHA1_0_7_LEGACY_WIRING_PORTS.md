# RSE Alpha 1.0.7 — Legacy Wiring & Port Diagnostics

This milestone renovates early transmission code before adding more machines.

## Port contract

- Passive cable/trace: no fixed input/output; connected faces are bidirectional transmission ports.
- Directional processor: **BACK = INPUT**, **FRONT = OUTPUT** unless the device explicitly documents an auxiliary side.
- Signal Probe: FRONT/FACING looks at the measured target; the opposite face is the instrumentation-bus connection.
- Redstone Cable Terminal: one face is Vanilla redstone, the opposite face is insulated 0–15 cable. Mode changes logical signal direction, not physical port placement.

## Transmission domains

RSE keeps physically different engineering domains separate:

- INSULATED_REDSTONE — bounded Minecraft-compatible 0..15 signal transport.
- COPPER — simplified electrical/voltage network.
- LAPIS_PRECISION — precision continuous-like signal domain.
- QUARTZ_TIMING — timing/clock domain.
- INSTRUMENT_BUS — measurement channel transport; carries probe observations, not redstone power.
- OPTICAL and AMETHYST remain separate physical domains.

Different media touching each other are **not** automatically electrically compatible. Cross-domain conversion must use an explicit terminal/transducer/converter. Port diagnostics report `DOMAIN_MISMATCH` instead of silently suggesting that two different media form one network.

## Instrument Cable renovation

The original Instrument Cable was a plain Block and InstrumentNetwork walked every adjacent cable cube. Alpha 1.0.7 upgrades it to ConnectedCableBlock:

- six-direction physical topology;
- multi-drop branching is allowed (up to all six faces);
- visual/runtime BlockState carries only topology, not measurement values;
- InstrumentNetwork traverses only edges that are connected at both cable ends;
- probes connect on their instrumentation/back side only;
- Oscilloscope and Logic Analyzer remain compatible bus instruments.

## Quick in-game port test

Right-click the renovated line/cable to show engineering diagnostics.

Examples:

`Insulated Redstone Cable | domain=INSULATED_REDSTONE | links=N,S | signal=12/15`

`Instrument Bus Cable | domain=INSTRUMENT_BUS | links=E,W,U | ports=3`

`Lapis Precision Trace | value=0.75 | domain=LAPIS_PRECISION | links=N,E | vertical=ISOLATED`

If a different medium directly touches a line, the diagnostic adds e.g. `DOMAIN_MISMATCH=E:COPPER`.

## Why no new third-party dependency in this milestone

NeoForge/Minecraft already expose the block-state, direction, neighbor and graph primitives required for this refactor. A dependency would not remove the hard part—the domain/port contract—and would add installation/version coupling. Third-party libraries remain welcome later when they materially improve UI/configuration, rendering, compatibility abstraction or network visualization.
