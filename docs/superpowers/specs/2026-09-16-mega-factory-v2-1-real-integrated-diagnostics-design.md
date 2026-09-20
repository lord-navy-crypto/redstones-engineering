# Mega Validation Factory v2.1 — Real Integrated Plant + Backend Diagnostic Export

**Date:** 2026-09-16  
**Branch:** `codex/persistent-plant-runtime-validation-world`  
**Status:** Design for field-tested v2.1 repair

## 1. Problem statement

The first in-world run of Mega Factory v2 exposed two architectural defects that CI could not reveal:

1. **False-green station evaluation.** The runtime can report `PASS` while a port snapshot is `NO_SIGNAL`. The generic evaluator currently treats anything other than `STALE` as fresh evidence, so `NO_SIGNAL`, `FAULT`, `DOMAIN_MISMATCH`, and `TOPOLOGY_ERROR` can be counted as usable evidence. This produced contradictory field output such as `stationsEverPassed=40/40` while only `2/20` phases completed.
2. **Insufficient physical integration.** The v2 structure places 40 primary DUTs and a plant-wide backbone, but many DUT bays are still independent fixtures. A station-local source at z=15 and an indicator at z=17 do not, by themselves, form a true upstream→DUT→downstream chain. The factory therefore looks integrated while some domains never receive a valid signal.

The field run also showed the H cell in an unhealthy normal-state combination: servo braking, position sensor `NO_SIGNAL`, and an interlock failed mask, while those stations still appeared as PASS.

v2.1 fixes the factory at the architecture level. It does not merely turn more lamps red.

## 2. Goals

v2.1 must provide all of the following:

- Keep the same **40 primary DUTs** and **8 cells × 5 DUTs**.
- Make every cell a real physical signal/process chain with explicit input and output paths.
- Connect cells through explicit, domain-correct bridge/conversion infrastructure instead of assuming unlike domains can be wired directly.
- Make normal-phase PASS require healthy evidence; `NO_SIGNAL` must never be accepted as normal success.
- Make fault phases phase-aware: an injected degraded condition can be the expected observation for that scenario, but it must still trigger the required downstream safety/recovery response.
- Distinguish **device health** from **scenario verdict** so expected fault injection is not confused with a healthy device.
- Produce one complete diagnosis from `/rsevalidation mega diagnose` without requiring visual inspection of lamps.
- Export that diagnosis to the Minecraft/Gradle backend log and to a copyable text file.
- Preserve historical evidence across retests/runs.
- Identify root blockers separately from downstream cascade failures.

## 3. Non-goals

- Do not remove v1 plant validation or the 19 selftests.
- Do not change the 40 primary DUT list merely to make wiring easier.
- Do not make commands the primary gameplay interface; physical panels remain useful, while commands are engineering/debug access.
- Do not count a validator-written DUT output as evidence. The validator may own fixture stimuli, but DUT outputs and device state must come from block runtime behavior.
- Do not require the player to inspect the factory visually to identify a failure.

## 4. Architecture

v2.1 uses four explicit layers.

### 4.1 Layer 1 — DUT evaluation

Each station has a structured evaluation containing at least:

- station number and cell
- expected block identity
- world position
- input evidence
- output evidence
- port quality/domain/topology evidence
- device-specific runtime evidence when available
- **health verdict**: `HEALTHY`, `DEGRADED`, `FAILED`, `PENDING`
- **scenario verdict**: `PASS`, `WAIT`, `FAIL`
- detail string and machine-readable reason code

A station can therefore say, for example:

```text
D38 POS SENSOR
health=DEGRADED
scenario=PASS
quality=NO_SIGNAL
reason=EXPECTED_SENSOR_FAULT_OBSERVED
```

only during `SENSOR_FAULT`. During `CELL_ACCEPTANCE`, the same `NO_SIGNAL` must be `scenario=FAIL` (or transient `WAIT` before timeout).

### 4.2 Layer 2 — Cell chain

Each cell contains five primary DUTs plus auxiliary wiring/conversion/test infrastructure. The five primary DUTs are physically connected according to their real port directions and domains.

The cell verdict is based on both:

- all five station scenario verdicts; and
- a cell-local continuity/behavior check proving the signal/process reaches the cell output through the intended chain.

A cell cannot PASS merely because all five blocks exist.

### 4.3 Layer 3 — Plant integration

Cells remain ordered as:

```text
A → B → C → D → E → F → G → H
```

but unlike domains are connected through **bridge bays**. Bridge infrastructure is auxiliary and does not increase the 40 primary DUT count.

Each bridge must have:

- explicit source domain
- explicit destination domain
- a concrete converter/adapter path using registered RSE blocks
- a probe/checkpoint on both sides
- a continuity expectation used by the plant acceptance runtime

The plant-level gate must prove both local cell health and cross-cell propagation.

### 4.4 Layer 4 — Safety/control closure

H cell is upgraded from a loose collection of devices into a real closed-loop safety fixture:

