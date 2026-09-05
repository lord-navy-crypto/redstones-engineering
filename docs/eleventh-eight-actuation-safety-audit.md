# Eleventh Eight — Actuation / Safety Integrity

Acceptance scope:

1. Pneumatic Proportional Valve
2. Pneumatic Relief Valve
3. Pneumatic Cylinder
4. Electromagnet
5. Permanent Magnet
6. Induction Coil
7. Magnetic Field Sensor
8. Magnetic Gradient Meter

Primary audit risks discovered before implementation:

- the pneumatic cylinder had no EngineeringPort contract, no Inspector projection, no removal cleanup, and continued scheduled polling after reaching its target;
- advanced pneumatic valves had real physical ports but opened the common Inspector as `UNKNOWN`;
- electromagnets accepted six-face Copper drive without declaring those physical inputs;
- magnetic sources and observers lacked a truthful distinction between free-space field behavior and wired adjacency ports;
- the induction coil converted field changes into Copper output but did not expose its cross-domain boundary or synchronized runtime value;
- all five magnetic devices were outside the common Field Device Inspector.

Acceptance contract:

- proportional pressure flow is BACK to FRONT and the opening command is REDSTONE on UP;
- the relief valve clamps its configured pressure and counts one event per overpressure episode;
- the cylinder consumes pneumatic pressure at BACK, publishes redstone position feedback at FRONT, stops polling when settled, and clears runtime on removal;
- an electromagnet is a six-face Copper load whose magnetic result is a free-space scalar field;
- permanent magnets, field sensors, and gradient meters do not invent wired ports for free-space behavior;
- induction is an explicit IRON_MAGNETIC measurement to COPPER conversion with transient output outside BlockState;
- all eight devices have authoritative Inspector projections, and eight dedicated GameTests plus the eleventh verifier gate the batch.
