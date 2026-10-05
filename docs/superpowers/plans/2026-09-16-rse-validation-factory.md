# RSE Validation Factory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build deterministic reusable Validation Factory structures, a safe server-side factory builder/reset/status command, and local packaging tooling for a real Minecraft-created validation world.

**Architecture:** A Python standard-library generator owns binary structure-template generation and world ZIP packaging. A server-side validation module places generated templates and creates only starting fixture state through existing Operations world facades; it never becomes a second plant runtime. A static verifier and a focused local GameTest lock the boundary.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.249, Gradle/NeoForge ModDev, Python 3 standard library, Minecraft structure-template NBT.

**Spec:** `docs/superpowers/specs/2026-09-16-rse-validation-factory-design.md`

## Global Constraints

- Java remains `21`.
- Minecraft remains `1.21.1`; NeoForge remains `21.1.249`.
- `OperationPlantSavedData` stays the single authoritative persistent Operations world state.
- Validation fixture IDs must begin with `validation:`.
- Validation code may create initial queue/buffer/maintenance state only through existing world facades when a facade exists.
- Validation code must not fabricate COMPLETED or DELIVERED history.
- AMR transport must use the real `redstoneengineering:engineering_mobile_robot` entity and production transport/docking APIs.
- Full Minecraft GameTests remain local/manual; normal CI must not begin launching the complete GameTest server.
- World ZIP packaging must package a real Minecraft-created save; it must not fabricate `level.dat`.

---

### Task 1: Lock the Validation Factory contract with a red static verifier

**Files:**
- Create: `tools/rse_validation_factory_verify.py`
- Test inputs: `build.gradle`, `tools/rse_validation_factory.py`, `src/main/java/dev/redstoneengineering/validation/RseValidationFactoryModule.java`, `src/main/java/dev/redstoneengineering/validation/RseValidationFactoryService.java`, `src/main/java/dev/redstoneengineering/gametest/RseValidationFactoryGameTests.java`

**Interfaces:**
- Consumes: repository files only.
- Produces: exit code `0` on contract success and non-zero with explicit messages on missing/unsafe implementation.

- [ ] **Step 1: Write the failing verifier**

The verifier must require these exact structure logical names:

```python
EXPECTED_STRUCTURES = {
    "material_release",
    "queue_dispatch",
    "maintenance_hold",
    "quality_output",
    "amr_lane",
    "operations_monitor",
    "full_factory",
}
```

It must also assert the Java implementation contains `/rsevalidation` build/reset/status entry points, `validation:` identity guards, `OperationIndustrialBufferState`, `OperationQueueWorldState`, `OperationMaintenanceWorldState`, and `EngineeringMobileRobotEntity`, while rejecting direct writes such as `putQueue(`, `putBuffer(`, or `putMaintenanceSnapshot(` inside the validation service.

- [ ] **Step 2: Run the verifier and confirm RED**

Run:

```bash
python3 tools/rse_validation_factory_verify.py
```

Expected: FAIL because the generator/module/service/GameTest do not yet exist.

- [ ] **Step 3: Commit the red verifier**

```bash
git add tools/rse_validation_factory_verify.py
git commit -m "test: define validation factory contract"
```

---

### Task 2: Generate deterministic reusable `.nbt` structures

**Files:**
- Create: `tools/rse_validation_factory.py`
- Generated: `src/generated/resources/data/redstoneengineering/structure/validation/material_release.nbt`
- Generated: `src/generated/resources/data/redstoneengineering/structure/validation/queue_dispatch.nbt`
- Generated: `src/generated/resources/data/redstoneengineering/structure/validation/maintenance_hold.nbt`
- Generated: `src/generated/resources/data/redstoneengineering/structure/validation/quality_output.nbt`
- Generated: `src/generated/resources/data/redstoneengineering/structure/validation/amr_lane.nbt`
- Generated: `src/generated/resources/data/redstoneengineering/structure/validation/operations_monitor.nbt`
- Generated: `src/generated/resources/data/redstoneengineering/structure/validation/full_factory.nbt`
- Modify: `build.gradle`

**Interfaces:**
- Produces CLI:
  - `python3 tools/rse_validation_factory.py --generate-structures`
  - `python3 tools/rse_validation_factory.py --package-world <save-dir>`
- Produces Minecraft structure NBT with root keys `size`, `palette`, `blocks`, `entities`, `DataVersion`.

- [ ] **Step 1: Add generator self-tests before generation code**

The Python tool must expose pure functions used by `unittest` inside the same file or a small `tools/test_rse_validation_factory.py`: verify signed big-endian NBT primitives, deterministic palette ordering, one known 3x3 fixture, and package exclusion rules.

Run:

```bash
python3 -m unittest tools.test_rse_validation_factory -v
```

