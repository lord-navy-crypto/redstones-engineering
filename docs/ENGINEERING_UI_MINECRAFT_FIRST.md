# Minecraft-First Engineering UI Contract

This document governs block-level RSE HMIs. BetterBoard and Engineering Lab are reference designs, not templates to copy wholesale.

## Primary rule

**Minecraft block first, engineering instrument second, desktop simulator never.**

The world remains the main system diagram. Wiring, placement, visible motion, pressure paths, signal routes, lamps, and actuators should explain what happened spatially. A block HMI explains the block's own state, model, parameters, evidence, and diagnostics; it must not replace the world with a full desktop workspace.

## Three UI tiers

### BLOCK

Use for passive media, cables, junctions, traces, pipes, fibers, and other topology-first blocks.

Appropriate:
- current value / quality
- declared ports and physical route
- topology and medium identity
- current evidence and health
- concise role explanation

Do not add:
- parameter sweep
- percentage presets
- response experiment workflow
- client-retained waveform history
- project/workspace concepts

If time-series evidence is needed, the player should place/use an analyzer, oscilloscope, monitor, or Diagnostic Tablet.

### DEVICE

Use for active or configurable blocks whose main interaction is setting a bounded parameter and reading a result.

Appropriate:
- exact bounded target entry
- fine +/- adjustment
- Min/Max when meaningful
- parameter units/range
- equation or transfer relation
- live input/output/readback
- diagnosis + next action
- evidence-quality boundary

Do not automatically add:
- 25/50/75 presets for categorical modes/channels
- parameter sweep merely because a parameter is numeric
- response plots when changing the parameter does not create a meaningful measured response

Examples: sensors, relays, threshold devices, radio channel devices, optical source/filter settings, watchdog/voter/latch configuration, operations readback blocks.

### LAB

Reserve for blocks with meaningful dynamic, sampled-data, frequency-domain, feedback, or actuator behavior.

Appropriate in addition to DEVICE tools:
- controlled parameter sweep
- real synchronized response capture
- response curve
- settling/overshoot/tracking-error metrics when the server already owns them
- parameter comparison when the comparison has engineering meaning

Examples: PID, signal conditioner/processor, clock/timing devices, sample-and-hold, tuned resonator/filter, induction coil, compressor/regulator/proportional valve/cylinder, servo, analyzers.

## Parameter semantics

A numeric value is not automatically an experiment variable.

Categorical values such as:
- mode
- channel
- profile index
- routing choice
- trigger type

receive exact selection and fine stepping only.

Fraction presets (25/50/75) are reserved for genuinely ordered numeric ranges.

Sweep is allowed only when all of these are true:
1. the device is LAB tier;
2. the parameter explicitly declares sweep as meaningful;
3. a real server-synchronized response quantity exists;
4. each sweep point uses the existing authoritative server action;
5. invalid/stale response evidence is rendered as a gap, never fabricated or interpolated.

## Authority boundary

The client HMI may:
- render synchronized state;
- retain short display-only samples for a DEVICE/LAB visualization;
- issue existing menu-button actions;
- present formulas and explanatory text.

The client HMI may not:
- solve the physical network;
- write world/block state directly;
- create synthetic evidence;
- reinterpret NO_SIGNAL as zero;
- turn retained numeric history into valid current evidence;
- create timing edges or measurement events.

## Global tools are different

The Redstone Encyclopedia, Diagnostic Tablet, and RSE Diagnostics are not single-block HMIs. Their broader information architecture is intentionally exempt from the BLOCK / DEVICE / LAB page-density rule.

## Design review checklist

Before adding a UI feature ask:
1. Is this owned by the block, or should it exist in the world/analyzer?
2. Is the control a real server-owned parameter?
3. Is the value continuous/ordered or categorical?
4. Does changing it produce a meaningful response worth plotting?
5. Can the result be measured from synchronized evidence rather than predicted on the client?
6. Would this still feel like operating a Minecraft engineering block rather than opening a desktop simulation program?

If the answer to 4 or 5 is no, do not add Sweep.
