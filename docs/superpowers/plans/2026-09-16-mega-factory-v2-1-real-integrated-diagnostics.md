# Mega Validation Factory v2.1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Repair Mega Validation Factory v2 so all 40 DUTs are genuinely integrated, normal-phase false-green verdicts are impossible, H-cell safety/control is physically closed, and `/rsevalidation mega diagnose` exports a copyable backend/file report with root-cause and cascade analysis.

**Architecture:** Split the current monolithic mega runtime into topology, evaluation, reporting/export, orchestration, and persistence responsibilities. The physical generator and Java runtime share the same station/cell/dependency contract through fail-closed contract tests. Evaluation becomes phase-aware: health and scenario verdict are separate so expected fault evidence can pass a scenario without being labeled healthy.

**Tech Stack:** Java 21, NeoForge 1.21.1, Minecraft server/client runtime APIs, existing RSE `EngineeringPortProvider`/`EngineeringPortSnapshot`/`PortQuality`, Python 3 unittest-based structure contracts, existing GitHub Actions build/operations workflows.

**Spec:** `docs/superpowers/specs/2026-09-16-mega-factory-v2-1-real-integrated-diagnostics-design.md`

## Global Constraints

- Keep exactly 40 primary DUTs and 8 cells × 5 DUTs.
- Do not remove the 19 selftests or v1 integrated plant.
- Do not let validator code write DUT outputs to manufacture PASS evidence.
- In normal phases, `NO_SIGNAL`, `FAULT`, `DOMAIN_MISMATCH`, and `TOPOLOGY_ERROR` cannot produce PASS.
- Fault phases may accept an intended degraded observation only when downstream safety/recovery behavior also matches the scenario.
- `/rsevalidation mega diagnose` must perform a fresh live evaluation.
- The same diagnostic report must go to chat, logger, `run/rse-diagnostics/mega-latest.txt`, and append to `run/rse-diagnostics/mega-history.log`.
- Retest increments integer `runNumber`; historical failures are never erased by recovery.
- Minecraft topology GameTests remain manual diagnostics unless repository policy changes.

---

### Task 1: Lock the false-green regression with phase-aware evaluation contracts

**Files:**
- Create: `src/main/java/dev/redstoneengineering/validation/RseMegaStationEvaluator.java`
- Modify: `src/main/java/dev/redstoneengineering/validation/RseMegaValidationService.java`
- Modify: `tools/test_rse_mega_factory.py`

**Interfaces:**
- Produces: `RseMegaStationEvaluator.Health { HEALTHY, DEGRADED, FAILED, PENDING }`
- Produces: `RseMegaStationEvaluator.StationEvaluation(int station, String cell, Health health, Verdict scenarioVerdict, String reasonCode, PortQuality quality, String detail, BlockPos worldPos)`
- Produces: `evaluate(ServerLevel level, BlockPos plantOrigin, RseMegaValidationTopology.Station station, RseMegaValidationService.Phase phase, long phaseAge)`
- Consumes: existing `RseValidationSelfTestService.Verdict`, `EngineeringPortProvider`, `EngineeringPortSnapshot`, `PortQuality`.

- [ ] **Step 1: Write failing static/contract tests for field findings**

Add tests that require the evaluator source to contain an explicit quality policy and station-specific normal-state checks:

```python
def test_normal_quality_policy_rejects_false_green_states(self):
    source = JAVA_EVALUATOR.read_text()
    for token in [
        "PortQuality.NO_SIGNAL",
        "PortQuality.FAULT",
        "PortQuality.DOMAIN_MISMATCH",
        "PortQuality.TOPOLOGY_ERROR",
        "PortQuality.STALE",
    ]:
        self.assertIn(token, source)
    self.assertNotIn("quality() != PortQuality.STALE", source)


def test_h_cell_normal_state_cannot_false_pass(self):
    source = JAVA_EVALUATOR.read_text()
    self.assertIn("UNINTENDED_BRAKE", source)
    self.assertIn("POSITION_FEEDBACK_NO_SIGNAL", source)
    self.assertIn("INTERLOCK_FAILED_MASK", source)
```

- [ ] **Step 2: Run the regression suite and verify RED**

Run:

```bash
python3 -m unittest tools.test_rse_mega_factory -v
```

Expected: new evaluator/policy tests fail because `RseMegaStationEvaluator` does not exist and the old generic logic still counts non-STALE snapshots as fresh.

- [ ] **Step 3: Implement the evaluation model and explicit quality policy**

