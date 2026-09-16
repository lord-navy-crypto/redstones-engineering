# Integrated Validation Plant v1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a modular, walkable in-world commissioning plant that self-validates six connected engineering cells and then runs a plant-level acceptance sequence without fabricating DUT success.

**Architecture:** Keep the existing 19-bench self-test yard unchanged. Add six `validation/plant/*` NBT modules plus a control-room module, composed by a new `RseValidationPlantService`; plant state is persisted separately from bench state. The service reads real block states, engineering snapshots, analyzer samples, interlock/alarm runtime and validation-owned stimulus sources, then drives only status lamps and owned stimulus during the acceptance sequence.

**Tech Stack:** Java 21, NeoForge/Minecraft 1.21.1 structure templates and SavedData, Python 3 structure generator/unittest.

**Spec:** Conversation design approved 2026-09-16: Modular Validation Plant v1, Cells A-F, central control room, plant place/status/retest commands, staged acceptance lifecycle.

## Global Constraints

- Preserve all existing 19 self-test IDs and `/rsevalidation selftest ...` behavior.
- Do not write DUT success state directly; evaluator is observer-only except validation-owned stimulus and status lamps.
- `PortQuality` and validation verdict are separate concepts: expected `SATURATED`, `FAULT`, or `NO_SIGNAL` may still produce PASS.
- Generate modular structures under `validation/plant/`; do not create one monolithic plant NBT.
- Plant v1 covers signal acquisition, conditioning, instrumentation, control, safety/protection, and process/actuation. Operations/AMR plant integration remains v2.
- Physical RETEST control remains available in the central control room; command fallback is also provided.

---

### Task 1: Contract tests for modular plant generation and command surface

**Files:**
- Create: `tools/test_rse_validation_plant.py`
- Modify later: `tools/rse_validation_factory.py`
- Create later: `tools/rse_validation_plant.py`
- Modify later: `src/main/java/dev/redstoneengineering/validation/RseValidationFactoryModule.java`

**Interfaces:**
- Produces expected module IDs: `control_room`, `cell_a_acquisition`, `cell_b_conditioning`, `cell_c_instrumentation`, `cell_d_control`, `cell_e_safety`, `cell_f_process`.
- Produces command literals `plant`, `place`, `status`, `retest`.

- [ ] Write Python tests asserting seven modular builders, non-overlapping module placement metadata, a physical central RETEST button, factory generation of `validation/plant/*.nbt`, and command/service wiring.
- [ ] Run `python3 -m unittest tools.test_rse_validation_plant -v` and verify RED because `tools.rse_validation_plant` / plant service do not exist.
- [ ] Implement the generator/module metadata and command skeleton.
- [ ] Re-run the test and verify GREEN.

### Task 2: Plant persistent lifecycle state

**Files:**
- Create: `src/main/java/dev/redstoneengineering/validation/RseValidationPlantSavedData.java`
- Create: `src/main/java/dev/redstoneengineering/validation/RseValidationPlantService.java`
- Modify: `tools/test_rse_validation_plant.py`

**Interfaces:**
- `RseValidationPlantSavedData.Placement(origin, placedTick, phase, retestPressed)`
- `RseValidationPlantService.place(ServerLevel, BlockPos)`
- `RseValidationPlantService.status(ServerLevel)`
- `RseValidationPlantService.retest(ServerLevel)`
- `RseValidationPlantService.tick(MinecraftServer)`

- [ ] Add tests requiring dedicated plant SavedData and server-tick ownership.
- [ ] Verify RED.
- [ ] Implement persistent origin/phase/retest state and module placement preflight.
- [ ] Verify GREEN.

### Task 3: Cell A-C real observation checks

**Files:**
- Modify: `tools/rse_validation_plant.py`
- Modify: `src/main/java/dev/redstoneengineering/validation/RseValidationPlantService.java`
- Modify: `tools/test_rse_validation_plant.py`

**Interfaces:**
- Cell A: reference source + probe acquisition.
- Cell B: conditioner gain chain and output indicator.
- Cell C: inline analyzer plus independent process observation.

- [ ] Add structural/contract tests for the A-C DUT blocks and offsets.
- [ ] Verify RED.
- [ ] Implement A-C structures and evaluators using real block/snapshot/analyzer evidence.
- [ ] Verify GREEN.

### Task 4: Cell D-F control, safety, and actuation checks

**Files:**
- Modify: `tools/rse_validation_plant.py`
- Modify: `src/main/java/dev/redstoneengineering/validation/RseValidationPlantService.java`
- Modify: `tools/test_rse_validation_plant.py`

**Interfaces:**
- Cell D: PWM/controller observation with analyzer samples.
- Cell E: safety interlock + alarm lifecycle and explicit permissive stimulus.
- Cell F: world-backed actuator output and feedback observation.

- [ ] Add tests for D-F block composition and lifecycle phase tokens.
- [ ] Verify RED.
- [ ] Implement D-F structures/evaluators.
- [ ] Verify GREEN.

### Task 5: Plant acceptance sequence and control-room visualization

**Files:**
- Modify: `src/main/java/dev/redstoneengineering/validation/RseValidationPlantService.java`
- Modify: `tools/rse_validation_plant.py`
- Modify: `tools/test_rse_validation_plant.py`

**Interfaces:**
- Acceptance phases: `PRECHECK`, `BIST`, `STARTUP`, `NOMINAL`, `DISTURBANCE`, `SATURATION`, `SENSOR_FAULT`, `INTERLOCK_TRIP`, `RECOVERY`, `FINAL_RUN`, `ACCEPTANCE`.
- Per-cell and master WAIT/PASS/FAIL lamps in the control room.

- [ ] Add tests requiring all named phases and status-panel metadata.
- [ ] Verify RED.
- [ ] Implement phase progression, owned stimulus transitions, per-cell panel updates, and master result.
- [ ] Verify GREEN.

### Task 6: Regression and generated-assets verification

**Files:**
- Modify: `tools/rse_validation_factory.py`
- Generated locally by user/tooling: `src/generated/resources/data/redstoneengineering/structure/validation/plant/*.nbt`

**Interfaces:**
- Existing `generate` continues to generate factory, presets, self-tests, plus plant modules.

- [ ] Run `python3 -m unittest tools.test_rse_validation_autorun tools.test_rse_validation_plant -v`.
- [ ] Run `python3 tools/rse_validation_factory.py generate` and verify seven plant NBT files are emitted.
- [ ] Run the repository build/test command when CI/local Gradle is available.
- [ ] Verify existing 19 self-test IDs remain unchanged.