```text
               feedback
          ┌─────────────────────┐
          │                     │
D36 PID ──┴──► D37 SERVO ───► D38 POSITION SENSOR
                    ▲                 │
                    │ brake/permissive│
                    │                 ▼
                 D39 INTERLOCK ───► D40 ALARM
                  A   B   C        condition/ack/reset
```

The exact block orientations and neighbor positions must be derived from each block's real port API/state implementation before generator coordinates are finalized.

Normal-state expectations include:

- D37 receives a valid command and is not unintentionally braking.
- D38 reports valid feedback from the adjacent servo.
- D39 has healthy permissives (`failedMask == 0`) and a valid permit.
- D40 is clear/unlatched unless the active scenario intentionally requires alarm state.

Fault phases then prove safe-state behavior and recovery rather than merely observing that a fault exists.

## 5. Phase-aware quality policy

The evaluator must use an explicit policy table instead of `quality != STALE`.

### 5.1 Normal phases

In normal commissioning phases (`STRUCTURE_PRECHECK`, `STATION_BIST`, `CELL_ACCEPTANCE`, `CHAIN_CONTINUITY`, `NOMINAL_STARTUP`, `DATA_INTEGRITY_TEST`, normal parts of `PROCESS_LOAD`, `RECOVERY`, `ENDURANCE_RUN`, `FINAL_ACCEPTANCE`):

- `VALID` → acceptable health evidence
- `STALE` → `WAIT`, then timeout → `FAIL`
- `NO_SIGNAL` → `WAIT` only during short propagation grace; then `FAIL`
- `SATURATED` → `FAIL` unless the active scenario explicitly expects saturation
- `FAULT` → `FAIL`
- `DOMAIN_MISMATCH` → `FAIL`
- `TOPOLOGY_ERROR` → `FAIL`

### 5.2 Fault/degradation phases

A degraded observation may be expected only for the designated station/scenario. Examples:

- `SATURATION_TEST`: the designated saturated path must actually show saturation and downstream behavior must match the scenario.
- `SENSOR_FAULT`: D38 may intentionally become `NO_SIGNAL`/degraded, but D37/D39/D40 must demonstrate the required fail-safe response.
- `ACTUATOR_FAULT`: actuator safe/brake evidence must be observed and feedback/safety logic must react correctly.
- `INTERLOCK_TRIP`: `failedMask > 0` is expected and permit must drop.
- `SAFE_STATE`: the servo must be in the intended safe state.
- `ACK_RESET`: alarm lifecycle must clear only under the correct conditions.
- `RECOVERY`: all health expectations return to normal; degraded evidence is no longer acceptable.

Expected fault evidence is a **scenario success**, not a claim that the device is healthy.

## 6. Physical topology repair

### 6.1 Generator responsibilities

`tools/rse_mega_factory.py` will be upgraded so each cell builder creates:

- five primary DUTs
- explicit per-DUT input/output wiring based on orientation
- local bridge blocks where a station-to-station domain conversion is required
- station probes/checkpoints
- a real cell input node and cell output node
- signs/panels retained from v2
- no disconnected “decorative fixture source” counted as a working input

The existing z=15 station fixture sources may remain only where they serve a real declared stimulus port. Otherwise they must be removed or repurposed as explicit test infrastructure.

### 6.2 Cross-cell bridge registry

Create one authoritative topology model shared conceptually by generator/tests/runtime. It describes:

- cell input/output checkpoints
- station dependencies
- auxiliary bridge dependencies
- expected signal domain at each boundary
- upstream station/root source for cascade attribution

The generator and runtime must not maintain unrelated ad-hoc assumptions about the same topology.

### 6.3 Dependency graph

The topology model provides a dependency graph used by diagnosis. If D18 is the first failed node and D19/D20 have no signal because of it, diagnosis should report:

```text
ROOT BLOCKER: D18 SERIALIZER ...
CASCADE: D19 <- D18
CASCADE: D20 <- D19
CELL D <- D18
PLANT <- CELL D
```

A downstream `NO_SIGNAL` caused by an upstream root failure should not be presented as an independent root cause.

## 7. Diagnostic export

### 7.1 Command

Keep the existing command surface and retain:

```text
/rsevalidation mega diagnose
```

The command performs a fresh live evaluation of all 40 stations, all 8 cells, and the active phase gate.

### 7.2 Outputs

The same report is emitted to three destinations:

1. Minecraft command feedback.
2. The normal server/backend logger with prefix `[RSE-MEGA-DIAG]`, therefore visible in the Gradle `runClient` terminal and `run/logs/latest.log`.
3. A copyable UTF-8 file:

```text
run/rse-diagnostics/mega-latest.txt
```

Each diagnosis also appends to:

```text
run/rse-diagnostics/mega-history.log
```

The export failure must not crash the game or invalidate the in-world test; it should emit a clear logging warning while still returning the diagnosis in chat.

### 7.3 Report format

The report must be stable and copy-friendly:

