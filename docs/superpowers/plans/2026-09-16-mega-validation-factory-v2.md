# Mega Validation Factory v2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a 40-DUT, eight-cell Mega Validation Factory with station/cell/process/safety/final acceptance diagnostics while preserving the existing v1.1 plant and 19 selftests.

**Architecture:** Python owns physical layout and station metadata; Java owns persistent runtime phase control, station/cell evaluation, visible panel updates, and developer commands. The Mega factory uses eight 31x7x25 cells plus a control hall and service corridors; all 40 stations contribute to cell and final verdicts, and a separate plant backbone verifies cross-cell continuity.

**Tech Stack:** Python 3 structure generator + NBT writer, Java 21, NeoForge/Minecraft structure templates and SavedData, unittest contract tests, Gradle/GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-16-mega-validation-factory-v2-design.md`

## Global Constraints

- Existing `/rsevalidation plant` behavior remains available.
- Existing 19 selftests remain exactly 19 and are not repurposed.
- Mega contains exactly 40 primary DUT stations and exactly 8 cells of 5 stations.
- Runtime may drive fixture stimuli but must never write DUT outputs to manufacture PASS.
- Every station must have a visible ID/name sign and WAIT/PASS/FAIL lights with yellow/lime/red bases.
- Final world acceptance is not claimed from CI alone.

---

### Task 1: Mega contract tests

**Files:**
- Create: `tools/test_rse_mega_factory.py`
- Modify: `.github/workflows/build.yml`

**Interfaces:**
- Consumes: v2 spec.
- Produces: contract tests covering 40 stations, 8 cells, layout, signs, panels, commands, saved data, phases, and generator integration.

- [ ] Write tests that import `tools.rse_mega_factory` and require exactly 40 unique station numbers D01–D40, exactly five per A–H, and 11 modular structures.
- [ ] Require non-overlapping module offsets and a footprint at least 130x70 blocks.
- [ ] Require per-station signs and three-color panel bases.
- [ ] Require Java command strings `mega place/status/station/cell/report/retest`, server-tick wiring, 20 phase names, and persistent station history fields.
- [ ] Add `tools.test_rse_mega_factory` to the validation contract test CI command.
- [ ] Verify the new tests fail before implementation.

### Task 2: Mega physical generator

**Files:**
- Create: `tools/rse_mega_factory.py`
- Modify: `tools/rse_validation_factory.py`

**Interfaces:**
- Produces: `MEGA_STRUCTURE_ORDER`, `MEGA_MODULE_OFFSETS`, `MEGA_MODULE_SIZES`, `MEGA_STATIONS`, `MEGA_CELL_STATIONS`, `build_all()`.

- [ ] Define control hall, north service spine, cross-service spine, and eight 31x7x25 cells.
- [ ] Define 40 station records with exact DUT ids and local coordinates.
- [ ] Build full-volume-clearing industrial shells with floor, frames, rails, lighting, maintenance lanes, station signs, station panels, cell panels, and a physical analog backbone.
- [ ] Build a control hall with a 40-station matrix, A–H cell panels, master panel, master RETEST button, and diagnostic signs.
- [ ] Add `generate-mega` plus standard `generate()` integration to write `validation/mega/*.nbt` and `mega-index.txt`.
- [ ] Run contract tests to verify generator requirements pass.

### Task 3: Persistent Mega runtime state

**Files:**
- Create: `src/main/java/dev/redstoneengineering/validation/RseMegaValidationSavedData.java`

**Interfaces:**
- Produces: placement/origin, phase, phaseStartedTick, retestPressed, station current/history arrays, cell current arrays, completed phase bitset, endurance counters.

- [ ] Implement SavedData serialization for all persistent fields with bounded station index 1..40 and cell index 0..7.
- [ ] Implement `place`, `advancePhase`, `resetForRetest`, `setRetestPressed`, `recordStation`, `recordCell`, `markPhaseComplete`, and endurance update APIs.
- [ ] Ensure reset clears history for the new run without affecting v1 data.

### Task 4: Mega station/cell registry and placement runtime

**Files:**
- Create: `src/main/java/dev/redstoneengineering/validation/RseMegaValidationService.java`

**Interfaces:**
- Consumes generated module ids/coordinates mirrored by contract tests.
- Produces `place`, `retest`, `tick`, module preflight/clear/place, station registry, cell registry.

- [ ] Define 11 ModuleSpec entries matching Python offsets/sizes.
- [ ] Define 40 StationSpec entries matching Python DUT ids/cell/local positions and panel power coordinates.
- [ ] Implement exact block-id presence checks using `BuiltInRegistries.BLOCK` / `ResourceLocation` instead of importing 40 concrete classes.
- [ ] Implement structure preflight, placement, full module clearing, master RETEST edge handling, and WAIT initialization.

### Task 5: Station and cell evaluation

**Files:**
- Modify: `RseMegaValidationService.java`

**Interfaces:**
- Produces station `Evaluation` and cell `Evaluation` maps used by status panels and acceptance logic.

- [ ] Implement generic station evidence: exact block id, stable blockstate, at least one live signal/topology observation, and `DirectionalSignalBlock.OUTPUT` evidence when present.
- [ ] Add specific high-value evaluators for D01/D03/D05/D10/D37/D38/D39/D40 using existing public runtime APIs.
- [ ] Implement five-station cell aggregation plus one local integration observation per cell.
- [ ] Persist station/cell verdict/detail/history on each evaluation cycle.

### Task 6: 20-phase acceptance controller

**Files:**
- Modify: `RseMegaValidationService.java`

**Interfaces:**
- Produces automatic phase progression and final master verdict.

- [ ] Add the 20 spec phases and per-phase settle/timeout windows.
- [ ] Drive only fixture-owned reference sources/fault controls.
- [ ] Evaluate station BIST, cell acceptance, backbone continuity, degraded phases, safe-state/recovery, endurance, and final criteria.
- [ ] Require all 40 stations to have passed at least once, all 8 cells current PASS, required phases complete, and no unresolved failures before FINAL_ACCEPTANCE PASS.

### Task 7: Visible panels and diagnostics

**Files:**
- Modify: `RseMegaValidationService.java`

**Interfaces:**
- Produces local station panels, local cell panels, control-room station matrix, A–H panels, and master panel.

- [ ] Mirror every station verdict to its local panel and control-wall lamp.
- [ ] Mirror each cell verdict to local/control cell panels.
- [ ] Update master WAIT/PASS/FAIL panel.
- [ ] Preserve yellow/lime/red base blocks while only swapping the rear power blocks.

### Task 8: Mega command surface and tick wiring

**Files:**
- Modify: `src/main/java/dev/redstoneengineering/validation/RseValidationFactoryModule.java`

**Interfaces:**
- Adds `/rsevalidation mega place|status|station|cell|report|retest` and `RseMegaValidationService.tick`.

- [ ] Add IntegerArgumentType station 1..40 and StringArgumentType cell A..H command branches.
- [ ] Keep all v1/selftest commands unchanged.
- [ ] Wire Mega tick after v1 plant tick.

### Task 9: CI and regression verification

**Files:**
- Modify: `.github/workflows/build.yml` only if Task 1 did not finish CI wiring.

- [ ] Run Python compileall and all three validation contract suites.
- [ ] Run static/reference verification.
- [ ] Run `compileJava`, Gradle tests, and clean build.
- [ ] Confirm existing v1 plant and 19-selftest tests remain green.
- [ ] Do not claim in-world Mega PASS because PR GameTests remain policy-skipped unless manually run.

### Task 10: PR documentation and operator handoff

**Files:**
- Modify: PR #190 body if useful.

- [ ] Document Mega commands and the regenerate requirement: `python3 tools/rse_validation_factory.py generate`.
- [ ] Document that the first field run should use `/rsevalidation mega place`, then `/rsevalidation mega status`, `/station N`, `/cell X`, `/report` for failures.
- [ ] Record final branch HEAD and CI results.