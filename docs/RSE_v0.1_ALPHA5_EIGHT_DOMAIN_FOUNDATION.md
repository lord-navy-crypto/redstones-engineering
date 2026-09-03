# RSE v0.1.0-alpha.5 — Eight Domain Foundation

## Purpose

This pass expands **Redstone Systems Engineering** from a Redstone-only signal toolbox into an engineering-domain foundation while preserving Vanilla-first behavior.

The existing alpha.4 instruments and processors remain **Redstone-only**:

- Engineering Signal Analyzer
- Signal Probe
- Instrument Cable
- Oscilloscope
- Logic Analyzer
- Signal Conditioner
- Calibration Module
- Precision Filter
- Sample & Hold
- Edge Detector
- Pulse Shaper
- PWM Controller
- Signal Tap
- Range Sensor

No existing device has been silently generalized to understand the new domains.

## Eight domains

| Domain | RSE meaning | alpha.5 primitive behavior |
|---|---|---|
| Redstone | Vanilla discrete control, 0–15 + ticks | Existing 14 devices unchanged |
| Lapis | High-resolution continuous-like measurement | 0.00–1.00 in 0.01 simulation steps; network carries the strongest configured source |
| Quartz | Timing / clock domain | 2/4/8/16/32 tick oscillators and timing traces |
| Amethyst | Resonance / vibration-frequency events | Frequency channels 1–15; short resonance events |
| Optical | Light intensity + channel | Glowglass fiber carries intensity 0–15 and optical channel 0–15 |
| Copper | Simplified macroscopic electrical domain | Voltage level, cable loss, resistive-load diagnostics using V=IR and P=VI |
| Iron | Magnetic field domain | Copper-driven electromagnets, magnetizable iron cores, field sensor |
| Thermal | Physical temperature state | Thermal mass exchanges temperature with simple hot/cold Minecraft environments |

## Engineering model boundaries

### Redstone
Vanilla redstone remains the world-facing control language. The new domains do not connect to redstone dust unless a future explicit converter is introduced.

### Lapis
Lapis is a Minecraft-specific continuous-signal abstraction. Internally alpha.5 uses 101 states (0–100) so players see values from 0.00 to 1.00. This is intentionally higher resolution than redstone's 16 states, but it is not claimed to be physically continuous.

### Quartz
Quartz represents timing rather than amplitude. Oscillators create deterministic clocks using Minecraft ticks. This is inspired by real timing/clock engineering but remains in the Minecraft tick domain.

### Amethyst
Minecraft already associates amethyst with vibration resonance and calibrated vibration frequencies. Alpha.5 extends that idea into a dedicated resonance medium. Conflicting active frequencies on one connected resonance network deliberately collapse to an invalid/idle state rather than silently choosing one.

### Optical
Glass + glowstone forms a Minecraft fiber-optics material. The conceptual basis is total internal reflection in glass/plastic optical fibers. alpha.5 carries an intensity level and one optical channel. Multiplexing, splitting loss, wavelength-specific components, and optical/redstone conversion are deferred.

### Copper
Copper is the most reality-inspired domain, but alpha.5 intentionally stops before a SPICE/Kirchhoff network solver. It models:

- source voltage level: 0–15
- cable loss: one voltage level per eight cable steps
- resistive-load diagnostics: `I = V/R`, `P = VI`

The load currently does not feed back into network voltage. That is reserved for a later AP-E&M circuit pass.

### Iron / magnetic
Electromagnets read adjacent Copper voltage and convert it into a normalized magnetic-field strength. Iron cores can become magnetized near strong electromagnets. The field sensor uses a bounded inverse-distance-squared approximation for gameplay and diagnostics; it is not a full Maxwell/Biot–Savart field solver.

### Thermal
Temperature is treated as a **physical state**, not another colored wire. Thermal mass trends toward the temperature of nearby hot/cold blocks and neighboring thermal masses. A temperature sensor reads this state but does not yet convert it into Lapis or Redstone.

## New blocks

### Lapis
1. `lapis_signal_line`
2. `lapis_precision_source`

### Quartz
3. `quartz_timing_line`
4. `quartz_oscillator`

### Amethyst
5. `amethyst_resonance_dust`
6. `amethyst_resonator`

### Optical
7. `optical_fiber`
8. `optical_emitter`
9. `optical_receiver`

### Copper
10. `copper_wire`
11. `copper_voltage_source`
12. `copper_resistive_load`

### Iron / magnetic
13. `iron_core`
14. `electromagnet`
15. `magnetic_field_sensor`

### Thermal
16. `thermal_mass`
17. `temperature_sensor`

## Interaction conventions

- Lapis source: right-click increases by 0.05; Shift-right-click decreases by 0.05.
- Quartz oscillator: right-click cycles 2/4/8/16/32 tick periods.
- Amethyst resonator: right-click changes frequency; Shift-right-click emits a short pulse.
- Optical emitter: right-click changes intensity; Shift-right-click changes channel.
- Copper source: right-click raises voltage level; Shift-right-click lowers it.
- Electrical load: right-click changes resistance; Shift-right-click reads V/R/I/P without changing resistance.
- Iron core: strong adjacent electromagnets magnetize it; Shift-right-click demagnetizes it for testing.
- Sensors/lines: right-click provides temporary alpha diagnostic readout.

## Deliberately deferred

- Cross-domain converters: Redstone↔Lapis, Quartz triggers, optical transceivers into other domains, magnetic induction into Copper, temperature→Lapis.
- Formal per-domain analyzer instruments.
- Full Kirchhoff circuit solving, capacitance/RC, induction/Faraday-law generator, transformer behavior.
- Optical splitters/MUX/WDM and attenuation by material/channel.
- Sculk-event capture into Amethyst.
- Thermal conductivity tables, heat capacity per material, radiation/convection.
- Connection-aware cable models and polished GUIs.

These are deferred so alpha.5 establishes clean domain boundaries before coupling them.

## Engineering references used for this pass

- College Board, AP Physics C: Electricity and Magnetism — electric circuits, magnetic fields/electromagnetism, electromagnetic induction.
- OpenStax University Physics Vol. 2 — Faraday's law, RC circuits, temperature and heat transfer.
- OpenStax University Physics Vol. 3 — total internal reflection and fiber optics.
- Minecraft Trails & Tales documentation — calibrated sculk sensors, vibration frequency levels, amethyst vibration resonance.
- NeoForge 1.21.1 block documentation — scheduled block ticks and block update architecture.
