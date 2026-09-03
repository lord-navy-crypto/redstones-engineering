# RSE Alpha 1.0.3 — Closed-Loop Systems & Diagnostics

## Engineering focus
- PID: external Manual/Auto ports, bumpless transfer, output limits, deadband, step-response diagnostics.
- Servo: Position and Velocity modes, acceleration and soft-limit diagnostics.
- Digital bus: distinct driver count versus distinct values, conflict and same-value multi-driver diagnostics.
- Radio: accumulated valid/undecodable/collision statistics.
- Pneumatics: proportional valve, relief valve, and finite-speed cylinder actuator.
- Operations: starvation/blocking proxies, longest downtime, high-queue-running and empty-queue-running metrics.

Design principle: configuration, measurement, diagnostics, non-ideal behavior, and explicit ports before adding more domains.
