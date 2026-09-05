# Ninth Eight — Pneumatic Foundation / Flow Integrity

Acceptance scope:

1. Air Compressor
2. Pneumatic Pipe
3. Air Reservoir
4. Pressure Regulator
5. Pneumatic Receiver
6. Pneumatic Valve
7. Pneumatic Check Valve
8. Pneumatic Flow Meter

Primary audit risks discovered before implementation:

- pneumatic receivers inherit a false REDSTONE input contract even though BACK reads pneumatic pressure;
- directional valves/meters are discovered as six-way pneumatic junctions by the current network solver;
- terminal receivers can accidentally bridge otherwise separate pneumatic networks;
- compressor command/output faces are implicit rather than explicit;
- reservoir runtime has no removal cleanup;
- several pneumatic devices are invisible to EngineeringPort/Inspector diagnostics.

The batch must preserve server-authoritative pneumatic solving and gate all fixes with executable GameTests and a dedicated verifier.
