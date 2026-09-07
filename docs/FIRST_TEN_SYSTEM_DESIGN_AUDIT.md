# First Ten Existing-Block System Design Audit

This audit is the first batch in RSE's fixed-content deepening pass. The registered block count remains **127**. The goal is not to add content; it is to make every existing block earn a distinct engineering role, expose truthful evidence, and compose into a coherent measurement-to-event system.

## Batch rule

Every ten-block batch is reviewed in this order:

1. **Role** — what engineering decision does the block own?
2. **Non-overlap** — why is a neighboring block not a substitute?
3. **Trade-off** — what does the block gain and what does it give up?
4. **Failure / limitation** — how can the block become unusable, ambiguous, saturated, stale, or incomplete?
5. **Evidence** — what can the player inspect without changing the physics?
6. **Runtime contract** — which behavior must be executable in GameTests?

A block does not justify itself merely by having a different recipe, texture, parameter, or real-world name. It must create a distinct design decision inside RSE.

## System-level chain

The first ten blocks form one engineering chain rather than ten isolated gadgets:

```text
physical redstone node
       |
       +--> Signal Analyzer -------------------------- local direct measurement
       |
       +--> Signal Probe -> Instrument Cable -------- remote non-invasive acquisition
                              |        |
                              |        +--> Oscilloscope ---- analog/time interpretation
                              |        +--> Logic Analyzer --- digital/time interpretation
                              |
redstone process path: Signal Conditioner -> Calibration Module -> Precision Filter
                                                    |
                                                    v
                                           Sample & Hold -> Edge Detector
```

The diagram is not a mandatory build order. It shows responsibility boundaries. For example, calibration does not have to follow conditioning, and a scope can observe an unconditioned signal.

## 1. Signal Analyzer

**Role:** local direct redstone measurement with two deliberately different installation modes.

- **TAP** is a non-invasive measurement aperture.
- **INLINE** is a real TEST-IN to INLINE-OUT 0..15 redstone path.
- Display calibration is observer-side only and must never alter INLINE physical pass-through.

**Unique decision:** choose whether measurement should be observational or physically inserted into the signal path.

**Failure / limitation:** wrong facing or an open measurement aperture can leave the analyzer without a meaningful target; INLINE installation can change topology because it is an actual path.

**Evidence:** raw value, calibrated display value, rolling window, min/max, change counts, sample age, mode/configuration changes.

**Verdict:** **KEEP / already mature.** Do not turn it into a remote multi-channel bus instrument; that would erase the Probe/Cable/Scope roles.

## 2. Signal Probe

**Role:** non-invasive acquisition endpoint that converts one physical measurement location into one logical Instrument Bus channel A-D.

**Unique decision:** where to measure and which logical channel owns that measurement.

**Trade-off:** it does not drive vanilla redstone and cannot manipulate the measured circuit. Channel identity has to be coordinated across the bus.

**Failure / limitation:** duplicate probes on the same channel become ambiguous; an open aperture with no target is `NO_SIGNAL` rather than a confirmed zero measurement. A real zero-valued target remains a valid measurement.

**Evidence:** TEST/BUS face orientation, selected channel, sampled value, measurement validity.

**Verdict:** **KEEP / deepen validity semantics.**

## 3. Instrument Cable

**Role:** carry logical probe channels through an explicit bounded Instrument Bus topology; it carries measurement information, not redstone power.

**Unique decision:** route remote instrumentation independently of the circuit being measured.

**Trade-off:** bus integrity depends on topology and unique channel ownership. Shielding is a separate integrity dimension handled by shielded cable, not a free property of the plain cable.

**Failure / limitation:** duplicate channel ownership or a truncated bounded scan is a topology error. No probes is `NO_SIGNAL`, not a fabricated zero.

**Evidence:** physical connection mask/count, valid/active channels, duplicate ownership, bounded scan status, cable/probe depth, shielding coverage. Plain Instrument Cable now exposes a runtime EngineeringPortSnapshot instead of being topology-only.

**Verdict:** **KEEP / deepen runtime observability.**

## 4. Oscilloscope

**Role:** interpret Instrument Bus channels A/B as analog values over time.

**Unique decision:** waveform shape, amplitude evolution, timing, trigger and cursor measurements.

**Trade-off:** only two selected logical channels are captured, but each keeps richer analog history.

**Failure / limitation:** current bus failure must not be hidden by old samples in the retained capture buffer. Live port quality therefore comes from the current A/B bus scan; history remains history.

**Evidence:** bounded capture, channel coverage, trigger status, cursors, live A/B bus quality.

**Verdict:** **KEEP / separate live link health from retained waveform evidence.**

## 5. Logic Analyzer

**Role:** interpret Instrument Bus channels A-D as digital states over time using a configurable threshold.

**Unique decision:** thresholded timing relationships, edges, duty and multi-lane digital chronology.

**Trade-off:** four channels and digital timing density are gained by discarding analog amplitude detail above/below the threshold.

**Failure / limitation:** duplicate channel ownership or truncated topology invalidates live bus evidence. Historical captured transitions must not make a presently broken bus appear valid.