Use a helper with normal/fault semantics instead of the old `!= STALE` rule:

```java
private static QualityDecision decideQuality(PortQuality quality, boolean expectedDegradation, long age) {
    return switch (quality) {
        case VALID -> new QualityDecision(Health.HEALTHY, Verdict.PASS, "VALID");
        case STALE -> age < PROPAGATION_TIMEOUT_TICKS
                ? new QualityDecision(Health.PENDING, Verdict.WAIT, "STALE_PROPAGATING")
                : new QualityDecision(Health.FAILED, Verdict.FAIL, "STALE_TIMEOUT");
        case NO_SIGNAL -> expectedDegradation
                ? new QualityDecision(Health.DEGRADED, Verdict.PASS, "EXPECTED_NO_SIGNAL")
                : age < PROPAGATION_TIMEOUT_TICKS
                    ? new QualityDecision(Health.PENDING, Verdict.WAIT, "NO_SIGNAL_PROPAGATING")
                    : new QualityDecision(Health.FAILED, Verdict.FAIL, "NO_SIGNAL_TIMEOUT");
        case SATURATED -> expectedDegradation
                ? new QualityDecision(Health.DEGRADED, Verdict.PASS, "EXPECTED_SATURATION")
                : new QualityDecision(Health.FAILED, Verdict.FAIL, "UNEXPECTED_SATURATION");
        case FAULT -> new QualityDecision(Health.FAILED, Verdict.FAIL, "FAULT");
        case DOMAIN_MISMATCH -> new QualityDecision(Health.FAILED, Verdict.FAIL, "DOMAIN_MISMATCH");
        case TOPOLOGY_ERROR -> new QualityDecision(Health.FAILED, Verdict.FAIL, "TOPOLOGY_ERROR");
    };
}
```

Add station-specific checks for D37/D38/D39/D40 so normal phases reject unintended braking, invalid feedback, nonzero interlock failed mask, and unexpected alarm latch.

- [ ] **Step 4: Route station evaluation through the new evaluator**

Replace `RseMegaValidationService.evaluateStation(...)` internals with delegation to `RseMegaStationEvaluator`, keeping orchestration in the service.

- [ ] **Step 5: Run tests and compile**

```bash
python3 -m unittest tools.test_rse_mega_factory -v
./gradlew compileJava
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add tools/test_rse_mega_factory.py \
  src/main/java/dev/redstoneengineering/validation/RseMegaStationEvaluator.java \
  src/main/java/dev/redstoneengineering/validation/RseMegaValidationService.java
git commit -m "fix: make mega station verdicts phase-aware"
```

---

### Task 2: Introduce an authoritative Mega topology/dependency model

**Files:**
- Create: `src/main/java/dev/redstoneengineering/validation/RseMegaValidationTopology.java`
- Modify: `src/main/java/dev/redstoneengineering/validation/RseMegaValidationService.java`
- Modify: `tools/rse_mega_factory.py`
- Modify: `tools/test_rse_mega_factory.py`

**Interfaces:**
- Produces: `Station`, `Cell`, `Dependency`, and `BridgeCheckpoint` records.
- Produces: `station(int)`, `stations()`, `cell(String)`, `stationWorldPos(BlockPos,int)`, `upstreamStation(int)`, `dependenciesForCell(String)`.
- Consumes: exact existing 40 station IDs and 11 module offsets.

- [ ] **Step 1: Add failing tests for orphan detection and dependency coverage**

```python
def test_every_primary_dut_has_declared_dependency_path(self):
    self.assertEqual(40, len(MEGA_STATIONS))
    for station in MEGA_STATIONS:
        self.assertIn(station.number, MEGA_DEPENDENCIES)


def test_every_cell_declares_real_input_and_output_checkpoints(self):
    for cell in "ABCDEFGH":
        contract = MEGA_CELL_CONTRACTS[cell]
        self.assertIsNotNone(contract.input_checkpoint)
        self.assertIsNotNone(contract.output_checkpoint)
        self.assertEqual(5, len(contract.primary_stations))
```

Also require the Java topology source to contain all 40 station declarations and A-H dependency data.

- [ ] **Step 2: Verify RED**

```bash
python3 -m unittest tools.test_rse_mega_factory -v
```

- [ ] **Step 3: Move module/station metadata out of the service**

Create immutable topology records and migrate `MODULES`, `STATIONS`, station lookup, and world-position calculations from `RseMegaValidationService` into `RseMegaValidationTopology`.

