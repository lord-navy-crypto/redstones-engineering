# Validation Plant v1.1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Integrated Validation Plant structurally complete and tester-readable with real sign text, color-coded status panels, a service spine, and structural regression tests.

**Architecture:** Keep the current Java acceptance runtime and A→F DUT topology. Extend the Python structure writer to serialize block-entity NBT, then use a reusable plant sign helper and industrial-frame helpers to generate complete modules. Add one non-cell `service_spine` module and update Java placement metadata to load it.

**Tech Stack:** Python 3 structure/NBT generator, Minecraft Java 1.21.1 structure-template NBT, NeoForge Java 21 runtime, unittest, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-16-validation-plant-v1-1-design.md`

## Global Constraints

- Preserve the existing 19 component self-tests.
- Preserve current A→F DUT semantics and coordinates inside each Cell unless a tested structural conflict requires movement.
- Validation may drive test stimulus and panel state; it must not write DUT outputs to manufacture PASS.
- v1.1 does not replace the backend acceptance phase engine with physical redstone sequencing.
- All final success claims require fresh CI/build evidence for the final HEAD.

---

### Task 1: Lock the v1.1 structural and signage contracts

**Files:**
- Modify: `tools/test_rse_validation_plant.py`

**Interfaces:**
- Consumes: `PLANT_STRUCTURE_ORDER`, `PLANT_STRUCTURES`, `PLANT_MODULE_OFFSETS`, status panel coordinates.
- Produces: failing tests for `service_spine`, structural framing, colored lamp bases, Cell signs, and sign NBT serialization.

- [ ] Add `service_spine` to `EXPECTED_MODULES`.
- [ ] Add a test requiring yellow/lime/red blocks directly below every local/master status lamp.
- [ ] Add a test requiring each Cell to contain y=4 frame blocks and four multi-level corner columns.
- [ ] Add a test requiring all six stable Cell sign texts and control-room legend/diagnostic sign texts.
- [ ] Add a test that calls `_structure_bytes` on a sign placement and verifies `front_text`, `messages`, and the JSON line text are present in the binary payload.
- [ ] Run `python3 -m unittest tools.test_rse_validation_plant -v` and confirm the new tests fail for missing v1.1 behavior.

### Task 2: Add block-entity NBT serialization for signs

**Files:**
- Modify: `tools/rse_validation_factory.py`

**Interfaces:**
- Consumes: extended `BlockSpec` tuple `(block_id, properties, nbt)`.
- Produces: `_block_nbt`, byte/string-list NBT encoders, and `_block_entry(..., nbt)` support.

- [ ] Extend `BlockSpec` without changing palette identity: palette normalization uses block id + properties only.
- [ ] Add TAG_BYTE serialization and generic named NBT support for bool, int, string, string lists, and nested compounds.
- [ ] Change `_block_entry` to optionally include a named `nbt` compound.
- [ ] Change `_structure_bytes` to pass per-block NBT into `_block_entry`.
- [ ] Run the sign-NBT unit test and confirm it passes.

### Task 3: Build the complete v1.1 plant structures

**Files:**
- Modify: `tools/rse_validation_plant.py`

**Interfaces:**
- Consumes: NBT-capable `BlockSpec` emitted by the factory writer.
- Produces: `_sign`, `_clear_volume`, industrial frame helpers, color-coded panels, `service_spine`, and signed A–F/control modules.

- [ ] Add `_sign(lines, rotation, color)` that pads to four lines and emits waxed/glowing 1.21.1 sign NBT.
- [ ] Add full-volume clearing so each module explicitly places air before structural overlays.
- [ ] Replace floor-only `_cell_base` with a semi-open industrial frame: floor, colored border, corner columns, header beams, access openings, lighting, and local status station.
- [ ] Put yellow/lime/red concrete directly below WAIT/PASS/FAIL lamps and add text labels.
- [ ] Add stable Cell identity/test-description signs using the exact wording from the spec.
- [ ] Upgrade the control room with frame/walls, master legend, per-cell identification, and diagnostic/reporting signs while preserving MASTER RETEST.
- [ ] Add `service_spine` at the existing z=13..15 gap, with floor markings, lighting, and a corridor sign.
- [ ] Run `python3 -m unittest tools.test_rse_validation_plant -v` and confirm the structural tests pass.

### Task 4: Teach the Java plant runtime about the service spine

**Files:**
- Modify: `src/main/java/dev/redstoneengineering/validation/RseValidationPlantService.java`

**Interfaces:**
- Consumes: generated `validation/plant/service_spine.nbt`.
- Produces: `ModuleSpec("service_spine", "", new BlockPos(0,0,13), new BlockPos(57,5,3))` placement/preflight/rebuild participation.

- [ ] Add the non-cell module to `MODULES` without adding it to `CELLS`.
- [ ] Update user-facing placement text to `Validation Plant v1.1` and describe readable signed/color-coded stations.
- [ ] Keep Cell origins and evaluator coordinates unchanged.
- [ ] Run Java compile through CI/Gradle verification.

### Task 5: Generate assets and verify final HEAD

**Files:**
- Generated: `src/generated/resources/data/redstoneengineering/structure/validation/plant/*.nbt`
- Existing CI: `.github/workflows/build.yml`

**Interfaces:**
- Consumes: final generator/runtime.
- Produces: eight plant NBT files including `service_spine.nbt` and fresh CI evidence.

- [ ] Run/trigger `python3 tools/rse_validation_factory.py generate-plant` through tests/CI-compatible generation checks.
- [ ] Run `python3 -m unittest tools.test_rse_validation_autorun tools.test_rse_validation_plant -v`.
- [ ] Verify `RSE Build Verification` final HEAD completes successfully: syntax, validation contract tests, static/reference checks, `compileJava`, Gradle tests, clean build.
- [ ] Do not claim in-world acceptance until the user places v1.1 in Minecraft and reports its first runtime result.
