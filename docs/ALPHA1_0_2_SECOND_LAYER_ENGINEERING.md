# RSE Alpha 1.0.2 — Second-Layer Engineering Refinement

This release deepens existing systems instead of adding another physical domain.

## Instrumentation
- Oscilloscope: rising/falling/free trigger modes, selectable trigger channel/level, arm/hold behavior, two measurement cursors, min/max/peak-to-peak, rough period measurement.
- Logic Analyzer: selectable edge trigger/channel, arm/hold capture, cursors, rising/falling counts, duty-cycle measurement.

## Control / Mechatronics
- PID: passive setpoint-step response measurement (90% rise time, settling time, overshoot) in addition to existing anti-windup, derivative filtering and inhibit.
- Servo: command/trajectory count, maximum velocity proxy, settling time and accumulated travel. Position sensor exposes position, velocity, error and trajectory diagnostics.

## Digital Communications
- Serial link: frame count, period, quality, node count, interarrival time and utilization proxy.
- 8-bit bus: update count, node/driver count, interarrival activity and conflict visibility.

## Industrial Wireless
- Radio keeps payload separate from link quality.
- Same-channel transmitters collide.
- Adjacent channels create interference penalty.
- Distance, obstacle samples and deterministic fading reduce quality.
- Receiver reports quality, interference, obstacle count and latency proxy.

## Pneumatics
- New Pneumatic Isolation Valve.
- New Pneumatic Check Valve (BACK -> FRONT only).
- New Pneumatic Flow Meter measuring pressure drop and flow proxy.
- Network solver honors closed valves and check-valve direction.

## Operations / IOE
Explicit monitor ports:
- DOWN = machine RUN state
- UP = completed-cycle pulse
- horizontal = queue/WIP proxy 0..15

Metrics:
- throughput (cycles/min)
- utilization
- last/average/max cycle time
- accumulated downtime and downtime events
- queue current/average/max

## Engineering principles
- Runtime measurements stay outside BlockState.
- Instruments expose causes and diagnostics, not just outputs.
- Reliability/communications quality are measurable.
- No magic optimizer: operations metrics must be interpreted by the player.
