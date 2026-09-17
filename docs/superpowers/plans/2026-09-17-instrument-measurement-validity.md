# Instrument Measurement Validity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve measurement validity from SignalProbe through InstrumentNetwork so an open/no-target probe cannot be reinterpreted as a valid zero-level measurement by oscilloscopes and other instruments.

**Architecture:** Keep the existing SignalProbe sampling API and InstrumentNetwork topology scan. Make InstrumentNetwork carry the probe's existing validity decision when recording a channel: a physically present zero remains 0, while NO_SIGNAL is represented by the network's existing invalid sentinel (-1). No changes to PID, watchdog, topology compatibility, or the established oscilloscope trigger/capture system.

**Tech Stack:** Java 21, Forge 1.20.1, Python 3 static contract verifier.

**Spec:** `src/main/java/dev/redstoneengineering/block/SignalProbeBlock.java` measurementPresent contract and the approved Electrical + Instrumentation retrofit design.

## Global Constraints

- Preserve Minecraft redstone 0..15 behavior.
- Preserve valid measured zero as distinct from NO_SIGNAL.
- Reuse PortQuality / existing `-1` invalid-channel sentinel; do not invent a parallel quality system.
- Do not change mature PID, watchdog, topology, or oscilloscope trigger semantics in this task.
- Verification must be focused; no GameTest is required for this semantic bug fix.

---

### Task 1: Lock the regression contract

**Files:**
- Create: `tools/rse_instrument_measurement_validity_verify.py`
- Modify: `.github/workflows/build.yml`

**Interfaces:**
- Consumes: `SignalProbeBlock.measurementPresent(Level, BlockPos, BlockState, int)`
- Produces: a fail-closed static gate requiring InstrumentNetwork to preserve NO_SIGNAL separately from numeric zero.

- [ ] **Step 1: Write the failing verifier** requiring `InstrumentNetwork.recordProbe` to call `SignalProbeBlock.measurementPresent` and store `-1` when absent.
- [ ] **Step 2: Run the verifier against the pre-fix source and confirm it fails for the missing validity propagation.**
- [ ] **Step 3: Add the verifier to the normal static-verification list.**

### Task 2: Propagate probe validity into InstrumentNetwork

**Files:**
- Modify: `src/main/java/dev/redstoneengineering/instrument/InstrumentNetwork.java`

**Interfaces:**
- Consumes: probe sample value plus `measurementPresent(...)`.
- Produces: channel value `0..15` only for a present measurement, otherwise `-1`; duplicate-channel behavior remains unchanged.

- [ ] **Step 1: Change `recordProbe` so the first probe on a channel records `value` only when the measurement is present, otherwise `-1`.**
- [ ] **Step 2: Preserve `counts[channel]` so a connected-but-no-signal probe remains distinguishable from no probe, and duplicate probes still become ambiguous.**
- [ ] **Step 3: Re-run the focused verifier and confirm PASS.**

### Task 3: Verify the measurement chain

**Files:**
- Inspect: `SignalProbeBlock.java`, `InstrumentNetwork.java`, `OscilloscopeBlock.java`, `OscilloscopeBlockEntity.java`

**Interfaces:**
- Consumes: network `ProbeSnapshot.valid/valueOr/qualityForMask`.
- Produces: scope captures `-1` for absent measurements while preserving legitimate zero samples.

- [ ] **Step 1: Confirm `ProbeSnapshot.valid` still rejects `-1` and accepts a legitimate `0` when exactly one probe owns the channel.**
- [ ] **Step 2: Confirm Oscilloscope uses `valueOr(channel, -1)` and therefore receives the corrected sentinel without further production changes.**
- [ ] **Step 3: Re-fetch changed files and report only verification evidence actually observed.**
