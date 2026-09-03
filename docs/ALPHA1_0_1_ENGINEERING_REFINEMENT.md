# RSE Alpha 1.0.1 — Engineering Refinement

This refinement strengthens existing Alpha 1.0 devices instead of adding another domain.

## Design basis

- University of Michigan EECS 461: digital I/O, A/D conversion, PWM, timing/frequency analysis, CAN, sensors, actuators, feedback and networking delay.
- Carnegie Mellon 18-474: event/clock sampling, PWM, PID, state feedback, state estimation, setpoint control, trajectory tracking, motors and encoders.
- UIUC ECE 110: measurement, modeling, analysis and design; sensors, motors, feedback and power/information systems.
- UW–Madison Robotics, Controls and Sensing: systems evolving over time, sensing, actuation, control, cyber-physical systems and precision instrumentation.

The gameplay interpretation is: useful engineering blocks should be configurable, measurable, diagnosable, composable and imperfect in controlled ways.

## Refined devices

### PID Controller
- Four compact tuning presets: P-GENTLE, PI, PID-BALANCED, PID-AGGRESSIVE.
- Anti-windup: integral state is not accumulated farther into output saturation.
- Low-pass derivative state reduces tick-to-tick derivative chatter.
- RIGHT port is INHIBIT; output becomes zero without destroying tuning state.
- Diagnostics: error, integral, filtered derivative, saturation count.
- Shift-right-click clears runtime controller state.

### Watchdog
- Selectable timeout: 20 / 40 / 80 / 160 ticks.
- Counts input transitions and timeout events.
- Shift-right-click resets diagnostics.

### 2oo3 Redundant Voter
- Median of BACK / LEFT / RIGHT remains the robust analog output.
- Selectable disagreement tolerance: 0 / 1 / 2 / 4 strength units.
- Reports spread, degraded state, maximum spread and disagreement count.

### Fault Latch
- Selectable trip threshold: 1 / 4 / 8 / 12.
- BACK is monitored fault input.
- RIGHT is remote reset.
- Tracks trip/reset events.

### Radio Link
- Major semantic correction: radio now transports the actual 0..15 payload.
- Distance changes quality, not decoded data value.
- Multiple in-range transmitters on one channel create COLLISION and no valid decoded payload.
- Receiver reports payload, quality and number of drivers.

### Free-Space Optical Link
- Receiver is channel-selective.
- Beam must enter the receiver's back face (alignment requirement).
- Line-of-sight and finite power budget remain required.
- Receiver reports optical quality as well as power.

### Pneumatics
- Compressor command is analog: redstone 0..15 maps to pressure 0..100.
- Reservoir no longer instantaneously becomes full line pressure; it charges with a finite rate and leaks slowly.
- Existing regulator remains part of the pneumatic network solver.

### Molecular Cloud Receiver
- Four sensitivity presets.
- A small sensor time constant makes output approach concentration rather than teleport instantly.
- Peak concentration is retained for diagnostics until reset.

### Operations Monitor
- Existing cycle count/utilization retained.
- Adds last / exponentially smoothed average / maximum cycle time.
- Last 60-second utilization is retained alongside the current partial-window value.

### Servo Actuator
- Directional command rather than `getBestNeighborSignal` from every side.
- BACK = target position, RIGHT = inhibit.
- Selectable slew step 1 / 2 / 3 every 2 ticks.
- Diagnostics: position, target, velocity proxy, position error and inhibit state.
- Shift-right-click homes the abstract servo state to zero.

### Digital Regenerator
- Selectable minimum accepted serial quality: 20 / 40 / 60%.
- Invalid/low-quality frames are rejected rather than magically restored.
- Successful regeneration restores quality only after the decision threshold is met.

### Sculk Vibration Interface
- Keeps Vanilla/Calibrated Sculk 1..15 event code behavior.
- Adds event count, last event code and transition count.
- Does not replace Vanilla GameEvent propagation.

## Engineering philosophy reinforced

1. Configuration is small and visible; high-cardinality runtime state stays outside BlockState.
2. Instrumentation is a first-class capability, not decorative text.
3. Communication separates payload from link quality.
4. Reliability blocks diagnose disagreement/timeouts rather than silently hiding faults.
5. Controllers include non-ideal behavior such as saturation, response rate and inhibition.
6. Vanilla redstone remains the cheap/simple control layer.

## Static validation

The merged Alpha 8.0.2 + Alpha 1.0 + Alpha 1.0.1 tree still passes:
- rse_redstone_verify.py
- rse_full_audit.py
- rse_alpha10_verify.py

This is not a substitute for `./gradlew compileJava`, `./gradlew clean build`, `./gradlew runClient`, new-world placement testing and multiplayer/performance testing on the target NeoForge environment.