- [ ] **Step 4: Add dependency semantics**

For each station, declare whether its predecessor is another primary DUT or an auxiliary bridge/source. The graph must distinguish source-type DUTs (for example D06 quartz oscillator and D11 noise source) from transform/sink DUTs; source-type DUTs still depend on the cell enable/checkpoint for integrated acceptance even when they do not consume the previous station's signal domain directly.

- [ ] **Step 5: Add bridge checkpoint contracts for A→B→C→D→E→F→G→H**

Each boundary records source domain, destination domain, cell output checkpoint, destination enable/input checkpoint, and auxiliary block IDs used by the Python generator. This gives the runtime enough information to distinguish a broken bridge from a broken DUT.

- [ ] **Step 6: Run tests and compile**

```bash
python3 -m unittest tools.test_rse_mega_factory -v
./gradlew compileJava
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add tools/rse_mega_factory.py tools/test_rse_mega_factory.py \
  src/main/java/dev/redstoneengineering/validation/RseMegaValidationTopology.java \
  src/main/java/dev/redstoneengineering/validation/RseMegaValidationService.java
git commit -m "refactor: centralize mega factory topology"
```

---

### Task 3: Rebuild the eight cells as connected physical subsystems

**Files:**
- Modify: `tools/rse_mega_factory.py`
- Modify: `tools/test_rse_mega_factory.py`

**Interfaces:**
- Consumes: `MEGA_DEPENDENCIES`, `MEGA_CELL_CONTRACTS`, and the existing 40 station identities.
- Produces: cell structures in `MEGA_STRUCTURES` where each primary DUT has a declared physical input/output or cell-enable dependency.

- [ ] **Step 1: Add generator tests that fail on disconnected fixtures**

Require every non-source primary DUT to have at least one adjacent declared upstream connector/bridge and every cell to have a connected input/output path. Explicitly reject the old pattern where the only evidence is an unrelated source at local z=15.

```python
def test_station_fixture_is_not_counted_as_connectivity_by_itself(self):
    for station in MEGA_STATIONS:
        if station.number not in SOURCE_TYPE_STATIONS:
            self.assertTrue(has_declared_upstream_neighbor(station))
```

- [ ] **Step 2: Verify RED against current generator**

```bash
python3 -m unittest tools.test_rse_mega_factory -v
```

- [ ] **Step 3: Rework cells A-D**

Build explicit routes for analog metrology, timing/waveform, precision-signal, and digital-data cells. Preserve D01-D20 identities and status panels. Use auxiliary converters/lines only as infrastructure; do not count them as primary DUTs.

- [ ] **Step 4: Rework cells E-H**

Build explicit robust-comms, optical, pneumatic, and control/safety routes. Remove or repurpose local z=15 sources that are not connected to a declared test/stimulus port.

- [ ] **Step 5: Generate structures and run contract tests**

```bash
python3 tools/rse_validation_factory.py generate
python3 -m unittest tools.test_rse_mega_factory -v
```

Expected: PASS, including 40 unchanged DUT identities and 8×5 grouping.

- [ ] **Step 6: Commit**

```bash
git add tools/rse_mega_factory.py tools/test_rse_mega_factory.py
git commit -m "fix: physically integrate mega factory cells"
```

---

### Task 4: Close the H-cell control/feedback/safety/alarm loop

**Files:**
- Modify: `tools/rse_mega_factory.py`
- Modify: `src/main/java/dev/redstoneengineering/validation/RseMegaStationEvaluator.java`
- Modify: `src/main/java/dev/redstoneengineering/validation/RseMegaValidationTopology.java`
- Modify: `tools/test_rse_mega_factory.py`

**Interfaces:**
- D37 Servo command/brake/runtime evidence from `ServoActuatorBlock`.
- D38 feedback quality from `ServoPositionSensorBlock.sourceQuality(...)`.
- D39 permissive mask from `SafetyInterlockBlock.failedMask(...)`.
- D40 latch state from `AlarmProcessorBlock.latched(...)`.

- [ ] **Step 1: Add failing H-cell topology tests**

Tests must assert servo/sensor mechanical adjacency and declare three interlock permissive inputs plus alarm condition/ACK/RESET fixture paths.

- [ ] **Step 2: Verify RED**

```bash
python3 -m unittest tools.test_rse_mega_factory -v
```

- [ ] **Step 3: Place D36-D40 with real orientation/neighbor semantics**

Use the real block port implementations as the source of truth. The generated fixture must satisfy:

