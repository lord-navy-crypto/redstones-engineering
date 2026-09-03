# RSE v0.1.0-alpha.6 — Engineering Depth Pass

## Goal

Alpha.6 turns the seven non-redstone domains from demonstrations into engineering systems with observable imperfections, measurable behavior, and design trade-offs. The original 14 Redstone instruments remain Redstone-only.

RSE still follows the world-facing rule:

> Vanilla-first. Real engineering principles are used as reduced-order models; Minecraft remains the world and interaction language.

This is deliberately **not** a SPICE, Maxwell-field, or molecular thermal solver.

## Why this kind of rigor

The design follows the same educational pattern emphasized by engineering programs and courses used in RSE research:

- Michigan Robotics describes rigor as engagement with mathematical models, physical principles, algorithmic reasoning, model limitations, and application to integrated systems constrained by hardware and data.
- Michigan EECS 461 moves through analog interfacing, measurement, sensors, actuators, PWM, feedback, timing/frequency analysis, and rapid prototyping.
- UIUC ECE 110 emphasizes measurement, modeling, analysis, design, sensors, motors, feedback, and a built final project.
- UIUC ECE 385 emphasizes design/build/debug, timing, hazards, clock domains, metastability/synchronization, testbenches, and system integration.
- CMU 18-349 and 18-474 emphasize real-time systems, implementation trade-offs, sampling, PWM, feedback, estimation, control, sensors, and actuators.
- AP Physics C: E&M provides the macroscopic physics boundary for the Copper/Iron path: circuits, capacitors, magnetic fields, and electromagnetic induction.

The project therefore treats difficulty as something the player can **observe, measure, model, debug, and improve**, not as hidden equation complexity.

## Domain map

### Redstone — discrete control, unchanged
Existing 14 instruments remain Redstone-specific:
Analyzer, Probe, Instrument Cable, Oscilloscope, Logic Analyzer, Conditioner, Calibration, Precision Filter, Sample & Hold, Edge Detector, Pulse Shaper, PWM, Signal Tap, Range Sensor.

### Lapis — precision continuous-like measurement
Foundation: Lapis Precision Source + Lapis Signal Line.

Alpha.6 adds:
- **Lapis Noise Source** — baseline plus bounded measurement noise.
- **Lapis Low-Pass Filter** — exponential moving-average reduced-order filter with selectable alpha.
- **Lapis Precision Meter** — explicit test-point measurement and validity reporting.

Engineering problems now visible:
- precision versus noise;
- smoothing versus response lag;
- conflicting sources invalidate a network instead of silently selecting one.

### Quartz — timing, synchronization, and stability
Foundation: Quartz Oscillator + Quartz Timing Trace.

Alpha.6 adds:
- **Quartz Laboratory Oscillator** — nominal clock with selectable tick jitter;
- **Quartz Clock Divider** — divide-by-2/4/8/16;
- **Quartz Phase Delay** — delayed event/phase path;
- **Quartz Stability Monitor** — measured period and error relative to nominal timing.

Engineering problems now visible:
- nominal period versus actual period;
- jitter;
- clock-domain conflicts;
- phase delay and synchronization.

Quartz is inspired by real quartz timing, but RSE uses Minecraft ticks rather than pretending to model MHz hardware.

### Amethyst — resonance and frequency-selective events
Foundation: Amethyst Resonator + Resonance Dust.

Alpha.6 adds:
- **Amethyst Frequency Filter** — passes one frequency channel with insertion loss;
- **Amethyst Tuned Resonator** — selectable natural frequency and Q-index; higher Q narrows the pass region;
- **Amethyst Spectrum Analyzer** — scans nearby resonance traffic and reports dominant frequency/active bands.

Engineering problems now visible:
- frequency selectivity;
- detuning;
- amplitude attenuation;
- resonance quality/selectivity;
- competing frequency sources.

This intentionally grows out of Minecraft's own calibrated sculk/vibration resonance vocabulary.

### Optical — light transmission, channels, and loss
Foundation: Glowglass Optical Fiber + Emitter + Receiver.

Alpha.6 adds:
- **Optical Power Meter** — explicit local intensity/channel measurement;
- **Optical 1x2 Splitter** — roughly halves optical power per branch (idealized 3 dB split concept);
- **Optical Channel Filter** — passes one optical channel with insertion loss;
- **Optical Attenuator** — adjustable loss element.

Engineering problems now visible:
- distance attenuation;
- splitter loss;
- channel conflicts;
- filtering and deliberate attenuation;
- power-budget thinking.

The current fiber is a Minecraft abstraction inspired by guided light. Full ray optics is deliberately out of scope.

### Copper — macroscopic electrical circuit domain
Foundation: Voltage Source + Copper Wire + Resistive Load.

