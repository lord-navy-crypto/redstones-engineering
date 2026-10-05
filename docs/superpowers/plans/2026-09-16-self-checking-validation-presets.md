# Self-Checking Validation Presets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the existing 01_basic and 02_signal preset structures into deterministic self-checking benches with runtime WAIT/PASS/FAIL evaluation and visible status lamps.

**Architecture:** Python generates block-state-aware NBT structures and a self-test catalog. Java places and evaluates those structures through existing RSE/world APIs, stores placed test origins in world SavedData, and updates only the status panel after evaluation. Existing device physics stays authoritative.

**Tech Stack:** Python 3, gzip NBT writer, Minecraft 1.21.1 / NeoForge 21.1.x, Java 21, existing RSE EngineeringPort/runtime APIs.

**Spec:** `docs/superpowers/specs/2026-09-16-self-checking-validation-presets-design.md`

## Global Constraints
- PASS must come from authoritative runtime/world evidence, never layout presence alone.
- Missing/invalid/stale evidence fails closed; insufficient samples return WAIT.
- UI/validation code must not duplicate or replace device physics.
- Full GameTestServer remains manual/local; CI uses unit/static/build verification only.

---

### Task 1: Block-state-aware NBT generation

**Files:**
- Modify: `tools/rse_validation_factory.py`
- Modify: `tools/rse_validation_presets.py`
- Modify: `tools/test_rse_validation_factory.py`

**Interfaces:**
- Produces `BlockSpec(name: str, properties: dict[str,str])` support in `_structure_bytes(...)`.
- Preset builders may return either a plain block id or a block-state-aware spec.

- [ ] Write failing Python tests asserting palette `Properties` are serialized and self-test structures use deterministic `facing/power/mode` values.
- [ ] Run `python3 -m unittest tools.test_rse_validation_factory -v` and confirm failure for missing property support.
- [ ] Implement minimal NBT compound serialization for palette `Properties` and update selected preset builders.
- [ ] Re-run unit tests and `python3 tools/rse_validation_factory.py generate`.
- [ ] Commit.

### Task 2: Self-test structure catalog and status panels

**Files:**
- Create: `tools/rse_validation_selftests.py`
- Modify: `tools/rse_validation_factory.py`
- Modify: `tools/test_rse_validation_factory.py`

**Interfaces:**
- Produces `SELFTESTS` keyed by `01_basic/...` and `02_signal/...`.
- Every definition exposes fixed offsets for DUT, stimulus/measurement points, and WAIT/PASS/FAIL lamps.

- [ ] Write failing tests requiring at least the 16 self-tests in the spec and asserting unique block positions plus a 3-lamp panel.
- [ ] Verify RED.
- [ ] Implement the self-test builders and generator output under `validation/selftest/...`.
- [ ] Verify GREEN and deterministic generation.
- [ ] Commit.

### Task 3: Server-side evaluation and commands

**Files:**
- Create: `src/main/java/dev/redstoneengineering/validation/RseValidationSelfTestSavedData.java`
- Create: `src/main/java/dev/redstoneengineering/validation/RseValidationSelfTestService.java`
- Modify: `src/main/java/dev/redstoneengineering/validation/RseValidationFactoryModule.java`
- Modify: `src/main/java/dev/redstoneengineering/gametest/RseValidationFactoryGameTests.java`

**Interfaces:**
- `/rsevalidation selftest list`
- `/rsevalidation selftest place <id>`
- `/rsevalidation selftest check <id>`
- `RseValidationSelfTestService.Result { WAIT, PASS, FAIL }`

- [ ] Add failing GameTest/static verifier expectations for command registration, origin persistence, and fail-closed result behavior.
- [ ] Verify RED.
- [ ] Implement SavedData origin tracking, template placement, evaluators for static/available runtime tests, and status-panel updates.
- [ ] Register commands in the existing validation module.
- [ ] Verify compile/tests/static checks.
- [ ] Commit.

### Task 4: Verification and operator instructions

**Files:**
- Modify: `docs/VALIDATION_FACTORY.md`
- Modify: `.github/workflows/rse-operations-material-flow.yml` only if needed for lightweight Python/static checks.

**Interfaces:**
- Documents exact `generate -> runClient -> selftest place -> selftest check` flow.

- [ ] Add concise usage examples and explain WAIT/PASS/FAIL semantics.
- [ ] Run Python unit tests, self-test generator, validation verifier, Java compile/tests, and clean build.
- [ ] Confirm no heavy GameTestServer was added to CI.
- [ ] Commit.