```text
D36 PID -> D37 SERVO -> D38 POSITION SENSOR
                 ^             |
                 | brake       | feedback/safety evidence
                 +--- D39 INTERLOCK -> D40 ALARM
```

Add validation-owned permissive/ACK/RESET sources only on the actual declared sides.

- [ ] **Step 4: Make H evaluation scenario-aware**

Normal phases require: D37 not unintentionally braking, D38 valid feedback, D39 `failedMask == 0`, and D40 clear. Fault phases require the expected trip/brake/latch behavior before scenario PASS; recovery requires all normal conditions restored.

- [ ] **Step 5: Run tests and compile**

```bash
python3 -m unittest tools.test_rse_mega_factory -v
./gradlew compileJava
```

- [ ] **Step 6: Commit**

```bash
git add tools/rse_mega_factory.py tools/test_rse_mega_factory.py \
  src/main/java/dev/redstoneengineering/validation/RseMegaStationEvaluator.java \
  src/main/java/dev/redstoneengineering/validation/RseMegaValidationTopology.java
git commit -m "fix: close mega H-cell safety loop"
```

---

### Task 5: Add root-blocker and cascade-aware diagnosis

**Files:**
- Create: `src/main/java/dev/redstoneengineering/validation/RseMegaDiagnosticReporter.java`
- Modify: `src/main/java/dev/redstoneengineering/validation/RseMegaValidationService.java`
- Modify: `tools/test_rse_mega_factory.py`

**Interfaces:**
- Consumes: live station evaluations, cell evaluations, active phase gate, topology dependency graph, saved history.
- Produces: `DiagnosticReport(List<Component> chatLines, List<String> textLines, List<Integer> rootBlockers, List<Integer> cascadeStations)`.

- [ ] **Step 1: Add failing reporter contract tests**

Require report sections:

```text
===== ROOT BLOCKERS =====
===== CASCADE =====
===== CELL SUMMARY =====
===== ALL 40 DUT =====
===== HISTORY =====
===== ACCEPTANCE =====
```

and dependency-driven upstream attribution.

- [ ] **Step 2: Verify RED**

```bash
python3 -m unittest tools.test_rse_mega_factory -v
```

- [ ] **Step 3: Implement reporter**

A failed/waiting station is a root blocker when no failed/waiting upstream dependency explains it. Downstream stations with an abnormal upstream dependency are emitted under CASCADE with an arrow chain.

- [ ] **Step 4: Replace inline diagnose formatting**

`RseMegaValidationService.diagnose(...)` performs live evaluation and delegates formatting/root-cause analysis to `RseMegaDiagnosticReporter`.

- [ ] **Step 5: Run tests and compile**

```bash
python3 -m unittest tools.test_rse_mega_factory -v
./gradlew compileJava
```

- [ ] **Step 6: Commit**

```bash
git add tools/test_rse_mega_factory.py \
  src/main/java/dev/redstoneengineering/validation/RseMegaDiagnosticReporter.java \
  src/main/java/dev/redstoneengineering/validation/RseMegaValidationService.java
git commit -m "feat: report mega root blockers and cascades"
```

---

### Task 6: Export diagnostics to backend logger and copyable files

**Files:**
- Modify: `src/main/java/dev/redstoneengineering/validation/RseMegaDiagnosticReporter.java`
- Modify: `src/main/java/dev/redstoneengineering/validation/RseMegaValidationService.java`
- Modify: `tools/test_rse_mega_factory.py`

**Interfaces:**
- Produces: logger lines prefixed `[RSE-MEGA-DIAG]`.
- Produces: `run/rse-diagnostics/mega-latest.txt` overwritten atomically per diagnosis.
- Produces: `run/rse-diagnostics/mega-history.log` appended per diagnosis.

- [ ] **Step 1: Add failing export contract tests**

Require exact path strings and logger prefix in reporter source.

- [ ] **Step 2: Verify RED**

```bash
python3 -m unittest tools.test_rse_mega_factory -v
```

- [ ] **Step 3: Implement safe file export**

Create the directory with `Files.createDirectories`, write latest through a temporary file then move/replace, append history using UTF-8, and catch `IOException` so export failure never crashes the world or suppresses chat output.

- [ ] **Step 4: Emit every report line to the backend logger**

Use the RSE logger with `[RSE-MEGA-DIAG]` prefix so the same content appears in `run/logs/latest.log` and the `runClient` terminal.

- [ ] **Step 5: Run tests and compile**

```bash
python3 -m unittest tools.test_rse_mega_factory -v
./gradlew compileJava
```