Alpha.6 adds:
- **Copper Series Resistor** — simplified voltage-divider behavior using downstream equivalent load;
- **Copper Capacitor** — reduced-order RC charge/discharge response;
- **Copper Fuse** — current-rating protection that trips under estimated overload;
- **Copper Circuit Meter** — reports V, estimated equivalent R, I, and P.

Models:
- `I = V/R`
- `P = VI = V^2/R`
- voltage divider for a series resistor feeding a downstream equivalent load;
- capacitor response uses a discrete RC time-constant proxy rather than a continuous SPICE solver.

Engineering problems now visible:
- load dependence;
- voltage drop;
- current and power;
- transients;
- protection/overload.

### Iron / Magnetic — field and induction domain
Foundation: Iron Core + Electromagnet + Magnetic Field Sensor.

Alpha.6 adds:
- **Permanent Magnet** — adjustable field source with pole-facing metadata;
- **Induction Coil** — creates Copper-domain emf from changing local magnetic flux using a Faraday-inspired `|emf| ∝ N|ΔΦ/Δt|` reduced model;
- **Magnetic Gradient Meter** — finite-difference field gradient for locating field changes.

Engineering problems now visible:
- field strength versus distance;
- magnetic material/source distinction;
- changing flux rather than static field as the source of induction;
- measurement of field gradients.

The scalar 1/r² field approximation is explicitly a gameplay model, not a Biot-Savart or Maxwell solver.

### Thermal — physical state, heat capacity, and heat transfer
Foundation: Thermal Mass + Temperature Sensor.

Alpha.6 adds:
- **Thermal Mass heat-capacity index** — larger capacity responds more slowly to the same environment;
- **Electrical Thermal Heater** — Copper voltage feeds a reduced `P = V²/R` heating model;
- **Thermal Radiator** — passive heat removal toward ambient, never magical below-ambient refrigeration;
- **Thermal Calorimeter** — measures temperature change over a fixed interval and reports a relative `C·ΔT` heat index.

Engineering problems now visible:
- thermal inertia;
- heating power;
- conduction/environment coupling;
- passive cooling;
- temperature lag and measurement.

Thermal is intentionally a physical-state domain rather than a colored 'thermal wire'.

## Cross-domain couplings in alpha.6

Only couplings that have a clear physical meaning are introduced:

- Copper → Iron/Magnetic: Electromagnet
- Magnetic change → Copper: Induction Coil
- Copper → Thermal: Electrical Thermal Heater

General-purpose Redstone/Lapis/Quartz/Optical converters are still reserved for a later explicit transducer pass.

## Model honesty

Every alpha.6 model should be described with one of these labels:

1. **Minecraft-native rule** — e.g. Redstone 0–15 and Amethyst vibration-frequency vocabulary.
2. **Engineering reduced-order model** — e.g. Lapis EMA filter, RC proxy, optical loss, lumped heat capacity.
3. **Physics-inspired approximation** — e.g. scalar magnetic field falloff.

Never claim that index values are SI volts, amperes, tesla, kelvin, hertz, or watts unless a later calibration layer explicitly defines that mapping.

## Showcase value

The strongest demonstration is not the number of blocks. A useful demo should show a hypothesis, a disturbance/failure, a measurement, and a design improvement. Examples:

- Add Lapis noise, compare raw and filtered response, then quantify lag.
- Add Quartz jitter, measure period error, then divide or synchronize the clock.
- Tune Amethyst Q and show rejection of a nearby frequency.
- Split an optical signal and construct a power budget to keep both receivers above threshold.
- Increase Copper load, measure V/I/P, trip a fuse, then redesign resistance/rating.
- Move/change a magnetic source near an induction coil and observe generated Copper emf.
- Compare two Thermal Mass blocks with different heat-capacity indices under the same heater.

That structure demonstrates modeling, experimentation, debugging, iteration, and system thinking rather than feature accumulation.

## Authoritative references used for this pass

- University of Michigan Robotics — Why Robotics / course rigor: https://robotics.umich.edu/academics/undergraduate/why-robotics/
- University of Michigan EECS 461 Embedded Control: https://web.eecs.umich.edu/~jfr/embeddedctrls/
- UIUC ECE 110: https://courses.grainger.illinois.edu/ECE110/fa2026/
- UIUC ECE 385: https://courses.grainger.illinois.edu/ECE385/fa2026/
- CMU 18-349: https://courses.ece.cmu.edu/18349
- CMU 18-474: https://courses.ece.cmu.edu/18474
- College Board AP Physics C: E&M: https://apcentral.collegeboard.org/courses/ap-physics-c-electricity-and-magnetism
- NIST quartz timing/frequency resources: https://www.nist.gov/pml/time-and-frequency-division
- OpenStax University Physics Vol. 2, RC circuits and induction: https://openstax.org/details/books/university-physics-volume-2
- Minecraft official Trails & Tales / calibrated sculk and vibration resonance: https://www.minecraft.net/en-us/article/trails-tales-update-out-today-java
