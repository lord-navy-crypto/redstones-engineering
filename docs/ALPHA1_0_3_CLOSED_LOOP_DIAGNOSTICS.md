# RSE Alpha 1.0.3 — Closed-Loop Systems & Diagnostics

## Engineering focus

Alpha 1.0.3 is an integrity/refinement milestone: it closes gaps between the intended engineering behavior and the actual source implementation while preserving the vanilla-facing 0–15 redstone boundary.

### PID controller

Ports:

- `BACK` — setpoint, `0..15`
- `LEFT` — measured process value, `0..15`
- `FRONT` — controller output, `0..15`
- `RIGHT` — inhibit
- `UP` — mode select: `0=AUTO`, `>0=MANUAL`
- `DOWN` — manual output, `0..15`

Alpha 1.0.3 adds/strengthens:

- external Manual/Auto operation;
- bounded output (`0..15`);
- integral anti-windup;
- filtered derivative state;
- one-signal-unit control deadband;
- controller-bias-based bumpless Manual→Auto transfer;
- step-response rise/settling/overshoot diagnostics.

### Servo actuator

Ports:

- `BACK` — position or velocity command;
- `UP` — mode select: `0=POSITION`, `>0=VELOCITY`;
- `RIGHT` — brake.

Velocity mode uses `7=stop`, values below 7 for reverse command and values above 7 for forward command. Applied velocity changes at a bounded rate. Position is soft-limited to the engineering range `0..15`, and attempted over-travel is counted.

### Digital data bus

The resolver now distinguishes:

- physical `driverCount`;
- number of distinct driven values;
- contention frames;
- conflicting-value frames;
- same-value multi-driver contention;
- interarrival/activity diagnostics.

Multiple drivers with different values invalidate the bus value. Multiple drivers carrying the same value can remain readable but are still recorded as a contention condition.

### Radio receiver

Payload and link quality remain separate concepts. The receiver accumulates:

- valid frames;
- undecodable frames;
- same-channel collisions;
- dropouts after a previously valid sample;
- operator/channel handoffs;
- current link quality and interference/obstacle-derived noise strength.

The world-facing decoded payload remains `0..15`.

### Pneumatics

Alpha 1.0.3 includes:

- Pneumatic Proportional Valve;
- Pneumatic Relief Valve;
- Pneumatic Cylinder;
- network recognition of those components;
- proportional pressure limiting;
- relief-trip diagnostics;
- finite actuator behavior.

The current network uses an internal pressure scale of roughly `0..100`, while redstone command/control interfaces remain `0..15`.

### Operations / IOE monitoring

The Operations Monitor measures throughput, utilization, cycle time, downtime and queue/WIP proxies. Alpha 1.0.3 adds derived state classification:

- `NOMINAL`
- `CONGESTED`
- `NOISY`
- `UNSTABLE`
- `OVERLOADED`
- `SAFETY_LIMITED`
- `FAILED`

It also records starvation, blocked/fault proxies, high-queue-running time, longest downtime, queue variation and run-state transitions. These are diagnostic classifications only; the monitor does not automatically optimize or override the plant.

## Design principle

**Configuration → measurement → diagnostics → non-ideal behavior → explicit control ports → optimization.**

RSE intentionally keeps high-cardinality runtime measurements outside BlockState, and it does not replace normal redstone with a separate incompatible power system.

## Validation contract

Alpha 1.0.3 is checked by `tools/rse_alpha103_closed_loop_verify.py`, the broader RSE audits, JDK 21 `compileJava`, and a clean Gradle build. Interactive `runClient` validation remains a local Minecraft test rather than a headless CI step.