Expected: FAIL because the writer/generator functions are missing.

- [ ] **Step 2: Implement the minimal standard-library NBT writer**

Implement only tags needed by Minecraft structures: Byte, Int, IntArray, String, List, Compound. Use `gzip` for structure file output and deterministic compound/list ordering owned by the generator.

- [ ] **Step 3: Define the seven station layouts**

Each layout uses explicit block IDs. RSE blocks in v1 include the currently registered `redstoneengineering:industrial_buffer`, `redstoneengineering:workcell_controller`, and safe vanilla shell/signage blocks. `amr_lane.nbt` remains a physical lane; the mobile entity is spawned by server-side setup so entity UUID/runtime state is not frozen into the template.

- [ ] **Step 4: Generate and validate files**

Run:

```bash
python3 tools/rse_validation_factory.py --generate-structures
python3 tools/rse_validation_factory_verify.py
```

Expected: generator succeeds; verifier may remain RED only for missing Java command/GameTest.

- [ ] **Step 5: Add Gradle generation task**

Add:

```groovy
def generateValidationStructures = tasks.register("generateValidationStructures", Exec) {
    commandLine "python3", "tools/rse_validation_factory.py", "--generate-structures"
}

tasks.named("processResources").configure {
    dependsOn(generateValidationStructures)
}
```

Add `packageValidationWorld` as an Exec task invoking:

```text
python3 tools/rse_validation_factory.py --package-world run/saves/RSE Validation Factory
```

The package command must fail clearly if `level.dat` is absent.

- [ ] **Step 6: Verify deterministic output**

Run generator twice and compare hashes:

```bash
find src/generated/resources/data/redstoneengineering/structure/validation -name '*.nbt' -print0 | sort -z | xargs -0 shasum -a 256 > /tmp/rse-validation-1.sha
python3 tools/rse_validation_factory.py --generate-structures
find src/generated/resources/data/redstoneengineering/structure/validation -name '*.nbt' -print0 | sort -z | xargs -0 shasum -a 256 > /tmp/rse-validation-2.sha
diff -u /tmp/rse-validation-1.sha /tmp/rse-validation-2.sha
```

Expected: no diff.

---

### Task 3: Add the server-side validation builder and safe fixture reset boundary

**Files:**
- Create: `src/main/java/dev/redstoneengineering/validation/RseValidationFactoryService.java`
- Create: `src/main/java/dev/redstoneengineering/validation/RseValidationFactoryModule.java`

**Interfaces:**
- `RseValidationFactoryService.build(ServerLevel level, BlockPos origin)` returns a result summary.
- `RseValidationFactoryService.reset(ServerLevel level, BlockPos origin, String station)` returns a result summary.
- `RseValidationFactoryService.status(ServerLevel level)` returns immutable text lines / components for observer-only reporting.
- Command root: `/rsevalidation build|reset|status`.

- [ ] **Step 1: Extend the static verifier for exact safety boundaries**

Require:

```text
OperationIndustrialBufferState.create
OperationQueueWorldState.create
OperationMaintenanceWorldState.observe
validation:
```

Reject direct mutation calls in the service:

```text
OperationPlantSavedData.get
.putBuffer(
.putQueue(
.putMaintenanceSnapshot(
.recordPlantEvent(
setDeltaMovement(
```

Run verifier and confirm it stays RED.

- [ ] **Step 2: Implement template placement**

Load `redstoneengineering:validation/full_factory` through Minecraft's structure template manager and place it at `origin`. Station reset loads the corresponding station template at a fixed offset table.

- [ ] **Step 3: Implement fixture creation through world facades**

Create reserved fixtures only if absent:

```text
buffer: validation:input_buffer
buffer: validation:output_buffer
queue: validation:main_queue
resource: validation:workcell_ready
resource: validation:workcell_due
```

Use explicit positions derived from the factory origin. Seed READY and MAINTENANCE_DUE evidence through `OperationMaintenanceWorldState.observe` only.

- [ ] **Step 4: Spawn one real AMR safely**

Use `RoboticsEntityModule.ENGINEERING_MOBILE_ROBOT` to create an idle robot at the source staging point. Tag/name it as a validation robot so reset can identify only this fixture. Do not modify private operating state or record transport success.

- [ ] **Step 5: Implement reset guard**

Reset may remove/recreate only known `validation:` fixtures and the tagged validation robot. If a queue/buffer cannot be removed because it contains WIP, return a fail-closed message rather than clearing `OperationPlantSavedData` directly.

- [ ] **Step 6: Implement observer-only status**

Status reads existing facade snapshots / plant readback and the tagged robot state. It must not call create/observe/dispatch/transport mutators.

- [ ] **Step 7: Register commands**