**Evidence:** four-lane capture, threshold, edge counts, trigger/cursor state, live four-channel bus quality.

**Verdict:** **KEEP / explicitly non-overlapping with Oscilloscope.**

## 6. Signal Conditioner

**Role:** real-time bounded transfer shaping of the live 0..15 signal.

Modes remain deliberately limited to:

- gain,
- offset,
- clamp,
- threshold,
- deadband.

**Unique decision:** change the transfer relationship for control usability.

**Non-overlap:** it is **not** a calibration instrument and does not compare against a known reference; it is **not** a time-domain filter.

**Failure / limitation:** gain/offset/clamp can hit the 0..15 physical boundary. That is now exposed as `SATURATED`. An intentional threshold HIGH or deadband hold is normal operation, not saturation.

**Evidence:** mode, parameter, input, output, active limiting state.

**Verdict:** **KEEP / strengthen truthful limiting diagnostics.**

## 7. Calibration Module

**Role:** establish reference-based measurement correction and validation.

- BACK = OBSERVED
- LEFT = REFERENCE
- FRONT = CALIBRATED

**Unique decision:** compare a corrected reading against an independent known reference and retain metrology evidence.

**Non-overlap:** transfer profiles alone do not justify this block. Its defining feature is residual/bias/repeatability/drift/noise/uncertainty-proxy evidence against REFERENCE. Signal Conditioner has no such reference path.

**Failure / limitation:** no measurement evidence, stale evidence, or saturated measurement must be visible in the CALIBRATED port quality. `DEGRADED` metrology remains usable evidence and is not silently promoted to a hard fault.

**Evidence:** MeasurementSnapshot quality, reading, bias, repeatability, drift, noise, sample age/count, uncertainty proxy.

**Verdict:** **KEEP / make metrology quality part of the output contract.**

## 8. Precision Filter

**Role:** dynamic slew/smoothing behavior.

**Unique decision:** how quickly output is allowed to approach a changing input.

**Non-overlap:** it does not own static gain, offset, threshold, clamp, or reference calibration.

**Trade-off:** reduced rate of change costs response delay. Temporary input/output lag is expected behavior, not automatically a fault.

**Evidence:** configured slew rate, live input, output, absolute lag, `SETTLING`/`SETTLED` state.

**Verdict:** **KEEP / expose dynamic response rather than inventing extra transfer modes.**

## 9. Sample & Hold

**Role:** cross from a continuously changing value into sampled-data behavior.

**Unique decision:** capture VALUE only on the configured rising/falling/both TRIGGER event, then deliberately retain the captured value.

**Trade-off:** deterministic held data costs freshness. A held value being old is not automatically wrong; the age is evidence for downstream engineering decisions.

**Failure / limitation:** reset has precedence over capture. Reload initialization must not fabricate a trigger edge.

**Evidence:** held value, trigger mode, trigger/reset ports, transient real-capture count, age of the most recent real capture.

**Verdict:** **KEEP / add capture chronology evidence.**

## 10. Edge Detector

**Role:** convert a level transition into an event pulse.

**Unique decision:** choose rising, falling or both edges and convert state change into a bounded pulse event.

**Non-overlap:** Sample & Hold stores a value at an event; Edge Detector creates the event indication itself. Pulse Shaper (outside this batch) owns pulse-width shaping, so Edge Detector should not grow into an arbitrary pulse generator.

**Failure / limitation:** initialization/reload must establish the current input state without inventing an edge. Runtime observation must not initialize state either.

**Evidence:** configured edge mode, current/previous state evidence, pulse remaining, real edge count, age of last real edge.

**Verdict:** **KEEP / deepen event chronology while preserving simple edge semantics.**

## Cross-block invariants

### Live state is not retained history

Oscilloscope and Logic Analyzer retain bounded captures. Their **current port quality** must come from the current Instrument Bus, so old healthy samples cannot mask a new disconnect or channel conflict.

### Zero is not the same as no signal

A real device at level 0 is valid data. An open acquisition aperture with no target is `NO_SIGNAL`. Diagnostics must preserve this distinction wherever the underlying model can determine it.

### Conditioning is not calibration

`Signal Conditioner` answers: **how should this live signal be transformed for use?**

`Calibration Module` answers: **how does this corrected measurement compare with an independent reference, and what evidence supports it?**

`Precision Filter` answers: **how quickly may the output move toward the input?**

None is a universal upgrade of the others.

### Expected dynamics are not faults

- Filter lag while converging is expected.
- A held sample becoming older is expected until the next trigger.
- Threshold output HIGH is expected.
- Deadband holding its previous output is expected.

Diagnostics should expose these states without calling them failures.

### Observer neutrality

UI, Jade and diagnostics may inspect runtime evidence but must not create trigger edges, captures, or physics state. Read-only diagnostic accessors use observer-neutral snapshots such as `RuntimeIntStore.peek(...)` where appropriate.

## Acceptance target

This batch is complete only when executable GameTests demonstrate the new distinctions in a live Minecraft server and all historical RSE tests remain green. The fixed-content rule remains in force: **127 registered blocks, no new engineering domain, no new block added by this audit.**
