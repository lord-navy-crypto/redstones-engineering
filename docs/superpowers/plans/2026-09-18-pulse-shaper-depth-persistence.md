# Pulse Shaper Depth + Persistence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deepen the existing Pulse Shaper into a configurable monostable while avoiding a high-cardinality BlockState explosion.

**Architecture:** Keep low-cardinality visual/operator state in BlockState, keep short-lived pulse timing in RuntimeIntStore, and move precise threshold plus retained trigger diagnostics into a small PulseShaperBlockEntity. Reuse the existing Signal Processor HMI; do not create a new subsystem.

**Tech Stack:** Java 21, NeoForge 1.20.1 project conventions, existing Engineering UI, Python verifier + javac semantic harness.

**Spec:** Approved in chat on 2026-09-18: width + retrigger remain low-cardinality state; threshold and retained trigger diagnostics persist in a small BlockEntity.

## Global Constraints

- Preserve vanilla redstone 0..15 interoperability.
- No new engineering domain and no new standalone block.
- Do not encode 15-step threshold into BlockState.
- Changing threshold must re-baseline trigger detection instead of generating a synthetic trigger.
- Runtime pulse countdown is transient; configuration and accepted/suppressed trigger evidence persist.
- Heavy GameTests are not part of this focused pass.

---

### Task 1: Persistent Pulse Shaper State

**Files:**
- Create: `src/main/java/dev/redstoneengineering/blockentity/PulseShaperBlockEntity.java`
- Modify: `src/main/java/dev/redstoneengineering/RedstoneEngineering.java`
- Modify: `src/main/java/dev/redstoneengineering/block/PulseShaperBlock.java`
- Test: `tools/rse_pulse_shaper_depth_verify.py`

**Interfaces:**
- Produces: threshold 1..15, acceptedTriggerCount, suppressedTriggerCount, lastTriggerTick, and baseline-invalidated flag.
- Consumes: existing PulseShaper block position and server tick.

- [ ] Extend the verifier so it requires BlockEntity-backed threshold/diagnostics and rejects a THRESHOLD BlockState property.
- [ ] Run/inspect RED against the current branch.
- [ ] Add the BlockEntity and registration using the repository's existing block-entity registration pattern.
- [ ] Move threshold and retained counters out of transient runtime/BlockState.
- [ ] Keep remaining pulse ticks and last-above-threshold transient.
- [ ] Verify threshold changes re-baseline without producing a trigger.

### Task 2: HMI Authority and Diagnostics

**Files:**
- Modify: `src/main/java/dev/redstoneengineering/ui/menu/SignalProcessorMenu.java`
- Modify: `src/main/java/dev/redstoneengineering/client/ui/SignalProcessorScreen.java`

**Interfaces:**
- Consumes: PulseShaperBlock accessors/actions.
- Produces: server-authoritative width, threshold, retrigger mode, accepted/suppressed trigger counts and last-trigger age.

- [ ] Keep existing width controls.
- [ ] Add threshold previous/next and retrigger toggle.
- [ ] Display retained trigger evidence in Diagnostics/History.
- [ ] Ensure opening UI is observer-neutral.

### Task 3: Focused Verification

**Files:**
- Modify: `tools/rse_pulse_shaper_depth_verify.py`
- Modify one already-invoked static verifier only if needed to chain this verifier.

- [ ] Compile and execute pure PulseShaperLogic semantic cases with javac/java.
- [ ] Verify source invariants: no high-cardinality THRESHOLD property, BlockEntity owns persistence, HMI exposes controls/evidence.
- [ ] Back-read every changed source file from the branch.
- [ ] Do not claim full repository build success without fresh compile/build evidence.