`RseValidationFactoryModule` subscribes to NeoForge command registration and registers permission level suitable for a cheats-enabled local validation world.

- [ ] **Step 8: Run verifier**

```bash
python3 tools/rse_validation_factory_verify.py
```

Expected: only GameTest/package checks may remain RED.

---

### Task 4: Add focused local GameTest coverage

**Files:**
- Create: `src/main/java/dev/redstoneengineering/gametest/RseValidationFactoryGameTests.java`
- Modify: `src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java`

**Interfaces:**
- GameTest method: `fullFactoryTemplateAndFixtureBaselineLoad`.
- Template: existing `empty5x4x5` test shell; the test invokes the builder at a controlled world position rather than requiring the entire factory to fit inside the 5x4x5 template.

- [ ] **Step 1: Write the GameTest before registration**

Test requirements:

1. `RseValidationFactoryService.build` succeeds.
2. `OperationIndustrialBufferState.snapshot` finds validation input/output buffers.
3. `OperationQueueWorldState.snapshot` finds `validation:main_queue` and it is initially empty.
4. `OperationMaintenanceWorldState.snapshot` reports READY and MAINTENANCE_DUE fixtures with their exact identities.
5. Exactly one tagged validation AMR exists in the expected nearby bounding box.
6. No COMPLETED or DELIVERED validation history is created merely by building the factory.

- [ ] **Step 2: Register the GameTest**

Add:

```java
event.register(RseValidationFactoryGameTests.class);
```

to `RseGameTestRegistration`.

- [ ] **Step 3: Compile**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Keep CI local/manual**

Do not modify normal build workflows to run the entire GameTest server. Static verifier and compile/build remain the CI-safe checks.

---

### Task 5: Package a real validation save and document the operator workflow

**Files:**
- Modify: `tools/rse_validation_factory.py`
- Create: `docs/VALIDATION_FACTORY.md`
- Modify: `README.md` only with a short link if appropriate.

**Interfaces:**
- `--package-world PATH` requires `PATH/level.dat`.
- Output: `build/validation/RSE-Validation-Factory.zip`.

- [ ] **Step 1: Write packaging tests first**

Test a temporary fake directory containing a sentinel `level.dat`, region file, player data, and transient files. Expected ZIP includes game save content but excludes `session.lock`, log files, crash reports, and macOS metadata.

- [ ] **Step 2: Implement deterministic ZIP packaging**

Sort archive paths, use a stable archive root `RSE Validation Factory/`, and reject a path without `level.dat` with a readable error.

- [ ] **Step 3: Write exact operator guide**

Document:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
python3 tools/rse_validation_factory.py --generate-structures
./gradlew compileJava test
./gradlew runClient
```

In game:

```text
Create/open a Creative cheats-enabled world named: RSE Validation Factory
/rsevalidation build
/rsevalidation status
```

After save/quit:

```bash
python3 tools/rse_validation_factory.py --package-world "run/saves/RSE Validation Factory"
```

Also document independent template placement:

```text
/place template redstoneengineering:validation/amr_lane
/place template redstoneengineering:validation/maintenance_hold
```

- [ ] **Step 4: Verify documentation commands against actual CLI names**

Run `--help`, structure generation, and packaging tests.

---

### Task 6: Final verification and evidence

**Files:**
- No new production files unless a defect is found.

**Interfaces:**
- Produces final evidence for the feature branch.

- [ ] **Step 1: Run static validation checks**

```bash
python3 -m unittest tools.test_rse_validation_factory -v
python3 tools/rse_validation_factory_verify.py
python3 tools/rse_operations_material_release_verify.py
python3 tools/rse_operations_maintenance_verify.py
python3 tools/rse_operations_world_integration_verify.py
python3 tools/rse_operations_amr_world_verify.py
```

Expected: all PASS. If the AMR implementation is still intentionally red at this point, finish that prerequisite before claiming Validation Factory completion.

- [ ] **Step 2: Run Java/Gradle verification**

```bash
./gradlew clean compileJava
./gradlew test
./gradlew build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Perform local interactive acceptance**

```bash
./gradlew runClient
```

Create/open `RSE Validation Factory`, run `/rsevalidation build`, exercise stations, save/quit/re-enter, run `/rsevalidation status`, then package the real save.

- [ ] **Step 4: Check packaged artifact**

```bash
unzip -l build/validation/RSE-Validation-Factory.zip | head -n 40
```

Expected: archive root `RSE Validation Factory/`, contains `level.dat`, generated region/save data, and no `session.lock`.

- [ ] **Step 5: Commit only after verification evidence is fresh**

Use focused commits for verifier, generator, Java validation module, GameTest, and docs. Do not merge the existing PR unless explicitly requested.