```text
[RSE-MEGA-DIAG] RUN=<n>
[RSE-MEGA-DIAG] PHASE=<phase> AGE=<ticks> MASTER=<verdict>

===== ROOT BLOCKERS =====
...

===== CASCADE =====
...

===== CELL SUMMARY =====
A ...
...
H ...

===== ALL 40 DUT =====
D01 ...
...
D40 ...

===== HISTORY =====
...

===== ACCEPTANCE =====
completedPhases=x/20
stationsEverPassed=x/40
liveHealthy=x/40
liveFail=x
liveWait=x
endurance=x/200t
```

Each abnormal station line should include as much structured evidence as the block exposes: input/output, quality, expected quality/state, failed mask, brake state, latch state, and world position.

## 8. Persistence

`RseMegaValidationSavedData` will continue to retain station/cell verdict history and phase completion. v2.1 adds an integer `runNumber` that increments on every explicit retest/rebuild. Initial placement starts at run 1. Exported reports include that exact number so repeated field runs can be compared without ambiguity.

History semantics must preserve:

- first failure phase/detail
- ever passed / ever failed
- current verdict/detail
- completed phases
- endurance progress
- current `runNumber`

A recovery PASS must not erase earlier failure history.

## 9. Code organization

v2.1 uses these fixed responsibility boundaries:

- `RseMegaValidationService` — lifecycle/orchestration, phase progression, placement hooks, panel updates
- `RseMegaStationEvaluator` — station/device semantics, quality policy, phase-aware evaluation, health/scenario verdicts
- `RseMegaValidationTopology` — station/cell/bridge dependency definitions, domains, checkpoints, world-position helpers
- `RseMegaDiagnosticReporter` — root/cascade analysis, stable text formatting, backend logger emission, latest/history file export
- `RseMegaValidationSavedData` — persistence only, including `runNumber`
- `tools/rse_mega_factory.py` — physical structure generation that follows the topology contract

Evaluation policy, diagnostic export, and physical topology must not be folded back into one monolithic service.

## 10. Testing strategy

### 10.1 RED tests first

Before repair, add regression tests reproducing the field findings:

- `NO_SIGNAL` must not count as fresh/healthy evidence in a normal phase.
- `FAULT`, `DOMAIN_MISMATCH`, and `TOPOLOGY_ERROR` cannot produce normal PASS.
- D38 `NO_SIGNAL` in `CELL_ACCEPTANCE` is not PASS.
- D39 `failedMask > 0` in normal acceptance is not PASS.
- D37 unintended brake in normal acceptance is not PASS.
- all five DUTs in every cell have declared physical dependency paths.
- every cross-cell boundary has a declared bridge/checkpoint.
- diagnosis contains root/cascade sections.
- diagnosis exporter targets `mega-latest.txt`, `mega-history.log`, and backend logger.

### 10.2 Generator contract tests

Extend `tools/test_rse_mega_factory.py` to assert:

- 40 primary DUT identities remain unchanged.
- 8 cells × 5 DUT remain unchanged.
- no primary DUT is orphaned from its declared cell chain.
- H-cell servo/sensor adjacency matches the real mechanical topology.
- interlock and alarm control ports have declared fixture paths.
- bridge/checkpoint coordinates are inside their structure volumes and non-overlapping.

### 10.3 Build verification

Final delivery requires a fresh HEAD with:

- Mega/Plant/selftest Python contracts PASS
- static/reference verification PASS
- `compileJava` PASS
- Gradle tests PASS
- clean build PASS
- SHA-256 artifact generation PASS
- verified artifact upload PASS
- Operations Material Flow workflow PASS

Minecraft topology GameTests remain a manual diagnostic under current repository policy; they are not to be falsely reported as passing when skipped.

### 10.4 Field acceptance

After CI is green, the user runs only:

```text
/rsevalidation mega retest
/rsevalidation mega diagnose
```

Then copies `run/rse-diagnostics/mega-latest.txt` to ChatGPT. No visual lamp inspection is required for debugging.

A successful field run must no longer show the contradiction “40/40 passed but only 2/20 phases complete.” Final acceptance requires all required phase gates, healthy final station state, recovery evidence, endurance, and plant continuity.

## 11. Delivery criteria

v2.1 is complete only when all of the following are true:

1. The field false-green condition is impossible by policy/tests.
2. Each cell is a real connected subsystem, not five independent displays.
3. Cross-cell domain transitions are explicit and testable.
4. H cell is a real servo-feedback-safety-alarm fixture.
5. Normal acceptance cannot PASS `NO_SIGNAL`, `FAULT`, `DOMAIN_MISMATCH`, or `TOPOLOGY_ERROR`.
6. Fault phases distinguish expected degraded evidence from health.
7. `/rsevalidation mega diagnose` identifies root blockers and cascades.
8. The same diagnosis is written to backend logs and `run/rse-diagnostics/mega-latest.txt`.
9. Retest history remains available in `mega-history.log` and SavedData.
10. Fresh CI/build verification passes on the final branch HEAD.
11. In-world acceptance is explicitly treated as pending until the user reruns and shares the exported diagnosis.