- [ ] **Step 6: Commit**

```bash
git add tools/test_rse_mega_factory.py \
  src/main/java/dev/redstoneengineering/validation/RseMegaDiagnosticReporter.java \
  src/main/java/dev/redstoneengineering/validation/RseMegaValidationService.java
git commit -m "feat: export mega diagnostics to backend files"
```

---

### Task 7: Add runNumber persistence and fix phase/history semantics

**Files:**
- Modify: `src/main/java/dev/redstoneengineering/validation/RseMegaValidationSavedData.java`
- Modify: `src/main/java/dev/redstoneengineering/validation/RseMegaValidationService.java`
- Modify: `src/main/java/dev/redstoneengineering/validation/RseMegaDiagnosticReporter.java`
- Modify: `tools/test_rse_mega_factory.py`

**Interfaces:**
- Produces: `int runNumber()` and retest increment semantics.
- Reporter includes `RUN=<n>`.

- [ ] **Step 1: Add failing persistence contract tests**

Require `runNumber` field, save/load tag key, increment on retest, and report inclusion.

- [ ] **Step 2: Verify RED**

```bash
python3 -m unittest tools.test_rse_mega_factory -v
```

- [ ] **Step 3: Implement `runNumber`**

Initial placement uses run 1. Every successful retest rebuild increments exactly once. Loading preserves the value. Recovery updates current verdict but does not clear first-failure/ever-failed history.

- [ ] **Step 4: Fix final acceptance invariants**

Final acceptance requires all prerequisite phases complete, zero unresolved station failures, healthy final station state, 8/8 cell pass, endurance complete, and plant continuity. `stationsEverPassed=40/40` alone is explicitly insufficient.

- [ ] **Step 5: Run tests and compile**

```bash
python3 -m unittest tools.test_rse_mega_factory -v
./gradlew compileJava
```

- [ ] **Step 6: Commit**

```bash
git add tools/test_rse_mega_factory.py \
  src/main/java/dev/redstoneengineering/validation/RseMegaValidationSavedData.java \
  src/main/java/dev/redstoneengineering/validation/RseMegaValidationService.java \
  src/main/java/dev/redstoneengineering/validation/RseMegaDiagnosticReporter.java
git commit -m "feat: persist mega validation run identity"
```

---

### Task 8: Full verification, docs, and field handoff

**Files:**
- Modify: `docs/VALIDATION_FACTORY.md`
- Modify: `.github/workflows/build.yml` only if new test file discovery requires it; preserve all existing safety gates.
- Test: existing full verification stack.

**Interfaces:**
- Field workflow remains only:
  - `/rsevalidation mega retest`
  - `/rsevalidation mega diagnose`
  - copy `run/rse-diagnostics/mega-latest.txt`

- [ ] **Step 1: Update user-facing validation docs**

Document the new file paths, root/cascade output, normal-quality semantics, and the fact that in-world acceptance remains unproven until a field rerun.

- [ ] **Step 2: Run Python contracts**

```bash
python3 -m unittest \
  tools.test_rse_validation_autorun \
  tools.test_rse_validation_plant \
  tools.test_rse_mega_factory -v
```

Expected: PASS.

- [ ] **Step 3: Run generator/static verification**

```bash
python3 tools/rse_validation_factory.py generate
python3 tools/rse_validation_factory_verify.py
python3 tools/rse_validation_selftest_verify.py
```

Expected: PASS.

- [ ] **Step 4: Run Java build gates**

```bash
./gradlew clean compileJava
./gradlew test
./gradlew clean build
```

Expected: PASS.

- [ ] **Step 5: Push final HEAD and verify GitHub Actions**

Required workflow results on the exact final SHA:

```text
RSE Build Verification: success
RSE Operations Material Flow Verification: success
```

Confirm contract/static/compileJava/Gradle tests/clean build/checksum/artifact steps are success. Do not report manual Minecraft topology GameTests as passed if skipped.

- [ ] **Step 6: Commit documentation**

```bash
git add docs/VALIDATION_FACTORY.md .github/workflows/build.yml
git commit -m "docs: document mega v2.1 field diagnostics"
```

- [ ] **Step 7: Field handoff**

User runs:

```text
/rsevalidation mega retest
/rsevalidation mega diagnose
```

Then shares:

```text
run/rse-diagnostics/mega-latest.txt
```

Do not claim in-world acceptance before that exported report proves the repaired physical topology and phase sequence in the real client.
