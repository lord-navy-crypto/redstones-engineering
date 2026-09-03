# Redstone Systems Engineering 0.1.0-alpha.4
## Second Engineering Pass — Instrumentation Revision

### The probe decision

RSE uses:

**six-direction physical installation + one explicit test point**

This is intentionally different from automatic six-side maximum selection.

AUTO strongest-neighbor measurement is useful as a survey/debug function, but
is not a trustworthy default measurement in a dense circuit because it may
select the wrong node.

### Measurement hierarchy

Tier 1 — Engineering Signal Analyzer
- one explicit adjacent node
- instantaneous 0..15 measurement
- six-direction placement
- Shift-right-click: six-side survey

Tier 2 — Signal Probe + Instrument Cable + Oscilloscope
- probe attaches to one explicit node
- probe channels A/B/C/D
- instrument cable is electrically separate from redstone
- oscilloscope records A and B
- duplicate probes on one channel are marked AMBIGUOUS

Tier 3 — Logic Analyzer
- four probe channels A/B/C/D
- configurable 1..15 logic threshold
- 32 samples
- per-channel rising/falling edge counts
- digital waveform history

### Reinforced original devices

Signal Conditioner:
- GAIN
- OFFSET
- CLAMP
- THRESHOLD
- DEADBAND

Sample & Hold:
- RISING
- FALLING
- BOTH
- reset port retained

PWM Controller:
- 4/8/16/32 tick period
- optional enable input on left port
- invert mode

Range Sensor:
- BLOCK / ENTITY / ANY
- 4 / 8 / 15 block range
- PROXIMITY
- DISTANCE
- THRESHOLD
- WINDOW

### Instrument network rules

1. Probe does not conduct redstone.
2. Instrument Cable does not conduct redstone.
3. Probe channel A-D is selected by right click.
4. Shift-right-click probe shows local measurement.
5. A scope/analyzer network should have at most one probe per channel.
6. Duplicate probes on a channel return AMBIGUOUS rather than silently
   selecting one.
7. Instrument cable graph traversal is bounded to 128 cable nodes.

### Architecture principle

Vanilla redstone remains the plant/world-facing signal language.
Instrumentation observes it without becoming part of it.
Signal processors enter the line only when they intentionally transform it.
