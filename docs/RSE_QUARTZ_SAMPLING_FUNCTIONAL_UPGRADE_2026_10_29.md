# RSE functional upgrade: Quartz source phase and sampled-data diagnostics

**Date:** 2026-10-29. **Branch:** `automation/rse-formula-parameter-workbench`.

## Motivation

A Quartz clock source must present a 0/1 logic phase at its physical QUARTZ output. The lab oscillator formerly emitted the nominal period as its `EngineeringPortSnapshot.value()`, while ordinary Quartz sources and wires presented actual 0/1 phase. That was inconsistent and produced misleading signal readouts. Timing period/jitter are separate metadata, not output amplitude.

The Redstone Sample & Hold previously displayed every connected/absent input as a valid numerical value; an empty connector displaying zero was not distinguishable from a real, intentionally driven zero. Inspection via Shift-right-click also cleared the held state, which made it difficult to test without modifying the circuit.

## Four device-specific improvements

| Registry block | New capability | Original behavior preserved |
| --- | --- | --- |
| `quartz_oscillator` | Shift-right-click **BOTTOM** prints 0/1 phase, nominal period and half-period, output face, quality-aware neighbor observation and explicit non-connectivity caveat. | Normal right-click HMI; Shift-horizontal rotates output; Shift-TOP cycles period. Timing rate and physical source remain unchanged. |
| `quartz_lab_oscillator` | Physical port snapshot now emits 0/1 phase on scale 0..1, separate from nominal period. Shift-TOP displays explicit phase/last-realized jitter evidence and still opens the original HMI. | Noise/jitter law, realized timing history and runtime slots, period/jitter controls, output orientation unchanged. |
| `sample_hold` | Read-only port snapshots preserve VALID vs NO_SIGNAL vs STALE source evidence on VALUE, TRIGGER and RESET input faces. Shift-right-click **BOTTOM** gives detailed non-destructive input and held-state commissioning report. | Trigger mode and hold/reset logic unchanged; Shift-other-faces still clears the held value; normal click retains HMI. |
| `quartz_triggered_lapis_sampler` | Shift-right-click now reports authoritative Quartz clock quality and HIGH/LOW, whether an observed-edge baseline exists, live Lapis input quality, and held Lapis value/quality in full chat. | First valid clock level only establishes baseline; later observed LOW-to-HIGH captures; actual Lapis output drive and old runtime layout remain unchanged. |

### Engineering and user-safety boundaries

- No block registry ID, recipe, loot, model, world-save key, BlockState property or persisted runtime layout changed.
- `QuartzClockSupport` is a small read-only helper shared by both oscillators. It does **not** modify the world or run a network solver during diagnostics.
- Existing world simulation uses the original active/inactive clock and source propagation. Only **the lab oscillator's observable port snapshot semantics** are intentionally corrected.
- A nearby QUARTZ observation is **not proof** of live cable continuity; no connectivity or delivery claim is fabricated.
- Input samples distinguish valid zero from no source; a missing/unknown input is **not** a falsely valid measured zero.
- Two `RseQuartzSamplingUpgradeGameTests` were added and registered, covering the lab port phase and source-aware Sample & Hold. Their runtime outcome remains **NOT_TESTED** until Minecraft GameTestServer actually runs. Gradle compilation alone does not satisfy runtime acceptance.

## Refactor inventory milestone

The all-130 inventory is unchanged. The 46 shared-implementation candidates now distinguish **11 shared-helper implementations** (previous 9 plus both clocks), **2 feature/evidence upgrades without shared core** (two samplers) and **33 remaining candidates** for cautious review. None of these metrics means that 46 distinct physical devices should be merged or removed.

## Target in-game acceptance checklist

1. Place both oscillator versions and confirm default low/high phase transitions on an attached QUARTZ timing line. Check disconnected and unloaded neighbor descriptions.
2. Compare lab osc's 0/1 port readout against active phase; nominal period remains visible as timing evidence, not signal magnitude. Vary jitter/period and verify no hidden parameter mutation from diagnostic inspection.
3. Place Sample & Hold with no rear input, then a Redstone reference source set to zero; check that input quality changes from NO_SIGNAL to VALID while value remains 0.
4. Verify Shift-BOTTOM on Sample & Hold **does not clear** held output; Shift other faces still clears and retains capture/readiness accounting.
5. On the Quartz-triggered Lapis sampler, first connect a currently HIGH Quartz clock, confirm no immediate false capture; then observe a LOW-to-HIGH edge and check captured Lapis value and quality.
6. Recheck full 130-block creative registry coverage, old-world saves, multiplayer status synchronization, and GameTests. No claim of passing these runtime stages until they run.

The CI static gate `tools/rse_quartz_sampling_upgrade_verify.py` checks source-level contracts and test registration, and `tools/rse_46_shared_code_rollout_verify.py` provides a 46-row per-block upgrade/remaining-work matrix.
